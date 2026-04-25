import axios from 'axios';
import { flattenAuthResponse } from '../types/auth';
import type { AuthApiResponse } from '../types/auth';

// In dev mode, Vite proxy forwards /api/* to localhost:8080 (no CORS)
// In production, set VITE_API_URL to the real gateway URL
const GATEWAY_URL = import.meta.env.VITE_API_URL || '';

const api = axios.create({
  baseURL: GATEWAY_URL,
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
});

// Request interceptor — attach JWT
api.interceptors.request.use((config) => {
  const stored = localStorage.getItem('aurora-auth');
  if (stored) {
    try {
      const { accessToken } = JSON.parse(stored);
      if (accessToken) {
        config.headers.Authorization = `Bearer ${accessToken}`;
      }
    } catch { /* ignore */ }
  }
  return config;
});

// Response interceptor — handle 401 + refresh
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;

      try {
        const stored = localStorage.getItem('aurora-auth');
        if (stored) {
          const { refreshToken } = JSON.parse(stored);
          const { data } = await axios.post<AuthApiResponse>(`${GATEWAY_URL}/api/auth/refresh`, { refreshToken });
          const flatUser = flattenAuthResponse(data);
          localStorage.setItem('aurora-auth', JSON.stringify(flatUser));
          originalRequest.headers.Authorization = `Bearer ${flatUser.accessToken}`;
          return api(originalRequest);
        }
      } catch {
        localStorage.removeItem('aurora-auth');
        window.location.href = '/login';
      }
    }

    return Promise.reject(error);
  }
);

export default api;

