/* ── Notification Types ── */
export type NotificationStatus = 'PENDING' | 'SENT' | 'FAILED';

export interface NotificationLog {
  id: string;
  type: string;
  destinataire: string;
  sujet: string;
  statut: NotificationStatus;
  attempts: number;
  errorMessage: string | null;
  metadata: Record<string, unknown>;
  createdAt: string;
  sentAt: string | null;
}
