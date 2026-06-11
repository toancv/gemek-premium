import React from 'react';
import { labelFor } from '@gemek/ui';
import { useDashboard } from '../api/hooks';
import { t } from '../i18n/vi';

function StatCard({ title, value, sub, color }: { title: string; value: string | number; sub?: string; color: string }) {
  return (
    <div className="bg-white rounded-lg border border-gray-200 p-6">
      <p className="text-sm font-medium text-gray-500">{title}</p>
      <p className={`text-3xl font-bold mt-1 ${color}`}>{value}</p>
      {sub && <p className="text-xs text-gray-400 mt-1">{sub}</p>}
    </div>
  );
}

export function DashboardPage() {
  const { data, isLoading, isError } = useDashboard();

  if (isLoading) return (
    <div className="flex items-center justify-center h-64">
      <svg className="animate-spin h-8 w-8 text-blue-600" fill="none" viewBox="0 0 24 24">
        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"/>
        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/>
      </svg>
    </div>
  );

  if (isError) return (
    <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700">
      {t('dashboard.loadError')}
    </div>
  );

  // Named tk (not t) to avoid shadowing the i18n t() helper
  const tk = data?.tickets ?? {};
  const a = data?.apartments ?? {};
  const am = data?.amenities ?? {};
  const c = data?.contracts ?? {};

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-6">{t('dashboard.title')}</h1>
      <div className="grid grid-cols-3 gap-4 mb-8">
        <StatCard title={t('dashboard.openTickets')} value={tk.openRequests ?? 0} sub={t('dashboard.inProgress', { n: tk.inProgressRequests ?? 0 })} color="text-blue-600" />
        <StatCard title={t('dashboard.slaBreached')} value={tk.overdueRequests ?? 0} sub={t('dashboard.requiresAction')} color="text-red-600" />
        {/* TEMP_HIDDEN_DEFERRED: amenity bookings dashboard card — feature deferred, see PROGRESS.md */}
        {/* <StatCard title="Bookings This Month" value={am.bookingsThisMonth ?? 0} sub={`${am.pendingApproval ?? 0} pending approval`} color="text-green-600" /> */}
        <StatCard title={t('dashboard.expiringContracts')} value={c.expiringIn30Days ?? 0} sub={t('dashboard.in90Days', { n: c.expiringIn90Days ?? 0 })} color="text-yellow-600" />
      </div>
      <div className="grid grid-cols-2 gap-4">
        <div className="bg-white rounded-lg border border-gray-200 p-6">
          <h2 className="text-base font-semibold text-gray-900 mb-4">{t('dashboard.apartments')}</h2>
          <div className="space-y-2">
            <div className="flex justify-between text-sm"><span className="text-gray-500">{t('dashboard.total')}</span><span className="font-medium">{a.total ?? 0}</span></div>
            <div className="flex justify-between text-sm"><span className="text-gray-500">{t('dashboard.occupied')}</span><span className="font-medium text-green-600">{a.occupied ?? 0}</span></div>
            <div className="flex justify-between text-sm"><span className="text-gray-500">{t('dashboard.available')}</span><span className="font-medium text-blue-600">{a.available ?? 0}</span></div>
            <div className="flex justify-between text-sm"><span className="text-gray-500">{t('dashboard.occupancyRate')}</span><span className="font-medium">{((a.occupancyRate ?? 0) * 100).toFixed(1)}%</span></div>
          </div>
        </div>
        <div className="bg-white rounded-lg border border-gray-200 p-6">
          <h2 className="text-base font-semibold text-gray-900 mb-4">{t('dashboard.ticketsByCategory')}</h2>
          <div className="space-y-2">
            {Object.entries(tk.byCategory ?? {}).map(([cat, count]) => (
              <div key={cat} className="flex justify-between text-sm">
                <span className="text-gray-500">{labelFor('TicketCategory', cat)}</span>
                <span className="font-medium">{count as number}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
