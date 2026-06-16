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

// Roles this portal serves. A shared host-scoped refresh cookie can restore a session belonging to
// the wrong portal (e.g. a RESIDENT on the admin host), so the role must be validated client-side.
const ALLOWED_ROLES = ['ADMIN', 'BOARD_MEMBER'];

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
      // Role-gate the restored session. On mismatch, drop LOCAL state only — do NOT call /auth/logout:
      // that revokes the user's refresh tokens and would kill their legitimate session in the other portal/tab.
      if (!ALLOWED_ROLES.includes(meRes.data?.role)) {
        set({ accessToken: null, user: null, authStatus: 'unauthenticated' });
        return;
      }
      set({ user: meRes.data, authStatus: 'authenticated' });
    } catch {
      // No cookie or expired/revoked token — land on login, no loop (interceptor skips /auth/refresh).
      set({ accessToken: null, authStatus: 'unauthenticated' });
    }
  },

  login: async (phone: string, password: string) => {
    const res = await apiClient.post('/auth/login', { phone, password });
    // Refresh token is cookie-only (httpOnly) — the login body carries no refreshToken.
    const { accessToken, user } = res.data;
    // Role-gate the login. On mismatch, drop LOCAL state only (no /auth/logout — see bootstrap) and
    // surface a Vietnamese message via the WRONG_PORTAL error code the LoginPage already maps.
    if (!user || !ALLOWED_ROLES.includes(user.role)) {
      set({ accessToken: null, user: null, authStatus: 'unauthenticated' });
      const err: any = new Error('WRONG_PORTAL');
      err.response = { data: { error: 'WRONG_PORTAL' } };
      throw err;
    }
    set({ accessToken, user, authStatus: 'authenticated' });
  },

  logout: async () => {
    try {
      await apiClient.post('/auth/logout');
    } catch {
      // ignore errors on logout
    }
    set({ accessToken: null, user: null, authStatus: 'unauthenticated' });
  },

  refreshToken: async () => {
    const res = await apiClient.post('/auth/refresh', {}, {
      headers: { 'X-Requested-With': 'XMLHttpRequest' },
    });
    set({ accessToken: res.data.accessToken });
  },
}));
