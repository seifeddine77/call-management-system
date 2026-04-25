import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { RefreshCw } from 'lucide-react';
import toast from 'react-hot-toast';
import { ticketService } from '../services/ticketService';
import { useAuth } from '../contexts/AuthContext';
import { KpiSkeleton, ErrorState, EmptyState } from '../components/ui/States';
import type { TicketStats, QueueEntry, Statut, Ticket } from '../types/ticket';

type FilterKey = 'QUEUE' | Statut;

/* Unified item so we can display queue entries AND filtered tickets in the same list */
interface DisplayItem {
  ticketId: string;
  ticketNumero: string;
  clientNom: string;
  canal: string;
  priorite: number;
  createdAt: string;
  statut?: string;
  operateurName?: string | null;
  position?: number;
}

export default function QueuePage() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [items, setItems] = useState<DisplayItem[]>([]);
  const [stats, setStats] = useState<TicketStats | null>(null);
  const [queueCount, setQueueCount] = useState<number>(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [assigning, setAssigning] = useState<string | null>(null);
  const [filter, setFilter] = useState<FilterKey>('QUEUE');

  /* ── Map Ticket → DisplayItem ── */
  const ticketToItem = (t: Ticket, i: number): DisplayItem => ({
    ticketId: t.id,
    ticketNumero: t.numero,
    clientNom: t.clientNom,
    canal: t.canal,
    priorite: t.priorite,
    createdAt: t.createdAt,
    statut: t.statut,
    operateurName: t.operateurName,
    position: i + 1,
  });

  /* ── Map QueueEntry → DisplayItem ── */
  const queueToItem = (q: QueueEntry): DisplayItem => ({
    ticketId: q.ticketId,
    ticketNumero: q.ticketNumero,
    clientNom: q.clientNom,
    canal: q.canal,
    priorite: q.priorite,
    createdAt: q.createdAt,
    position: q.position,
  });

  const fetchData = useCallback(async (activeFilter: FilterKey) => {
    setLoading(true);
    setError(null);
    try {
      // Always fetch stats + queue count (for the QUEUE card to stay stable)
      const [queueData, statsData] = await Promise.all([
        ticketService.getQueue(),
        ticketService.getStats(),
      ]);
      setStats(statsData);
      setQueueCount((queueData.tickets || []).length);

      if (activeFilter === 'QUEUE') {
        setItems((queueData.tickets || []).map(queueToItem));
      } else {
        const ticketData = await ticketService.getTickets({ statut: activeFilter, size: 50 });
        setItems((ticketData.content || []).map(ticketToItem));
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Erreur de chargement');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchData(filter); }, [filter, fetchData]);

  const handleRefresh = () => fetchData(filter);

  const handleFilterClick = (key: FilterKey) => {
    if (key === filter) return; // already selected
    setFilter(key);
  };

  const handleAssign = async (ticketId: string) => {
    if (!user?.userId) return;
    setAssigning(ticketId);
    try {
      await ticketService.assignTicket(ticketId, user.userId);
      toast.success('Ticket pris en charge !');
      setItems(prev => prev.filter(t => t.ticketId !== ticketId));
      navigate(`/tickets/${ticketId}`);
    } catch {
      toast.error('Impossible d\'assigner ce ticket');
    } finally {
      setAssigning(null);
    }
  };

  const canalIcon = (canal: string) => {
    const map: Record<string, { icon: string; cls: string }> = {
      CALL: { icon: '☎', cls: 'bg-violet-500/10 text-violet-400 border-violet-500/20' },
      EMAIL: { icon: '✉', cls: 'bg-blue-400/10 text-blue-400 border-blue-400/20' },
      WEB: { icon: '⬡', cls: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' },
    };
    return map[canal] || { icon: '?', cls: 'bg-[var(--muted)] text-txt-3 border-border-subtle' };
  };

  const timeSince = (dateStr: string) => {
    const diff = Date.now() - new Date(dateStr).getTime();
    const mins = Math.floor(diff / 60000);
    if (mins < 60) return `${mins}min`;
    return `${Math.floor(mins / 60)}h${mins % 60}m`;
  };

  /* ── Filter config ── */
  const filters: { key: FilterKey; label: string; icon: string; color: string; activeColor: string; value: number | null }[] = [
    { key: 'QUEUE',    label: 'File d\'attente', icon: '📋', color: 'text-indigo-400',  activeColor: 'bg-indigo-500/15 border-indigo-500/30 text-indigo-400', value: queueCount },
    { key: 'NOUVEAU',  label: 'Nouveaux',        icon: '🆕', color: 'text-blue-400',    activeColor: 'bg-blue-500/15 border-blue-500/30 text-blue-400',      value: stats?.nouveau ?? null },
    { key: 'EN_COURS', label: 'En cours',         icon: '🔄', color: 'text-amber-400',   activeColor: 'bg-amber-500/15 border-amber-500/30 text-amber-400',   value: stats?.enCours ?? null },
    { key: 'RESOLU',   label: 'Résolus',          icon: '✅', color: 'text-emerald-400', activeColor: 'bg-emerald-500/15 border-emerald-500/30 text-emerald-400', value: stats?.resolu ?? null },
    { key: 'FERME',    label: 'Fermés',           icon: '📁', color: 'text-txt-3',       activeColor: 'bg-[var(--muted)] border-[var(--border)] text-txt-2',  value: stats?.ferme ?? null },
  ];

  const subtitleMap: Record<FilterKey, string> = {
    QUEUE: `${items.length} ticket${items.length > 1 ? 's' : ''} en attente`,
    NOUVEAU: `${items.length} ticket${items.length > 1 ? 's' : ''} nouveau${items.length > 1 ? 'x' : ''}`,
    EN_COURS: `${items.length} ticket${items.length > 1 ? 's' : ''} en cours`,
    RESOLU: `${items.length} ticket${items.length > 1 ? 's' : ''} résolu${items.length > 1 ? 's' : ''}`,
    FERME: `${items.length} ticket${items.length > 1 ? 's' : ''} fermé${items.length > 1 ? 's' : ''}`,
  };

  const statutBadge = (statut?: string) => {
    const map: Record<string, { label: string; cls: string }> = {
      NOUVEAU: { label: 'Nouveau', cls: 'bg-blue-500/10 text-blue-400 border-blue-500/20' },
      EN_COURS: { label: 'En cours', cls: 'bg-amber-500/10 text-amber-400 border-amber-500/20' },
      RESOLU: { label: 'Résolu', cls: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' },
      FERME: { label: 'Fermé', cls: 'bg-[var(--muted)] text-txt-3 border-border-subtle' },
    };
    return statut ? map[statut] : null;
  };

  if (error) return <ErrorState message={error} onRetry={handleRefresh} />;

  return (
    <div className="flex flex-col gap-4">
      {/* Header */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2">
        <div>
          <h1 className="text-xl font-extrabold tracking-tight">File d'attente</h1>
          <p className="text-xs text-txt-3 font-mono mt-0.5">{subtitleMap[filter]}</p>
        </div>
        <button onClick={handleRefresh} className="btn-ghost h-9 px-3 rounded-lg text-xs flex items-center gap-1.5 cursor-pointer" disabled={loading}>
          <RefreshCw size={12} className={loading ? 'animate-spin' : ''} /> Actualiser
        </button>
      </div>

      {/* Filter buttons */}
      {loading && !stats ? (
        <div className="grid grid-cols-2 md:grid-cols-5 gap-3">{Array.from({ length: 5 }).map((_, i) => <KpiSkeleton key={i} />)}</div>
      ) : stats && (
        <div className="grid grid-cols-2 md:grid-cols-5 gap-3">
          {filters.map((f) => {
            const isActive = filter === f.key;
            return (
              <button
                key={f.key}
                onClick={() => handleFilterClick(f.key)}
                className={`rounded-2xl p-4 text-center transition-all duration-200 cursor-pointer border
                  ${isActive
                    ? `${f.activeColor} shadow-lg scale-[1.02]`
                    : 'card-aurora border-transparent hover:border-[var(--border)] hover:scale-[1.01]'
                  }`}
              >
                <div className="flex items-center justify-center gap-1.5 mb-2">
                  <span className="text-sm">{f.icon}</span>
                  <span className={`font-mono text-[9px] uppercase tracking-wider ${isActive ? '' : 'text-txt-3'}`}>
                    {f.label}
                  </span>
                </div>
                <div className={`text-2xl font-extrabold ${isActive ? '' : f.color}`}>
                  {f.value ?? '—'}
                </div>
              </button>
            );
          })}
        </div>
      )}

      {/* Ticket list */}
      <div className="card-aurora rounded-2xl overflow-hidden">
        {loading && items.length === 0 ? (
          <div className="p-5 space-y-3 animate-pulse">{Array.from({ length: 5 }).map((_, i) => <div key={i} className="h-14 bg-[var(--muted)] rounded-lg" />)}</div>
        ) : items.length === 0 ? (
          <EmptyState
            icon={filter === 'QUEUE' ? '✅' : '🔍'}
            title={filter === 'QUEUE' ? 'File vide' : 'Aucun résultat'}
            subtitle={filter === 'QUEUE'
              ? 'Aucun ticket en attente ! Tous les tickets sont pris en charge.'
              : `Aucun ticket avec le statut "${filters.find(f => f.key === filter)?.label}".`
            }
          />
        ) : (
          items.map((t) => {
            const c = canalIcon(t.canal);
            const wait = timeSince(t.createdAt);
            const isUrgent = t.priorite === 1;
            const sBadge = statutBadge(t.statut);
            return (
              <div
                key={t.ticketId}
                className={`flex flex-wrap md:flex-nowrap items-center gap-3 md:gap-4 px-4 md:px-5 py-3.5 border-b border-border cursor-pointer transition-all hover:bg-[var(--overlay)] relative
                  ${isUrgent ? 'before:absolute before:left-0 before:top-0 before:bottom-0 before:w-[3px] before:bg-rose-500' : ''}`}
                onClick={() => navigate(`/tickets/${t.ticketId}`)}
              >
                <span className="font-mono text-[11px] text-txt-3 w-5 text-center">{t.position}</span>
                <div className={`w-10 h-10 rounded-xl flex items-center justify-center text-base flex-shrink-0 border ${c.cls}`}>{c.icon}</div>
                <div className="flex-1 min-w-0">
                  <div className="text-[13px] font-semibold">{t.clientNom}</div>
                  <div className="font-mono text-[10px] text-txt-3">{t.ticketNumero}</div>
                </div>
                <span className={`font-mono text-[10px] px-2.5 py-1 rounded-lg border ${c.cls}`}>{c.icon} {t.canal === 'CALL' ? 'Appel' : t.canal}</span>
                {/* Show statut badge when not in queue view */}
                {sBadge && filter !== 'QUEUE' && (
                  <span className={`font-mono text-[10px] px-2.5 py-1 rounded-lg border ${sBadge.cls}`}>{sBadge.label}</span>
                )}
                <span className={`font-mono text-[10px] px-2.5 py-1 rounded-lg border ${isUrgent ? 'bg-rose-500/10 text-rose-400 border-rose-500/20' : 'bg-amber-500/10 text-amber-400 border-amber-500/20'}`}>
                  ⏱ {wait}
                </span>
                <span className={`badge border ${t.priorite === 1 ? 'bg-rose-500/10 text-rose-400 border-rose-500/20' : t.priorite === 2 ? 'bg-amber-500/10 text-amber-400 border-amber-500/20' : 'bg-[var(--muted)] text-txt-3 border-border-subtle'}`}>
                  P{t.priorite}
                </span>
                {/* Show "Prendre" button only for queue / nouveau tickets */}
                {(filter === 'QUEUE' || filter === 'NOUVEAU') && (
                  <button
                    onClick={(e) => { e.stopPropagation(); handleAssign(t.ticketId); }}
                    disabled={assigning === t.ticketId}
                    className="font-mono text-[10px] rounded-lg px-4 h-8 bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 cursor-pointer hover:bg-indigo-500/20 transition-colors disabled:opacity-50"
                  >
                    {assigning === t.ticketId ? '...' : 'Prendre →'}
                  </button>
                )}
                {/* Show operator name for EN_COURS */}
                {t.operateurName && filter !== 'QUEUE' && (
                  <span className="font-mono text-[10px] text-txt-3 truncate max-w-[100px]">👤 {t.operateurName}</span>
                )}
              </div>
            );
          })
        )}
      </div>
    </div>
  );
}
