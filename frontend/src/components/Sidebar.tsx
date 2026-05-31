/* eslint-disable react-refresh/only-export-components */
import { useState, useEffect, useMemo, createContext, useContext } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import { motion } from 'framer-motion';
import { useAuth } from '../contexts/AuthContext';
import Logo from './ui/Logo';
import {
  LayoutDashboard, Phone, Ticket, Settings, Users,
  Building2, Bell, BarChart3, Cpu, Search, ChevronsLeft, ChevronsRight, LogOut, X
} from 'lucide-react';

// ── Sidebar Context for mobile toggle ──
interface SidebarContextType {
  mobileOpen: boolean;
  setMobileOpen: (open: boolean) => void;
}
const SidebarContext = createContext<SidebarContextType>({ mobileOpen: false, setMobileOpen: () => {} });
export const useSidebar = () => useContext(SidebarContext);

export function SidebarProvider({ children }: Readonly<{ children: React.ReactNode }>) {
  const [mobileOpen, setMobileOpen] = useState(false);
  const value = useMemo(() => ({ mobileOpen, setMobileOpen }), [mobileOpen]);
  return <SidebarContext.Provider value={value}>{children}</SidebarContext.Provider>;
}

const navGroups = [
  {
    label: 'PRINCIPAL',
    items: [
      { path: '/dashboard', icon: LayoutDashboard, label: 'Tableau de bord' },
      { path: '/queue', icon: Phone, label: "File d'attente" },
      { path: '/tickets', icon: Ticket, label: 'Tickets' },
    ],
  },
  {
    label: 'IA & RÈGLES',
    items: [
      { path: '/rules', icon: Cpu, label: 'Moteur de règles' },
      { path: '/analytics', icon: BarChart3, label: 'Analytique' },
    ],
  },
  {
    label: 'ADMINISTRATION',
    items: [
      { path: '/users', icon: Users, label: 'Utilisateurs' },
      { path: '/agences', icon: Building2, label: 'Agences' },
      { path: '/notifications', icon: Bell, label: 'Notifications' },
      { path: '/settings', icon: Settings, label: 'Paramètres' },
    ],
  },
];

export default function Sidebar() {
  const { user, isAdmin, isSupervisor, logout } = useAuth();
  const [collapsed, setCollapsed] = useState(false);
  const { mobileOpen, setMobileOpen } = useSidebar();
  const location = useLocation();

  // Auto-close mobile sidebar on navigation
  useEffect(() => {
    setMobileOpen(false);
  }, [location.pathname, setMobileOpen]);

  // Close mobile sidebar on escape key
  useEffect(() => {
    const handleEsc = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setMobileOpen(false);
    };
    document.addEventListener('keydown', handleEsc);
    return () => document.removeEventListener('keydown', handleEsc);
  }, [setMobileOpen]);

  const sidebarContent = (
    <>
      {/* Brand */}
      <div className="px-3.5 py-4">
        <div className="flex items-center gap-2.5">
          <div className="w-9 h-9 rounded-xl flex items-center justify-center bg-[var(--elevated)] border border-[var(--border)] relative flex-shrink-0 overflow-hidden">
            <Logo size={28} animated={true} />
          </div>
          {!collapsed && (
            <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="flex-1 min-w-0">
              <div className="text-sm font-extrabold tracking-tight">AutoAssist</div>
              <div className="font-mono text-[8px] text-[var(--text-3)] tracking-widest uppercase">Aurora v3.0</div>
            </motion.div>
          )}
          {/* Mobile close button */}
          <button
            onClick={() => setMobileOpen(false)}
            className="md:hidden w-8 h-8 rounded-lg flex items-center justify-center text-[var(--text-3)] hover:text-[var(--text)] hover:bg-[var(--overlay)] transition-colors cursor-pointer ml-auto"
          >
            <X size={16} />
          </button>
        </div>
      </div>

      {/* Search trigger (opens ⌘K) */}
      {!collapsed && (
        <div className="px-3.5 mb-2">
          <button
            onClick={() => globalThis.dispatchEvent(new KeyboardEvent('keydown', { key: 'k', ctrlKey: true }))}
            className="w-full flex items-center gap-2 h-9 px-3 rounded-lg border border-[var(--border)] text-[var(--text-3)] cursor-pointer hover:border-[var(--border-visible)] hover:text-[var(--text-2)] transition-all"
          >
            <Search size={13} />
            <span className="text-xs flex-1 text-left">Rechercher…</span>
            <kbd className="font-mono text-[9px] px-1.5 py-0.5 rounded border border-[var(--border)] text-[var(--text-3)] hidden sm:inline">⌘K</kbd>
          </button>
        </div>
      )}

      {/* Navigation */}
      <nav className="flex-1 overflow-y-auto px-2 py-2 space-y-4">
        {navGroups.map(group => {
          if (group.label === 'ADMINISTRATION' && !isSupervisor) return null;
          if (group.label === 'IA & RÈGLES' && !isSupervisor) return null;

          // Filter items based on role
          const visibleItems = group.items.filter(item => {
            // Notifications → ADMIN only
            if (item.path === '/notifications' && !isAdmin) return false;
            return true;
          });
          if (visibleItems.length === 0) return null;

          return (
            <div key={group.label}>
              {!collapsed && (
                <div className="px-3 mb-1.5">
                  <span className="font-mono text-[8px] text-[var(--text-3)] tracking-[0.12em] uppercase">{group.label}</span>
                </div>
              )}
              <div className="space-y-0.5">
                {visibleItems.map(navItem => (
                  <NavLink
                    key={navItem.path}
                    to={navItem.path}
                    className={({ isActive }) => `nav-item ${isActive ? 'active' : ''} ${collapsed ? 'justify-center px-0' : ''}`}
                    title={collapsed ? navItem.label : undefined}
                  >
                    <navItem.icon size={16} />
                    {!collapsed && <span className="flex-1 text-[13px]">{navItem.label}</span>}
                  </NavLink>
                ))}
              </div>
            </div>
          );
        })}
      </nav>

      {/* Collapse toggle — hidden on mobile */}
      <div className="px-2 py-1 border-t border-[var(--border)] hidden md:block">
        <button
          onClick={() => setCollapsed(c => !c)}
          className="w-full flex items-center justify-center gap-2 h-8 rounded-lg hover:bg-[var(--overlay)] transition-colors cursor-pointer text-[var(--text-3)]"
          title={collapsed ? 'Étendre' : 'Réduire'}
        >
          {collapsed ? <ChevronsRight size={14} /> : <><ChevronsLeft size={14} />{<span className="text-[10px]">Réduire</span>}</>}
        </button>
      </div>

      {/* User footer */}
      <div className="p-2 border-t border-[var(--border)]">
        <div className="flex items-center gap-2.5 p-2 rounded-lg hover:bg-[var(--overlay)] transition-colors">
          <div className="w-8 h-8 rounded-lg flex items-center justify-center text-xs font-bold text-white flex-shrink-0" style={{ background: 'var(--gradient-cta)' }}>
            {((user?.prenom + ' ' + user?.nom) || 'A')[0].toUpperCase()}
          </div>
          {!collapsed && (
            <>
              <div className="flex-1 min-w-0">
                <div className="text-xs font-semibold truncate">{(user?.prenom + ' ' + user?.nom) || 'Utilisateur'}</div>
                <div className="font-mono text-[9px] text-[var(--text-3)] truncate">{user?.role || 'OPERATEUR'}</div>
              </div>
              <div className="pulse-dot bg-emerald-400 flex-shrink-0" />
            </>
          )}
        </div>
        {!collapsed && (
          <button onClick={logout} className="w-full flex items-center gap-2 px-3 h-7 rounded-lg text-xs text-[var(--text-3)] hover:text-[var(--danger)] hover:bg-[var(--overlay)] transition-colors cursor-pointer mt-1">
            <LogOut size={13} /> Déconnexion
          </button>
        )}
      </div>
    </>
  );

  return (
    <>
      {/* Mobile overlay backdrop */}
      <button
        className={`sidebar-overlay md:hidden ${mobileOpen ? 'active' : ''}`}
        aria-label="Fermer le menu"
        onClick={() => setMobileOpen(false)}
        onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); setMobileOpen(false); } }}
      />

      {/* Sidebar — responsive: overlay on mobile, static on desktop */}
      <motion.aside
        className={`flex-shrink-0 flex flex-col h-full sidebar-glass border-r border-[var(--border)] relative z-[2]
          sidebar-mobile ${mobileOpen ? 'open' : ''}
          md:relative md:translate-x-0 md:transition-none`}
        animate={{ width: collapsed ? 60 : 244 }}
        transition={{ duration: 0.2, ease: [0.22, 1, 0.36, 1] as const }}
        style={{ width: 244 }}
      >
        {sidebarContent}
      </motion.aside>
    </>
  );
}
