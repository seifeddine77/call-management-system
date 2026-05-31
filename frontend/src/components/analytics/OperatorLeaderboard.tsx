import { motion } from 'framer-motion';
import { Users, PhoneCall, Mail, Globe } from 'lucide-react';
import type { OperatorPerformance } from '../../types/analytics';

const item = { hidden: { opacity: 0, y: 12 }, show: { opacity: 1, y: 0, transition: { duration: 0.35, ease: [0.22, 1, 0.36, 1] as const } } };

/** Higher value is better (e.g. resolution rate, SLA compliance, score). */
const getColorForHigherBetter = (value: number, thresholdGood: number, thresholdAvg: number): string => {
  if (value >= thresholdGood) return 'var(--success)';
  if (value >= thresholdAvg) return 'var(--warning)';
  return 'var(--danger)';
};

/** Lower value is better (e.g. average handling time). */
const getColorForLowerBetter = (value: number, thresholdGood: number, thresholdAvg: number): string => {
  if (value <= thresholdGood) return 'var(--success)';
  if (value <= thresholdAvg) return 'var(--warning)';
  return 'var(--danger)';
};

const getScoreSymbol = (score: number) => {
  if (score >= 75) return '★';
  if (score >= 50) return '◆';
  return '▼';
};

interface OperatorLeaderboardProps {
  operators: OperatorPerformance[];
}

export default function OperatorLeaderboard({ operators }: Readonly<OperatorLeaderboardProps>) {
  return (
    <motion.div variants={item} className="card-aurora rounded-2xl p-5">
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2">
          <Users size={14} style={{ color: 'var(--primary)' }} />
          <span className="font-mono text-[9px] text-[var(--text-3)] tracking-wider uppercase">Performance opérateurs</span>
          <span className="text-[9px] font-mono px-1.5 py-0.5 rounded-full"
            style={{ background: 'var(--primary-soft)', color: 'var(--primary)' }}>{operators.length}</span>
        </div>
      </div>

      <div className="overflow-x-auto">
        <table style={{ width: '100%', borderCollapse: 'separate', borderSpacing: '0 4px' }}>
          <thead>
            <tr>
              {['#', 'Opérateur', 'Score', 'Tickets', 'Résolution', 'Temps moy.', 'SLA', 'En cours', 'Canaux'].map(h => (
                <th key={h} className="font-mono text-[8px] text-[var(--text-3)] uppercase tracking-wider text-left px-3 py-1">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {operators.map((op, i) => {
              const medal = `${i + 1}`;
              const maxTickets = operators[0]?.ticketsTraites || 1;
              const barPct = (op.ticketsTraites / maxTickets) * 100;
              const resColor = getColorForHigherBetter(op.tauxResolution, 80, 50);
              const tempsColor = getColorForLowerBetter(op.tempsMoyenMinutes, 30, 120);
              const scoreColor = getColorForHigherBetter(op.scoreComposite, 75, 50);
              const slaColor = getColorForHigherBetter(op.slaCompliance, 80, 60);
              const totalCanal = op.ticketsCall + op.ticketsEmail + op.ticketsWeb || 1;
              const circumference = 2 * Math.PI * 12;
              const scoreDash = (op.scoreComposite / 100) * circumference;

              return (
                <motion.tr key={op.operateurId}
                  initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: i * 0.05 }}
                  className="group"
                  style={{ background: i < 3 ? 'var(--elevated)' : 'transparent', borderRadius: '10px' }}>

                  {/* Rank */}
                  <td className="px-3 py-2.5 rounded-l-xl">
                    <span className="text-sm">{medal}</span>
                  </td>

                  {/* Name */}
                  <td className="px-3 py-2.5">
                    <div className="flex items-center gap-2">
                      <div className="w-7 h-7 rounded-full flex items-center justify-center text-[10px] font-bold"
                        style={{ background: 'var(--primary-soft)', color: 'var(--primary)' }}>
                        {(op.operateurName || 'O')[0].toUpperCase()}
                      </div>
                      <div>
                        <div className="text-xs font-semibold truncate max-w-[140px]">{op.operateurName || 'Opérateur'}</div>
                        <div className="text-[8px] font-mono text-[var(--text-3)]">{op.operateurId.substring(0, 8)}</div>
                      </div>
                    </div>
                  </td>

                  {/* Score composite ring */}
                  <td className="px-3 py-2.5">
                    <div className="flex items-center gap-1.5">
                      <svg width="28" height="28" viewBox="0 0 28 28">
                        <circle cx="14" cy="14" r="12" fill="none" stroke="var(--muted)" strokeWidth="2.5" />
                        <motion.circle cx="14" cy="14" r="12" fill="none" stroke={scoreColor} strokeWidth="2.5"
                          strokeLinecap="round" transform="rotate(-90 14 14)"
                          initial={{ strokeDasharray: `0 ${circumference}` }}
                          animate={{ strokeDasharray: `${scoreDash} ${circumference}` }}
                          transition={{ duration: 0.8, delay: 0.3 + i * 0.05 }} />
                        <text x="14" y="15.5" textAnchor="middle" className="text-[7px] font-bold" fill="var(--text)">{op.scoreComposite}</text>
                      </svg>
                      <span className="text-[8px] font-mono" style={{ color: scoreColor }}>
                        {getScoreSymbol(op.scoreComposite)}
                      </span>
                    </div>
                  </td>

                  {/* Tickets with bar */}
                  <td className="px-3 py-2.5">
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-bold font-mono w-6 text-right">{op.ticketsTraites}</span>
                      <div className="h-1.5 rounded-full bg-[var(--muted)] flex-1 max-w-[80px] overflow-hidden">
                        <motion.div className="h-full rounded-full" style={{ background: 'var(--primary)' }}
                          initial={{ width: 0 }} animate={{ width: `${barPct}%` }}
                          transition={{ duration: 0.6, delay: 0.2 + i * 0.05 }} />
                      </div>
                    </div>
                  </td>

                  {/* Resolution rate */}
                  <td className="px-3 py-2.5">
                    <span className="text-xs font-bold font-mono px-2 py-0.5 rounded-full"
                      style={{ background: resColor + '18', color: resColor }}>
                      {op.tauxResolution}%
                    </span>
                  </td>

                  {/* Average time */}
                  <td className="px-3 py-2.5">
                    <span className="text-xs font-mono" style={{ color: tempsColor }}>
                      {op.tempsMoyenMinutes < 60
                        ? `${Math.round(op.tempsMoyenMinutes)}min`
                        : `${(op.tempsMoyenMinutes / 60).toFixed(1)}h`}
                    </span>
                  </td>

                  {/* SLA */}
                  <td className="px-3 py-2.5">
                    <span className="text-[10px] font-mono font-bold" style={{ color: slaColor }}>
                      {op.slaCompliance}%
                    </span>
                  </td>

                  {/* In progress */}
                  <td className="px-3 py-2.5">
                    <span className="text-xs font-mono font-bold" style={{ color: op.ticketsEnCours > 5 ? 'var(--danger)' : 'var(--text-2)' }}>
                      {op.ticketsEnCours}
                    </span>
                  </td>

                  {/* Canal breakdown mini bar */}
                  <td className="px-3 py-2.5 rounded-r-xl">
                    <div className="flex h-2 rounded-full overflow-hidden w-[60px]">
                      {op.ticketsCall > 0 && <div style={{ flex: op.ticketsCall / totalCanal, background: 'var(--canal-call)' }} />}
                      {op.ticketsEmail > 0 && <div style={{ flex: op.ticketsEmail / totalCanal, background: 'var(--canal-email)' }} />}
                      {op.ticketsWeb > 0 && <div style={{ flex: op.ticketsWeb / totalCanal, background: 'var(--canal-web)' }} />}
                    </div>
                    <div className="flex gap-1 mt-0.5">
                      <span className="text-[7px] font-mono text-[var(--text-3)] flex items-center gap-0.5"><PhoneCall size={7} />{op.ticketsCall}</span>
                      <span className="text-[7px] font-mono text-[var(--text-3)] flex items-center gap-0.5"><Mail size={7} />{op.ticketsEmail}</span>
                      <span className="text-[7px] font-mono text-[var(--text-3)] flex items-center gap-0.5"><Globe size={7} />{op.ticketsWeb}</span>
                    </div>
                  </td>
                </motion.tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </motion.div>
  );
}
