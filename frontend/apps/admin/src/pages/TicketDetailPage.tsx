import React, { useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useTicket, useAssignTicket, useUpdateTicketStatus } from '../api/hooks';
import { SearchableSelect, VNDatePicker, getVnErrorMessage, labelFor, formatVNDateTime } from '@gemek/ui';
import { apiClient } from '../api/client';
import { useRoleFlags } from '../lib/useRoleFlags';
import { t } from '../i18n/vi';

const STATUS_COLORS: Record<string, string> = {
  NEW: 'bg-blue-100 text-blue-700', ASSIGNED: 'bg-purple-100 text-purple-700',
  IN_PROGRESS: 'bg-yellow-100 text-yellow-700', DONE: 'bg-green-100 text-green-700',
  CANCELLED: 'bg-gray-100 text-gray-700',
};

const ROLE_LABEL: Record<string, string> = {
  ADMIN: 'Quản trị viên',
  BOARD_MEMBER: 'Ban quản lý',
  TECHNICIAN: 'Kỹ thuật viên',
};

export function TicketDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data: ticket, isLoading, isError } = useTicket(id!);
  const assignTicket = useAssignTicket();
  const updateStatus = useUpdateTicketStatus();
  // Write controls are gated to their exact BE @PreAuthorize set (backlog (c) BOARD_MEMBER 403
  // fix, Direction A — see reports/c-boardmember-403-diagnosis.md):
  //   Assign (PUT /{id}/assign)  = hasRole('ADMIN')              → isAdmin only
  //   Status (PUT /{id}/status)  = hasAnyRole('ADMIN','TECHNICIAN') → isAdmin || isTechnician
  // BOARD_MEMBER (read/oversight) sees neither — both would 403. The assign staff-picker also
  // calls GET /users (ADMIN-only), so gating assign to ADMIN removes that broken call for BOARD.
  const { isAdmin, isTechnician } = useRoleFlags();

  const [assignedUserId, setAssignedUserId] = useState('');
  const [assignedContractorId, setAssignedContractorId] = useState('');
  const [userMap, setUserMap] = useState<Record<string, any>>({});
  const [contractorMap, setContractorMap] = useState<Record<string, any>>({});
  const [scheduledDate, setScheduledDate] = useState('');
  const [assignNotes, setAssignNotes] = useState('');
  const [assignError, setAssignError] = useState('');

  const [newStatus, setNewStatus] = useState('');
  const [statusNotes, setStatusNotes] = useState('');
  const [statusError, setStatusError] = useState('');

  // 3 parallel calls (ADMIN, BOARD_MEMBER, TECHNICIAN) merged + deduped — single role param only on BE
  const loadStaffOptions = useCallback(async (query: string) => {
    const params: Record<string, unknown> = { size: 20, isActive: true };
    if (query) params.search = query;
    const [admins, boards, techs] = await Promise.all([
      apiClient.get('/users', { params: { ...params, role: 'ADMIN' } }).then((r) => r.data?.data ?? []),
      apiClient.get('/users', { params: { ...params, role: 'BOARD_MEMBER' } }).then((r) => r.data?.data ?? []),
      apiClient.get('/users', { params: { ...params, role: 'TECHNICIAN' } }).then((r) => r.data?.data ?? []),
    ]);
    const seen = new Set<string>();
    const merged: any[] = [];
    for (const u of [...admins, ...boards, ...techs]) {
      if (!seen.has(u.id)) { seen.add(u.id); merged.push(u); }
    }
    setUserMap((prev) => {
      const next = { ...prev };
      merged.forEach((u) => { next[u.id] = u; });
      return next;
    });
    return merged.map((u) => ({
      value: u.id,
      label: `${u.fullName} — ${ROLE_LABEL[u.role] ?? u.role}`,
    }));
  }, []);

  const loadContractorOptions = useCallback(async (query: string) => {
    const params: Record<string, unknown> = { size: 20, isActive: true };
    if (query) params.search = query;
    const res = await apiClient.get('/contractors', { params });
    const contractors: any[] = res.data?.data ?? [];
    setContractorMap((prev) => {
      const next = { ...prev };
      contractors.forEach((c) => { next[c.id] = c; });
      return next;
    });
    return contractors.map((c) => ({ value: c.id, label: c.companyName }));
  }, []);

  const handleUserChange = (uid: string) => {
    setAssignedUserId(uid);
    if (uid) setAssignedContractorId('');
    setAssignError('');
  };

  const handleContractorChange = (cid: string) => {
    setAssignedContractorId(cid);
    if (cid) setAssignedUserId('');
    setAssignError('');
  };

  const handleAssign = async (e: React.FormEvent) => {
    e.preventDefault();
    setAssignError('');
    if (!assignedUserId && !assignedContractorId) { setAssignError('Chọn nhân viên hoặc nhà thầu'); return; }
    const payload: Record<string, unknown> = {
      assignedToUserId: assignedUserId || null,
      assignedToContractorId: assignedContractorId || null,
      scheduledDate: scheduledDate || null,
      notes: assignNotes || null,
    };
    try {
      await assignTicket.mutateAsync({ id: id!, data: payload });
      setAssignedUserId(''); setAssignedContractorId(''); setScheduledDate(''); setAssignNotes('');
    } catch (err: any) { setAssignError(getVnErrorMessage(err?.response?.data?.error)); }
  };

  const handleStatusUpdate = async (e: React.FormEvent) => {
    e.preventDefault();
    setStatusError('');
    if (!newStatus) { setStatusError('Vui lòng chọn trạng thái.'); return; }
    try {
      await updateStatus.mutateAsync({ id: id!, data: { status: newStatus, notes: statusNotes } });
      setNewStatus(''); setStatusNotes('');
    } catch (err: any) { setStatusError(getVnErrorMessage(err?.response?.data?.error)); }
  };

  if (isLoading) return <div className="flex items-center justify-center h-64"><svg className="animate-spin h-8 w-8 text-blue-600" fill="none" viewBox="0 0 24 24"><circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"/><path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/></svg></div>;
  if (isError) return <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700">{t('ticketDetail.loadError')}</div>;

  const isMaintenanceRepair = ticket.category === 'MAINTENANCE_REPAIR';

  return (
    <div className="max-w-4xl">
      <button onClick={() => navigate(-1)} className="text-sm text-blue-600 hover:underline mb-4 flex items-center gap-1">
        {t('ticketDetail.back')}
      </button>
      <div className="bg-white rounded-lg border border-gray-200 p-6 mb-4">
        <div className="flex items-start justify-between mb-4">
          <div>
            <h1 className="text-xl font-bold text-gray-900">{ticket.title}</h1>
            <p className="text-xs text-gray-400 mt-1 font-mono">{ticket.id}</p>
          </div>
          <span className={`inline-flex px-3 py-1 rounded-full text-sm font-medium ${STATUS_COLORS[ticket.status] ?? 'bg-gray-100 text-gray-700'}`}>{labelFor('TicketStatus', ticket.status)}</span>
        </div>
        <div className="grid grid-cols-3 gap-4 text-sm mb-4">
          <div><span className="text-gray-500">{t('ticketDetail.category')}</span> <span className="font-medium">{labelFor('TicketCategory', ticket.category)}</span></div>
          <div><span className="text-gray-500">{t('ticketDetail.priority')}</span> <span className="font-medium">{labelFor('TicketPriority', ticket.priority)}</span></div>
          <div><span className="text-gray-500">{t('ticketDetail.apartment')}</span> <span className="font-medium">{ticket.apartment?.unitNumber} - {ticket.apartment?.block?.name}</span></div>
          <div><span className="text-gray-500">{t('ticketDetail.submittedBy')}</span> <span className="font-medium">{ticket.submittedBy?.fullName}{ticket.apartment?.unitNumber ? ` - ${ticket.apartment.unitNumber}` : ''}</span></div>
          <div><span className="text-gray-500">{t('ticketDetail.assignee')}</span> <span className="font-medium">{ticket.assignedToUser?.fullName ?? ticket.assignedToContractor?.companyName ?? '—'}</span></div>
          <div><span className="text-gray-500">{t('ticketDetail.sla')}</span> <span className={`font-medium ${ticket.slaBreached ? 'text-red-600' : ''}`}>{ticket.slaDeadline ? formatVNDateTime(ticket.slaDeadline) : '—'}{ticket.slaBreached && ' ⚠'}</span></div>
        </div>
        {ticket.description && <div className="bg-gray-50 rounded-md p-4 text-sm text-gray-700 mb-4">{ticket.description}</div>}
        {ticket.rating && <div className="text-sm"><span className="text-gray-500">{t('ticketDetail.rating')}</span> <span className="font-medium">{'★'.repeat(ticket.rating)}{'☆'.repeat(5 - ticket.rating)}</span> {ticket.ratingComment && <span className="text-gray-500 ml-2">"{ticket.ratingComment}"</span>}</div>}
      </div>

      {/* Photos */}
      {ticket.photos?.length > 0 && (
        <div className="bg-white rounded-lg border border-gray-200 p-6 mb-4">
          <h2 className="text-base font-semibold mb-3">{t('ticketDetail.photos')}</h2>
          <div className="grid grid-cols-3 gap-3">
            {ticket.photos.map((p: any) => (
              <div key={p.id} className="rounded-lg overflow-hidden border border-gray-200">
                <div className="bg-gray-100 px-2 py-1 text-xs font-medium text-gray-500">{p.phase}</div>
                <img src={p.presignedUrl} alt={p.fileName} className="w-full h-32 object-cover" />
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Status History */}
      <div className="bg-white rounded-lg border border-gray-200 p-6 mb-4">
        <h2 className="text-base font-semibold mb-3">{t('ticketDetail.statusHistory')}</h2>
        <div className="space-y-3">
          {ticket.statusHistory?.map((h: any) => (
            <div key={h.id} className="flex items-start gap-3 text-sm">
              <div className="w-2 h-2 rounded-full bg-blue-500 mt-1.5 flex-shrink-0" />
              <div>
                <span className="font-medium">{h.oldStatus ? labelFor('TicketStatus', h.oldStatus) : t('ticketDetail.created')} → {labelFor('TicketStatus', h.newStatus)}</span>
                <span className="text-gray-400 ml-2">{t('ticketDetail.by')} {h.changedBy?.fullName}</span>
                <span className="text-gray-400 ml-2">{formatVNDateTime(h.changedAt)}</span>
                {h.notes && <p className="text-gray-500 mt-0.5">{h.notes}</p>}
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Admin Actions */}
      <div className="grid grid-cols-2 gap-4">
        {/* Assign (Phân công): ADMIN-only on BE (PUT /{id}/assign) — shown only to ADMIN. */}
        {isAdmin && (
        <div className="bg-white rounded-lg border border-gray-200 p-6">
          <h2 className="text-base font-semibold mb-3">Phân công</h2>
          <form onSubmit={handleAssign} className="space-y-3">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Nhân viên</label>
              <SearchableSelect
                value={assignedUserId}
                onChange={handleUserChange}
                loadOptions={loadStaffOptions}
                placeholder="Tìm theo tên..."
              />
            </div>
            {isMaintenanceRepair && (
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Nhà thầu <span className="text-xs text-gray-400">(chỉ {labelFor('TicketCategory', 'MAINTENANCE_REPAIR')})</span></label>
                <SearchableSelect
                  value={assignedContractorId}
                  onChange={handleContractorChange}
                  loadOptions={loadContractorOptions}
                  placeholder="Tìm theo tên công ty..."
                />
              </div>
            )}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Ngày hẹn</label>
              {/* BE AssignTicketRequest.scheduledDate is LocalDate (date-only) — the old
                  datetime-local sent a time component AND rendered browser-locale mm/dd.
                  VNDatePicker: dd/MM/yyyy display, ISO yyyy-mm-dd value (rollout pattern). */}
              <VNDatePicker value={scheduledDate} onChange={setScheduledDate} />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Ghi chú</label>
              <textarea value={assignNotes} onChange={(e) => setAssignNotes(e.target.value)} rows={2} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm resize-none" />
            </div>
            {assignError && <p className="text-xs text-red-600">{assignError}</p>}
            <button type="submit" disabled={assignTicket.isPending} className="w-full bg-blue-600 text-white rounded-md py-2 text-sm hover:bg-blue-700 disabled:opacity-50">
              {assignTicket.isPending ? 'Đang phân công...' : 'Phân công'}
            </button>
          </form>
        </div>
        )}
        {/* Status (Cập nhật trạng thái): BE PUT /{id}/status = hasAnyRole('ADMIN','TECHNICIAN').
            Shown to ADMIN + TECHNICIAN; hidden from BOARD_MEMBER (would 403). */}
        {(isAdmin || isTechnician) && (
        <div className="bg-white rounded-lg border border-gray-200 p-6">
          <h2 className="text-base font-semibold mb-3">Cập nhật trạng thái</h2>
          <form onSubmit={handleStatusUpdate} className="space-y-3">
            <div><label className="block text-sm font-medium text-gray-700 mb-1">Trạng thái mới</label>
              <select value={newStatus} onChange={(e) => setNewStatus(e.target.value)} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm bg-white">
                <option value="">Chọn trạng thái...</option>
                <option value="IN_PROGRESS">{labelFor('TicketStatus', 'IN_PROGRESS')}</option>
                <option value="DONE">{labelFor('TicketStatus', 'DONE')}</option>
                <option value="CANCELLED">{labelFor('TicketStatus', 'CANCELLED')}</option>
              </select></div>
            <div><label className="block text-sm font-medium text-gray-700 mb-1">Ghi chú</label>
              <textarea value={statusNotes} onChange={(e) => setStatusNotes(e.target.value)} rows={2} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" /></div>
            {statusError && <p className="text-xs text-red-600">{statusError}</p>}
            <button type="submit" disabled={updateStatus.isPending} className="w-full bg-green-600 text-white rounded-md py-2 text-sm hover:bg-green-700 disabled:opacity-50">
              {updateStatus.isPending ? 'Đang cập nhật...' : 'Cập nhật trạng thái'}
            </button>
          </form>
        </div>
        )}
      </div>
    </div>
  );
}
