import api from './api';
import type { NotificationLog } from '../types/notification';

export const notificationService = {
  getLogs: async (params?: { status?: string; page?: number; size?: number }): Promise<NotificationLog[]> => {
    const res = await api.get<NotificationLog[] | { content: NotificationLog[] }>('/api/notifications/logs', { params });
    // Handle both paginated and array responses
    const raw = Array.isArray(res.data) ? res.data : (res.data as { content: NotificationLog[] }).content;
    // Map API field names (English) → frontend type (French)
    return (raw as any[] || []).map((n: any) => ({
      id: (n.id as string) || '',
      type: (n.type as string) || '',
      destinataire: (n.destinataire as string) || (n.recipient as string) || '',
      sujet: (n.sujet as string) || (n.subject as string) || '',
      statut: ((n.statut as string) || (n.status as string) || 'FAILED') as NotificationLog['statut'],
      attempts: (n.attempts as number) || 0,
      errorMessage: (n.errorMessage as string) || null,
      metadata: (n.metadata as Record<string, unknown>) || {},
      createdAt: (n.createdAt as string) || '',
      sentAt: (n.sentAt as string) || null,
    }));
  },

  retry: async (id: string): Promise<void> => {
    await api.post(`/api/notifications/retry/${id}`);
  },
};
