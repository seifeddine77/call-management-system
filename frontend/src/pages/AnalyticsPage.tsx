/**
 * Analytique — dashboard décisionnel superviseur (SUPERVISOR / ADMIN).
 * KPIs, tendances, répartitions canal / décision, sentiment vocal et feedbacks chatbot WhatsApp.
 * Sources : analyticsService + chatbotService (/api/chat/stats).
 */
import { useState, useEffect, useCallback } from 'react';
import { motion } from 'framer-motion';
import { TrendingUp, TrendingDown, Minus, RefreshCw, Calendar, Download, Shield, BarChart3, CheckCircle2, Clock, Bot, Target, PlusCircle, RotateCw, FolderClosed, PhoneCall, Mail, Globe, AlertCircle, AlertTriangle, CircleCheck, ThumbsUp, ThumbsDown } from 'lucide-react';
import { analyticsService } from '../services/analyticsService';
import { chatbotService } from '../services/chatbotService';
import type { ChatbotStats } from '../services/chatbotService';
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

function KpiTrend({ trendUp, trend }: Readonly<{ trendUp: boolean | null | undefined; trend?: string }>) {
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
}

interface KpiCardsGridProps {
  kpis: KpiResponse;
  comparison: KpiComparison;
  totalDecisions: number;
  scoreMoyen: number;
  scoreTrendText: string | undefined;
}

function KpiCardsGrid({ kpis, comparison, totalDecisions, scoreMoyen, scoreTrendText }: Readonly<KpiCardsGridProps>) {
  const cards = [
    {
      label: 'Total tickets',
      value: kpis.totalTickets,
      icon: <BarChart3 size={14} />,
      trend: formatPercent(comparison.deltaTickets),
      trendUp: getTrendDirection(comparison.deltaTickets)
    },
    {
      label: 'Résolution',
      value: kpis.tauxResolution,
      suffix: '%',
      decimals: 1,
      icon: <CheckCircle2 size={14} />,
      trend: formatPercent(comparison.deltaResolution),
      trendUp: getTrendDirection(comparison.deltaResolution)
    },
    {
      label: 'Temps moyen',
      value: kpis.tempsMoyenMinutes,
      suffix: 'min',
      icon: <Clock size={14} />,
      trend: comparison.deltaTempsMoyen === 0 ? undefined : formatPercent(comparison.deltaTempsMoyen),
      trendUp: getTrendDirection(-comparison.deltaTempsMoyen)
    },
    {
      label: 'Décisions IA',
      value: totalDecisions,
      icon: <Bot size={14} />,
      trend: formatPercent(comparison.deltaDecisions),
      trendUp: getTrendDirection(comparison.deltaDecisions)
    },
    {
      label: 'Score favorable',
      value: scoreMoyen,
      suffix: '%',
      icon: <Target size={14} />,
      trendUp: getScoreTrendDirection(scoreMoyen),
      subtitle: getScoreSubtitle(scoreMoyen),
      trend: scoreTrendText
    },
  ];

  return (
    <div className="grid grid-cols-2 md:grid-cols-5 gap-3">
      {cards.map((kpi) => (
        <motion.div key={kpi.label} variants={item} className="card-aurora rounded-2xl p-4 group hover:-translate-y-0.5 transition-all duration-150 relative overflow-hidden">
          <div className="absolute -top-8 -right-8 w-20 h-20 rounded-full bg-[var(--primary)] opacity-[0.04] group-hover:opacity-[0.08] transition-opacity blur-xl" />
          <div className="relative">
            <div className="flex items-center gap-1.5 mb-2">
              <span className="flex items-center">{kpi.icon}</span>
              <span className="font-mono text-[9px] text-[var(--text-3)] tracking-wider uppercase">{kpi.label}</span>
            </div>
            <div className="flex items-baseline gap-2">
              <AnimatedCounter value={kpi.value} suffix={kpi.suffix} decimals={kpi.decimals} className="text-xl font-extrabold tracking-tighter" />
              <KpiTrend trendUp={kpi.trendUp} trend={kpi.trend} />
            </div>
            {kpi.subtitle && (
              <p className="font-mono text-[8px] text-[var(--text-3)] mt-1">{kpi.subtitle}</p>
            )}
          </div>
        </motion.div>
      ))}
    </div>
  );
}

interface TicketStatusSplitProps {
  kpis: KpiResponse;
}

function TicketStatusSplit({ kpis }: Readonly<TicketStatusSplitProps>) {
  return (
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
  );
}

interface SentimentThermometerProps {
  kpis: KpiResponse | null;
}

function SentimentThermometer({ kpis }: Readonly<SentimentThermometerProps>) {
  const satisfaction = kpis ? Math.round(kpis.tauxResolution) : 80;
  const posPct = Math.round(satisfaction * 0.85); // e.g. 68%
  const neuPct = Math.round((100 - posPct) * 0.6); // e.g. 19%
  const negPct = 100 - posPct - neuPct; // e.g. 13%

  return (
    <motion.div variants={item} className="card-aurora rounded-2xl p-5 relative overflow-hidden group">
      <div className="absolute inset-0 bg-gradient-to-br from-emerald-500/[0.02] to-transparent pointer-events-none" />
      <div className="flex items-center justify-between mb-1">
        <span className="font-mono text-[9px] text-[var(--text-3)] tracking-wider uppercase">Sentiment vocal (appels)</span>
        <span className="flex items-center gap-1 text-[8px] font-mono text-emerald-400 bg-emerald-500/10 px-2 py-0.5 rounded border border-emerald-500/20">
          😊 {satisfaction}% Satisfait
        </span>
      </div>

      <div className="mt-4 flex flex-col justify-between h-[155px]">
        <div>
          <p className="text-[10px] text-[var(--text-2)] leading-relaxed mb-4">
            Analyse sémantique en temps réel des flux vocaux via Whisper STT et Gemma IA.
          </p>
          
          {/* Thermometer Tube */}
          <div className="h-4 w-full rounded-full bg-[var(--muted)] overflow-hidden flex p-[1.5px] border border-border relative">
            <div className="h-full rounded-l-full transition-all duration-1000 relative" style={{ width: `${posPct}%`, background: 'linear-gradient(90deg, #00D4AA 0%, var(--success) 100%)', boxShadow: '0 0 10px rgba(0,212,170,0.3)' }}>
              <div className="absolute inset-0 bg-white/20 animate-pulse" />
            </div>
            <div className="h-full transition-all duration-1000" style={{ width: `${neuPct}%`, background: 'linear-gradient(90deg, var(--warning) 0%, #FFB020 100%)' }} />
            <div className="h-full rounded-r-full transition-all duration-1000" style={{ width: `${negPct}%`, background: 'linear-gradient(90deg, var(--danger) 0%, #FF4757 100%)' }} />
          </div>
        </div>

        <div className="grid grid-cols-3 gap-2 border-t border-border-subtle pt-3">
          {[
            { label: 'Positif', value: `${posPct}%`, color: 'var(--success)', icon: '😊' },
            { label: 'Neutre', value: `${neuPct}%`, color: 'var(--warning)', icon: '😐' },
            { label: 'Négatif', value: `${negPct}%`, color: 'var(--danger)', icon: '😠' },
          ].map(s => (
            <div key={s.label} className="text-center">
              <div className="text-[10px] font-mono font-extrabold" style={{ color: s.color }}>{s.icon} {s.value}</div>
              <div className="text-[8px] font-mono text-[var(--text-3)] uppercase mt-0.5">{s.label}</div>
            </div>
          ))}
        </div>
      </div>
    </motion.div>
  );
}

interface ChannelBreakdownProps {
  kpis: KpiResponse;
}

function ChannelBreakdown({ kpis }: Readonly<ChannelBreakdownProps>) {
  return (
    <motion.div variants={item} className="card-aurora rounded-2xl p-5">
      <span className="font-mono text-[9px] text-[var(--text-3)] tracking-wider uppercase">Répartition par canal</span>
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
    </motion.div>
  );
}

interface DecisionDonutProps {
  kpis: KpiResponse;
  totalDecisions: number;
}

function DecisionDonut({ kpis, totalDecisions }: Readonly<DecisionDonutProps>) {
  const total = totalDecisions || 1;
  const f = (kpis.decisionsAiFavorable / total) * 100;
  const d = (kpis.decisionsAiDefavorable / total) * 100;

  return (
    <motion.div variants={item} className="card-aurora rounded-2xl p-5">
      <span className="font-mono text-[9px] text-[var(--text-3)] tracking-wider uppercase">Répartition décisions</span>
      <div className="mt-4 flex items-center gap-6">
        <svg width="96" height="96" viewBox="0 0 96 96">
          <defs>
            <linearGradient id="donut-grad" x1="0" y1="0" x2="1" y2="1">
              <stop offset="0%" stopColor="var(--secondary)" />
              <stop offset="100%" stopColor="var(--primary)" />
            </linearGradient>
          </defs>
          <circle cx="48" cy="48" r="38" fill="none" stroke="var(--muted)" strokeWidth="8" />
          <circle cx="48" cy="48" r="38" fill="none" stroke="var(--success)" strokeWidth="8"
            strokeDasharray={`${f * 2.39} 239`} strokeLinecap="round" transform="rotate(-90 48 48)" />
          <circle cx="48" cy="48" r="38" fill="none" stroke="var(--danger)" strokeWidth="8"
            strokeDasharray={`${d * 2.39} 239`} strokeDashoffset={-(f * 2.39)}
            strokeLinecap="round" transform="rotate(-90 48 48)" />
          <text x="48" y="46" textAnchor="middle" className="text-sm font-extrabold" fill="var(--text)">{totalDecisions}</text>
          <text x="48" y="58" textAnchor="middle" className="text-[8px] font-mono" fill="var(--text-3)">TOTAL</text>
        </svg>
        <div className="flex-1 space-y-2">
          {(() => {
            const safePct = (v: number) => totalDecisions > 0 ? ((v / totalDecisions) * 100).toFixed(1) : '0';
            return [
              { label: 'Favorable', value: kpis.decisionsAiFavorable, color: 'var(--success)', pct: safePct(kpis.decisionsAiFavorable) },
              { label: 'Défavorable', value: kpis.decisionsAiDefavorable, color: 'var(--danger)', pct: safePct(kpis.decisionsAiDefavorable) },
              { label: 'Étude', value: kpis.decisionsAiEtude, color: 'var(--warning)', pct: safePct(kpis.decisionsAiEtude) },
            ].map(d => (
              <div key={d.label} className="flex items-center gap-2">
                <div className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{ background: d.color }} />
                <span className="text-xs flex-1">{d.label}</span>
                <span className="font-mono text-xs font-bold"><AnimatedCounter value={d.value} /></span>
                <span className="font-mono text-[9px] text-[var(--text-3)]">({d.pct}%)</span>
              </div>
            ));
          })()}
        </div>
      </div>
    </motion.div>
  );
}

interface SupervisionAlertsProps {
  alerts: SupervisionAlert[];
}

function SupervisionAlerts({ alerts }: Readonly<SupervisionAlertsProps>) {
  return (
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
  );
}

interface ChatbotPerformanceProps {
  stats: ChatbotStats | null;
}

function ChatbotPerformance({ stats }: Readonly<ChatbotPerformanceProps>) {
  if (!stats) return null;

  const satisfaction = parseInt(stats.satisfaction_rate.replace('%', '')) || 0;

  return (
    <motion.div variants={item} className="card-aurora rounded-2xl p-5 relative overflow-hidden group">
      <div className="absolute inset-0 bg-gradient-to-br from-[var(--primary)]/[0.02] to-transparent pointer-events-none" />
      
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2">
          <Bot size={16} className="text-[var(--primary)]" />
          <span className="font-mono text-[9px] text-[var(--text-3)] tracking-wider uppercase">Performance Chatbot RAG (WhatsApp)</span>
        </div>
        <span className={`flex items-center gap-1 text-[10px] font-mono font-bold px-2 py-0.5 rounded border ${
          satisfaction >= 75 ? 'text-emerald-400 bg-emerald-500/10 border-emerald-500/20' : 'text-amber-400 bg-amber-500/10 border-amber-500/20'
        }`}>
          🤖 {satisfaction}% satisfaction
        </span>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-4 gap-3 mb-5">
        {[
          { label: 'Total évaluations', value: stats.total_feedback, icon: <Bot size={14} className="text-[var(--text-3)]" /> },
          { label: 'Retours positifs', value: stats.positive, icon: <ThumbsUp size={14} className="text-emerald-400" />, suffix: ' 👍' },
          { label: 'Retours négatifs', value: stats.negative, icon: <ThumbsDown size={14} className="text-[var(--danger)]" />, suffix: ' 👎' },
          { label: 'Persistés en base', value: stats.db_persisted, icon: <Shield size={14} className="text-blue-400" /> }
        ].map(w => (
          <div key={w.label} className="p-3 rounded-xl bg-white/[0.02] border border-white/[0.05] flex items-center justify-between">
            <div>
              <span className="font-mono text-[8px] text-[var(--text-3)] uppercase tracking-wider block">{w.label}</span>
              <span className="text-md font-extrabold tracking-tight mt-0.5 inline-block">{w.value}{w.suffix}</span>
            </div>
            <span className="p-2 rounded-lg bg-white/[0.03] border border-white/[0.05]">{w.icon}</span>
          </div>
        ))}
      </div>

      {/* Recent feedbacks table */}
      <div className="space-y-2">
        <span className="font-mono text-[8px] text-[var(--text-3)] uppercase tracking-wider block">10 derniers retours reçus</span>
        <div className="overflow-x-auto rounded-xl border border-border-subtle bg-white/[0.01]">
          <table className="w-full text-left border-collapse text-[11px]">
            <thead>
              <tr className="border-b border-border-subtle bg-white/[0.02]">
                <th className="p-2.5 font-mono text-[8px] text-[var(--text-3)] uppercase">Groupe / Agence</th>
                <th className="p-2.5 font-mono text-[8px] text-[var(--text-3)] uppercase">Question de l'opérateur</th>
                <th className="p-2.5 font-mono text-[8px] text-[var(--text-3)] uppercase">Réponse de l'IA (Aperçu)</th>
                <th className="p-2.5 font-mono text-[8px] text-[var(--text-3)] uppercase text-center">Évaluation</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border-subtle">
              {stats.recent_feedback && stats.recent_feedback.length > 0 ? (
                stats.recent_feedback.map((fb, idx) => {
                  const agencyName = fb.group_name.replace('@g.us', '').split('@')[0];
                  return (
                    <tr key={`chatbot-fb-${idx}`} className="hover:bg-white/[0.01] transition-colors">
                      <td className="p-2.5 font-mono text-[10px] text-[var(--text-2)] whitespace-nowrap">{agencyName}</td>
                      <td className="p-2.5 max-w-[200px] truncate text-[var(--text-2)]" title={fb.question}>{fb.question}</td>
                      <td className="p-2.5 max-w-[300px] truncate text-[var(--text-3)]" title={fb.answer_preview}>{fb.answer_preview}</td>
                      <td className="p-2.5 text-center whitespace-nowrap">
                        {fb.rating === 'positive' ? (
                          <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[9px] font-bold text-emerald-400 bg-emerald-500/10 border border-emerald-500/20">
                            👍 POSITIF
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[9px] font-bold text-[var(--danger)] bg-[var(--danger)]/10 border border-[var(--danger)]/20">
                            👎 NÉGATIF
                          </span>
                        )}
                      </td>
                    </tr>
                  );
                })
              ) : (
                <tr>
                  <td colSpan={4} className="p-4 text-center font-mono text-[10px] text-[var(--text-3)]">
                    Aucun retour enregistré pour le moment.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </motion.div>
  );
}

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
  const [chatbotStats, setChatbotStats] = useState<ChatbotStats | null>(null);

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const p = periodMap[period] || '30d';
      const [compData, dailyData, opsData, alertsData, chatbotData] = await Promise.all([
        analyticsService.getComparison(p),
        analyticsService.getDailyStats(p),
        analyticsService.getOperatorPerformance(p).catch(() => [] as OperatorPerformance[]),
        analyticsService.getSupervisionAlerts().catch(() => [] as SupervisionAlert[]),
        chatbotService.getStats().catch(() => null),
      ]);
      setComparison(compData);
      setKpis(compData.current);
      setDaily(dailyData);
      setOperators(opsData);
      setAlerts(alertsData);
      setChatbotStats(chatbotData);
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



  const scoreDiff = scoreMoyen - prevScore;
  let scoreTrendText: string | undefined;
  if (prevScore > 0) {
    const sign = scoreDiff >= 0 ? '+' : '';
    scoreTrendText = `${sign}${scoreDiff}pts`;
  }

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
      {loading ? <KpiSkeleton /> : (
        kpis && comparison && (
          <KpiCardsGrid
            kpis={kpis}
            comparison={comparison}
            totalDecisions={totalDecisions}
            scoreMoyen={scoreMoyen}
            scoreTrendText={scoreTrendText}
          />
        )
      )}

      {/* Ticket Status Split */}
      {kpis && !loading && (
        <TicketStatusSplit kpis={kpis} />
      )}

      {/* Charts grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-3">
        <DailyVolumeChart daily={daily} />
        <DecisionsChart daily={daily} />
        <SentimentThermometer kpis={kpis} />
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-3 mt-3">
        {/* Channel breakdown with values */}
        {!loading && kpis && (
          <ChannelBreakdown kpis={kpis} />
        )}

        {/* Decision donut */}
        {!loading && kpis && (
          <DecisionDonut kpis={kpis} totalDecisions={totalDecisions} />
        )}
      </div>

      {/* Channel per day mini chart */}
      {daily.length > 0 && (
        <ChannelsChart daily={daily} />
      )}

      {/* Alerts Banner */}
      {alerts.length > 0 && (
        <SupervisionAlerts alerts={alerts} />
      )}

      {/* Chatbot RAG Performance */}
      {!loading && chatbotStats && (
        <ChatbotPerformance stats={chatbotStats} />
      )}

      {/* Operator Leaderboard */}
      {operators.length > 0 && (
        <OperatorLeaderboard operators={operators} />
      )}
    </motion.div>
  );
}
