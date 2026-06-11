import React, { useState, useCallback } from 'react';
import { useParkingSlots, useGuestVehicles, useCreateParkingAssignment, useEndParkingAssignment } from '../api/hooks';
import { SearchableSelect, getVnErrorMessage, labelFor, formatVNDateTime } from '@gemek/ui';
import type { SearchableOption } from '@gemek/ui';
import { apiClient } from '../api/client';
import { t } from '../i18n/vi';

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
  const [endError, setEndError] = useState('');
  const [assignApartmentId, setAssignApartmentId] = useState('');
  const [assignVehicleId, setAssignVehicleId] = useState('');

  const { data: slotsData, isLoading } = useParkingSlots({ size: 50, ...(type && { type }), ...(status && { status }) });
  const { data: guestsData, isLoading: gLoading } = useGuestVehicles({ size: 50 });
  const createAssignment = useCreateParkingAssignment();
  const endAssignment = useEndParkingAssignment();

  const closeAssign = () => {
    setShowAssign(null);
    setAssignApartmentId('');
    setAssignVehicleId('');
    setFormError('');
  };

  const loadApartmentOptions = useCallback(async (query: string): Promise<SearchableOption[]> => {
    const params: Record<string, unknown> = { size: 10, sort: 'unitNumber', direction: 'asc' };
    if (query) params.search = query;
    const res = await apiClient.get('/apartments', { params });
    return (res.data?.data ?? []).map((a: any) => ({
      value: a.id,
      label: `${a.block?.name ?? ''} - ${a.unitNumber}`,
    }));
  }, []);

  // Filters vehicles by selected apartment — guarantees no vehicle/apartment mismatch on submit.
  const loadVehicleOptions = useCallback(async (query: string): Promise<SearchableOption[]> => {
    const params: Record<string, unknown> = { size: 10, isActive: true };
    if (assignApartmentId) params.apartmentId = assignApartmentId;
    if (query) params.search = query;
    const res = await apiClient.get('/vehicles', { params });
    return (res.data?.data ?? []).map((v: any) => {
      const suffix = [v.brand, v.model].filter(Boolean).join(' ');
      return {
        value: v.id,
        label: suffix ? `${v.licensePlate} · ${suffix}` : v.licensePlate,
      };
    });
  }, [assignApartmentId]);

  const handleAssign = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError('');
    const fd = new FormData(e.target as HTMLFormElement);
    const startDate = fd.get('startDate') as string;
    if (!assignVehicleId || !assignApartmentId || !startDate) {
      setFormError('Vui lòng chọn căn hộ, phương tiện và ngày bắt đầu.');
      return;
    }
    try {
      await createAssignment.mutateAsync({
        parkingSlotId: showAssign.id,
        vehicleId: assignVehicleId,
        apartmentId: assignApartmentId,
        startDate,
        parkingCardNumber: fd.get('parkingCardNumber') || null,
      });
      closeAssign();
    } catch (err: any) { setFormError(getVnErrorMessage(err?.response?.data?.error)); }
  };

  const handleEndAssignment = async (slotId: string) => {
    setEndError('');
    if (!window.confirm('Kết thúc phân công chỗ đậu xe này?')) return;
    try {
      await endAssignment.mutateAsync({ id: slotId, data: { endDate: new Date().toISOString().split('T')[0] } });
    } catch (err: any) {
      setEndError(getVnErrorMessage(err?.response?.data?.error));
    }
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">{t('parking.title')}</h1>
      </div>
      <div className="flex gap-4 mb-4 border-b border-gray-200">
        <button onClick={() => setTab('slots')} className={`pb-3 text-sm font-medium border-b-2 ${tab === 'slots' ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'}`}>{t('parking.tabSlots')}</button>
        <button onClick={() => setTab('guests')} className={`pb-3 text-sm font-medium border-b-2 ${tab === 'guests' ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'}`}>{t('parking.tabGuests')}</button>
      </div>

      {tab === 'slots' && (
        <>
          <div className="bg-white rounded-lg border border-gray-200 p-4 mb-4 flex gap-3">
            <select value={type} onChange={(e) => setType(e.target.value)} className="border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none">
              <option value="">{t('parking.allTypes')}</option>
              <option value="CAR">{labelFor('VehicleType', 'CAR')}</option>
              <option value="MOTORBIKE">{labelFor('VehicleType', 'MOTORBIKE')}</option>
              <option value="BICYCLE">{labelFor('VehicleType', 'BICYCLE')}</option>
            </select>
            <select value={status} onChange={(e) => setStatus(e.target.value)} className="border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none">
              <option value="">{t('parking.allStatuses')}</option>
              <option value="AVAILABLE">{labelFor('ParkingSlotStatus', 'AVAILABLE')}</option>
              <option value="OCCUPIED">{labelFor('ParkingSlotStatus', 'OCCUPIED')}</option>
              <option value="RESERVED">{labelFor('ParkingSlotStatus', 'RESERVED')}</option>
            </select>
          </div>
          {endError && <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded mb-3">{endError}</p>}
          <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 border-b border-gray-200">
                <tr>
                  <th className="text-left px-4 py-3 font-medium text-gray-500">{t('parking.slot')}</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-500">{t('parking.zone')}</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-500">{t('parking.type')}</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-500">{t('common.status')}</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-500">{t('parking.assignedTo')}</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-500">{t('actions')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {isLoading && <tr><td colSpan={6} className="text-center py-8 text-gray-400">{t('loading')}</td></tr>}
                {!isLoading && !slotsData?.data?.length && <tr><td colSpan={6} className="text-center py-8 text-gray-400">{t('emptyFound', { item: 'chỗ đậu xe' })}</td></tr>}
                {slotsData?.data?.map((s: any) => (
                  <tr key={s.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3 font-medium">{s.slotNumber}</td>
                    <td className="px-4 py-3">{s.zone}</td>
                    <td className="px-4 py-3">{labelFor('VehicleType', s.type)}</td>
                    <td className="px-4 py-3"><span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${SLOT_COLORS[s.status] ?? 'bg-gray-100 text-gray-700'}`}>{labelFor('ParkingSlotStatus', s.status)}</span></td>
                    <td className="px-4 py-3 text-gray-500">{s.currentAssignment ? `${s.currentAssignment.vehicle?.licensePlate} (${s.currentAssignment.apartment?.unitNumber})` : '—'}</td>
                    <td className="px-4 py-3 flex gap-2">
                      {s.status === 'AVAILABLE' && <button onClick={() => { setShowAssign(s); setFormError(''); }} className="text-blue-600 hover:underline text-xs">{t('parking.assign')}</button>}
                      {s.currentAssignment && <button onClick={() => handleEndAssignment(s.id)} className="text-red-600 hover:underline text-xs">{t('parking.unassign')}</button>}
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
                <th className="text-left px-4 py-3 font-medium text-gray-500">{t('parking.licensePlate')}</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">{t('parking.owner')}</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">{t('parking.hostApartment')}</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">{t('parking.entryTime')}</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">{t('parking.exitTime')}</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">{t('parking.purpose')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {gLoading && <tr><td colSpan={6} className="text-center py-8 text-gray-400">{t('loading')}</td></tr>}
              {!gLoading && !guestsData?.data?.length && <tr><td colSpan={6} className="text-center py-8 text-gray-400">{t('emptyYet', { item: 'xe khách' })}</td></tr>}
              {guestsData?.data?.map((g: any) => (
                <tr key={g.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3 font-medium">{g.licensePlate}</td>
                  <td className="px-4 py-3">{g.ownerName ?? '—'}</td>
                  <td className="px-4 py-3">{g.hostApartment?.unitNumber}</td>
                  <td className="px-4 py-3">{g.entryTime ? formatVNDateTime(g.entryTime) : '—'}</td>
                  <td className="px-4 py-3">{g.exitTime ? formatVNDateTime(g.exitTime) : <span className="text-green-600">{t('parking.stillInside')}</span>}</td>
                  <td className="px-4 py-3 text-gray-500">{g.purpose ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {showAssign && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/50" onClick={closeAssign} />
          <div className="relative bg-white rounded-lg shadow-xl w-full max-w-md p-6">
            <h2 className="text-lg font-semibold mb-4">{t('parking.assignModalTitle', { slotNumber: showAssign.slotNumber })}</h2>
            <form onSubmit={handleAssign} className="space-y-3">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('parking.apartment')} <span className="text-red-500">*</span></label>
                <SearchableSelect
                  value={assignApartmentId}
                  onChange={(val) => { setAssignApartmentId(val); setAssignVehicleId(''); }}
                  loadOptions={loadApartmentOptions}
                  placeholder={t('parking.searchApartment')}
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('parking.vehicle')} <span className="text-red-500">*</span></label>
                <SearchableSelect
                  value={assignVehicleId}
                  onChange={setAssignVehicleId}
                  loadOptions={loadVehicleOptions}
                  placeholder={assignApartmentId ? t('parking.searchVehicle') : t('parking.selectApartmentFirst')}
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('parking.startDate')} <span className="text-red-500">*</span></label>
                <input name="startDate" type="date" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('parking.cardNumber')}</label>
                <input name="parkingCardNumber" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" />
              </div>
              {formError && <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded">{formError}</p>}
              <div className="flex gap-2 justify-end pt-2">
                <button type="button" onClick={closeAssign} className="px-4 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50">{t('cancel')}</button>
                <button type="submit" disabled={createAssignment.isPending} className="px-4 py-2 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50">
                  {createAssignment.isPending ? t('parking.assigning') : t('parking.assign')}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
