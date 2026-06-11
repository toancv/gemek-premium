import React, { useState } from 'react';
import { labelFor } from '@gemek/ui';
import { useDashboard, useTicketReport, useAmenityReport, useContractsExpiringReport } from '../api/hooks';
import { t } from '../i18n/vi';

export function ReportsPage() {
  const [tab, setTab] = useState<'summary' | 'tickets' | 'amenities' | 'contracts'>('summary');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');

  const { data: dashboard } = useDashboard();
  const { data: ticketReport, isLoading: tLoading } = useTicketReport({ ...(from && { from }), ...(to && { to }) });
  const { data: amenityReport, isLoading: aLoading } = useAmenityReport({ ...(from && { from }), ...(to && { to }) });
  const { data: contractsReport, isLoading: cLoading } = useContractsExpiringReport({ withinDays: 90 });

  const tabs = [
    { id: 'summary', label: t('reports.tabSummary') },
    { id: 'tickets', label: t('reports.tabTickets') },
    { id: 'amenities', label: t('reports.tabAmenities') },
    { id: 'contracts', label: t('reports.tabContracts') },
  ] as const;

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-6">{t('reports.title')}</h1>
      <div className="flex gap-4 mb-6 border-b border-gray-200">
        {tabs.map((t) => (
          <button key={t.id} onClick={() => setTab(t.id)} className={`pb-3 text-sm font-medium border-b-2 ${tab === t.id ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'}`}>{t.label}</button>
        ))}
      </div>

      {(tab === 'tickets' || tab === 'amenities') && (
        <div className="flex gap-3 mb-6">
          <div className="flex items-center gap-2 text-sm">
            <label className="text-gray-600">{t('reports.from')}</label>
            <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} className="border border-gray-300 rounded-md px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>
          <div className="flex items-center gap-2 text-sm">
            <label className="text-gray-600">{t('reports.to')}</label>
            <input type="date" value={to} onChange={(e) => setTo(e.target.value)} className="border border-gray-300 rounded-md px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>
        </div>
      )}

      {tab === 'summary' && dashboard && (
        <div className="space-y-4">
          <div className="grid grid-cols-4 gap-4">
            <div className="bg-white rounded-lg border border-gray-200 p-6">
              <p className="text-sm text-gray-500">{t('reports.totalApartments')}</p>
              <p className="text-3xl font-bold text-gray-900 mt-1">{dashboard.apartments?.total ?? 0}</p>
              <p className="text-xs text-gray-400 mt-1">{t('reports.pctOccupied', { n: ((dashboard.apartments?.occupancyRate ?? 0) * 100).toFixed(1) })}</p>
            </div>
            <div className="bg-white rounded-lg border border-gray-200 p-6">
              <p className="text-sm text-gray-500">{t('reports.openTickets')}</p>
              <p className="text-3xl font-bold text-blue-600 mt-1">{dashboard.tickets?.openRequests ?? 0}</p>
              <p className="text-xs text-gray-400 mt-1">{t('reports.nOverdue', { n: dashboard.tickets?.overdueRequests ?? 0 })}</p>
            </div>
            <div className="bg-white rounded-lg border border-gray-200 p-6">
              <p className="text-sm text-gray-500">{t('reports.activeContracts')}</p>
              <p className="text-3xl font-bold text-green-600 mt-1">{dashboard.contracts?.active ?? 0}</p>
              <p className="text-xs text-gray-400 mt-1">{t('reports.nExpiring30', { n: dashboard.contracts?.expiringIn30Days ?? 0 })}</p>
            </div>
            <div className="bg-white rounded-lg border border-gray-200 p-6">
              <p className="text-sm text-gray-500">{t('reports.amenityBookingsMonth')}</p>
              <p className="text-3xl font-bold text-purple-600 mt-1">{dashboard.amenities?.bookingsThisMonth ?? 0}</p>
              <p className="text-xs text-gray-400 mt-1">{t('reports.nPending', { n: dashboard.amenities?.pendingApproval ?? 0 })}</p>
            </div>
          </div>
          <div className="bg-white rounded-lg border border-gray-200 p-6">
            <h2 className="text-base font-semibold mb-4">{t('reports.ticketsByCategory')}</h2>
            {Object.entries(dashboard.tickets?.byCategory ?? {}).map(([cat, count]) => (
              <div key={cat} className="flex items-center gap-3 mb-2">
                <span className="w-48 text-sm text-gray-600">{labelFor('TicketCategory', cat)}</span>
                <div className="flex-1 bg-gray-100 rounded-full h-4 overflow-hidden">
                  <div className="bg-blue-500 h-full rounded-full transition-all" style={{ width: `${Math.min(100, ((count as number) / Math.max(1, dashboard.tickets?.openRequests ?? 1)) * 100)}%` }} />
                </div>
                <span className="text-sm font-medium w-8 text-right">{count as number}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {tab === 'tickets' && (
        <div className="space-y-4">
          {tLoading ? <div className="text-center py-8 text-gray-400">{t('common.loading')}</div> : (
            <>
              {ticketReport?.summary && (
                <div className="grid grid-cols-4 gap-4">
                  <div className="bg-white rounded-lg border border-gray-200 p-4"><p className="text-sm text-gray-500">{t('reports.total')}</p><p className="text-2xl font-bold mt-1">{ticketReport.summary.total}</p></div>
                  <div className="bg-white rounded-lg border border-gray-200 p-4"><p className="text-sm text-gray-500">{t('reports.completed')}</p><p className="text-2xl font-bold text-green-600 mt-1">{ticketReport.summary.completed}</p></div>
                  <div className="bg-white rounded-lg border border-gray-200 p-4"><p className="text-sm text-gray-500">{t('reports.slaBreachRate')}</p><p className="text-2xl font-bold text-red-600 mt-1">{((ticketReport.summary.slaBreachRate ?? 0) * 100).toFixed(1)}%</p></div>
                  <div className="bg-white rounded-lg border border-gray-200 p-4"><p className="text-sm text-gray-500">{t('reports.avgRating')}</p><p className="text-2xl font-bold text-yellow-600 mt-1">{ticketReport.summary.avgRating?.toFixed(1) ?? '—'}</p></div>
                </div>
              )}
              {ticketReport?.breakdown?.length > 0 && (
                <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
                  <table className="w-full text-sm">
                    <thead className="bg-gray-50 border-b border-gray-200">
                      <tr>
                        <th className="text-left px-4 py-3 font-medium text-gray-500">{t('reports.period')}</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-500">{t('reports.total')}</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-500">{t('reports.completed')}</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-500">{t('reports.slaBreachedCol')}</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-500">{t('reports.avgRating')}</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100">
                      {ticketReport.breakdown.map((b: any) => (
                        <tr key={b.label}>
                          <td className="px-4 py-3 font-medium">{b.label}</td>
                          <td className="px-4 py-3">{b.total}</td>
                          <td className="px-4 py-3 text-green-600">{b.completed}</td>
                          <td className="px-4 py-3 text-red-600">{b.slaBreached}</td>
                          <td className="px-4 py-3">{b.avgRating?.toFixed(1) ?? '—'}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </>
          )}
        </div>
      )}

      {tab === 'amenities' && (
        <div>
          {aLoading ? <div className="text-center py-8 text-gray-400">{t('common.loading')}</div> : (
            <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
              <table className="w-full text-sm">
                <thead className="bg-gray-50 border-b border-gray-200">
                  <tr>
                    <th className="text-left px-4 py-3 font-medium text-gray-500">{t('reports.amenity')}</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-500">{t('reports.totalBookings')}</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-500">{t('reports.approved')}</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-500">{t('reports.rejected')}</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-500">{t('reports.cancelled')}</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-500">{t('reports.utilization')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {!amenityReport?.byAmenity?.length && <tr><td colSpan={6} className="text-center py-8 text-gray-400">{t('common.emptyFound', { item: 'dữ liệu' })}</td></tr>}
                  {amenityReport?.byAmenity?.map((a: any) => (
                    <tr key={a.amenity?.id}>
                      <td className="px-4 py-3 font-medium">{a.amenity?.name}</td>
                      <td className="px-4 py-3">{a.totalBookings}</td>
                      <td className="px-4 py-3 text-green-600">{a.approvedBookings}</td>
                      <td className="px-4 py-3 text-red-600">{a.rejectedBookings}</td>
                      <td className="px-4 py-3 text-gray-500">{a.cancelledBookings}</td>
                      <td className="px-4 py-3">{((a.utilizationRate ?? 0) * 100).toFixed(1)}%</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {tab === 'contracts' && (
        <div>
          {cLoading ? <div className="text-center py-8 text-gray-400">{t('common.loading')}</div> : (
            <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
              <table className="w-full text-sm">
                <thead className="bg-gray-50 border-b border-gray-200">
                  <tr>
                    <th className="text-left px-4 py-3 font-medium text-gray-500">{t('reports.contractTitle')}</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-500">{t('reports.contractor')}</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-500">{t('reports.endDate')}</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-500">{t('reports.daysToExpiry')}</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-500">{t('reports.valueVnd')}</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-500">{t('common.status')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {!contractsReport?.contracts?.length && <tr><td colSpan={6} className="text-center py-8 text-gray-400">{t('common.emptyYet', { item: 'hợp đồng sắp hết hạn' })}</td></tr>}
                  {contractsReport?.contracts?.map((c: any) => (
                    <tr key={c.id} className={c.daysToExpiry <= 30 ? 'bg-red-50' : ''}>
                      <td className="px-4 py-3 font-medium">{c.title}</td>
                      <td className="px-4 py-3">{c.contractor?.companyName}</td>
                      <td className="px-4 py-3">{c.endDate}</td>
                      <td className="px-4 py-3"><span className={`font-medium ${c.daysToExpiry <= 30 ? 'text-red-600' : 'text-yellow-600'}`}>{t('reports.nDays', { n: c.daysToExpiry })}</span></td>
                      <td className="px-4 py-3">{c.contractValue?.toLocaleString('vi-VN')}</td>
                      <td className="px-4 py-3"><span className="inline-flex px-2 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-700">{labelFor('ActiveStatus', c.status)}</span></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
