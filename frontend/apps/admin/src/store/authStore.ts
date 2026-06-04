import { create } from 'zustand';
import axios from 'axios';
import { apiClient } from '../api/client';

interface AuthUser {
  id: string;
  email: string;
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
  login: (email: string, password: string) => Promise<void>;
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

    const rt = localStorage.getItem('gemek_refresh');
    if (!rt) {
      set({ authStatus: 'unauthenticated' });
      return;
    }
    try {
      // Use raw axios (bypasses apiClient interceptors) to avoid circular refresh loops.
      const base = (apiClient.defaults.baseURL ?? '/api') as string;
      const refreshRes = await axios.post(`${base}/auth/refresh`, { refreshToken: rt });
      const { accessToken, refreshToken: newRt } = refreshRes.data;
      if (newRt) localStorage.setItem('gemek_refresh', newRt);
      // Set token before /auth/me so the request interceptor includes Authorization header.
      set({ accessToken });
      const meRes = await apiClient.get('/auth/me');
      set({ user: meRes.data, authStatus: 'authenticated' });
    } catch {
      localStorage.removeItem('gemek_refresh');
      set({ accessToken: null, authStatus: 'unauthenticated' });
    }
  },

  login: async (email: string, password: string) => {
    const res = await apiClient.post('/auth/login', { email, password });
    const { accessToken, refreshToken, user } = res.data;
    localStorage.setItem('gemek_refresh', refreshToken);
    set({ accessToken, user, authStatus: 'authenticated' });
  },

  logout: async () => {
    try {
      await apiClient.post('/auth/logout');
    } catch {
      // ignore errors on logout
    }
    localStorage.removeItem('gemek_refresh');
    set({ accessToken: null, user: null, authStatus: 'unauthenticated' });
  },

  refreshToken: async () => {
    const rt = localStorage.getItem('gemek_refresh');
    if (!rt) throw new Error('No refresh token');
    const res = await apiClient.post('/auth/refresh', { refreshToken: rt });
    set({ accessToken: res.data.accessToken });
  },
}));
