import React, { useRef, useState } from 'react';
import { getVnErrorMessage } from '@gemek/ui';
import { useUploadAnnouncementMedia, useDeleteAnnouncementMedia } from '../api/hooks';

/** One media row from the draft detail manifest (GET /announcements/{id} → media[]). */
export interface AnnouncementMediaItem {
  id: string;
  kind: 'COVER' | 'INLINE';
  /** Fresh presigned GET URL (ADMIN-on-draft is non-empty) — used as the thumbnail src. */
  url: string;
}

const MAX_IMAGES = 5;
const ACCEPT = 'image/jpeg,image/png,image/webp';

/**
 * Media manager for the announcement EDIT page (draft only). ONE grid lists every uploaded image
 * (thumbnails from the detail manifest); upload + delete are draft-only (C2.2). Cover is chosen at
 * upload time via TWO separate buttons — "Tải lên ảnh bìa" (kind=COVER) and "Tải lên ảnh bài viết"
 * (kind=INLINE) — no checkbox. Uploading a 2nd cover replaces the 1st in-tx on the BE (C2.2), so a
 * cover upload never increases the count when a cover already exists; the inline button is disabled
 * at the 5-image cap while the cover button stays available to allow that replace.
 *
 * - INLINE item → "Chèn vào bài" (drops `announcement-media:{id}` at the editor cursor via onInsert)
 *   + "Xoá".
 * - COVER item → COVER badge + "Xoá" only (cover renders as a banner, never inline — no insert).
 *
 * On a successful delete the parent strips the deleted id's placeholder from the body (onDeleted) so
 * a dangling insertion doesn't linger. After each upload/delete the parent's detail query refetches,
 * so the grid thumbnails and the preview's presigned URLs both refresh.
 */
export function AnnouncementMediaManager({
  announcementId,
  media,
  onInsert,
  onDeleted,
}: {
  announcementId: string;
  media: AnnouncementMediaItem[];
  onInsert: (mediaId: string) => void;
  onDeleted: (mediaId: string) => void;
}) {
  const upload = useUploadAnnouncementMedia();
  const remove = useDeleteAnnouncementMedia();
  const fileRef = useRef<HTMLInputElement>(null);
  // The kind the pending file-picker selection will be uploaded as, set by whichever button opened it.
  const kindRef = useRef<'COVER' | 'INLINE'>('INLINE');
  const [error, setError] = useState('');
  const [pendingDelete, setPendingDelete] = useState<AnnouncementMediaItem | null>(null);

  const atLimit = media.length >= MAX_IMAGES;
  // A cover already exists → a cover upload REPLACES it in-tx (net count unchanged), so it stays
  // allowed at the cap; a cover upload with no existing cover is net-new and must respect the cap.
  const hasCover = media.some((m) => m.kind === 'COVER');
  const busy = upload.isPending || remove.isPending;

  // Open the OS file picker, remembering which kind the chosen file should be uploaded as.
  const pick = (kind: 'COVER' | 'INLINE') => {
    kindRef.current = kind;
    fileRef.current?.click();
  };

  // Maps a request failure to a VN message. A servlet per-file size overflow returns HTTP 413 with NO
  // `error` code (it never reaches the app handler), so getVnErrorMessage would collapse it to the
  // generic fallback — special-case it to a clear size message so the admin doesn't retry the same
  // oversized file. Everything else (Tika type reject / caps / NOT_DRAFT) carries a coded VN message.
  const errorText = (err: any): string =>
    err?.response?.status === 413
      ? 'Ảnh quá lớn (tối đa 10MB mỗi ảnh).'
      : getVnErrorMessage(err?.response?.data?.error);

  const onPick = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    // Reset the input value so re-selecting the same file still fires onChange.
    e.target.value = '';
    if (!file) return;
    setError('');
    try {
      await upload.mutateAsync({ id: announcementId, file, kind: kindRef.current });
    } catch (err: any) {
      setError(errorText(err));
    }
  };

  const confirmDelete = async () => {
    if (!pendingDelete) return;
    const item = pendingDelete;
    setPendingDelete(null);
    setError('');
    try {
      await remove.mutateAsync({ id: announcementId, mediaId: item.id });
      // Strip the deleted image's placeholder from the body so a dangling insertion doesn't linger
      // (a COVER id never appears in the body → no-op for cover deletes).
      onDeleted(item.id);
    } catch (err: any) {
      setError(errorText(err));
    }
  };

  return (
    <div className="border border-gray-200 rounded-md p-4 bg-white space-y-3">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold text-gray-500 uppercase tracking-wide">Thư viện ảnh</h2>
        <span className="text-xs text-gray-400">{media.length}/{MAX_IMAGES} ảnh</span>
      </div>

      <p className="text-xs text-gray-500">Tối đa 5 ảnh, tổng dung lượng tối đa 50MB. Định dạng: JPG, PNG, WebP.</p>

      {/* ── Upload control: two kind-specific buttons (cover stays enabled at cap to allow replace) ── */}
      <div className="flex flex-wrap items-center gap-3">
        <input ref={fileRef} type="file" accept={ACCEPT} onChange={onPick} className="hidden" />
        <button
          type="button"
          onClick={() => pick('COVER')}
          disabled={busy || (atLimit && !hasCover)}
          className="px-3 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50 disabled:opacity-50"
        >
          {upload.isPending && kindRef.current === 'COVER' ? 'Đang tải lên...' : 'Tải lên ảnh bìa'}
        </button>
        <button
          type="button"
          onClick={() => pick('INLINE')}
          disabled={atLimit || busy}
          className="px-3 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50 disabled:opacity-50"
        >
          {upload.isPending && kindRef.current === 'INLINE' ? 'Đang tải lên...' : 'Tải lên ảnh bài viết'}
        </button>
        {atLimit && <span className="text-xs text-amber-600">Đã đạt giới hạn 5 ảnh — xoá bớt để thêm ảnh bài viết{hasCover ? ' (ảnh bìa vẫn có thể thay)' : ''}.</span>}
      </div>

      {error && <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded">{error}</p>}

      {/* ── Grid: every uploaded image (thumbnails from the detail manifest) ── */}
      {media.length === 0 ? (
        <p className="text-sm text-gray-400 italic">Chưa có ảnh nào.</p>
      ) : (
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3">
          {media.map((m) => (
            <div key={m.id} className="border border-gray-200 rounded-md overflow-hidden flex flex-col">
              <div className="relative aspect-square bg-gray-100">
                <img src={m.url} alt="" loading="lazy" className="absolute inset-0 w-full h-full object-cover" />
                {m.kind === 'COVER' && (
                  <span className="absolute top-1 left-1 px-1.5 py-0.5 text-[10px] font-semibold bg-blue-600 text-white rounded">BÌA</span>
                )}
              </div>
              <div className="flex gap-1 p-1.5">
                {m.kind === 'INLINE' && (
                  <button
                    type="button"
                    onClick={() => onInsert(m.id)}
                    disabled={busy}
                    className="flex-1 px-2 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50 disabled:opacity-50"
                  >
                    Chèn vào bài
                  </button>
                )}
                <button
                  type="button"
                  onClick={() => setPendingDelete(m)}
                  disabled={busy}
                  className="px-2 py-1 text-xs border border-red-300 text-red-600 rounded hover:bg-red-50 disabled:opacity-50"
                >
                  Xoá
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* ── Delete confirm dialog ──────────────────────────────────── */}
      {pendingDelete && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/50" onClick={() => setPendingDelete(null)} />
          <div className="relative bg-white rounded-lg shadow-xl w-full max-w-sm p-6">
            <h3 className="text-lg font-semibold mb-2">Xoá ảnh này?</h3>
            <p className="text-sm text-gray-600 mb-6">
              Ảnh sẽ bị xoá khỏi thông báo, và đoạn chèn ảnh này trong nội dung (nếu có) cũng sẽ được gỡ bỏ.
              Nhớ bấm "Lưu" để lưu lại thay đổi nội dung.
            </p>
            <div className="flex gap-2 justify-end">
              <button type="button" onClick={() => setPendingDelete(null)} className="px-4 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50">Huỷ</button>
              <button type="button" onClick={confirmDelete} className="px-4 py-2 text-sm bg-red-600 text-white rounded-md hover:bg-red-700">Xoá ảnh</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
