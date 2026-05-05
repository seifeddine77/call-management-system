import { useState, useEffect, useRef, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { useNavigate } from 'react-router-dom';
import {
  Search, LayoutDashboard, Phone, Ticket, Cpu,
  BarChart3, Users, Building2, Bell, Settings, ArrowRight
} from 'lucide-react';

const pages = [
  { path: '/dashboard', label: 'Tableau de bord', icon: LayoutDashboard, keywords: 'home accueil kpi' },
  { path: '/queue', label: "File d'attente", icon: Phone, keywords: 'queue appels calls' },
  { path: '/tickets', label: 'Tickets', icon: Ticket, keywords: 'ticket réclamation' },
  { path: '/rules', label: 'Moteur de règles', icon: Cpu, keywords: 'rules ia decision' },
  { path: '/analytics', label: 'Analytique', icon: BarChart3, keywords: 'stats statistiques charts' },
  { path: '/users', label: 'Utilisateurs', icon: Users, keywords: 'users agents operateurs' },
  { path: '/agences', label: 'Agences', icon: Building2, keywords: 'agences bureaux' },
  { path: '/notifications', label: 'Notifications', icon: Bell, keywords: 'notifications logs' },
  { path: '/settings', label: 'Paramètres', icon: Settings, keywords: 'settings profil password' },
];

export default function CommandPalette() {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const inputRef = useRef<HTMLInputElement>(null);
  const navigate = useNavigate();

  const filtered = pages.filter(p =>
    (p.label + ' ' + p.keywords).toLowerCase().includes(query.toLowerCase())
  );

  const handleSelect = useCallback((path: string) => {
    navigate(path);
    setOpen(false);
    setQuery('');
  }, [navigate]);

  // ⌘K / Ctrl+K shortcut
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault();
        setOpen(prev => !prev);
      }
      if (e.key === 'Escape') setOpen(false);
    };
    globalThis.addEventListener('keydown', handler);
    return () => globalThis.removeEventListener('keydown', handler);
  }, []);

  // Focus input when opened
  useEffect(() => {
    if (open) setTimeout(() => inputRef.current?.focus(), 50);
  }, [open]);

  return (
    <AnimatePresence>
      {open && (
        <>
          {/* Backdrop */}
          <motion.div
            className="fixed inset-0 z-50 bg-black/40 backdrop-blur-sm"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={() => setOpen(false)}
          />
          {/* Palette */}
          <motion.div
            className="fixed z-50 left-1/2 top-[18%] w-full max-w-[520px]"
            initial={{ opacity: 0, y: -12, x: '-50%', scale: 0.97 }}
            animate={{ opacity: 1, y: 0, x: '-50%', scale: 1 }}
            exit={{ opacity: 0, y: -8, x: '-50%', scale: 0.97 }}
            transition={{ duration: 0.15, ease: [0.22, 1, 0.36, 1] }}
          >
            <div className="glass rounded-2xl overflow-hidden" style={{ background: 'var(--elevated)', border: '1px solid var(--border-visible)' }}>
              {/* Search input */}
              <div className="flex items-center gap-3 px-4 h-12 border-b border-[var(--border)]">
                <Search size={15} className="text-[var(--text-3)] flex-shrink-0" />
                <input
                  ref={inputRef}
                  type="text"
                  placeholder="Rechercher une page, un ticket, un utilisateur…"
                  value={query}
                  onChange={e => setQuery(e.target.value)}
                  className="flex-1 bg-transparent outline-none text-sm text-[var(--text)] placeholder:text-[var(--text-3)]"
                  onKeyDown={e => {
                    if (e.key === 'Enter' && filtered.length > 0) handleSelect(filtered[0].path);
                  }}
                />
                <kbd className="font-mono text-[9px] px-1.5 py-0.5 rounded border border-[var(--border)] text-[var(--text-3)]">ESC</kbd>
              </div>

              {/* Results */}
              <div className="max-h-[320px] overflow-y-auto py-2">
                {filtered.length === 0 ? (
                  <div className="px-4 py-6 text-center text-xs text-[var(--text-3)]">
                    Aucun résultat pour "{query}"
                  </div>
                ) : (
                  <>
                    <div className="px-4 py-1">
                      <span className="font-mono text-[8px] text-[var(--text-3)] tracking-widest uppercase">Pages</span>
                    </div>
                    {filtered.map((page, i) => (
                      <button
                        key={page.path}
                        onClick={() => handleSelect(page.path)}
                        className={`w-full flex items-center gap-3 px-4 h-10 text-left hover:bg-[var(--overlay)] transition-colors cursor-pointer ${i === 0 && query ? 'bg-[var(--overlay)]' : ''}`}
                      >
                        <page.icon size={15} className="text-[var(--text-3)]" />
                        <span className="flex-1 text-sm">{page.label}</span>
                        <ArrowRight size={12} className="text-[var(--text-3)] opacity-0 group-hover:opacity-100" />
                      </button>
                    ))}
                  </>
                )}
              </div>

              {/* Footer */}
              <div className="px-4 py-2 border-t border-[var(--border)] flex items-center gap-4">
                <span className="font-mono text-[8px] text-[var(--text-3)]">↑↓ naviguer</span>
                <span className="font-mono text-[8px] text-[var(--text-3)]">↵ ouvrir</span>
                <span className="font-mono text-[8px] text-[var(--text-3)]">esc fermer</span>
              </div>
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}
