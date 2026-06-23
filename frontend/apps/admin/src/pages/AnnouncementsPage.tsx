import React, { useState } from 'react';
import { useAnnouncements, useCreateAnnouncement, usePublishAnnouncement, useBlocks } from '../api/hooks';
import { useRoleFlags } from '../lib/useRoleFlags';
import { getVnErrorMessage, labelFor, formatVNDate, MarkdownContent } from '@gemek/ui';

const TYPES = ['GENERAL','URGENT','MAINTENANCE','AMENITY','EVENT'];

export function AnnouncementsPage() {
  const [page, setPage] = useState(0);
  const [showCreate, setShowCreate] = useState(false);
  const [formError, setFormError] = useState('');
  const [scope, setScope] = useState('ALL');
  const [blockId, setBlockId] = useState('');
  const [floor, setFloor] = useState('');
  const [pendingPublishId, setPendingPublishId] = useState<string | null>(null);
  const [publishError, setPublishError] = useState('');
  // Markdown body is controlled so the toolbar can insert syntax at the cursor and the
  // live preview can render with the SAME safe renderer the resident app uses.
  const [content, setContent] = useState('');
  const [showPreview, setShowPreview] = useState(true);
  const textareaRef = React.useRef<HTMLTextAreaElement>(null);

  // Announcement writes (create + publish) are ADMIN-only on the BE (POST/publish = hasRole('ADMIN')).
  // BOARD_MEMBER now reads this page (route opened read-only — backlog (c) #7) but sees NO write
  // control: gate create + publish to ADMIN so opening the route does not create a new 403 mismatch.
  const { isAdmin } = useRoleFlags();

  const { data, isLoading, isError } = useAnnouncements({ page, size: 20 });
  const { data: blocksData } = useBlocks();
  const create = useCreateAnnouncement();
  const publish = usePublishAnnouncement();

  const resetForm = () => { setScope('ALL'); setBlockId(''); setFloor(''); setFormError(''); setContent(''); };

  // Wraps the current selection (or inserts a placeholder) with Markdown syntax, keeping
  // the textarea controlled and restoring focus/selection after the state update.
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

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError('');
    const fd = new FormData(e.target as HTMLFormElement);
    const title = (fd.get('title') as string).trim();
    const content = (fd.get('content') as string).trim();
    const shouldPublish = fd.get('publishNow') === 'true';
    if (!title || !content) { setFormError('Tiêu đề và nội dung là bắt buộc.'); return; }
    if (scope !== 'ALL' && !blockId) { setFormError('Vui lòng chọn tòa.'); return; }
    if (scope === 'FLOOR' && !floor.trim()) { setFormError('Vui lòng nhập số tầng.'); return; }
    try {
      const created: any = await create.mutateAsync({
        title,
        content,
        type: fd.get('type'),
        targetScope: scope,
        targetBlockId: scope !== 'ALL' ? blockId : null,
        targetFloor: scope === 'FLOOR' ? parseInt(floor, 10) : null,
        sendPush: true,
        sendEmail: false,
        sendSms: false,
      });
      if (shouldPublish) {
        try {
          await publish.mutateAsync(created.id);
        } catch (pubErr: any) {
          setFormError('Thông báo đã được tạo nhưng xuất bản thất bại: ' + getVnErrorMessage(pubErr?.response?.data?.error));
          return;
        }
      }
      setShowCreate(false);
      resetForm();
    } catch (err: any) { setFormError(getVnErrorMessage(err?.response?.data?.error)); }
  };

  const handleConfirmPublish = async () => {
    if (!pendingPublishId) return;
    setPublishError('');
    try {
      await publish.mutateAsync(pendingPublishId);
      setPendingPublishId(null);
    } catch (err: any) {
      setPublishError(getVnErrorMessage(err?.response?.data?.error));
      setPendingPublishId(null);
    }
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Thông báo</h1>
        {isAdmin && (
          <button onClick={() => { setShowCreate(true); resetForm(); }} className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-md text-sm font-medium">Tạo thông báo</button>
        )}
      </div>
      {isError && <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 mb-4">Không thể tải danh sách thông báo.</div>}
      {publishError && <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 mb-4">{publishError}</div>}
      <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 border-b border-gray-200">
            <tr>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Tiêu đề</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Loại</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Phạm vi</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Ngày đăng</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Trạng thái</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Thao tác</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {isLoading && <tr><td colSpan={6} className="text-center py-8 text-gray-400">Đang tải...</td></tr>}
            {!isLoading && !data?.data?.length && <tr><td colSpan={6} className="text-center py-8 text-gray-400">Không có thông báo nào.</td></tr>}
            {data?.data?.map((a: any) => (
              <tr key={a.id} className="hover:bg-gray-50">
                <td className="px-4 py-3 font-medium max-w-xs truncate">{a.title}</td>
                <td className="px-4 py-3"><span className="inline-flex px-2 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-700">{labelFor('AnnouncementType', a.type)}</span></td>
                <td className="px-4 py-3">{labelFor('AnnouncementScope', a.targetScope)}{a.targetBlock ? ` - ${a.targetBlock.name}` : ''}</td>
                <td className="px-4 py-3 text-gray-500">{a.publishedAt ? formatVNDate(a.publishedAt) : '—'}</td>
                <td className="px-4 py-3"><span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${a.publishedAt ? 'bg-green-100 text-green-700' : 'bg-yellow-100 text-yellow-700'}`}>{a.publishedAt ? 'Đã đăng' : 'Nháp'}</span></td>
                <td className="px-4 py-3">
                  {isAdmin && !a.publishedAt && (
                    <button
                      onClick={() => { setPublishError(''); setPendingPublishId(a.id); }}
                      className="text-blue-600 hover:underline text-xs"
                    >
                      Đăng
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="flex items-center justify-between px-4 py-3 border-t border-gray-200">
          <span className="text-xs text-gray-500">Tổng: {data?.total ?? 0}</span>
          <div className="flex gap-2">
            <button onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0} className="px-3 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50 disabled:opacity-40">Trước</button>
            <span className="px-3 py-1 text-xs">{page + 1} / {data?.totalPages ?? 1}</span>
            <button onClick={() => setPage((p) => p + 1)} disabled={page + 1 >= (data?.totalPages ?? 1)} className="px-3 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50 disabled:opacity-40">Sau</button>
          </div>
        </div>
      </div>

      {showCreate && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/50" onClick={() => { setShowCreate(false); resetForm(); }} />
          <div className="relative bg-white rounded-lg shadow-xl w-full max-w-lg p-6 max-h-[90vh] overflow-y-auto">
            <h2 className="text-lg font-semibold mb-4">Tạo thông báo</h2>
            <form onSubmit={handleCreate} className="space-y-3">
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Tiêu đề <span className="text-red-500">*</span></label>
                <input name="title" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" /></div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Nội dung <span className="text-red-500">*</span></label>
                <div className="flex flex-wrap items-center gap-1 mb-1">
                  <span className="text-xs text-gray-500 mr-1">Định dạng:</span>
                  <button type="button" onClick={() => insertMarkdown('**', '**', 'văn bản đậm')} className="px-2 py-1 text-xs font-bold border border-gray-300 rounded hover:bg-gray-50" title="Đậm">Đậm</button>
                  <button type="button" onClick={() => insertMarkdown('*', '*', 'văn bản nghiêng')} className="px-2 py-1 text-xs italic border border-gray-300 rounded hover:bg-gray-50" title="Nghiêng">Nghiêng</button>
                  <button type="button" onClick={() => insertMarkdown('## ', '', 'Tiêu đề')} className="px-2 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50" title="Tiêu đề">Tiêu đề</button>
                  <button type="button" onClick={() => insertMarkdown('- ', '', 'Mục danh sách')} className="px-2 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50" title="Danh sách">Danh sách</button>
                  <button type="button" onClick={() => insertMarkdown('[', '](https://)', 'văn bản liên kết')} className="px-2 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50" title="Liên kết">Liên kết</button>
                </div>
                <textarea ref={textareaRef} name="content" value={content} onChange={(e) => setContent(e.target.value)} rows={6} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm font-mono resize-y" placeholder="Hỗ trợ Markdown: **đậm**, *nghiêng*, ## tiêu đề, - danh sách, [liên kết](https://...)" />
                <div className="mt-2">
                  <button type="button" onClick={() => setShowPreview((p) => !p)} className="text-xs text-blue-600 hover:underline">
                    {showPreview ? 'Ẩn xem trước' : 'Xem trước'}
                  </button>
                  {showPreview && (
                    <div className="mt-1 border border-gray-200 rounded-md p-3 bg-gray-50">
                      <p className="text-xs text-gray-400 mb-1">Xem trước</p>
                      {content.trim()
                        ? <MarkdownContent content={content} className="text-sm text-gray-700" />
                        : <p className="text-sm text-gray-400 italic">Chưa có nội dung.</p>}
                    </div>
                  )}
                </div>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div><label className="block text-sm font-medium text-gray-700 mb-1">Loại</label>
                  <select name="type" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm bg-white">
                    {TYPES.map((at) => <option key={at} value={at}>{labelFor('AnnouncementType', at)}</option>)}
                  </select></div>
                <div><label className="block text-sm font-medium text-gray-700 mb-1">Phạm vi</label>
                  <select value={scope} onChange={(e) => { setScope(e.target.value); setBlockId(''); setFloor(''); }} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm bg-white">
                    <option value="ALL">{labelFor('AnnouncementScope', 'ALL')}</option>
                    <option value="BLOCK">{labelFor('AnnouncementScope', 'BLOCK')}</option>
                    <option value="FLOOR">{labelFor('AnnouncementScope', 'FLOOR')}</option>
                  </select></div>
              </div>
              {scope !== 'ALL' && (
                <div><label className="block text-sm font-medium text-gray-700 mb-1">Tòa <span className="text-red-500">*</span></label>
                  <select value={blockId} onChange={(e) => setBlockId(e.target.value)} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm bg-white">
                    <option value="">-- Chọn tòa --</option>
                    {blocksData?.data?.map((b: any) => (
                      <option key={b.id} value={b.id}>{b.name}</option>
                    ))}
                  </select></div>
              )}
              {scope === 'FLOOR' && (
                <div><label className="block text-sm font-medium text-gray-700 mb-1">Tầng <span className="text-red-500">*</span></label>
                  <input type="number" min="1" value={floor} onChange={(e) => setFloor(e.target.value)} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" placeholder="Số tầng" /></div>
              )}
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Xuất bản</label>
                <select name="publishNow" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm bg-white">
                  <option value="true">Xuất bản ngay</option>
                  <option value="false">Lưu nháp</option>
                </select></div>
              {formError && <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded">{formError}</p>}
              <div className="flex gap-2 justify-end pt-2">
                <button type="button" onClick={() => { setShowCreate(false); resetForm(); }} className="px-4 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50">Huỷ</button>
                <button type="submit" disabled={create.isPending || publish.isPending} className="px-4 py-2 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50">
                  {publish.isPending ? 'Đang xuất bản...' : create.isPending ? 'Đang tạo...' : 'Tạo'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {pendingPublishId && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/50" onClick={() => setPendingPublishId(null)} />
          <div className="relative bg-white rounded-lg shadow-xl w-full max-w-sm p-6">
            <h2 className="text-lg font-semibold mb-2">Đăng thông báo tới toàn bộ cư dân?</h2>
            <p className="text-sm text-gray-600 mb-6">Thông báo sẽ được gửi tới tất cả cư dân và không thể thu hồi.</p>
            <div className="flex gap-2 justify-end">
              <button type="button" onClick={() => setPendingPublishId(null)} className="px-4 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50">Huỷ</button>
              <button type="button" onClick={handleConfirmPublish} disabled={publish.isPending} className="px-4 py-2 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50">
                {publish.isPending ? 'Đang đăng...' : 'Đăng thông báo'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
