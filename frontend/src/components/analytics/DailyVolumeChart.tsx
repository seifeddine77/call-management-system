import { motion } from 'framer-motion';
import type { DailyStat } from '../../types/analytics';

const item = { hidden: { opacity: 0, y: 12 }, show: { opacity: 1, y: 0, transition: { duration: 0.35, ease: [0.22, 1, 0.36, 1] as const } } };

interface DailyVolumeChartProps {
  daily: DailyStat[];
}

export default function DailyVolumeChart({ daily }: Readonly<DailyVolumeChartProps>) {
  return (
    <motion.div variants={item} className="card-aurora rounded-2xl p-5 relative overflow-hidden group">
      <div className="absolute inset-0 bg-gradient-to-br from-indigo-500/[0.02] to-transparent pointer-events-none" />
      <span className="font-mono text-[9px] text-[var(--text-3)] tracking-wider uppercase">Volume quotidien</span>
      {daily.length > 0 ? (
        <div className="flex items-end gap-1.5 h-[160px] mt-4">
          {[...daily].sort((a, b) => a.date.localeCompare(b.date)).slice(-14).map((d, i, arr) => {
            const max = Math.max(...arr.map(x => x.totalTickets), 1);
            const pct = (d.totalTickets / max) * 100;
            return (
              <div key={d.date} className="flex flex-col items-center flex-1 gap-1.5 group/bar">
                <span className="font-mono text-[7px] text-[var(--text-3)] opacity-0 group-hover/bar:opacity-100 transition-opacity font-bold">{d.totalTickets}</span>
                <div className="w-full flex flex-col items-center" style={{ height: `${pct}%`, minHeight: '6px' }}>
                  <div className="w-full rounded-t-md flex-1 transition-all duration-300 group-hover/bar:scale-y-105" 
                    style={{ 
                      background: 'linear-gradient(180deg, var(--primary) 0%, rgba(99,91,255,0.4) 100%)',
                      boxShadow: '0 0 12px rgba(99,91,255,0.15)',
                      opacity: 0.6 + (i / arr.length) * 0.4 
                    }} />
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
