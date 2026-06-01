/* ── Auth Types ── */

/** Backend role values (English) – distinct from the French UserRole used on the frontend */
export type BackendRole = 'ADMIN' | 'SUPERVISOR' | 'OPERATOR';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  nom: string;
  prenom: string;
  telephone: string;
  role: BackendRole;
}

/** Raw response from Spring Boot /api/auth/login */
export interface AuthApiResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: {
    id: string;
    email: string;
    nom: string;
    prenom: string;
    role: BackendRole;
    agenceId: string | null;
  };
}

/** Flattened auth state stored in localStorage & used throughout the app */
export interface AuthUser {
  accessToken: string;
  refreshToken: string;
  userId: string;
  email: string;
  nom: string;
  prenom: string;
  role: BackendRole;
  agenceId: string | null;
}

/** Helper: flatten API response → app-level user */
export function flattenAuthResponse(r: AuthApiResponse): AuthUser {
  return {
    accessToken: r.accessToken,
    refreshToken: r.refreshToken,
    userId: r.user.id,
    email: r.user.email,
    nom: r.user.nom,
    prenom: r.user.prenom,
    role: r.user.role,
    agenceId: r.user.agenceId,
  };
}
