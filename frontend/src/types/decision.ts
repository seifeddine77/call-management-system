/* ── Decision Types ── */
export type DecisionType = 'FAVORABLE' | 'DEFAVORABLE' | 'ETUDE';
export type DecisionSource = 'RULES' | 'AI' | 'HYBRID';

export interface DecisionRequest {
  ticketId: string;
  typePiece?: string;
  ageVehiculeAns?: number;
  kilometrage?: number;
  categorieClient?: string;
  typeSinistre?: string;
  pieceReference?: string;
  transcriptionText?: string;
}

export interface DecisionResult {
  decisionId: string;
  ticketId: string;
  decision: DecisionType;
  score: number;
  motif: string;
  source: DecisionSource;
  rulesApplied: string[];
  ambiguous: boolean;
  cached: boolean;
  finalDecision: DecisionType | null;
  overrideMotif: string | null;
}

export interface OverrideRequest {
  finalDecision: DecisionType;
  motif: string;
  operateurId: string;
  operateurName: string;
  clientNom?: string;
  clientEmail?: string;
}
