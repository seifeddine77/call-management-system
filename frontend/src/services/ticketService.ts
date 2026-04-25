import api from './api';
import type { Ticket, CreateTicketRequest, TicketFilter, TicketStats, QueueResponse, TranscriptionData } from '../types/ticket';

export const ticketService = {
  getTickets: async (filters?: TicketFilter) => {
    const res = await api.get<{ content: Ticket[]; totalElements: number; totalPages: number }>('/api/tickets', { params: filters });
    return res.data;
  },

  getTicketById: async (id: string): Promise<Ticket> => {
    const res = await api.get<Ticket>(`/api/tickets/${id}`);
    return res.data;
  },

  createTicket: async (data: CreateTicketRequest): Promise<Ticket> => {
    const res = await api.post<Ticket>('/api/tickets', data);
    return res.data;
  },

  updateStatut: async (id: string, statut: string): Promise<Ticket> => {
    const res = await api.patch<Ticket>(`/api/tickets/${id}/statut`, { newStatut: statut });
    return res.data;
  },

  assignTicket: async (id: string, operateurId: string): Promise<Ticket> => {
    const res = await api.patch<Ticket>(`/api/tickets/${id}/assign`, { operateurId });
    return res.data;
  },

  getStats: async (): Promise<TicketStats> => {
    const res = await api.get<TicketStats>('/api/tickets/stats');
    return res.data;
  },

  uploadAudio: async (id: string, file: File): Promise<Ticket> => {
    const formData = new FormData();
    formData.append('file', file);
    const res = await api.post<Ticket>(`/api/tickets/${id}/audio`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return res.data;
  },

  getTranscription: async (id: string): Promise<TranscriptionData> => {
    const res = await api.get<TranscriptionData>(`/api/tickets/${id}/transcription`);
    return res.data;
  },

  getQueue: async (): Promise<QueueResponse> => {
    const res = await api.get<QueueResponse>('/api/tickets/queue');
    return res.data;
  },

  anonymize: async (id: string): Promise<Ticket> => {
    const res = await api.post<Ticket>(`/api/tickets/${id}/anonymize`);
    return res.data;
  },

  uploadPhotos: async (id: string, files: File[]): Promise<Ticket> => {
    const formData = new FormData();
    files.forEach(file => formData.append('files', file));
    const res = await api.post<Ticket>(`/api/tickets/${id}/photos`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return res.data;
  },
};
