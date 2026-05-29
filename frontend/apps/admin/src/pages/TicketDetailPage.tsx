import React, { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useTicket, useAssignTicket, useUpdateTicketStatus } from '../api/hooks';

const STATUS_COLORS: Record<string, string> = {
  NEW: 'bg-blue-100 text-blue-700', ASSIGNED: 'bg-purple-100 text-purple-700',
  IN_PROGRESS: 'bg-yellow-100 text-yellow-700', DONE: 'bg-green-100 text-green-700',
  CANCELLED: 'bg-gray-100 text-gray-700',
};

export function TicketDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data: ticket, isLoading, isError } = useTicket(id!);
  const assignTicket = useAssignTicket();
  const updateStatus = useUpdateTicketStatus();
  const [assignUserId, setAssignUserId] = useState('');
  const [newStatus, setNewStatus] = useState('');
  const [statusNotes, setStatusNotes] = useState('');
  const [actionError, setActionError] = useState('');

  if (isLoading) return <div className="flex items-center justify-center h-64"><svg className="animate-spin h-8 w-8 text-blue-600" fill="none" viewBox="0 0 24 24"><circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"/><path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/></svg></div>;
  if (isError) return <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700">Failed to load ticket.</div>;

  const handleAssign = async (e: React.FormEvent) => {
    e.preventDefault();
    setActionError('');
    if (!assignUserId.trim()) { setActionError('User ID is required'); return; }
    try {
      await assignTicket.mutateAsync({ id: id!, data: { assignedToUserId: assignUserId, scheduledDate: null, notes: null } });
      setAssignUserId('');
    } catch (err: any) { setActionError(err?.response?.data?.message ?? 'Failed to assign'); }
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
          <h2 className="text-base font-semibold mb-3">Assign Ticket</h2>
          <form onSubmit={handleAssign} className="space-y-3">
            <div><label className="block text-sm font-medium text-gray-700 mb-1">User ID</label>
              <input value={assignUserId} onChange={(e) => setAssignUserId(e.target.value)} placeholder="Staff user UUID" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" /></div>
            {actionError && <p className="text-xs text-red-600">{actionError}</p>}
            <button type="submit" disabled={assignTicket.isPending} className="w-full bg-blue-600 text-white rounded-md py-2 text-sm hover:bg-blue-700 disabled:opacity-50">
              {assignTicket.isPending ? 'Assigning...' : 'Assign'}
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
