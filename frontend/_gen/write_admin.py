"""Generates all admin app source files."""
import os

BASE = "D:/Projects/my-project/gemek-premium/frontend/apps/admin/src"

FILES = {}

# ── App.tsx ──────────────────────────────────────────────────────────────────
FILES["App.tsx"] = r"""import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useAuthStore } from './store/authStore';
import { Layout } from './components/Layout';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { ApartmentsPage } from './pages/ApartmentsPage';
import { ResidentsPage } from './pages/ResidentsPage';
import { TicketsPage } from './pages/TicketsPage';
import { TicketDetailPage } from './pages/TicketDetailPage';
import { ContractorsPage } from './pages/ContractorsPage';
import { AnnouncementsPage } from './pages/AnnouncementsPage';
import { AmenitiesPage } from './pages/AmenitiesPage';
import { ParkingPage } from './pages/ParkingPage';
import { ReportsPage } from './pages/ReportsPage';

function RequireAuth({ children }: { children: React.ReactNode }) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  if (!isAuthenticated()) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

function RequireRole({ roles, children }: { roles: string[]; children: React.ReactNode }) {
  const user = useAuthStore((s) => s.user);
  if (!user || !roles.includes(user.role)) return <Navigate to="/dashboard" replace />;
  return <>{children}</>;
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/" element={<RequireAuth><Layout /></RequireAuth>}>
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="dashboard" element={<DashboardPage />} />
          <Route path="apartments" element={<RequireRole roles={['ADMIN','BOARD_MEMBER']}><ApartmentsPage /></RequireRole>} />
          <Route path="residents" element={<RequireRole roles={['ADMIN']}><ResidentsPage /></RequireRole>} />
          <Route path="tickets" element={<TicketsPage />} />
          <Route path="tickets/:id" element={<TicketDetailPage />} />
          <Route path="contractors" element={<RequireRole roles={['ADMIN','BOARD_MEMBER']}><ContractorsPage /></RequireRole>} />
          <Route path="announcements" element={<RequireRole roles={['ADMIN']}><AnnouncementsPage /></RequireRole>} />
          <Route path="amenities" element={<RequireRole roles={['ADMIN']}><AmenitiesPage /></RequireRole>} />
          <Route path="parking" element={<RequireRole roles={['ADMIN']}><ParkingPage /></RequireRole>} />
          <Route path="reports" element={<RequireRole roles={['ADMIN','BOARD_MEMBER']}><ReportsPage /></RequireRole>} />
        </Route>
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
"""

# ── components/Layout.tsx ────────────────────────────────────────────────────
FILES["components/Layout.tsx"] = r"""import React, { useState } from 'react';
import { Outlet, NavLink, useNavigate } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
import { useNotifications, useMarkAllRead } from '../api/hooks';

const NAV = [
  { to: '/dashboard', label: 'Dashboard', roles: ['ADMIN','BOARD_MEMBER','TECHNICIAN'] },
  { to: '/apartments', label: 'Apartments', roles: ['ADMIN','BOARD_MEMBER'] },
  { to: '/residents', label: 'Residents', roles: ['ADMIN'] },
  { to: '/tickets', label: 'Tickets', roles: ['ADMIN','BOARD_MEMBER','TECHNICIAN'] },
  { to: '/contractors', label: 'Contractors', roles: ['ADMIN','BOARD_MEMBER'] },
  { to: '/announcements', label: 'Announcements', roles: ['ADMIN'] },
  { to: '/amenities', label: 'Amenities', roles: ['ADMIN'] },
  { to: '/parking', label: 'Parking', roles: ['ADMIN'] },
  { to: '/reports', label: 'Reports', roles: ['ADMIN','BOARD_MEMBER'] },
];

export function Layout() {
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const navigate = useNavigate();
  const [notifOpen, setNotifOpen] = useState(false);
  const { data: notifData } = useNotifications();
  const markAllRead = useMarkAllRead();

  const nav = NAV.filter((n) => user && n.roles.includes(user.role));
  const handleLogout = async () => { await logout(); navigate('/login'); };

  return (
    <div className="flex h-screen bg-gray-50" style={{ minWidth: 1280 }}>
      <aside className="w-60 bg-gray-900 flex flex-col flex-shrink-0">
        <div className="px-6 py-5 border-b border-gray-700">
          <h1 className="text-white font-bold text-lg">Gemek Premium</h1>
          <p className="text-gray-400 text-xs mt-0.5">Admin Portal</p>
        </div>
        <nav className="flex-1 py-4 overflow-y-auto">
          {nav.map((n) => (
            <NavLink key={n.to} to={n.to} className={({ isActive }) =>
              'flex items-center px-6 py-2.5 text-sm transition-colors ' +
              (isActive ? 'bg-blue-600 text-white' : 'text-gray-300 hover:bg-gray-800 hover:text-white')
            }>{n.label}</NavLink>
          ))}
        </nav>
        <div className="px-6 py-4 border-t border-gray-700">
          <p className="text-gray-300 text-sm font-medium truncate">{user?.fullName}</p>
          <p className="text-gray-500 text-xs">{user?.role}</p>
          <button onClick={handleLogout} className="mt-2 text-xs text-gray-400 hover:text-white">Sign out</button>
        </div>
      </aside>
      <div className="flex-1 flex flex-col overflow-hidden">
        <header className="bg-white border-b border-gray-200 px-6 py-3 flex items-center justify-end gap-4">
          <div className="relative">
            <button onClick={() => setNotifOpen((o) => !o)} className="relative p-1.5 text-gray-500 hover:text-gray-700">
              <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
              </svg>
              {notifData?.unreadCount > 0 && (
                <span className="absolute -top-0.5 -right-0.5 h-4 w-4 bg-red-500 text-white text-xs rounded-full flex items-center justify-center">
                  {notifData.unreadCount > 9 ? '9+' : notifData.unreadCount}
                </span>
              )}
            </button>
            {notifOpen && (
              <div className="absolute right-0 top-full mt-1 w-80 bg-white rounded-lg shadow-lg border border-gray-200 z-50">
                <div className="flex items-center justify-between px-4 py-2 border-b border-gray-100">
                  <span className="font-medium text-sm">Notifications</span>
                  <button onClick={() => markAllRead.mutate()} className="text-xs text-blue-600 hover:underline">Mark all read</button>
                </div>
                <div className="max-h-64 overflow-y-auto">
                  {(!notifData?.data?.length) && <p className="text-center text-gray-400 text-sm py-6">No notifications</p>}
                  {notifData?.data?.map((n: any) => (
                    <div key={n.id} className={'px-4 py-3 border-b border-gray-50 ' + (!n.isRead ? 'bg-blue-50' : '')}>
                      <p className="text-sm font-medium text-gray-800">{n.title}</p>
                      <p className="text-xs text-gray-500 mt-0.5">{n.body}</p>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
          <span className="text-sm text-gray-700">{user?.fullName}</span>
        </header>
        <main className="flex-1 overflow-y-auto p-6"><Outlet /></main>
      </div>
    </div>
  );
}
"""

# ── pages/LoginPage.tsx ──────────────────────────────────────────────────────
FILES["pages/LoginPage.tsx"] = r"""import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';

export function LoginPage() {
  const login = useAuthStore((s) => s.login);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  if (isAuthenticated()) { navigate('/dashboard', { replace: true }); return null; }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    if (!email.trim()) { setError('Email is required'); return; }
    if (!password) { setError('Password is required'); return; }
    setLoading(true);
    try {
      await login(email, password);
      navigate('/dashboard', { replace: true });
    } catch (err: any) {
      const msg = err?.response?.data?.message ?? 'Invalid email or password';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-gray-100 flex items-center justify-center p-4">
      <div className="bg-white rounded-xl shadow-md w-full max-w-sm p-8">
        <div className="text-center mb-8">
          <h1 className="text-2xl font-bold text-gray-900">Gemek Premium</h1>
          <p className="text-gray-500 text-sm mt-1">Admin Portal</p>
        </div>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Email</label>
            <input
              type="email" value={email} onChange={(e) => setEmail(e.target.value)}
              className="block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="admin@example.com" autoComplete="email"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Password</label>
            <input
              type="password" value={password} onChange={(e) => setPassword(e.target.value)}
              className="block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="••••••••" autoComplete="current-password"
            />
          </div>
          {error && <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded">{error}</p>}
          <button
            type="submit" disabled={loading}
            className="w-full bg-blue-600 hover:bg-blue-700 text-white rounded-md py-2 text-sm font-medium transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
          >
            {loading && <svg className="animate-spin h-4 w-4" fill="none" viewBox="0 0 24 24"><circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"/><path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/></svg>}
            {loading ? 'Signing in...' : 'Sign in'}
          </button>
        </form>
      </div>
    </div>
  );
}
"""

# ── pages/DashboardPage.tsx ──────────────────────────────────────────────────
FILES["pages/DashboardPage.tsx"] = r"""import React from 'react';
import { useDashboard } from '../api/hooks';

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
      Failed to load dashboard data. Please try again.
    </div>
  );

  const t = data?.tickets ?? {};
  const a = data?.apartments ?? {};
  const am = data?.amenities ?? {};
  const c = data?.contracts ?? {};

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Dashboard</h1>
      <div className="grid grid-cols-4 gap-4 mb-8">
        <StatCard title="Open Tickets" value={t.openRequests ?? 0} sub={`${t.inProgressRequests ?? 0} in progress`} color="text-blue-600" />
        <StatCard title="SLA Breached" value={t.overdueRequests ?? 0} sub="Requires immediate action" color="text-red-600" />
        <StatCard title="Bookings This Month" value={am.bookingsThisMonth ?? 0} sub={`${am.pendingApproval ?? 0} pending approval`} color="text-green-600" />
        <StatCard title="Expiring Contracts" value={c.expiringIn30Days ?? 0} sub={`${c.expiringIn90Days ?? 0} in 90 days`} color="text-yellow-600" />
      </div>
      <div className="grid grid-cols-2 gap-4">
        <div className="bg-white rounded-lg border border-gray-200 p-6">
          <h2 className="text-base font-semibold text-gray-900 mb-4">Apartments</h2>
          <div className="space-y-2">
            <div className="flex justify-between text-sm"><span className="text-gray-500">Total</span><span className="font-medium">{a.total ?? 0}</span></div>
            <div className="flex justify-between text-sm"><span className="text-gray-500">Occupied</span><span className="font-medium text-green-600">{a.occupied ?? 0}</span></div>
            <div className="flex justify-between text-sm"><span className="text-gray-500">Available</span><span className="font-medium text-blue-600">{a.available ?? 0}</span></div>
            <div className="flex justify-between text-sm"><span className="text-gray-500">Occupancy Rate</span><span className="font-medium">{((a.occupancyRate ?? 0) * 100).toFixed(1)}%</span></div>
          </div>
        </div>
        <div className="bg-white rounded-lg border border-gray-200 p-6">
          <h2 className="text-base font-semibold text-gray-900 mb-4">Tickets by Category</h2>
          <div className="space-y-2">
            {Object.entries(t.byCategory ?? {}).map(([cat, count]) => (
              <div key={cat} className="flex justify-between text-sm">
                <span className="text-gray-500">{cat.replace(/_/g, ' ')}</span>
                <span className="font-medium">{count as number}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
"""

# ── pages/ApartmentsPage.tsx ─────────────────────────────────────────────────
FILES["pages/ApartmentsPage.tsx"] = r"""import React, { useState } from 'react';
import { useApartments, useBlocks, useUpdateApartment } from '../api/hooks';

export function ApartmentsPage() {
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');
  const [blockId, setBlockId] = useState('');
  const [page, setPage] = useState(0);
  const [editApt, setEditApt] = useState<any>(null);

  const params = { page, size: 20, ...(search && { search }), ...(status && { status }), ...(blockId && { blockId }) };
  const { data, isLoading, isError } = useApartments(params);
  const { data: blocksData } = useBlocks();
  const updateApt = useUpdateApartment();

  const statusColors: Record<string, string> = {
    OCCUPIED: 'bg-green-100 text-green-700',
    AVAILABLE: 'bg-blue-100 text-blue-700',
    MAINTENANCE: 'bg-yellow-100 text-yellow-700',
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Apartments</h1>
      </div>
      <div className="bg-white rounded-lg border border-gray-200 p-4 mb-4 flex gap-3">
        <input value={search} onChange={(e) => { setSearch(e.target.value); setPage(0); }}
          placeholder="Search unit number..." className="border border-gray-300 rounded-md px-3 py-2 text-sm flex-1 focus:outline-none focus:ring-2 focus:ring-blue-500" />
        <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}
          className="border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
          <option value="">All Statuses</option>
          <option value="OCCUPIED">Occupied</option>
          <option value="AVAILABLE">Available</option>
          <option value="MAINTENANCE">Maintenance</option>
        </select>
        <select value={blockId} onChange={(e) => { setBlockId(e.target.value); setPage(0); }}
          className="border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
          <option value="">All Blocks</option>
          {blocksData?.data?.map((b: any) => <option key={b.id} value={b.id}>{b.name}</option>)}
        </select>
      </div>

      {isError && <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 mb-4">Failed to load apartments.</div>}

      <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 border-b border-gray-200">
            <tr>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Block</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Unit</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Floor</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Area (sqm)</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Status</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Primary Contact</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {isLoading && <tr><td colSpan={7} className="text-center py-8 text-gray-400">Loading...</td></tr>}
            {!isLoading && !data?.data?.length && <tr><td colSpan={7} className="text-center py-8 text-gray-400">No apartments found</td></tr>}
            {data?.data?.map((apt: any) => (
              <tr key={apt.id} className="hover:bg-gray-50">
                <td className="px-4 py-3">{apt.block?.name}</td>
                <td className="px-4 py-3 font-medium">{apt.unitNumber}</td>
                <td className="px-4 py-3">{apt.floor}</td>
                <td className="px-4 py-3">{apt.areaSqm}</td>
                <td className="px-4 py-3">
                  <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${statusColors[apt.status] ?? 'bg-gray-100 text-gray-700'}`}>{apt.status}</span>
                </td>
                <td className="px-4 py-3">{apt.primaryContact?.fullName ?? '—'}</td>
                <td className="px-4 py-3">
                  <button onClick={() => setEditApt(apt)} className="text-blue-600 hover:underline text-xs">Edit</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="flex items-center justify-between px-4 py-3 border-t border-gray-200">
          <span className="text-xs text-gray-500">Total: {data?.total ?? 0}</span>
          <div className="flex gap-2">
            <button onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0}
              className="px-3 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50 disabled:opacity-40">Prev</button>
            <span className="px-3 py-1 text-xs">{page + 1} / {data?.totalPages ?? 1}</span>
            <button onClick={() => setPage((p) => p + 1)} disabled={page + 1 >= (data?.totalPages ?? 1)}
              className="px-3 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50 disabled:opacity-40">Next</button>
          </div>
        </div>
      </div>

      {editApt && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/50" onClick={() => setEditApt(null)} />
          <div className="relative bg-white rounded-lg shadow-xl w-full max-w-md p-6">
            <h2 className="text-lg font-semibold mb-4">Edit Apartment {editApt.unitNumber}</h2>
            <form onSubmit={async (e) => {
              e.preventDefault();
              const fd = new FormData(e.target as HTMLFormElement);
              await updateApt.mutateAsync({ id: editApt.id, data: {
                floor: Number(fd.get('floor')),
                unitNumber: fd.get('unitNumber') as string,
                areaSqm: Number(fd.get('areaSqm')),
                status: fd.get('status') as string,
                notes: fd.get('notes') as string,
              }});
              setEditApt(null);
            }} className="space-y-3">
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Unit Number</label>
                <input name="unitNumber" defaultValue={editApt.unitNumber} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" /></div>
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Floor</label>
                <input name="floor" type="number" defaultValue={editApt.floor} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" /></div>
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Area (sqm)</label>
                <input name="areaSqm" type="number" step="0.1" defaultValue={editApt.areaSqm} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" /></div>
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Status</label>
                <select name="status" defaultValue={editApt.status} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm bg-white">
                  <option value="OCCUPIED">Occupied</option>
                  <option value="AVAILABLE">Available</option>
                  <option value="MAINTENANCE">Maintenance</option>
                </select></div>
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Notes</label>
                <textarea name="notes" defaultValue={editApt.notes ?? ''} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" rows={2} /></div>
              <div className="flex gap-2 justify-end pt-2">
                <button type="button" onClick={() => setEditApt(null)} className="px-4 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50">Cancel</button>
                <button type="submit" disabled={updateApt.isPending} className="px-4 py-2 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50">
                  {updateApt.isPending ? 'Saving...' : 'Save'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
"""

# ── pages/ResidentsPage.tsx ──────────────────────────────────────────────────
FILES["pages/ResidentsPage.tsx"] = r"""import React, { useState } from 'react';
import { useResidents, useCreateResident } from '../api/hooks';

export function ResidentsPage() {
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [showCreate, setShowCreate] = useState(false);
  const [formError, setFormError] = useState('');

  const { data, isLoading, isError } = useResidents({ page, size: 20, ...(search && { search }) });
  const createResident = useCreateResident();

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError('');
    const fd = new FormData(e.target as HTMLFormElement);
    const userId = (fd.get('userId') as string).trim();
    const apartmentId = (fd.get('apartmentId') as string).trim();
    const moveInDate = fd.get('moveInDate') as string;
    if (!userId || !apartmentId || !moveInDate) { setFormError('User ID, Apartment ID and Move-in Date are required'); return; }
    try {
      await createResident.mutateAsync({ userId, apartmentId, type: fd.get('type'), moveInDate, isPrimaryContact: fd.get('isPrimaryContact') === 'true' });
      setShowCreate(false);
    } catch (err: any) {
      setFormError(err?.response?.data?.message ?? 'Failed to create resident');
    }
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Residents</h1>
        <button onClick={() => setShowCreate(true)} className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-md text-sm font-medium">
          Add Resident
        </button>
      </div>

      <div className="bg-white rounded-lg border border-gray-200 p-4 mb-4">
        <input value={search} onChange={(e) => { setSearch(e.target.value); setPage(0); }}
          placeholder="Search by name or email..." className="border border-gray-300 rounded-md px-3 py-2 text-sm w-80 focus:outline-none focus:ring-2 focus:ring-blue-500" />
      </div>

      {isError && <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 mb-4">Failed to load residents.</div>}

      <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 border-b border-gray-200">
            <tr>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Name</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Email</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Apartment</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Type</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Move-in Date</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {isLoading && <tr><td colSpan={5} className="text-center py-8 text-gray-400">Loading...</td></tr>}
            {!isLoading && !data?.data?.length && <tr><td colSpan={5} className="text-center py-8 text-gray-400">No residents found</td></tr>}
            {data?.data?.map((r: any) => (
              <tr key={r.id} className="hover:bg-gray-50">
                <td className="px-4 py-3 font-medium">{r.user?.fullName ?? r.fullName}</td>
                <td className="px-4 py-3 text-gray-500">{r.user?.email ?? r.email}</td>
                <td className="px-4 py-3">{r.apartment?.unitNumber}</td>
                <td className="px-4 py-3">
                  <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${r.type === 'OWNER' ? 'bg-purple-100 text-purple-700' : 'bg-blue-100 text-blue-700'}`}>{r.type}</span>
                </td>
                <td className="px-4 py-3">{r.moveInDate}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="flex items-center justify-between px-4 py-3 border-t border-gray-200">
          <span className="text-xs text-gray-500">Total: {data?.total ?? 0}</span>
          <div className="flex gap-2">
            <button onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0} className="px-3 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50 disabled:opacity-40">Prev</button>
            <span className="px-3 py-1 text-xs">{page + 1} / {data?.totalPages ?? 1}</span>
            <button onClick={() => setPage((p) => p + 1)} disabled={page + 1 >= (data?.totalPages ?? 1)} className="px-3 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50 disabled:opacity-40">Next</button>
          </div>
        </div>
      </div>

      {showCreate && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/50" onClick={() => setShowCreate(false)} />
          <div className="relative bg-white rounded-lg shadow-xl w-full max-w-md p-6">
            <h2 className="text-lg font-semibold mb-4">Add Resident</h2>
            <form onSubmit={handleCreate} className="space-y-3">
              <div><label className="block text-sm font-medium text-gray-700 mb-1">User ID <span className="text-red-500">*</span></label>
                <input name="userId" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" placeholder="UUID of existing user" /></div>
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Apartment ID <span className="text-red-500">*</span></label>
                <input name="apartmentId" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" placeholder="UUID of apartment" /></div>
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Type</label>
                <select name="type" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm bg-white">
                  <option value="OWNER">Owner</option><option value="TENANT">Tenant</option>
                </select></div>
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Move-in Date <span className="text-red-500">*</span></label>
                <input name="moveInDate" type="date" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" /></div>
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Primary Contact</label>
                <select name="isPrimaryContact" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm bg-white">
                  <option value="false">No</option><option value="true">Yes</option>
                </select></div>
              {formError && <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded">{formError}</p>}
              <div className="flex gap-2 justify-end pt-2">
                <button type="button" onClick={() => setShowCreate(false)} className="px-4 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50">Cancel</button>
                <button type="submit" disabled={createResident.isPending} className="px-4 py-2 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50">
                  {createResident.isPending ? 'Saving...' : 'Create'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
"""

# ── pages/TicketsPage.tsx ────────────────────────────────────────────────────
FILES["pages/TicketsPage.tsx"] = r"""import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTickets } from '../api/hooks';

const STATUS_COLORS: Record<string, string> = {
  NEW: 'bg-blue-100 text-blue-700', ASSIGNED: 'bg-purple-100 text-purple-700',
  IN_PROGRESS: 'bg-yellow-100 text-yellow-700', DONE: 'bg-green-100 text-green-700',
  CANCELLED: 'bg-gray-100 text-gray-700',
};
const CAT_COLORS: Record<string, string> = {
  MAINTENANCE_REPAIR: 'bg-orange-100 text-orange-700', COMPLAINT: 'bg-red-100 text-red-700',
  ADMINISTRATIVE: 'bg-blue-100 text-blue-700', SUGGESTION_FEEDBACK: 'bg-purple-100 text-purple-700',
  OTHER: 'bg-gray-100 text-gray-700',
};

export function TicketsPage() {
  const navigate = useNavigate();
  const [category, setCategory] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);

  const params = { page, size: 20, ...(category && { category }), ...(status && { status }) };
  const { data, isLoading, isError } = useTickets(params);

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Tickets</h1>
      </div>
      <div className="bg-white rounded-lg border border-gray-200 p-4 mb-4 flex gap-3">
        <select value={category} onChange={(e) => { setCategory(e.target.value); setPage(0); }}
          className="border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
          <option value="">All Categories</option>
          <option value="MAINTENANCE_REPAIR">Maintenance & Repair</option>
          <option value="COMPLAINT">Complaint</option>
          <option value="ADMINISTRATIVE">Administrative</option>
          <option value="SUGGESTION_FEEDBACK">Suggestion / Feedback</option>
          <option value="OTHER">Other</option>
        </select>
        <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}
          className="border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
          <option value="">All Statuses</option>
          <option value="NEW">New</option>
          <option value="ASSIGNED">Assigned</option>
          <option value="IN_PROGRESS">In Progress</option>
          <option value="DONE">Done</option>
          <option value="CANCELLED">Cancelled</option>
        </select>
      </div>

      {isError && <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 mb-4">Failed to load tickets.</div>}

      <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 border-b border-gray-200">
            <tr>
              <th className="text-left px-4 py-3 font-medium text-gray-500">ID</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Title</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Category</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Status</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Assignee</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">SLA Deadline</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {isLoading && <tr><td colSpan={6} className="text-center py-8 text-gray-400">Loading...</td></tr>}
            {!isLoading && !data?.data?.length && <tr><td colSpan={6} className="text-center py-8 text-gray-400">No tickets found</td></tr>}
            {data?.data?.map((t: any) => (
              <tr key={t.id} className="hover:bg-gray-50 cursor-pointer" onClick={() => navigate(`/tickets/${t.id}`)}>
                <td className="px-4 py-3 font-mono text-xs text-gray-500">{t.id.substring(0, 8)}</td>
                <td className="px-4 py-3 font-medium max-w-xs truncate">{t.title}</td>
                <td className="px-4 py-3"><span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${CAT_COLORS[t.category] ?? 'bg-gray-100 text-gray-700'}`}>{t.category.replace(/_/g, ' ')}</span></td>
                <td className="px-4 py-3"><span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${STATUS_COLORS[t.status] ?? 'bg-gray-100 text-gray-700'}`}>{t.status}</span></td>
                <td className="px-4 py-3 text-gray-500">{t.assignedToUser?.fullName ?? t.assignedToContractor?.companyName ?? '—'}</td>
                <td className="px-4 py-3">
                  <span className={t.slaBreached ? 'text-red-600 font-medium' : 'text-gray-500'}>
                    {t.slaDeadline ? new Date(t.slaDeadline).toLocaleDateString() : '—'}
                    {t.slaBreached && ' ⚠'}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="flex items-center justify-between px-4 py-3 border-t border-gray-200">
          <span className="text-xs text-gray-500">Total: {data?.total ?? 0}</span>
          <div className="flex gap-2">
            <button onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0} className="px-3 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50 disabled:opacity-40">Prev</button>
            <span className="px-3 py-1 text-xs">{page + 1} / {data?.totalPages ?? 1}</span>
            <button onClick={() => setPage((p) => p + 1)} disabled={page + 1 >= (data?.totalPages ?? 1)} className="px-3 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50 disabled:opacity-40">Next</button>
          </div>
        </div>
      </div>
    </div>
  );
}
"""

# ── pages/TicketDetailPage.tsx ───────────────────────────────────────────────
FILES["pages/TicketDetailPage.tsx"] = r"""import React, { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useTicket, useAssignTicket, useUpdateTicketStatus } from '../api/hooks';

const STATUS_COLORS: Record<string, string> = {
  NEW: 'bg-blue-100 text-blue-700', ASSIGNED: 'bg-purple-100 text-purple-700',
  IN_PROGRESS: 'bg-yellow-100 text-yellow-700', DONE: 'bg-green-100 text-green-700',
  CANCELLED: 'bg-gray-100 text-gray-700',
};

export function TicketDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data: ticket, isLoading, isError } = useTicket(id!);
  const assignTicket = useAssignTicket();
  const updateStatus = useUpdateTicketStatus();
  const [assignUserId, setAssignUserId] = useState('');
  const [newStatus, setNewStatus] = useState('');
  const [statusNotes, setStatusNotes] = useState('');
  const [actionError, setActionError] = useState('');

  if (isLoading) return <div className="flex items-center justify-center h-64"><svg className="animate-spin h-8 w-8 text-blue-600" fill="none" viewBox="0 0 24 24"><circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"/><path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/></svg></div>;
  if (isError) return <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700">Failed to load ticket.</div>;

  const handleAssign = async (e: React.FormEvent) => {
    e.preventDefault();
    setActionError('');
    if (!assignUserId.trim()) { setActionError('User ID is required'); return; }
    try {
      await assignTicket.mutateAsync({ id: id!, data: { assignedToUserId: assignUserId, scheduledDate: null, notes: null } });
      setAssignUserId('');
    } catch (err: any) { setActionError(err?.response?.data?.message ?? 'Failed to assign'); }
  };

  const handleStatusUpdate = async (e: React.FormEvent) => {
    e.preventDefault();
    setActionError('');
    if (!newStatus) { setActionError('Select a status'); return; }
    try {
      await updateStatus.mutateAsync({ id: id!, data: { status: newStatus, notes: statusNotes } });
      setNewStatus(''); setStatusNotes('');
    } catch (err: any) { setActionError(err?.response?.data?.message ?? 'Failed to update status'); }
  };

  return (
    <div className="max-w-4xl">
      <button onClick={() => navigate(-1)} className="text-sm text-blue-600 hover:underline mb-4 flex items-center gap-1">
        &larr; Back to Tickets
      </button>
      <div className="bg-white rounded-lg border border-gray-200 p-6 mb-4">
        <div className="flex items-start justify-between mb-4">
          <div>
            <h1 className="text-xl font-bold text-gray-900">{ticket.title}</h1>
            <p className="text-xs text-gray-400 mt-1 font-mono">{ticket.id}</p>
          </div>
          <span className={`inline-flex px-3 py-1 rounded-full text-sm font-medium ${STATUS_COLORS[ticket.status] ?? 'bg-gray-100 text-gray-700'}`}>{ticket.status}</span>
        </div>
        <div className="grid grid-cols-3 gap-4 text-sm mb-4">
          <div><span className="text-gray-500">Category:</span> <span className="font-medium">{ticket.category}</span></div>
          <div><span className="text-gray-500">Priority:</span> <span className="font-medium">{ticket.priority}</span></div>
          <div><span className="text-gray-500">Apartment:</span> <span className="font-medium">{ticket.apartment?.unitNumber} - {ticket.apartment?.block?.name}</span></div>
          <div><span className="text-gray-500">Submitted by:</span> <span className="font-medium">{ticket.submittedBy?.fullName}</span></div>
          <div><span className="text-gray-500">Assignee:</span> <span className="font-medium">{ticket.assignedToUser?.fullName ?? ticket.assignedToContractor?.companyName ?? '—'}</span></div>
          <div><span className="text-gray-500">SLA:</span> <span className={`font-medium ${ticket.slaBreached ? 'text-red-600' : ''}`}>{ticket.slaDeadline ? new Date(ticket.slaDeadline).toLocaleString() : '—'}{ticket.slaBreached && ' ⚠'}</span></div>
        </div>
        {ticket.description && <div className="bg-gray-50 rounded-md p-4 text-sm text-gray-700 mb-4">{ticket.description}</div>}
        {ticket.rating && <div className="text-sm"><span className="text-gray-500">Rating:</span> <span className="font-medium">{'★'.repeat(ticket.rating)}{'☆'.repeat(5 - ticket.rating)}</span> {ticket.ratingComment && <span className="text-gray-500 ml-2">"{ticket.ratingComment}"</span>}</div>}
      </div>

      {/* Photos */}
      {ticket.photos?.length > 0 && (
        <div className="bg-white rounded-lg border border-gray-200 p-6 mb-4">
          <h2 className="text-base font-semibold mb-3">Photos</h2>
          <div className="grid grid-cols-3 gap-3">
            {ticket.photos.map((p: any) => (
              <div key={p.id} className="rounded-lg overflow-hidden border border-gray-200">
                <div className="bg-gray-100 px-2 py-1 text-xs font-medium text-gray-500">{p.phase}</div>
                <img src={p.presignedUrl} alt={p.fileName} className="w-full h-32 object-cover" />
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Status History */}
      <div className="bg-white rounded-lg border border-gray-200 p-6 mb-4">
        <h2 className="text-base font-semibold mb-3">Status History</h2>
        <div className="space-y-3">
          {ticket.statusHistory?.map((h: any) => (
            <div key={h.id} className="flex items-start gap-3 text-sm">
              <div className="w-2 h-2 rounded-full bg-blue-500 mt-1.5 flex-shrink-0" />
              <div>
                <span className="font-medium">{h.oldStatus ?? 'Created'} → {h.newStatus}</span>
                <span className="text-gray-400 ml-2">by {h.changedBy?.fullName}</span>
                <span className="text-gray-400 ml-2">{new Date(h.changedAt).toLocaleString()}</span>
                {h.notes && <p className="text-gray-500 mt-0.5">{h.notes}</p>}
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Admin Actions */}
      <div className="grid grid-cols-2 gap-4">
        <div className="bg-white rounded-lg border border-gray-200 p-6">
          <h2 className="text-base font-semibold mb-3">Assign Ticket</h2>
          <form onSubmit={handleAssign} className="space-y-3">
            <div><label className="block text-sm font-medium text-gray-700 mb-1">User ID</label>
              <input value={assignUserId} onChange={(e) => setAssignUserId(e.target.value)} placeholder="Staff user UUID" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" /></div>
            {actionError && <p className="text-xs text-red-600">{actionError}</p>}
            <button type="submit" disabled={assignTicket.isPending} className="w-full bg-blue-600 text-white rounded-md py-2 text-sm hover:bg-blue-700 disabled:opacity-50">
              {assignTicket.isPending ? 'Assigning...' : 'Assign'}
            </button>
          </form>
        </div>
        <div className="bg-white rounded-lg border border-gray-200 p-6">
          <h2 className="text-base font-semibold mb-3">Update Status</h2>
          <form onSubmit={handleStatusUpdate} className="space-y-3">
            <div><label className="block text-sm font-medium text-gray-700 mb-1">New Status</label>
              <select value={newStatus} onChange={(e) => setNewStatus(e.target.value)} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm bg-white">
                <option value="">Select status...</option>
                <option value="IN_PROGRESS">In Progress</option>
                <option value="DONE">Done</option>
                <option value="CANCELLED">Cancelled</option>
              </select></div>
            <div><label className="block text-sm font-medium text-gray-700 mb-1">Notes</label>
              <textarea value={statusNotes} onChange={(e) => setStatusNotes(e.target.value)} rows={2} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" /></div>
            <button type="submit" disabled={updateStatus.isPending} className="w-full bg-green-600 text-white rounded-md py-2 text-sm hover:bg-green-700 disabled:opacity-50">
              {updateStatus.isPending ? 'Updating...' : 'Update Status'}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}
"""

# ── pages/ContractorsPage.tsx ─────────────────────────────────────────────────
FILES["pages/ContractorsPage.tsx"] = r"""import React, { useState } from 'react';
import { useContractors, useCreateContractor, useUpdateContractor } from '../api/hooks';

const SPECIALTIES = ['CLEANING','SECURITY','ELEVATOR','FIRE_SAFETY','LANDSCAPING','PEST_CONTROL','ELECTRICAL','PLUMBING','OTHER'];

export function ContractorsPage() {
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [modal, setModal] = useState<null | 'create' | any>(null);
  const [formError, setFormError] = useState('');

  const { data, isLoading, isError } = useContractors({ page, size: 20, ...(search && { search }) });
  const createContractor = useCreateContractor();
  const updateContractor = useUpdateContractor();
  const isEdit = modal && modal !== 'create';

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError('');
    const fd = new FormData(e.target as HTMLFormElement);
    const payload = { companyName: fd.get('companyName'), contactPerson: fd.get('contactPerson'), phone: fd.get('phone'), email: fd.get('email'), specialty: fd.get('specialty'), address: fd.get('address') };
    if (!payload.companyName) { setFormError('Company name is required'); return; }
    try {
      if (isEdit) await updateContractor.mutateAsync({ id: modal.id, data: payload });
      else await createContractor.mutateAsync(payload);
      setModal(null);
    } catch (err: any) { setFormError(err?.response?.data?.message ?? 'Failed'); }
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Contractors</h1>
        <button onClick={() => { setModal('create'); setFormError(''); }} className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-md text-sm font-medium">Add Contractor</button>
      </div>
      <div className="bg-white rounded-lg border border-gray-200 p-4 mb-4">
        <input value={search} onChange={(e) => { setSearch(e.target.value); setPage(0); }} placeholder="Search company name..." className="border border-gray-300 rounded-md px-3 py-2 text-sm w-80 focus:outline-none focus:ring-2 focus:ring-blue-500" />
      </div>
      {isError && <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 mb-4">Failed to load contractors.</div>}
      <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 border-b border-gray-200">
            <tr>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Company</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Contact</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Phone</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Specialty</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Rating</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Status</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {isLoading && <tr><td colSpan={7} className="text-center py-8 text-gray-400">Loading...</td></tr>}
            {!isLoading && !data?.data?.length && <tr><td colSpan={7} className="text-center py-8 text-gray-400">No contractors found</td></tr>}
            {data?.data?.map((c: any) => (
              <tr key={c.id} className="hover:bg-gray-50">
                <td className="px-4 py-3 font-medium">{c.companyName}</td>
                <td className="px-4 py-3 text-gray-500">{c.contactPerson ?? '—'}</td>
                <td className="px-4 py-3">{c.phone ?? '—'}</td>
                <td className="px-4 py-3"><span className="inline-flex px-2 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-700">{c.specialty}</span></td>
                <td className="px-4 py-3">{c.rating ? `${'★'.repeat(Math.round(c.rating))} ${c.rating.toFixed(1)}` : '—'}</td>
                <td className="px-4 py-3"><span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${c.isActive ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-500'}`}>{c.isActive ? 'Active' : 'Inactive'}</span></td>
                <td className="px-4 py-3"><button onClick={() => { setModal(c); setFormError(''); }} className="text-blue-600 hover:underline text-xs">Edit</button></td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="flex items-center justify-between px-4 py-3 border-t border-gray-200">
          <span className="text-xs text-gray-500">Total: {data?.total ?? 0}</span>
          <div className="flex gap-2">
            <button onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0} className="px-3 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50 disabled:opacity-40">Prev</button>
            <span className="px-3 py-1 text-xs">{page + 1} / {data?.totalPages ?? 1}</span>
            <button onClick={() => setPage((p) => p + 1)} disabled={page + 1 >= (data?.totalPages ?? 1)} className="px-3 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50 disabled:opacity-40">Next</button>
          </div>
        </div>
      </div>

      {modal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/50" onClick={() => setModal(null)} />
          <div className="relative bg-white rounded-lg shadow-xl w-full max-w-md p-6">
            <h2 className="text-lg font-semibold mb-4">{isEdit ? 'Edit' : 'Add'} Contractor</h2>
            <form onSubmit={handleSubmit} className="space-y-3">
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Company Name <span className="text-red-500">*</span></label>
                <input name="companyName" defaultValue={isEdit ? modal.companyName : ''} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" /></div>
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Contact Person</label>
                <input name="contactPerson" defaultValue={isEdit ? modal.contactPerson ?? '' : ''} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" /></div>
              <div className="grid grid-cols-2 gap-3">
                <div><label className="block text-sm font-medium text-gray-700 mb-1">Phone</label>
                  <input name="phone" defaultValue={isEdit ? modal.phone ?? '' : ''} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" /></div>
                <div><label className="block text-sm font-medium text-gray-700 mb-1">Email</label>
                  <input name="email" type="email" defaultValue={isEdit ? modal.email ?? '' : ''} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" /></div>
              </div>
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Specialty</label>
                <select name="specialty" defaultValue={isEdit ? modal.specialty : 'OTHER'} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm bg-white">
                  {SPECIALTIES.map((s) => <option key={s} value={s}>{s}</option>)}
                </select></div>
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Address</label>
                <input name="address" defaultValue={isEdit ? modal.address ?? '' : ''} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" /></div>
              {formError && <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded">{formError}</p>}
              <div className="flex gap-2 justify-end pt-2">
                <button type="button" onClick={() => setModal(null)} className="px-4 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50">Cancel</button>
                <button type="submit" disabled={createContractor.isPending || updateContractor.isPending} className="px-4 py-2 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50">
                  {createContractor.isPending || updateContractor.isPending ? 'Saving...' : 'Save'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
"""

# ── pages/AnnouncementsPage.tsx ───────────────────────────────────────────────
FILES["pages/AnnouncementsPage.tsx"] = r"""import React, { useState } from 'react';
import { useAnnouncements, useCreateAnnouncement, usePublishAnnouncement } from '../api/hooks';

const TYPES = ['GENERAL','URGENT','MAINTENANCE','AMENITY','EVENT'];

export function AnnouncementsPage() {
  const [page, setPage] = useState(0);
  const [showCreate, setShowCreate] = useState(false);
  const [formError, setFormError] = useState('');
  const [scope, setScope] = useState('ALL');

  const { data, isLoading, isError } = useAnnouncements({ page, size: 20 });
  const create = useCreateAnnouncement();
  const publish = usePublishAnnouncement();

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError('');
    const fd = new FormData(e.target as HTMLFormElement);
    const title = (fd.get('title') as string).trim();
    const content = (fd.get('content') as string).trim();
    if (!title || !content) { setFormError('Title and content are required'); return; }
    try {
      await create.mutateAsync({ title, content, type: fd.get('type'), targetScope: fd.get('targetScope'), targetBlockId: fd.get('targetBlockId') || null, publishNow: fd.get('publishNow') === 'true', sendPush: true, sendEmail: false, sendSms: false });
      setShowCreate(false);
    } catch (err: any) { setFormError(err?.response?.data?.message ?? 'Failed to create'); }
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Announcements</h1>
        <button onClick={() => { setShowCreate(true); setFormError(''); }} className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-md text-sm font-medium">Create Announcement</button>
      </div>
      {isError && <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 mb-4">Failed to load announcements.</div>}
      <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 border-b border-gray-200">
            <tr>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Title</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Type</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Scope</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Published</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Status</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {isLoading && <tr><td colSpan={6} className="text-center py-8 text-gray-400">Loading...</td></tr>}
            {!isLoading && !data?.data?.length && <tr><td colSpan={6} className="text-center py-8 text-gray-400">No announcements found</td></tr>}
            {data?.data?.map((a: any) => (
              <tr key={a.id} className="hover:bg-gray-50">
                <td className="px-4 py-3 font-medium max-w-xs truncate">{a.title}</td>
                <td className="px-4 py-3"><span className="inline-flex px-2 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-700">{a.type}</span></td>
                <td className="px-4 py-3">{a.targetScope}{a.targetBlock ? ` - ${a.targetBlock.name}` : ''}</td>
                <td className="px-4 py-3 text-gray-500">{a.publishedAt ? new Date(a.publishedAt).toLocaleDateString() : '—'}</td>
                <td className="px-4 py-3"><span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${a.publishedAt ? 'bg-green-100 text-green-700' : 'bg-yellow-100 text-yellow-700'}`}>{a.publishedAt ? 'Published' : 'Draft'}</span></td>
                <td className="px-4 py-3">
                  {!a.publishedAt && (
                    <button onClick={() => { if (window.confirm('Publish this announcement?')) publish.mutate(a.id); }} className="text-blue-600 hover:underline text-xs">Publish</button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="flex items-center justify-between px-4 py-3 border-t border-gray-200">
          <span className="text-xs text-gray-500">Total: {data?.total ?? 0}</span>
          <div className="flex gap-2">
            <button onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0} className="px-3 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50 disabled:opacity-40">Prev</button>
            <span className="px-3 py-1 text-xs">{page + 1} / {data?.totalPages ?? 1}</span>
            <button onClick={() => setPage((p) => p + 1)} disabled={page + 1 >= (data?.totalPages ?? 1)} className="px-3 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50 disabled:opacity-40">Next</button>
          </div>
        </div>
      </div>

      {showCreate && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/50" onClick={() => setShowCreate(false)} />
          <div className="relative bg-white rounded-lg shadow-xl w-full max-w-lg p-6 max-h-[90vh] overflow-y-auto">
            <h2 className="text-lg font-semibold mb-4">Create Announcement</h2>
            <form onSubmit={handleCreate} className="space-y-3">
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Title <span className="text-red-500">*</span></label>
                <input name="title" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" /></div>
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Content <span className="text-red-500">*</span></label>
                <textarea name="content" rows={4} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm resize-y" /></div>
              <div className="grid grid-cols-2 gap-3">
                <div><label className="block text-sm font-medium text-gray-700 mb-1">Type</label>
                  <select name="type" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm bg-white">
                    {TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
                  </select></div>
                <div><label className="block text-sm font-medium text-gray-700 mb-1">Scope</label>
                  <select name="targetScope" value={scope} onChange={(e) => setScope(e.target.value)} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm bg-white">
                    <option value="ALL">All</option>
                    <option value="BLOCK">Block</option>
                    <option value="FLOOR">Floor</option>
                  </select></div>
              </div>
              {scope !== 'ALL' && (
                <div><label className="block text-sm font-medium text-gray-700 mb-1">Block ID</label>
                  <input name="targetBlockId" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" placeholder="Block UUID" /></div>
              )}
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Publish</label>
                <select name="publishNow" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm bg-white">
                  <option value="true">Publish now</option>
                  <option value="false">Save as draft</option>
                </select></div>
              {formError && <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded">{formError}</p>}
              <div className="flex gap-2 justify-end pt-2">
                <button type="button" onClick={() => setShowCreate(false)} className="px-4 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50">Cancel</button>
                <button type="submit" disabled={create.isPending} className="px-4 py-2 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50">
                  {create.isPending ? 'Creating...' : 'Create'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
"""

# ── pages/AmenitiesPage.tsx ───────────────────────────────────────────────────
FILES["pages/AmenitiesPage.tsx"] = r"""import React, { useState } from 'react';
import { useAmenities, useAmenityBookings, useApproveBooking, useRejectBooking, useCreateAmenity, useUpdateAmenity } from '../api/hooks';

export function AmenitiesPage() {
  const [tab, setTab] = useState<'amenities' | 'bookings'>('amenities');
  const [modal, setModal] = useState<null | 'create' | any>(null);
  const [formError, setFormError] = useState('');
  const [rejectReason, setRejectReason] = useState('');
  const [rejectId, setRejectId] = useState<string | null>(null);

  const { data: amenitiesData, isLoading, isError } = useAmenities();
  const { data: bookingsData, isLoading: bLoading } = useAmenityBookings({ status: 'PENDING', size: 50 });
  const create = useCreateAmenity();
  const update = useUpdateAmenity();
  const approve = useApproveBooking();
  const reject = useRejectBooking();
  const isEdit = modal && modal !== 'create';

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError('');
    const fd = new FormData(e.target as HTMLFormElement);
    const payload = { name: fd.get('name'), location: fd.get('location'), capacity: Number(fd.get('capacity')), openingTime: fd.get('openingTime'), closingTime: fd.get('closingTime'), maxDailyBookingsPerResident: Number(fd.get('maxDailyBookingsPerResident')), requiresApproval: fd.get('requiresApproval') === 'true' };
    if (!payload.name) { setFormError('Name is required'); return; }
    try {
      if (isEdit) await update.mutateAsync({ id: modal.id, data: payload });
      else await create.mutateAsync(payload);
      setModal(null);
    } catch (err: any) { setFormError(err?.response?.data?.message ?? 'Failed'); }
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Amenities</h1>
        {tab === 'amenities' && <button onClick={() => { setModal('create'); setFormError(''); }} className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-md text-sm font-medium">Add Amenity</button>}
      </div>
      <div className="flex gap-4 mb-4 border-b border-gray-200">
        <button onClick={() => setTab('amenities')} className={`pb-3 text-sm font-medium border-b-2 transition-colors ${tab === 'amenities' ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'}`}>Amenities</button>
        <button onClick={() => setTab('bookings')} className={`pb-3 text-sm font-medium border-b-2 transition-colors ${tab === 'bookings' ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'}`}>
          Pending Bookings {bookingsData?.data?.length > 0 && <span className="ml-1 bg-red-500 text-white text-xs rounded-full px-1.5">{bookingsData.data.length}</span>}
        </button>
      </div>

      {isError && <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 mb-4">Failed to load data.</div>}

      {tab === 'amenities' && (
        <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="text-left px-4 py-3 font-medium text-gray-500">Name</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">Location</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">Capacity</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">Hours</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">Requires Approval</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {isLoading && <tr><td colSpan={6} className="text-center py-8 text-gray-400">Loading...</td></tr>}
              {!isLoading && !amenitiesData?.data?.length && <tr><td colSpan={6} className="text-center py-8 text-gray-400">No amenities found</td></tr>}
              {amenitiesData?.data?.map((a: any) => (
                <tr key={a.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3 font-medium">{a.name}</td>
                  <td className="px-4 py-3 text-gray-500">{a.location ?? '—'}</td>
                  <td className="px-4 py-3">{a.capacity}</td>
                  <td className="px-4 py-3">{a.openingTime} - {a.closingTime}</td>
                  <td className="px-4 py-3"><span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${a.requiresApproval ? 'bg-yellow-100 text-yellow-700' : 'bg-green-100 text-green-700'}`}>{a.requiresApproval ? 'Yes' : 'No'}</span></td>
                  <td className="px-4 py-3"><button onClick={() => { setModal(a); setFormError(''); }} className="text-blue-600 hover:underline text-xs">Edit</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {tab === 'bookings' && (
        <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="text-left px-4 py-3 font-medium text-gray-500">Amenity</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">Resident</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">Apartment</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">Date</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">Time</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {bLoading && <tr><td colSpan={6} className="text-center py-8 text-gray-400">Loading...</td></tr>}
              {!bLoading && !bookingsData?.data?.length && <tr><td colSpan={6} className="text-center py-8 text-gray-400">No pending bookings</td></tr>}
              {bookingsData?.data?.map((b: any) => (
                <tr key={b.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3 font-medium">{b.amenity?.name}</td>
                  <td className="px-4 py-3">{b.resident?.user?.fullName}</td>
                  <td className="px-4 py-3">{b.apartment?.unitNumber}</td>
                  <td className="px-4 py-3">{b.bookingDate}</td>
                  <td className="px-4 py-3">{b.startTime} - {b.endTime}</td>
                  <td className="px-4 py-3 flex gap-2">
                    <button onClick={() => approve.mutate(b.id)} disabled={approve.isPending} className="text-green-600 hover:underline text-xs disabled:opacity-50">Approve</button>
                    <button onClick={() => setRejectId(b.id)} className="text-red-600 hover:underline text-xs">Reject</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Reject dialog */}
      {rejectId && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/50" onClick={() => setRejectId(null)} />
          <div className="relative bg-white rounded-lg shadow-xl w-full max-w-sm p-6">
            <h2 className="text-lg font-semibold mb-3">Reject Booking</h2>
            <label className="block text-sm font-medium text-gray-700 mb-1">Reason <span className="text-red-500">*</span></label>
            <textarea value={rejectReason} onChange={(e) => setRejectReason(e.target.value)} rows={3} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm mb-4" />
            <div className="flex gap-2 justify-end">
              <button onClick={() => { setRejectId(null); setRejectReason(''); }} className="px-4 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50">Cancel</button>
              <button onClick={async () => {
                if (!rejectReason.trim()) return;
                await reject.mutateAsync({ id: rejectId, reason: rejectReason });
                setRejectId(null); setRejectReason('');
              }} disabled={reject.isPending || !rejectReason.trim()} className="px-4 py-2 text-sm bg-red-600 text-white rounded-md hover:bg-red-700 disabled:opacity-50">
                {reject.isPending ? 'Rejecting...' : 'Reject'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Create/Edit Amenity modal */}
      {modal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/50" onClick={() => setModal(null)} />
          <div className="relative bg-white rounded-lg shadow-xl w-full max-w-md p-6">
            <h2 className="text-lg font-semibold mb-4">{isEdit ? 'Edit' : 'Add'} Amenity</h2>
            <form onSubmit={handleSubmit} className="space-y-3">
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Name <span className="text-red-500">*</span></label>
                <input name="name" defaultValue={isEdit ? modal.name : ''} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" /></div>
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Location</label>
                <input name="location" defaultValue={isEdit ? modal.location ?? '' : ''} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" /></div>
              <div className="grid grid-cols-2 gap-3">
                <div><label className="block text-sm font-medium text-gray-700 mb-1">Capacity</label>
                  <input name="capacity" type="number" defaultValue={isEdit ? modal.capacity : 10} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" /></div>
                <div><label className="block text-sm font-medium text-gray-700 mb-1">Max bookings/resident/day</label>
                  <input name="maxDailyBookingsPerResident" type="number" defaultValue={isEdit ? modal.maxDailyBookingsPerResident : 1} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" /></div>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div><label className="block text-sm font-medium text-gray-700 mb-1">Opening Time</label>
                  <input name="openingTime" type="time" defaultValue={isEdit ? modal.openingTime : '06:00'} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" /></div>
                <div><label className="block text-sm font-medium text-gray-700 mb-1">Closing Time</label>
                  <input name="closingTime" type="time" defaultValue={isEdit ? modal.closingTime : '22:00'} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" /></div>
              </div>
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Requires Approval</label>
                <select name="requiresApproval" defaultValue={isEdit ? String(modal.requiresApproval) : 'false'} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm bg-white">
                  <option value="false">No</option><option value="true">Yes</option>
                </select></div>
              {formError && <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded">{formError}</p>}
              <div className="flex gap-2 justify-end pt-2">
                <button type="button" onClick={() => setModal(null)} className="px-4 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50">Cancel</button>
                <button type="submit" disabled={create.isPending || update.isPending} className="px-4 py-2 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50">
                  {create.isPending || update.isPending ? 'Saving...' : 'Save'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
"""

# ── pages/ParkingPage.tsx ─────────────────────────────────────────────────────
FILES["pages/ParkingPage.tsx"] = r"""import React, { useState } from 'react';
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
"""

# ── pages/ReportsPage.tsx ─────────────────────────────────────────────────────
FILES["pages/ReportsPage.tsx"] = r"""import React, { useState } from 'react';
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
"""

for rel_path, content in FILES.items():
    full_path = os.path.join(BASE, rel_path)
    os.makedirs(os.path.dirname(full_path), exist_ok=True)
    with open(full_path, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f"wrote {rel_path}")

print("\nALL ADMIN PAGES DONE")
PYEOF
