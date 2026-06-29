import React, { useEffect, useRef, useState } from 'react';
import { getVnErrorMessage, formatVNDate } from '@gemek/ui';
import {
  useContractorDocuments,
  useUploadContractorDocument,
  useDeleteContractorDocument,
} from '../api/hooks';

/** One document row from GET /contractors/{id}/documents (ContractorDocumentResponse). */
export interface ContractorDocumentItem {
  id: string;
  displayFilename: string;
  contentType: string | null;
  sizeBytes: number | null;
  createdAt: string | null;
  /** Fresh presigned FORCED-DOWNLOAD URL (server-minted, Content-Disposition: attachment). */
  downloadUrl: string;
}

const MAX_DOCUMENTS = 5;
const MAX_FILE_BYTES = 10 * 1024 * 1024;
const MAX_TOTAL_BYTES = 50 * 1024 * 1024;
// accept hint only — the BE Tika magic-byte check is authoritative.
const ACCEPT = '.pdf,.docx,.xlsx,.pptx,.txt';

/**
 * Renders a human-readable size (B/KB/MB) for a document.
 *
 * <p>BOUNDARY-CORRECT (P3a requirement): the admin announcement formatSize rounds KB with toFixed(0),
 * so a size just under 1MB (e.g. 1,048,063 B) displays as "1024 KB" instead of rolling over to MB.
 * This formatter rounds the KB value first and, if the rounding pushes it to 1024, promotes to MB —
 * so KB never displays ≥1024. (The announcement formatSize bug stays OPEN; not touched this phase.)
 *
 * @param bytes the byte size, may be null.
 * @returns a short VN-friendly size label.
 */
function formatSize(bytes: number | null): string {
  if (bytes == null) return '';
  if (bytes < 1024) return `${bytes} B`;
  const kb = Math.round(bytes / 1024);
  // Rounding can push a sub-1MB value to 1024 KB — promote to MB so KB never displays ≥1024.
  if (kb < 1024) return `${kb} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/**
 * Document manager for the contractor EDIT page (P3a). Clones the C3 AnnouncementAttachmentsManager
 * mechanics — flat downloadable list, multi-file upload, inline size/count pre-checks, forced-download
 * anchors, VN error mapping, delete-confirm dialog — against the P1 contractor-document endpoints.
 *
 * <p>Mounted ONLY on the edit page where a contractor id already exists; the create page (/contractors/new)
 * shows no documents section this phase (P3b adds lazy-save there). Unlike the announcement attachments
 * (embedded in the parent's detail manifest), the list here comes from the manager's OWN query
 * (useContractorDocuments) — so it self-fetches on mount and refetches after each upload/delete.
 *
 * <p>Caps (≤5 files, ≤10MB/file, ≤50MB total) are enforced server-side and surfaced up front; the upload
 * button disables at the 5-file cap. The edit page is ADMIN-only, so this surfaces full upload/delete for
 * ADMIN; BOARD_MEMBER read-access stays API-only this phase (no FE surface).
 *
 * <p>P3b LAZY-SAVE: the create page (/contractors/new) mounts this same manager with `onLazyUpload` (and no
 * `contractorId`). In that mode the SAME pre-checks run, then the validated file is handed to the
 * orchestrator (which creates the contractor then uploads) instead of the internal mutation — so the caps
 * pre-check + 413 errorText live in exactly one place (no duplicate create-mode component, mirrors how the
 * C3 attachments manager is parameterized). With no id the list query stays idle and shows the empty state;
 * the create page redirects to /:id/edit on the first successful upload, so the list never renders there.
 */
export function ContractorDocumentsManager({
  contractorId,
  onLazyUpload,
  externalBusy,
}: {
  contractorId?: string;
  onLazyUpload?: (file: File) => Promise<void>;
  externalBusy?: boolean;
}) {
  const { data, isLoading, isError } = useContractorDocuments(contractorId ?? '');
  const upload = useUploadContractorDocument();
  const remove = useDeleteContractorDocument();
  const fileRef = useRef<HTMLInputElement>(null);
  const [error, setError] = useState('');
  const [pendingDelete, setPendingDelete] = useState<ContractorDocumentItem | null>(null);

  const documents: ContractorDocumentItem[] = Array.isArray(data) ? data : [];
  const atLimit = documents.length >= MAX_DOCUMENTS;
  // externalBusy folds in the create page's lazy create+upload so the trigger disables while it runs.
  const busy = upload.isPending || remove.isPending || !!externalBusy;

  // Dismiss the delete-confirm dialog on Escape (the backdrop already dismisses on click).
  useEffect(() => {
    if (!pendingDelete) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setPendingDelete(null); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [pendingDelete]);

  // Defense-in-depth: downloadUrl is a server-minted presigned URL, but assert an http(s) scheme before
  // binding it to an anchor href so a non-http value can never reach the DOM as a clickable
  // javascript:/data: vector (mirrors the C3 manager's ^https?:// guard).
  const safeHref = (url: string) => (/^https?:\/\//i.test(url) ? url : undefined);

  // Maps a request failure to a VN message. SANCTIONED DIVERGENCE from C3 (DECISIONS 2026-06-28): the
  // contractor per-file cap returns HTTP 413 CONTRACTOR_DOCUMENT_TOO_LARGE and the servlet multipart
  // limit returns HTTP 413 PAYLOAD_TOO_LARGE — both map to ONE "tệp quá lớn" message, keyed off the 413
  // status (covering both codes regardless of body). Everything else (Tika type reject / caps) carries a
  // coded VN message via getVnErrorMessage.
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
    // Pre-validate BEFORE uploading so an oversize file is never sent (instant feedback + avoids a wasted
    // upload that the servlet limit would abort mid-stream). Caps mirror the BE (10MB/file, 50MB total).
    if (file.size > MAX_FILE_BYTES) {
      setError('Tệp quá lớn (tối đa 10MB mỗi tệp).');
      return;
    }
    const currentTotal = documents.reduce((sum, d) => sum + (d.sizeBytes ?? 0), 0);
    if (currentTotal + file.size > MAX_TOTAL_BYTES) {
      setError('Tổng dung lượng tệp vượt quá 50MB.');
      return;
    }
    try {
      // Lazy mode: hand the validated file to the create-page orchestrator (create-then-upload). It owns
      // create-failure/upload-failure surfacing + the redirect, so on success this manager unmounts. Eager
      // mode (edit page): upload directly against the known id (`!` safe — edit always passes contractorId).
      if (onLazyUpload) {
        await onLazyUpload(file);
      } else {
        await upload.mutateAsync({ id: contractorId!, file });
      }
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
      // `!` safe: delete only renders for existing rows, which exist only in eager (edit) mode where
      // contractorId is always present — lazy (create) mode shows no rows, so this path is unreachable there.
      await remove.mutateAsync({ id: contractorId!, documentId: item.id });
    } catch (err: any) {
      setError(errorText(err));
    }
  };

  return (
    <div className="border border-gray-200 rounded-md p-4 bg-white space-y-3 mt-6">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold text-gray-500 uppercase tracking-wide">Tài liệu hợp đồng</h2>
        <span className="text-xs text-gray-400">{documents.length}/{MAX_DOCUMENTS} tệp</span>
      </div>

      <p className="text-xs text-gray-500">Tối đa 5 tệp, mỗi tệp ≤10MB, tổng ≤50MB. Định dạng: PDF, Word, Excel, PowerPoint, TXT.</p>

      {/* ── Upload control: ONE button ── */}
      <div className="flex flex-wrap items-center gap-3">
        <input ref={fileRef} type="file" accept={ACCEPT} onChange={onPick} className="hidden" aria-hidden="true" tabIndex={-1} />
        <button
          type="button"
          onClick={() => fileRef.current?.click()}
          // Disable while the list is loading/errored too: the total-size pre-check sums the loaded list,
          // so admitting an upload against an unknown list could let a file past the 50MB-total guard only
          // for the BE to reject it. Block until the list is known.
          disabled={atLimit || busy || isLoading || isError}
          aria-label="Tải lên tài liệu"
          className="px-3 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50 disabled:opacity-50"
        >
          {/* externalBusy covers lazy (create-page) upload, where the actual mutation runs on the page,
              not this manager's own `upload` — so the in-progress label still shows. */}
          {(upload.isPending || externalBusy) ? 'Đang tải lên...' : 'Tải lên tài liệu'}
        </button>
        {atLimit && <span className="text-xs text-amber-600">Đã đạt giới hạn 5 tệp — xoá bớt để thêm tệp mới.</span>}
      </div>

      {error && <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded">{error}</p>}

      {/* ── List: documents (forced-download) ── */}
      {isLoading ? (
        <p className="text-sm text-gray-400 italic">Đang tải danh sách tài liệu...</p>
      ) : isError ? (
        <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded">Không tải được danh sách tài liệu.</p>
      ) : documents.length === 0 ? (
        <p className="text-sm text-gray-400 italic">Chưa có tài liệu nào.</p>
      ) : (
        <ul className="divide-y divide-gray-100 border border-gray-200 rounded-md">
          {documents.map((d) => (
            <li key={d.id} className="flex items-center gap-3 px-3 py-2">
              <svg className="w-5 h-5 flex-shrink-0 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="1.5" aria-hidden="true">
                <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m2.25 0H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9z" />
              </svg>
              <span className="flex-1 min-w-0 truncate text-sm text-gray-800" title={d.displayFilename}>{d.displayFilename}</span>
              {formatVNDate(d.createdAt) && <span className="flex-shrink-0 text-xs text-gray-400">{formatVNDate(d.createdAt)}</span>}
              <span className="flex-shrink-0 text-xs text-gray-400">{formatSize(d.sizeBytes)}</span>
              {/* Forced-download is set server-side (Content-Disposition: attachment) — a plain anchor downloads.
                  The href is scheme-guarded; a non-http value renders an inert (disabled) control, never a link.
                  No client-side `download` attribute — the signed Content-Disposition drives the filename. */}
              {safeHref(d.downloadUrl) ? (
                <a
                  href={safeHref(d.downloadUrl)}
                  target="_blank"
                  rel="noopener noreferrer"
                  aria-label={`Tải về ${d.displayFilename}`}
                  className="flex-shrink-0 px-2 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50"
                >
                  Tải về
                </a>
              ) : (
                <span className="flex-shrink-0 px-2 py-1 text-xs border border-gray-200 rounded text-gray-300" aria-disabled="true">Tải về</span>
              )}
              <button
                type="button"
                onClick={() => setPendingDelete(d)}
                disabled={busy}
                aria-label={`Xoá tài liệu ${d.displayFilename}`}
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
          <div role="dialog" aria-modal="true" aria-labelledby="contractor-doc-delete-title" className="relative bg-white rounded-lg shadow-xl w-full max-w-sm p-6">
            <h3 id="contractor-doc-delete-title" className="text-lg font-semibold mb-2">Xoá tài liệu này?</h3>
            <p className="text-sm text-gray-600 mb-6 break-words">
              "{pendingDelete.displayFilename}" sẽ bị xoá khỏi nhà thầu. Thao tác này không thể hoàn tác.
            </p>
            <div className="flex gap-2 justify-end">
              <button type="button" onClick={() => setPendingDelete(null)} className="px-4 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50">Huỷ</button>
              <button type="button" onClick={confirmDelete} className="px-4 py-2 text-sm bg-red-600 text-white rounded-md hover:bg-red-700">Xoá tài liệu</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
