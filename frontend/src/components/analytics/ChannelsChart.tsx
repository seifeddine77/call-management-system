import { motion } from 'framer-motion';
import type { DailyStat } from '../../types/analytics';

const item = { hidden: { opacity: 0, y: 12 }, show: { opacity: 1, y: 0, transition: { duration: 0.35, ease: [0.22, 1, 0.36, 1] as const } } };

interface ChannelsChartProps {
  daily: DailyStat[];
}

export default function ChannelsChart({ daily }: Readonly<ChannelsChartProps>) {
  return (
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
        {[...daily].sort((a, b) => a.date.localeCompare(b.date)).slice(-14).map((d, _, arr) => {
          const total = d.ticketsCall + d.ticketsEmail + d.ticketsWeb;
          const max = Math.max(...arr.map(x => x.ticketsCall + x.ticketsEmail + x.ticketsWeb), 1);
          return (
            <div key={d.date} className="flex flex-col items-center flex-1 gap-1">
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
  );
}
