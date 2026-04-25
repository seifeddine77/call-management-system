import api from './api';
import type { Agence, AgenceRequest } from '../types/agence';

export const agenceService = {
  getAgences: async (params?: { page?: number; size?: number }) => {
    const res = await api.get<{ content: Agence[]; totalElements: number }>('/api/agences', { params });
    return res.data;
  },

  getAgenceById: async (id: string): Promise<Agence> => {
    const res = await api.get<Agence>(`/api/agences/${id}`);
    return res.data;
  },

  createAgence: async (data: AgenceRequest): Promise<Agence> => {
    const res = await api.post<Agence>('/api/agences', data);
    return res.data;
  },

  updateAgence: async (id: string, data: AgenceRequest): Promise<Agence> => {
    const res = await api.put<Agence>(`/api/agences/${id}`, data);
    return res.data;
  },

  deleteAgence: async (id: string): Promise<void> => {
    await api.delete(`/api/agences/${id}`);
  },

  assignResponsable: async (id: string, userId: string): Promise<Agence> => {
    const res = await api.patch<Agence>(`/api/agences/${id}/responsable`, { userId });
    return res.data;
  },
};
