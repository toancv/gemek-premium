import React, { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { SearchableSelect, getVnErrorMessage, labelFor } from '@gemek/ui';
import type { SearchableOption } from '@gemek/ui';
import { useTickets, useCreateTicket } from '../api/hooks';
import { apiClient } from '../api/client';
import { t } from '../i18n/vi';

const STATUS_COLORS: Record<string, string> = {
  NEW: 'bg-blue-100 text-blue-700', ASSIGNED: 'bg-purple-100 text-purple-700',
  IN_PROGRESS: 'bg-yellow-100 text-yellow-700', DONE: 'bg-green-100 text-green-700',
  CANCELLED: 'bg-gray-100 text-gray-700',
};
const CAT_COLORS: Record<string, string> = {
  MAINTENANCE_REPAIR: 'bg-orange-100 text-orange-700', COMPLAINT: 'bg-red-100 text-red-700',
  ADMINISTRATIVE: 'bg-blue-100 text-blue-700', SUGGESTION_FEEDBACK: 'bg-purple-100 text-purple-700',
  OTHER: 'bg-gray-100 text-gray-700',
};

export function TicketsPage() {
  const navigate = useNavigate();
  const [category, setCategory] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [showCreate, setShowCreate] = useState(false);
  const [apartmentId, setApartmentId] = useState('');
  const [formError, setFormError] = useState('');

  const params = { page, size: 20, ...(category && { category }), ...(status && { status }) };
  const { data, isLoading, isError } = useTickets(params);
  const createTicket = useCreateTicket();

  const loadApartmentOptions = useCallback(async (query: string): Promise<SearchableOption[]> => {
    const params: Record<string, unknown> = { size: 10, sort: 'unitNumber', direction: 'asc' };
    if (query) params.search = query;
    const res = await apiClient.get('/apartments', { params });
    return (res.data?.data ?? []).map((a: any) => ({
      value: a.id,
      label: `${a.block?.name ?? ''} - ${a.unitNumber}`,
    }));
  }, []);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError('');
    const fd = new FormData(e.target as HTMLFormElement);
    const title = (fd.get('title') as string).trim();
    if (!apartmentId) { setFormError('Vui lòng chọn căn hộ'); return; }
    if (!title) { setFormError('Tiêu đề không được để trống'); return; }
    try {
      const created: any = await createTicket.mutateAsync({
        apartmentId,
        category: fd.get('category'),
        title,
        description: fd.get('description') || null,
        priority: fd.get('priority') || 'MEDIUM',
      });
      setShowCreate(false);
      setApartmentId('');
      navigate(`/tickets/${created.id}`);
    } catch (err: any) {
      setFormError(getVnErrorMessage(err?.response?.data?.error));
    }
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">{t('tickets.title')}</h1>
        <button
          onClick={() => { setShowCreate(true); setApartmentId(''); setFormError(''); }}
          className="bg-blue-600 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-blue-700"
        >
          {t('tickets.new')}
        </button>
      </div>

      <div className="bg-white rounded-lg border border-gray-200 p-4 mb-4 flex gap-3">
        <select value={category} onChange={(e) => { setCategory(e.target.value); setPage(0); }}
          className="border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
          <option value="">{t('tickets.allCategories')}</option>
          <option value="MAINTENANCE_REPAIR">{labelFor('TicketCategory', 'MAINTENANCE_REPAIR')}</option>
          <option value="COMPLAINT">{labelFor('TicketCategory', 'COMPLAINT')}</option>
          <option value="ADMINISTRATIVE">{labelFor('TicketCategory', 'ADMINISTRATIVE')}</option>
          <option value="SUGGESTION_FEEDBACK">{labelFor('TicketCategory', 'SUGGESTION_FEEDBACK')}</option>
          <option value="OTHER">{labelFor('TicketCategory', 'OTHER')}</option>
        </select>
        <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}
          className="border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
          <option value="">{t('tickets.allStatuses')}</option>
          <option value="NEW">{labelFor('TicketStatus', 'NEW')}</option>
          <option value="ASSIGNED">{labelFor('TicketStatus', 'ASSIGNED')}</option>
          <option value="IN_PROGRESS">{labelFor('TicketStatus', 'IN_PROGRESS')}</option>
          <option value="DONE">{labelFor('TicketStatus', 'DONE')}</option>
          <option value="CANCELLED">{labelFor('TicketStatus', 'CANCELLED')}</option>
        </select>
      </div>

      {isError && <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 mb-4">{t('tickets.loadError')}</div>}

      <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 border-b border-gray-200">
            <tr>
              <th className="text-left px-4 py-3 font-medium text-gray-500">{t('tickets.idCol')}</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">{t('tickets.titleCol')}</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">{t('tickets.category')}</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">{t('common.status')}</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">{t('tickets.assignee')}</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">{t('tickets.slaDeadline')}</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {isLoading && <tr><td colSpan={6} className="text-center py-8 text-gray-400">{t('common.loading')}</td></tr>}
            {!isLoading && !data?.data?.length && <tr><td colSpan={6} className="text-center py-8 text-gray-400">{t('common.emptyFound', { item: 'phản ánh' })}</td></tr>}
            {data?.data?.map((t: any) => (
              <tr key={t.id} className="hover:bg-gray-50 cursor-pointer" onClick={() => navigate(`/tickets/${t.id}`)}>
                <td className="px-4 py-3 font-mono text-xs text-gray-500">{t.id.substring(0, 8)}</td>
                <td className="px-4 py-3 font-medium max-w-xs truncate">{t.title}</td>
                <td className="px-4 py-3"><span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${CAT_COLORS[t.category] ?? 'bg-gray-100 text-gray-700'}`}>{labelFor('TicketCategory', t.category)}</span></td>
                <td className="px-4 py-3"><span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${STATUS_COLORS[t.status] ?? 'bg-gray-100 text-gray-700'}`}>{labelFor('TicketStatus', t.status)}</span></td>
                <td className="px-4 py-3 text-gray-500">{t.assignedToUser?.fullName ?? t.assignedToContractor?.companyName ?? '—'}</td>
                <td className="px-4 py-3">
                  <span className={t.slaBreached ? 'text-red-600 font-medium' : 'text-gray-500'}>
                    {t.slaDeadline ? new Date(t.slaDeadline).toLocaleDateString() : '—'}
                    {t.slaBreached && ' ⚠'}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="flex items-center justify-between px-4 py-3 border-t border-gray-200">
          <span className="text-xs text-gray-500">{t('common.total')} {data?.total ?? 0}</span>
          <div className="flex gap-2">
            <button onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0} className="px-3 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50 disabled:opacity-40">{t('common.prev')}</button>
            <span className="px-3 py-1 text-xs">{page + 1} / {data?.totalPages ?? 1}</span>
            <button onClick={() => setPage((p) => p + 1)} disabled={page + 1 >= (data?.totalPages ?? 1)} className="px-3 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50 disabled:opacity-40">{t('common.next')}</button>
          </div>
        </div>
      </div>

      {showCreate && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/50" onClick={() => setShowCreate(false)} />
          <div className="relative bg-white rounded-lg shadow-xl w-full max-w-md p-6">
            <h2 className="text-lg font-semibold mb-4">{t('tickets.modalTitle')}</h2>
            <form onSubmit={handleCreate} className="space-y-3">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Căn hộ <span className="text-red-500">*</span></label>
                <SearchableSelect
                  loadOptions={loadApartmentOptions}
                  value={apartmentId}
                  onChange={setApartmentId}
                  placeholder="Chọn căn hộ..."
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('tickets.category')}</label>
                <select name="category" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500">
                  <option value="MAINTENANCE_REPAIR">{labelFor('TicketCategory', 'MAINTENANCE_REPAIR')}</option>
                  <option value="COMPLAINT">{labelFor('TicketCategory', 'COMPLAINT')}</option>
                  <option value="ADMINISTRATIVE">{labelFor('TicketCategory', 'ADMINISTRATIVE')}</option>
                  <option value="SUGGESTION_FEEDBACK">{labelFor('TicketCategory', 'SUGGESTION_FEEDBACK')}</option>
                  <option value="OTHER">{labelFor('TicketCategory', 'OTHER')}</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('tickets.priority')}</label>
                <select name="priority" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500">
                  <option value="LOW">{labelFor('TicketPriority', 'LOW')}</option>
                  <option value="MEDIUM">{labelFor('TicketPriority', 'MEDIUM')}</option>
                  <option value="HIGH">{labelFor('TicketPriority', 'HIGH')}</option>
                  <option value="URGENT">{labelFor('TicketPriority', 'URGENT')}</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('tickets.titleCol')} <span className="text-red-500">*</span></label>
                <input name="title" maxLength={255} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('tickets.description')}</label>
                <textarea name="description" rows={3} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm resize-none focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              {formError && <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded">{formError}</p>}
              <div className="flex gap-2 justify-end pt-2">
                <button type="button" onClick={() => setShowCreate(false)} className="px-4 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50">{t('common.cancel')}</button>
                <button type="submit" disabled={createTicket.isPending} className="px-4 py-2 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50">
                  {createTicket.isPending ? t('tickets.creating') : t('tickets.create')}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
