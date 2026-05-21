import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ThemeProvider } from './contexts/ThemeContext';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import AppShell from './components/AppShell';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import QueuePage from './pages/QueuePage';
import TicketListPage from './pages/TicketListPage';
import TicketDetailPage from './pages/TicketDetailPage';
import RulesPage from './pages/RulesPage';
import AnalyticsPage from './pages/AnalyticsPage';
import UsersPage from './pages/UsersPage';
import AgencesPage from './pages/AgencesPage';
import NotificationsPage from './pages/NotificationsPage';
import AuditPage from './pages/AuditPage';
import SettingsPage from './pages/SettingsPage';
import PublicFormPage from './pages/PublicFormPage';

function ProtectedRoute({ children }: Readonly<{ children: React.ReactNode }>) {
  const { isAuthenticated, isLoading } = useAuth();
  if (isLoading) return null;
  return isAuthenticated ? <>{children}</> : <Navigate to="/login" replace />;
}

/** Route guard by role — redirects to /dashboard if unauthorized */
function RoleGuard({ children, minRole }: Readonly<{ children: React.ReactNode; minRole: 'SUPERVISOR' | 'ADMIN' }>) {
  const { user } = useAuth();
  const role = user?.role;
  if (minRole === 'ADMIN' && role !== 'ADMIN') return <Navigate to="/dashboard" replace />;
  if (minRole === 'SUPERVISOR' && role !== 'SUPERVISOR' && role !== 'ADMIN') return <Navigate to="/dashboard" replace />;
  return <>{children}</>;
}

function AppRoutes() {
  const { isAuthenticated } = useAuth();

  return (
    <Routes>
      <Route path="/login" element={isAuthenticated ? <Navigate to="/dashboard" replace /> : <LoginPage />} />
      <Route path="/contact" element={<PublicFormPage />} />
      <Route
        element={
          <ProtectedRoute>
            <AppShell />
          </ProtectedRoute>
        }
      >
        {/* All roles */}
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/queue" element={<QueuePage />} />
        <Route path="/tickets" element={<TicketListPage />} />
        <Route path="/tickets/:id" element={<TicketDetailPage />} />
        <Route path="/settings" element={<SettingsPage />} />

        {/* SUPERVISOR + ADMIN only */}
        <Route path="/rules" element={<RoleGuard minRole="SUPERVISOR"><RulesPage /></RoleGuard>} />
        <Route path="/analytics" element={<RoleGuard minRole="SUPERVISOR"><AnalyticsPage /></RoleGuard>} />
        <Route path="/audit/:id" element={<RoleGuard minRole="SUPERVISOR"><AuditPage /></RoleGuard>} />

        {/* SUPERVISOR + ADMIN */}
        <Route path="/users" element={<RoleGuard minRole="SUPERVISOR"><UsersPage /></RoleGuard>} />
        <Route path="/agences" element={<RoleGuard minRole="SUPERVISOR"><AgencesPage /></RoleGuard>} />
        <Route path="/notifications" element={<RoleGuard minRole="ADMIN"><NotificationsPage /></RoleGuard>} />
      </Route>
      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  );
}

export default function App() {
  return (
    <ThemeProvider>
      <AuthProvider>
        <BrowserRouter>
          <AppRoutes />
        </BrowserRouter>
      </AuthProvider>
    </ThemeProvider>
  );
}
