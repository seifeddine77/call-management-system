import { useState, useEffect, useCallback } from 'react';
import { motion } from 'framer-motion';
import { TrendingUp, TrendingDown, Minus, RefreshCw, Calendar, Download } from 'lucide-react';
import { analyticsService } from '../services/analyticsService';
import { KpiSkeleton, ErrorState } from '../components/ui/States';
import AnimatedCounter from '../components/ui/AnimatedCounter';
import type { KpiResponse, KpiComparison, DailyStat } from '../types/analytics';

const container = { hidden: {}, show: { transition: { staggerChildren: 0.06 } } };
const item = { hidden: { opacity: 0, y: 12 }, show: { opacity: 1, y: 0, transition: { duration: 0.35, ease: [0.22, 1, 0.36, 1] as const } } };

const periodLabels: Record<string, string> = { week: '7 derniers jours', month: '30 derniers jours', year: '90 derniers jours' };
const periodMap: Record<string, string> = { week: '7d', month: '30d', year: '90d' };

export default function AnalyticsPage() {
  const [kpis, setKpis] = useState<KpiResponse | null>(null);
  const [comparison, setComparison] = useState<KpiComparison | null>(null);
  const [daily, setDaily] = useState<DailyStat[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [period, setPeriod] = useState<string>('month');
  const [exporting, setExporting] = useState(false);

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const p = periodMap[period] || '30d';
      const [compData, dailyData] = await Promise.all([
        analyticsService.getComparison(p),
        analyticsService.getDailyStats(p),
      ]);
      setComparison(compData);
      setKpis(compData.current);
      setDaily(dailyData);
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
                {p === 'week' ? 'Semaine' : p === 'month' ? 'Mois' : 'Année'}
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
            { label: 'Total tickets', value: kpis.totalTickets, icon: '📊',
              trend: `${comparison.deltaTickets >= 0 ? '+' : ''}${comparison.deltaTickets}%`,
              trendUp: comparison.deltaTickets > 0 ? true : comparison.deltaTickets < 0 ? false : null },
            { label: 'Résolution', value: kpis.tauxResolution, suffix: '%', decimals: 1, icon: '✅',
              trend: `${comparison.deltaResolution >= 0 ? '+' : ''}${comparison.deltaResolution}%`,
              trendUp: comparison.deltaResolution > 0 ? true : comparison.deltaResolution < 0 ? false : null },
            { label: 'Temps moyen', value: kpis.tempsMoyenMinutes, suffix: 'min', icon: '⏱',
              trend: comparison.deltaTempsMoyen !== 0 ? `${comparison.deltaTempsMoyen >= 0 ? '+' : ''}${comparison.deltaTempsMoyen}%` : undefined,
              trendUp: comparison.deltaTempsMoyen < 0 ? true : comparison.deltaTempsMoyen > 0 ? false : null },
            { label: 'Décisions IA', value: totalDecisions, icon: '🤖',
              trend: `${comparison.deltaDecisions >= 0 ? '+' : ''}${comparison.deltaDecisions}%`,
              trendUp: comparison.deltaDecisions > 0 ? true : comparison.deltaDecisions < 0 ? false : null },
            { label: 'Score favorable', value: scoreMoyen, suffix: '%', icon: '🎯',
              trendUp: scoreMoyen >= 60 ? true : scoreMoyen < 40 ? false : null,
              subtitle: scoreMoyen >= 80 ? 'Excellent' : scoreMoyen >= 60 ? 'Bon' : scoreMoyen >= 40 ? 'Moyen' : 'Faible',
              trend: prevScore > 0 ? `${scoreMoyen >= prevScore ? '+' : ''}${scoreMoyen - prevScore}pts` : undefined },
          ].map((kpi, i) => (
            <motion.div key={i} variants={item} className="card-aurora rounded-2xl p-4 group hover:-translate-y-0.5 transition-all duration-150 relative overflow-hidden">
              <div className="absolute -top-8 -right-8 w-20 h-20 rounded-full bg-[var(--primary)] opacity-[0.04] group-hover:opacity-[0.08] transition-opacity blur-xl" />
              <div className="relative">
                <div className="flex items-center gap-1.5 mb-2">
                  <span className="text-sm">{kpi.icon}</span>
                  <span className="font-mono text-[9px] text-[var(--text-3)] tracking-wider uppercase">{kpi.label}</span>
                </div>
                <div className="flex items-baseline gap-2">
                  <AnimatedCounter value={kpi.value} suffix={kpi.suffix} decimals={kpi.decimals} className="text-xl font-extrabold tracking-tighter" />
                  {kpi.trendUp !== undefined && kpi.trendUp !== null ? (
                    <span className={`flex items-center gap-0.5 font-mono text-[10px] ${kpi.trendUp ? 'text-emerald-400' : 'text-[var(--danger)]'}`}>
                      {kpi.trendUp ? <TrendingUp size={10} /> : <TrendingDown size={10} />} {kpi.trend || ''}
                    </span>
                  ) : kpi.trendUp === null ? (
                    <span className="flex items-center gap-0.5 font-mono text-[10px] text-[var(--text-3)]">
                      <Minus size={10} /> {kpi.trend || 'Stable'}
                    </span>
                  ) : null}
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
              { label: 'Nouveaux', value: kpis.ticketsNouveau, color: 'var(--info)', icon: '🆕' },
              { label: 'En cours', value: kpis.ticketsEnCours, color: 'var(--warning)', icon: '🔄' },
              { label: 'Résolus', value: kpis.ticketsResolu, color: 'var(--success)', icon: '✅' },
              { label: 'Fermés', value: kpis.ticketsFerme, color: 'var(--text-3)', icon: '📁' },
            ].map(s => {
              const pct = kpis.totalTickets > 0 ? (s.value / kpis.totalTickets) * 100 : 0;
              return (
                <div key={s.label} className="flex-1 p-3 rounded-xl" style={{ background: `${s.color}08`, border: `1px solid ${s.color}20` }}>
                  <div className="flex items-center gap-1.5 mb-1">
                    <span className="text-xs">{s.icon}</span>
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
      <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
        {/* Daily volume chart */}
        <motion.div variants={item} className="card-aurora rounded-2xl p-5">
          <span className="font-mono text-[9px] text-[var(--text-3)] tracking-wider uppercase">Volume quotidien</span>
          {daily.length > 0 ? (
            <div className="flex items-end gap-1 h-[160px] mt-3">
              {[...daily].sort((a, b) => a.date.localeCompare(b.date)).slice(-14).map((d, i, arr) => {
                const max = Math.max(...arr.map(x => x.totalTickets), 1);
                const pct = (d.totalTickets / max) * 100;
                return (
                  <div key={i} className="flex flex-col items-center flex-1 gap-1 group/bar">
                    <span className="font-mono text-[7px] text-[var(--text-3)] opacity-0 group-hover/bar:opacity-100 transition-opacity">{d.totalTickets}</span>
                    <div className="w-full flex flex-col items-center" style={{ height: `${pct}%`, minHeight: '4px' }}>
                      <div className="w-full rounded-t-sm flex-1 transition-all group-hover/bar:opacity-100" style={{ background: 'var(--primary)', opacity: 0.5 + (i / arr.length) * 0.5 }} />
                    </div>
                    <span className="font-mono text-[7px] text-[var(--text-3)]">{new Date(d.date).getDate()}/{new Date(d.date).getMonth() + 1}</span>
                  </div>
                );
              })}
            </div>
          ) : <div className="h-[160px] flex items-center justify-center text-xs text-[var(--text-3)]">Aucune donnée</div>}
        </motion.div>

        {/* Decision stacked chart */}
        <motion.div variants={item} className="card-aurora rounded-2xl p-5">
          <div className="flex items-center justify-between">
            <span className="font-mono text-[9px] text-[var(--text-3)] tracking-wider uppercase">Décisions par jour</span>
            <div className="flex gap-2">
              {[
                { label: 'Fav', color: 'var(--success)' },
                { label: 'Déf', color: 'var(--danger)' },
                { label: 'Étu', color: 'var(--warning)' },
              ].map(l => (
                <span key={l.label} className="flex items-center gap-1 text-[7px] font-mono text-[var(--text-3)]">
                  <span className="w-1.5 h-1.5 rounded-full inline-block" style={{ background: l.color }} />{l.label}
                </span>
              ))}
            </div>
          </div>
          {daily.length > 0 ? (
            <div className="flex items-end gap-1 h-[160px] mt-3">
              {[...daily].sort((a, b) => a.date.localeCompare(b.date)).slice(-14).map((d, i, arr) => {
                const total = d.decisionsFavorable + d.decisionsDefavorable + d.decisionsEtude;
                const max = Math.max(...arr.map(x => x.decisionsFavorable + x.decisionsDefavorable + x.decisionsEtude), 1);
                return (
                  <div key={i} className="flex flex-col items-center flex-1 gap-1 group/bar">
                    <span className="font-mono text-[7px] text-[var(--text-3)] opacity-0 group-hover/bar:opacity-100 transition-opacity">{total}</span>
                    <div className="w-full flex flex-col rounded-t-sm overflow-hidden" style={{ height: `${(total / max) * 100}%`, minHeight: '4px' }}>
                      {d.decisionsFavorable > 0 && <div style={{ flex: d.decisionsFavorable, background: 'var(--success)' }} />}
                      {d.decisionsDefavorable > 0 && <div style={{ flex: d.decisionsDefavorable, background: 'var(--danger)' }} />}
                      {d.decisionsEtude > 0 && <div style={{ flex: d.decisionsEtude, background: 'var(--warning)' }} />}
                    </div>
                    <span className="font-mono text-[7px] text-[var(--text-3)]">{new Date(d.date).getDate()}/{new Date(d.date).getMonth() + 1}</span>
                  </div>
                );
              })}
            </div>
          ) : <div className="h-[160px] flex items-center justify-center text-xs text-[var(--text-3)]">Aucune donnée</div>}
        </motion.div>

        {/* Channel breakdown with values */}
        <motion.div variants={item} className="card-aurora rounded-2xl p-5">
          <span className="font-mono text-[9px] text-[var(--text-3)] tracking-wider uppercase">Répartition par canal</span>
          {kpis && (
            <div className="mt-4 space-y-3">
              {[
                { label: 'Appels', value: kpis.ticketsCall, color: 'var(--canal-call)', icon: '📞' },
                { label: 'Emails', value: kpis.ticketsEmail, color: 'var(--canal-email)', icon: '📧' },
                { label: 'Web', value: kpis.ticketsWeb, color: 'var(--canal-web)', icon: '🌐' },
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
        <motion.div variants={item} className="card-aurora rounded-2xl p-5">
          <span className="font-mono text-[9px] text-[var(--text-3)] tracking-wider uppercase">Canaux par jour</span>
          <div className="flex items-center gap-3 mt-1">
            {[
              { label: 'Appels', color: 'var(--canal-call)' },
              { label: 'Emails', color: 'var(--canal-email)' },
              { label: 'Web', color: 'var(--canal-web)' },
            ].map(l => (
              <span key={l.label} className="flex items-center gap-1 text-[8px] font-mono text-[var(--text-3)]">
                <span className="w-2 h-2 rounded-full inline-block" style={{ background: l.color }} />{l.label}
              </span>
            ))}
          </div>
          <div className="flex items-end gap-1 h-[120px] mt-3">
            {[...daily].sort((a, b) => a.date.localeCompare(b.date)).slice(-14).map((d, i, arr) => {
              const total = d.ticketsCall + d.ticketsEmail + d.ticketsWeb;
              const max = Math.max(...arr.map(x => x.ticketsCall + x.ticketsEmail + x.ticketsWeb), 1);
              return (
                <div key={i} className="flex flex-col items-center flex-1 gap-1">
                  <div className="w-full flex flex-col rounded-t-sm overflow-hidden" style={{ height: `${(total / max) * 100}%`, minHeight: '4px' }}>
                    {d.ticketsCall > 0 && <div style={{ flex: d.ticketsCall, background: 'var(--canal-call)' }} />}
                    {d.ticketsEmail > 0 && <div style={{ flex: d.ticketsEmail, background: 'var(--canal-email)' }} />}
                    {d.ticketsWeb > 0 && <div style={{ flex: d.ticketsWeb, background: 'var(--canal-web)' }} />}
                  </div>
                  <span className="font-mono text-[7px] text-[var(--text-3)]">{new Date(d.date).getDate()}/{new Date(d.date).getMonth() + 1}</span>
                </div>
              );
            })}
          </div>
        </motion.div>
      )}
    </motion.div>
  );
}
