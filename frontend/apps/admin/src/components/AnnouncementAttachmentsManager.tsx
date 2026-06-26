import React, { useEffect, useRef, useState } from 'react';
import { getVnErrorMessage } from '@gemek/ui';
import { useUploadAnnouncementAttachment, useDeleteAnnouncementAttachment } from '../api/hooks';

/** One attachment row from the draft detail manifest (GET /announcements/{id} → attachments[]). */
export interface AnnouncementAttachmentItem {
  id: string;
  displayFilename: string;
  sizeBytes: number | null;
  /** Fresh presigned FORCED-DOWNLOAD URL (ADMIN-on-draft is non-empty). */
  downloadUrl: string;
}

const MAX_ATTACHMENTS = 5;
const MAX_FILE_BYTES = 10 * 1024 * 1024;
const MAX_TOTAL_BYTES = 50 * 1024 * 1024;
// accept hint only — the BE Tika magic-byte check is authoritative.
const ACCEPT = '.pdf,.docx,.xlsx,.pptx,.txt';

/**
 * Renders a human-readable size (KB/MB) for an attachment.
 *
 * @param bytes the byte size, may be null.
 * @returns a short VN-friendly size label.
 */
function formatSize(bytes: number | null): string {
  if (bytes == null) return '';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/**
 * Document-attachment manager for the announcement EDIT page (draft only, C3 P2). A FLAT downloadable
 * list — NO cover/inline kind and NO body placeholder/insert (unlike the image media manager): documents
 * are downloaded, never rendered inline. Upload + delete are draft-only (C3 BE). The list (filename +
 * size + download) comes from the parent's detail manifest (`attachments[]`, carries the forced-download
 * URL); after each upload/delete the detail query refetches so the list and its presigned URLs refresh.
 *
 * <p>Caps (≤5 files, ≤10MB/file, ≤50MB total) are enforced server-side and INDEPENDENT of the image caps;
 * the FE surfaces them up front and disables the upload button at the 5-file cap. Id-driven so a later
 * phase (P2.5 lazy-save on /new) can mount it once a draft id exists, without changing this component.
 */
export function AnnouncementAttachmentsManager({
  announcementId,
  attachments,
  onLazyUpload,
  externalBusy = false,
}: {
  announcementId: string;
  attachments: AnnouncementAttachmentItem[];
  /**
   * Lazy-save hook for the create page (/new), where no draft id exists yet. When provided, a picked
   * file (already size-pre-checked below) is handed to the parent's create-then-upload orchestrator
   * instead of uploaded directly. Absent on the edit page → the existing direct-upload path runs.
   */
  onLazyUpload?: (file: File) => void | Promise<void>;
  /** External busy flag (e.g. a lazy-save in flight on /new) — ORed into the local busy disable. */
  externalBusy?: boolean;
}) {
  const upload = useUploadAnnouncementAttachment();
  const remove = useDeleteAnnouncementAttachment();
  const fileRef = useRef<HTMLInputElement>(null);
  const [error, setError] = useState('');
  const [pendingDelete, setPendingDelete] = useState<AnnouncementAttachmentItem | null>(null);

  const atLimit = attachments.length >= MAX_ATTACHMENTS;
  const busy = upload.isPending || remove.isPending || externalBusy;

  // Dismiss the delete-confirm dialog on Escape (the backdrop already dismisses on click).
  useEffect(() => {
    if (!pendingDelete) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setPendingDelete(null); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [pendingDelete]);

  // Defense-in-depth: downloadUrl is a server-minted presigned URL, but assert an http(s) scheme before
  // binding it to an anchor href so a non-http manifest value can never reach the DOM as a clickable
  // javascript:/data: vector (mirrors the resident detail page's ^https?:// guard).
  const safeHref = (url: string) => (/^https?:\/\//i.test(url) ? url : undefined);

  // Maps a request failure to a VN message. A servlet per-file size overflow returns HTTP 413 with NO
  // `error` code (it never reaches the app handler), so special-case it; everything else (Tika type
  // reject / caps / NOT_DRAFT) carries a coded VN message. Mirrors AnnouncementMediaManager.
  const errorText = (err: any): string =>
    err?.response?.status === 413
      ? 'Tệp quá lớn (tối đa 10MB mỗi tệp).'
      : getVnErrorMessage(err?.response?.data?.error);

  const onPick = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    // Reset the input value so re-selecting the same file still fires onChange.
    e.target.value = '';
    if (!file) return;
    setError('');
    // Pre-validate BEFORE uploading so an oversize file is never sent — a >10MB multipart upload would
    // otherwise be aborted mid-stream by the servlet limit (clean 413 on the server now, but the FE
    // pre-check is instant and avoids the wasted upload + any streaming-reset edge). Caps are independent
    // of the image manager's and mirror the BE (10MB/file, 50MB total, 5 files).
    if (file.size > MAX_FILE_BYTES) {
      setError('Tệp quá lớn (tối đa 10MB mỗi tệp).');
      return;
    }
    const currentTotal = attachments.reduce((sum, a) => sum + (a.sizeBytes ?? 0), 0);
    if (currentTotal + file.size > MAX_TOTAL_BYTES) {
      setError('Tổng dung lượng tệp đính kèm vượt quá 50MB.');
      return;
    }
    // /new lazy-save: the size pre-checks above have already passed, so hand the file to the parent's
    // create-then-upload orchestrator (validate form → create draft → upload → navigate to edit). The
    // orchestrator owns its own errors (form error / edit-page notice), so nothing to catch here.
    if (onLazyUpload) {
      await onLazyUpload(file);
      return;
    }
    try {
      await upload.mutateAsync({ id: announcementId, file });
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
      await remove.mutateAsync({ id: announcementId, attachmentId: item.id });
    } catch (err: any) {
      setError(errorText(err));
    }
  };

  return (
    <div className="border border-gray-200 rounded-md p-4 bg-white space-y-3">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold text-gray-500 uppercase tracking-wide">Tệp đính kèm</h2>
        <span className="text-xs text-gray-400">{attachments.length}/{MAX_ATTACHMENTS} tệp</span>
      </div>

      <p className="text-xs text-gray-500">Tối đa 5 tệp, mỗi tệp ≤10MB, tổng ≤50MB. Định dạng: PDF, Word, Excel, PowerPoint, TXT.</p>

      {/* ── Upload control: ONE button (no kind) ── */}
      <div className="flex flex-wrap items-center gap-3">
        <input ref={fileRef} type="file" accept={ACCEPT} onChange={onPick} className="hidden" aria-hidden="true" tabIndex={-1} />
        <button
          type="button"
          onClick={() => fileRef.current?.click()}
          disabled={atLimit || busy}
          aria-label="Tải lên tệp đính kèm"
          className="px-3 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50 disabled:opacity-50"
        >
          {upload.isPending ? 'Đang tải lên...' : 'Tải lên tệp đính kèm'}
        </button>
        {atLimit && <span className="text-xs text-amber-600">Đã đạt giới hạn 5 tệp — xoá bớt để thêm tệp mới.</span>}
      </div>

      {error && <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded">{error}</p>}

      {/* ── List: documents (NOT a thumbnail grid) ── */}
      {attachments.length === 0 ? (
        <p className="text-sm text-gray-400 italic">Chưa có tệp đính kèm nào.</p>
      ) : (
        <ul className="divide-y divide-gray-100 border border-gray-200 rounded-md">
          {attachments.map((a) => (
            <li key={a.id} className="flex items-center gap-3 px-3 py-2">
              <svg className="w-5 h-5 flex-shrink-0 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="1.5" aria-hidden="true">
                <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m2.25 0H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9z" />
              </svg>
              <span className="flex-1 min-w-0 truncate text-sm text-gray-800" title={a.displayFilename}>{a.displayFilename}</span>
              <span className="flex-shrink-0 text-xs text-gray-400">{formatSize(a.sizeBytes)}</span>
              {/* Forced-download is set server-side (Content-Disposition: attachment) — a plain anchor downloads.
                  The href is scheme-guarded; a non-http value renders an inert (disabled) control, never a link. */}
              {safeHref(a.downloadUrl) ? (
                <a
                  href={safeHref(a.downloadUrl)}
                  target="_blank"
                  rel="noopener noreferrer"
                  aria-label={`Tải về ${a.displayFilename}`}
                  className="flex-shrink-0 px-2 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50"
                >
                  Tải về
                </a>
              ) : (
                <span className="flex-shrink-0 px-2 py-1 text-xs border border-gray-200 rounded text-gray-300" aria-disabled="true">Tải về</span>
              )}
              <button
                type="button"
                onClick={() => setPendingDelete(a)}
                disabled={busy}
                aria-label={`Xoá tệp ${a.displayFilename}`}
                className="flex-shrink-0 px-2 py-1 text-xs border border-red-300 text-red-600 rounded hover:bg-red-50 disabled:opacity-50"
              >
                Xoá
              </button>
            </li>
          ))}
        </ul>
      )}

      {/* ── Delete confirm dialog ── */}
      {pendingDelete && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/50" onClick={() => setPendingDelete(null)} />
          <div role="dialog" aria-modal="true" aria-labelledby="attach-delete-title" className="relative bg-white rounded-lg shadow-xl w-full max-w-sm p-6">
            <h3 id="attach-delete-title" className="text-lg font-semibold mb-2">Xoá tệp đính kèm này?</h3>
            <p className="text-sm text-gray-600 mb-6 break-words">
              "{pendingDelete.displayFilename}" sẽ bị xoá khỏi thông báo. Thao tác này không thể hoàn tác.
            </p>
            <div className="flex gap-2 justify-end">
              <button type="button" onClick={() => setPendingDelete(null)} className="px-4 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50">Huỷ</button>
              <button type="button" onClick={confirmDelete} className="px-4 py-2 text-sm bg-red-600 text-white rounded-md hover:bg-red-700">Xoá tệp</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
