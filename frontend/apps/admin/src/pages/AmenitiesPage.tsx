import React, { useState } from 'react';
import { getVnErrorMessage } from '@gemek/ui';
import { useAmenities, useAmenityBookings, useApproveBooking, useRejectBooking, useCreateAmenity, useUpdateAmenity } from '../api/hooks';

export function AmenitiesPage() {
  const [tab, setTab] = useState<'amenities' | 'bookings'>('amenities');
  const [modal, setModal] = useState<null | 'create' | any>(null);
  const [formError, setFormError] = useState('');
  const [approveError, setApproveError] = useState('');
  const [rejectReason, setRejectReason] = useState('');
  const [rejectId, setRejectId] = useState<string | null>(null);
  const [rejectError, setRejectError] = useState('');

  const { data: amenitiesData, isLoading, isError } = useAmenities();
  const { data: bookingsData, isLoading: bLoading } = useAmenityBookings({ status: 'PENDING', size: 50 });
  const create = useCreateAmenity();
  const update = useUpdateAmenity();
  const approve = useApproveBooking();
  const reject = useRejectBooking();
  const isEdit = modal && modal !== 'create';

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError('');
    const fd = new FormData(e.target as HTMLFormElement);
    const payload = { name: fd.get('name'), location: fd.get('location'), capacity: Number(fd.get('capacity')), openingTime: fd.get('openingTime'), closingTime: fd.get('closingTime'), maxDailyBookingsPerResident: Number(fd.get('maxDailyBookingsPerResident')), requiresApproval: fd.get('requiresApproval') === 'true' };
    if (!payload.name) { setFormError('Tên tiện ích là bắt buộc.'); return; }
    try {
      if (isEdit) await update.mutateAsync({ id: modal.id, data: payload });
      else await create.mutateAsync(payload);
      setModal(null);
    } catch (err: any) { setFormError(getVnErrorMessage(err?.response?.data?.error)); }
  };

  const handleApprove = async (id: string) => {
    setApproveError('');
    try {
      await approve.mutateAsync(id);
    } catch (err: any) {
      setApproveError(getVnErrorMessage(err?.response?.data?.error));
    }
  };

  const handleReject = async () => {
    if (!rejectReason.trim()) return;
    setRejectError('');
    try {
      await reject.mutateAsync({ id: rejectId!, reason: rejectReason });
      setRejectId(null);
      setRejectReason('');
    } catch (err: any) {
      setRejectError(getVnErrorMessage(err?.response?.data?.error));
    }
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Amenities</h1>
        {tab === 'amenities' && <button onClick={() => { setModal('create'); setFormError(''); }} className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-md text-sm font-medium">Add Amenity</button>}
      </div>
      <div className="flex gap-4 mb-4 border-b border-gray-200">
        <button onClick={() => setTab('amenities')} className={`pb-3 text-sm font-medium border-b-2 transition-colors ${tab === 'amenities' ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'}`}>Amenities</button>
        <button onClick={() => setTab('bookings')} className={`pb-3 text-sm font-medium border-b-2 transition-colors ${tab === 'bookings' ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'}`}>
          Pending Bookings {bookingsData?.data?.length > 0 && <span className="ml-1 bg-red-500 text-white text-xs rounded-full px-1.5">{bookingsData.data.length}</span>}
        </button>
      </div>

      {isError && <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 mb-4">Không thể tải dữ liệu.</div>}

      {tab === 'amenities' && (
        <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="text-left px-4 py-3 font-medium text-gray-500">Name</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">Location</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">Capacity</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">Hours</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">Requires Approval</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {isLoading && <tr><td colSpan={6} className="text-center py-8 text-gray-400">Loading...</td></tr>}
              {!isLoading && !amenitiesData?.data?.length && <tr><td colSpan={6} className="text-center py-8 text-gray-400">No amenities found</td></tr>}
              {amenitiesData?.data?.map((a: any) => (
                <tr key={a.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3 font-medium">{a.name}</td>
                  <td className="px-4 py-3 text-gray-500">{a.location ?? '—'}</td>
                  <td className="px-4 py-3">{a.capacity}</td>
                  <td className="px-4 py-3">{a.openingTime} - {a.closingTime}</td>
                  <td className="px-4 py-3"><span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${a.requiresApproval ? 'bg-yellow-100 text-yellow-700' : 'bg-green-100 text-green-700'}`}>{a.requiresApproval ? 'Yes' : 'No'}</span></td>
                  <td className="px-4 py-3"><button onClick={() => { setModal(a); setFormError(''); }} className="text-blue-600 hover:underline text-xs">Edit</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {tab === 'bookings' && (
        <div>
          {approveError && <div className="bg-red-50 border border-red-200 rounded-lg p-3 text-sm text-red-700 mb-3">{approveError}</div>}
          <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 border-b border-gray-200">
                <tr>
                  <th className="text-left px-4 py-3 font-medium text-gray-500">Amenity</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-500">Resident</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-500">Apartment</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-500">Date</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-500">Time</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-500">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {bLoading && <tr><td colSpan={6} className="text-center py-8 text-gray-400">Loading...</td></tr>}
                {!bLoading && !bookingsData?.data?.length && <tr><td colSpan={6} className="text-center py-8 text-gray-400">No pending bookings</td></tr>}
                {bookingsData?.data?.map((b: any) => (
                  <tr key={b.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3 font-medium">{b.amenity?.name}</td>
                    <td className="px-4 py-3">{b.resident?.user?.fullName}</td>
                    <td className="px-4 py-3">{b.apartment?.unitNumber}</td>
                    <td className="px-4 py-3">{b.bookingDate}</td>
                    <td className="px-4 py-3">{b.startTime} - {b.endTime}</td>
                    <td className="px-4 py-3 flex gap-2">
                      <button onClick={() => handleApprove(b.id)} disabled={approve.isPending} className="text-green-600 hover:underline text-xs disabled:opacity-50">Approve</button>
                      <button onClick={() => { setRejectId(b.id); setRejectError(''); }} className="text-red-600 hover:underline text-xs">Reject</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Reject dialog */}
      {rejectId && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/50" onClick={() => { setRejectId(null); setRejectError(''); }} />
          <div className="relative bg-white rounded-lg shadow-xl w-full max-w-sm p-6">
            <h2 className="text-lg font-semibold mb-3">Reject Booking</h2>
            <label className="block text-sm font-medium text-gray-700 mb-1">Reason <span className="text-red-500">*</span></label>
            <textarea value={rejectReason} onChange={(e) => setRejectReason(e.target.value)} rows={3} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm mb-3" />
            {rejectError && <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded mb-3">{rejectError}</p>}
            <div className="flex gap-2 justify-end">
              <button onClick={() => { setRejectId(null); setRejectReason(''); setRejectError(''); }} className="px-4 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50">Cancel</button>
              <button onClick={handleReject} disabled={reject.isPending || !rejectReason.trim()} className="px-4 py-2 text-sm bg-red-600 text-white rounded-md hover:bg-red-700 disabled:opacity-50">
                {reject.isPending ? 'Rejecting...' : 'Reject'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Create/Edit Amenity modal */}
      {modal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/50" onClick={() => setModal(null)} />
          <div className="relative bg-white rounded-lg shadow-xl w-full max-w-md p-6">
            <h2 className="text-lg font-semibold mb-4">{isEdit ? 'Edit' : 'Add'} Amenity</h2>
            <form onSubmit={handleSubmit} className="space-y-3">
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Name <span className="text-red-500">*</span></label>
                <input name="name" defaultValue={isEdit ? modal.name : ''} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" /></div>
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Location</label>
                <input name="location" defaultValue={isEdit ? modal.location ?? '' : ''} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" /></div>
              <div className="grid grid-cols-2 gap-3">
                <div><label className="block text-sm font-medium text-gray-700 mb-1">Capacity</label>
                  <input name="capacity" type="number" defaultValue={isEdit ? modal.capacity : 10} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" /></div>
                <div><label className="block text-sm font-medium text-gray-700 mb-1">Max bookings/resident/day</label>
                  <input name="maxDailyBookingsPerResident" type="number" defaultValue={isEdit ? modal.maxDailyBookingsPerResident : 1} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" /></div>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div><label className="block text-sm font-medium text-gray-700 mb-1">Opening Time</label>
                  <input name="openingTime" type="time" defaultValue={isEdit ? modal.openingTime : '06:00'} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" /></div>
                <div><label className="block text-sm font-medium text-gray-700 mb-1">Closing Time</label>
                  <input name="closingTime" type="time" defaultValue={isEdit ? modal.closingTime : '22:00'} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" /></div>
              </div>
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Requires Approval</label>
                <select name="requiresApproval" defaultValue={isEdit ? String(modal.requiresApproval) : 'false'} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm bg-white">
                  <option value="false">No</option><option value="true">Yes</option>
                </select></div>
              {formError && <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded">{formError}</p>}
              <div className="flex gap-2 justify-end pt-2">
                <button type="button" onClick={() => setModal(null)} className="px-4 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50">Cancel</button>
                <button type="submit" disabled={create.isPending || update.isPending} className="px-4 py-2 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50">
                  {create.isPending || update.isPending ? 'Saving...' : 'Save'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
