import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { RefreshCw, Search, Download } from 'lucide-react';
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
        ...(statut && { statut: statut as Statut }),
        ...(canal && { canal: canal as Canal }),
        ...(priorite && { priorite: parseInt(priorite) as 1 | 2 | 3 }),
        ...(search && { search }),
      };
      const data = await ticketService.getTickets(filters);
      let sorted = [...data.content];
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

      {/* Filters bar */}
      <motion.div variants={item} className="card-aurora rounded-2xl p-4">
        <div className="flex flex-wrap items-center gap-3">
          {/* Search */}
          <div className="relative flex-1 min-w-[160px]">
            <Search size={13} className="absolute left-3 top-1/2 -translate-y-1/2 text-txt-3" />
            <input
              className="aurora-input pl-9 h-9"
              placeholder="Rechercher par client, numéro…"
              value={search}
              onChange={e => { setSearch(e.target.value); setPage(0); }}
            />
          </div>

          {/* Statut */}
          <select className="aurora-select h-9 text-xs w-[140px]" value={statut} onChange={e => { setStatut(e.target.value as Statut | ''); setPage(0); }}>
            <option value="">Tous statuts</option>
            <option value="NOUVEAU">Nouveau</option>
            <option value="EN_COURS">En cours</option>
            <option value="RESOLU">Résolu</option>
            <option value="FERME">Fermé</option>
          </select>

          {/* Canal */}
          <select className="aurora-select h-9 text-xs w-[120px]" value={canal} onChange={e => { setCanal(e.target.value as Canal | ''); setPage(0); }}>
            <option value="">Tous canaux</option>
            <option value="CALL">📞 Appel</option>
            <option value="EMAIL">📧 Email</option>
            <option value="WEB">🌐 Web</option>
          </select>

          {/* Priorité */}
          <select className="aurora-select h-9 text-xs w-[120px]" value={priorite} onChange={e => { setPriorite(e.target.value as '' | '1' | '2' | '3'); setPage(0); }}>
            <option value="">Priorité</option>
            <option value="1">🔴 P1 Urgente</option>
            <option value="2">🟡 P2 Normale</option>
            <option value="3">🟢 P3 Basse</option>
          </select>

          {/* Sort */}
          <select className="aurora-select h-9 text-xs w-[140px]" value={`${sortBy}-${sortDir}`} onChange={e => { const [by, dir] = e.target.value.split('-'); setSortBy(by as typeof sortBy); setSortDir(dir as 'asc' | 'desc'); }}>
            <option value="createdAt-desc">Plus récent</option>
            <option value="createdAt-asc">Plus ancien</option>
            <option value="priorite-asc">Priorité ↑</option>
            <option value="priorite-desc">Priorité ↓</option>
            <option value="statut-asc">Statut A→Z</option>
          </select>

          {hasFilters && (
            <button onClick={resetFilters} className="text-[10px] font-mono text-rose-400 hover:text-rose-300 cursor-pointer">✕ Réinitialiser</button>
          )}
        </div>
      </motion.div>

      {/* Table */}
      <motion.div variants={item} className="card-aurora rounded-2xl overflow-hidden">
        {loading ? <div className="p-4"><TableSkeleton rows={8} cols={7} /></div> : tickets.length === 0 ? (
          <EmptyState icon="🔍" title="Aucun résultat" subtitle="Aucun ticket ne correspond à ces filtres" action={
            hasFilters ? <button onClick={resetFilters} className="btn-ghost h-8 px-4 rounded-lg text-xs cursor-pointer">Réinitialiser les filtres</button> : undefined
          } />
        ) : (
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
                </tr>
              </thead>
              <tbody>
                {tickets.map(t => (
                  <tr key={t.id} onClick={() => navigate(`/tickets/${t.id}`)} className="cursor-pointer">
                    <td className="font-mono text-xs font-semibold text-[var(--primary)]">{t.numero}</td>
                    <td>
                      <div className="text-xs font-medium">{t.clientNom}</div>
                      {t.clientTel && <div className="font-mono text-[9px] text-txt-3">{t.clientTel}</div>}
                    </td>
                    <td>
                      <span className="badge" style={{ background: `${canalColors[t.canal]}15`, color: canalColors[t.canal] }}>
                        {t.canal === 'CALL' ? '📞' : t.canal === 'EMAIL' ? '📧' : '🌐'} {t.canal}
                      </span>
                    </td>
                    <td>
                      <span className="badge" style={{ background: `${statutColors[t.statut]}15`, color: statutColors[t.statut] }}>
                        {statutLabels[t.statut] || t.statut}
                      </span>
                    </td>
                    <td>
                      <span className={`font-mono text-xs font-bold ${t.priorite === 1 ? 'text-[var(--danger)]' : t.priorite === 2 ? 'text-[var(--warning)]' : 'text-txt-3'}`}>
                        P{t.priorite}
                      </span>
                    </td>
                    <td className="font-mono text-[10px] text-txt-3">
                      {t.operateurName ? t.operateurName : t.operateurId ? t.operateurId.slice(0, 8) + '…' : <span className="text-amber-400">Non assigné</span>}
                    </td>
                    <td className="font-mono text-[11px] text-txt-3">
                      {new Date(t.createdAt).toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit', year: '2-digit' })}
                      <div className="text-[9px]">{new Date(t.createdAt).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })}</div>
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
        )}
      </motion.div>
    </motion.div>
  );
}
