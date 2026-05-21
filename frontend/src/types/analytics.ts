/* ── Analytics Types ── */
export interface KpiResponse {
  totalTickets: number;
  ticketsNouveau: number;
  ticketsEnCours: number;
  ticketsResolu: number;
  ticketsFerme: number;
  tauxResolution: number;
  tempsMoyenMinutes: number;
  decisionsAiFavorable: number;
  decisionsAiDefavorable: number;
  decisionsAiEtude: number;
  ticketsCall: number;
  ticketsEmail: number;
  ticketsWeb: number;
  period: string;
}

export interface KpiComparison {
  current: KpiResponse;
  previous: KpiResponse;
  deltaTickets: number;
  deltaResolution: number;
  deltaTempsMoyen: number;
  deltaDecisions: number;
  deltaFavorable: number;
}

export interface HourlyData {
  label?: string;
  hour?: number;
  count: number;
  date?: string;
}

export interface DailyStat {
  date: string;
  totalTickets: number;
  ticketsResolus: number;
  ticketsNouveau: number;
  ticketsEnCours: number;
  ticketsFerme: number;
  tempsMoyenMinutes: number | null;
  ticketsCall: number;
  ticketsEmail: number;
  ticketsWeb: number;
  decisionsFavorable: number;
  decisionsDefavorable: number;
  decisionsEtude: number;
}

/* ── Supervision ── */
export interface OperatorPerformance {
  operateurId: string;
  operateurName: string | null;
  ticketsTraites: number;
  ticketsEnCours: number;
  ticketsResolu: number;
  tauxResolution: number;
  tempsMoyenMinutes: number;
  ticketsCall: number;
  ticketsEmail: number;
  ticketsWeb: number;
  scoreComposite: number;
  slaCompliance: number;
}

export interface SupervisionAlert {
  type: 'TICKET_BLOQUE' | 'VOLUME_SPIKE' | 'NON_ASSIGNE' | 'OPERATEUR_SURCHARGE';
  severity: 'CRITIQUE' | 'WARNING' | 'INFO';
  message: string;
  count: number;
}

