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
  // P5 server-side visibility param: omitted = "mine" (pre-P5 scoping), "community" = public tickets.
  const [tab, setTab] = useState<'mine' | 'community'>('mine');
  const { data, isLoading } = useMyTickets(
    tab === 'community' ? { size: 20, visibility: 'community' } : { size: 20 });
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
        // G3: creator-chosen at create time, immutable afterwards. Default OFF.
        isPublic: fd.get('isPublic') === 'on',
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

      <div className="flex gap-1 mb-4 bg-gray-100 rounded-lg p-1">
        {(['mine', 'community'] as const).map((tabKey) => (
          <button
            key={tabKey}
            onClick={() => setTab(tabKey)}
            className={`flex-1 py-1.5 text-sm rounded-md transition-colors ${tab === tabKey ? 'bg-white shadow font-medium text-gray-900' : 'text-gray-500'}`}
          >
            {tabKey === 'mine' ? t('tickets.tabMine') : t('tickets.tabCommunity')}
          </button>
        ))}
      </div>

      {isLoading && <div className="text-center py-8 text-gray-400">{t('common.loading')}</div>}
      {!isLoading && !data?.data?.length && (
        <div className="text-center py-12 text-gray-400">
          <p className="text-4xl mb-2">🎫</p>
          <p>{t('common.emptyYet', { item: tab === 'community' ? 'phản ánh cộng đồng' : 'phản ánh' })}</p>
          {tab === 'mine' && <button onClick={() => setShowForm(true)} className="mt-3 text-blue-600 text-sm">{t('tickets.createFirst')}</button>}
        </div>
      )}
      <div className="space-y-3">
        {/* Map param named tk — `t` would shadow the i18n t(). Community rows arrive
            REDACTED from the BE (submitter = «Cư dân», block only) — render with
            optional chaining only, no FE hiding logic. */}
        {data?.data?.map((tk: any) => (
          <Link to={`/tickets/${tk.id}`} key={tk.id} className="block bg-white rounded-xl border border-gray-200 p-4 hover:border-blue-300 transition-colors">
            <div className="flex items-start justify-between gap-2">
              <p className="font-medium text-gray-900 text-sm flex-1">{tk.title}</p>
              <span className={`flex-shrink-0 text-xs px-2 py-0.5 rounded-full ${STATUS_BG[tk.status] ?? 'bg-gray-100 text-gray-700'}`}>{labelFor('TicketStatus', tk.status)}</span>
            </div>
            <p className="text-xs text-gray-400 mt-1">{labelFor('TicketCategory', tk.category)} • {formatVNDate(tk.createdAt)}</p>
            {tab === 'community' && (
              <p className="text-xs text-gray-400 mt-0.5">
                {tk.submittedBy?.fullName}{tk.apartment?.block?.name ? ` • Tòa ${tk.apartment.block.name}` : ''}
              </p>
            )}
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
              <label className="flex items-start gap-2 text-sm text-gray-700">
                <input type="checkbox" name="isPublic" className="mt-0.5 rounded border-gray-300" />
                <span>{t('tickets.publicToggle')}</span>
              </label>
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
