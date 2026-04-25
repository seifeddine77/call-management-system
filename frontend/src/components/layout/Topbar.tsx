import { useLocation } from 'react-router-dom';
import { useTheme } from '../../contexts/ThemeContext';
import { Search, Bell, Sun, Moon } from 'lucide-react';

const pageMeta: Record<string, { title: string; accent: string; sub: string }> = {
  '/': {
    title: 'Vue',
    accent: 'opérations',
    sub: 'Vendredi 13 mars 2026 · Agence Tunis-Centre · Shift 08:00–16:00',
  },
  '/queue': {
    title: "File",
    accent: "d'attente",
    sub: '7 tickets en attente · 4 opérateurs actifs',
  },
  '/rules': {
    title: 'Moteur de',
    accent: 'règles',
    sub: 'Configurations et logique métier',
  },
  '/analytics': {
    title: 'Tableau',
    accent: 'analytique',
    sub: 'Mars 2026 · Statistiques et tendances',
  },
  '/users': {
    title: 'Gestion',
    accent: 'utilisateurs',
    sub: 'Comptes, rôles et permissions',
  },
  '/agences': {
    title: 'Gestion',
    accent: 'agences',
    sub: 'Agences et affectations',
  },
  '/notifications': {
    title: 'Centre de',
    accent: 'notifications',
    sub: 'Logs et historique des envois',
  },
  '/settings': {
    title: '',
    accent: 'Paramètres',
    sub: 'Profil, notifications et intégrations',
  },
};

export default function Topbar() {
  const location = useLocation();
  const { theme, toggleTheme } = useTheme();

  // Match ticket detail route
  const isTicketDetail = location.pathname.startsWith('/tickets/') && location.pathname !== '/tickets/list';
  const meta = isTicketDetail
    ? { title: 'Ticket', accent: location.pathname.split('/').pop() || '', sub: 'Détail du ticket' }
    : pageMeta[location.pathname] || { title: 'Page', accent: '', sub: '' };

  return (
    <header className="topbar-line bg-[var(--surface)]/92 backdrop-blur-[20px] border-b border-border h-14 px-6 flex items-center gap-3 flex-shrink-0 relative z-10">
      {/* Title */}
      <div>
        <div className="text-[15px] font-extrabold tracking-tight">
          {meta.title}{' '}
          <span className="text-gradient">{meta.accent}</span>
        </div>
        <div className="font-mono text-[9px] text-txt-3 mt-px">{meta.sub}</div>
      </div>

      {/* Divider */}
      <div className="w-px h-[22px] bg-border-subtle" />

      {/* Segment tabs */}
      <div className="flex bg-[var(--elevated)] border border-border-subtle rounded-[9px] p-[3px] gap-0.5">
        {['Aperçu', 'Tickets', 'Analytique'].map((tab, i) => (
          <button
            key={tab}
            className={`text-[11px] font-medium px-3.5 py-[5px] rounded-[6px] transition-all duration-[130ms] cursor-pointer
              ${i === 0
                ? 'bg-[var(--surface)] text-txt shadow-[0_2px_8px_rgba(0,0,0,0.3)]'
                : 'text-txt-3 hover:text-txt-2'
              }`}
          >
            {tab}
          </button>
        ))}
      </div>

      {/* Spacer */}
      <div className="flex-1" />

      {/* Right side */}
      <div className="flex items-center gap-2">
        {/* Live indicator */}
        <div className="flex items-center gap-1.5 bg-success-soft border border-success/20 px-3 py-[5px] rounded-full">
          <div className="pulse-dot bg-success text-success" />
          <span className="font-mono text-[9.5px] text-success">LIVE · 14:32</span>
        </div>

        {/* Theme toggle */}
        <button
          onClick={toggleTheme}
          className="w-[34px] h-[34px] bg-[var(--elevated)] border border-border-subtle rounded-lg flex items-center justify-center cursor-pointer text-txt-2 hover:border-primary/30 hover:text-primary hover:bg-primary-soft/50 transition-all duration-[130ms]"
          title={theme === 'dark' ? 'Mode jour' : 'Mode nuit'}
        >
          {theme === 'dark' ? <Sun size={14} /> : <Moon size={14} />}
        </button>

        {/* Search */}
        <button className="w-[34px] h-[34px] bg-[var(--elevated)] border border-border-subtle rounded-lg flex items-center justify-center cursor-pointer text-txt-2 hover:border-primary/30 hover:text-primary hover:bg-primary-soft/50 transition-all duration-[130ms]">
          <Search size={14} />
        </button>

        {/* Notifications */}
        <button className="w-[34px] h-[34px] bg-[var(--elevated)] border border-border-subtle rounded-lg flex items-center justify-center cursor-pointer text-txt-2 hover:border-primary/30 hover:text-primary hover:bg-primary-soft/50 transition-all duration-[130ms] relative">
          <Bell size={14} />
          <span className="absolute -top-[3px] -right-[3px] w-[14px] h-[14px] bg-danger rounded-full font-mono text-[8px] flex items-center justify-center text-white border-2 border-[var(--bg)]">
            3
          </span>
        </button>
      </div>
    </header>
  );
}
