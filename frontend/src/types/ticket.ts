/* ── Ticket Types ── */
export type Canal = 'CALL' | 'EMAIL' | 'WEB';
export type Statut = 'NOUVEAU' | 'EN_COURS' | 'RESOLU' | 'FERME';
export type Priorite = 1 | 2 | 3;

export interface Ticket {
  id: string;
  numero: string;
  clientNom: string;
  clientTel: string;
  clientEmail: string;
  canal: Canal;
  statut: Statut;
  priorite: Priorite;
  operateurId: string | null;
  operateurName: string | null;
  agenceId: string;
  agenceName: string | null;
  description: string;
  audioPath: string | null;
  transcription: string | null;
  transcriptionKeywords: string | null;
  photosPaths: string | null;
  consentementEnregistrement: boolean;
  createdAt: string;
  updatedAt: string;
  actions: TicketAction[];
}

export interface TicketAction {
  id: string;
  action: string;
  oldValue: string | null;
  newValue: string | null;
  userName: string;
  notes: string | null;
  createdAt: string;
}

export interface CreateTicketRequest {
  clientNom: string;
  clientTel?: string;
  clientEmail?: string;
  canal: Canal;
  description: string;
  agenceId?: string;
  priorite?: Priorite;
}

export interface TicketFilter {
  statut?: Statut;
  canal?: Canal;
  priorite?: Priorite;
  operateurId?: string;
  agenceId?: string;
  search?: string;
  dateFrom?: string;
  dateTo?: string;
  page?: number;
  size?: number;
}

export interface TicketStats {
  total: number;
  nouveau: number;
  enCours: number;
  resolu: number;
  ferme: number;
}

export interface QueueEntry {
  position: number;
  ticketId: string;
  ticketNumero: string;
  clientNom: string;
  canal: Canal;
  priorite: Priorite;
  createdAt: string;
}

export interface QueueResponse {
  totalEnAttente: number;
  tickets: QueueEntry[];
}

export interface TranscriptionData {
  audioPath: string;
  transcription: string;
  keywords: string;
  hasAudio: boolean;
  hasTranscription: boolean;
}
