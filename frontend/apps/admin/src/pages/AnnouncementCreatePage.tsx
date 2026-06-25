import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { useCreateAnnouncement, usePublishAnnouncement } from '../api/hooks';
import { getVnErrorMessage } from '@gemek/ui';
import { useAnnouncementForm, AnnouncementComposeFields } from '../components/AnnouncementComposer';

/**
 * Admin announcement create page (replaces the retired create modal). SAVE-FIRST per the C2.3b
 * ruling: "Lưu nháp" creates the draft then redirects to /:id/edit (where media lands in P2).
 * "Tạo & đăng" creates then publishes, preserving the two-stage draft-survives recovery.
 */
export function AnnouncementCreatePage() {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const form = useAnnouncementForm();
  const create = useCreateAnnouncement();
  const publish = usePublishAnnouncement();
  // Which action is in flight, so each button shows its own correct busy label (not the other's).
  const [submitting, setSubmitting] = useState<null | 'draft' | 'publish'>(null);

  // Shared create path. When publishNow, publish after create — but if publish fails, the draft
  // survives: carry a notice to the edit page (where the admin can retry) rather than losing it.
  // The just-created draft is seeded into the detail cache so the edit page renders immediately
  // and keeps the notice even if a background refetch is slow/fails.
  const submit = async (publishNow: boolean) => {
    if (!form.validate()) return;
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
      // Draft saved → continue editing (media manager arrives here in P2).
      navigate(`/announcements/${created.id}/edit`);
    } catch (err: any) {
      form.setFormError(getVnErrorMessage(err?.response?.data?.error));
    } finally {
      setSubmitting(null);
    }
  };

  return (
    <div className="w-full">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Tạo thông báo</h1>
      </div>

      <AnnouncementComposeFields form={form} />

      {form.formError && <p className="mt-4 text-sm text-red-600 bg-red-50 px-3 py-2 rounded">{form.formError}</p>}

      <div className="flex gap-2 justify-end pt-6 mt-4 border-t border-gray-200">
        <button type="button" onClick={() => navigate('/announcements')} className="px-4 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50">Huỷ</button>
        <button type="button" onClick={() => submit(false)} disabled={submitting !== null} className="px-4 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50 disabled:opacity-50">
          {submitting === 'draft' ? 'Đang lưu...' : 'Lưu nháp'}
        </button>
        <button type="button" onClick={() => submit(true)} disabled={submitting !== null} className="px-4 py-2 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50">
          {submitting === 'publish' ? 'Đang đăng...' : 'Tạo & đăng'}
        </button>
      </div>
    </div>
  );
}
