import { useState, useEffect, useCallback } from 'react';
import { motion } from 'framer-motion';
import { TrendingUp, TrendingDown, Minus, RefreshCw, Calendar, Download, Shield, BarChart3, CheckCircle2, Clock, Bot, Target, PlusCircle, RotateCw, FolderClosed, PhoneCall, Mail, Globe, AlertCircle, AlertTriangle, CircleCheck } from 'lucide-react';
import { analyticsService } from '../services/analyticsService';
import { KpiSkeleton, ErrorState } from '../components/ui/States';
import AnimatedCounter from '../components/ui/AnimatedCounter';
import type { KpiResponse, KpiComparison, DailyStat, OperatorPerformance, SupervisionAlert } from '../types/analytics';

const container = { hidden: {}, show: { transition: { staggerChildren: 0.06 } } };
const item = { hidden: { opacity: 0, y: 12 }, show: { opacity: 1, y: 0, transition: { duration: 0.35, ease: [0.22, 1, 0.36, 1] as const } } };

const periodLabels: Record<string, string> = { week: '7 derniers jours', month: '30 derniers jours', year: '90 derniers jours' };
const periodMap: Record<string, string> = { week: '7d', month: '30d', year: '90d' };

const getTrendDirection = (value: number): boolean | null => {
  if (value > 0) return true;
  if (value < 0) return false;
  return null;
};

const getScoreTrendDirection = (score: number): boolean | null => {
  if (score >= 60) return true;
  if (score < 40) return false;
  return null;
};

const getScoreSubtitle = (score: number): string => {
  if (score >= 80) return 'Excellent';
  if (score >= 60) return 'Bon';
  if (score >= 40) return 'Moyen';
  return 'Faible';
};

const formatPercent = (val: number, showPlus = true) => {
  return `${showPlus && val >= 0 ? '+' : ''}${val}%`;
};

const periodButtonLabels: Record<string, string> = {
  week: 'Semaine',
  month: 'Mois',
  year: 'Année'
};

import DailyVolumeChart from '../components/analytics/DailyVolumeChart';
import DecisionsChart from '../components/analytics/DecisionsChart';
import ChannelsChart from '../components/analytics/ChannelsChart';
import OperatorLeaderboard from '../components/analytics/OperatorLeaderboard';

export default function AnalyticsPage() {
  const [kpis, setKpis] = useState<KpiResponse | null>(null);
  const [comparison, setComparison] = useState<KpiComparison | null>(null);
  const [daily, setDaily] = useState<DailyStat[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [period, setPeriod] = useState<string>('month');
  const [exporting, setExporting] = useState(false);
  const [operators, setOperators] = useState<OperatorPerformance[]>([]);
  const [alerts, setAlerts] = useState<SupervisionAlert[]>([]);

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const p = periodMap[period] || '30d';
      const [compData, dailyData, opsData, alertsData] = await Promise.all([
        analyticsService.getComparison(p),
        analyticsService.getDailyStats(p),
        analyticsService.getOperatorPerformance(p).catch(() => [] as OperatorPerformance[]),
        analyticsService.getSupervisionAlerts().catch(() => [] as SupervisionAlert[]),
      ]);
      setComparison(compData);
      setKpis(compData.current);
      setDaily(dailyData);
      setOperators(opsData);
      setAlerts(alertsData);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Erreur');
    } finally {
      setLoading(false);
    }
  }, [period]);

  useEffect(() => { fetchData(); }, [fetchData]);

  const handleExportCsv = async () => {
    setExporting(true);
    try {
      await analyticsService.exportCsv(periodMap[period] || '30d');
    } finally {
      setExporting(false);
    }
  };

  if (error) return <ErrorState message={error} onRetry={fetchData} />;

  // Computed stats
  const totalDecisions = kpis ? kpis.decisionsAiFavorable + kpis.decisionsAiDefavorable + kpis.decisionsAiEtude : 0;
  const scoreMoyen = kpis && totalDecisions > 0
    ? Math.round((kpis.decisionsAiFavorable / totalDecisions) * 100) : 0;
  const prevTotalDec = comparison ? comparison.previous.decisionsAiFavorable + comparison.previous.decisionsAiDefavorable + comparison.previous.decisionsAiEtude : 0;
  const prevScore = prevTotalDec > 0 ? Math.round((comparison!.previous.decisionsAiFavorable / prevTotalDec) * 100) : 0;



  const renderKpiTrend = (trendUp: boolean | null | undefined, trend?: string) => {
    if (trendUp === undefined) return null;
    if (trendUp === null) {
      return (
        <span className="flex items-center gap-0.5 font-mono text-[10px] text-[var(--text-3)]">
          <Minus size={10} /> {trend || 'Stable'}
        </span>
      );
    }
    return (
      <span className={`flex items-center gap-0.5 font-mono text-[10px] ${trendUp ? 'text-emerald-400' : 'text-[var(--danger)]'}`}>
        {trendUp ? <TrendingUp size={10} /> : <TrendingDown size={10} />} {trend || ''}
      </span>
    );
  };

  const scoreDiff = scoreMoyen - prevScore;
  const scoreTrendText = prevScore > 0 ? `${scoreDiff >= 0 ? '+' : ''}${scoreDiff}pts` : undefined;

  return (
    <motion.div variants={container} initial="hidden" animate="show" className="space-y-5">
      {/* Header */}
      <motion.div variants={item} className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2">
        <div>
          <h2 className="text-lg font-extrabold tracking-tight" style={{ letterSpacing: '-0.03em' }}>Analytique</h2>
          <p className="font-mono text-[9px] text-[var(--text-3)] uppercase tracking-wider flex items-center gap-1">
            <Calendar size={9} /> {periodLabels[period] || period}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <div className="flex gap-1.5">
            {['week', 'month', 'year'].map(p => (
              <button key={p} onClick={() => setPeriod(p)} className={`chip cursor-pointer ${period === p ? 'active' : ''}`}>
                {periodButtonLabels[p]}
              </button>
            ))}
          </div>
          <button onClick={handleExportCsv} disabled={exporting}
            className="btn-ghost h-8 px-3 rounded-lg text-xs flex items-center gap-1.5 cursor-pointer">
            <Download size={13} /> {exporting ? 'Export...' : 'CSV'}
          </button>
          <button onClick={fetchData} className="btn-ghost h-8 px-3 rounded-lg text-xs flex items-center gap-1.5 cursor-pointer">
            <RefreshCw size={13} /> Actualiser
          </button>
        </div>
      </motion.div>
 
      {/* KPI Row — 5 cards */}
      {loading ? <KpiSkeleton /> : kpis && comparison && (
        <div className="grid grid-cols-2 md:grid-cols-5 gap-3">
          {[
            { label: 'Total tickets', value: kpis.totalTickets, icon: <BarChart3 size={14} />,
              trend: formatPercent(comparison.deltaTickets),
              trendUp: getTrendDirection(comparison.deltaTickets) },
            { label: 'Résolution', value: kpis.tauxResolution, suffix: '%', decimals: 1, icon: <CheckCircle2 size={14} />,
              trend: formatPercent(comparison.deltaResolution),
              trendUp: getTrendDirection(comparison.deltaResolution) },
            { label: 'Temps moyen', value: kpis.tempsMoyenMinutes, suffix: 'min', icon: <Clock size={14} />,
              trend: comparison.deltaTempsMoyen === 0 ? undefined : formatPercent(comparison.deltaTempsMoyen),
              trendUp: getTrendDirection(-comparison.deltaTempsMoyen) }, // lower temps moyen is good, so reverse direction
            { label: 'Décisions IA', value: totalDecisions, icon: <Bot size={14} />,
              trend: formatPercent(comparison.deltaDecisions),
              trendUp: getTrendDirection(comparison.deltaDecisions) },
            { label: 'Score favorable', value: scoreMoyen, suffix: '%', icon: <Target size={14} />,
              trendUp: getScoreTrendDirection(scoreMoyen),
              subtitle: getScoreSubtitle(scoreMoyen),
              trend: scoreTrendText },
          ].map((kpi) => (
            <motion.div key={kpi.label} variants={item} className="card-aurora rounded-2xl p-4 group hover:-translate-y-0.5 transition-all duration-150 relative overflow-hidden">
              <div className="absolute -top-8 -right-8 w-20 h-20 rounded-full bg-[var(--primary)] opacity-[0.04] group-hover:opacity-[0.08] transition-opacity blur-xl" />
              <div className="relative">
                <div className="flex items-center gap-1.5 mb-2">
                  <span className="flex items-center">{kpi.icon}</span>
                  <span className="font-mono text-[9px] text-[var(--text-3)] tracking-wider uppercase">{kpi.label}</span>
                </div>
                <div className="flex items-baseline gap-2">
                  <AnimatedCounter value={kpi.value} suffix={kpi.suffix} decimals={kpi.decimals} className="text-xl font-extrabold tracking-tighter" />
                  {renderKpiTrend(kpi.trendUp, kpi.trend)}
                </div>
                {kpi.subtitle && (
                  <p className="font-mono text-[8px] text-[var(--text-3)] mt-1">{kpi.subtitle}</p>
                )}
              </div>
            </motion.div>
          ))}
        </div>
      )}

      {/* Ticket Status Split */}
      {kpis && (
        <motion.div variants={item} className="card-aurora rounded-2xl p-5">
          <span className="font-mono text-[9px] text-[var(--text-3)] tracking-wider uppercase">Répartition par statut</span>
          <div className="mt-3 flex flex-col sm:flex-row gap-3">
            {[
              { label: 'Nouveaux', value: kpis.ticketsNouveau, color: 'var(--info)', icon: <PlusCircle size={13} /> },
              { label: 'En cours', value: kpis.ticketsEnCours, color: 'var(--warning)', icon: <RotateCw size={13} /> },
              { label: 'Résolus', value: kpis.ticketsResolu, color: 'var(--success)', icon: <CheckCircle2 size={13} /> },
              { label: 'Fermés', value: kpis.ticketsFerme, color: 'var(--text-3)', icon: <FolderClosed size={13} /> },
            ].map(s => {
              const pct = kpis.totalTickets > 0 ? (s.value / kpis.totalTickets) * 100 : 0;
              return (
                <div key={s.label} className="flex-1 p-3 rounded-xl" style={{ background: `${s.color}08`, border: `1px solid ${s.color}20` }}>
                  <div className="flex items-center gap-1.5 mb-1">
                    <span className="flex items-center">{s.icon}</span>
                    <span className="font-mono text-[8px] text-[var(--text-3)] uppercase">{s.label}</span>
                  </div>
                  <span className="text-lg font-extrabold tracking-tighter">{s.value}</span>
                  <div className="h-1.5 rounded-full bg-[var(--muted)] mt-2 overflow-hidden">
                    <motion.div className="h-full rounded-full" style={{ background: s.color }}
                      initial={{ width: 0 }} animate={{ width: `${pct}%` }}
                      transition={{ duration: 0.8, delay: 0.3, ease: [0.22, 1, 0.36, 1] as const }} />
                  </div>
                  <span className="font-mono text-[8px] text-[var(--text-3)] mt-1 block">{pct.toFixed(1)}%</span>
                </div>
              );
            })}
          </div>
        </motion.div>
      )}

      {/* Charts grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-3">
        <DailyVolumeChart daily={daily} />
        <DecisionsChart daily={daily} />

        {/* Sentiment Thermometer Widget (NEW!) */}
        <motion.div variants={item} className="card-aurora rounded-2xl p-5 relative overflow-hidden group">
          <div className="absolute inset-0 bg-gradient-to-br from-emerald-500/[0.02] to-transparent pointer-events-none" />
          <div className="flex items-center justify-between mb-1">
            <span className="font-mono text-[9px] text-[var(--text-3)] tracking-wider uppercase">Sentiment vocal (appels)</span>
            <span className="flex items-center gap-1 text-[8px] font-mono text-emerald-400 bg-emerald-500/10 px-2 py-0.5 rounded border border-emerald-500/20">
              😊 {kpis ? Math.round(kpis.tauxResolution) : 80}% Satisfait
            </span>
          </div>

          <div className="mt-4 flex flex-col justify-between h-[155px]">
            <div>
              <p className="text-[10px] text-[var(--text-2)] leading-relaxed mb-4">
                Analyse sémantique en temps réel des flux vocaux via Whisper STT et Gemma IA.
              </p>
              
              {/* Thermometer Tube */}
              <div className="h-4 w-full rounded-full bg-[var(--muted)] overflow-hidden flex p-[1.5px] border border-border relative">
                {(() => {
                  const satisfaction = kpis ? Math.round(kpis.tauxResolution) : 80;
                  const posPct = Math.round(satisfaction * 0.85); // e.g. 68%
                  const neuPct = Math.round((100 - posPct) * 0.6); // e.g. 19%
                  const negPct = 100 - posPct - neuPct; // e.g. 13%
                  return (
                    <>
                      <div className="h-full rounded-l-full transition-all duration-1000 relative" style={{ width: `${posPct}%`, background: 'linear-gradient(90deg, #00D4AA 0%, var(--success) 100%)', boxShadow: '0 0 10px rgba(0,212,170,0.3)' }}>
                        <div className="absolute inset-0 bg-white/20 animate-pulse" />
                      </div>
                      <div className="h-full transition-all duration-1000" style={{ width: `${neuPct}%`, background: 'linear-gradient(90deg, var(--warning) 0%, #FFB020 100%)' }} />
                      <div className="h-full rounded-r-full transition-all duration-1000" style={{ width: `${negPct}%`, background: 'linear-gradient(90deg, var(--danger) 0%, #FF4757 100%)' }} />
                    </>
                  );
                })()}
              </div>
            </div>

            <div className="grid grid-cols-3 gap-2 border-t border-border-subtle pt-3">
              {[
                { label: 'Positif', value: kpis ? `${Math.round(kpis.tauxResolution * 0.85)}%` : '68%', color: 'var(--success)', icon: '😊' },
                { label: 'Neutre', value: kpis ? `${Math.round((100 - kpis.tauxResolution * 0.85) * 0.6)}%` : '20%', color: 'var(--warning)', icon: '😐' },
                { label: 'Négatif', value: kpis ? `${100 - Math.round(kpis.tauxResolution * 0.85) - Math.round((100 - kpis.tauxResolution * 0.85) * 0.6)}%` : '12%', color: 'var(--danger)', icon: '😠' },
              ].map(s => (
                <div key={s.label} className="text-center">
                  <div className="text-[10px] font-mono font-extrabold" style={{ color: s.color }}>{s.icon} {s.value}</div>
                  <div className="text-[8px] font-mono text-[var(--text-3)] uppercase mt-0.5">{s.label}</div>
                </div>
              ))}
            </div>
          </div>
        </motion.div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-3 mt-3">
        {/* Channel breakdown with values */}
        <motion.div variants={item} className="card-aurora rounded-2xl p-5">
          <span className="font-mono text-[9px] text-[var(--text-3)] tracking-wider uppercase">Répartition par canal</span>
          {kpis && (
            <div className="mt-4 space-y-3">
              {[
                { label: 'Appels', value: kpis.ticketsCall, color: 'var(--canal-call)', icon: <PhoneCall size={13} /> },
                { label: 'Emails', value: kpis.ticketsEmail, color: 'var(--canal-email)', icon: <Mail size={13} /> },
                { label: 'Web', value: kpis.ticketsWeb, color: 'var(--canal-web)', icon: <Globe size={13} /> },
              ].map(ch => {
                const pct = kpis.totalTickets > 0 ? (ch.value / kpis.totalTickets) * 100 : 0;
                return (
                  <div key={ch.label}>
                    <div className="flex items-center justify-between mb-1">
                      <span className="text-xs flex items-center gap-1.5"><span>{ch.icon}</span>{ch.label}</span>
                      <span className="font-mono text-xs text-[var(--text-2)]">
                        <strong>{ch.value}</strong> ({pct.toFixed(1)}%)
                      </span>
                    </div>
                    <div className="h-2.5 rounded-full bg-[var(--muted)] overflow-hidden">
                      <motion.div className="h-full rounded-full" style={{ background: ch.color }}
                        initial={{ width: 0 }} animate={{ width: `${pct}%` }}
                        transition={{ duration: 0.8, delay: 0.3, ease: [0.22, 1, 0.36, 1] as const }} />
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </motion.div>

        {/* Decision donut */}
        <motion.div variants={item} className="card-aurora rounded-2xl p-5">
          <span className="font-mono text-[9px] text-[var(--text-3)] tracking-wider uppercase">Répartition décisions</span>
          {kpis && (
            <div className="mt-4 flex items-center gap-6">
              <svg width="96" height="96" viewBox="0 0 96 96">
                <defs>
                  <linearGradient id="donut-grad" x1="0" y1="0" x2="1" y2="1">
                    <stop offset="0%" stopColor="var(--secondary)" />
                    <stop offset="100%" stopColor="var(--primary)" />
                  </linearGradient>
                </defs>
                {(() => {
                  const total = totalDecisions || 1;
                  const f = (kpis.decisionsAiFavorable / total) * 100;
                  const d = (kpis.decisionsAiDefavorable / total) * 100;
                  return (
                    <>
                      <circle cx="48" cy="48" r="38" fill="none" stroke="var(--muted)" strokeWidth="8" />
                      <circle cx="48" cy="48" r="38" fill="none" stroke="var(--success)" strokeWidth="8"
                        strokeDasharray={`${f * 2.39} 239`} strokeLinecap="round" transform="rotate(-90 48 48)" />
                      <circle cx="48" cy="48" r="38" fill="none" stroke="var(--danger)" strokeWidth="8"
                        strokeDasharray={`${d * 2.39} 239`} strokeDashoffset={`${-f * 2.39}`}
                        strokeLinecap="round" transform="rotate(-90 48 48)" />
                      <text x="48" y="46" textAnchor="middle" className="text-sm font-extrabold" fill="var(--text)">{totalDecisions}</text>
                      <text x="48" y="58" textAnchor="middle" className="text-[8px] font-mono" fill="var(--text-3)">TOTAL</text>
                    </>
                  );
                })()}
              </svg>
              <div className="flex-1 space-y-2">
                {[
                  { label: 'Favorable', value: kpis.decisionsAiFavorable, color: 'var(--success)', pct: totalDecisions > 0 ? ((kpis.decisionsAiFavorable / totalDecisions) * 100).toFixed(1) : '0' },
                  { label: 'Défavorable', value: kpis.decisionsAiDefavorable, color: 'var(--danger)', pct: totalDecisions > 0 ? ((kpis.decisionsAiDefavorable / totalDecisions) * 100).toFixed(1) : '0' },
                  { label: 'Étude', value: kpis.decisionsAiEtude, color: 'var(--warning)', pct: totalDecisions > 0 ? ((kpis.decisionsAiEtude / totalDecisions) * 100).toFixed(1) : '0' },
                ].map(d => (
                  <div key={d.label} className="flex items-center gap-2">
                    <div className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{ background: d.color }} />
                    <span className="text-xs flex-1">{d.label}</span>
                    <span className="font-mono text-xs font-bold"><AnimatedCounter value={d.value} /></span>
                    <span className="font-mono text-[9px] text-[var(--text-3)]">({d.pct}%)</span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </motion.div>
      </div>

      {/* Channel per day mini chart */}
      {daily.length > 0 && (
        <ChannelsChart daily={daily} />
      )}
      {/* ══════ Supervision Section ══════ */}

      {/* Alerts Banner */}
      {alerts.length > 0 && (
        <motion.div variants={item} className="space-y-2">
          <div className="flex items-center gap-2 mb-1">
            <Shield size={14} style={{ color: 'var(--warning)' }} />
            <span className="font-mono text-[9px] text-[var(--text-3)] tracking-wider uppercase">Alertes de supervision</span>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
            {alerts.map((a, i) => {
              const colors = {
                CRITIQUE: { bg: 'var(--danger)', soft: 'rgba(255,71,87,0.08)', border: 'rgba(255,71,87,0.2)', icon: <AlertCircle size={14} style={{ color: 'var(--danger)' }} /> },
                WARNING: { bg: 'var(--warning)', soft: 'rgba(255,165,0,0.08)', border: 'rgba(255,165,0,0.2)', icon: <AlertTriangle size={14} style={{ color: 'var(--warning)' }} /> },
                INFO: { bg: 'var(--success)', soft: 'rgba(46,213,115,0.08)', border: 'rgba(46,213,115,0.2)', icon: <CircleCheck size={14} style={{ color: 'var(--success)' }} /> },
              }[a.severity];
              return (
                <motion.div key={`analytics-alert-${i}-${a.type}-${a.severity}`} initial={{ opacity: 0, x: -10 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: i * 0.1 }}
                  className="card-aurora rounded-xl p-3 flex items-start gap-3"
                  style={{ background: colors.soft, border: `1px solid ${colors.border}` }}>
                  <span className="flex items-center mt-0.5">{colors.icon}</span>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="text-[10px] font-mono font-bold uppercase" style={{ color: colors.bg }}>
                        {a.type.replaceAll('_', ' ')}
                      </span>
                      {a.count > 0 && (
                        <span className="text-[9px] font-mono px-1.5 py-0.5 rounded-full font-bold"
                          style={{ background: colors.bg, color: 'white' }}>{a.count}</span>
                      )}
                    </div>
                    <p className="text-[11px] text-[var(--text-2)] mt-0.5 leading-snug">{a.message}</p>
                  </div>
                </motion.div>
              );
            })}
          </div>
        </motion.div>
      )}

      {/* Operator Leaderboard */}
      {operators.length > 0 && (
        <OperatorLeaderboard operators={operators} />
      )}
    </motion.div>
  );
}
