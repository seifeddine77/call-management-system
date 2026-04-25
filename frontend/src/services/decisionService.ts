import api from './api';
import type { DecisionRequest, DecisionResult, OverrideRequest } from '../types/decision';

export const decisionService = {
  analyze: async (data: DecisionRequest): Promise<DecisionResult> => {
    const res = await api.post<DecisionResult>('/api/decisions/analyze', data);
    return res.data;
  },

  accept: async (id: string, data: OverrideRequest): Promise<DecisionResult> => {
    const res = await api.post<DecisionResult>(`/api/decisions/${id}/accept`, data);
    return res.data;
  },

  reject: async (id: string, data: OverrideRequest): Promise<DecisionResult> => {
    const res = await api.post<DecisionResult>(`/api/decisions/${id}/reject`, data);
    return res.data;
  },

  getByTicket: async (ticketId: string): Promise<DecisionResult> => {
    const res = await api.get<DecisionResult>(`/api/decisions/ticket/${ticketId}`);
    return res.data;
  },

  downloadLetter: async (id: string): Promise<Blob> => {
    const res = await api.get(`/api/decisions/${id}/letter`, { responseType: 'blob' });
    return res.data;
  },

  deleteByTicket: async (ticketId: string): Promise<void> => {
    await api.delete(`/api/decisions/ticket/${ticketId}`);
  },
};
