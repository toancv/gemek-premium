import React, { useState } from 'react';
import { useParkingSlots, useGuestVehicles, useCreateParkingAssignment, useEndParkingAssignment } from '../api/hooks';

const SLOT_COLORS: Record<string, string> = {
  AVAILABLE: 'bg-green-100 text-green-700',
  OCCUPIED: 'bg-red-100 text-red-700',
  RESERVED: 'bg-yellow-100 text-yellow-700',
};

export function ParkingPage() {
  const [tab, setTab] = useState<'slots' | 'guests'>('slots');
  const [type, setType] = useState('');
  const [status, setStatus] = useState('');
  const [showAssign, setShowAssign] = useState<any>(null);
  const [formError, setFormError] = useState('');

  const { data: slotsData, isLoading } = useParkingSlots({ size: 50, ...(type && { type }), ...(status && { status }) });
  const { data: guestsData, isLoading: gLoading } = useGuestVehicles({ size: 50 });
  const createAssignment = useCreateParkingAssignment();
  const endAssignment = useEndParkingAssignment();

  const handleAssign = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError('');
    const fd = new FormData(e.target as HTMLFormElement);
    const vehicleId = (fd.get('vehicleId') as string).trim();
    const apartmentId = (fd.get('apartmentId') as string).trim();
    const startDate = fd.get('startDate') as string;
    if (!vehicleId || !apartmentId || !startDate) { setFormError('Vehicle ID, Apartment ID and Start Date are required'); return; }
    try {
      await createAssignment.mutateAsync({ parkingSlotId: showAssign.id, vehicleId, apartmentId, startDate, endDate: fd.get('endDate') || null, parkingCardNumber: fd.get('parkingCardNumber') || null });
      setShowAssign(null);
    } catch (err: any) { setFormError(err?.response?.data?.message ?? 'Failed'); }
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Parking</h1>
      </div>
      <div className="flex gap-4 mb-4 border-b border-gray-200">
        <button onClick={() => setTab('slots')} className={`pb-3 text-sm font-medium border-b-2 ${tab === 'slots' ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'}`}>Parking Slots</button>
        <button onClick={() => setTab('guests')} className={`pb-3 text-sm font-medium border-b-2 ${tab === 'guests' ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'}`}>Guest Vehicles</button>
      </div>

      {tab === 'slots' && (
        <>
          <div className="bg-white rounded-lg border border-gray-200 p-4 mb-4 flex gap-3">
            <select value={type} onChange={(e) => setType(e.target.value)} className="border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none">
              <option value="">All Types</option>
              <option value="CAR">Car</option>
              <option value="MOTORBIKE">Motorbike</option>
              <option value="BICYCLE">Bicycle</option>
            </select>
            <select value={status} onChange={(e) => setStatus(e.target.value)} className="border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none">
              <option value="">All Statuses</option>
              <option value="AVAILABLE">Available</option>
              <option value="OCCUPIED">Occupied</option>
              <option value="RESERVED">Reserved</option>
            </select>
          </div>
          <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 border-b border-gray-200">
                <tr>
                  <th className="text-left px-4 py-3 font-medium text-gray-500">Slot</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-500">Zone</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-500">Type</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-500">Status</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-500">Assigned To</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-500">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {isLoading && <tr><td colSpan={6} className="text-center py-8 text-gray-400">Loading...</td></tr>}
                {!isLoading && !slotsData?.data?.length && <tr><td colSpan={6} className="text-center py-8 text-gray-400">No slots found</td></tr>}
                {slotsData?.data?.map((s: any) => (
                  <tr key={s.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3 font-medium">{s.slotNumber}</td>
                    <td className="px-4 py-3">{s.zone}</td>
                    <td className="px-4 py-3">{s.type}</td>
                    <td className="px-4 py-3"><span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${SLOT_COLORS[s.status] ?? 'bg-gray-100 text-gray-700'}`}>{s.status}</span></td>
                    <td className="px-4 py-3 text-gray-500">{s.currentAssignment ? `${s.currentAssignment.vehicle?.licensePlate} (${s.currentAssignment.apartment?.unitNumber})` : '—'}</td>
                    <td className="px-4 py-3 flex gap-2">
                      {s.status === 'AVAILABLE' && <button onClick={() => { setShowAssign(s); setFormError(''); }} className="text-blue-600 hover:underline text-xs">Assign</button>}
                      {s.currentAssignment && <button onClick={() => { if (window.confirm('End this assignment?')) endAssignment.mutate({ id: s.currentAssignment.id, data: { endDate: new Date().toISOString().split('T')[0] } }); }} className="text-red-600 hover:underline text-xs">Unassign</button>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}

      {tab === 'guests' && (
        <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="text-left px-4 py-3 font-medium text-gray-500">License Plate</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">Owner</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">Host Apartment</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">Entry Time</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">Exit Time</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">Purpose</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {gLoading && <tr><td colSpan={6} className="text-center py-8 text-gray-400">Loading...</td></tr>}
              {!gLoading && !guestsData?.data?.length && <tr><td colSpan={6} className="text-center py-8 text-gray-400">No guest vehicles</td></tr>}
              {guestsData?.data?.map((g: any) => (
                <tr key={g.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3 font-medium">{g.licensePlate}</td>
                  <td className="px-4 py-3">{g.ownerName ?? '—'}</td>
                  <td className="px-4 py-3">{g.hostApartment?.unitNumber}</td>
                  <td className="px-4 py-3">{g.entryTime ? new Date(g.entryTime).toLocaleString() : '—'}</td>
                  <td className="px-4 py-3">{g.exitTime ? new Date(g.exitTime).toLocaleString() : <span className="text-green-600">Still inside</span>}</td>
                  <td className="px-4 py-3 text-gray-500">{g.purpose ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {showAssign && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/50" onClick={() => setShowAssign(null)} />
          <div className="relative bg-white rounded-lg shadow-xl w-full max-w-md p-6">
            <h2 className="text-lg font-semibold mb-4">Assign Slot {showAssign.slotNumber}</h2>
            <form onSubmit={handleAssign} className="space-y-3">
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Vehicle ID <span className="text-red-500">*</span></label>
                <input name="vehicleId" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" placeholder="Vehicle UUID" /></div>
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Apartment ID <span className="text-red-500">*</span></label>
                <input name="apartmentId" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" placeholder="Apartment UUID" /></div>
              <div className="grid grid-cols-2 gap-3">
                <div><label className="block text-sm font-medium text-gray-700 mb-1">Start Date <span className="text-red-500">*</span></label>
                  <input name="startDate" type="date" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" /></div>
                <div><label className="block text-sm font-medium text-gray-700 mb-1">End Date</label>
                  <input name="endDate" type="date" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" /></div>
              </div>
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Parking Card Number</label>
                <input name="parkingCardNumber" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" /></div>
              {formError && <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded">{formError}</p>}
              <div className="flex gap-2 justify-end pt-2">
                <button type="button" onClick={() => setShowAssign(null)} className="px-4 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50">Cancel</button>
                <button type="submit" disabled={createAssignment.isPending} className="px-4 py-2 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50">
                  {createAssignment.isPending ? 'Assigning...' : 'Assign'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
