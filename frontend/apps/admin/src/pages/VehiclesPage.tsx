import React, { useState, useCallback } from 'react';
import { useVehicles, useCreateVehicle } from '../api/hooks';
import { SearchableSelect, getVnErrorMessage, labelFor } from '@gemek/ui';
import { apiClient } from '../api/client';
import { t } from '../i18n/vi';

const VEHICLE_TYPES = ['CAR', 'MOTORBIKE', 'BICYCLE', 'OTHER'];

export function VehiclesPage() {
  const [filterType, setFilterType] = useState('');
  const [filterActive, setFilterActive] = useState('');
  const { data, isLoading } = useVehicles({
    size: 50,
    ...(filterType && { type: filterType }),
    ...(filterActive !== '' && { isActive: filterActive }),
  });
  const createVehicle = useCreateVehicle();

  const [showCreate, setShowCreate] = useState(false);
  const [formError, setFormError] = useState('');
  const [selectedResidentId, setSelectedResidentId] = useState('');
  const [derivedApartment, setDerivedApartment] = useState<any>(null);
  const [residentError, setResidentError] = useState('');
  const [residentMap, setResidentMap] = useState<Record<string, any>>({});

  const loadResidentOptions = useCallback(async (query: string) => {
    const params: Record<string, unknown> = { size: 20, isActive: true };
    if (query) params.search = query;
    const res = await apiClient.get('/residents', { params });
    const residents: any[] = res.data?.data ?? [];
    setResidentMap((prev) => {
      const next = { ...prev };
      residents.forEach((r) => { next[r.id] = r; });
      return next;
    });
    return residents.map((r) => ({
      value: r.id,
      label: `${r.user?.fullName ?? ''} — ${r.apartment?.block?.name ? r.apartment.block.name + ' / ' : ''}${r.apartment?.unitNumber ?? ''}`,
    }));
  }, []);

  const handleResidentChange = (id: string) => {
    setSelectedResidentId(id);
    setResidentError('');
    const resident = residentMap[id];
    setDerivedApartment(resident?.apartment ?? null);
  };

  const resetForm = () => {
    setSelectedResidentId('');
    setDerivedApartment(null);
    setResidentError('');
    setFormError('');
  };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError('');
    if (!selectedResidentId) { setResidentError('Vui lòng chọn cư dân'); return; }
    if (!derivedApartment?.id) { setFormError('Không tìm thấy thông tin căn hộ'); return; }
    const fd = new FormData(e.target as HTMLFormElement);
    const licensePlate = (fd.get('licensePlate') as string).trim();
    const type = fd.get('type') as string;
    if (!licensePlate || !type) { setFormError('Biển số và loại phương tiện là bắt buộc'); return; }
    try {
      await createVehicle.mutateAsync({
        residentId: selectedResidentId,
        apartmentId: derivedApartment.id,
        type,
        licensePlate,
        brand: (fd.get('brand') as string) || null,
        model: (fd.get('model') as string) || null,
        color: (fd.get('color') as string) || null,
        notes: (fd.get('notes') as string) || null,
      });
      setShowCreate(false);
      resetForm();
    } catch (err: any) {
      setFormError(getVnErrorMessage(err?.response?.data?.error));
    }
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">{t('vehicles.title')}</h1>
        <button
          onClick={() => { setShowCreate(true); resetForm(); }}
          className="px-4 py-2 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700"
        >
          {t('vehicles.add')}
        </button>
      </div>

      <div className="bg-white rounded-lg border border-gray-200 p-4 mb-4 flex gap-3">
        <select value={filterType} onChange={(e) => setFilterType(e.target.value)} className="border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none">
          <option value="">{t('vehicles.allTypes')}</option>
          {/* `vt` not `t` — would shadow the i18n t() import */}
          {VEHICLE_TYPES.map((vt) => <option key={vt} value={vt}>{labelFor('VehicleType', vt)}</option>)}
        </select>
        <select value={filterActive} onChange={(e) => setFilterActive(e.target.value)} className="border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none">
          <option value="">{t('vehicles.allStatuses')}</option>
          {/* values are boolean strings for the isActive filter — labels via ActiveStatus map */}
          <option value="true">{labelFor('ActiveStatus', 'ACTIVE')}</option>
          <option value="false">{labelFor('ActiveStatus', 'INACTIVE')}</option>
        </select>
      </div>

      <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 border-b border-gray-200">
            <tr>
              <th className="text-left px-4 py-3 font-medium text-gray-500">{t('vehicles.licensePlate')}</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">{t('vehicles.type')}</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">{t('vehicles.brandModel')}</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">{t('vehicles.color')}</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">{t('vehicles.resident')}</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">{t('vehicles.apartment')}</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">{t('status')}</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {isLoading && <tr><td colSpan={7} className="text-center py-8 text-gray-400">{t('loading')}</td></tr>}
            {!isLoading && !data?.data?.length && <tr><td colSpan={7} className="text-center py-8 text-gray-400">{t('emptyFound', { item: 'phương tiện' })}</td></tr>}
            {data?.data?.map((v: any) => (
              <tr key={v.id} className="hover:bg-gray-50">
                <td className="px-4 py-3 font-medium">{v.licensePlate}</td>
                <td className="px-4 py-3">{labelFor('VehicleType', v.type)}</td>
                <td className="px-4 py-3 text-gray-500">{[v.brand, v.model].filter(Boolean).join(' ') || '—'}</td>
                <td className="px-4 py-3 text-gray-500">{v.color || '—'}</td>
                <td className="px-4 py-3">{v.resident?.user?.fullName ?? '—'}</td>
                <td className="px-4 py-3">
                  {v.apartment
                    ? `${v.apartment.block?.name ? v.apartment.block.name + ' / ' : ''}${v.apartment.unitNumber}`
                    : '—'}
                </td>
                <td className="px-4 py-3">
                  <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${v.isActive ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-500'}`}>
                    {labelFor('ActiveStatus', v.isActive ? 'ACTIVE' : 'INACTIVE')}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {showCreate && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/50" onClick={() => { setShowCreate(false); resetForm(); }} />
          <div className="relative bg-white rounded-lg shadow-xl w-full max-w-md p-6 max-h-[90vh] overflow-y-auto">
            <h2 className="text-lg font-semibold mb-4">{t('vehicles.addTitle')}</h2>
            <form onSubmit={handleCreate} className="space-y-3">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Cư dân <span className="text-red-500">*</span>
                </label>
                <SearchableSelect
                  value={selectedResidentId}
                  onChange={handleResidentChange}
                  loadOptions={loadResidentOptions}
                  placeholder="Tìm theo tên hoặc email..."
                  error={residentError}
                />
              </div>

              {derivedApartment && (
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Căn hộ</label>
                  <div className="px-3 py-2 bg-gray-50 border border-gray-200 rounded-md text-sm text-gray-700">
                    {derivedApartment.block?.name ? `${derivedApartment.block.name} / ` : ''}{derivedApartment.unitNumber}
                  </div>
                </div>
              )}

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Loại phương tiện <span className="text-red-500">*</span>
                </label>
                <select name="type" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                  {VEHICLE_TYPES.map((vt) => <option key={vt} value={vt}>{labelFor('VehicleType', vt)}</option>)}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Biển số <span className="text-red-500">*</span>
                </label>
                <input name="licensePlate" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" placeholder="51A-123.45" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Hãng xe</label>
                <input name="brand" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Model</label>
                <input name="model" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Màu</label>
                <input name="color" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Ghi chú</label>
                <input name="notes" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>

              {formError && <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded">{formError}</p>}
              <div className="flex gap-2 justify-end pt-2">
                <button type="button" onClick={() => { setShowCreate(false); resetForm(); }} className="px-4 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50">
                  Hủy
                </button>
                <button type="submit" disabled={createVehicle.isPending} className="px-4 py-2 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50">
                  {createVehicle.isPending ? 'Đang tạo...' : 'Tạo'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
