import { useState, useEffect, useCallback } from 'react';
import { RefreshCw } from 'lucide-react';
import toast from 'react-hot-toast';
import { notificationService } from '../services/notificationService';
import { ErrorState, EmptyState, TableSkeleton } from '../components/ui/States';
import type { NotificationLog } from '../types/notification';

type FilterType = 'ALL' | 'SENT' | 'FAILED';

export default function NotificationsPage() {
  const [logs, setLogs] = useState<NotificationLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<FilterType>('ALL');
  const [retrying, setRetrying] = useState<string | null>(null);

  const fetchLogs = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await notificationService.getLogs();
      setLogs(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Erreur');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchLogs(); }, [fetchLogs]);

  const handleRetry = async (id: string) => {
    setRetrying(id);
    try {
      await notificationService.retry(id);
      toast.success('Notification renvoyée !');
      fetchLogs();
    } catch {
      toast.error('Échec du renvoi');
    } finally {
      setRetrying(null);
    }
  };

  const filtered = filter === 'ALL' ? logs : logs.filter(l => filter === 'SENT' ? l.statut === 'SENT' : l.statut !== 'SENT');

  if (error) return <ErrorState message={error} onRetry={fetchLogs} />;

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2">
        <div>
          <h1 className="text-xl font-extrabold tracking-tight">Notifications</h1>
          <p className="text-xs text-txt-3 font-mono mt-0.5">{logs.length} notification{logs.length > 1 ? 's' : ''} · {logs.filter(l => l.statut !== 'SENT').length} échouée{logs.filter(l => l.statut !== 'SENT').length > 1 ? 's' : ''}</p>
        </div>
        <button onClick={fetchLogs} className="btn-ghost h-9 px-3 rounded-lg text-xs flex items-center gap-1.5 cursor-pointer" disabled={loading}>
          <RefreshCw size={12} className={loading ? 'animate-spin' : ''} /> Actualiser
        </button>
      </div>

      <div className="flex gap-1.5">
        {(['ALL', 'SENT', 'FAILED'] as FilterType[]).map(f => (
          <button key={f} onClick={() => setFilter(f)} className={`chip cursor-pointer ${filter === f ? 'active' : ''}`}>
            {f === 'ALL' ? `Tous (${logs.length})` : f === 'SENT' ? `Envoyés (${logs.filter(l => l.statut === 'SENT').length})` : `Échoués (${logs.filter(l => l.statut !== 'SENT').length})`}
          </button>
        ))}
      </div>

      {loading ? <TableSkeleton rows={5} cols={4} /> : filtered.length === 0 ? (
        <div className="card-aurora rounded-2xl"><EmptyState icon="📭" title="Aucune notification" subtitle={filter !== 'ALL' ? 'Aucune notification avec ce filtre' : 'Aucune notification envoyée'} /></div>
      ) : (
        <div className="card-aurora rounded-2xl overflow-hidden">
          <div className="table-scroll-wrapper">
          <table className="aurora-table">
            <thead><tr><th>Type</th><th>Destinataire</th><th>Sujet</th><th>Statut</th><th>Date</th><th></th></tr></thead>
            <tbody>
              {filtered.map((n) => (
                <tr key={n.id}>
                  <td>
                    <span className={`badge border ${n.type === 'EMAIL' ? 'bg-blue-400/10 text-blue-400 border-blue-400/20' : 'bg-violet-500/10 text-violet-400 border-violet-500/20'}`}>
                      {n.type === 'EMAIL' ? '✉' : '💬'} {n.type}
                    </span>
                  </td>
                  <td className="font-mono text-xs text-txt-2">{n.destinataire}</td>
                  <td className="text-xs max-w-[200px] truncate">{n.sujet}</td>
                  <td>
                    <span className={`badge border ${n.statut === 'SENT' ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' : 'bg-rose-500/10 text-rose-400 border-rose-500/20'}`}>
                      {n.statut === 'SENT' ? '✓ Envoyé' : '✕ Échoué'}
                    </span>
                  </td>
                  <td className="font-mono text-[10px] text-txt-3">{new Date(n.createdAt).toLocaleString('fr-FR')}</td>
                  <td>
                    {n.statut !== 'SENT' && (
                      <button onClick={() => handleRetry(n.id)} disabled={retrying === n.id} className="btn-ghost h-7 px-2.5 rounded text-[10px] cursor-pointer disabled:opacity-50">
                        {retrying === n.id ? '...' : '↻ Retry'}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          </div>
        </div>
      )}
    </div>
  );
}
