import { NavLink, useLocation } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import {
  LayoutDashboard, ListOrdered, Ticket, Brain, BarChart3,
  Users, Building2, Bell, Settings, LogOut, Search,
} from 'lucide-react';

const navGroups = [
  {
    label: 'Principal',
    items: [
      { to: '/', icon: LayoutDashboard, label: 'Tableau de bord' },
      { to: '/queue', icon: ListOrdered, label: "File d'attente", badge: '7', badgeColor: 'bg-danger-soft text-danger border border-danger/20' },
      { to: '/tickets/list', icon: Ticket, label: 'Mes tickets', badge: '12', badgeColor: 'bg-primary-soft text-primary border border-primary/20' },
    ],
  },
  {
    label: 'Intelligence IA',
    items: [
      { to: '/rules', icon: Brain, label: 'Moteur de règles', badge: 'v3', badgeColor: 'bg-[var(--muted)] text-txt-3 border border-border-subtle' },
      { to: '/analytics', icon: BarChart3, label: 'Analytique' },
    ],
  },
  {
    label: 'Administration',
    items: [
      { to: '/users', icon: Users, label: 'Utilisateurs' },
      { to: '/agences', icon: Building2, label: 'Agences' },
      { to: '/notifications', icon: Bell, label: 'Notifications' },
      { to: '/settings', icon: Settings, label: 'Paramètres' },
    ],
  },
];

export default function Sidebar() {
  const { user, logout } = useAuth();
  const location = useLocation();

  const initials = (user?.prenom + ' ' + user?.nom)
    ?.split(' ')
    .map((n) => n[0])
    .join('')
    .slice(0, 2)
    .toUpperCase() || 'U';

  return (
    <aside className="w-[244px] flex-shrink-0 bg-[var(--surface)]/97 border-r border-border flex flex-col relative z-20"
      style={{ backdropFilter: 'blur(12px)' }}
    >
      {/* Gradient accent line on right edge */}
      <div className="absolute right-0 top-0 bottom-0 w-px"
        style={{ background: 'linear-gradient(180deg, transparent 0%, var(--primary) 50%, transparent 100%)', opacity: 0.3 }}
      />

      {/* Brand */}
      <div className="px-4 pt-[18px] pb-[14px] border-b border-border flex items-center gap-3">
        <div className="w-9 h-9 rounded-[10px] flex items-center justify-center relative flex-shrink-0"
          style={{ background: 'var(--gradient-cta)' }}
        >
          <div className="absolute inset-[2px] rounded-[8px] bg-[var(--surface)]" />
          <div className="relative z-[1] w-4 h-4 rounded-[4px]" style={{ background: 'var(--gradient-cta)' }} />
        </div>
        <div>
          <div className="text-sm font-extrabold tracking-tight">AutoAssist</div>
          <div className="font-mono text-[8px] text-txt-3">Centre d'appels IA</div>
        </div>
        <span className="ml-auto font-mono text-[8px] text-success bg-success-soft border border-success/20 px-1.5 py-0.5 rounded">
          PROD
        </span>
      </div>

      {/* Search */}
      <div className="px-3 pt-2.5 pb-1">
        <div className="flex items-center gap-2 bg-[var(--elevated)] border border-border-subtle rounded-[9px] px-3 py-[7px] cursor-text hover:border-border-visible transition-colors">
          <Search size={12} className="text-txt-3 flex-shrink-0" />
          <span className="text-xs text-txt-3 flex-1">Rechercher…</span>
          <kbd className="font-mono text-[8px] text-txt-3 bg-[var(--overlay)] border border-border px-1.5 py-0.5 rounded">⌘K</kbd>
        </div>
      </div>

      {/* Nav */}
      <nav className="flex-1 overflow-y-auto px-2.5 py-2 scrollbar-hide">
        {navGroups.map((group) => (
          <div key={group.label} className="mb-4">
            <div className="font-mono text-[8px] text-txt-3 uppercase tracking-[0.15em] px-1.5 pb-2 pt-1">
              {group.label}
            </div>
            {group.items.map((item) => {
              const isActive = item.to === '/'
                ? location.pathname === '/'
                : location.pathname.startsWith(item.to);

              return (
                <NavLink
                  key={item.to}
                  to={item.to}
                  className={`relative flex items-center gap-2.5 px-2 py-[7px] rounded-[9px] text-[12.5px] font-medium mb-0.5 transition-all duration-[130ms] select-none group
                    ${isActive
                      ? 'nav-item-active text-primary'
                      : 'text-txt-2 hover:bg-[var(--overlay)] hover:text-txt'
                    }`}
                >
                  <div className={`w-[30px] h-[30px] rounded-lg flex items-center justify-center flex-shrink-0 transition-all duration-[130ms]
                    ${isActive
                      ? 'bg-primary-soft text-primary'
                      : 'bg-[var(--overlay)] text-txt-2 group-hover:bg-[var(--muted)]'
                    }`}
                  >
                    <item.icon size={14} />
                  </div>
                  <span className="flex-1">{item.label}</span>
                  {item.badge && (
                    <span className={`font-mono text-[9px] px-[7px] py-0.5 rounded-full ${item.badgeColor}`}>
                      {item.badge}
                    </span>
                  )}
                </NavLink>
              );
            })}
          </div>
        ))}
      </nav>

      {/* User footer */}
      <div className="px-3 py-2.5 border-t border-border flex items-center gap-2.5">
        <div className="w-8 h-8 rounded-full flex items-center justify-center text-[11px] font-bold relative flex-shrink-0"
          style={{ background: 'var(--gradient-cta)' }}
        >
          {initials}
          <div className="absolute bottom-0 right-0 w-2 h-2 rounded-full bg-success border-2 border-[var(--surface)]" />
        </div>
        <div className="min-w-0 flex-1">
          <div className="text-[12.5px] font-semibold truncate">{(user?.prenom + ' ' + user?.nom) || 'Utilisateur'}</div>
          <div className="font-mono text-[8.5px] text-txt-3 uppercase">{user?.role || 'OPERATOR'}</div>
        </div>
        <button
          onClick={logout}
          className="text-txt-3 hover:text-danger transition-colors p-1"
          title="Se déconnecter"
        >
          <LogOut size={14} />
        </button>
      </div>
    </aside>
  );
}
