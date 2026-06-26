import React, { useRef, useState } from 'react';
import { useBlocks } from '../api/hooks';
import { labelFor, MarkdownContent } from '@gemek/ui';
import type { AnnouncementMediaManifestEntry } from '@gemek/ui';

// Announcement type options — kept in sync with the BE AnnouncementType enum.
export const ANNOUNCEMENT_TYPES = ['GENERAL', 'URGENT', 'MAINTENANCE', 'AMENITY', 'EVENT'];

/** Editable announcement form fields, held as form-local strings (parsed on submit). */
export interface AnnouncementFormValue {
  title: string;
  content: string;
  type: string;
  scope: string;
  blockId: string;
  floor: string;
}

const EMPTY: AnnouncementFormValue = {
  title: '',
  content: '',
  type: ANNOUNCEMENT_TYPES[0],
  scope: 'ALL',
  blockId: '',
  floor: '',
};

/**
 * Owns all announcement compose state + the cursor-aware Markdown toolbar mechanism.
 * Shared by the create (/new) and edit (/:id/edit) pages so the form, validation, and
 * the textarea selection-restore logic live in exactly one place (no drift between pages).
 */
export function useAnnouncementForm(initial?: Partial<AnnouncementFormValue>) {
  const [title, setTitle] = useState(initial?.title ?? EMPTY.title);
  const [content, setContent] = useState(initial?.content ?? EMPTY.content);
  const [type, setType] = useState(initial?.type ?? EMPTY.type);
  const [scope, setScope] = useState(initial?.scope ?? EMPTY.scope);
  const [blockId, setBlockId] = useState(initial?.blockId ?? EMPTY.blockId);
  const [floor, setFloor] = useState(initial?.floor ?? EMPTY.floor);
  const [formError, setFormError] = useState('');
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  // Whether the textarea has ever held focus. Until it has, its selectionStart is 0, which would make
  // a toolbar/insert action prepend to the very start of the body — wrong for the media manager's
  // "Chèn vào bài" (a sibling component) when the author hasn't clicked into the editor yet. While
  // unfocused we append at the end instead. Set true on the textarea's onFocus (markFocused).
  const focusedRef = useRef(false);
  const markFocused = () => { focusedRef.current = true; };

  // Single splice primitive shared by the toolbar and the image insert: wraps the current selection
  // (or the placeholder when nothing is selected) as `before+selected+after`, keeps the textarea
  // controlled, and restores focus + selection via ref + requestAnimationFrame. `collapseAfter`
  // chooses the post-insert caret: false = re-select the inner text (toolbar — type-to-replace);
  // true = collapse the caret AFTER the whole snippet (image insert — see insertImage rationale).
  const spliceSelection = (before: string, after: string, placeholder: string, collapseAfter: boolean) => {
    const ta = textareaRef.current;
    // No live selection to anchor to (ref missing, or never focused) → append at the end.
    if (!ta || !focusedRef.current) { setContent((c) => c + before + placeholder + after); return; }
    const start = ta.selectionStart;
    const end = ta.selectionEnd;
    const selected = content.slice(start, end) || placeholder;
    const inserted = before + selected + after;
    const next = content.slice(0, start) + inserted + content.slice(end);
    setContent(next);
    requestAnimationFrame(() => {
      ta.focus();
      if (collapseAfter) {
        const pos = start + inserted.length;
        ta.setSelectionRange(pos, pos);
      } else {
        const pos = start + before.length;
        ta.setSelectionRange(pos, pos + selected.length);
      }
    });
  };

  // Toolbar formatting: wrap the selection and KEEP the inner text selected so the author can type
  // over the placeholder. Used for **bold**, headings, links, etc.
  const insertMarkdown = (before: string, after = '', placeholder = '') =>
    spliceSelection(before, after, placeholder, false);

  // Image insert (media manager "Chèn vào bài"): same wrap (a current selection becomes the alt — no
  // data loss) but COLLAPSE the caret AFTER the snippet. Leaving the alt selected (insertMarkdown's
  // behaviour) made a second consecutive image insert WRAP the first placeholder's still-selected alt,
  // producing nested markdown `![![…](…b)](…a)` whose inner image flattens into the outer alt (only
  // one renders). Collapsing the caret makes a second insert append a sibling placeholder instead.
  const insertImage = (before: string, after = '', placeholder = '') =>
    spliceSelection(before, after, placeholder, true);

  // Validates the same three rules the original modal enforced, plus an upper bound on floor:
  // the BE targetFloor column is a Short (max 32767), so an out-of-range value would otherwise
  // fail the save opaquely with a generic error rather than a clear field message.
  const validate = (): boolean => {
    setFormError('');
    if (!title.trim() || !content.trim()) { setFormError('Tiêu đề và nội dung là bắt buộc.'); return false; }
    if (scope !== 'ALL' && !blockId) { setFormError('Vui lòng chọn tòa.'); return false; }
    if (scope === 'FLOOR') {
      if (!floor.trim()) { setFormError('Vui lòng nhập số tầng.'); return false; }
      const n = parseInt(floor, 10);
      if (!Number.isInteger(n) || n < 1 || n > 32767) { setFormError('Số tầng không hợp lệ (1–32767).'); return false; }
    }
    return true;
  };

  // Builds the create/update request body. sendPush/Email/Sms stay hardcoded (invisible to the admin,
  // same as the retired modal) — surfacing them is out of scope.
  const toPayload = () => ({
    title: title.trim(),
    content: content.trim(),
    type,
    targetScope: scope,
    targetBlockId: scope !== 'ALL' ? blockId : null,
    targetFloor: scope === 'FLOOR' ? parseInt(floor, 10) : null,
    sendPush: true,
    sendEmail: false,
    sendSms: false,
  });

  return {
    title, setTitle,
    content, setContent,
    type, setType,
    scope, setScope,
    blockId, setBlockId,
    floor, setFloor,
    formError, setFormError,
    textareaRef,
    markFocused,
    insertMarkdown,
    insertImage,
    validate,
    toPayload,
  };
}

export type AnnouncementForm = ReturnType<typeof useAnnouncementForm>;

/** Stable empty-manifest identity for the default prop — avoids a fresh `[]` each render. */
const EMPTY_MANIFEST: AnnouncementMediaManifestEntry[] = [];

/**
 * The 2-column compose|preview body shared by both authoring pages: left = "Soạn" (form + toolbar +
 * textarea), right = "Xem trước" (live MarkdownContent). Stacks on narrow viewports. The edit page
 * passes the draft's `mediaManifest` (id→mediaId mapped) so inserted `announcement-media:{id}`
 * placeholders resolve to live INLINE images in the preview; /new passes none (no media before save).
 */
export function AnnouncementComposeFields({
  form,
  mediaManifest = EMPTY_MANIFEST,
}: {
  form: AnnouncementForm;
  mediaManifest?: AnnouncementMediaManifestEntry[];
}) {
  const { data: blocksData } = useBlocks();

  // Cover entry for the live preview banner (mirrors resident AnnouncementDetailPage): show the COVER
  // manifest item as a banner above the body. Defense-in-depth http(s) guard before binding to src so a
  // non-http manifest value can never reach the DOM. No cover → no banner (same as the resident page).
  const coverEntry = mediaManifest.find((m) => m.kind === 'COVER');
  const cover = coverEntry && /^https?:\/\//i.test(coverEntry.url) ? coverEntry : undefined;

  return (
    <div className="space-y-6">
      {/* ── Row-aligned 2-col mirror: left "Soạn" ↔ right "Xem trước", aligned row-by-row
            (cover-slot ↔ cover-slot, title ↔ title, body ↔ body). One grid so each grid row sizes to its
            taller cell, keeping the two columns in lockstep. items-start aligns cell content to the top.
            Mobile (single column): `order-*` regroups the cells into compose-first then preview-second
            (DOM order is column-pair order for the desktop row mirror); `lg:order-none` restores it. ── */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-x-6 lg:gap-x-8 gap-y-2 items-start">
        {/* Section headers (row 0) */}
        <h2 className="order-1 lg:order-none text-sm font-semibold text-gray-500 uppercase tracking-wide">Soạn</h2>
        <h2 className="order-5 lg:order-none text-sm font-semibold text-gray-500 uppercase tracking-wide">Xem trước</h2>

        {/* ROW 1 — cover slot (fixed admin-side height so title/body stay aligned regardless of cover) */}
        <div className="hidden lg:flex h-40 items-center justify-center text-center px-3 border border-dashed border-gray-200 rounded-md text-xs text-gray-400">
          Ảnh bìa quản lý ở Thư viện ảnh phía trên
        </div>
        {cover ? (
          <div className="order-6 lg:order-none h-40 rounded-md overflow-hidden bg-gray-100">
            <img src={cover.url} alt={form.title || 'Ảnh bìa'} loading="lazy" className="w-full h-full object-cover" />
          </div>
        ) : (
          <div className="order-6 lg:order-none h-40 flex items-center justify-center border border-gray-200 rounded-md bg-gray-50 text-sm text-gray-400 italic">
            Chưa có ảnh bìa
          </div>
        )}

        {/* ROW 2 — title (input on the left, preview title on the right) */}
        <div className="order-2 lg:order-none">
          <label className="block text-sm font-medium text-gray-700 mb-1">Tiêu đề <span className="text-red-500">*</span></label>
          <input value={form.title} onChange={(e) => form.setTitle(e.target.value)} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" />
        </div>
        <div className="order-7 lg:order-none px-1">
          {/* desktop spacer matches the left label height so the preview title aligns with the input, not the label */}
          <div className="hidden lg:block h-5 mb-1" aria-hidden="true" />
          {form.title.trim()
            ? <h1 className="font-semibold text-gray-900">{form.title}</h1>
            : <p className="text-sm text-gray-400 italic">Chưa có tiêu đề.</p>}
        </div>

        {/* ROW 3 — body (editor on the left, markdown render on the right). A matched-height header band
              on both cells (label + toolbar vs a spacer) keeps the textarea and the preview box tops aligned. */}
        <div className="order-3 lg:order-none flex flex-col">
          <div className="min-h-[3.75rem]">
            <label className="block text-sm font-medium text-gray-700 mb-1">Nội dung <span className="text-red-500">*</span></label>
            <div className="flex flex-wrap items-center gap-1">
              <span className="text-xs text-gray-500 mr-1">Định dạng:</span>
              <button type="button" onClick={() => form.insertMarkdown('**', '**', 'văn bản đậm')} className="px-2 py-1 text-xs font-bold border border-gray-300 rounded hover:bg-gray-50" title="Đậm">Đậm</button>
              <button type="button" onClick={() => form.insertMarkdown('*', '*', 'văn bản nghiêng')} className="px-2 py-1 text-xs italic border border-gray-300 rounded hover:bg-gray-50" title="Nghiêng">Nghiêng</button>
              <button type="button" onClick={() => form.insertMarkdown('## ', '', 'Tiêu đề')} className="px-2 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50" title="Tiêu đề">Tiêu đề</button>
              <button type="button" onClick={() => form.insertMarkdown('- ', '', 'Mục danh sách')} className="px-2 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50" title="Danh sách">Danh sách</button>
              <button type="button" onClick={() => form.insertMarkdown('[', '](https://)', 'văn bản liên kết')} className="px-2 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50" title="Liên kết">Liên kết</button>
            </div>
          </div>
          <textarea
            ref={form.textareaRef}
            value={form.content}
            onChange={(e) => form.setContent(e.target.value)}
            onFocus={form.markFocused}
            rows={20}
            className="block w-full min-h-[28rem] border border-gray-300 rounded-md px-3 py-2 text-sm font-mono resize-y"
            placeholder="Hỗ trợ Markdown: **đậm**, *nghiêng*, ## tiêu đề, - danh sách, [liên kết](https://...)"
          />
        </div>
        <div className="order-8 lg:order-none flex flex-col">
          {/* desktop spacer matches the left label+toolbar band; mobile groups under the row-0 "Xem trước" header */}
          <div className="hidden lg:block min-h-[3.75rem]" aria-hidden="true" />
          <div className="border border-gray-200 rounded-md p-4 bg-gray-50 min-h-[28rem]">
            {form.content.trim()
              ? <MarkdownContent content={form.content} className="text-sm text-gray-700" mediaManifest={mediaManifest} />
              : <p className="text-sm text-gray-400 italic">Chưa có nội dung.</p>}
          </div>
        </div>
      </div>

      {/* ── Type / scope / block / floor — compact selects in a wrapping row (not edge-to-edge) ── */}
      <div className="flex flex-wrap gap-4">
        <div className="w-full sm:w-44">
          <label className="block text-sm font-medium text-gray-700 mb-1">Loại</label>
          <select value={form.type} onChange={(e) => form.setType(e.target.value)} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm bg-white">
            {ANNOUNCEMENT_TYPES.map((at) => <option key={at} value={at}>{labelFor('AnnouncementType', at)}</option>)}
          </select>
        </div>
        <div className="w-full sm:w-44">
          <label className="block text-sm font-medium text-gray-700 mb-1">Phạm vi</label>
          <select value={form.scope} onChange={(e) => { form.setScope(e.target.value); form.setBlockId(''); form.setFloor(''); }} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm bg-white">
            <option value="ALL">{labelFor('AnnouncementScope', 'ALL')}</option>
            <option value="BLOCK">{labelFor('AnnouncementScope', 'BLOCK')}</option>
            <option value="FLOOR">{labelFor('AnnouncementScope', 'FLOOR')}</option>
          </select>
        </div>
        {form.scope !== 'ALL' && (
          <div className="w-full sm:w-56">
            <label className="block text-sm font-medium text-gray-700 mb-1">Tòa <span className="text-red-500">*</span></label>
            <select value={form.blockId} onChange={(e) => form.setBlockId(e.target.value)} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm bg-white">
              <option value="">-- Chọn tòa --</option>
              {blocksData?.data?.map((b: any) => (
                <option key={b.id} value={b.id}>{b.name}</option>
              ))}
            </select>
          </div>
        )}
        {form.scope === 'FLOOR' && (
          <div className="w-full sm:w-32">
            <label className="block text-sm font-medium text-gray-700 mb-1">Tầng <span className="text-red-500">*</span></label>
            <input type="number" min="1" value={form.floor} onChange={(e) => form.setFloor(e.target.value)} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" placeholder="Số tầng" />
          </div>
        )}
      </div>
    </div>
  );
}
