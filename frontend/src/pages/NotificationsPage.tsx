import { useState, useEffect, useCallback, useMemo } from 'react';
import { RefreshCw, Mail, MessageSquare, AlertCircle, CheckCircle, RefreshCcw, Send, Sparkles } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import toast from 'react-hot-toast';
import { notificationService } from '../services/notificationService';
import { ErrorState, EmptyState } from '../components/ui/States';
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
      setError(err instanceof Error ? err.message : 'Erreur de chargement');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchLogs();
  }, [fetchLogs]);

  const handleRetry = async (id: string) => {
    setRetrying(id);
    try {
      await notificationService.retry(id);
      toast.success('🎙 Notification renvoyée avec succès !');
      fetchLogs();
    } catch {
      toast.error('Échec du renvoi de la notification');
    } finally {
      setRetrying(null);
    }
  };

  const filtered = useMemo(() => {
    if (filter === 'ALL') return logs;
    if (filter === 'SENT') return logs.filter(l => l.statut === 'SENT');
    return logs.filter(l => l.statut === 'FAILED' || l.statut !== 'SENT');
  }, [logs, filter]);

  const stats = useMemo(() => {
    const sent = logs.filter(l => l.statut === 'SENT').length;
    return {
      total: logs.length,
      sent,
      failed: logs.length - sent,
    };
  }, [logs]);

  const renderTableContent = () => {
    if (loading && filtered.length === 0) {
      return (
        <div className="p-5 space-y-3">
          {Array.from({ length: 5 }, (_, idx) => `skel-notif-${idx}`).map((key) => (
            <div key={key} className="h-12 bg-[var(--muted)]/40 border border-border/40 rounded-xl animate-pulse" />
          ))}
        </div>
      );
    }
    if (filtered.length === 0) {
      return (
        <div className="py-12">
          <EmptyState 
            icon="mail" 
            title="Aucune notification" 
            subtitle={filter === 'ALL' ? 'Aucune notification envoyée' : 'Aucune notification avec ce filtre'} 
          />
        </div>
      );
    }
    return (
      <table className="aurora-table">
        <thead>
          <tr>
            <th>Type</th>
            <th>Destinataire</th>
            <th>Sujet & Message</th>
            <th>Statut</th>
            <th>Date d'envoi</th>
            <th>Action</th>
          </tr>
        </thead>
        <tbody>
          <AnimatePresence mode="popLayout">
            {filtered.map((n, idx) => (
              <motion.tr 
                key={n.id}
                initial={{ opacity: 0, y: 5 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, x: -15 }}
                transition={{ duration: 0.15, delay: Math.min(idx * 0.02, 0.3) }}
                className="group"
              >
                <td>
                  <span className={`badge border ${n.type === 'EMAIL' ? 'bg-blue-400/10 text-blue-400 border-blue-400/20' : 'bg-violet-500/10 text-violet-400 border-violet-500/20'}`}>
                    {n.type === 'EMAIL' ? <Mail size={11} /> : <MessageSquare size={11} />}
                    <span className="font-mono text-[9px] uppercase tracking-wider font-extrabold">{n.type}</span>
                  </span>
                </td>
                <td className="font-mono text-[11px] text-txt font-semibold">{n.destinataire}</td>
                <td className="max-w-[280px]">
                  <div className="text-[12.5px] font-semibold text-txt tracking-wide truncate">{n.sujet}</div>
                  {n.metadata && typeof n.metadata === 'object' && Object.keys(n.metadata).length > 0 && (
                    <div className="font-mono text-[9px] text-txt-3 mt-0.5 truncate tracking-wider">
                      ID: {(() => {
                        const meta = n.metadata as { ticketId?: string | number; ticketNumero?: string | number } | null | undefined;
                        return String(meta?.ticketId || meta?.ticketNumero || '');
                      })()}
                    </div>
                  )}
                </td>
                <td>
                  <span className={`badge border ${n.statut === 'SENT' ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' : 'bg-rose-500/10 text-rose-400 border-rose-500/20'}`}>
                    <span className={`w-1 h-1 rounded-full ${n.statut === 'SENT' ? 'bg-emerald-400' : 'bg-rose-400 animate-pulse'}`} />
                    <span className="font-mono text-[9px] uppercase tracking-widest font-extrabold">
                      {n.statut === 'SENT' ? 'Délivré' : 'Échoué'}
                    </span>
                  </span>
                </td>
                <td className="font-mono text-[10px] text-txt-3">
                  {new Date(n.createdAt).toLocaleString('fr-FR', {
                    day: '2-digit',
                    month: '2-digit',
                    year: 'numeric',
                    hour: '2-digit',
                    minute: '2-digit',
                    second: '2-digit'
                  })}
                </td>
                <td>
                  {n.statut === 'SENT' ? (
                    <span className="font-mono text-[9px] text-txt-3 select-none">—</span>
                  ) : (
                    <motion.button 
                      whileHover={{ scale: 1.05 }}
                      whileTap={{ scale: 0.95 }}
                      onClick={() => handleRetry(n.id)} 
                      disabled={retrying === n.id} 
                      className="font-mono text-[9px] font-extrabold uppercase tracking-widest rounded-xl px-3 h-7 bg-indigo-500/10 text-indigo-400 border border-indigo-500/25 cursor-pointer hover:bg-indigo-500/20 hover:text-indigo-500 dark:hover:text-indigo-300 hover:border-indigo-400/40 transition-all flex items-center gap-1 disabled:opacity-50 disabled:pointer-events-none"
                    >
                      <RefreshCcw size={10} className={retrying === n.id ? 'animate-spin' : ''} />
                      Renvoyer
                    </motion.button>
                  )}
                </td>
              </motion.tr>
            ))}
          </AnimatePresence>
        </tbody>
      </table>
    );
  };

  if (error) return <ErrorState message={error} onRetry={fetchLogs} />;

  return (
    <div className="flex flex-col gap-6 max-w-[1600px] mx-auto w-full px-1 py-2">
      {/* Premium Header */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-extrabold tracking-tight text-gradient-title">
            Console des Transmissions
          </h1>
          <p className="text-xs text-txt-3 font-mono mt-1 flex items-center gap-1.5">
            <span className="w-1.5 h-1.5 rounded-full bg-indigo-500 animate-pulse mr-1" />{' '}
            Suivi des notifications sortantes (Emails et SMS) et alertes client
          </p>
        </div>
        
        <motion.button 
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.98 }}
          onClick={fetchLogs} 
          className="btn-ghost h-9 px-4 rounded-xl text-xs flex items-center gap-2 cursor-pointer border border-border bg-[var(--surface)] hover:bg-[var(--elevated)] shadow-sm font-medium" 
          disabled={loading}
        >
          <RefreshCw size={12} className={loading ? 'animate-spin text-indigo-400' : 'text-txt-3'} /> 
          Rafraîchir le flux
        </motion.button>
      </div>

      {/* Bento Grid Stats */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        {[
          { 
            key: 'ALL', 
            label: 'Total notifications', 
            value: stats.total, 
            color: 'text-indigo-400', 
            activeColor: 'bg-indigo-500/10 border-indigo-500/30 text-indigo-400',
            icon: <Send size={15} /> 
          },
          { 
            key: 'SENT', 
            label: 'Délivrées', 
            value: stats.sent, 
            color: 'text-emerald-400', 
            activeColor: 'bg-emerald-500/10 border-emerald-500/30 text-emerald-400',
            icon: <CheckCircle size={15} /> 
          },
          { 
            key: 'FAILED', 
            label: 'Échouées', 
            value: stats.failed, 
            color: 'text-rose-400', 
            activeColor: 'bg-rose-500/10 border-rose-500/30 text-rose-400',
            icon: <AlertCircle size={15} /> 
          },
        ].map((s) => {
          const isActive = filter === s.key;
          return (
            <motion.button
              key={s.key}
              whileHover={{ y: -2, scale: 1.01 }}
              whileTap={{ scale: 0.99 }}
              onClick={() => setFilter(s.key as FilterType)}
              className={`relative overflow-hidden rounded-2xl p-5 text-left transition-all duration-300 cursor-pointer border flex flex-col justify-between h-[110px]
                ${isActive
                  ? `${s.activeColor} shadow-[0_0_20px_rgba(99,102,241,0.06)] border-[var(--border)]`
                  : 'card-aurora border-transparent hover:border-border/60'
                }`}
            >
              {isActive && (
                <div className="absolute -right-6 -bottom-6 w-16 h-16 rounded-full bg-indigo-500/10 blur-xl pointer-events-none" />
              )}
              
              <div className="flex items-center justify-between w-full">
                <span className={`p-1.5 rounded-lg border bg-[var(--surface)]/60 ${isActive ? 'border-indigo-500/20 text-indigo-400' : 'border-border/60 text-txt-3'}`}>
                  {s.icon}
                </span>
                {isActive && (
                  <span className="w-1.5 h-1.5 rounded-full bg-indigo-500 shadow-[0_0_8px_#6366f1]" />
                )}
              </div>

              <div className="mt-2">
                <div className="font-mono text-[9px] uppercase tracking-wider text-txt-3 font-semibold mb-0.5">
                  {s.label}
                </div>
                <div className={`text-2xl font-extrabold ${isActive ? 'text-txt' : s.color} tracking-tight`}>
                  {s.value}
                </div>
              </div>
            </motion.button>
          );
        })}
      </div>

      {/* Main Table command center */}
      <div className="card-aurora rounded-2xl border border-border bg-gradient-to-b from-[var(--surface)] to-[var(--elevated)] overflow-hidden shadow-xl">
        <div className="px-5 py-4 border-b border-border/80 flex items-center justify-between bg-black/5">
          <div className="font-mono text-[10px] text-txt-3 uppercase tracking-widest font-extrabold flex items-center gap-1.5">
            <Sparkles size={11} className="text-indigo-400" />
            Historique des envois
          </div>
          <span className="font-mono text-[9px] text-txt-3 bg-[var(--surface)] px-2 py-0.5 border border-border rounded-md">
            {`${filtered.length} entrée${filtered.length > 1 ? 's' : ''}`}
          </span>
        </div>

        <div className="overflow-x-auto">
          {renderTableContent()}
        </div>
      </div>
    </div>
  );
}

