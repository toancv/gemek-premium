import React, { useState } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { useAnnouncement, useUpdateAnnouncement, usePublishAnnouncement } from '../api/hooks';
import { getVnErrorMessage } from '@gemek/ui';
import { useAnnouncementForm, AnnouncementComposeFields } from '../components/AnnouncementComposer';

/**
 * Loader/guard wrapper for the edit page. Resolves the draft before mounting the form so the
 * form's initial values are correct on first render (no async reset). Blocks editing of an
 * already-published announcement (BE PUT is draft-only) and handles not-found gracefully.
 */
export function AnnouncementEditPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data, isLoading, isError } = useAnnouncement(id!);

  if (isLoading) {
    return (
      <div className="min-h-[16rem] flex items-center justify-center">
        <svg className="animate-spin h-8 w-8 text-blue-600" fill="none" viewBox="0 0 24 24">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
        </svg>
      </div>
    );
  }

  if (isError || !data) {
    return (
      <div className="w-full">
        <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 mb-4">Không tìm thấy thông báo.</div>
        <button type="button" onClick={() => navigate('/announcements')} className="px-4 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50">Quay lại danh sách</button>
      </div>
    );
  }

  // Published announcements are immutable (PUT is draft-only) — block editing, offer a way back.
  if (data.publishedAt) {
    return (
      <div className="w-full">
        <h1 className="text-2xl font-bold text-gray-900 mb-4">{data.title}</h1>
        <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-4 text-yellow-800 mb-4">
          Thông báo đã được đăng nên không thể chỉnh sửa.
        </div>
        <button type="button" onClick={() => navigate('/announcements')} className="px-4 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50">Quay lại danh sách</button>
      </div>
    );
  }

  // key on the draft id so navigating between two /:id/edit URLs remounts the form with fresh
  // initial values rather than reusing the previous draft's state.
  return <AnnouncementEditForm key={data.id} announcement={data} />;
}

/**
 * The actual edit form, mounted only once a DRAFT is loaded. "Lưu" updates and stays on the page;
 * "Đăng" saves the current edits first (so publish never ships a stale draft) then publishes,
 * preserving the two-stage draft-survives recovery if publish fails. Publish is gated behind a
 * confirm dialog — the broadcast to all residents is irreversible (same guard as the list page).
 */
function AnnouncementEditForm({ announcement }: { announcement: any }) {
  const navigate = useNavigate();
  const location = useLocation();
  const update = useUpdateAnnouncement();
  const publish = usePublishAnnouncement();
  // A notice carried over from the create page's publish-failure recovery (draft survived).
  const [notice, setNotice] = useState<string>((location.state as any)?.notice ?? '');
  // Which action is in flight (correct per-button busy label); + the publish-confirm gate.
  const [submitting, setSubmitting] = useState<null | 'save' | 'publish'>(null);
  const [confirmPublish, setConfirmPublish] = useState(false);

  const form = useAnnouncementForm({
    title: announcement.title,
    content: announcement.content,
    type: announcement.type,
    scope: announcement.targetScope,
    blockId: announcement.targetBlock?.id ?? '',
    floor: announcement.targetFloor != null ? String(announcement.targetFloor) : '',
  });

  const save = async () => {
    if (!form.validate()) return;
    setNotice('');
    setSubmitting('save');
    try {
      await update.mutateAsync({ id: announcement.id, data: form.toPayload() });
    } catch (err: any) {
      form.setFormError(getVnErrorMessage(err?.response?.data?.error));
    } finally {
      setSubmitting(null);
    }
  };

  // Confirmed publish: persist current edits before publishing so the published copy reflects the
  // editor state, then publish. If publish fails the draft survives — keep the admin here to retry.
  const confirmAndPublish = async () => {
    if (!form.validate()) { setConfirmPublish(false); return; }
    setNotice('');
    setConfirmPublish(false);
    setSubmitting('publish');
    try {
      await update.mutateAsync({ id: announcement.id, data: form.toPayload() });
    } catch (err: any) {
      form.setFormError(getVnErrorMessage(err?.response?.data?.error));
      setSubmitting(null);
      return;
    }
    try {
      await publish.mutateAsync(announcement.id);
    } catch (pubErr: any) {
      form.setFormError('Đã lưu chỉnh sửa nhưng xuất bản thất bại: ' + getVnErrorMessage(pubErr?.response?.data?.error));
      setSubmitting(null);
      return;
    }
    navigate('/announcements');
  };

  return (
    <div className="w-full">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Chỉnh sửa thông báo</h1>
      </div>

      {notice && <div className="mb-4 bg-yellow-50 border border-yellow-200 rounded-lg p-4 text-yellow-800">{notice}</div>}

      <AnnouncementComposeFields form={form} />

      {form.formError && <p className="mt-4 text-sm text-red-600 bg-red-50 px-3 py-2 rounded">{form.formError}</p>}

      <div className="flex gap-2 justify-end pt-6 mt-4 border-t border-gray-200">
        <button type="button" onClick={() => navigate('/announcements')} className="px-4 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50">Huỷ</button>
        <button type="button" onClick={save} disabled={submitting !== null} className="px-4 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50 disabled:opacity-50">
          {submitting === 'save' ? 'Đang lưu...' : 'Lưu'}
        </button>
        <button type="button" onClick={() => { form.setFormError(''); setConfirmPublish(true); }} disabled={submitting !== null} className="px-4 py-2 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50">
          {submitting === 'publish' ? 'Đang đăng...' : 'Đăng'}
        </button>
      </div>

      {confirmPublish && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/50" onClick={() => setConfirmPublish(false)} />
          <div className="relative bg-white rounded-lg shadow-xl w-full max-w-sm p-6">
            <h2 className="text-lg font-semibold mb-2">Đăng thông báo tới toàn bộ cư dân?</h2>
            <p className="text-sm text-gray-600 mb-6">Chỉnh sửa sẽ được lưu rồi đăng tới tất cả cư dân và không thể thu hồi.</p>
            <div className="flex gap-2 justify-end">
              <button type="button" onClick={() => setConfirmPublish(false)} className="px-4 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50">Huỷ</button>
              <button type="button" onClick={confirmAndPublish} className="px-4 py-2 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700">Đăng thông báo</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
