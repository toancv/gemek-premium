import React, { useState, useCallback } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { SearchableSelect, getVnErrorMessage, labelFor, formatVNDate } from '@gemek/ui';
import type { SearchableOption } from '@gemek/ui';
import { useTickets, useCreateTicket, useTicketCount } from '../api/hooks';
import { apiClient } from '../api/client';
import { useAuthStore } from '../store/authStore';
import { t } from '../i18n/vi';

// Ticket-stats block (backlog (c) P2.5 + P2.7): mirrors the dashboard's ticket semantics on the
// Tickets page so a technician (admitted in P3) sees ticket stats here, not on the business
// dashboard. Counts come from the role-scoped list endpoint via PageResponse.total — accurate
// whole-dataset counts for the caller's scope, never page rows. P2.7 adds the SLA-breached card
// (now derivable via the GET /api/tickets ?overdue=true filter) and makes every card a drill-down:
// clicking navigates to the filtered list on this page (URL params). See reports/c-p2.7-ticketstats-fe.md.
const STAT_STATUSES: { status: string; color: string }[] = [
  { status: 'NEW', color: 'text-blue-600' },
  { status: 'ASSIGNED', color: 'text-purple-600' },
  { status: 'IN_PROGRESS', color: 'text-yellow-600' },
  { status: 'DONE', color: 'text-green-600' },
];
const STAT_CATEGORIES = ['MAINTENANCE_REPAIR', 'COMPLAINT', 'ADMINISTRATIVE', 'SUGGESTION_FEEDBACK', 'OTHER'];

function StatCard({ title, value, color, onClick }: { title: string; value: string | number; color: string; onClick?: () => void }) {
  return (
    <div
      onClick={onClick}
      className={`bg-white rounded-lg border border-gray-200 p-6${onClick ? ' cursor-pointer hover:border-blue-400 hover:shadow-sm transition-colors' : ''}`}
    >
      <p className="text-sm font-medium text-gray-500">{title}</p>
      <p className={`text-3xl font-bold mt-1 ${color}`}>{value}</p>
    </div>
  );
}

function StatusCountCard({ status, color, onClick }: { status: string; color: string; onClick: () => void }) {
  const { data, isLoading } = useTicketCount({ status });
  return <StatCard title={labelFor('TicketStatus', status)} value={isLoading ? '…' : data ?? 0} color={color} onClick={onClick} />;
}

// SLA-breached / overdue card (P2.7): sourced the SAME way as the status cards — the role-scoped
// list total with ?overdue=true (P2.6 filter: sla_deadline present AND past AND status not closed).
// On error shows '—', never a fabricated 0: the number is real or explicitly absent.
function SlaCountCard({ onClick }: { onClick: () => void }) {
  const { data, isLoading, isError } = useTicketCount({ overdue: true });
  const value = isError ? '—' : isLoading ? '…' : data ?? 0;
  return <StatCard title={t('dashboard.slaBreached')} value={value} color="text-red-600" onClick={onClick} />;
}

function CategoryCountRow({ category, onClick }: { category: string; onClick: () => void }) {
  const { data, isLoading } = useTicketCount({ category });
  return (
    <button type="button" onClick={onClick} className="flex justify-between text-sm w-full text-left hover:text-blue-600">
      <span className="text-gray-500">{labelFor('TicketCategory', category)}</span>
      <span className="font-medium">{isLoading ? '…' : data ?? 0}</span>
    </button>
  );
}

function TicketStats({ onDrill }: { onDrill: (patch: Record<string, string>) => void }) {
  return (
    <div className="mb-6">
      <div className="grid grid-cols-5 gap-4 mb-4">
        {STAT_STATUSES.map((s) => (
          <StatusCountCard key={s.status} status={s.status} color={s.color} onClick={() => onDrill({ status: s.status })} />
        ))}
        <SlaCountCard onClick={() => onDrill({ overdue: 'true' })} />
      </div>
      <div className="bg-white rounded-lg border border-gray-200 p-6">
        <h2 className="text-base font-semibold text-gray-900 mb-4">{t('dashboard.ticketsByCategory')}</h2>
        <div className="grid grid-cols-2 gap-x-8 gap-y-2">
          {STAT_CATEGORIES.map((c) => (
            <CategoryCountRow key={c} category={c} onClick={() => onDrill({ category: c })} />
          ))}
        </div>
      </div>
    </div>
  );
}

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
  // Ticket creation is ADMIN+RESIDENT on the BE (POST /api/tickets) — a TECHNICIAN would 403.
  // Hide the create control from TECHNICIAN; the stat cards/drill-down/list below are role-neutral.
  const isTechnician = useAuthStore((s) => s.user?.role) === 'TECHNICIAN';
  // URL search params are the single source of truth for list filters (P2.7) — stat-card
  // drill-downs (which set the URL) and the in-page dropdowns derive from the same place, and
  // landing on /tickets?overdue=true or ?status=NEW applies the filter immediately on mount.
  const [searchParams, setSearchParams] = useSearchParams();
  const category = searchParams.get('category') ?? '';
  const status = searchParams.get('status') ?? '';
  const overdue = searchParams.get('overdue') === 'true';
  const page = Math.max(0, Number(searchParams.get('page') ?? '0') || 0);
  const [showCreate, setShowCreate] = useState(false);
  const [apartmentId, setApartmentId] = useState('');
  const [formError, setFormError] = useState('');

  // Merge a filter change into the URL, resetting pagination. Falsy value clears the key.
  // Used by the in-page dropdowns + the clearable overdue chip (preserves the other filters).
  const setFilter = (patch: Record<string, string>) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      Object.entries(patch).forEach(([k, v]) => (v ? next.set(k, v) : next.delete(k)));
      next.delete('page');
      return next;
    });
  };
  // Stat-card drill-down: REPLACE all filters with the single one, so the resulting list length
  // equals that card's own count (each card is counted with exactly one unscoped filter).
  const drillDown = (patch: Record<string, string>) => {
    const next = new URLSearchParams();
    Object.entries(patch).forEach(([k, v]) => v && next.set(k, v));
    setSearchParams(next);
  };
  const goPage = (p: number) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      if (p > 0) next.set('page', String(p)); else next.delete('page');
      return next;
    });
  };

  const params = { page, size: 20, ...(category && { category }), ...(status && { status }), ...(overdue && { overdue: true }) };
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
        {!isTechnician && (
          <button
            onClick={() => { setShowCreate(true); setApartmentId(''); setFormError(''); }}
            className="bg-blue-600 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-blue-700"
          >
            {t('tickets.new')}
          </button>
        )}
      </div>

      <TicketStats onDrill={drillDown} />

      <div className="bg-white rounded-lg border border-gray-200 p-4 mb-4 flex gap-3 items-center">
        <select value={category} onChange={(e) => setFilter({ category: e.target.value })}
          className="border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
          <option value="">{t('tickets.allCategories')}</option>
          <option value="MAINTENANCE_REPAIR">{labelFor('TicketCategory', 'MAINTENANCE_REPAIR')}</option>
          <option value="COMPLAINT">{labelFor('TicketCategory', 'COMPLAINT')}</option>
          <option value="ADMINISTRATIVE">{labelFor('TicketCategory', 'ADMINISTRATIVE')}</option>
          <option value="SUGGESTION_FEEDBACK">{labelFor('TicketCategory', 'SUGGESTION_FEEDBACK')}</option>
          <option value="OTHER">{labelFor('TicketCategory', 'OTHER')}</option>
        </select>
        <select value={status} onChange={(e) => setFilter({ status: e.target.value })}
          className="border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
          <option value="">{t('tickets.allStatuses')}</option>
          <option value="NEW">{labelFor('TicketStatus', 'NEW')}</option>
          <option value="ASSIGNED">{labelFor('TicketStatus', 'ASSIGNED')}</option>
          <option value="IN_PROGRESS">{labelFor('TicketStatus', 'IN_PROGRESS')}</option>
          <option value="DONE">{labelFor('TicketStatus', 'DONE')}</option>
          <option value="CANCELLED">{labelFor('TicketStatus', 'CANCELLED')}</option>
        </select>
        {/* overdue has no dropdown (filter controls are status/category only); when active via a
            drill-down it is honored by the list query and shown as a clearable chip here. */}
        {overdue && (
          <button type="button" onClick={() => setFilter({ overdue: '' })}
            className="inline-flex items-center gap-1 px-3 py-2 text-sm rounded-md bg-red-50 text-red-700 border border-red-200 hover:bg-red-100">
            {t('dashboard.slaBreached')} ✕
          </button>
        )}
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
                    {t.slaDeadline ? formatVNDate(t.slaDeadline) : '—'}
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
            <button onClick={() => goPage(page - 1)} disabled={page === 0} className="px-3 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50 disabled:opacity-40">{t('common.prev')}</button>
            <span className="px-3 py-1 text-xs">{page + 1} / {data?.totalPages ?? 1}</span>
            <button onClick={() => goPage(page + 1)} disabled={page + 1 >= (data?.totalPages ?? 1)} className="px-3 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50 disabled:opacity-40">{t('common.next')}</button>
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
