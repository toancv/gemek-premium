"""Generates all resident app source files."""
import os

BASE = "D:/Projects/my-project/gemek-premium/frontend/apps/resident/src"
os.makedirs(BASE + "/api", exist_ok=True)
os.makedirs(BASE + "/components", exist_ok=True)
os.makedirs(BASE + "/pages", exist_ok=True)
os.makedirs(BASE + "/store", exist_ok=True)
os.makedirs(BASE + "/types", exist_ok=True)

FILES = {}

# ── api/client.ts (same pattern as admin) ────────────────────────────────────
FILES["api/client.ts"] = r"""import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api';

export const apiClient = axios.create({ baseURL: BASE_URL });

apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const storeState = (window as any).__gemekAuthState?.();
  if (storeState?.accessToken) {
    config.headers.Authorization = `Bearer ${storeState.accessToken}`;
  }
  return config;
});

let isRefreshing = false;
let failedQueue: Array<{ resolve: (v: string) => void; reject: (e: unknown) => void }> = [];

function processQueue(error: unknown, token: string | null) {
  failedQueue.forEach((p) => (error ? p.reject(error) : p.resolve(token!)));
  failedQueue = [];
}

apiClient.interceptors.response.use(
  (res) => res,
  async (error: AxiosError) => {
    const original = error.config as InternalAxiosRequestConfig & { _retry?: boolean };
    if (error.response?.status === 401 && !original._retry) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        }).then((token) => {
          original.headers.Authorization = `Bearer ${token}`;
          return apiClient(original);
        });
      }
      original._retry = true;
      isRefreshing = true;
      const rt = localStorage.getItem('gemek_refresh');
      if (!rt) { isRefreshing = false; window.location.href = '/login'; return Promise.reject(error); }
      try {
        const res = await axios.post(`${BASE_URL}/auth/refresh`, { refreshToken: rt });
        const newToken: string = res.data.accessToken;
        (window as any).__gemekSetToken?.(newToken);
        processQueue(null, newToken);
        original.headers.Authorization = `Bearer ${newToken}`;
        return apiClient(original);
      } catch (refreshErr) {
        processQueue(refreshErr, null);
        localStorage.removeItem('gemek_refresh');
        window.location.href = '/login';
        return Promise.reject(refreshErr);
      } finally {
        isRefreshing = false;
      }
    }
    return Promise.reject(error);
  }
);
"""

# ── api/hooks.ts ──────────────────────────────────────────────────────────────
FILES["api/hooks.ts"] = r"""import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from './client';

const get = (url: string, params?: Record<string, unknown>) =>
  apiClient.get(url, { params }).then((r) => r.data);
const post = (url: string, data?: unknown) => apiClient.post(url, data).then((r) => r.data);
const put = (url: string, data?: unknown) => apiClient.put(url, data).then((r) => r.data);

export const useMe = () =>
  useQuery({ queryKey: ['me'], queryFn: () => get('/auth/me') });

export const useMyTickets = (params?: Record<string, unknown>) =>
  useQuery({ queryKey: ['tickets', params], queryFn: () => get('/tickets', params) });

export const useTicket = (id: string) =>
  useQuery({ queryKey: ['tickets', id], queryFn: () => get(`/tickets/${id}`), enabled: !!id });

export const useCreateTicket = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: unknown) => post('/tickets', data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['tickets'] }),
  });
};

export const useRateTicket = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: unknown }) =>
      post(`/tickets/${id}/rate`, data),
    onSuccess: (_d, v) => qc.invalidateQueries({ queryKey: ['tickets', v.id] }),
  });
};

export const useAmenities = () =>
  useQuery({ queryKey: ['amenities'], queryFn: () => get('/amenities') });

export const useMyBookings = (params?: Record<string, unknown>) =>
  useQuery({ queryKey: ['my-bookings', params], queryFn: () => get('/amenity-bookings', params) });

export const useCreateBooking = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: unknown) => post('/amenity-bookings', data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['my-bookings'] }),
  });
};

export const useCancelBooking = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => put(`/amenity-bookings/${id}/cancel`, {}),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['my-bookings'] }),
  });
};

export const useParkingAssignments = () =>
  useQuery({ queryKey: ['my-parking'], queryFn: () => get('/parking/assignments', { isActive: true }) });

export const useAnnouncements = (params?: Record<string, unknown>) =>
  useQuery({ queryKey: ['announcements', params], queryFn: () => get('/announcements', params) });

export const useMarkAnnouncementRead = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => post(`/announcements/${id}/read`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['announcements'] }),
  });
};

export const useChangePassword = () =>
  useMutation({ mutationFn: (data: unknown) => put('/auth/me/password', data) });

export const useNotifications = () =>
  useQuery({ queryKey: ['notifications'], queryFn: () => get('/notifications', { size: 20 }) });

export const useMarkAllRead = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => put('/notifications/read-all'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notifications'] }),
  });
};
"""

# ── store/authStore.ts ────────────────────────────────────────────────────────
FILES["store/authStore.ts"] = r"""import { create } from 'zustand';
import { apiClient } from '../api/client';

interface AuthUser {
  id: string;
  email: string;
  fullName: string;
  role: string;
  avatarUrl?: string | null;
}

interface AuthState {
  user: AuthUser | null;
  accessToken: string | null;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  refreshToken: () => Promise<void>;
  isAuthenticated: () => boolean;
  setTokenAndUser: (token: string, user: AuthUser) => void;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  accessToken: null,
  isAuthenticated: () => !!get().accessToken,
  setTokenAndUser: (token, user) => set({ accessToken: token, user }),
  login: async (email, password) => {
    const res = await apiClient.post('/auth/login', { email, password });
    const { accessToken, refreshToken, user } = res.data;
    localStorage.setItem('gemek_refresh', refreshToken);
    set({ accessToken, user });
  },
  logout: async () => {
    try { await apiClient.post('/auth/logout'); } catch { /* ignore */ }
    localStorage.removeItem('gemek_refresh');
    set({ accessToken: null, user: null });
  },
  refreshToken: async () => {
    const rt = localStorage.getItem('gemek_refresh');
    if (!rt) throw new Error('No refresh token');
    const res = await apiClient.post('/auth/refresh', { refreshToken: rt });
    set((state) => ({ ...state, accessToken: res.data.accessToken }));
  },
}));
"""

# ── main.tsx ──────────────────────────────────────────────────────────────────
FILES["main.tsx"] = r"""import React from 'react';
import ReactDOM from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import App from './App';
import './index.css';
import { useAuthStore } from './store/authStore';

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, staleTime: 30_000 } },
});

(window as any).__gemekAuthState = () => useAuthStore.getState();
(window as any).__gemekSetToken = (token: string) => {
  useAuthStore.getState().setTokenAndUser(token, useAuthStore.getState().user!);
};

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>
  </React.StrictMode>
);
"""

# ── App.tsx ───────────────────────────────────────────────────────────────────
FILES["App.tsx"] = r"""import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useAuthStore } from './store/authStore';
import { Layout } from './components/Layout';
import { LoginPage } from './pages/LoginPage';
import { HomePage } from './pages/HomePage';
import { MyTicketsPage } from './pages/MyTicketsPage';
import { TicketDetailPage } from './pages/TicketDetailPage';
import { AmenitiesPage } from './pages/AmenitiesPage';
import { MyBookingsPage } from './pages/MyBookingsPage';
import { ParkingPage } from './pages/ParkingPage';
import { AnnouncementsPage } from './pages/AnnouncementsPage';
import { ProfilePage } from './pages/ProfilePage';

function RequireAuth({ children }: { children: React.ReactNode }) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  if (!isAuthenticated()) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/" element={<RequireAuth><Layout /></RequireAuth>}>
          <Route index element={<HomePage />} />
          <Route path="tickets" element={<MyTicketsPage />} />
          <Route path="tickets/:id" element={<TicketDetailPage />} />
          <Route path="amenities" element={<AmenitiesPage />} />
          <Route path="bookings" element={<MyBookingsPage />} />
          <Route path="parking" element={<ParkingPage />} />
          <Route path="announcements" element={<AnnouncementsPage />} />
          <Route path="profile" element={<ProfilePage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
"""

# ── components/Layout.tsx ─────────────────────────────────────────────────────
FILES["components/Layout.tsx"] = r"""import React, { useState } from 'react';
import { Outlet, NavLink, useNavigate } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
import { useNotifications, useMarkAllRead } from '../api/hooks';

export function Layout() {
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const navigate = useNavigate();
  const [notifOpen, setNotifOpen] = useState(false);
  const { data: notifData } = useNotifications();
  const markAllRead = useMarkAllRead();

  const handleLogout = async () => { await logout(); navigate('/login'); };

  const navLinks = [
    { to: '/', label: 'Home', icon: 'H', end: true },
    { to: '/tickets', label: 'Tickets', icon: 'T' },
    { to: '/amenities', label: 'Amenities', icon: 'A' },
    { to: '/bookings', label: 'Bookings', icon: 'B' },
    { to: '/parking', label: 'Parking', icon: 'P' },
    { to: '/announcements', label: 'News', icon: 'N' },
    { to: '/profile', label: 'Profile', icon: 'Me' },
  ];

  return (
    <div className="flex flex-col min-h-screen bg-gray-50 max-w-md mx-auto">
      {/* Top bar */}
      <header className="bg-blue-600 text-white px-4 py-3 flex items-center justify-between sticky top-0 z-40">
        <div>
          <h1 className="font-semibold text-sm">Gemek Premium</h1>
          <p className="text-blue-200 text-xs">Hello, {user?.fullName?.split(' ').pop()}</p>
        </div>
        <div className="flex items-center gap-3">
          <div className="relative">
            <button onClick={() => setNotifOpen((o) => !o)} className="relative p-1">
              <svg className="h-5 w-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
              </svg>
              {notifData?.unreadCount > 0 && (
                <span className="absolute -top-0.5 -right-0.5 h-4 w-4 bg-red-500 text-white text-xs rounded-full flex items-center justify-center">{notifData.unreadCount > 9 ? '9+' : notifData.unreadCount}</span>
              )}
            </button>
            {notifOpen && (
              <div className="absolute right-0 top-full mt-1 w-72 bg-white rounded-lg shadow-lg border border-gray-200 z-50">
                <div className="flex items-center justify-between px-4 py-2 border-b border-gray-100">
                  <span className="font-medium text-sm text-gray-900">Notifications</span>
                  <button onClick={() => markAllRead.mutate()} className="text-xs text-blue-600">Mark all read</button>
                </div>
                <div className="max-h-64 overflow-y-auto">
                  {(!notifData?.data?.length) && <p className="text-center text-gray-400 text-sm py-4">No notifications</p>}
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
          <button onClick={handleLogout} className="text-blue-200 text-xs hover:text-white">Out</button>
        </div>
      </header>

      {/* Page content */}
      <main className="flex-1 pb-16 overflow-y-auto">
        <Outlet />
      </main>

      {/* Bottom nav */}
      <nav className="fixed bottom-0 left-1/2 -translate-x-1/2 w-full max-w-md bg-white border-t border-gray-200 flex z-40">
        {navLinks.map((n) => (
          <NavLink key={n.to} to={n.to} end={n.end} className={({ isActive }) =>
            'flex-1 flex flex-col items-center py-2 text-xs ' + (isActive ? 'text-blue-600' : 'text-gray-500')
          }>
            <span className="text-xs font-bold w-5 h-5 flex items-center justify-center">{n.icon}</span>
            <span>{n.label}</span>
          </NavLink>
        ))}
      </nav>
    </div>
  );
}
"""

# ── pages/LoginPage.tsx ───────────────────────────────────────────────────────
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

  if (isAuthenticated()) { navigate('/', { replace: true }); return null; }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    if (!email.trim()) { setError('Email is required'); return; }
    if (!password) { setError('Password is required'); return; }
    setLoading(true);
    try {
      await login(email, password);
      navigate('/', { replace: true });
    } catch (err: any) {
      setError(err?.response?.data?.message ?? 'Invalid email or password');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-blue-600 flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-sm p-8">
        <div className="text-center mb-8">
          <div className="w-16 h-16 bg-blue-100 rounded-2xl flex items-center justify-center mx-auto mb-4">
            <span className="text-blue-600 font-bold text-xl">GP</span>
          </div>
          <h1 className="text-xl font-bold text-gray-900">Gemek Premium</h1>
          <p className="text-gray-500 text-sm mt-1">Resident Portal</p>
        </div>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Email</label>
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)}
              className="block w-full rounded-lg border border-gray-300 px-4 py-3 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="your@email.com" autoComplete="email" />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Password</label>
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)}
              className="block w-full rounded-lg border border-gray-300 px-4 py-3 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="••••••••" autoComplete="current-password" />
          </div>
          {error && <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded-lg">{error}</p>}
          <button type="submit" disabled={loading}
            className="w-full bg-blue-600 hover:bg-blue-700 text-white rounded-lg py-3 text-sm font-semibold transition-colors disabled:opacity-50 flex items-center justify-center gap-2">
            {loading && <svg className="animate-spin h-4 w-4" fill="none" viewBox="0 0 24 24"><circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"/><path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/></svg>}
            {loading ? 'Signing in...' : 'Sign in'}
          </button>
        </form>
      </div>
    </div>
  );
}
"""

# ── pages/HomePage.tsx ────────────────────────────────────────────────────────
FILES["pages/HomePage.tsx"] = r"""import React from 'react';
import { Link } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
import { useMyTickets, useMyBookings, useAnnouncements, useMe } from '../api/hooks';

export function HomePage() {
  const user = useAuthStore((s) => s.user);
  const { data: me } = useMe();
  const { data: ticketsData } = useMyTickets({ size: 5, status: 'NEW,ASSIGNED,IN_PROGRESS' });
  const { data: bookingsData } = useMyBookings({ size: 5 });
  const { data: announcements } = useAnnouncements({ size: 3, isPublished: true });

  return (
    <div className="p-4 space-y-4">
      {/* Welcome card */}
      <div className="bg-blue-600 rounded-2xl p-5 text-white">
        <p className="text-blue-200 text-sm">Welcome back</p>
        <h2 className="text-xl font-bold mt-0.5">{user?.fullName}</h2>
        {me?.phone && <p className="text-blue-200 text-xs mt-1">{me.phone}</p>}
      </div>

      {/* Quick stats */}
      <div className="grid grid-cols-2 gap-3">
        <Link to="/tickets" className="bg-white rounded-xl border border-gray-200 p-4 hover:border-blue-300 transition-colors">
          <p className="text-2xl font-bold text-blue-600">{ticketsData?.total ?? 0}</p>
          <p className="text-sm text-gray-500 mt-0.5">Active Tickets</p>
        </Link>
        <Link to="/bookings" className="bg-white rounded-xl border border-gray-200 p-4 hover:border-blue-300 transition-colors">
          <p className="text-2xl font-bold text-green-600">{bookingsData?.total ?? 0}</p>
          <p className="text-sm text-gray-500 mt-0.5">Bookings</p>
        </Link>
      </div>

      {/* Announcements */}
      <div>
        <div className="flex items-center justify-between mb-2">
          <h3 className="font-semibold text-gray-900">Announcements</h3>
          <Link to="/announcements" className="text-xs text-blue-600">View all</Link>
        </div>
        {!announcements?.data?.length && (
          <div className="bg-white rounded-xl border border-gray-200 p-4 text-center text-gray-400 text-sm">No announcements</div>
        )}
        <div className="space-y-2">
          {announcements?.data?.map((a: any) => (
            <Link to="/announcements" key={a.id} className="block bg-white rounded-xl border border-gray-200 p-4 hover:border-blue-300 transition-colors">
              <div className="flex items-start justify-between gap-2">
                <p className="font-medium text-gray-900 text-sm">{a.title}</p>
                <span className={`flex-shrink-0 text-xs px-2 py-0.5 rounded-full ${a.type === 'URGENT' ? 'bg-red-100 text-red-600' : 'bg-blue-100 text-blue-600'}`}>{a.type}</span>
              </div>
              <p className="text-xs text-gray-400 mt-1">{a.publishedAt ? new Date(a.publishedAt).toLocaleDateString() : ''}</p>
            </Link>
          ))}
        </div>
      </div>
    </div>
  );
}
"""

# ── pages/MyTicketsPage.tsx ───────────────────────────────────────────────────
FILES["pages/MyTicketsPage.tsx"] = r"""import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMyTickets, useCreateTicket } from '../api/hooks';
import { useMe } from '../api/hooks';

const STATUS_BG: Record<string, string> = {
  NEW: 'bg-blue-100 text-blue-700', ASSIGNED: 'bg-purple-100 text-purple-700',
  IN_PROGRESS: 'bg-yellow-100 text-yellow-700', DONE: 'bg-green-100 text-green-700',
  CANCELLED: 'bg-gray-100 text-gray-700',
};

export function MyTicketsPage() {
  const { data: me } = useMe();
  const { data, isLoading } = useMyTickets({ size: 20 });
  const create = useCreateTicket();
  const [showForm, setShowForm] = useState(false);
  const [formError, setFormError] = useState('');

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError('');
    const fd = new FormData(e.target as HTMLFormElement);
    const title = (fd.get('title') as string).trim();
    if (!title) { setFormError('Title is required'); return; }
    if (!me?.id) { setFormError('Could not determine your apartment'); return; }
    try {
      await create.mutateAsync({ apartmentId: fd.get('apartmentId'), category: fd.get('category'), title, description: fd.get('description'), priority: 'MEDIUM' });
      setShowForm(false);
    } catch (err: any) { setFormError(err?.response?.data?.message ?? 'Failed to create ticket'); }
  };

  return (
    <div className="p-4">
      <div className="flex items-center justify-between mb-4">
        <h1 className="text-lg font-bold text-gray-900">My Tickets</h1>
        <button onClick={() => { setShowForm(true); setFormError(''); }} className="bg-blue-600 text-white px-4 py-2 rounded-lg text-sm font-medium">+ New</button>
      </div>

      {isLoading && <div className="text-center py-8 text-gray-400">Loading...</div>}
      {!isLoading && !data?.data?.length && (
        <div className="text-center py-12 text-gray-400">
          <p className="text-4xl mb-2">🎫</p>
          <p>No tickets yet</p>
          <button onClick={() => setShowForm(true)} className="mt-3 text-blue-600 text-sm">Create your first ticket</button>
        </div>
      )}
      <div className="space-y-3">
        {data?.data?.map((t: any) => (
          <Link to={`/tickets/${t.id}`} key={t.id} className="block bg-white rounded-xl border border-gray-200 p-4 hover:border-blue-300 transition-colors">
            <div className="flex items-start justify-between gap-2">
              <p className="font-medium text-gray-900 text-sm flex-1">{t.title}</p>
              <span className={`flex-shrink-0 text-xs px-2 py-0.5 rounded-full ${STATUS_BG[t.status] ?? 'bg-gray-100 text-gray-700'}`}>{t.status.replace('_', ' ')}</span>
            </div>
            <p className="text-xs text-gray-400 mt-1">{t.category.replace(/_/g, ' ')} • {new Date(t.createdAt).toLocaleDateString()}</p>
          </Link>
        ))}
      </div>

      {showForm && (
        <div className="fixed inset-0 z-50 flex items-end justify-center">
          <div className="absolute inset-0 bg-black/50" onClick={() => setShowForm(false)} />
          <div className="relative bg-white rounded-t-2xl w-full max-w-md p-6 pb-8">
            <h2 className="text-lg font-semibold mb-4">New Ticket</h2>
            <form onSubmit={handleCreate} className="space-y-3">
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Apartment ID <span className="text-red-500">*</span></label>
                <input name="apartmentId" className="block w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm" placeholder="Your apartment UUID" /></div>
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Category</label>
                <select name="category" className="block w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm bg-white">
                  <option value="MAINTENANCE_REPAIR">Maintenance & Repair</option>
                  <option value="COMPLAINT">Complaint</option>
                  <option value="ADMINISTRATIVE">Administrative</option>
                  <option value="SUGGESTION_FEEDBACK">Suggestion / Feedback</option>
                  <option value="OTHER">Other</option>
                </select></div>
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Title <span className="text-red-500">*</span></label>
                <input name="title" maxLength={255} className="block w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm" /></div>
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Description</label>
                <textarea name="description" rows={3} className="block w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm resize-none" /></div>
              {formError && <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded-lg">{formError}</p>}
              <div className="flex gap-2 pt-1">
                <button type="button" onClick={() => setShowForm(false)} className="flex-1 py-2.5 text-sm border border-gray-300 rounded-lg hover:bg-gray-50">Cancel</button>
                <button type="submit" disabled={create.isPending} className="flex-1 py-2.5 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50">
                  {create.isPending ? 'Submitting...' : 'Submit'}
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

# ── pages/TicketDetailPage.tsx ────────────────────────────────────────────────
FILES["pages/TicketDetailPage.tsx"] = r"""import React, { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useTicket, useRateTicket } from '../api/hooks';

const STATUS_BG: Record<string, string> = {
  NEW: 'bg-blue-100 text-blue-700', ASSIGNED: 'bg-purple-100 text-purple-700',
  IN_PROGRESS: 'bg-yellow-100 text-yellow-700', DONE: 'bg-green-100 text-green-700',
  CANCELLED: 'bg-gray-100 text-gray-700',
};

export function TicketDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data: ticket, isLoading, isError } = useTicket(id!);
  const rate = useRateTicket();
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState('');
  const [rateError, setRateError] = useState('');

  if (isLoading) return <div className="flex items-center justify-center h-64"><svg className="animate-spin h-8 w-8 text-blue-600" fill="none" viewBox="0 0 24 24"><circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"/><path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/></svg></div>;
  if (isError) return <div className="p-4 bg-red-50 text-red-700 m-4 rounded-xl">Failed to load ticket.</div>;

  const canRate = ticket.status === 'DONE' && !ticket.rating;

  const handleRate = async (e: React.FormEvent) => {
    e.preventDefault();
    setRateError('');
    if (!rating) { setRateError('Please select a rating'); return; }
    try {
      await rate.mutateAsync({ id: id!, data: { rating, comment: comment || null } });
    } catch (err: any) { setRateError(err?.response?.data?.message ?? 'Failed to submit rating'); }
  };

  return (
    <div className="p-4 space-y-4">
      <button onClick={() => navigate(-1)} className="text-sm text-blue-600 flex items-center gap-1">&larr; Back</button>

      <div className="bg-white rounded-xl border border-gray-200 p-4">
        <div className="flex items-start justify-between gap-2 mb-3">
          <h1 className="font-semibold text-gray-900">{ticket.title}</h1>
          <span className={`flex-shrink-0 text-xs px-2 py-0.5 rounded-full ${STATUS_BG[ticket.status] ?? 'bg-gray-100 text-gray-700'}`}>{ticket.status}</span>
        </div>
        <div className="space-y-1 text-sm">
          <div className="flex gap-2"><span className="text-gray-500">Category:</span><span>{ticket.category?.replace(/_/g, ' ')}</span></div>
          <div className="flex gap-2"><span className="text-gray-500">Priority:</span><span>{ticket.priority}</span></div>
          <div className="flex gap-2"><span className="text-gray-500">Submitted:</span><span>{new Date(ticket.createdAt).toLocaleDateString()}</span></div>
          {ticket.assignedToUser && <div className="flex gap-2"><span className="text-gray-500">Assigned to:</span><span>{ticket.assignedToUser.fullName}</span></div>}
          {ticket.slaDeadline && <div className="flex gap-2"><span className="text-gray-500">SLA:</span><span className={ticket.slaBreached ? 'text-red-600 font-medium' : ''}>{new Date(ticket.slaDeadline).toLocaleDateString()}{ticket.slaBreached ? ' (Breached)' : ''}</span></div>}
        </div>
        {ticket.description && <p className="mt-3 text-sm text-gray-600 bg-gray-50 p-3 rounded-lg">{ticket.description}</p>}
        {ticket.rating && (
          <div className="mt-3 p-3 bg-yellow-50 rounded-lg">
            <p className="text-sm font-medium">Your rating: {'★'.repeat(ticket.rating)}{'☆'.repeat(5 - ticket.rating)}</p>
            {ticket.ratingComment && <p className="text-xs text-gray-500 mt-1">"{ticket.ratingComment}"</p>}
          </div>
        )}
      </div>

      {ticket.photos?.length > 0 && (
        <div className="bg-white rounded-xl border border-gray-200 p-4">
          <h2 className="font-semibold text-gray-900 mb-3 text-sm">Photos</h2>
          <div className="grid grid-cols-2 gap-2">
            {ticket.photos.map((p: any) => (
              <div key={p.id} className="rounded-lg overflow-hidden border border-gray-200">
                <div className="bg-gray-100 px-2 py-0.5 text-xs text-gray-500">{p.phase}</div>
                <img src={p.presignedUrl} alt={p.fileName} className="w-full h-28 object-cover" />
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Status timeline */}
      <div className="bg-white rounded-xl border border-gray-200 p-4">
        <h2 className="font-semibold text-gray-900 mb-3 text-sm">Timeline</h2>
        <div className="space-y-3">
          {ticket.statusHistory?.map((h: any) => (
            <div key={h.id} className="flex gap-3 text-sm">
              <div className="w-2 h-2 rounded-full bg-blue-500 mt-1.5 flex-shrink-0" />
              <div>
                <p className="font-medium">{h.oldStatus ?? 'Created'} → {h.newStatus}</p>
                <p className="text-xs text-gray-400">{h.changedBy?.fullName} • {new Date(h.changedAt).toLocaleString()}</p>
                {h.notes && <p className="text-xs text-gray-500 mt-0.5">{h.notes}</p>}
              </div>
            </div>
          ))}
        </div>
      </div>

      {canRate && (
        <div className="bg-white rounded-xl border border-gray-200 p-4">
          <h2 className="font-semibold text-gray-900 mb-3 text-sm">Rate this service</h2>
          <form onSubmit={handleRate} className="space-y-3">
            <div className="flex gap-2 justify-center">
              {[1,2,3,4,5].map((s) => (
                <button type="button" key={s} onClick={() => setRating(s)}
                  className={`text-2xl transition-transform hover:scale-110 ${s <= rating ? 'text-yellow-400' : 'text-gray-300'}`}>★</button>
              ))}
            </div>
            <textarea value={comment} onChange={(e) => setComment(e.target.value)} rows={2}
              placeholder="Optional comment..." className="block w-full border border-gray-300 rounded-lg px-3 py-2 text-sm resize-none" />
            {rateError && <p className="text-xs text-red-600">{rateError}</p>}
            <button type="submit" disabled={rate.isPending} className="w-full bg-yellow-500 text-white rounded-lg py-2.5 text-sm font-medium hover:bg-yellow-600 disabled:opacity-50">
              {rate.isPending ? 'Submitting...' : 'Submit Rating'}
            </button>
          </form>
        </div>
      )}
    </div>
  );
}
"""

# ── pages/AmenitiesPage.tsx ───────────────────────────────────────────────────
FILES["pages/AmenitiesPage.tsx"] = r"""import React, { useState } from 'react';
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
"""

# ── pages/MyBookingsPage.tsx ──────────────────────────────────────────────────
FILES["pages/MyBookingsPage.tsx"] = r"""import React from 'react';
import { useMyBookings, useCancelBooking } from '../api/hooks';

const STATUS_BG: Record<string, string> = {
  PENDING: 'bg-yellow-100 text-yellow-700', APPROVED: 'bg-green-100 text-green-700',
  REJECTED: 'bg-red-100 text-red-700', CANCELLED: 'bg-gray-100 text-gray-500',
  COMPLETED: 'bg-blue-100 text-blue-700',
};

export function MyBookingsPage() {
  const { data, isLoading } = useMyBookings({ size: 20 });
  const cancel = useCancelBooking();

  return (
    <div className="p-4">
      <h1 className="text-lg font-bold text-gray-900 mb-4">My Bookings</h1>
      {isLoading && <div className="text-center py-8 text-gray-400">Loading...</div>}
      {!isLoading && !data?.data?.length && (
        <div className="text-center py-12 text-gray-400">
          <p className="text-4xl mb-2">📅</p>
          <p>No bookings yet</p>
        </div>
      )}
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
              <button onClick={() => { if (window.confirm('Cancel this booking?')) cancel.mutate(b.id); }} disabled={cancel.isPending}
                className="mt-3 text-xs text-red-600 hover:underline disabled:opacity-50">Cancel booking</button>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
"""

# ── pages/ParkingPage.tsx ─────────────────────────────────────────────────────
FILES["pages/ParkingPage.tsx"] = r"""import React, { useState } from 'react';
import { useParkingAssignments } from '../api/hooks';
import { apiClient } from '../api/client';
import { useMutation, useQueryClient } from '@tanstack/react-query';

export function ParkingPage() {
  const { data, isLoading } = useParkingAssignments();
  const qc = useQueryClient();
  const [showGuestForm, setShowGuestForm] = useState(false);
  const [formError, setFormError] = useState('');
  const logGuest = useMutation({
    mutationFn: (data: unknown) => apiClient.post('/parking/guest-vehicles', data).then((r) => r.data),
    onSuccess: () => { setShowGuestForm(false); qc.invalidateQueries({ queryKey: ['my-parking'] }); },
  });

  const handleLogGuest = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError('');
    const fd = new FormData(e.target as HTMLFormElement);
    const licensePlate = (fd.get('licensePlate') as string).trim();
    const hostApartmentId = (fd.get('hostApartmentId') as string).trim();
    if (!licensePlate || !hostApartmentId) { setFormError('License plate and apartment ID are required'); return; }
    try {
      await logGuest.mutateAsync({ licensePlate, ownerName: fd.get('ownerName') || null, hostApartmentId, purpose: fd.get('purpose') || null });
    } catch (err: any) { setFormError(err?.response?.data?.message ?? 'Failed'); }
  };

  return (
    <div className="p-4 space-y-4">
      <h1 className="text-lg font-bold text-gray-900">Parking</h1>

      {/* My parking slots */}
      <div>
        <h2 className="font-semibold text-gray-700 text-sm mb-2">My Parking Slots</h2>
        {isLoading && <div className="text-center py-4 text-gray-400">Loading...</div>}
        {!isLoading && !data?.data?.length && (
          <div className="bg-white rounded-xl border border-gray-200 p-4 text-center text-gray-400 text-sm">No parking slots assigned</div>
        )}
        <div className="space-y-3">
          {data?.data?.map((a: any) => (
            <div key={a.id} className="bg-white rounded-xl border border-gray-200 p-4">
              <p className="font-semibold text-gray-900">{a.parkingSlot?.slotNumber ?? 'Slot'}</p>
              <div className="text-sm text-gray-500 mt-1 space-y-0.5">
                <p>Zone: {a.parkingSlot?.zone ?? '—'} • Type: {a.parkingSlot?.type ?? '—'}</p>
                <p>Vehicle: {a.vehicle?.licensePlate ?? '—'}</p>
                <p>Card: {a.parkingCardNumber ?? '—'}</p>
                <p>Since: {a.startDate}</p>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Guest vehicle */}
      <div>
        <div className="flex items-center justify-between mb-2">
          <h2 className="font-semibold text-gray-700 text-sm">Guest Vehicle</h2>
          <button onClick={() => { setShowGuestForm(true); setFormError(''); }} className="text-xs text-blue-600 font-medium">+ Log guest</button>
        </div>
        <p className="text-xs text-gray-400">Log a guest vehicle entering the building</p>
      </div>

      {showGuestForm && (
        <div className="fixed inset-0 z-50 flex items-end justify-center">
          <div className="absolute inset-0 bg-black/50" onClick={() => setShowGuestForm(false)} />
          <div className="relative bg-white rounded-t-2xl w-full max-w-md p-6 pb-8">
            <h2 className="text-lg font-semibold mb-4">Log Guest Vehicle</h2>
            <form onSubmit={handleLogGuest} className="space-y-3">
              <div><label className="block text-sm font-medium text-gray-700 mb-1">License Plate <span className="text-red-500">*</span></label>
                <input name="licensePlate" className="block w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm" placeholder="e.g. 51A-123.45" /></div>
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Owner Name</label>
                <input name="ownerName" className="block w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm" /></div>
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Host Apartment ID <span className="text-red-500">*</span></label>
                <input name="hostApartmentId" className="block w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm" placeholder="Apartment UUID" /></div>
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Purpose</label>
                <input name="purpose" className="block w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm" /></div>
              {formError && <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded-lg">{formError}</p>}
              <div className="flex gap-2 pt-1">
                <button type="button" onClick={() => setShowGuestForm(false)} className="flex-1 py-2.5 text-sm border border-gray-300 rounded-lg">Cancel</button>
                <button type="submit" disabled={logGuest.isPending} className="flex-1 py-2.5 text-sm bg-blue-600 text-white rounded-lg disabled:opacity-50">
                  {logGuest.isPending ? 'Logging...' : 'Log'}
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
FILES["pages/AnnouncementsPage.tsx"] = r"""import React from 'react';
import { useAnnouncements, useMarkAnnouncementRead } from '../api/hooks';

const TYPE_COLORS: Record<string, string> = {
  GENERAL: 'bg-blue-100 text-blue-700', URGENT: 'bg-red-100 text-red-700',
  MAINTENANCE: 'bg-yellow-100 text-yellow-700', AMENITY: 'bg-purple-100 text-purple-700',
  EVENT: 'bg-green-100 text-green-700',
};

export function AnnouncementsPage() {
  const { data, isLoading } = useAnnouncements({ size: 30, isPublished: true });
  const markRead = useMarkAnnouncementRead();

  return (
    <div className="p-4">
      <h1 className="text-lg font-bold text-gray-900 mb-4">Announcements</h1>
      {isLoading && <div className="text-center py-8 text-gray-400">Loading...</div>}
      {!isLoading && !data?.data?.length && (
        <div className="text-center py-12 text-gray-400">
          <p className="text-4xl mb-2">📢</p>
          <p>No announcements</p>
        </div>
      )}
      <div className="space-y-3">
        {data?.data?.map((a: any) => (
          <div key={a.id} onClick={() => { if (!a.isRead) markRead.mutate(a.id); }}
            className={`bg-white rounded-xl border p-4 cursor-pointer transition-colors ${!a.isRead ? 'border-blue-300 bg-blue-50' : 'border-gray-200'}`}>
            <div className="flex items-start justify-between gap-2 mb-2">
              <h3 className="font-semibold text-gray-900 text-sm">{a.title}</h3>
              <span className={`flex-shrink-0 text-xs px-2 py-0.5 rounded-full ${TYPE_COLORS[a.type] ?? 'bg-gray-100 text-gray-700'}`}>{a.type}</span>
            </div>
            <div className="flex items-center justify-between text-xs text-gray-400">
              <span>{a.targetScope === 'ALL' ? 'Everyone' : `${a.targetScope}${a.targetBlock ? ': ' + a.targetBlock.name : ''}`}</span>
              <span>{a.publishedAt ? new Date(a.publishedAt).toLocaleDateString() : ''}</span>
            </div>
            {!a.isRead && <div className="w-2 h-2 rounded-full bg-blue-600 absolute top-4 left-4" />}
          </div>
        ))}
      </div>
    </div>
  );
}
"""

# ── pages/ProfilePage.tsx ─────────────────────────────────────────────────────
FILES["pages/ProfilePage.tsx"] = r"""import React, { useState } from 'react';
import { useAuthStore } from '../store/authStore';
import { useMe, useChangePassword } from '../api/hooks';

export function ProfilePage() {
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const { data: me } = useMe();
  const changePassword = useChangePassword();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [pwError, setPwError] = useState('');
  const [pwSuccess, setPwSuccess] = useState(false);

  const handleChangePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setPwError('');
    setPwSuccess(false);
    if (!currentPassword || !newPassword) { setPwError('All fields are required'); return; }
    if (newPassword !== confirmPassword) { setPwError('New passwords do not match'); return; }
    if (newPassword.length < 8) { setPwError('Password must be at least 8 characters'); return; }
    try {
      await changePassword.mutateAsync({ currentPassword, newPassword });
      setPwSuccess(true);
      setCurrentPassword(''); setNewPassword(''); setConfirmPassword('');
    } catch (err: any) {
      setPwError(err?.response?.data?.message ?? 'Failed to change password');
    }
  };

  return (
    <div className="p-4 space-y-4">
      <h1 className="text-lg font-bold text-gray-900">My Profile</h1>

      {/* Profile info */}
      <div className="bg-white rounded-xl border border-gray-200 p-4">
        <div className="flex items-center gap-3 mb-4">
          <div className="w-12 h-12 rounded-full bg-blue-100 flex items-center justify-center">
            <span className="text-blue-600 font-bold text-lg">{user?.fullName?.charAt(0).toUpperCase()}</span>
          </div>
          <div>
            <p className="font-semibold text-gray-900">{user?.fullName}</p>
            <p className="text-sm text-gray-500">{user?.email}</p>
          </div>
        </div>
        <div className="space-y-2 text-sm">
          <div className="flex gap-2"><span className="text-gray-500 w-20">Role:</span><span className="font-medium">{user?.role}</span></div>
          {me?.phone && <div className="flex gap-2"><span className="text-gray-500 w-20">Phone:</span><span>{me.phone}</span></div>}
          {me?.lastLoginAt && <div className="flex gap-2"><span className="text-gray-500 w-20">Last login:</span><span>{new Date(me.lastLoginAt).toLocaleString()}</span></div>}
        </div>
      </div>

      {/* Change password */}
      <div className="bg-white rounded-xl border border-gray-200 p-4">
        <h2 className="font-semibold text-gray-900 mb-3 text-sm">Change Password</h2>
        <form onSubmit={handleChangePassword} className="space-y-3">
          <div><label className="block text-sm font-medium text-gray-700 mb-1">Current Password</label>
            <input type="password" value={currentPassword} onChange={(e) => setCurrentPassword(e.target.value)} className="block w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm" autoComplete="current-password" /></div>
          <div><label className="block text-sm font-medium text-gray-700 mb-1">New Password</label>
            <input type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} className="block w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm" autoComplete="new-password" /></div>
          <div><label className="block text-sm font-medium text-gray-700 mb-1">Confirm New Password</label>
            <input type="password" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} className="block w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm" autoComplete="new-password" /></div>
          {pwError && <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded-lg">{pwError}</p>}
          {pwSuccess && <p className="text-sm text-green-600 bg-green-50 px-3 py-2 rounded-lg">Password changed successfully</p>}
          <button type="submit" disabled={changePassword.isPending} className="w-full bg-blue-600 text-white rounded-lg py-2.5 text-sm font-medium disabled:opacity-50">
            {changePassword.isPending ? 'Changing...' : 'Change Password'}
          </button>
        </form>
      </div>

      {/* Logout */}
      <button onClick={logout} className="w-full bg-red-50 text-red-600 border border-red-200 rounded-xl py-3 text-sm font-medium hover:bg-red-100 transition-colors">
        Sign out
      </button>
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

print("\nALL RESIDENT FILES DONE")
