import React, { useRef, useState } from 'react';
import { useBlocks } from '../api/hooks';
import { labelFor, MarkdownContent } from '@gemek/ui';

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

  // Wraps the current selection (or inserts a placeholder) with Markdown syntax, keeping
  // the textarea controlled and restoring focus/selection after the state update. This exact
  // ref + requestAnimationFrame mechanism is what P2's insert-image will reuse — do not regress it.
  const insertMarkdown = (before: string, after = '', placeholder = '') => {
    const ta = textareaRef.current;
    if (!ta) { setContent((c) => c + before + placeholder + after); return; }
    const start = ta.selectionStart;
    const end = ta.selectionEnd;
    const selected = content.slice(start, end) || placeholder;
    const next = content.slice(0, start) + before + selected + after + content.slice(end);
    setContent(next);
    requestAnimationFrame(() => {
      ta.focus();
      const pos = start + before.length;
      ta.setSelectionRange(pos, pos + selected.length);
    });
  };

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
    insertMarkdown,
    validate,
    toPayload,
  };
}

export type AnnouncementForm = ReturnType<typeof useAnnouncementForm>;

/**
 * The 2-column compose|preview body shared by both authoring pages: left = "Soạn" (form + toolbar +
 * textarea), right = "Xem trước" (live MarkdownContent). Stacks on narrow viewports. NO mediaManifest
 * yet (P2) — placeholder images won't resolve in the preview, same as the retired modal.
 */
export function AnnouncementComposeFields({ form }: { form: AnnouncementForm }) {
  const { data: blocksData } = useBlocks();

  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 lg:gap-8">
      {/* ── Left: compose ─────────────────────────────────────────── */}
      <div className="space-y-3">
        <h2 className="text-sm font-semibold text-gray-500 uppercase tracking-wide">Soạn</h2>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Tiêu đề <span className="text-red-500">*</span></label>
          <input value={form.title} onChange={(e) => form.setTitle(e.target.value)} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Nội dung <span className="text-red-500">*</span></label>
          <div className="flex flex-wrap items-center gap-1 mb-1">
            <span className="text-xs text-gray-500 mr-1">Định dạng:</span>
            <button type="button" onClick={() => form.insertMarkdown('**', '**', 'văn bản đậm')} className="px-2 py-1 text-xs font-bold border border-gray-300 rounded hover:bg-gray-50" title="Đậm">Đậm</button>
            <button type="button" onClick={() => form.insertMarkdown('*', '*', 'văn bản nghiêng')} className="px-2 py-1 text-xs italic border border-gray-300 rounded hover:bg-gray-50" title="Nghiêng">Nghiêng</button>
            <button type="button" onClick={() => form.insertMarkdown('## ', '', 'Tiêu đề')} className="px-2 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50" title="Tiêu đề">Tiêu đề</button>
            <button type="button" onClick={() => form.insertMarkdown('- ', '', 'Mục danh sách')} className="px-2 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50" title="Danh sách">Danh sách</button>
            <button type="button" onClick={() => form.insertMarkdown('[', '](https://)', 'văn bản liên kết')} className="px-2 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50" title="Liên kết">Liên kết</button>
          </div>
          <textarea
            ref={form.textareaRef}
            value={form.content}
            onChange={(e) => form.setContent(e.target.value)}
            rows={20}
            className="block w-full min-h-[28rem] border border-gray-300 rounded-md px-3 py-2 text-sm font-mono resize-y"
            placeholder="Hỗ trợ Markdown: **đậm**, *nghiêng*, ## tiêu đề, - danh sách, [liên kết](https://...)"
          />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Loại</label>
            <select value={form.type} onChange={(e) => form.setType(e.target.value)} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm bg-white">
              {ANNOUNCEMENT_TYPES.map((at) => <option key={at} value={at}>{labelFor('AnnouncementType', at)}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Phạm vi</label>
            <select value={form.scope} onChange={(e) => { form.setScope(e.target.value); form.setBlockId(''); form.setFloor(''); }} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm bg-white">
              <option value="ALL">{labelFor('AnnouncementScope', 'ALL')}</option>
              <option value="BLOCK">{labelFor('AnnouncementScope', 'BLOCK')}</option>
              <option value="FLOOR">{labelFor('AnnouncementScope', 'FLOOR')}</option>
            </select>
          </div>
        </div>
        {form.scope !== 'ALL' && (
          <div>
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
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Tầng <span className="text-red-500">*</span></label>
            <input type="number" min="1" value={form.floor} onChange={(e) => form.setFloor(e.target.value)} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" placeholder="Số tầng" />
          </div>
        )}
      </div>

      {/* ── Right: live preview (same safe renderer the resident app uses) ── */}
      <div className="space-y-3">
        <h2 className="text-sm font-semibold text-gray-500 uppercase tracking-wide">Xem trước</h2>
        <div className="border border-gray-200 rounded-md p-4 bg-gray-50 min-h-[28rem]">
          {form.content.trim()
            ? <MarkdownContent content={form.content} className="text-sm text-gray-700" />
            : <p className="text-sm text-gray-400 italic">Chưa có nội dung.</p>}
        </div>
      </div>
    </div>
  );
}
