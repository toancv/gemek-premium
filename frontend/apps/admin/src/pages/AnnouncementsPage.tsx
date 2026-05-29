import React, { useState } from 'react';
import { useAnnouncements, useCreateAnnouncement, usePublishAnnouncement } from '../api/hooks';

const TYPES = ['GENERAL','URGENT','MAINTENANCE','AMENITY','EVENT'];

export function AnnouncementsPage() {
  const [page, setPage] = useState(0);
  const [showCreate, setShowCreate] = useState(false);
  const [formError, setFormError] = useState('');
  const [scope, setScope] = useState('ALL');

  const { data, isLoading, isError } = useAnnouncements({ page, size: 20 });
  const create = useCreateAnnouncement();
  const publish = usePublishAnnouncement();

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError('');
    const fd = new FormData(e.target as HTMLFormElement);
    const title = (fd.get('title') as string).trim();
    const content = (fd.get('content') as string).trim();
    if (!title || !content) { setFormError('Title and content are required'); return; }
    try {
      await create.mutateAsync({ title, content, type: fd.get('type'), targetScope: fd.get('targetScope'), targetBlockId: fd.get('targetBlockId') || null, publishNow: fd.get('publishNow') === 'true', sendPush: true, sendEmail: false, sendSms: false });
      setShowCreate(false);
    } catch (err: any) { setFormError(err?.response?.data?.message ?? 'Failed to create'); }
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Announcements</h1>
        <button onClick={() => { setShowCreate(true); setFormError(''); }} className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-md text-sm font-medium">Create Announcement</button>
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
          <div className="absolute inset-0 bg-black/50" onClick={() => setShowCreate(false)} />
          <div className="relative bg-white rounded-lg shadow-xl w-full max-w-lg p-6 max-h-[90vh] overflow-y-auto">
            <h2 className="text-lg font-semibold mb-4">Create Announcement</h2>
            <form onSubmit={handleCreate} className="space-y-3">
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Title <span className="text-red-500">*</span></label>
                <input name="title" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" /></div>
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Content <span className="text-red-500">*</span></label>
                <textarea name="content" rows={4} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm resize-y" /></div>
              <div className="grid grid-cols-2 gap-3">
                <div><label className="block text-sm font-medium text-gray-700 mb-1">Type</label>
                  <select name="type" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm bg-white">
                    {TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
                  </select></div>
                <div><label className="block text-sm font-medium text-gray-700 mb-1">Scope</label>
                  <select name="targetScope" value={scope} onChange={(e) => setScope(e.target.value)} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm bg-white">
                    <option value="ALL">All</option>
                    <option value="BLOCK">Block</option>
                    <option value="FLOOR">Floor</option>
                  </select></div>
              </div>
              {scope !== 'ALL' && (
                <div><label className="block text-sm font-medium text-gray-700 mb-1">Block ID</label>
                  <input name="targetBlockId" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" placeholder="Block UUID" /></div>
              )}
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Publish</label>
                <select name="publishNow" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm bg-white">
                  <option value="true">Publish now</option>
                  <option value="false">Save as draft</option>
                </select></div>
              {formError && <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded">{formError}</p>}
              <div className="flex gap-2 justify-end pt-2">
                <button type="button" onClick={() => setShowCreate(false)} className="px-4 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50">Cancel</button>
                <button type="submit" disabled={create.isPending} className="px-4 py-2 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50">
                  {create.isPending ? 'Creating...' : 'Create'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
