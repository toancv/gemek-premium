import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
// SECURITY-FIX: Import store directly instead of reading from window.__gemekAuthState global.
import { useAuthStore } from '../store/authStore';

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api';

export const apiClient = axios.create({
  baseURL: BASE_URL,
  paramsSerializer: { indexes: null },
});

apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  // SECURITY-FIX: Read token directly from Zustand store state — no window global needed.
  const token = useAuthStore.getState().accessToken;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
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
        // SECURITY-FIX: Call store setter directly instead of window.__gemekSetToken global.
        useAuthStore.getState().setTokenAndUser(newToken, useAuthStore.getState().user!);
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
