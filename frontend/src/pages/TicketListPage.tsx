import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { RefreshCw, Search, Download, X, PhoneCall, Mail, Globe, Radio, ShieldCheck } from 'lucide-react';
import { motion } from 'framer-motion';
import { ticketService } from '../services/ticketService';
import { TableSkeleton, ErrorState, EmptyState } from '../components/ui/States';
import type { Ticket, Canal, Statut, TicketFilter } from '../types/ticket';

const container = { hidden: {}, show: { transition: { staggerChildren: 0.04 } } };
const item = { hidden: { opacity: 0, y: 8 }, show: { opacity: 1, y: 0, transition: { duration: 0.25 } } };

const canalColors: Record<string, string> = {
  CALL: 'var(--canal-call)', EMAIL: 'var(--canal-email)', WEB: 'var(--canal-web)',
};
const statutColors: Record<string, string> = {
  NOUVEAU: 'var(--info)', EN_COURS: 'var(--warning)', RESOLU: 'var(--success)', FERME: 'var(--text-3)',
};
const statutLabels: Record<string, string> = {
  NOUVEAU: '● Nouveau', EN_COURS: '◐ En cours', RESOLU: '✓ Résolu', FERME: '○ Fermé',
};

export default function TicketListPage() {
  const navigate = useNavigate();
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  // ── Filtres avancés (CDC : liste filtrable) ──
  const [statut, setStatut] = useState<Statut | ''>('');
  const [canal, setCanal] = useState<Canal | ''>('');
  const [priorite, setPriorite] = useState<'' | '1' | '2' | '3'>('');
  const [search, setSearch] = useState('');
  const [sortBy, setSortBy] = useState<'createdAt' | 'priorite' | 'statut'>('createdAt');
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('desc');

  const fetchTickets = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const filters: TicketFilter = {
        page,
        size: 15,
        ...(statut && { statut }),
        ...(canal && { canal }),
        ...(priorite && { priorite: Number.parseInt(priorite, 10) as 1 | 2 | 3 }),
        ...(search && { search }),
      };
      const data = await ticketService.getTickets(filters);
      const sorted = [...data.content];
      // Client-side sort
      sorted.sort((a, b) => {
        let cmp = 0;
        if (sortBy === 'createdAt') cmp = new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
        else if (sortBy === 'priorite') cmp = a.priorite - b.priorite;
        else if (sortBy === 'statut') cmp = a.statut.localeCompare(b.statut);
        return sortDir === 'desc' ? -cmp : cmp;
      });
      setTickets(sorted);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Erreur de chargement');
    } finally {
      setLoading(false);
    }
  }, [page, statut, canal, priorite, search, sortBy, sortDir]);

  useEffect(() => { fetchTickets(); }, [fetchTickets]);

  const handleExportCSV = () => {
    const headers = ['Numéro', 'Client', 'Canal', 'Statut', 'Priorité', 'Date'];
    const rows = tickets.map(t => [t.numero, t.clientNom, t.canal, t.statut, `P${t.priorite}`, new Date(t.createdAt).toLocaleDateString('fr-FR')]);
    const csv = [headers, ...rows].map(r => r.join(',')).join('\n');
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url; a.download = `tickets-export-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click(); URL.revokeObjectURL(url);
  };

  const resetFilters = () => { setStatut(''); setCanal(''); setPriorite(''); setSearch(''); setPage(0); };
  const hasFilters = !!(statut || canal || priorite || search);

  if (error) return <ErrorState message={error} onRetry={fetchTickets} />;

  return (
    <motion.div variants={container} initial="hidden" animate="show" className="space-y-4">
      {/* Header */}
      <motion.div variants={item} className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2">
        <div>
          <h1 className="text-xl font-extrabold tracking-tight">Tickets</h1>
          <p className="text-xs text-txt-3 font-mono mt-0.5">{totalElements} ticket{totalElements > 1 ? 's' : ''} au total</p>
        </div>
        <div className="flex items-center gap-2">
          <button onClick={handleExportCSV} className="btn-ghost h-9 px-3 rounded-lg text-xs flex items-center gap-1.5 cursor-pointer">
            <Download size={12} /> CSV
          </button>
          <button onClick={fetchTickets} className="btn-ghost h-9 px-3 rounded-lg text-xs flex items-center gap-1.5 cursor-pointer" disabled={loading}>
            <RefreshCw size={12} className={loading ? 'animate-spin' : ''} /> Actualiser
          </button>
        </div>
      </motion.div>

      {/* ── Premium Filter Bar — Glassmorphic & Ultra-Clean ── */}
      <motion.div 
        variants={item} 
        className="card-aurora rounded-2xl glass backdrop-blur-md border border-[rgba(99,91,255,0.08)] bg-opacity-70 shadow-lg p-4"
      >
        <div className="flex flex-col gap-4">
          {/* Top Row: Search and Quick Actions */}
          <div className="flex flex-col md:flex-row items-center justify-between gap-4">
            <div className="relative w-full md:max-w-md group">
              <Search size={14} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-[var(--text-3)] transition-colors group-focus-within:text-[var(--primary)]" />
              <input
                className="aurora-input pl-11 pr-9 h-10 text-xs rounded-xl bg-[var(--elevated)]/60 border border-[var(--border)] focus:border-[var(--primary)] focus:ring-[3px] focus:ring-[var(--primary-glow)] transition-all duration-300 shadow-sm w-full"
                style={{ paddingLeft: '38px', paddingRight: '36px' }}
                placeholder="Rechercher par client, numéro..."
                value={search}
                onChange={e => { setSearch(e.target.value); setPage(0); }}
              />
              {search ? (
                <button 
                  onClick={() => { setSearch(''); setPage(0); }} 
                  className="absolute right-2.5 top-1/2 -translate-y-1/2 w-5 h-5 rounded-full flex items-center justify-center bg-[var(--muted)] hover:bg-[var(--border-visible)] text-[var(--text-3)] hover:text-[var(--text)] transition-colors cursor-pointer border-none text-[9px]"
                >
                  <X size={11} />
                </button>
              ) : (
                <span className="absolute right-3 top-1/2 -translate-y-1/2 font-mono text-[9px] text-[var(--text-3)] border border-[var(--border)] px-1.5 py-0.5 rounded bg-[var(--surface)] opacity-60 select-none pointer-events-none transition-all group-focus-within:opacity-0 group-focus-within:scale-75">
                  ⌘F
                </span>
              )}
            </div>

            {/* Sort Selector & Reset button side-by-side */}
            <div className="flex items-center gap-3 w-full md:w-auto justify-end">
              <select 
                className="aurora-select h-10 text-xs rounded-xl bg-[var(--elevated)] border border-[var(--border)] px-3 text-[var(--text-2)] hover:text-[var(--text)] transition-all font-mono min-w-[145px]"
                value={`${sortBy}-${sortDir}`}
                onChange={e => { const [by, dir] = e.target.value.split('-'); setSortBy(by as typeof sortBy); setSortDir(dir as 'asc' | 'desc'); }}
              >
                <option value="createdAt-desc">↓ Plus récent</option>
                <option value="createdAt-asc">↑ Plus ancien</option>
                <option value="priorite-asc">↑ Priorité</option>
                <option value="priorite-desc">↓ Priorité</option>
                <option value="statut-asc">A→Z Statut</option>
              </select>

              {hasFilters && (
                <motion.button 
                  initial={{ opacity: 0, scale: 0.9 }} 
                  animate={{ opacity: 1, scale: 1 }} 
                  whileTap={{ scale: 0.95 }}
                  onClick={resetFilters} 
                  className="cursor-pointer inline-flex items-center justify-center gap-1.5 px-3 h-10 rounded-xl font-mono text-[10.5px] font-extrabold border bg-[var(--danger-soft)] border-rose-500/20 text-[var(--danger)] hover:bg-rose-500/15 transition-all shadow-[0_0_15px_rgba(255,71,87,0.06)] hover:shadow-[0_0_20px_rgba(255,71,87,0.12)]"
                >
                  <X size={12} />
                  <span>Reset</span>
                  <span className="flex items-center justify-center bg-[var(--danger)] text-white w-4.5 h-4.5 rounded-full text-[9px] font-bold">
                    {[statut, canal, priorite, search].filter(Boolean).length}
                  </span>
                </motion.button>
              )}
            </div>
          </div>

          {/* Bottom Row: Segments / Pill Filters */}
          <div className="flex flex-wrap items-center gap-5 pt-3 border-t border-[var(--border)]/40">
            {/* Statut filter group */}
            <div className="flex flex-col gap-1.5">
              <span className="font-mono text-[9px] text-[var(--text-3)] font-bold uppercase tracking-wider">Statut</span>
              <div className="bg-[var(--elevated)]/50 border border-[var(--border)] rounded-xl p-1 flex gap-0.5 flex-wrap">
                {([
                  { value: '', label: 'Tous', icon: '⊙', color: undefined },
                  { value: 'NOUVEAU', label: 'Nouveau', icon: '●', color: 'var(--info)' },
                  { value: 'EN_COURS', label: 'En cours', icon: '◐', color: 'var(--warning)' },
                  { value: 'RESOLU', label: 'Résolu', icon: '✓', color: 'var(--success)' },
                  { value: 'FERME', label: 'Fermé', icon: '○', color: 'var(--text-3)' },
                ] as const).map(s => (
                  <button 
                    key={s.value} 
                    onClick={() => { setStatut(s.value as Statut | ''); setPage(0); }} 
                    className="relative cursor-pointer px-3.5 py-1.5 rounded-lg text-[10.5px] font-semibold transition-all duration-300 flex items-center gap-1.5 border-none bg-transparent"
                    style={{
                      color: statut === s.value ? (s.color || 'var(--primary)') : 'var(--text-2)',
                    }}
                  >
                    {statut === s.value && (
                      <motion.div 
                        layoutId="statut-seg" 
                        className="absolute inset-0 rounded-lg bg-[var(--surface)] border border-[var(--primary)]/20 shadow-[0_2px_8px_rgba(99,91,255,0.08)]"
                        transition={{ type: 'spring', stiffness: 450, damping: 30 }}
                      />
                    )}
                    <span className="relative z-10 flex items-center gap-1.5">
                      <span style={{ color: s.color || 'var(--text-3)' }}>{s.icon}</span>
                      {s.label}
                    </span>
                  </button>
                ))}
              </div>
            </div>

            {/* Canal filter group */}
            <div className="flex flex-col gap-1.5">
              <span className="font-mono text-[9px] text-[var(--text-3)] font-bold uppercase tracking-wider">Canal</span>
              <div className="bg-[var(--elevated)]/50 border border-[var(--border)] rounded-xl p-1 flex gap-0.5 flex-wrap">
                {([
                  { value: '', label: 'Tous', icon: <Radio size={11} />, color: undefined },
                  { value: 'CALL', label: 'Appel', icon: <PhoneCall size={11} />, color: 'var(--canal-call)' },
                  { value: 'EMAIL', label: 'Email', icon: <Mail size={11} />, color: 'var(--canal-email)' },
                  { value: 'WEB', label: 'Web', icon: <Globe size={11} />, color: 'var(--canal-web)' },
                ] as const).map(c => {
                  const active = canal === c.value;
                  return (
                    <button 
                      key={c.value} 
                      onClick={() => { setCanal(c.value as Canal | ''); setPage(0); }} 
                      className="relative cursor-pointer px-3.5 py-1.5 rounded-lg text-[10.5px] font-semibold transition-all duration-300 flex items-center gap-1.5 border-none bg-transparent"
                      style={{
                        color: active ? (c.color || 'var(--primary)') : 'var(--text-2)',
                      }}
                    >
                      {active && (
                        <motion.div 
                          layoutId="canal-seg" 
                          className="absolute inset-0 rounded-lg bg-[var(--surface)] border border-[var(--primary)]/20 shadow-[0_2px_8px_rgba(99,91,255,0.08)]"
                          transition={{ type: 'spring', stiffness: 450, damping: 30 }}
                        />
                      )}
                      <span className="relative z-10 flex items-center gap-1.5">
                        <span style={{ color: c.color || 'var(--text-3)' }}>{c.icon}</span>
                        {c.label}
                      </span>
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Priorité filter group */}
            <div className="flex flex-col gap-1.5">
              <span className="font-mono text-[9px] text-[var(--text-3)] font-bold uppercase tracking-wider">Priorité</span>
              <div className="bg-[var(--elevated)]/50 border border-[var(--border)] rounded-xl p-1 flex gap-0.5 flex-wrap">
                {([
                  { value: '', label: 'Toutes', dot: 'var(--text-3)' },
                  { value: '1', label: 'P1', dot: 'var(--danger)' },
                  { value: '2', label: 'P2', dot: 'var(--warning)' },
                  { value: '3', label: 'P3', dot: 'var(--success)' },
                ] as const).map(p => {
                  const active = priorite === p.value;
                  return (
                    <button 
                      key={p.value} 
                      onClick={() => { setPriorite(p.value as '' | '1' | '2' | '3'); setPage(0); }} 
                      className="relative cursor-pointer px-3.5 py-1.5 rounded-lg text-[10.5px] font-semibold transition-all duration-300 flex items-center gap-1.5 border-none bg-transparent"
                      style={{
                        color: active ? p.dot : 'var(--text-2)',
                      }}
                    >
                      {active && (
                        <motion.div 
                          layoutId="priorite-seg" 
                          className="absolute inset-0 rounded-lg bg-[var(--surface)] border border-[var(--primary)]/20 shadow-[0_2px_8px_rgba(99,91,255,0.08)]"
                          transition={{ type: 'spring', stiffness: 450, damping: 30 }}
                        />
                      )}
                      <span className="relative z-10 flex items-center gap-1.5">
                        <span 
                          className="w-1.5 h-1.5 rounded-full transition-transform duration-300"
                          style={{ 
                            background: p.dot, 
                            boxShadow: `0 0 6px ${p.dot}`,
                            transform: active ? 'scale(1.2)' : 'scale(1)'
                          }} 
                        />
                        {p.label}
                      </span>
                    </button>
                  );
                })}
              </div>
            </div>

          </div>
        </div>
      </motion.div>

      {/* Table */}
      <motion.div variants={item} className="card-aurora rounded-2xl overflow-hidden">
        {(() => {
          if (loading) return <div className="p-4"><TableSkeleton rows={8} cols={7} /></div>;
          if (tickets.length === 0) return (
            <EmptyState icon="search" title="Aucun résultat" subtitle="Aucun ticket ne correspond à ces filtres" action={
              hasFilters ? <button onClick={resetFilters} className="btn-ghost h-8 px-4 rounded-lg text-xs cursor-pointer">Réinitialiser les filtres</button> : undefined
            } />
          );
          return (
          <>
            <div className="table-scroll-wrapper">
            <table className="aurora-table">
              <thead>
                <tr>
                  <th>Numéro</th>
                  <th>Client</th>
                  <th>Canal</th>
                  <th>Statut</th>
                  <th>Priorité</th>
                  <th>Opérateur</th>
                  <th>Date</th>
                  <th style={{ width: '60px', textAlign: 'center' }}>Audit</th>
                </tr>
              </thead>
              <tbody>
                {tickets.map(t => (
                  <tr 
                    key={t.id} 
                    role="button"
                    tabIndex={0}
                    onClick={() => navigate(`/tickets/${t.id}`)} 
                    onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); navigate(`/tickets/${t.id}`); } }}
                    className="cursor-pointer"
                  >
                    <td className="font-mono text-xs font-semibold text-[var(--primary)]">{t.numero}</td>
                    <td>
                      <div className="text-xs font-medium">{t.clientNom}</div>
                      {t.clientTel && <div className="font-mono text-[9px] text-txt-3">{t.clientTel}</div>}
                    </td>
                    <td>
                      {(() => {
                        let canalIcon: React.ReactNode;
                        if (t.canal === 'CALL') canalIcon = <PhoneCall size={12} />;
                        else if (t.canal === 'EMAIL') canalIcon = <Mail size={12} />;
                        else canalIcon = <Globe size={12} />;
                        return (
                          <span className="badge" style={{ background: `${canalColors[t.canal]}15`, color: canalColors[t.canal] }}>
                            <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>{canalIcon} {t.canal}</span>
                          </span>
                        );
                      })()}
                    </td>
                    <td>
                      <span className="badge" style={{ background: `${statutColors[t.statut]}15`, color: statutColors[t.statut] }}>
                        {statutLabels[t.statut] || t.statut}
                      </span>
                    </td>
                    <td>
                      {(() => {
                        let prioClass = 'text-txt-3';
                        if (t.priorite === 1) prioClass = 'text-[var(--danger)]';
                        else if (t.priorite === 2) prioClass = 'text-[var(--warning)]';
                        return <span className={`font-mono text-xs font-bold ${prioClass}`}>P{t.priorite}</span>;
                      })()}
                    </td>
                    <td className="font-mono text-[10px] text-txt-3">
                      {(() => {
                        if (t.operateurName) return t.operateurName;
                        if (t.operateurId) return `${t.operateurId.slice(0, 8)}…`;
                        return <span className="text-amber-400">Non assigné</span>;
                      })()}
                    </td>
                    <td className="font-mono text-[11px] text-txt-3">
                      {new Date(t.createdAt).toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit', year: '2-digit' })}
                      <div className="text-[9px]">{new Date(t.createdAt).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })}</div>
                    </td>
                    <td style={{ textAlign: 'center' }}>
                      <button
                        onClick={(e) => { e.stopPropagation(); navigate(`/audit/${t.id}`); }}
                        title="Auditer ce ticket"
                        className="cursor-pointer transition-all hover:scale-110"
                        style={{
                          display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
                          width: '28px', height: '28px', borderRadius: '8px',
                          background: 'rgba(99,91,255,0.08)', border: '1px solid rgba(99,91,255,0.15)',
                          color: 'var(--primary)',
                        }}
                      >
                        <ShieldCheck size={14} />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            </div>
            {/* Pagination */}
            {totalPages > 1 && (
              <div className="p-3 border-t border-[var(--border)] flex items-center justify-between px-5">
                <span className="font-mono text-[10px] text-txt-3">
                  Page {page + 1} / {totalPages} · {totalElements} résultats
                </span>
                <div className="flex gap-1.5">
                  <button onClick={() => setPage(0)} disabled={page === 0} className="btn-ghost h-7 px-2.5 rounded text-xs cursor-pointer disabled:opacity-30">⟪</button>
                  <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0} className="btn-ghost h-7 px-2.5 rounded text-xs cursor-pointer disabled:opacity-30">←</button>
                  <button onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))} disabled={page >= totalPages - 1} className="btn-ghost h-7 px-2.5 rounded text-xs cursor-pointer disabled:opacity-30">→</button>
                  <button onClick={() => setPage(totalPages - 1)} disabled={page >= totalPages - 1} className="btn-ghost h-7 px-2.5 rounded text-xs cursor-pointer disabled:opacity-30">⟫</button>
                </div>
              </div>
            )}
          </>
          );
        })()}
      </motion.div>
    </motion.div>
  );
}
