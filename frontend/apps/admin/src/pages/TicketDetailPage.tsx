import React, { useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useTicket, useAssignTicket, useUpdateTicketStatus } from '../api/hooks';
import { SearchableSelect } from '@gemek/ui';
import { apiClient } from '../api/client';

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

  const [assignedUserId, setAssignedUserId] = useState('');
  const [assignedContractorId, setAssignedContractorId] = useState('');
  const [userMap, setUserMap] = useState<Record<string, any>>({});
  const [contractorMap, setContractorMap] = useState<Record<string, any>>({});
  const [scheduledDate, setScheduledDate] = useState('');
  const [assignNotes, setAssignNotes] = useState('');
  const [actionError, setActionError] = useState('');

  const [newStatus, setNewStatus] = useState('');
  const [statusNotes, setStatusNotes] = useState('');

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
    setActionError('');
  };

  const handleContractorChange = (cid: string) => {
    setAssignedContractorId(cid);
    if (cid) setAssignedUserId('');
    setActionError('');
  };

  const handleAssign = async (e: React.FormEvent) => {
    e.preventDefault();
    setActionError('');
    if (!assignedUserId && !assignedContractorId) { setActionError('Chọn nhân viên hoặc nhà thầu'); return; }
    const payload: Record<string, unknown> = {
      assignedToUserId: assignedUserId || null,
      assignedToContractorId: assignedContractorId || null,
      scheduledDate: scheduledDate || null,
      notes: assignNotes || null,
    };
    try {
      await assignTicket.mutateAsync({ id: id!, data: payload });
      setAssignedUserId(''); setAssignedContractorId(''); setScheduledDate(''); setAssignNotes('');
    } catch (err: any) { setActionError(err?.response?.data?.message ?? 'Không thể phân công'); }
  };

  const handleStatusUpdate = async (e: React.FormEvent) => {
    e.preventDefault();
    setActionError('');
    if (!newStatus) { setActionError('Select a status'); return; }
    try {
      await updateStatus.mutateAsync({ id: id!, data: { status: newStatus, notes: statusNotes } });
      setNewStatus(''); setStatusNotes('');
    } catch (err: any) { setActionError(err?.response?.data?.message ?? 'Failed to update status'); }
  };

  if (isLoading) return <div className="flex items-center justify-center h-64"><svg className="animate-spin h-8 w-8 text-blue-600" fill="none" viewBox="0 0 24 24"><circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"/><path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/></svg></div>;
  if (isError) return <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700">Failed to load ticket.</div>;

  const isMaintenanceRepair = ticket.category === 'MAINTENANCE_REPAIR';

  return (
    <div className="max-w-4xl">
      <button onClick={() => navigate(-1)} className="text-sm text-blue-600 hover:underline mb-4 flex items-center gap-1">
        &larr; Back to Tickets
      </button>
      <div className="bg-white rounded-lg border border-gray-200 p-6 mb-4">
        <div className="flex items-start justify-between mb-4">
          <div>
            <h1 className="text-xl font-bold text-gray-900">{ticket.title}</h1>
            <p className="text-xs text-gray-400 mt-1 font-mono">{ticket.id}</p>
          </div>
          <span className={`inline-flex px-3 py-1 rounded-full text-sm font-medium ${STATUS_COLORS[ticket.status] ?? 'bg-gray-100 text-gray-700'}`}>{ticket.status}</span>
        </div>
        <div className="grid grid-cols-3 gap-4 text-sm mb-4">
          <div><span className="text-gray-500">Category:</span> <span className="font-medium">{ticket.category}</span></div>
          <div><span className="text-gray-500">Priority:</span> <span className="font-medium">{ticket.priority}</span></div>
          <div><span className="text-gray-500">Apartment:</span> <span className="font-medium">{ticket.apartment?.unitNumber} - {ticket.apartment?.block?.name}</span></div>
          <div><span className="text-gray-500">Submitted by:</span> <span className="font-medium">{ticket.submittedBy?.fullName}</span></div>
          <div><span className="text-gray-500">Assignee:</span> <span className="font-medium">{ticket.assignedToUser?.fullName ?? ticket.assignedToContractor?.companyName ?? '—'}</span></div>
          <div><span className="text-gray-500">SLA:</span> <span className={`font-medium ${ticket.slaBreached ? 'text-red-600' : ''}`}>{ticket.slaDeadline ? new Date(ticket.slaDeadline).toLocaleString() : '—'}{ticket.slaBreached && ' ⚠'}</span></div>
        </div>
        {ticket.description && <div className="bg-gray-50 rounded-md p-4 text-sm text-gray-700 mb-4">{ticket.description}</div>}
        {ticket.rating && <div className="text-sm"><span className="text-gray-500">Rating:</span> <span className="font-medium">{'★'.repeat(ticket.rating)}{'☆'.repeat(5 - ticket.rating)}</span> {ticket.ratingComment && <span className="text-gray-500 ml-2">"{ticket.ratingComment}"</span>}</div>}
      </div>

      {/* Photos */}
      {ticket.photos?.length > 0 && (
        <div className="bg-white rounded-lg border border-gray-200 p-6 mb-4">
          <h2 className="text-base font-semibold mb-3">Photos</h2>
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
        <h2 className="text-base font-semibold mb-3">Status History</h2>
        <div className="space-y-3">
          {ticket.statusHistory?.map((h: any) => (
            <div key={h.id} className="flex items-start gap-3 text-sm">
              <div className="w-2 h-2 rounded-full bg-blue-500 mt-1.5 flex-shrink-0" />
              <div>
                <span className="font-medium">{h.oldStatus ?? 'Created'} → {h.newStatus}</span>
                <span className="text-gray-400 ml-2">by {h.changedBy?.fullName}</span>
                <span className="text-gray-400 ml-2">{new Date(h.changedAt).toLocaleString()}</span>
                {h.notes && <p className="text-gray-500 mt-0.5">{h.notes}</p>}
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Admin Actions */}
      <div className="grid grid-cols-2 gap-4">
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
                <label className="block text-sm font-medium text-gray-700 mb-1">Nhà thầu <span className="text-xs text-gray-400">(chỉ MAINTENANCE_REPAIR)</span></label>
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
              <input type="datetime-local" value={scheduledDate} onChange={(e) => setScheduledDate(e.target.value)} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Ghi chú</label>
              <textarea value={assignNotes} onChange={(e) => setAssignNotes(e.target.value)} rows={2} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm resize-none" />
            </div>
            {actionError && <p className="text-xs text-red-600">{actionError}</p>}
            <button type="submit" disabled={assignTicket.isPending} className="w-full bg-blue-600 text-white rounded-md py-2 text-sm hover:bg-blue-700 disabled:opacity-50">
              {assignTicket.isPending ? 'Đang phân công...' : 'Phân công'}
            </button>
          </form>
        </div>
        <div className="bg-white rounded-lg border border-gray-200 p-6">
          <h2 className="text-base font-semibold mb-3">Update Status</h2>
          <form onSubmit={handleStatusUpdate} className="space-y-3">
            <div><label className="block text-sm font-medium text-gray-700 mb-1">New Status</label>
              <select value={newStatus} onChange={(e) => setNewStatus(e.target.value)} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm bg-white">
                <option value="">Select status...</option>
                <option value="IN_PROGRESS">In Progress</option>
                <option value="DONE">Done</option>
                <option value="CANCELLED">Cancelled</option>
              </select></div>
            <div><label className="block text-sm font-medium text-gray-700 mb-1">Notes</label>
              <textarea value={statusNotes} onChange={(e) => setStatusNotes(e.target.value)} rows={2} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" /></div>
            <button type="submit" disabled={updateStatus.isPending} className="w-full bg-green-600 text-white rounded-md py-2 text-sm hover:bg-green-700 disabled:opacity-50">
              {updateStatus.isPending ? 'Updating...' : 'Update Status'}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}
