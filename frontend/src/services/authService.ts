import api from './api';
import type { LoginRequest, AuthApiResponse, AuthUser } from '../types/auth';
import { flattenAuthResponse } from '../types/auth';

export const authService = {
  login: async (data: LoginRequest): Promise<AuthUser> => {
    const res = await api.post<AuthApiResponse>('/api/auth/login', data);
    return flattenAuthResponse(res.data);
  },

  register: async (data: { email: string; password: string; nom: string; prenom: string; telephone: string; role: string }): Promise<AuthUser> => {
    const roleMap: Record<string, string> = { OPERATEUR: 'OPERATOR', SUPERVISEUR: 'SUPERVISOR', ADMIN: 'ADMIN' };
    const payload = { ...data, role: roleMap[data.role] || data.role };
    const res = await api.post<AuthApiResponse>('/api/auth/register', payload);
    return flattenAuthResponse(res.data);
  },

  refresh: async (refreshToken: string): Promise<AuthUser> => {
    const res = await api.post<AuthApiResponse>('/api/auth/refresh', { refreshToken });
    return flattenAuthResponse(res.data);
  },

  logout: async (refreshToken: string): Promise<void> => {
    await api.post('/api/auth/logout', { refreshToken });
  },
};
