import React, { useState } from 'react';
import { useMyResident, useCreateVehicle } from '../api/hooks';
import { getVnErrorMessage, labelFor } from '@gemek/ui';

const VEHICLE_TYPES = ['CAR', 'MOTORBIKE', 'BICYCLE', 'OTHER'];

export function MyVehiclesPage() {
  const { data: resident, isLoading: residentLoading } = useMyResident();
  const createVehicle = useCreateVehicle();
  const [showCreate, setShowCreate] = useState(false);
  const [formError, setFormError] = useState('');

  const residentId: string | undefined = resident?.id;
  const apartmentId: string | undefined = resident?.apartment?.id;
  const unitLabel = resident?.apartment
    ? `${resident.apartment.block?.name ? resident.apartment.block.name + ' / ' : ''}${resident.apartment.unitNumber}`
    : null;

  if (residentLoading) {
    return <div className="p-4 text-center text-gray-400 pt-10">Đang tải...</div>;
  }

  if (!resident || !apartmentId) {
    return (
      <div className="p-4 pt-6">
        <p className="text-red-600 text-sm bg-red-50 rounded-xl px-4 py-3">
          Không tìm thấy thông tin cư trú. Vui lòng liên hệ quản lý tòa nhà.
        </p>
      </div>
    );
  }

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError('');
    const fd = new FormData(e.target as HTMLFormElement);
    const licensePlate = (fd.get('licensePlate') as string).trim();
    const type = fd.get('type') as string;
    if (!licensePlate || !type) { setFormError('Biển số và loại phương tiện là bắt buộc'); return; }
    try {
      await createVehicle.mutateAsync({
        residentId,
        apartmentId,
        type,
        licensePlate,
        brand: (fd.get('brand') as string) || null,
        model: (fd.get('model') as string) || null,
        color: (fd.get('color') as string) || null,
        notes: (fd.get('notes') as string) || null,
      });
      setShowCreate(false);
      setFormError('');
      (e.target as HTMLFormElement).reset();
    } catch (err: any) {
      setFormError(getVnErrorMessage(err?.response?.data?.error));
    }
  };

  return (
    <div className="p-4 space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-lg font-bold text-gray-900">Phương tiện</h1>
        <button
          onClick={() => { setShowCreate(true); setFormError(''); }}
          className="text-sm text-blue-600 font-medium"
        >
          + Đăng ký
        </button>
      </div>

      <div className="bg-blue-50 rounded-xl px-4 py-3 text-sm text-blue-700">
        Căn hộ của bạn: <span className="font-semibold">{unitLabel}</span>
      </div>

      <p className="text-sm text-gray-500">
        Đăng ký phương tiện để được cấp thẻ xe và sử dụng bãi đỗ xe tòa nhà.
      </p>

      {showCreate && (
        <div className="fixed inset-0 z-50 flex items-end justify-center">
          <div className="absolute inset-0 bg-black/50" onClick={() => setShowCreate(false)} />
          <div className="relative bg-white rounded-t-2xl w-full max-w-md p-6 pb-8">
            <h2 className="text-lg font-semibold mb-1">Đăng ký phương tiện</h2>
            <p className="text-sm text-gray-500 mb-4">Căn hộ: <span className="font-medium text-gray-700">{unitLabel}</span></p>
            <form onSubmit={handleCreate} className="space-y-3">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Loại phương tiện <span className="text-red-500">*</span>
                </label>
                <select name="type" className="block w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                  {VEHICLE_TYPES.map((vt) => <option key={vt} value={vt}>{labelFor('VehicleType', vt)}</option>)}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Biển số <span className="text-red-500">*</span>
                </label>
                <input name="licensePlate" className="block w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" placeholder="51A-123.45" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Hãng xe</label>
                <input name="brand" className="block w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Model</label>
                <input name="model" className="block w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Màu</label>
                <input name="color" className="block w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Ghi chú</label>
                <input name="notes" className="block w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              {formError && <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded-lg">{formError}</p>}
              <div className="flex gap-2 pt-1">
                <button type="button" onClick={() => setShowCreate(false)} className="flex-1 py-2.5 text-sm border border-gray-300 rounded-lg hover:bg-gray-50">
                  Hủy
                </button>
                <button type="submit" disabled={createVehicle.isPending} className="flex-1 py-2.5 text-sm bg-blue-600 text-white rounded-lg disabled:opacity-50">
                  {createVehicle.isPending ? 'Đang đăng ký...' : 'Đăng ký'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
