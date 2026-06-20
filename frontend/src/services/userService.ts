import api from './api';
import type { User, UserRequest, ChangePasswordRequest, UserRole } from '../types/user';

/** Maps French frontend roles to English backend roles */
const roleMapping: Record<string, string> = {
  OPERATEUR: 'OPERATOR',
  SUPERVISEUR: 'SUPERVISOR',
  ADMIN: 'ADMIN',
};

const toBackendRole = (role: string): string => roleMapping[role] || role;
const toFrontendRole = (role: string): string => {
  const reverse: Record<string, string> = { OPERATOR: 'OPERATEUR', SUPERVISOR: 'SUPERVISEUR', ADMIN: 'ADMIN' };
  return reverse[role] || role;
};

const mapUserResponse = (user: User): User => ({
  ...user,
  role: toFrontendRole(user.role) as UserRole,
});

export const userService = {
  getUsers: async (params?: { role?: string; agenceId?: string; page?: number; size?: number }): Promise<User[]> => {
    // La page Utilisateurs n'a pas de pagination : sans size, le backend renvoie 20 users sur 30+.
    // On force une grande page pour tout afficher (le rôle reste filtrable).
    const withSize = { size: 1000, ...params };
    const backendParams = withSize.role && withSize.role !== 'ALL'
      ? { ...withSize, role: toBackendRole(withSize.role) }
      : withSize;
    const res = await api.get<User[] | { content: User[] }>('/api/users', { params: backendParams });
    const data = res.data;
    const users = Array.isArray(data) ? data : data.content;
    return users.map(mapUserResponse);
  },

  getUserById: async (id: string): Promise<User> => {
    const res = await api.get<User>(`/api/users/${id}`);
    return mapUserResponse(res.data);
  },

  createUser: async (data: UserRequest): Promise<User> => {
    const payload = {
      ...data,
      role: toBackendRole(data.role),
      agenceId: data.agenceId && data.agenceId.length > 0 ? data.agenceId : null,
    };
    const res = await api.post<User>('/api/users', payload);
    return mapUserResponse(res.data);
  },

  updateUser: async (id: string, data: UserRequest): Promise<User> => {
    const payload = {
      ...data,
      role: toBackendRole(data.role),
      password: data.password && data.password.length > 0 ? data.password : null,
      agenceId: data.agenceId && data.agenceId.length > 0 ? data.agenceId : null,
    };
    const res = await api.put<User>(`/api/users/${id}`, payload);
    return mapUserResponse(res.data);
  },

  deleteUser: async (id: string): Promise<void> => {
    await api.delete(`/api/users/${id}`);
  },

  changePassword: async (id: string, data: ChangePasswordRequest): Promise<void> => {
    await api.patch(`/api/users/${id}/password`, data);
  },
};
