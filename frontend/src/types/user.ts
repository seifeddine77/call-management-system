/* ── User Types ── */
export type UserRole = 'ADMIN' | 'SUPERVISEUR' | 'OPERATEUR';

export interface User {
  id: string;
  email: string;
  nom: string;
  prenom: string;
  telephone: string;
  role: UserRole;
  agenceId: string | null;
  agenceName: string | null;
  actif: boolean;
  planning: string | null;
  createdAt: string | null;
  lastLogin: string | null;
}

export interface UserRequest {
  email: string;
  password: string;
  nom: string;
  prenom: string;
  telephone: string;
  role: UserRole;
  agenceId?: string;
  planning?: string;
}

export interface ChangePasswordRequest {
  oldPassword: string;
  newPassword: string;
}
