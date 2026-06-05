import { useState, useEffect, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { 
  RefreshCw, ClipboardList, PlusCircle, RotateCw, CheckCircle2, 
  FolderClosed, PhoneCall, Mail, Globe, Clock, User, ArrowRight, ShieldAlert,
  Search, SlidersHorizontal, Bot
} from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import toast from 'react-hot-toast';
import { ticketService } from '../services/ticketService';
import { useAuth } from '../contexts/AuthContext';
import { KpiSkeleton, ErrorState, EmptyState } from '../components/ui/States';
import type { TicketStats, QueueEntry, Statut, Ticket } from '../types/ticket';

type FilterKey = 'QUEUE' | Statut;
type SortOption = 'PRIO_SLA' | 'NEWEST' | 'OLDEST';

const getPriorityBadgeClass = (priorite: number) => {
  if (priorite === 1) return 'bg-rose-500/10 text-rose-400 border-rose-500/20';
  if (priorite === 2) return 'bg-amber-500/10 text-amber-400 border-amber-500/20';
  return 'bg-indigo-500/5 text-indigo-400 border-indigo-500/10';
};

const getCanalIcon = (canal: string) => {
  const map: Record<string, { icon: React.ReactNode; cls: string }> = {
    CALL: { icon: <PhoneCall size={15} />, cls: 'bg-violet-500/10 text-violet-400 border-violet-500/20' },
    EMAIL: { icon: <Mail size={15} />, cls: 'bg-blue-400/10 text-blue-400 border-blue-400/20' },
    WEB: { icon: <Globe size={15} />, cls: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' },
  };
  return map[canal] || { icon: <Globe size={15} />, cls: 'bg-[var(--muted)] text-txt-3 border-border-subtle' };
};

const getTimeSince = (dateStr: string) => {
  const diff = Date.now() - new Date(dateStr).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 60) return `${mins} min`;
  return `${Math.floor(mins / 60)}h ${mins % 60}m`;
};

const getStatutBadge = (statut?: string) => {
  const map: Record<string, { label: string; cls: string }> = {
    NOUVEAU: { label: 'Nouveau', cls: 'bg-blue-500/10 text-blue-400 border-blue-500/20' },
    EN_COURS: { label: 'En cours', cls: 'bg-amber-500/10 text-amber-400 border-amber-500/20' },
    RESOLU: { label: 'Résolu', cls: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' },
    FERME: { label: 'Fermé', cls: 'bg-zinc-500/10 text-zinc-400 border-zinc-500/20' },
  };
  return statut ? map[statut] : null;
};

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

const queueToItem = (q: QueueEntry): DisplayItem => ({
  ticketId: q.ticketId,
  ticketNumero: q.ticketNumero,
  clientNom: q.clientNom,
  canal: q.canal,
  priorite: q.priorite,
  createdAt: q.createdAt,
  position: q.position,
});

const compareDisplayItems = (a: DisplayItem, b: DisplayItem, sortBy: SortOption): number => {
  if (sortBy === 'PRIO_SLA') {
    if (a.priorite !== b.priorite) {
      return a.priorite - b.priorite;
    }
    return new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
  }
  if (sortBy === 'NEWEST') {
    return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
  }
  if (sortBy === 'OLDEST') {
    return new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
  }
  return 0;
};

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

interface KpiCardProps {
  f: {
    key: FilterKey;
    label: string;
    icon: React.ReactNode;
    color: string;
    activeColor: string;
    value: number | null;
  };
  isActive: boolean;
  onClick: () => void;
}

function KpiCard({ f, isActive, onClick }: Readonly<KpiCardProps>) {
  return (
    <motion.button
      whileHover={{ y: -2, scale: 1.01 }}
      whileTap={{ scale: 0.99 }}
      onClick={onClick}
      className={`relative overflow-hidden rounded-2xl p-5 text-left transition-all duration-300 cursor-pointer border flex flex-col justify-between h-[110px]
        ${isActive
          ? `${f.activeColor} shadow-[0_0_20px_rgba(99,102,241,0.06)] border-[var(--border)]`
          : 'card-aurora border-transparent hover:border-border/60'
        }`}
    >
      {isActive && (
        <div className="absolute -right-6 -bottom-6 w-16 h-16 rounded-full bg-indigo-500/10 blur-xl pointer-events-none" />
      )}
      
      <div className="flex items-center justify-between w-full">
        <span className={`p-1.5 rounded-lg border bg-[var(--surface)]/60 ${isActive ? 'border-indigo-500/20 text-indigo-400' : 'border-border/60 text-txt-3'}`}>
          {f.icon}
        </span>
        {isActive && (
          <span className="w-1.5 h-1.5 rounded-full bg-indigo-500 shadow-[0_0_8px_#6366f1]" />
        )}
      </div>

      <div className="mt-2">
        <div className="font-mono text-[9px] uppercase tracking-wider text-txt-3 font-semibold mb-0.5">
          {f.label}
        </div>
        <div className={`text-2xl font-extrabold ${isActive ? 'text-txt' : f.color} tracking-tight`}>
          {f.value ?? '—'}
        </div>
      </div>
    </motion.button>
  );
}


interface AdvancedFiltersPanelProps {
  search: string;
  setSearch: (s: string) => void;
  channelFilter: string;
  setChannelFilter: (c: string) => void;
  priorityFilter: number | 'ALL';
  setPriorityFilter: (p: number | 'ALL') => void;
  sortBy: SortOption;
  setSortBy: (s: SortOption) => void;
  showAdvancedFilters: boolean;
  setShowAdvancedFilters: (b: boolean) => void;
}

function AdvancedFiltersPanel({
  search,
  setSearch,
  channelFilter,
  setChannelFilter,
  priorityFilter,
  setPriorityFilter,
  sortBy,
  setSortBy,
  showAdvancedFilters,
  setShowAdvancedFilters,
}: Readonly<AdvancedFiltersPanelProps>) {
  return (
    <div className="px-5 py-3 border-b border-border/60 bg-[var(--surface)]/40 flex flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="relative flex-1 min-w-[240px]">
          <Search size={14} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-txt-3" />
          <input
            type="text"
            placeholder="Rechercher par client ou par numéro de ticket..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="aurora-input pl-10"
          />
        </div>

        <button
          onClick={() => setShowAdvancedFilters(!showAdvancedFilters)}
          className={`btn-ghost h-9 px-3.5 rounded-xl text-xs flex items-center gap-1.5 cursor-pointer font-semibold border ${
            showAdvancedFilters ? 'bg-primary-soft text-primary border-primary/20' : 'bg-[var(--surface)] text-txt-2 border-border'
          }`}
        >
          <SlidersHorizontal size={12} />
          Filtres avancés
        </button>
      </div>

      <AnimatePresence>
        {showAdvancedFilters && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.25, ease: 'easeInOut' }}
            className="overflow-hidden flex flex-col gap-3 pt-3 border-t border-border/40"
          >
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div className="space-y-1.5">
                <span className="field-label block font-semibold text-[9px] tracking-wider text-txt-3">Canal de communication</span>
                <div className="flex flex-wrap gap-1.5">
                  {[
                    { k: 'ALL', l: 'Tous' },
                    { k: 'CALL', l: '📞 Vocal' },
                    { k: 'EMAIL', l: '✉️ Email' },
                    { k: 'WEB', l: '🌐 Web' }
                  ].map(ch => (
                    <button
                      key={ch.k}
                      onClick={() => setChannelFilter(ch.k)}
                      className={`chip font-semibold ${channelFilter === ch.k ? 'active' : ''}`}
                    >
                      {ch.l}
                    </button>
                  ))}
                </div>
              </div>

              <div className="space-y-1.5">
                <span className="field-label block font-semibold text-[9px] tracking-wider text-txt-3">Niveau de priorité</span>
                <div className="flex flex-wrap gap-1.5">
                  {[
                    { k: 'ALL', l: 'Toutes' },
                    { k: 1, l: '🔴 Prio 1 (Urgent)' },
                    { k: 2, l: '🟡 Prio 2 (Moyen)' },
                    { k: 3, l: '🔵 Prio 3 (Faible)' }
                  ].map(prio => (
                    <button
                      key={prio.k}
                      onClick={() => setPriorityFilter(prio.k as number | 'ALL')}
                      className={`chip font-semibold ${priorityFilter === prio.k ? 'active' : ''}`}
                    >
                      {prio.l}
                    </button>
                  ))}
                </div>
              </div>

              <div className="space-y-1.5">
                <span className="field-label block font-semibold text-[9px] tracking-wider text-txt-3">Ordre de tri</span>
                <select
                  value={sortBy}
                  onChange={(e) => setSortBy(e.target.value as SortOption)}
                  className="aurora-select"
                >
                  <option value="PRIO_SLA">Priorité & SLA (Urgent + FIFO)</option>
                  <option value="NEWEST">Plus récents d'abord</option>
                  <option value="OLDEST">Plus anciens d'abord</option>
                </select>
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}


interface TicketRowProps {
  t: DisplayItem;
  idx: number;
  filter: FilterKey;
  assigning: string | null;
  onAssign: (ticketId: string) => void;
  onNavigate: (ticketId: string) => void;
}

function TicketRow({
  t,
  idx,
  filter,
  assigning,
  onAssign,
  onNavigate,
}: Readonly<TicketRowProps>) {
  const c = getCanalIcon(t.canal);
  const wait = getTimeSince(t.createdAt);
  const isUrgent = t.priorite === 1;
  const sBadge = getStatutBadge(t.statut);

  return (
    <motion.div
      role="button"
      tabIndex={0}
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, x: -20 }}
      transition={{ duration: 0.2, delay: idx * 0.02 }}
      className="group flex flex-wrap md:flex-nowrap items-center justify-between gap-4 px-5 py-4 border-b border-border/50 cursor-pointer transition-all duration-300 hover:bg-indigo-500/[0.02] relative overflow-hidden"
      onClick={() => onNavigate(t.ticketId)}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          onNavigate(t.ticketId);
        }
      }}
    >
      {isUrgent && (
        <div className="absolute left-0 top-0 bottom-0 w-[3.5px] bg-rose-500 shadow-[0_0_10px_rgba(239,68,68,0.4)]" />
      )}

      <div className="flex items-center gap-4 flex-1 min-w-0">
        <span className="font-mono text-[10px] text-txt-3 w-6 text-center font-bold">
          #{t.position || idx + 1}
        </span>

        <div className={`w-10 h-10 rounded-xl flex items-center justify-center text-base flex-shrink-0 border transition-transform group-hover:scale-105 duration-300 ${c.cls}`}>
          {c.icon}
        </div>

        <div className="min-w-0">
          <div className="text-[13px] font-semibold text-txt tracking-wide group-hover:text-indigo-400 transition-colors flex items-center gap-2">
            {t.clientNom}
            {isUrgent && (
              <span className="flex items-center gap-0.5 text-[8px] font-extrabold font-mono uppercase tracking-widest text-rose-400 bg-rose-500/10 border border-rose-500/20 px-1.5 py-0.5 rounded shadow-sm">
                <ShieldAlert size={8} /> Urgent
              </span>
            )}
          </div>
          <div className="font-mono text-[9px] text-txt-3 mt-0.5 tracking-wider flex items-center gap-2">
            <span>{t.ticketNumero}</span>
            <span className="text-border/80">•</span>
            <span>Créé il y a {wait}</span>
          </div>
        </div>
      </div>

      <div className="flex items-center gap-3.5 flex-wrap">
        <span className={`font-mono text-[9.5px] font-semibold px-2.5 py-1 rounded-xl border flex items-center gap-1.5 shadow-sm bg-[var(--surface)] ${c.cls}`}>
          {c.icon}
          <span className="uppercase text-[8px] tracking-wider font-bold">
            {t.canal === 'CALL' ? 'Vocal' : t.canal}
          </span>
        </span>

        {sBadge && filter !== 'QUEUE' && (
          <span className={`font-mono text-[8px] font-extrabold uppercase tracking-widest px-2.5 py-1 rounded-xl border ${sBadge.cls}`}>
            {sBadge.label}
          </span>
        )}

        <span className={`font-mono text-[9.5px] px-2.5 py-1 rounded-xl border flex items-center gap-1 bg-[var(--surface)]/40 border-border/80 text-txt-2
          ${isUrgent ? 'text-rose-400 border-rose-500/10' : ''}`}
        >
          <Clock size={10} className={isUrgent ? 'animate-pulse text-rose-400' : 'text-txt-3'} /> 
          {wait}
        </span>

        <span className={`font-mono text-[9px] font-extrabold px-2.5 py-0.5 rounded-lg border shadow-sm ${getPriorityBadgeClass(t.priorite)}`}
        >
          PRIO {t.priorite}
        </span>

        {(filter === 'QUEUE' || filter === 'NOUVEAU') ? (
          <motion.button
            whileHover={{ scale: 1.04 }}
            whileTap={{ scale: 0.96 }}
            onClick={(e) => { e.stopPropagation(); onAssign(t.ticketId); }}
            disabled={assigning === t.ticketId}
            className="font-mono text-[9px] font-extrabold uppercase tracking-widest rounded-xl px-4.5 h-8 bg-indigo-500/10 text-indigo-400 border border-indigo-500/25 cursor-pointer hover:bg-indigo-500/20 hover:text-indigo-500 dark:hover:text-indigo-300 hover:border-indigo-400/40 transition-all flex items-center gap-1 disabled:opacity-50 disabled:pointer-events-none"
          >
            {assigning === t.ticketId ? (
              <RefreshCw size={9} className="animate-spin text-indigo-400" />
            ) : (
              <>
                Traiter
                <ArrowRight size={10} className="group-hover:translate-x-0.5 transition-transform" />
              </>
            )}
          </motion.button>
        ) : t.operateurName && (
          <span className="font-mono text-[10px] text-txt-3 truncate max-w-[120px] flex items-center gap-1.5 bg-[var(--surface)] border border-border px-2.5 py-1 rounded-xl">
            <User size={10} className="text-indigo-400" />
            {t.operateurName}
          </span>
        )}
      </div>
    </motion.div>
  );
}


/* ─── Queue State Handlers (Extracted to module level for reduced cognitive complexity) ─── */

async function fetchQueueData(
  activeFilter: FilterKey,
  setLoading: (l: boolean) => void,
  setError: (e: string | null) => void,
  setStats: (s: TicketStats | null) => void,
  setQueueCount: (c: number) => void,
  setItems: (i: DisplayItem[]) => void
) {
  setLoading(true);
  setError(null);
  try {
    const [queueData, statsData] = await Promise.all([
      ticketService.getQueue(),
      ticketService.getStats(),
    ]);
    setStats(statsData);
    setQueueCount((queueData.tickets || []).length);

    if (activeFilter === 'QUEUE') {
      setItems((queueData.tickets || []).map((q) => queueToItem(q)));
    } else {
      const ticketData = await ticketService.getTickets({ statut: activeFilter, size: 50 });
      setItems((ticketData.content || []).map((t, i) => ticketToItem(t, i)));
    }
  } catch (err: unknown) {
    setError(err instanceof Error ? err.message : 'Erreur de chargement');
  } finally {
    setLoading(false);
  }
}

async function assignQueueTicket(
  ticketId: string,
  userId: string,
  setAssigning: (id: string | null) => void,
  setItems: React.Dispatch<React.SetStateAction<DisplayItem[]>>,
  navigate: (path: string) => void
) {
  setAssigning(ticketId);
  try {
    await ticketService.assignTicket(ticketId, userId);
    toast.success('🎙 Ticket pris en charge avec succès !');
    setItems(prev => prev.filter(t => t.ticketId !== ticketId));
    navigate(`/tickets/${ticketId}`);
  } catch {
    toast.error('Impossible d\'assigner ce ticket');
  } finally {
    setAssigning(null);
  }
}

async function autoDispatchQueueTicket(
  userId: string,
  items: DisplayItem[],
  setAssigning: (id: string | null) => void,
  navigate: (path: string) => void
) {
  const queueCandidates = items.filter(t => !t.statut || t.statut === 'NOUVEAU');
  
  if (queueCandidates.length === 0) {
    toast.error("Aucun ticket disponible dans la file d'attente");
    return;
  }
  
  const sortedCandidates = [...queueCandidates].sort((a, b) => {
    if (a.priorite !== b.priorite) return a.priorite - b.priorite;
    return new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
  });

  const targetTicket = sortedCandidates[0];
  setAssigning(targetTicket.ticketId);
  
  try {
    await ticketService.assignTicket(targetTicket.ticketId, userId);
    toast.success(`🎙️ Auto-dispatch : Ticket #${targetTicket.ticketNumero} pris en charge !`);
    navigate(`/tickets/${targetTicket.ticketId}`);
  } catch {
    toast.error("Impossible d'assigner automatiquement ce ticket");
  } finally {
    setAssigning(null);
  }
}

function filterAndSortQueueItems(
  items: DisplayItem[],
  search: string,
  channelFilter: string,
  priorityFilter: number | 'ALL',
  sortBy: SortOption
): DisplayItem[] {
  let result = [...items];

  if (search.trim()) {
    const q = search.toLowerCase();
    result = result.filter(
      t =>
        t.clientNom.toLowerCase().includes(q) ||
        t.ticketNumero.toLowerCase().includes(q)
    );
  }

  if (channelFilter !== 'ALL') {
    result = result.filter(t => t.canal === channelFilter);
  }

  if (priorityFilter !== 'ALL') {
    result = result.filter(t => t.priorite === priorityFilter);
  }

  result.sort((a, b) => compareDisplayItems(a, b, sortBy));

  return result.map((item, idx) => ({
    ...item,
    position: idx + 1
  }));
}

function useQueueState() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [items, setItems] = useState<DisplayItem[]>([]);
  const [stats, setStats] = useState<TicketStats | null>(null);
  const [queueCount, setQueueCount] = useState<number>(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [assigning, setAssigning] = useState<string | null>(null);
  const [filter, setFilter] = useState<FilterKey>('QUEUE');

  /* ── Advanced Filter States ── */
  const [search, setSearch] = useState('');
  const [channelFilter, setChannelFilter] = useState<string>('ALL');
  const [priorityFilter, setPriorityFilter] = useState<number | 'ALL'>('ALL');
  const [sortBy, setSortBy] = useState<SortOption>('PRIO_SLA');
  const [showAdvancedFilters, setShowAdvancedFilters] = useState(false);

  const fetchData = useCallback(async (activeFilter: FilterKey) => {
    await fetchQueueData(activeFilter, setLoading, setError, setStats, setQueueCount, setItems);
  }, []);

  useEffect(() => { fetchData(filter); }, [filter, fetchData]);

  const handleRefresh = () => fetchData(filter);

  const handleFilterClick = (key: FilterKey) => {
    if (key === filter) return;
    setFilter(key);
  };

  const handleAssign = async (ticketId: string) => {
    if (!user?.userId) return;
    await assignQueueTicket(ticketId, user.userId, setAssigning, setItems, navigate);
  };

  const handleAutoDispatch = async () => {
    if (!user?.userId) return;
    await autoDispatchQueueTicket(user.userId, items, setAssigning, navigate);
  };

  const filteredItems = useMemo(() => 
    filterAndSortQueueItems(items, search, channelFilter, priorityFilter, sortBy),
    [items, search, channelFilter, priorityFilter, sortBy]
  );

  return {
    items,
    stats,
    queueCount,
    loading,
    error,
    assigning,
    filter,
    search,
    channelFilter,
    priorityFilter,
    sortBy,
    showAdvancedFilters,
    setSearch,
    setChannelFilter,
    setPriorityFilter,
    setSortBy,
    setShowAdvancedFilters,
    handleRefresh,
    handleFilterClick,
    handleAssign,
    handleAutoDispatch,
    filteredItems,
    navigate
  };
}

interface QueueHeaderProps {
  filter: FilterKey;
  subtitle: string;
  loading: boolean;
  onRefresh: () => void;
}

function QueueHeader({ subtitle, loading, onRefresh }: Readonly<QueueHeaderProps>) {
  return (
    <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
      <div>
        <h1 className="text-2xl font-extrabold tracking-tight text-gradient-title">
          Console de Files d'Attente
        </h1>
        <p className="text-xs text-txt-3 font-mono mt-1 flex items-center gap-1.5">
          <span className="w-1.5 h-1.5 rounded-full bg-indigo-500 animate-pulse" />
          {subtitle}
        </p>
      </div>
      
      <div className="flex items-center gap-2">
        <motion.button 
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.98 }}
          onClick={onRefresh} 
          className="btn-ghost h-9 px-4 rounded-xl text-xs flex items-center gap-2 cursor-pointer border border-border bg-[var(--surface)] hover:bg-[var(--elevated)] shadow-sm font-medium" 
          disabled={loading}
        >
          <RefreshCw size={12} className={loading ? 'animate-spin text-indigo-400' : 'text-txt-3'} /> 
          Actualiser
        </motion.button>
      </div>
    </div>
  );
}

interface QueueFilterItem {
  key: FilterKey;
  label: string;
  icon: React.ReactNode;
  color: string;
  activeColor: string;
  value: number | null;
}

interface QueueKpiSectionProps {
  loading: boolean;
  stats: TicketStats | null;
  filters: QueueFilterItem[];
  filter: FilterKey;
  onFilterClick: (key: FilterKey) => void;
}

function QueueKpiSection({ loading, stats, filters, filter, onFilterClick }: Readonly<QueueKpiSectionProps>) {
  if (loading && !stats) {
    return (
      <div className="grid grid-cols-2 lg:grid-cols-5 gap-4">
        {Array.from({ length: 5 }, (_, idx) => `skel-queue-kpi-${idx}`).map((key) => <KpiSkeleton key={key} />)}
      </div>
    );
  }

  if (!stats) return null;

  return (
    <div className="grid grid-cols-2 lg:grid-cols-5 gap-4">
      {filters.map((f) => {
        const isActive = filter === f.key;
        return (
          <KpiCard
            key={f.key}
            f={f}
            isActive={isActive}
            onClick={() => onFilterClick(f.key)}
          />
        );
      })}
    </div>
  );
}

interface QueueTableSectionProps {
  filter: FilterKey;
  items: DisplayItem[];
  filteredItems: DisplayItem[];
  loading: boolean;
  assigning: string | null;
  search: string;
  setSearch: (s: string) => void;
  channelFilter: string;
  setChannelFilter: (c: string) => void;
  priorityFilter: number | 'ALL';
  setPriorityFilter: (p: number | 'ALL') => void;
  sortBy: SortOption;
  setSortBy: (s: SortOption) => void;
  showAdvancedFilters: boolean;
  setShowAdvancedFilters: (b: boolean) => void;
  handleAutoDispatch: () => void;
  handleAssign: (ticketId: string) => void;
  navigate: (path: string) => void;
}

interface IncidentListProps {
  loading: boolean;
  filteredItems: DisplayItem[];
  filter: FilterKey;
  assigning: string | null;
  handleAssign: (ticketId: string) => void;
  navigate: (path: string) => void;
}

function IncidentList({
  loading,
  filteredItems,
  filter,
  assigning,
  handleAssign,
  navigate,
}: Readonly<IncidentListProps>) {
  if (loading && filteredItems.length === 0) {
    return (
      <div className="p-5 space-y-3">
        {Array.from({ length: 5 }, (_, idx) => `skel-queue-row-${idx}`).map((key) => (
          <div key={key} className="h-16 bg-[var(--muted)]/40 border border-border/40 rounded-2xl animate-pulse" />
        ))}
      </div>
    );
  }

  if (filteredItems.length === 0) {
    return (
      <motion.div
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        exit={{ opacity: 0, y: -10 }}
        className="py-12"
      >
        <EmptyState
          icon={filter === 'QUEUE' ? 'check' : 'search'}
          title={filter === 'QUEUE' ? 'File d\'attente vide' : 'Aucun résultat'}
          subtitle={filter === 'QUEUE'
            ? 'Incroyable ! Tous les tickets en attente ont été pris en charge.'
            : 'Aucun ticket ne correspond à vos critères de recherche ou filtres avancés.'
          }
        />
      </motion.div>
    );
  }

  return (
    <>
      {filteredItems.map((t, idx) => (
        <TicketRow
          key={t.ticketId}
          t={t}
          idx={idx}
          filter={filter}
          assigning={assigning}
          onAssign={handleAssign}
          onNavigate={(id) => navigate(`/tickets/${id}`)}
        />
      ))}
    </>
  );
}

function QueueTableSection({
  filter,
  items,
  filteredItems,
  loading,
  assigning,
  search,
  setSearch,
  channelFilter,
  setChannelFilter,
  priorityFilter,
  setPriorityFilter,
  sortBy,
  setSortBy,
  showAdvancedFilters,
  setShowAdvancedFilters,
  handleAutoDispatch,
  handleAssign,
  navigate,
}: Readonly<QueueTableSectionProps>) {
  return (
    <div className="card-aurora rounded-2xl border border-border bg-gradient-to-b from-[var(--surface)] to-[var(--elevated)] overflow-hidden shadow-xl">
      <div className="px-5 py-4 border-b border-border/80 flex items-center justify-between bg-black/5 flex-wrap gap-3">
        <div className="font-mono text-[10px] text-txt-3 uppercase tracking-widest font-extrabold flex items-center gap-1.5">
          <ClipboardList size={11} className="text-indigo-400" />
          Liste des incidents
        </div>

        <div className="flex items-center gap-2">
          {(filter === 'QUEUE' || filter === 'NOUVEAU') && items.length > 0 && (
            <motion.button
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
              onClick={handleAutoDispatch}
              disabled={assigning !== null}
              className="btn-cta h-8 px-4 rounded-xl text-[10.5px] font-mono font-extrabold uppercase tracking-widest flex items-center gap-1.5 cursor-pointer text-white disabled:opacity-50"
            >
              <Bot size={13} className={assigning ? 'animate-spin text-white' : 'animate-pulse text-white'} />
              Auto-Dispatch (Suivant)
            </motion.button>
          )}
          
          <span className="font-mono text-[9px] text-txt-3 bg-[var(--surface)] px-2.5 py-1 border border-border rounded-xl shadow-sm">
            {filteredItems.length} affiché{filteredItems.length > 1 ? 's' : ''} / {items.length} total
          </span>
        </div>
      </div>

      <AdvancedFiltersPanel
        search={search}
        setSearch={setSearch}
        channelFilter={channelFilter}
        setChannelFilter={setChannelFilter}
        priorityFilter={priorityFilter}
        setPriorityFilter={setPriorityFilter}
        sortBy={sortBy}
        setSortBy={setSortBy}
        showAdvancedFilters={showAdvancedFilters}
        setShowAdvancedFilters={setShowAdvancedFilters}
      />

      <div className="divide-y divide-border/60">
        <AnimatePresence mode="popLayout">
          <IncidentList
            loading={loading}
            filteredItems={filteredItems}
            filter={filter}
            assigning={assigning}
            handleAssign={handleAssign}
            navigate={navigate}
          />
        </AnimatePresence>
      </div>
    </div>
  );
}

export default function QueuePage() {
  const {
    items,
    stats,
    queueCount,
    loading,
    error,
    assigning,
    filter,
    search,
    channelFilter,
    priorityFilter,
    sortBy,
    showAdvancedFilters,
    setSearch,
    setChannelFilter,
    setPriorityFilter,
    setSortBy,
    setShowAdvancedFilters,
    handleRefresh,
    handleFilterClick,
    handleAssign,
    handleAutoDispatch,
    filteredItems,
    navigate
  } = useQueueState();

  const filters: { key: FilterKey; label: string; icon: React.ReactNode; color: string; activeColor: string; value: number | null }[] = [
    { key: 'QUEUE',    label: 'File d\'attente', icon: <ClipboardList size={14} />, color: 'text-indigo-400',  activeColor: 'bg-indigo-500/10 border-indigo-500/30 text-indigo-400', value: queueCount },
    { key: 'NOUVEAU',  label: 'Nouveaux',        icon: <PlusCircle size={14} />, color: 'text-blue-400',    activeColor: 'bg-blue-500/10 border-blue-500/30 text-blue-400',      value: stats?.nouveau ?? null },
    { key: 'EN_COURS', label: 'En cours',         icon: <RotateCw size={14} />, color: 'text-amber-400',   activeColor: 'bg-amber-500/10 border-amber-500/30 text-amber-400',   value: stats?.enCours ?? null },
    { key: 'RESOLU',   label: 'Résolus',          icon: <CheckCircle2 size={14} />, color: 'text-emerald-400', activeColor: 'bg-emerald-500/10 border-emerald-500/30 text-emerald-400', value: stats?.resolu ?? null },
    { key: 'FERME',    label: 'Fermés',           icon: <FolderClosed size={14} />, color: 'text-txt-3',       activeColor: 'bg-[var(--muted)] border-border text-txt-2',  value: stats?.ferme ?? null },
  ];

  const subtitleMap: Record<FilterKey, string> = {
    QUEUE: `${filteredItems.length} ticket${filteredItems.length > 1 ? 's' : ''} en attente de traitement`,
    NOUVEAU: `${filteredItems.length} nouveau${filteredItems.length > 1 ? 'x' : ''} ticket${filteredItems.length > 1 ? 's' : ''} disponible${filteredItems.length > 1 ? 's' : ''}`,
    EN_COURS: `${filteredItems.length} ticket${filteredItems.length > 1 ? 's' : ''} en cours d'instruction`,
    RESOLU: `${filteredItems.length} ticket${filteredItems.length > 1 ? 's' : ''} résolu${filteredItems.length > 1 ? 's' : ''}`,
    FERME: `${filteredItems.length} ticket${filteredItems.length > 1 ? 's' : ''} clos définitivement`,
  };

  if (error) return <ErrorState message={error} onRetry={handleRefresh} />;

  return (
    <div className="flex flex-col gap-6 max-w-[1600px] mx-auto w-full px-1 py-2">
      <QueueHeader
        filter={filter}
        subtitle={subtitleMap[filter]}
        loading={loading}
        onRefresh={handleRefresh}
      />

      <QueueKpiSection
        loading={loading}
        stats={stats}
        filters={filters}
        filter={filter}
        onFilterClick={handleFilterClick}
      />

      <QueueTableSection
        filter={filter}
        items={items}
        filteredItems={filteredItems}
        loading={loading}
        assigning={assigning}
        search={search}
        setSearch={setSearch}
        channelFilter={channelFilter}
        setChannelFilter={setChannelFilter}
        priorityFilter={priorityFilter}
        setPriorityFilter={setPriorityFilter}
        sortBy={sortBy}
        setSortBy={setSortBy}
        showAdvancedFilters={showAdvancedFilters}
        setShowAdvancedFilters={setShowAdvancedFilters}
        handleAutoDispatch={handleAutoDispatch}
        handleAssign={handleAssign}
        navigate={navigate}
      />
    </div>
  );
}
