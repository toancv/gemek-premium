import { create } from 'zustand';
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
