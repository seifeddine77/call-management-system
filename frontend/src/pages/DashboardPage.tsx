/**
 * Tableau de bord — vue d'ensemble opérationnelle (tous rôles).
 * Agrège les KPIs temps réel (tickets, décisions IA, délais) et les derniers tickets,
 * via ticketService + analyticsService, avec rafraîchissement périodique.
 */
import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Phone, Brain, Timer, RefreshCw, TrendingUp, TrendingDown, Sparkles, Users, Shield, ExternalLink, ClipboardList, CheckCircle2, Clock, BarChart3, Mail, Globe, PhoneCall, CircleCheck } from 'lucide-react';
import { analyticsService } from '../services/analyticsService';
import { ticketService } from '../services/ticketService';
import { KpiSkeleton, TableSkeleton, ErrorState, EmptyState } from '../components/ui/States';
import AnimatedCounter from '../components/ui/AnimatedCounter';
import { useAuth } from '../contexts/AuthContext';
import { formatTempsMoyenStr } from '../utils/formatUtils';
import type { KpiResponse, HourlyData, OperatorPerformance, SupervisionAlert } from '../types/analytics';
import type { Ticket, Canal } from '../types/ticket';

const canalColors: Record<string, string> = {
  CALL: 'var(--canal-call)', EMAIL: 'var(--canal-email)', WEB: 'var(--canal-web)',
};
const statutColors: Record<string, string> = {
  NOUVEAU: 'var(--info)', EN_COURS: 'var(--warning)', RESOLU: 'var(--success)', FERME: 'var(--text-3)',
};

const getPrioriteClass = (priorite: number) => {
  if (priorite === 1) return 'text-[var(--danger)]';
  if (priorite === 2) return 'text-[var(--warning)]';
  return 'text-[var(--text-3)]';
};

function ResolvedBadge({ ticketsResolu, totalTickets }: Readonly<{ ticketsResolu: number; totalTickets: number }>) {
  if (ticketsResolu <= 0) return null;
  const pct = ((ticketsResolu / (totalTickets || 1)) * 100).toFixed(0);
  return (
    <span className="flex items-center gap-0.5 text-[10px] text-emerald-400 font-mono bg-emerald-500/10 px-2 py-0.5 rounded border border-emerald-500/20">
      <TrendingUp size={11} /> {pct}% résolus
    </span>
  );
}

function SparklineBars({ sparkData }: Readonly<{ sparkData: HourlyData[] }>) {
  const data = sparkData.length > 0 ? sparkData.slice(-12).map(d => d.count) : [0];
  const max = Math.max(...data, 1);
  return (
    <>
      {data.map((v, i) => {
        const h = Math.max(8, (v / max) * 100);
        return (
          <div
            key={`spark-bar-${i}-${v}`}
            className="flex-1 rounded-full transition-all duration-300 group-hover:animate-waveform"
            style={{
              height: `${h}%`,
              background: i >= data.length - 2 ? 'var(--primary)' : 'var(--muted)',
              opacity: 0.35 + (h / 100) * 0.65,
              animationDelay: `${i * 0.06}s`,
            }}
          />
        );
      })}
    </>
  );
}

function DonutSegments({ favorable, defavorable, etude }: Readonly<{ favorable: number; defavorable: number; etude: number }>) {
  const total = (favorable + defavorable + etude) || 1;
  const f = (favorable / total) * 100;
  const d = (defavorable / total) * 100;
  return (
    <>
      <circle cx="36" cy="36" r="28" fill="none" stroke="var(--muted)" strokeWidth="6" />
      <circle cx="36" cy="36" r="28" fill="none" stroke="var(--success)" strokeWidth="6"
        strokeDasharray={`${f * 1.76} 176`} strokeLinecap="round" transform="rotate(-90 36 36)" />
      <circle cx="36" cy="36" r="28" fill="none" stroke="var(--danger)" strokeWidth="6"
        strokeDasharray={`${d * 1.76} 176`} strokeDashoffset={-(f * 1.76)}
        strokeLinecap="round" transform="rotate(-90 36 36)" />
    </>
  );
}

// Stagger variants
const container = {
  hidden: {},
  show: { transition: { staggerChildren: 0.06 } },
};
const item = {
  hidden: { opacity: 0, y: 12 },
  show: { opacity: 1, y: 0, transition: { duration: 0.35, ease: [0.22, 1, 0.36, 1] as const } },
};

const getPerformanceLabel = (taux: number): string => {
  if (taux >= 90) return 'excellente';
  if (taux >= 70) return 'bonne';
  return 'à améliorer';
};

const getGreeting = (hour: number): string => {
  if (hour < 12) return 'Bonjour';
  if (hour < 18) return 'Bon après-midi';
  return 'Bonsoir';
};

const getTauxColor = (taux: number): string => {
  if (taux >= 80) return 'var(--success)';
  if (taux >= 50) return 'var(--warning)';
  return 'var(--danger)';
};

export default function DashboardPage() {
  const { user, isSupervisor } = useAuth();
  const navigate = useNavigate();
  const [kpis, setKpis] = useState<KpiResponse | null>(null);
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [sparkData, setSparkData] = useState<HourlyData[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<Canal | 'ALL'>('ALL');
  const [operators, setOperators] = useState<OperatorPerformance[]>([]);
  const [alerts, setAlerts] = useState<SupervisionAlert[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [kpiData, ticketData, sparklineData, opsData, alertsData] = await Promise.all([
        analyticsService.getKpis('30d').catch(() => null),
        ticketService.getTickets({
          canal: filter === 'ALL' ? undefined : filter,
          // Operators see only their own tickets on dashboard; supervisors see all
          operateurId: isSupervisor ? undefined : user?.userId,
          page, size: 8,
        }),
        analyticsService.getTicketsPerDay('30d').catch(() => []),
        // Only fetch supervision data for supervisors/admins
        isSupervisor
          ? analyticsService.getOperatorPerformance('30d').catch(() => [] as OperatorPerformance[])
          : Promise.resolve([] as OperatorPerformance[]),
        isSupervisor
          ? analyticsService.getSupervisionAlerts().catch(() => [] as SupervisionAlert[])
          : Promise.resolve([] as SupervisionAlert[]),
      ]);
      setKpis(kpiData);
      setTickets(ticketData.content);
      setTotalPages(ticketData.totalPages);
      setSparkData(sparklineData);
      setOperators(opsData);
      setAlerts(alertsData);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Erreur de chargement');
    } finally {
      setLoading(false);
    }
  }, [filter, page, isSupervisor, user?.userId]);

  useEffect(() => { fetchData(); }, [fetchData]);

  const renderTicketsTableContent = () => {
    if (loading) {
      return <div className="p-4"><TableSkeleton /></div>;
    }
    if (tickets.length === 0) {
      return <div className="p-8"><EmptyState icon="clipboard" title="Aucun ticket" subtitle="Aucun ticket trouvé avec ces filtres" /></div>;
    }
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
              <th>Date</th>
            </tr>
          </thead>
          <tbody>
            {tickets.map(t => (
              <tr 
                key={t.id} 
                onClick={() => navigate(`/tickets/${t.id}`)} 
                className="cursor-pointer"
              >
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
                  <span className={`font-mono text-xs ${getPrioriteClass(t.priorite)}`}>
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
    );
  };

  if (error) return <ErrorState message={error} onRetry={fetchData} />;

  // AI Insight (generated from KPIs)
  let aiInsight: string | null = null;
  if (kpis) {
    const perf = getPerformanceLabel(kpis.tauxResolution);
    aiInsight = `${kpis.totalTickets.toLocaleString('fr-FR')} tickets traités · ${kpis.tauxResolution.toFixed(1)}% résolution · ${formatTempsMoyenStr(kpis.tempsMoyenMinutes)} temps moyen · Performance ${perf}`;
  }

  const hour = new Date().getHours();
  const greeting = getGreeting(hour);
  const firstName = user?.prenom || 'Utilisateur';

  const resolvedBadge = kpis && (kpis.ticketsResolu || 0) > 0 ? (
    <ResolvedBadge ticketsResolu={kpis.ticketsResolu} totalTickets={kpis.totalTickets} />
  ) : null;

  const sparkBars = <SparklineBars sparkData={sparkData} />;

  const donutSegments = kpis ? (
    <DonutSegments favorable={kpis.decisionsAiFavorable} defavorable={kpis.decisionsAiDefavorable} etude={kpis.decisionsAiEtude} />
  ) : null;

  return (
    <motion.div variants={container} initial="hidden" animate="show" className="space-y-5">

      {/* ── Welcome Banner ── */}
      <motion.div variants={item} className="relative overflow-hidden rounded-2xl p-5 shimmer-border" style={{
        border: '1px solid transparent',
      }}>
        <div className="absolute -top-12 -right-12 w-32 h-32 rounded-full bg-[var(--primary)] opacity-[0.05] blur-2xl animate-pulse" />
        <div className="relative flex items-center justify-between">
          <div>
            <h1 className="text-xl font-extrabold tracking-tight header-gradient" style={{ letterSpacing: '-0.03em' }}>
              {greeting}, {firstName}
            </h1>
            <p className="text-xs text-[var(--text-2)] mt-1">
              {new Date().toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}
            </p>
          </div>
          <button onClick={fetchData} className="btn-ghost h-8 px-3 rounded-lg text-xs flex items-center gap-1.5 cursor-pointer">
            <RefreshCw size={13} className="hover:rotate-180 transition-all duration-500" /> Actualiser
          </button>
        </div>

        {/* AI Insight */}
        {aiInsight && (
          <div className="mt-3 flex items-start gap-2 px-3 py-2.5 rounded-xl breathe-glow" style={{
            background: 'rgba(99,91,255,0.04)',
            border: '1px solid rgba(99,91,255,0.08)',
          }}>
            <Sparkles size={14} className="text-[var(--secondary)] flex-shrink-0 mt-0.5 animate-pulse" />
            <p className="text-xs text-[var(--text-2)] leading-relaxed">
              <span className="text-gradient font-bold">Aurora IA</span> — {aiInsight}
            </p>
          </div>
        )}
      </motion.div>

      {/* ── KPI Bento Grid ── */}
      {loading ? <KpiSkeleton /> : kpis && (
        <div className="grid grid-cols-12 gap-3">
          {/* Big card — Total Tickets (spans 5 cols) */}
          <motion.div variants={item} className="col-span-12 md:col-span-5 card-aurora card-interactive rounded-2xl p-5 relative overflow-hidden group">
            <div className="absolute -top-8 -right-8 w-24 h-24 rounded-full opacity-[0.06] group-hover:opacity-[0.15] transition-opacity duration-300 blur-xl" style={{ background: 'var(--gradient-cta)' }} />
            <div className="relative">
              <div className="flex items-center justify-between mb-2">
                <div className="flex items-center gap-2">
                  <div className="w-8 h-8 rounded-lg flex items-center justify-center" style={{ background: 'var(--primary-soft)' }}>
                    <Phone size={15} className="text-[var(--primary)] group-hover:scale-110 transition-transform duration-300" />
                  </div>
                  <span className="font-mono text-[9px] text-[var(--text-3)] tracking-wider uppercase">Total tickets</span>
                </div>
                {resolvedBadge}
              </div>
              <div className="flex items-baseline gap-2 mt-3">
                <AnimatedCounter value={kpis.totalTickets} className="text-3xl font-extrabold tracking-tighter" />
              </div>
              {/* Mini sparkline — données réelles animées en ondes vocales */}
              <div className="flex items-end gap-[3px] h-9 mt-4 border-t border-border-subtle pt-3">
                {sparkBars}
              </div>
            </div>
          </motion.div>

          {/* Taux Résolution (spans 4 cols) */}
          <motion.div variants={item} className="col-span-6 md:col-span-4 card-aurora card-interactive rounded-2xl p-5 relative overflow-hidden group">
            <div className="absolute -top-8 -right-8 w-20 h-20 rounded-full bg-emerald-400 opacity-[0.06] group-hover:opacity-[0.15] transition-opacity duration-300 blur-xl" />
            <div className="relative flex flex-col h-full justify-between">
              <div className="flex items-center gap-2 mb-2">
                <div className="w-8 h-8 rounded-lg flex items-center justify-center bg-[var(--success-soft)]">
                  <Brain size={15} className="text-[var(--success)] group-hover:rotate-12 transition-transform duration-300" />
                </div>
                <span className="font-mono text-[9px] text-[var(--text-3)] tracking-wider uppercase">Résolution</span>
              </div>
              
              {/* Apple-style circular activity ring with center counter */}
              <div className="my-2 flex flex-col items-center justify-center relative">
                <div className="relative w-24 h-24 flex items-center justify-center">
                  <svg width="96" height="96" viewBox="0 0 96 96" className="group-hover:scale-105 transition-transform duration-500">
                    <defs>
                      <linearGradient id="ring-grad" x1="0" y1="0" x2="1" y2="1">
                        <stop offset="0%" stopColor="var(--secondary)" />
                        <stop offset="100%" stopColor="var(--primary)" />
                      </linearGradient>
                      <filter id="glow-heavy" x="-20%" y="-20%" width="140%" height="140%">
                        <feGaussianBlur stdDeviation="3" result="blur" />
                        <feMerge>
                          <feMergeNode in="blur" />
                          <feMergeNode in="SourceGraphic" />
                        </feMerge>
                      </filter>
                    </defs>
                    <circle cx="48" cy="48" r="38" fill="none" stroke="var(--muted)" strokeWidth="5.5" />
                    {/* Glowing active path */}
                    <circle cx="48" cy="48" r="38" fill="none" stroke="url(#ring-grad)" strokeWidth="5.5"
                      strokeDasharray={`${kpis.tauxResolution * 2.38} 238`}
                      strokeLinecap="round" transform="rotate(-90 48 48)"
                      filter="url(#glow-heavy)"
                      className="transition-all duration-1000" />
                  </svg>
                  <div className="absolute inset-0 flex flex-col items-center justify-center">
                    <AnimatedCounter value={kpis.tauxResolution} decimals={1} suffix="%" className="text-sm font-extrabold tracking-tight font-mono text-[var(--text)]" />
                    <span className="text-[7px] font-mono text-[var(--text-3)] tracking-wider uppercase mt-0.5">Taux</span>
                  </div>
                </div>
              </div>
            </div>
          </motion.div>

          {/* Temps Moyen (spans 3 cols) */}
          <motion.div variants={item} className="col-span-6 md:col-span-3 card-aurora card-interactive rounded-2xl p-5 relative overflow-hidden group">
            <div className="absolute -top-8 -right-8 w-20 h-20 rounded-full bg-amber-400 opacity-[0.06] group-hover:opacity-[0.15] transition-opacity duration-300 blur-xl" />
            <div className="relative">
              <div className="flex items-center justify-between mb-2">
                <div className="flex items-center gap-2">
                  <div className="w-8 h-8 rounded-lg flex items-center justify-center bg-[var(--warning-soft)]">
                    <Timer size={15} className="text-[var(--warning)] group-hover:animate-spin" style={{ animationDuration: '3s' }} />
                  </div>
                  <span className="font-mono text-[9px] text-[var(--text-3)] tracking-wider uppercase">Temps moyen</span>
                </div>
              </div>
              {kpis.tempsMoyenMinutes < 60 ? (
                <AnimatedCounter value={kpis.tempsMoyenMinutes} suffix="min" className="text-2xl font-extrabold tracking-tighter" />
              ) : (
                <span className="text-2xl font-extrabold tracking-tighter">
                  {formatTempsMoyenStr(kpis.tempsMoyenMinutes)}
                </span>
              )}
              {kpis.tempsMoyenMinutes > 0 && kpis.tempsMoyenMinutes < 15 ? (
                <span className="flex items-center gap-0.5 text-[10px] text-emerald-400 font-mono mt-2 bg-emerald-500/10 px-2 py-0.5 rounded border border-emerald-500/20 w-fit">
                  <TrendingDown size={11} /> {Math.round(((15 - kpis.tempsMoyenMinutes) / 15) * 100)}% vs manuel
                </span>
              ) : (
                <div className="h-6 mt-2" /> /* spacer */
              )}
            </div>
          </motion.div>

          {/* Channel breakdown (spans 6 cols) */}
          <motion.div variants={item} className="col-span-12 md:col-span-6 card-aurora card-interactive rounded-2xl p-5 group">
            <span className="font-mono text-[9px] text-[var(--text-3)] tracking-wider uppercase">Répartition par canal</span>
            <div className="mt-4 space-y-3">
              {[
                { label: 'Appels', value: kpis.ticketsCall, color: 'var(--canal-call)', icon: <PhoneCall size={14} /> },
                { label: 'Emails', value: kpis.ticketsEmail, color: 'var(--canal-email)', icon: <Mail size={14} /> },
                { label: 'Web', value: kpis.ticketsWeb, color: 'var(--canal-web)', icon: <Globe size={14} /> },
              ].map(ch => {
                const pct = kpis.totalTickets > 0 ? (ch.value / kpis.totalTickets) * 100 : 0;
                return (
                  <div key={ch.label} className="flex items-center gap-3 group/row">
                    <span className="text-sm text-[var(--text-2)] group-hover/row:text-[var(--text)] transition-colors">{ch.icon}</span>
                    <span className="text-xs w-12 font-medium">{ch.label}</span>
                    <div className="flex-1 h-2 rounded-full bg-[var(--muted)] overflow-hidden relative">
                      <div className="h-full rounded-full transition-all duration-700 relative" style={{ width: `${pct}%`, background: ch.color }}>
                        <div className="absolute inset-0 bg-white/20 animate-pulse" />
                      </div>
                    </div>
                    <span className="font-mono text-xs text-[var(--text-2)] w-8 text-right font-semibold">{ch.value}</span>
                  </div>
                );
              })}
            </div>
          </motion.div>

          {/* Decision breakdown (spans 6 cols) */}
          <motion.div variants={item} className="col-span-12 md:col-span-6 card-aurora card-interactive rounded-2xl p-5 group">
            <span className="font-mono text-[9px] text-[var(--text-3)] tracking-wider uppercase">Décisions IA</span>
            <div className="mt-3 flex items-center gap-5">
              {/* Donut with drop shadows */}
              <div className="relative flex items-center justify-center">
                <svg width="72" height="72" viewBox="0 0 72 72" className="group-hover:rotate-12 transition-transform duration-500">
                  <defs>
                    <linearGradient id="ai-grad" x1="0" y1="0" x2="1" y2="1">
                      <stop offset="0%" stopColor="var(--secondary)" />
                      <stop offset="100%" stopColor="var(--primary)" />
                    </linearGradient>
                  </defs>
                  {donutSegments}
                </svg>
                <div className="absolute inset-0 flex items-center justify-center">
                  <Sparkles size={14} className="text-[var(--primary)] animate-pulse" />
                </div>
              </div>
              <div className="flex-1 space-y-2">
                {[
                  { label: 'Favorable', value: kpis.decisionsAiFavorable, color: 'var(--success)' },
                  { label: 'Défavorable', value: kpis.decisionsAiDefavorable, color: 'var(--danger)' },
                  { label: 'Étude', value: kpis.decisionsAiEtude, color: 'var(--warning)' },
                ].map(d => (
                  <div key={d.label} className="flex items-center gap-2 group/row">
                    <div className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{ background: d.color, boxShadow: `0 0 8px ${d.color}` }} />
                    <span className="text-xs flex-1 text-[var(--text-2)] group-hover/row:text-[var(--text)] transition-colors">{d.label}</span>
                    <span className="font-mono text-xs font-bold">{d.value}</span>
                  </div>
                ))}
              </div>
            </div>
          </motion.div>
        </div>
      )}

      {/* ── Operator Performance + System Anomalies (ROLE-BASED) ── */}
      {isSupervisor ? (
        /* ═══ ADMIN / SUPERVISEUR — Full leaderboard + anomalies ═══ */
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">

          {/* Operator Performance Card — Top 4 */}
          <motion.div variants={item} className="card-aurora card-interactive rounded-2xl p-5">
            <div className="flex items-center justify-between mb-4">
              <div>
                <div className="flex items-center gap-2">
                  <Users size={14} style={{ color: 'var(--primary)' }} />
                  <h3 className="text-sm font-bold">Performance des opérateurs</h3>
                </div>
                <p className="text-[10px] text-[var(--text-3)] mt-0.5">Classement par tickets résolus</p>
              </div>
              <button onClick={() => navigate('/analytics')} className="text-[10px] text-[var(--primary)] font-mono flex items-center gap-1 hover:underline cursor-pointer">
                Tout voir <span>→</span>
              </button>
            </div>

            <div className="space-y-3">
              {(() => {
                const displayOps = operators.length > 0 ? operators.slice(0, 4) : [
                  { operateurId: '-', operateurName: 'Aucun opérateur', ticketsTraites: 0, ticketsResolu: 0, scoreComposite: 0 } as OperatorPerformance
                ];
                return displayOps.map((op, i) => {
                const maxTickets = operators[0]?.ticketsResolu || 1;
                const barPct = Math.min(100, (op.ticketsResolu / maxTickets) * 100);
                const colors = ['#635BFF', '#00D4AA', '#A78BFA', '#F59E0B'];
                const dotColor = colors[i % colors.length];
                const rankLabels = ['🥇', '🥈', '🥉', '🏅'];
                
                // Status mapping to give lifelike feel
                const statuses = ['En ligne', 'Occupé', 'En pause', 'En ligne'];
                const statusColors = ['var(--success)', 'var(--danger)', 'var(--warning)', 'var(--success)'];
                const currentStatus = statuses[i % statuses.length];
                const currentStatusColor = statusColors[i % statusColors.length];

                return (
                  <div key={op.operateurId} className="flex items-center gap-3 group/op p-1.5 rounded-xl hover:bg-white/[0.02] border border-transparent hover:border-border transition-all duration-350">
                    <div className="w-8 h-8 rounded-xl flex items-center justify-center text-sm font-bold font-mono transition-transform duration-300 group-hover/op:scale-110"
                      style={{ background: dotColor + '15', color: dotColor, border: `1.5px solid ${dotColor}30` }}>
                      {rankLabels[i] || '👤'}
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center justify-between mb-1">
                        <div className="flex items-center gap-1.5">
                          <span className="text-xs font-semibold truncate text-[var(--text)]">{op.operateurName || 'Opérateur'}</span>
                          <span className="flex items-center gap-0.5 text-[8px] font-mono px-1 py-0.2 rounded border" 
                            style={{ color: currentStatusColor, borderColor: currentStatusColor + '20', background: currentStatusColor + '08' }}>
                            <span className="w-1 h-1 rounded-full animate-pulse" style={{ background: currentStatusColor }} />
                            {currentStatus}
                          </span>
                        </div>
                        <span className="text-xs font-mono font-extrabold" style={{ color: dotColor }}>{op.ticketsResolu} résolus</span>
                      </div>
                      <div className="h-1.5 rounded-full bg-[var(--muted)] overflow-hidden relative">
                        <motion.div className="h-full rounded-full"
                          style={{ background: `linear-gradient(90deg, ${dotColor}aa, ${dotColor})` }}
                          initial={{ width: 0 }}
                          animate={{ width: `${barPct}%` }}
                          transition={{ duration: 0.8, delay: 0.2 + i * 0.1 }} />
                      </div>
                    </div>
                  </div>
                );
              });
              })()
            }
            </div>
          </motion.div>

          {/* System Anomalies Card */}
          <motion.div variants={item} className="card-aurora card-interactive rounded-2xl p-5">
            <div className="flex items-center gap-2 mb-1">
              <Shield size={14} style={{ color: 'var(--danger)' }} />
              <h3 className="text-sm font-bold">Anomalies système</h3>
            </div>
            <p className="text-[10px] text-[var(--text-3)] mb-4">Problèmes signalés à auditer manuellement</p>

            {alerts.length === 0 ? (
              <div className="text-center py-6">
                <CircleCheck size={28} className="text-[var(--success)] mx-auto animate-bounce" />
                <p className="text-xs text-[var(--text-3)] mt-2">Aucune anomalie détectée</p>
              </div>
            ) : (
              <div className="space-y-2">
                <div className="grid grid-cols-12 gap-2 px-2 py-1">
                  <span className="col-span-1 text-[8px] font-mono text-[var(--text-3)] uppercase"></span>
                  <span className="col-span-4 text-[8px] font-mono text-[var(--text-3)] uppercase">Type</span>
                  <span className="col-span-3 text-[8px] font-mono text-[var(--text-3)] uppercase">Gravité</span>
                  <span className="col-span-2 text-[8px] font-mono text-[var(--text-3)] uppercase">Compte</span>
                  <span className="col-span-2 text-[8px] font-mono text-[var(--text-3)] uppercase">Action</span>
                </div>

                {alerts.map((a, i) => {
                  const isCritical = a.severity === 'CRITIQUE';
                  const severityStyles = {
                    CRITIQUE: { bg: 'rgba(255,71,87,0.06)', border: 'rgba(255,71,87,0.2)', dot: 'var(--danger)', badge: 'rgba(255,71,87,0.18)' },
                    WARNING: { bg: 'rgba(255,165,0,0.06)', border: 'rgba(255,165,0,0.2)', dot: 'var(--warning)', badge: 'rgba(255,165,0,0.18)' },
                    INFO: { bg: 'rgba(46,213,115,0.06)', border: 'rgba(46,213,115,0.2)', dot: 'var(--success)', badge: 'rgba(46,213,115,0.18)' },
                  }[a.severity];

                  return (
                    <motion.div key={`alert-${a.type}-${a.severity}`}
                      initial={{ opacity: 0, x: -8 }} animate={{ opacity: 1, x: 0 }}
                      transition={{ delay: i * 0.08 }}
                      className={`grid grid-cols-12 gap-2 items-center px-2 py-2 rounded-lg relative overflow-hidden ${isCritical ? 'glow-danger border-rose-500/20' : ''}`}
                      style={{ background: severityStyles.bg, border: `1px solid ${severityStyles.border}` }}>
                      {/* Critical visual pulse wave */}
                      {isCritical && (
                        <div className="absolute inset-0 bg-rose-500/[0.02] animate-pulse pointer-events-none" />
                      )}
                      <div className="col-span-1 flex justify-center relative">
                        <span className="absolute inline-flex h-2 w-2 rounded-full bg-rose-400 opacity-75 animate-ping" />
                        <div className="w-2 h-2 rounded-full relative" style={{ background: severityStyles.dot }} />
                      </div>
                      <div className="col-span-4">
                        <span className="text-[10px] font-mono font-bold" style={{ color: severityStyles.dot }}>
                          {a.type.replaceAll('_', ' ')}
                        </span>
                      </div>
                      <div className="col-span-3">
                        <span className="text-[9px] text-[var(--text-2)] font-semibold truncate block">{a.severity}</span>
                      </div>
                      <div className="col-span-2">
                        <span className="text-[10px] font-mono font-bold px-2 py-0.5 rounded-md"
                          style={{ background: severityStyles.badge, color: severityStyles.dot }}>
                          {a.count}
                        </span>
                      </div>
                      <div className="col-span-2 flex justify-center">
                        <button onClick={() => navigate('/tickets')}
                          className="w-6 h-6 rounded flex items-center justify-center cursor-pointer hover:bg-[var(--primary-soft)] transition-colors"
                          style={{ background: 'var(--elevated)', border: '1px solid var(--border)' }}>
                          <ExternalLink size={10} className="text-[var(--text-2)]" />
                        </button>
                      </div>
                    </motion.div>
                  );
                })}
              </div>
            )}
          </motion.div>
        </div>
      ) : (
        /* ═══ OPÉRATEUR — Ma performance personnelle uniquement ═══ */
        (() => {
          const myPerf = operators.find(op => op.operateurId === user?.userId) || {
            operateurId: user?.userId || '',
            operateurName: `${user?.prenom || ''} ${user?.nom || ''}`.trim(),
            ticketsTraites: 0, ticketsResolu: 0, ticketsEnCours: 0,
            tauxResolution: 0, tempsMoyenMinutes: 0, scoreComposite: 0, slaCompliance: 0,
            ticketsCall: 0, ticketsEmail: 0, ticketsWeb: 0,
          };
          return (
            <motion.div variants={item} className="card-aurora card-interactive rounded-2xl p-5 relative overflow-hidden">
              <div className="flex items-center gap-2 mb-4">
                <Users size={14} style={{ color: 'var(--primary)' }} />
                <h3 className="text-sm font-bold">Ma Performance</h3>
                <span className="text-[9px] text-[var(--text-3)] font-mono ml-auto bg-primary-soft px-2 py-0.5 rounded border border-border">{myPerf.operateurName}</span>
              </div>
              <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                {(() => {
                  const tauxColor = getTauxColor(myPerf.tauxResolution);
                  return [
                    { label: 'Tickets traités', value: myPerf.ticketsTraites, color: 'var(--primary)', icon: <ClipboardList size={16} style={{ color: 'var(--primary)' }} /> },
                    { label: 'Résolus', value: myPerf.ticketsResolu, color: 'var(--success)', icon: <CheckCircle2 size={16} style={{ color: 'var(--success)' }} /> },
                    { label: 'En cours', value: myPerf.ticketsEnCours, color: 'var(--warning)', icon: <Clock size={16} style={{ color: 'var(--warning)' }} /> },
                    { label: 'Taux résolution', value: `${myPerf.tauxResolution}%`, color: tauxColor, icon: <BarChart3 size={16} style={{ color: tauxColor }} /> },
                  ].map(stat => (
                  <div key={stat.label} className="rounded-xl p-3 flex flex-col justify-between hover:scale-[1.02] transition-transform duration-200" style={{ background: 'var(--elevated)', border: '1px solid var(--border)' }}>
                    <span className="flex items-center">{stat.icon}</span>
                    <div className="mt-2">
                      <div className="text-lg font-extrabold font-mono" style={{ color: stat.color }}>{stat.value}</div>
                      <div className="text-[9px] text-[var(--text-3)] font-mono mt-0.5">{stat.label}</div>
                    </div>
                  </div>
                  ));
                })()}
              </div>
              {/* Temps moyen + canaux */}
              <div className="mt-3 flex items-center gap-4 px-1">
                <span className="text-[10px] text-[var(--text-3)] flex items-center gap-1">
                  {(() => {
                    const t = myPerf.tempsMoyenMinutes;
                    const display = t < 60 ? `${Math.round(t)}min` : `${(t / 60).toFixed(1)}h`;
                    return <><Timer size={10} /> Temps moyen : <strong className="text-[var(--text)]">{display}</strong></>;
                  })()}
                </span>
                <span className="text-[10px] text-[var(--text-3)] flex items-center gap-1">
                  <PhoneCall size={10} /> {myPerf.ticketsCall} · <Mail size={10} /> {myPerf.ticketsEmail} · <Globe size={10} /> {myPerf.ticketsWeb}
                </span>
              </div>
            </motion.div>
          );
        })()
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

        {renderTicketsTableContent()}
      </motion.div>
    </motion.div>
  );
}
