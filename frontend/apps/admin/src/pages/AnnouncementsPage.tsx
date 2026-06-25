import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAnnouncements, usePublishAnnouncement } from '../api/hooks';
import { useRoleFlags } from '../lib/useRoleFlags';
import { getVnErrorMessage, labelFor, formatVNDate } from '@gemek/ui';

export function AnnouncementsPage() {
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [pendingPublishId, setPendingPublishId] = useState<string | null>(null);
  const [publishError, setPublishError] = useState('');

  // Announcement writes (create + publish) are ADMIN-only on the BE (POST/publish = hasRole('ADMIN')).
  // BOARD_MEMBER now reads this page (route opened read-only — backlog (c) #7) but sees NO write
  // control: gate create + publish + edit to ADMIN so opening the route does not create a new 403 mismatch.
  const { isAdmin } = useRoleFlags();

  const { data, isLoading, isError } = useAnnouncements({ page, size: 20 });
  const publish = usePublishAnnouncement();

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
          <button onClick={() => navigate('/announcements/new')} className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-md text-sm font-medium">Tạo thông báo</button>
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
                  {/* Drafts-only edit + publish — published announcements are immutable. */}
                  {isAdmin && !a.publishedAt && (
                    <div className="flex gap-3">
                      <button
                        onClick={() => navigate(`/announcements/${a.id}/edit`)}
                        className="text-blue-600 hover:underline text-xs"
                      >
                        Sửa
                      </button>
                      <button
                        onClick={() => { setPublishError(''); setPendingPublishId(a.id); }}
                        className="text-blue-600 hover:underline text-xs"
                      >
                        Đăng
                      </button>
                    </div>
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
