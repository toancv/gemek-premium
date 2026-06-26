import React, { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import {
  useCreateAnnouncement,
  usePublishAnnouncement,
  useUploadAnnouncementMedia,
  useUploadAnnouncementAttachment,
} from '../api/hooks';
import { getVnErrorMessage } from '@gemek/ui';
import { useAnnouncementForm, AnnouncementComposeFields } from '../components/AnnouncementComposer';
import { AnnouncementMediaManager, type AnnouncementMediaItem } from '../components/AnnouncementMediaManager';
import { AnnouncementAttachmentsManager, type AnnouncementAttachmentItem } from '../components/AnnouncementAttachmentsManager';

// Stable empty arrays for the /new managers — no media/attachments exist before the draft is created,
// and a stable identity avoids needless manager re-renders.
const EMPTY_MEDIA: AnnouncementMediaItem[] = [];
const EMPTY_ATTACHMENTS: AnnouncementAttachmentItem[] = [];
// The media manager's insert/delete callbacks can never fire on /new (no items render before the
// lazy-save navigates away), so a no-op is correct.
const noop = () => {};

/**
 * Admin announcement create page (replaces the retired create modal). SAVE-FIRST per the C2.3b
 * ruling: "Lưu nháp" creates the draft then redirects to /:id/edit. "Tạo & đăng" creates then
 * publishes, preserving the two-stage draft-survives recovery.
 *
 * C3 P2.5 LAZY-SAVE (CTO ruling A): the image media manager AND the attachments manager are mounted
 * here too. With no draft id yet, the FIRST upload (image or file) auto-creates the draft —
 * validate form → create → upload → navigate(replace) to /:id/edit — so files can be added on /new
 * without a separate save step. A draft is created ONLY on a real upload or an explicit save (never
 * by merely visiting/leaving /new), and a synchronous in-flight guard guarantees exactly ONE draft
 * even under a double-click or a quick second file.
 */
export function AnnouncementCreatePage() {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const form = useAnnouncementForm();
  const create = useCreateAnnouncement();
  const publish = usePublishAnnouncement();
  const uploadMedia = useUploadAnnouncementMedia();
  const uploadAttachment = useUploadAnnouncementAttachment();
  // Which action is in flight, so each button shows its own correct busy label (not the other's).
  const [submitting, setSubmitting] = useState<null | 'draft' | 'publish'>(null);
  // True while a lazy-save (create-on-first-upload) is in flight — drives the disabled styling on both
  // managers' upload triggers and the save buttons.
  const [lazyBusy, setLazyBusy] = useState(false);
  // Hard concurrency guard (synchronous ref, NOT state): exactly ONE of {Lưu nháp, Tạo & đăng,
  // lazy-upload} may run at a time, so a double-click or a quick second picked file can NEVER create
  // two drafts. State drives the disabled UI; this ref is the actual single-draft guarantee (a ref
  // updates synchronously, so a second trigger in the same tick sees it set and bails).
  const inFlight = useRef(false);

  // Shared explicit-save path ("Lưu nháp" / "Tạo & đăng"). When publishNow, publish after create — but
  // if publish fails, the draft survives: carry a notice to the edit page (where the admin can retry)
  // rather than losing it. The just-created draft is seeded into the detail cache so the edit page
  // renders immediately and keeps the notice even if a background refetch is slow/fails.
  const submit = async (publishNow: boolean) => {
    if (inFlight.current) return;          // blocked while any create/upload is already in flight
    if (!form.validate()) return;
    inFlight.current = true;
    setSubmitting(publishNow ? 'publish' : 'draft');
    try {
      const created: any = await create.mutateAsync(form.toPayload());
      qc.setQueryData(['announcements', created.id], created);
      if (publishNow) {
        try {
          await publish.mutateAsync(created.id);
        } catch (pubErr: any) {
          navigate(`/announcements/${created.id}/edit`, {
            state: { notice: 'Thông báo đã được tạo (bản nháp đã lưu) nhưng xuất bản thất bại: ' + getVnErrorMessage(pubErr?.response?.data?.error) },
          });
          return;
        }
        // Full success → back to the list.
        navigate('/announcements');
        return;
      }
      // Draft saved → continue editing (media/attachment managers live on the edit page too).
      navigate(`/announcements/${created.id}/edit`);
    } catch (err: any) {
      form.setFormError(getVnErrorMessage(err?.response?.data?.error));
    } finally {
      setSubmitting(null);
      inFlight.current = false;
    }
  };

  // VN message for an upload failure AFTER the draft was created — surfaced on the edit page (the draft
  // survived; the admin retries there). A 413 has no coded body, so map it to a size message, mirroring
  // the managers' own errorText.
  const uploadErrNotice = (err: any, kindLabel: 'ảnh' | 'tệp'): string => {
    const detail = err?.response?.status === 413
      ? (kindLabel === 'ảnh' ? 'Ảnh quá lớn (tối đa 10MB mỗi ảnh).' : 'Tệp quá lớn (tối đa 10MB mỗi tệp).')
      : getVnErrorMessage(err?.response?.data?.error);
    return `Bản nháp đã được lưu nhưng tải lên ${kindLabel} thất bại: ${detail}`;
  };

  // Lazy-save orchestrator (CTO ruling A). On the FIRST upload on /new there is no draft id yet:
  // validate the create form → create the draft → upload the file → navigate(replace) to /:id/edit
  // (which refetches detail and shows the just-uploaded item). The file-size pre-check has ALREADY run
  // in the calling manager BEFORE we get here, so an oversize file never reaches this point and never
  // creates a draft (no orphan from an invalid file). Partial-failure handling mirrors create→publish:
  //  - invalid form → no draft, no upload; the inline VN validation error shows on this page.
  //  - create fails  → no upload, no navigate; show the create error.
  //  - upload fails   → still navigate(replace) to edit (the draft IS saved); surface the error there.
  const ensureDraftThenUpload = async (
    uploadFn: (id: string) => Promise<unknown>,
    kindLabel: 'ảnh' | 'tệp',
  ): Promise<void> => {
    if (inFlight.current) return;          // single-draft concurrency guard (synchronous)
    if (!form.validate()) return;          // invalid → no draft, no upload
    inFlight.current = true;
    setLazyBusy(true);
    let created: any;
    try {
      created = await create.mutateAsync(form.toPayload());
    } catch (err: any) {
      form.setFormError(getVnErrorMessage(err?.response?.data?.error));
      inFlight.current = false;
      setLazyBusy(false);
      return;                              // create failed → abort: no orphan, no upload
    }
    // Deliberately do NOT seed the detail cache here (unlike the explicit-save path below): `created`
    // is the PRE-upload draft with EMPTY media/attachments, so seeding it would flash an empty edit
    // page until the upload's refetch lands (the just-uploaded file would look lost). Leaving the
    // cache unseeded lets the edit page's useAnnouncement fetch fresh detail — which already includes
    // the just-uploaded item, since the upload completes before we navigate — behind its spinner.
    // Draft saved. Upload, then go to edit. If the upload fails the draft still survives → navigate
    // anyway and carry the error as a notice (never strand the admin on /new, never delete the draft).
    try {
      await uploadFn(created.id);
      navigate(`/announcements/${created.id}/edit`, { replace: true });
    } catch (upErr: any) {
      navigate(`/announcements/${created.id}/edit`, {
        replace: true,
        state: { notice: uploadErrNotice(upErr, kindLabel) },
      });
    }
    // navigate(replace) unmounts /new — no inFlight/lazyBusy reset needed (this component is gone).
  };

  const busy = submitting !== null || lazyBusy;

  return (
    <div className="w-full">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Tạo thông báo</h1>
      </div>

      {/* Media + attachment managers (same as the edit page). With no draft id yet they show only their
          upload controls + constraints + empty state; the first upload lazy-creates the draft. */}
      <div className="mb-6">
        <AnnouncementMediaManager
          announcementId=""
          media={EMPTY_MEDIA}
          onInsert={noop}
          onDeleted={noop}
          externalBusy={busy}
          onLazyUpload={(file, kind) =>
            ensureDraftThenUpload((id) => uploadMedia.mutateAsync({ id, file, kind }), 'ảnh')
          }
        />
      </div>

      <div className="mb-6">
        <AnnouncementAttachmentsManager
          announcementId=""
          attachments={EMPTY_ATTACHMENTS}
          externalBusy={busy}
          onLazyUpload={(file) =>
            ensureDraftThenUpload((id) => uploadAttachment.mutateAsync({ id, file }), 'tệp')
          }
        />
      </div>

      <AnnouncementComposeFields form={form} />

      {form.formError && <p className="mt-4 text-sm text-red-600 bg-red-50 px-3 py-2 rounded">{form.formError}</p>}

      <div className="flex gap-2 justify-end pt-6 mt-4 border-t border-gray-200">
        <button type="button" onClick={() => navigate('/announcements')} className="px-4 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50">Huỷ</button>
        <button type="button" onClick={() => submit(false)} disabled={busy} className="px-4 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50 disabled:opacity-50">
          {submitting === 'draft' ? 'Đang lưu...' : 'Lưu nháp'}
        </button>
        <button type="button" onClick={() => submit(true)} disabled={busy} className="px-4 py-2 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50">
          {submitting === 'publish' ? 'Đang đăng...' : 'Tạo & đăng'}
        </button>
      </div>
    </div>
  );
}
