import React, { useState } from 'react';
import { useAmenities, useCreateBooking } from '../api/hooks';

export function AmenitiesPage() {
  const { data, isLoading } = useAmenities();
  const create = useCreateBooking();
  const [selected, setSelected] = useState<any>(null);
  const [formError, setFormError] = useState('');
  const [success, setSuccess] = useState(false);

  const handleBook = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError('');
    setSuccess(false);
    const fd = new FormData(e.target as HTMLFormElement);
    const bookingDate = fd.get('bookingDate') as string;
    const startTime = fd.get('startTime') as string;
    const endTime = fd.get('endTime') as string;
    if (!bookingDate || !startTime || !endTime) { setFormError('Date, start time and end time are required'); return; }
    try {
      await create.mutateAsync({ amenityId: selected.id, bookingDate, startTime, endTime, notes: fd.get('notes') || null });
      setSuccess(true);
      setTimeout(() => { setSelected(null); setSuccess(false); }, 1500);
    } catch (err: any) { setFormError(err?.response?.data?.message ?? 'Failed to book'); }
  };

  return (
    <div className="p-4">
      <h1 className="text-lg font-bold text-gray-900 mb-4">Book Amenity</h1>
      {isLoading && <div className="text-center py-8 text-gray-400">Loading...</div>}
      {!isLoading && !data?.data?.length && <div className="text-center py-8 text-gray-400">No amenities available</div>}
      <div className="space-y-3">
        {data?.data?.map((a: any) => (
          <div key={a.id} className="bg-white rounded-xl border border-gray-200 p-4">
            <div className="flex items-start justify-between">
              <div>
                <h3 className="font-semibold text-gray-900">{a.name}</h3>
                {a.location && <p className="text-xs text-gray-400 mt-0.5">{a.location}</p>}
                <p className="text-xs text-gray-500 mt-1">Capacity: {a.capacity} • {a.openingTime} - {a.closingTime}</p>
                {a.requiresApproval && <p className="text-xs text-orange-500 mt-0.5">Requires approval</p>}
              </div>
              <button onClick={() => { setSelected(a); setFormError(''); setSuccess(false); }}
                className="bg-blue-600 text-white px-3 py-1.5 rounded-lg text-sm font-medium flex-shrink-0">Book</button>
            </div>
          </div>
        ))}
      </div>

      {selected && (
        <div className="fixed inset-0 z-50 flex items-end justify-center">
          <div className="absolute inset-0 bg-black/50" onClick={() => setSelected(null)} />
          <div className="relative bg-white rounded-t-2xl w-full max-w-md p-6 pb-8">
            <h2 className="text-lg font-semibold mb-1">Book {selected.name}</h2>
            <p className="text-xs text-gray-400 mb-4">{selected.openingTime} - {selected.closingTime}</p>
            {success ? (
              <div className="text-center py-6">
                <p className="text-4xl mb-2">✅</p>
                <p className="font-medium text-green-700">Booking submitted!</p>
              </div>
            ) : (
              <form onSubmit={handleBook} className="space-y-3">
                <div><label className="block text-sm font-medium text-gray-700 mb-1">Date <span className="text-red-500">*</span></label>
                  <input name="bookingDate" type="date" min={new Date().toISOString().split('T')[0]} className="block w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm" /></div>
                <div className="grid grid-cols-2 gap-3">
                  <div><label className="block text-sm font-medium text-gray-700 mb-1">Start <span className="text-red-500">*</span></label>
                    <input name="startTime" type="time" min={selected.openingTime} max={selected.closingTime} className="block w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm" /></div>
                  <div><label className="block text-sm font-medium text-gray-700 mb-1">End <span className="text-red-500">*</span></label>
                    <input name="endTime" type="time" min={selected.openingTime} max={selected.closingTime} className="block w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm" /></div>
                </div>
                <div><label className="block text-sm font-medium text-gray-700 mb-1">Notes</label>
                  <textarea name="notes" rows={2} className="block w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm resize-none" /></div>
                {formError && <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded-lg">{formError}</p>}
                <div className="flex gap-2 pt-1">
                  <button type="button" onClick={() => setSelected(null)} className="flex-1 py-2.5 text-sm border border-gray-300 rounded-lg">Cancel</button>
                  <button type="submit" disabled={create.isPending} className="flex-1 py-2.5 text-sm bg-blue-600 text-white rounded-lg disabled:opacity-50">
                    {create.isPending ? 'Booking...' : 'Confirm'}
                  </button>
                </div>
              </form>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
