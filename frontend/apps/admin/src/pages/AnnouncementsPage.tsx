import React, { useState } from 'react';
import { useAnnouncements, useCreateAnnouncement, usePublishAnnouncement, useBlocks } from '../api/hooks';

const TYPES = ['GENERAL','URGENT','MAINTENANCE','AMENITY','EVENT'];

export function AnnouncementsPage() {
  const [page, setPage] = useState(0);
  const [showCreate, setShowCreate] = useState(false);
  const [formError, setFormError] = useState('');
  const [scope, setScope] = useState('ALL');
  const [blockId, setBlockId] = useState('');
  const [floor, setFloor] = useState('');

  const { data, isLoading, isError } = useAnnouncements({ page, size: 20 });
  const { data: blocksData } = useBlocks();
  const create = useCreateAnnouncement();
  const publish = usePublishAnnouncement();

  const resetForm = () => { setScope('ALL'); setBlockId(''); setFloor(''); setFormError(''); };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError('');
    const fd = new FormData(e.target as HTMLFormElement);
    const title = (fd.get('title') as string).trim();
    const content = (fd.get('content') as string).trim();
    const shouldPublish = fd.get('publishNow') === 'true';
    if (!title || !content) { setFormError('Tiêu đề và nội dung là bắt buộc'); return; }
    if (scope !== 'ALL' && !blockId) { setFormError('Vui lòng chọn block'); return; }
    if (scope === 'FLOOR' && !floor.trim()) { setFormError('Vui lòng nhập số tầng'); return; }
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
        } catch {
          setFormError('Thông báo đã được tạo nhưng xuất bản thất bại. Vui lòng xuất bản thủ công.');
          return;
        }
      }
      setShowCreate(false);
      resetForm();
    } catch (err: any) { setFormError(err?.response?.data?.message ?? 'Tạo thông báo thất bại'); }
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Announcements</h1>
        <button onClick={() => { setShowCreate(true); resetForm(); }} className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-md text-sm font-medium">Create Announcement</button>
      </div>
      {isError && <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 mb-4">Failed to load announcements.</div>}
      <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 border-b border-gray-200">
            <tr>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Title</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Type</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Scope</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Published</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Status</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {isLoading && <tr><td colSpan={6} className="text-center py-8 text-gray-400">Loading...</td></tr>}
            {!isLoading && !data?.data?.length && <tr><td colSpan={6} className="text-center py-8 text-gray-400">No announcements found</td></tr>}
            {data?.data?.map((a: any) => (
              <tr key={a.id} className="hover:bg-gray-50">
                <td className="px-4 py-3 font-medium max-w-xs truncate">{a.title}</td>
                <td className="px-4 py-3"><span className="inline-flex px-2 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-700">{a.type}</span></td>
                <td className="px-4 py-3">{a.targetScope}{a.targetBlock ? ` - ${a.targetBlock.name}` : ''}</td>
                <td className="px-4 py-3 text-gray-500">{a.publishedAt ? new Date(a.publishedAt).toLocaleDateString() : '—'}</td>
                <td className="px-4 py-3"><span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${a.publishedAt ? 'bg-green-100 text-green-700' : 'bg-yellow-100 text-yellow-700'}`}>{a.publishedAt ? 'Published' : 'Draft'}</span></td>
                <td className="px-4 py-3">
                  {!a.publishedAt && (
                    <button onClick={() => { if (window.confirm('Publish this announcement?')) publish.mutate(a.id); }} className="text-blue-600 hover:underline text-xs">Publish</button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="flex items-center justify-between px-4 py-3 border-t border-gray-200">
          <span className="text-xs text-gray-500">Total: {data?.total ?? 0}</span>
          <div className="flex gap-2">
            <button onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0} className="px-3 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50 disabled:opacity-40">Prev</button>
            <span className="px-3 py-1 text-xs">{page + 1} / {data?.totalPages ?? 1}</span>
            <button onClick={() => setPage((p) => p + 1)} disabled={page + 1 >= (data?.totalPages ?? 1)} className="px-3 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50 disabled:opacity-40">Next</button>
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
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Nội dung <span className="text-red-500">*</span></label>
                <textarea name="content" rows={4} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm resize-y" /></div>
              <div className="grid grid-cols-2 gap-3">
                <div><label className="block text-sm font-medium text-gray-700 mb-1">Loại</label>
                  <select name="type" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm bg-white">
                    {TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
                  </select></div>
                <div><label className="block text-sm font-medium text-gray-700 mb-1">Phạm vi</label>
                  <select value={scope} onChange={(e) => { setScope(e.target.value); setBlockId(''); setFloor(''); }} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm bg-white">
                    <option value="ALL">Toàn bộ</option>
                    <option value="BLOCK">Theo block</option>
                    <option value="FLOOR">Theo tầng</option>
                  </select></div>
              </div>
              {scope !== 'ALL' && (
                <div><label className="block text-sm font-medium text-gray-700 mb-1">Block <span className="text-red-500">*</span></label>
                  <select value={blockId} onChange={(e) => setBlockId(e.target.value)} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm bg-white">
                    <option value="">-- Chọn block --</option>
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
                <button type="button" onClick={() => { setShowCreate(false); resetForm(); }} className="px-4 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50">Hủy</button>
                <button type="submit" disabled={create.isPending || publish.isPending} className="px-4 py-2 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50">
                  {publish.isPending ? 'Đang xuất bản...' : create.isPending ? 'Đang tạo...' : 'Tạo'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
