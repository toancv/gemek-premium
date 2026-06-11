import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMyTickets, useCreateTicket, useMyResident } from '../api/hooks';
import { getVnErrorMessage, labelFor, formatVNDate } from '@gemek/ui';
import { t } from '../i18n/vi';

const STATUS_BG: Record<string, string> = {
  NEW: 'bg-blue-100 text-blue-700', ASSIGNED: 'bg-purple-100 text-purple-700',
  IN_PROGRESS: 'bg-yellow-100 text-yellow-700', DONE: 'bg-green-100 text-green-700',
  CANCELLED: 'bg-gray-100 text-gray-700',
};

export function MyTicketsPage() {
  const { data, isLoading } = useMyTickets({ size: 20 });
  const { data: resident, isLoading: aptLoading } = useMyResident();
  const apartment = resident?.apartment ?? null;
  const create = useCreateTicket();
  const [showForm, setShowForm] = useState(false);
  const [formError, setFormError] = useState('');

  const aptLabel = apartment?.unitNumber ?? null;

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError('');
    if (!apartment?.id) { setFormError('Không thể xác định căn hộ của bạn'); return; }
    const fd = new FormData(e.target as HTMLFormElement);
    const title = (fd.get('title') as string).trim();
    if (!title) { setFormError('Tiêu đề không được để trống'); return; }
    try {
      await create.mutateAsync({
        apartmentId: apartment.id,
        category: fd.get('category'),
        title,
        description: fd.get('description') || null,
        priority: 'MEDIUM',
      });
      setShowForm(false);
    } catch (err: any) { setFormError(getVnErrorMessage(err?.response?.data?.error)); }
  };

  return (
    <div className="p-4">
      <div className="flex items-center justify-between mb-4">
        <h1 className="text-lg font-bold text-gray-900">{t('tickets.title')}</h1>
        <button onClick={() => { setShowForm(true); setFormError(''); }} className="bg-blue-600 text-white px-4 py-2 rounded-lg text-sm font-medium">{t('tickets.new')}</button>
      </div>

      {isLoading && <div className="text-center py-8 text-gray-400">{t('common.loading')}</div>}
      {!isLoading && !data?.data?.length && (
        <div className="text-center py-12 text-gray-400">
          <p className="text-4xl mb-2">🎫</p>
          <p>{t('common.emptyYet', { item: 'phản ánh' })}</p>
          <button onClick={() => setShowForm(true)} className="mt-3 text-blue-600 text-sm">{t('tickets.createFirst')}</button>
        </div>
      )}
      <div className="space-y-3">
        {data?.data?.map((t: any) => (
          <Link to={`/tickets/${t.id}`} key={t.id} className="block bg-white rounded-xl border border-gray-200 p-4 hover:border-blue-300 transition-colors">
            <div className="flex items-start justify-between gap-2">
              <p className="font-medium text-gray-900 text-sm flex-1">{t.title}</p>
              <span className={`flex-shrink-0 text-xs px-2 py-0.5 rounded-full ${STATUS_BG[t.status] ?? 'bg-gray-100 text-gray-700'}`}>{labelFor('TicketStatus', t.status)}</span>
            </div>
            <p className="text-xs text-gray-400 mt-1">{labelFor('TicketCategory', t.category)} • {formatVNDate(t.createdAt)}</p>
          </Link>
        ))}
      </div>

      {showForm && (
        <div className="fixed inset-0 z-50 flex items-end justify-center">
          <div className="absolute inset-0 bg-black/50" onClick={() => setShowForm(false)} />
          <div className="relative bg-white rounded-t-2xl w-full max-w-md p-6 pb-8">
            <h2 className="text-lg font-semibold mb-4">{t('tickets.modalTitle')}</h2>
            <form onSubmit={handleCreate} className="space-y-3">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Căn hộ</label>
                {aptLoading ? (
                  <div className="block w-full border border-gray-200 rounded-lg px-3 py-2.5 text-sm bg-gray-50 text-gray-400">Đang tải...</div>
                ) : aptLabel ? (
                  <div className="block w-full border border-gray-200 rounded-lg px-3 py-2.5 text-sm bg-gray-50 text-gray-700 font-medium">{aptLabel}</div>
                ) : (
                  <div className="block w-full border border-red-200 rounded-lg px-3 py-2.5 text-sm bg-red-50 text-red-600">
                    Không thể xác định căn hộ. Vui lòng liên hệ quản trị viên.
                  </div>
                )}
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Loại phản ánh</label>
                <select name="category" className="block w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm bg-white">
                  <option value="MAINTENANCE_REPAIR">Sửa chữa & Bảo trì</option>
                  <option value="COMPLAINT">Khiếu nại</option>
                  <option value="ADMINISTRATIVE">Hành chính</option>
                  <option value="SUGGESTION_FEEDBACK">Góp ý & Phản hồi</option>
                  <option value="OTHER">Khác</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Tiêu đề <span className="text-red-500">*</span></label>
                <input name="title" maxLength={255} className="block w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Mô tả chi tiết</label>
                <textarea name="description" rows={3} className="block w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm resize-none" />
              </div>
              {formError && <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded-lg">{formError}</p>}
              <div className="flex gap-2 pt-1">
                <button type="button" onClick={() => setShowForm(false)} className="flex-1 py-2.5 text-sm border border-gray-300 rounded-lg hover:bg-gray-50">Hủy</button>
                <button
                  type="submit"
                  disabled={create.isPending || !apartment?.id || aptLoading}
                  className="flex-1 py-2.5 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
                >
                  {create.isPending ? 'Đang gửi...' : 'Gửi phản ánh'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
