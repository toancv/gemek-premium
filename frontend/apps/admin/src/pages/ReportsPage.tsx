import React, { useState } from 'react';
import { useDashboard, useTicketReport, useAmenityReport, useContractsExpiringReport } from '../api/hooks';

export function ReportsPage() {
  const [tab, setTab] = useState<'summary' | 'tickets' | 'amenities' | 'contracts'>('summary');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');

  const { data: dashboard } = useDashboard();
  const { data: ticketReport, isLoading: tLoading } = useTicketReport({ ...(from && { from }), ...(to && { to }) });
  const { data: amenityReport, isLoading: aLoading } = useAmenityReport({ ...(from && { from }), ...(to && { to }) });
  const { data: contractsReport, isLoading: cLoading } = useContractsExpiringReport({ withinDays: 90 });

  const tabs = [
    { id: 'summary', label: 'Summary' },
    { id: 'tickets', label: 'Tickets' },
    { id: 'amenities', label: 'Amenities' },
    { id: 'contracts', label: 'Expiring Contracts' },
  ] as const;

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Reports</h1>
      <div className="flex gap-4 mb-6 border-b border-gray-200">
        {tabs.map((t) => (
          <button key={t.id} onClick={() => setTab(t.id)} className={`pb-3 text-sm font-medium border-b-2 ${tab === t.id ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'}`}>{t.label}</button>
        ))}
      </div>

      {(tab === 'tickets' || tab === 'amenities') && (
        <div className="flex gap-3 mb-6">
          <div className="flex items-center gap-2 text-sm">
            <label className="text-gray-600">From:</label>
            <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} className="border border-gray-300 rounded-md px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>
          <div className="flex items-center gap-2 text-sm">
            <label className="text-gray-600">To:</label>
            <input type="date" value={to} onChange={(e) => setTo(e.target.value)} className="border border-gray-300 rounded-md px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>
        </div>
      )}

      {tab === 'summary' && dashboard && (
        <div className="space-y-4">
          <div className="grid grid-cols-4 gap-4">
            <div className="bg-white rounded-lg border border-gray-200 p-6">
              <p className="text-sm text-gray-500">Total Apartments</p>
              <p className="text-3xl font-bold text-gray-900 mt-1">{dashboard.apartments?.total ?? 0}</p>
              <p className="text-xs text-gray-400 mt-1">{((dashboard.apartments?.occupancyRate ?? 0) * 100).toFixed(1)}% occupied</p>
            </div>
            <div className="bg-white rounded-lg border border-gray-200 p-6">
              <p className="text-sm text-gray-500">Open Tickets</p>
              <p className="text-3xl font-bold text-blue-600 mt-1">{dashboard.tickets?.openRequests ?? 0}</p>
              <p className="text-xs text-gray-400 mt-1">{dashboard.tickets?.overdueRequests ?? 0} overdue</p>
            </div>
            <div className="bg-white rounded-lg border border-gray-200 p-6">
              <p className="text-sm text-gray-500">Active Contracts</p>
              <p className="text-3xl font-bold text-green-600 mt-1">{dashboard.contracts?.active ?? 0}</p>
              <p className="text-xs text-gray-400 mt-1">{dashboard.contracts?.expiringIn30Days ?? 0} expiring in 30 days</p>
            </div>
            <div className="bg-white rounded-lg border border-gray-200 p-6">
              <p className="text-sm text-gray-500">Amenity Bookings (Month)</p>
              <p className="text-3xl font-bold text-purple-600 mt-1">{dashboard.amenities?.bookingsThisMonth ?? 0}</p>
              <p className="text-xs text-gray-400 mt-1">{dashboard.amenities?.pendingApproval ?? 0} pending</p>
            </div>
          </div>
          <div className="bg-white rounded-lg border border-gray-200 p-6">
            <h2 className="text-base font-semibold mb-4">Tickets by Category</h2>
            {Object.entries(dashboard.tickets?.byCategory ?? {}).map(([cat, count]) => (
              <div key={cat} className="flex items-center gap-3 mb-2">
                <span className="w-48 text-sm text-gray-600">{cat.replace(/_/g, ' ')}</span>
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
          {tLoading ? <div className="text-center py-8 text-gray-400">Loading...</div> : (
            <>
              {ticketReport?.summary && (
                <div className="grid grid-cols-4 gap-4">
                  <div className="bg-white rounded-lg border border-gray-200 p-4"><p className="text-sm text-gray-500">Total</p><p className="text-2xl font-bold mt-1">{ticketReport.summary.total}</p></div>
                  <div className="bg-white rounded-lg border border-gray-200 p-4"><p className="text-sm text-gray-500">Completed</p><p className="text-2xl font-bold text-green-600 mt-1">{ticketReport.summary.completed}</p></div>
                  <div className="bg-white rounded-lg border border-gray-200 p-4"><p className="text-sm text-gray-500">SLA Breach Rate</p><p className="text-2xl font-bold text-red-600 mt-1">{((ticketReport.summary.slaBreachRate ?? 0) * 100).toFixed(1)}%</p></div>
                  <div className="bg-white rounded-lg border border-gray-200 p-4"><p className="text-sm text-gray-500">Avg Rating</p><p className="text-2xl font-bold text-yellow-600 mt-1">{ticketReport.summary.avgRating?.toFixed(1) ?? '—'}</p></div>
                </div>
              )}
              {ticketReport?.breakdown?.length > 0 && (
                <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
                  <table className="w-full text-sm">
                    <thead className="bg-gray-50 border-b border-gray-200">
                      <tr>
                        <th className="text-left px-4 py-3 font-medium text-gray-500">Period</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-500">Total</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-500">Completed</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-500">SLA Breached</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-500">Avg Rating</th>
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
          {aLoading ? <div className="text-center py-8 text-gray-400">Loading...</div> : (
            <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
              <table className="w-full text-sm">
                <thead className="bg-gray-50 border-b border-gray-200">
                  <tr>
                    <th className="text-left px-4 py-3 font-medium text-gray-500">Amenity</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-500">Total Bookings</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-500">Approved</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-500">Rejected</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-500">Cancelled</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-500">Utilization</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {!amenityReport?.byAmenity?.length && <tr><td colSpan={6} className="text-center py-8 text-gray-400">No data</td></tr>}
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
          {cLoading ? <div className="text-center py-8 text-gray-400">Loading...</div> : (
            <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
              <table className="w-full text-sm">
                <thead className="bg-gray-50 border-b border-gray-200">
                  <tr>
                    <th className="text-left px-4 py-3 font-medium text-gray-500">Title</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-500">Contractor</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-500">End Date</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-500">Days to Expiry</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-500">Value (VND)</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-500">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {!contractsReport?.contracts?.length && <tr><td colSpan={6} className="text-center py-8 text-gray-400">No expiring contracts</td></tr>}
                  {contractsReport?.contracts?.map((c: any) => (
                    <tr key={c.id} className={c.daysToExpiry <= 30 ? 'bg-red-50' : ''}>
                      <td className="px-4 py-3 font-medium">{c.title}</td>
                      <td className="px-4 py-3">{c.contractor?.companyName}</td>
                      <td className="px-4 py-3">{c.endDate}</td>
                      <td className="px-4 py-3"><span className={`font-medium ${c.daysToExpiry <= 30 ? 'text-red-600' : 'text-yellow-600'}`}>{c.daysToExpiry} days</span></td>
                      <td className="px-4 py-3">{c.contractValue?.toLocaleString('vi-VN')}</td>
                      <td className="px-4 py-3"><span className="inline-flex px-2 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-700">{c.status}</span></td>
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
