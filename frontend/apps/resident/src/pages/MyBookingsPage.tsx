import React, { useState } from 'react';
import { useMyBookings, useCancelBooking } from '../api/hooks';
import { getVnErrorMessage } from '@gemek/ui';
import { t } from '../i18n/vi';

const STATUS_BG: Record<string, string> = {
  PENDING: 'bg-yellow-100 text-yellow-700', APPROVED: 'bg-green-100 text-green-700',
  REJECTED: 'bg-red-100 text-red-700', CANCELLED: 'bg-gray-100 text-gray-500',
  COMPLETED: 'bg-blue-100 text-blue-700',
};

export function MyBookingsPage() {
  const { data, isLoading } = useMyBookings({ size: 20 });
  const cancel = useCancelBooking();
  const [cancelError, setCancelError] = useState('');

  const handleCancel = async (id: string) => {
    if (!window.confirm('Hủy đặt chỗ này?')) return;
    setCancelError('');
    try {
      await cancel.mutateAsync(id);
    } catch (err: any) {
      setCancelError(getVnErrorMessage(err?.response?.data?.error));
    }
  };

  return (
    <div className="p-4">
      <h1 className="text-lg font-bold text-gray-900 mb-4">{t('bookings.title')}</h1>
      {isLoading && <div className="text-center py-8 text-gray-400">{t('common.loading')}</div>}
      {!isLoading && !data?.data?.length && (
        <div className="text-center py-12 text-gray-400">
          <p className="text-4xl mb-2">📅</p>
          <p>{t('common.emptyYet', { item: 'lượt đặt' })}</p>
        </div>
      )}
      {cancelError && <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded-lg mb-3">{cancelError}</p>}
      <div className="space-y-3">
        {data?.data?.map((b: any) => (
          <div key={b.id} className="bg-white rounded-xl border border-gray-200 p-4">
            <div className="flex items-start justify-between gap-2">
              <div>
                <p className="font-semibold text-gray-900">{b.amenity?.name}</p>
                <p className="text-sm text-gray-500 mt-0.5">{b.bookingDate} • {b.startTime} - {b.endTime}</p>
              </div>
              <span className={`flex-shrink-0 text-xs px-2 py-0.5 rounded-full ${STATUS_BG[b.status] ?? 'bg-gray-100 text-gray-700'}`}>{b.status}</span>
            </div>
            {b.status === 'PENDING' && (
              <button onClick={() => handleCancel(b.id)} disabled={cancel.isPending}
                className="mt-3 text-xs text-red-600 hover:underline disabled:opacity-50">Hủy đặt chỗ</button>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
