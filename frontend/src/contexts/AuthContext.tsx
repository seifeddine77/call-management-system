import { createContext, useContext, useState, useEffect, useCallback, useMemo, type ReactNode } from 'react';
import type { AuthUser } from '../types/auth';
import { authService } from '../services/authService';

interface AuthContextType {
  user: AuthUser | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
  updateUserSession: (userData: Partial<AuthUser>) => void;
  isAdmin: boolean;
  isSupervisor: boolean;
  isOperator: boolean;
}

const AuthContext = createContext<AuthContextType | null>(null);

export function AuthProvider({ children }: Readonly<{ children: ReactNode }>) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const stored = localStorage.getItem('aurora-auth');
    if (stored) {
      try {
        const parsed = JSON.parse(stored) as AuthUser;
        setUser(parsed);
      } catch {
        localStorage.removeItem('aurora-auth');
      }
    }
    setIsLoading(false);
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const flatUser = await authService.login({ email, password });
    setUser(flatUser);
    localStorage.setItem('aurora-auth', JSON.stringify(flatUser));
  }, []);

  const logout = useCallback(() => {
    if (user?.refreshToken) {
      authService.logout(user.refreshToken).catch(() => {});
    }
    setUser(null);
    localStorage.removeItem('aurora-auth');
  }, [user]);

  const updateUserSession = useCallback((userData: Partial<AuthUser>) => {
    setUser(prev => {
      if (!prev) return null;
      const updated = { ...prev, ...userData };
      localStorage.setItem('aurora-auth', JSON.stringify(updated));
      return updated;
    });
  }, []);

  const role = user?.role;

  const value = useMemo(() => ({
    user,
    isAuthenticated: !!user,
    isLoading,
    login,
    logout,
    updateUserSession,
    isAdmin: role === 'ADMIN',
    isSupervisor: role === 'SUPERVISOR' || role === 'ADMIN',
    isOperator: role === 'OPERATOR',
  }), [user, isLoading, login, logout, updateUserSession, role]);

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
