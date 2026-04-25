import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Phone, Brain, Timer, RefreshCw, TrendingUp, TrendingDown, Sparkles } from 'lucide-react';
import { analyticsService } from '../services/analyticsService';
import { ticketService } from '../services/ticketService';
import { KpiSkeleton, TableSkeleton, ErrorState, EmptyState } from '../components/ui/States';
import AnimatedCounter from '../components/ui/AnimatedCounter';
import { useAuth } from '../contexts/AuthContext';
import type { KpiResponse, HourlyData } from '../types/analytics';
import type { Ticket, Canal } from '../types/ticket';

const canalColors: Record<string, string> = {
  CALL: 'var(--canal-call)', EMAIL: 'var(--canal-email)', WEB: 'var(--canal-web)',
};
const statutColors: Record<string, string> = {
  NOUVEAU: 'var(--info)', EN_COURS: 'var(--warning)', RESOLU: 'var(--success)', FERME: 'var(--text-3)',
};

// Stagger variants
const container = {
  hidden: {},
  show: { transition: { staggerChildren: 0.06 } },
};
const item = {
  hidden: { opacity: 0, y: 12 },
  show: { opacity: 1, y: 0, transition: { duration: 0.35, ease: [0.22, 1, 0.36, 1] as const } },
};

export default function DashboardPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [kpis, setKpis] = useState<KpiResponse | null>(null);
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [sparkData, setSparkData] = useState<HourlyData[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<Canal | 'ALL'>('ALL');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [kpiData, ticketData, sparklineData] = await Promise.all([
        analyticsService.getKpis('30d').catch(() => null),
        ticketService.getTickets({
          canal: filter === 'ALL' ? undefined : filter as Canal,
          page, size: 8,
        }),
        analyticsService.getTicketsPerDay('30d').catch(() => []),
      ]);
      setKpis(kpiData);
      setTickets(ticketData.content);
      setTotalPages(ticketData.totalPages);
      setSparkData(sparklineData);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Erreur de chargement');
    } finally {
      setLoading(false);
    }
  }, [filter, page]);

  useEffect(() => { fetchData(); }, [fetchData]);

  if (error) return <ErrorState message={error} onRetry={fetchData} />;

  // AI Insight (generated from KPIs)
  const aiInsight = kpis ? (() => {
    const perf = kpis.tauxResolution >= 90 ? 'excellente 🟢' : kpis.tauxResolution >= 70 ? 'bonne 🟡' : 'à améliorer 🔴';
    return `${kpis.totalTickets.toLocaleString('fr-FR')} tickets traités · ${kpis.tauxResolution.toFixed(1)}% résolution · ${kpis.tempsMoyenMinutes.toFixed(0)}min temps moyen · Performance ${perf}`;
  })() : null;

  const hour = new Date().getHours();
  const greeting = hour < 12 ? 'Bonjour' : hour < 18 ? 'Bon après-midi' : 'Bonsoir';
  const firstName = user?.prenom || 'Utilisateur';

  return (
    <motion.div variants={container} initial="hidden" animate="show" className="space-y-5">

      {/* ── Welcome Banner ── */}
      <motion.div variants={item} className="relative overflow-hidden rounded-2xl p-5" style={{
        background: 'linear-gradient(135deg, rgba(99,91,255,0.08) 0%, rgba(0,212,170,0.04) 50%, rgba(167,139,250,0.06) 100%)',
        border: '1px solid var(--border)',
      }}>
        <div className="absolute -top-12 -right-12 w-32 h-32 rounded-full bg-[var(--primary)] opacity-[0.04] blur-2xl" />
        <div className="relative flex items-center justify-between">
          <div>
            <h1 className="text-xl font-extrabold tracking-tight" style={{ letterSpacing: '-0.03em' }}>
              {greeting}, {firstName} 👋
            </h1>
            <p className="text-xs text-[var(--text-2)] mt-1">
              {new Date().toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}
            </p>
          </div>
          <button onClick={fetchData} className="btn-ghost h-8 px-3 rounded-lg text-xs flex items-center gap-1.5 cursor-pointer">
            <RefreshCw size={13} /> Actualiser
          </button>
        </div>

        {/* AI Insight */}
        {aiInsight && (
          <div className="mt-3 flex items-start gap-2 px-3 py-2.5 rounded-xl" style={{
            background: 'rgba(99,91,255,0.05)',
            border: '1px solid rgba(99,91,255,0.08)',
          }}>
            <Sparkles size={14} className="text-[var(--primary)] flex-shrink-0 mt-0.5" />
            <p className="text-xs text-[var(--text-2)] leading-relaxed">
              <span className="text-gradient font-semibold">Aurora IA</span> — {aiInsight}
            </p>
          </div>
        )}
      </motion.div>

      {/* ── KPI Bento Grid ── */}
      {loading ? <KpiSkeleton /> : kpis && (
        <div className="grid grid-cols-12 gap-3">
          {/* Big card — Total Tickets (spans 5 cols) */}
          <motion.div variants={item} className="col-span-12 md:col-span-5 card-aurora rounded-2xl p-5 relative overflow-hidden group hover:-translate-y-0.5 transition-all duration-150">
            <div className="absolute -top-8 -right-8 w-24 h-24 rounded-full opacity-[0.06] group-hover:opacity-[0.12] transition-opacity blur-xl" style={{ background: 'var(--gradient-cta)' }} />
            <div className="relative">
              <div className="flex items-center gap-2 mb-2">
                <div className="w-8 h-8 rounded-lg flex items-center justify-center" style={{ background: 'var(--primary-soft)' }}>
                  <Phone size={15} className="text-[var(--primary)]" />
                </div>
                <span className="font-mono text-[9px] text-[var(--text-3)] tracking-wider uppercase">Total tickets</span>
              </div>
              <div className="flex items-baseline gap-2">
                <AnimatedCounter value={kpis.totalTickets} className="text-3xl font-extrabold tracking-tighter" />
                {(() => {
                  const resolved = kpis.ticketsResolu || 0;
                  const total = kpis.totalTickets || 1;
                  const pct = ((resolved / total) * 100).toFixed(0);
                  return resolved > 0 ? (
                    <span className="flex items-center gap-0.5 text-xs text-emerald-400 font-mono">
                      <TrendingUp size={11} /> {pct}% résolus
                    </span>
                  ) : null;
                })()}
              </div>
              {/* Mini sparkline — données réelles */}
              <div className="flex items-end gap-[2px] h-8 mt-3">
                {(() => {
                  const data = sparkData.length > 0
                    ? sparkData.slice(-12).map(d => d.count)
                    : [0];
                  const max = Math.max(...data, 1);
                  return data.map((v, i) => {
                    const h = Math.max(5, (v / max) * 100);
                    return <div key={i} className="flex-1 rounded-full transition-all" style={{ height: `${h}%`, background: i >= data.length - 2 ? 'var(--primary)' : 'var(--muted)', opacity: 0.3 + (h / 100) * 0.7 }} />;
                  });
                })()}
              </div>
            </div>
          </motion.div>

          {/* Taux Résolution (spans 4 cols) */}
          <motion.div variants={item} className="col-span-6 md:col-span-4 card-aurora rounded-2xl p-5 relative overflow-hidden group hover:-translate-y-0.5 transition-all duration-150">
            <div className="absolute -top-8 -right-8 w-20 h-20 rounded-full bg-emerald-400 opacity-[0.06] group-hover:opacity-[0.12] transition-opacity blur-xl" />
            <div className="relative">
              <div className="flex items-center gap-2 mb-2">
                <div className="w-8 h-8 rounded-lg flex items-center justify-center bg-[var(--success-soft)]">
                  <Brain size={15} className="text-[var(--success)]" />
                </div>
                <span className="font-mono text-[9px] text-[var(--text-3)] tracking-wider uppercase">Résolution</span>
              </div>
              <AnimatedCounter value={kpis.tauxResolution} decimals={1} suffix="%" className="text-2xl font-extrabold tracking-tighter" />
              {/* Score ring */}
              <div className="mt-3 flex justify-center">
                <svg width="64" height="64" viewBox="0 0 64 64">
                  <defs>
                    <linearGradient id="ring-grad" x1="0" y1="0" x2="1" y2="1">
                      <stop offset="0%" stopColor="var(--secondary)" />
                      <stop offset="100%" stopColor="var(--primary)" />
                    </linearGradient>
                  </defs>
                  <circle cx="32" cy="32" r="26" fill="none" stroke="var(--muted)" strokeWidth="4" />
                  <circle cx="32" cy="32" r="26" fill="none" stroke="url(#ring-grad)" strokeWidth="4"
                    strokeDasharray={`${kpis.tauxResolution * 1.63} 163`}
                    strokeLinecap="round" transform="rotate(-90 32 32)"
                    className="transition-all duration-1000" />
                </svg>
              </div>
            </div>
          </motion.div>

          {/* Temps Moyen (spans 3 cols) */}
          <motion.div variants={item} className="col-span-6 md:col-span-3 card-aurora rounded-2xl p-5 relative overflow-hidden group hover:-translate-y-0.5 transition-all duration-150">
            <div className="absolute -top-8 -right-8 w-20 h-20 rounded-full bg-amber-400 opacity-[0.06] group-hover:opacity-[0.12] transition-opacity blur-xl" />
            <div className="relative">
              <div className="flex items-center gap-2 mb-2">
                <div className="w-8 h-8 rounded-lg flex items-center justify-center bg-[var(--warning-soft)]">
                  <Timer size={15} className="text-[var(--warning)]" />
                </div>
                <span className="font-mono text-[9px] text-[var(--text-3)] tracking-wider uppercase">Temps moyen</span>
              </div>
              <AnimatedCounter value={kpis.tempsMoyenMinutes} suffix="min" className="text-2xl font-extrabold tracking-tighter" />
              {kpis.tempsMoyenMinutes > 0 && kpis.tempsMoyenMinutes < 15 && (
                <span className="flex items-center gap-0.5 text-xs text-emerald-400 font-mono mt-1"><TrendingDown size={11} /> {Math.round(((15 - kpis.tempsMoyenMinutes) / 15) * 100)}% vs manuel</span>
              )}
            </div>
          </motion.div>

          {/* Channel breakdown (spans 6 cols) */}
          <motion.div variants={item} className="col-span-12 md:col-span-6 card-aurora rounded-2xl p-5 group hover:-translate-y-0.5 transition-all duration-150">
            <span className="font-mono text-[9px] text-[var(--text-3)] tracking-wider uppercase">Répartition par canal</span>
            <div className="mt-3 space-y-2.5">
              {[
                { label: 'Appels', value: kpis.ticketsCall, color: 'var(--canal-call)', icon: '📞' },
                { label: 'Emails', value: kpis.ticketsEmail, color: 'var(--canal-email)', icon: '📧' },
                { label: 'Web', value: kpis.ticketsWeb, color: 'var(--canal-web)', icon: '🌐' },
              ].map(ch => {
                const pct = kpis.totalTickets > 0 ? (ch.value / kpis.totalTickets) * 100 : 0;
                return (
                  <div key={ch.label} className="flex items-center gap-3">
                    <span className="text-sm">{ch.icon}</span>
                    <span className="text-xs w-12">{ch.label}</span>
                    <div className="flex-1 h-2 rounded-full bg-[var(--muted)] overflow-hidden">
                      <div className="h-full rounded-full transition-all duration-700" style={{ width: `${pct}%`, background: ch.color }} />
                    </div>
                    <span className="font-mono text-xs text-[var(--text-2)] w-8 text-right">{ch.value}</span>
                  </div>
                );
              })}
            </div>
          </motion.div>

          {/* Decision breakdown (spans 6 cols) */}
          <motion.div variants={item} className="col-span-12 md:col-span-6 card-aurora rounded-2xl p-5 group hover:-translate-y-0.5 transition-all duration-150">
            <span className="font-mono text-[9px] text-[var(--text-3)] tracking-wider uppercase">Décisions IA</span>
            <div className="mt-3 flex items-center gap-4">
              {/* Donut */}
              <svg width="72" height="72" viewBox="0 0 72 72">
                <defs>
                  <linearGradient id="ai-grad" x1="0" y1="0" x2="1" y2="1">
                    <stop offset="0%" stopColor="var(--secondary)" />
                    <stop offset="100%" stopColor="var(--primary)" />
                  </linearGradient>
                </defs>
                {(() => {
                  const total = (kpis.decisionsAiFavorable + kpis.decisionsAiDefavorable + kpis.decisionsAiEtude) || 1;
                  const f = (kpis.decisionsAiFavorable / total) * 100;
                  const d = (kpis.decisionsAiDefavorable / total) * 100;
                  return (
                    <>
                      <circle cx="36" cy="36" r="28" fill="none" stroke="var(--muted)" strokeWidth="6" />
                      <circle cx="36" cy="36" r="28" fill="none" stroke="var(--success)" strokeWidth="6"
                        strokeDasharray={`${f * 1.76} 176`} strokeLinecap="round" transform="rotate(-90 36 36)" />
                      <circle cx="36" cy="36" r="28" fill="none" stroke="var(--danger)" strokeWidth="6"
                        strokeDasharray={`${d * 1.76} 176`} strokeDashoffset={`${-f * 1.76}`}
                        strokeLinecap="round" transform="rotate(-90 36 36)" />
                    </>
                  );
                })()}
              </svg>
              <div className="flex-1 space-y-1.5">
                {[
                  { label: 'Favorable', value: kpis.decisionsAiFavorable, color: 'var(--success)' },
                  { label: 'Défavorable', value: kpis.decisionsAiDefavorable, color: 'var(--danger)' },
                  { label: 'Étude', value: kpis.decisionsAiEtude, color: 'var(--warning)' },
                ].map(d => (
                  <div key={d.label} className="flex items-center gap-2">
                    <div className="w-2 h-2 rounded-full flex-shrink-0" style={{ background: d.color }} />
                    <span className="text-xs flex-1">{d.label}</span>
                    <span className="font-mono text-xs font-semibold">{d.value}</span>
                  </div>
                ))}
              </div>
            </div>
          </motion.div>
        </div>
      )}

      {/* ── Tickets Table ── */}
      <motion.div variants={item} className="card-aurora rounded-2xl overflow-hidden">
        {/* Header + filters */}
        <div className="p-4 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2 border-b border-[var(--border)]">
          <div>
            <h3 className="text-sm font-bold">Derniers tickets</h3>
            <p className="font-mono text-[9px] text-[var(--text-3)]">{tickets.length} affichés</p>
          </div>
          <div className="flex gap-1.5 flex-wrap">
            {(['ALL', 'CALL', 'EMAIL', 'WEB'] as const).map(f => (
              <button
                key={f}
                onClick={() => { setFilter(f); setPage(0); }}
                className={`chip cursor-pointer ${filter === f ? 'active' : ''}`}
              >
                {f === 'ALL' ? 'Tous' : f}
              </button>
            ))}
          </div>
        </div>

        {loading ? <div className="p-4"><TableSkeleton /></div> : tickets.length === 0 ? (
          <div className="p-8"><EmptyState icon="📋" title="Aucun ticket" subtitle="Aucun ticket trouvé avec ces filtres" /></div>
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
                  <th>Date</th>
                </tr>
              </thead>
              <tbody>
                {tickets.map(t => (
                  <tr key={t.id} onClick={() => navigate(`/tickets/${t.id}`)} className="cursor-pointer">
                    <td className="font-mono text-xs font-semibold text-[var(--primary)]">{t.numero}</td>
                    <td className="text-xs">{t.clientNom}</td>
                    <td>
                      <span className="badge" style={{ background: `${canalColors[t.canal]}15`, color: canalColors[t.canal] }}>
                        {t.canal}
                      </span>
                    </td>
                    <td>
                      <span className="badge" style={{ background: `${statutColors[t.statut]}15`, color: statutColors[t.statut] }}>
                        <span className="w-1.5 h-1.5 rounded-full inline-block" style={{ background: statutColors[t.statut] }} />
                        {t.statut}
                      </span>
                    </td>
                    <td>
                      <span className={`font-mono text-xs ${t.priorite === 1 ? 'text-[var(--danger)]' : t.priorite === 2 ? 'text-[var(--warning)]' : 'text-[var(--text-3)]'}`}>
                        P{t.priorite}
                      </span>
                    </td>
                    <td className="font-mono text-[11px] text-[var(--text-3)]">
                      {new Date(t.createdAt).toLocaleDateString('fr-FR')}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            </div>
            {/* Pagination */}
            {totalPages > 1 && (
              <div className="p-3 border-t border-[var(--border)] flex items-center justify-center gap-2">
                <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0} className="btn-ghost h-7 px-3 rounded text-xs cursor-pointer disabled:opacity-30">← Précédent</button>
                <span className="font-mono text-[10px] text-[var(--text-3)]">{page + 1} / {totalPages}</span>
                <button onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))} disabled={page >= totalPages - 1} className="btn-ghost h-7 px-3 rounded text-xs cursor-pointer disabled:opacity-30">Suivant →</button>
              </div>
            )}
          </>
        )}
      </motion.div>
    </motion.div>
  );
}
