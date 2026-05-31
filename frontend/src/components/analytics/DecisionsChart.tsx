import { motion } from 'framer-motion';
import type { DailyStat } from '../../types/analytics';

const item = { hidden: { opacity: 0, y: 12 }, show: { opacity: 1, y: 0, transition: { duration: 0.35, ease: [0.22, 1, 0.36, 1] as const } } };

interface DecisionsChartProps {
  daily: DailyStat[];
}

export default function DecisionsChart({ daily }: Readonly<DecisionsChartProps>) {
  return (
    <motion.div variants={item} className="card-aurora rounded-2xl p-5 relative overflow-hidden group">
      <div className="absolute inset-0 bg-gradient-to-br from-purple-500/[0.02] to-transparent pointer-events-none" />
      <div className="flex items-center justify-between">
        <span className="font-mono text-[9px] text-[var(--text-3)] tracking-wider uppercase">Décisions par jour</span>
        <div className="flex gap-2">
          {[
            { label: 'Fav', color: 'var(--success)' },
            { label: 'Déf', color: 'var(--danger)' },
            { label: 'Étu', color: 'var(--warning)' },
          ].map(l => (
            <span key={l.label} className="flex items-center gap-1 text-[7px] font-mono text-[var(--text-3)] font-bold">
              <span className="w-1.5 h-1.5 rounded-full inline-block" style={{ background: l.color, boxShadow: `0 0 4px ${l.color}` }} />{l.label}
            </span>
          ))}
        </div>
      </div>
      {daily.length > 0 ? (
        <div className="flex items-end gap-1.5 h-[160px] mt-4">
          {[...daily].sort((a, b) => a.date.localeCompare(b.date)).slice(-14).map((d, _, arr) => {
            const total = d.decisionsFavorable + d.decisionsDefavorable + d.decisionsEtude;
            const max = Math.max(...arr.map(x => x.decisionsFavorable + x.decisionsDefavorable + x.decisionsEtude), 1);
            return (
              <div key={d.date} className="flex flex-col items-center flex-1 gap-1.5 group/bar">
                <span className="font-mono text-[7px] text-[var(--text-3)] opacity-0 group-hover/bar:opacity-100 transition-opacity font-bold">{total}</span>
                <div className="w-full flex flex-col rounded-t-md overflow-hidden transition-all duration-300 group-hover/bar:scale-y-105" 
                  style={{ 
                    height: `${(total / max) * 100}%`, 
                    minHeight: '6px',
                    boxShadow: '0 0 8px rgba(0,0,0,0.1)'
                  }}>
                  {d.decisionsFavorable > 0 && <div style={{ flex: d.decisionsFavorable, background: 'var(--success)' }} />}
                  {d.decisionsDefavorable > 0 && <div style={{ flex: d.decisionsDefavorable, background: 'var(--danger)' }} />}
                  {d.decisionsEtude > 0 && <div style={{ flex: d.decisionsEtude, background: 'var(--warning)' }} />}
                </div>
                <span className="font-mono text-[7px] text-[var(--text-3)] font-semibold">{new Date(d.date).getDate()}</span>
              </div>
            );
          })}
        </div>
      ) : <div className="h-[160px] flex items-center justify-center text-xs text-[var(--text-3)]">Aucune donnée</div>}
    </motion.div>
  );
}
