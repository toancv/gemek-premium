import { create } from 'zustand';
import axios from 'axios';
import { apiClient } from '../api/client';

interface AuthUser {
  id: string;
  phone: string;
  fullName: string;
  role: string;
  avatarUrl?: string | null;
}

type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated';

interface AuthState {
  user: AuthUser | null;
  accessToken: string | null;
  authStatus: AuthStatus;
  bootstrap: () => Promise<void>;
  login: (phone: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  refreshToken: () => Promise<void>;
  isAuthenticated: () => boolean;
  setTokenAndUser: (token: string, user: AuthUser) => void;
}

// Module-level guard: prevents React 18 StrictMode double-invocation from firing two refresh calls.
let bootstrapped = false;

export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  accessToken: null,
  authStatus: 'loading',

  isAuthenticated: () => !!get().accessToken,

  setTokenAndUser: (token, user) =>
    set({ accessToken: token, user, authStatus: 'authenticated' }),

  bootstrap: async () => {
    if (bootstrapped) return;
    bootstrapped = true;

    // Legacy cleanup: pre-H4 sessions persisted the refresh token in localStorage (F-04).
    localStorage.removeItem('gemek_refresh');

    try {
      // Cookie-implicit refresh: the httpOnly cookie carries the token; no body payload.
      // Use raw axios (bypasses apiClient interceptors) to avoid circular refresh loops.
      const base = (apiClient.defaults.baseURL ?? '/api') as string;
      const refreshRes = await axios.post(`${base}/auth/refresh`, {}, {
        withCredentials: true,
        headers: { 'X-Requested-With': 'XMLHttpRequest' },
      });
      const { accessToken } = refreshRes.data;
      // Set token before /auth/me so the request interceptor includes Authorization header.
      set({ accessToken });
      const meRes = await apiClient.get('/auth/me');
      set({ user: meRes.data, authStatus: 'authenticated' });
    } catch {
      // No cookie or expired/revoked token — land on login, no loop (interceptor skips /auth/refresh).
      set({ accessToken: null, authStatus: 'unauthenticated' });
    }
  },

  login: async (phone, password) => {
    const res = await apiClient.post('/auth/login', { phone, password });
    // Body still carries refreshToken until sprint close-out — deliberately ignored; cookie is the channel.
    const { accessToken, user } = res.data;
    set({ accessToken, user, authStatus: 'authenticated' });
  },

  logout: async () => {
    try { await apiClient.post('/auth/logout'); } catch { /* ignore */ }
    set({ accessToken: null, user: null, authStatus: 'unauthenticated' });
  },

  refreshToken: async () => {
    const res = await apiClient.post('/auth/refresh', {}, {
      headers: { 'X-Requested-With': 'XMLHttpRequest' },
    });
    set((state) => ({ ...state, accessToken: res.data.accessToken }));
  },
}));
