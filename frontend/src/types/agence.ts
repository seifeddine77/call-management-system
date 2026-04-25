/* ── Agence Types ── */
export interface Agence {
  id: string;
  nom: string;
  adresse: string;
  telephone: string;
  email: string;
  responsableId: string | null;
  responsableName: string | null;
  stockDescription?: string;
  actif: boolean;
}

export interface AgenceRequest {
  nom: string;
  adresse: string;
  telephone: string;
  email: string;
  responsableId?: string;
  stockDescription?: string;
}
