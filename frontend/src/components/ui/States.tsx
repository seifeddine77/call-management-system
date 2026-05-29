import { AlertTriangle } from 'lucide-react';

export function CardSkeleton({ rows = 3 }: Readonly<{ rows?: number }>) {
  return (
    <div className="card-aurora rounded-[14px] p-5 animate-pulse space-y-3">
      <div className="h-3 w-24 bg-[var(--muted)] rounded" />
      {Array.from({ length: rows }).map((_, i) => (
        <div key={`card-row-${i}`} className="h-2.5 bg-[var(--muted)] rounded" style={{ width: `${75 - i * 12}%` }} />
      ))}
    </div>
  );
}

export function TableSkeleton({ rows = 5, cols = 4 }: Readonly<{ rows?: number; cols?: number }>) {
  return (
    <div className="card-aurora rounded-[14px] overflow-hidden animate-pulse">
      <div className="px-5 py-3.5 border-b border-border">
        <div className="h-3 w-32 bg-[var(--muted)] rounded" />
      </div>
      {Array.from({ length: rows }).map((_, r) => (
        <div key={`table-row-${r}`} className="flex items-center gap-4 px-5 py-3 border-b border-border">
          {Array.from({ length: cols }).map((_, c) => (
            <div key={`table-col-${c}`} className="h-2.5 bg-[var(--muted)] rounded flex-1" style={{ maxWidth: c === 0 ? 60 : undefined }} />
          ))}
        </div>
      ))}
    </div>
  );
}

export function KpiSkeleton() {
  return (
    <div className="card-aurora rounded-[14px] p-5 animate-pulse space-y-3">
      <div className="h-2 w-20 bg-[var(--muted)] rounded" />
      <div className="h-7 w-16 bg-[var(--muted)] rounded" />
      <div className="h-2 w-28 bg-[var(--muted)] rounded" />
    </div>
  );
}

export function EmptyState({ icon, title, subtitle, action }: Readonly<{ icon: React.ReactNode; title: string; subtitle: string; action?: React.ReactNode }>) {
  return (
    <div className="flex flex-col items-center justify-center py-16 text-center">
      <div className="mb-3 opacity-40 flex items-center justify-center">{icon}</div>
      <div className="text-sm font-bold text-txt-2 mb-1">{title}</div>
      <div className="text-xs text-txt-3 mb-4 max-w-[280px]">{subtitle}</div>
      {action}
    </div>
  );
}

export function ErrorState({ message, onRetry }: Readonly<{ message: string; onRetry?: () => void }>) {
  return (
    <div className="flex flex-col items-center justify-center py-16 text-center">
      <div className="mb-3 flex items-center justify-center text-amber-400"><AlertTriangle size={36} /></div>
      <div className="text-sm font-bold text-danger mb-1">Erreur</div>
      <div className="text-xs text-txt-3 mb-4">{message}</div>
      {onRetry && (
        <button onClick={onRetry} className="btn-ghost h-8 px-4 rounded-lg text-xs cursor-pointer">↻ Réessayer</button>
      )}
    </div>
  );
}
