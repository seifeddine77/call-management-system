import { useLocation } from 'react-router-dom';
import { useTheme } from '../contexts/ThemeContext';
import { useAuth } from '../contexts/AuthContext';
import { useSidebar } from './Sidebar';
import { Menu, Sun, Moon } from 'lucide-react';

const titles: Record<string, string> = {
  '/dashboard': 'Tableau de bord',
  '/queue': "File d'attente",
  '/tickets': 'Tickets',
  '/rules': 'Moteur de règles',
  '/analytics': 'Analytique',
  '/users': 'Utilisateurs',
  '/agences': 'Agences',
  '/notifications': 'Notifications',
  '/settings': 'Paramètres',
};

export default function Topbar() {
  const { pathname } = useLocation();
  const { theme, toggleTheme } = useTheme();
  const { user } = useAuth();
  const { setMobileOpen } = useSidebar();

  const basePath = '/' + (pathname.split('/').find(Boolean) || '');
  const title = titles[basePath] || 'AutoAssist';

  const now = new Date();
  const timeStr = now.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
  const dateStr = now.toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' });

  return (
    <header className="h-[52px] flex-shrink-0 flex items-center justify-between px-3 md:px-5 border-b border-[var(--border)] topbar-glass relative z-10">
      {/* Gradient line (AutoAssist) */}
      <div className="absolute top-0 left-0 right-0 h-[1.5px]" style={{ background: 'var(--gradient-ai)', opacity: 0.5 }} />

      <div className="flex items-center gap-2 md:gap-3">
        {/* Mobile hamburger button */}
        <button
          onClick={() => setMobileOpen(true)}
          className="md:hidden w-9 h-9 rounded-xl flex items-center justify-center border border-[var(--border)] cursor-pointer hover:bg-[var(--overlay)] transition-all"
          aria-label="Ouvrir le menu"
        >
          <Menu size={16} />
        </button>

        <h2 className="text-sm font-bold tracking-tight">{title}</h2>
        {pathname.startsWith('/tickets/') && pathname.length > 9 && (
          <span className="font-mono text-[10px] text-[var(--text-3)] bg-[var(--overlay)] px-2.5 py-1 rounded-md border border-[var(--border-subtle)] hidden sm:inline">
            Détail
          </span>
        )}
      </div>

      <div className="flex items-center gap-2 md:gap-3">
        {/* Live indicator — hidden on very small screens */}
        <div className="hidden sm:flex items-center gap-1.5 px-2.5 py-1 rounded-lg border border-[var(--border)] text-emerald-400">
          <div className="pulse-dot bg-emerald-400" />
          <span className="font-mono text-[9px] tracking-wider">LIVE</span>
        </div>

        {/* Date & time */}
        <div className="text-right hidden md:block">
          <div className="font-mono text-[9px] text-[var(--text-3)]">{dateStr}</div>
          <div className="font-mono text-[10px] text-[var(--text-2)]">{timeStr}</div>
        </div>

        {/* Theme toggle */}
        <button
          onClick={toggleTheme}
          className="w-9 h-9 rounded-xl flex items-center justify-center border border-[var(--border)] text-sm cursor-pointer hover:border-[var(--border-subtle)] hover:bg-[var(--overlay)] transition-all"
          title={theme === 'dark' ? 'Mode jour' : 'Mode nuit'}
        >
          {theme === 'dark' ? <Sun size={16} /> : <Moon size={16} />}
        </button>

        {/* User */}
        <div className="flex items-center gap-2 pl-2 md:pl-3 border-l border-[var(--border)]">
          <div className="w-7 h-7 rounded-lg flex items-center justify-center text-[10px] font-bold text-white flex-shrink-0" style={{ background: 'var(--gradient-cta)' }}>
            {((user?.prenom + ' ' + user?.nom) || 'A')[0].toUpperCase()}
          </div>
          <span className="text-xs font-medium hidden lg:block">{(user?.prenom + ' ' + user?.nom) || 'Utilisateur'}</span>
        </div>
      </div>
    </header>
  );
}
