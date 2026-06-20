import api from './api';
import type { NotificationLog } from '../types/notification';

interface RawNotification {
  id?: string;
  type?: string;
  destinataire?: string;
  recipient?: string;
  sujet?: string;
  subject?: string;
  statut?: string;
  status?: string;
  attempts?: number;
  errorMessage?: string;
  metadata?: Record<string, unknown>;
  createdAt?: string;
  sentAt?: string;
}

export const notificationService = {
  getLogs: async (params?: { status?: string; page?: number; size?: number }): Promise<NotificationLog[]> => {
    // La page Notifications n'a pas de pagination : sans size, le backend renvoie 20 logs sur 721+.
    // On demande une grande page pour tout afficher.
    const merged = { size: 1000, ...params };
    const res = await api.get<NotificationLog[] | { content: NotificationLog[] }>('/api/notifications/logs', { params: merged });
    // Handle both paginated and array responses
    const data = res.data;
    const raw = Array.isArray(data) ? data : data.content;
    // Map API field names (English) → frontend type (French)
    return ((raw as RawNotification[]) || []).map((n) => ({
      id: n.id || '',
      type: n.type || '',
      destinataire: n.destinataire || n.recipient || '',
      sujet: n.sujet || n.subject || '',
      statut: (n.statut || n.status || 'FAILED') as NotificationLog['statut'],
      attempts: n.attempts || 0,
      errorMessage: n.errorMessage || null,
      metadata: n.metadata || {},
      createdAt: n.createdAt || '',
      sentAt: n.sentAt || null,
    }));
  },

  retry: async (id: string): Promise<void> => {
    await api.post(`/api/notifications/retry/${id}`);
  },
};
