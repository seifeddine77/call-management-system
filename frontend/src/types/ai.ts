/* ── AI Types (Python schemas) ── */
export interface TranscribeResponse {
  ticket_id: string;
  text: string;
  confidence: number;
  duration_seconds: number;
  segments: AudioSegment[];
}

export interface AudioSegment {
  start: number;
  end: number;
  text: string;
}

export interface AnalysisResponse {
  ticket_id: string;
  piece_reference: string | null;
  type_sinistre: string;
  sentiment: 'POSITIF' | 'NEGATIF' | 'NEUTRE';
  keywords: string[];
  confidence: number;
}

export interface AnalysisRequest {
  ticket_id: string;
  text: string;
}
