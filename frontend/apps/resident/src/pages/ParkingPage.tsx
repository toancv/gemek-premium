import React, { useState } from 'react';
import { useParkingAssignments } from '../api/hooks';
import { apiClient } from '../api/client';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { getVnErrorMessage } from '@gemek/ui';

export function ParkingPage() {
  const { data, isLoading } = useParkingAssignments();
  const qc = useQueryClient();
  const [showGuestForm, setShowGuestForm] = useState(false);
  const [formError, setFormError] = useState('');
  const logGuest = useMutation({
    mutationFn: (data: unknown) => apiClient.post('/parking/guest-vehicles', data).then((r) => r.data),
    meta: { skipErrorToast: true, successMessage: 'Đã ghi nhận xe khách.' },
    onSuccess: () => { setShowGuestForm(false); qc.invalidateQueries({ queryKey: ['my-parking'] }); },
  });

  const handleLogGuest = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError('');
    const fd = new FormData(e.target as HTMLFormElement);
    const licensePlate = (fd.get('licensePlate') as string).trim();
    const hostApartmentId = (fd.get('hostApartmentId') as string).trim();
    if (!licensePlate || !hostApartmentId) { setFormError('Biển số và mã căn hộ là bắt buộc'); return; }
    try {
      await logGuest.mutateAsync({ licensePlate, ownerName: fd.get('ownerName') || null, hostApartmentId, purpose: fd.get('purpose') || null });
    } catch (err: any) { setFormError(getVnErrorMessage(err?.response?.data?.error)); }
  };

  return (
    <div className="p-4 space-y-4">
      <h1 className="text-lg font-bold text-gray-900">Parking</h1>

      {/* My parking slots */}
      <div>
        <h2 className="font-semibold text-gray-700 text-sm mb-2">My Parking Slots</h2>
        {isLoading && <div className="text-center py-4 text-gray-400">Loading...</div>}
        {!isLoading && !data?.data?.length && (
          <div className="bg-white rounded-xl border border-gray-200 p-4 text-center text-gray-400 text-sm">No parking slots assigned</div>
        )}
        <div className="space-y-3">
          {data?.data?.map((a: any) => (
            <div key={a.id} className="bg-white rounded-xl border border-gray-200 p-4">
              <p className="font-semibold text-gray-900">{a.parkingSlot?.slotNumber ?? 'Slot'}</p>
              <div className="text-sm text-gray-500 mt-1 space-y-0.5">
                <p>Zone: {a.parkingSlot?.zone ?? '—'} • Type: {a.parkingSlot?.type ?? '—'}</p>
                <p>Vehicle: {a.vehicle?.licensePlate ?? '—'}</p>
                <p>Card: {a.parkingCardNumber ?? '—'}</p>
                <p>Since: {a.startDate}</p>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Guest vehicle */}
      <div>
        <div className="flex items-center justify-between mb-2">
          <h2 className="font-semibold text-gray-700 text-sm">Xe khách</h2>
          <button onClick={() => { setShowGuestForm(true); setFormError(''); }} className="text-xs text-blue-600 font-medium">+ Ghi nhận xe khách</button>
        </div>
        <p className="text-xs text-gray-400">Ghi nhận xe khách vào tòa nhà</p>
      </div>

      {showGuestForm && (
        <div className="fixed inset-0 z-50 flex items-end justify-center">
          <div className="absolute inset-0 bg-black/50" onClick={() => setShowGuestForm(false)} />
          <div className="relative bg-white rounded-t-2xl w-full max-w-md p-6 pb-8">
            <h2 className="text-lg font-semibold mb-4">Ghi nhận xe khách</h2>
            <form onSubmit={handleLogGuest} className="space-y-3">
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Biển số <span className="text-red-500">*</span></label>
                <input name="licensePlate" className="block w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm" placeholder="51A-123.45" /></div>
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Tên chủ xe</label>
                <input name="ownerName" className="block w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm" /></div>
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Mã căn hộ <span className="text-red-500">*</span></label>
                <input name="hostApartmentId" className="block w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm" placeholder="UUID căn hộ" /></div>
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Mục đích</label>
                <input name="purpose" className="block w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm" /></div>
              {formError && <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded-lg">{formError}</p>}
              <div className="flex gap-2 pt-1">
                <button type="button" onClick={() => setShowGuestForm(false)} className="flex-1 py-2.5 text-sm border border-gray-300 rounded-lg">Hủy</button>
                <button type="submit" disabled={logGuest.isPending} className="flex-1 py-2.5 text-sm bg-blue-600 text-white rounded-lg disabled:opacity-50">
                  {logGuest.isPending ? 'Đang ghi nhận...' : 'Ghi nhận'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
