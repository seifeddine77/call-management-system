import { useState, useEffect, useCallback, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  ShieldCheck, ArrowLeft, RefreshCw, Phone, Mail, Globe, Clock,
  CheckCircle2, XCircle, AlertTriangle, Bot, FileText, Camera,
  Users, Zap, Database, Brain, UserCheck, Timer, Activity,
  Lock, Eye, Shield, Download
} from 'lucide-react';
import jsPDF from 'jspdf';
import { ticketService } from '../services/ticketService';
import { decisionService } from '../services/decisionService';
import { notificationService } from '../services/notificationService';
import { CardSkeleton, ErrorState } from '../components/ui/States';
import type { Ticket, TicketAction } from '../types/ticket';
import type { DecisionResult } from '../types/decision';
import type { NotificationLog } from '../types/notification';

const container = { hidden: {}, show: { transition: { staggerChildren: 0.07 } } };
const item = { hidden: { opacity: 0, y: 12 }, show: { opacity: 1, y: 0, transition: { duration: 0.4, ease: [0.22, 1, 0.36, 1] as const } } };

/* ── Unified timeline event ── */
interface TimelineEvent {
  id: string;
  timestamp: string;
  source: 'ticket' | 'decision' | 'notification';
  type: string;
  title: string;
  detail: string;
  color: string;
  icon: React.ReactNode;
}

/* Helper: minutes → human */
const fmtDuration = (ms: number) => {
  const m = Math.round(ms / 60000);
  if (m < 1) return '< 1min';
  if (m < 60) return `${m}min`;
  const h = Math.floor(m / 60);
  return `${h}h ${m % 60}min`;
};

const actionLabel: Record<string, string> = {
  CREE: 'Ticket créé',
  ASSIGNE: 'Assigné à un opérateur',
  STATUT_CHANGE: 'Changement de statut',
  TRANSCRIPTION_AJOUTEE: 'Transcription audio ajoutée',
  PHOTOS_AJOUTEES: 'Photos sinistre ajoutées',
  ANONYMISE: 'Données anonymisées (RGPD)',
};

const actionIcon: Record<string, React.ReactNode> = {
  CREE: <Zap size={12} />,
  ASSIGNE: <Users size={12} />,
  STATUT_CHANGE: <Activity size={12} />,
  TRANSCRIPTION_AJOUTEE: <FileText size={12} />,
  PHOTOS_AJOUTEES: <Camera size={12} />,
  ANONYMISE: <Lock size={12} />,
};

export default function AuditPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [ticket, setTicket] = useState<Ticket | null>(null);
  const [decision, setDecision] = useState<DecisionResult | null>(null);
  const [notifications, setNotifications] = useState<NotificationLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);

  /* ── If id==='latest', redirect to last ticket ── */
  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      let ticketId = id;
      if (id === 'latest') {
        const page = await ticketService.getTickets({ page: 0, size: 1 });
        if (page.content.length > 0) {
          ticketId = page.content[0].id;
          navigate(`/audit/${ticketId}`, { replace: true });
        } else {
          setError('Aucun ticket trouvé');
          setLoading(false);
          return;
        }
      }
      const [t, d, n] = await Promise.all([
        ticketService.getTicketById(ticketId!),
        decisionService.getByTicket(ticketId!).catch(() => null),
        notificationService.getLogs({ size: 100 }).catch(() => [] as NotificationLog[]),
      ]);
      setTicket(t);
      setDecision(d);
      // Filter notifications related to this ticket (by metadata or by client email)
      const related = (n || []).filter((log) => {
        const meta = log.metadata || {};
        return (meta as Record<string, unknown>).ticketId === ticketId
          || (meta as Record<string, unknown>).ticketNumero === t.numero
          || (meta as Record<string, unknown>).numero === t.numero
          || (t.clientEmail && log.destinataire === t.clientEmail);
      });
      setNotifications(related);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Erreur de chargement');
    } finally {
      setLoading(false);
    }
  }, [id, navigate]);

  useEffect(() => { fetchData(); }, [fetchData]);

  /* ── Build unified timeline ── */
  const timeline = useMemo((): TimelineEvent[] => {
    if (!ticket) return [];
    const events: TimelineEvent[] = [];

    // 1. Ticket actions
    (ticket.actions || []).forEach((a: TicketAction) => {
      const detail = a.action === 'STATUT_CHANGE' && a.oldValue && a.newValue
        ? `${a.oldValue} → ${a.newValue}${a.notes ? ` · ${a.notes}` : ''}`
        : a.action === 'ASSIGNE' && a.newValue
          ? `Opérateur: ${a.newValue}${a.userName ? ` · Par: ${a.userName}` : ''}`
          : `${a.userName || 'Système'}${a.notes ? ` · ${a.notes}` : ''}`;
      events.push({
        id: `action-${a.id}`,
        timestamp: a.createdAt,
        source: 'ticket',
        type: a.action,
        title: actionLabel[a.action] || a.action,
        detail,
        color: '#8B5CF6',
        icon: actionIcon[a.action] || <Activity size={12} />,
      });
    });

    // 2. Decision events
    if (decision) {
      events.push({
        id: `decision-created`,
        timestamp: (decision as any).createdAt || ticket.updatedAt,
        source: 'decision',
        type: 'DECISION_IA',
        title: `Décision IA : ${decision.decision}`,
        detail: `Score: ${decision.score}% · Source: ${decision.source}${decision.rulesApplied.length > 0 ? ` · Règles: ${decision.rulesApplied.join(', ')}` : ''} · ${decision.motif}`,
        color: '#10B981',
        icon: <Bot size={12} />,
      });
      if (decision.finalDecision) {
        events.push({
          id: `decision-finalized`,
          timestamp: (decision as any).overriddenAt || ticket.updatedAt,
          source: 'decision',
          type: 'DECISION_FINALE',
          title: `Décision superviseur : ${decision.finalDecision}`,
          detail: decision.overrideMotif || 'Validée',
          color: '#10B981',
          icon: <UserCheck size={12} />,
        });
      }
    }

    // 3. Notification events
    notifications.forEach((n, i) => {
      events.push({
        id: `notif-${n.id || i}`,
        timestamp: n.createdAt,
        source: 'notification',
        type: n.type,
        title: `Email: ${n.type.replace(/_/g, ' ')}`,
        detail: `→ ${n.destinataire} · ${n.statut === 'SENT' ? '✓ Envoyé' : n.statut === 'FAILED' ? '✕ Échec' : '◷ En attente'}${n.sujet ? ` · ${n.sujet}` : ''}`,
        color: '#3B82F6',
        icon: <Mail size={12} />,
      });
    });

    // Sort chronologically
    return events.sort((a, b) => {
      const ta = new Date(a.timestamp.endsWith('Z') ? a.timestamp : a.timestamp + 'Z').getTime();
      const tb = new Date(b.timestamp.endsWith('Z') ? b.timestamp : b.timestamp + 'Z').getTime();
      return ta - tb;
    });
  }, [ticket, decision, notifications]);

  /* ── RGPD compliance checks (canal-aware, based on real project fields) ── */
  const rgpd = useMemo(() => {
    if (!ticket) return [];
    const isCall = ticket.canal === 'CALL';
    const isEmail = ticket.canal === 'EMAIL';
    const hasConsent = ticket.consentementEnregistrement;
    const hasAudio = !!ticket.audioPath;
    const hasTranscription = !!ticket.transcription;
    const hasNotif = notifications.length > 0;
    const hasDecisionTrace = !!decision;
    const dataMinimization = !ticket.description || ticket.description.length < 2000;
    const isClosed = ticket.statut === 'FERME' || ticket.statut === 'RESOLU';

    const checks: { label: string; desc: string; ok: boolean | null; icon: React.ReactNode; na?: boolean; recommendation?: string }[] = [];

    // 1. Consentement — always applicable
    checks.push({
      label: isCall ? 'Consentement enregistrement (IVR)' : isEmail ? 'Consentement implicite (email initié)' : 'Consentement explicite (formulaire)',
      desc: 'Art. 6 RGPD — Base légale du traitement',
      ok: hasConsent,
      icon: hasConsent ? <CheckCircle2 size={14} /> : <XCircle size={14} />,
      recommendation: !hasConsent ? (isCall ? 'Vérifier le message IVR de consentement' : 'Le client doit accepter les conditions de traitement') : undefined,
    });

    // 2. Audio & Transcription — only for CALL canal
    if (isCall) {
      checks.push({
        label: 'Audio enregistré & stocké',
        desc: 'Art. 32 — Sécurité du traitement des données vocales',
        ok: hasAudio,
        icon: hasAudio ? <Lock size={14} /> : <AlertTriangle size={14} />,
        recommendation: !hasAudio ? 'Aucun enregistrement audio associé à cet appel' : undefined,
      });
      checks.push({
        label: 'Transcription générée (Whisper)',
        desc: 'Art. 22 — Transparence du traitement automatisé',
        ok: hasTranscription,
        icon: hasTranscription ? <FileText size={14} /> : <AlertTriangle size={14} />,
        recommendation: !hasTranscription ? 'Lancer la transcription pour documenter l\'échange' : undefined,
      });
    }

    // 3. Photos — only for WEB (sinistre photos)
    if (!isCall && !isEmail) {
      const hasPhotos = !!ticket.photosPaths && ticket.photosPaths !== '[]';
      checks.push({
        label: 'Photos sinistre sécurisées',
        desc: 'Art. 32 — Protection des données visuelles',
        ok: hasPhotos ? true : null,
        icon: hasPhotos ? <Camera size={14} /> : <AlertTriangle size={14} />,
        na: !hasPhotos,
        recommendation: undefined,
      });
    }

    // 4. Traçabilité décisionnelle — always
    checks.push({
      label: 'Traçabilité décisionnelle IA',
      desc: 'Art. 22 — Décision automatisée explicable',
      ok: hasDecisionTrace,
      icon: hasDecisionTrace ? <Bot size={14} /> : <XCircle size={14} />,
      recommendation: !hasDecisionTrace ? 'Lancer l\'analyse IA pour documenter la décision' : undefined,
    });

    // 5. Notifications — always
    checks.push({
      label: 'Notification client envoyée',
      desc: 'Art. 13/14 — Droit à l\'information',
      ok: hasNotif,
      icon: hasNotif ? <Mail size={14} /> : <XCircle size={14} />,
      recommendation: !hasNotif ? 'Informer le client du traitement de sa demande' : undefined,
    });

    // 6. Minimisation des données — always
    checks.push({
      label: 'Minimisation des données',
      desc: 'Art. 5(1)(c) — Données adéquates et pertinentes',
      ok: dataMinimization,
      icon: dataMinimization ? <CheckCircle2 size={14} /> : <AlertTriangle size={14} />,
      recommendation: !dataMinimization ? 'Réduire le volume de données collectées' : undefined,
    });

    // 7. Droit à l'effacement — check if closed ticket is old enough
    if (isClosed) {
      const closedDays = Math.floor((Date.now() - new Date(ticket.updatedAt.endsWith('Z') ? ticket.updatedAt : ticket.updatedAt + 'Z').getTime()) / 86400000);
      const needsDeletion = closedDays > 90;
      checks.push({
        label: `Droit à l'effacement (${closedDays}j depuis clôture)`,
        desc: 'Art. 17 — Suppression après délai de rétention',
        ok: !needsDeletion,
        icon: needsDeletion ? <Eye size={14} /> : <Shield size={14} />,
        recommendation: needsDeletion ? `Ticket clôturé depuis ${closedDays} jours — planifier l'anonymisation` : undefined,
      });
    }

    return checks;
  }, [ticket, notifications, decision]);

  const rgpdScore = useMemo(() => {
    const applicable = rgpd.filter(r => !(r as any).na);
    if (applicable.length === 0) return 0;
    return Math.round((applicable.filter(r => r.ok).length / applicable.length) * 100);
  }, [rgpd]);

  /* ── Performance metrics ── */
  const perf = useMemo(() => {
    if (!ticket) return null;
    const created = new Date(ticket.createdAt.endsWith('Z') ? ticket.createdAt : ticket.createdAt + 'Z').getTime();
    const updated = new Date(ticket.updatedAt.endsWith('Z') ? ticket.updatedAt : ticket.updatedAt + 'Z').getTime();
    const assignAction = (ticket.actions || []).find(a => a.action === 'ASSIGNE');
    const resolveAction = (ticket.actions || []).find(a => a.action === 'STATUT_CHANGE' && a.newValue === 'RESOLU');
    const assignTime = assignAction ? new Date(assignAction.createdAt.endsWith('Z') ? assignAction.createdAt : assignAction.createdAt + 'Z').getTime() - created : null;
    const resolveTime = resolveAction ? new Date(resolveAction.createdAt.endsWith('Z') ? resolveAction.createdAt : resolveAction.createdAt + 'Z').getTime() - created : null;
    const totalDuration = updated - created;
    const slaOk = totalDuration < 4 * 3600000;
    return { totalDuration, assignTime, resolveTime, slaOk, actionCount: (ticket.actions || []).length };
  }, [ticket]);

  /* ── PDF Export ── */
  const handleExportPDF = useCallback(async () => {
    if (!ticket) return;
    setExporting(true);
    try {
      const doc = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'a4' });
      const W = 210; const M = 15; const CW = W - 2 * M;
      let y = 20;

      // Header
      doc.setFillColor(99, 91, 255); doc.rect(0, 0, W, 35, 'F');
      doc.setTextColor(255); doc.setFontSize(18); doc.setFont('helvetica', 'bold');
      doc.text('RAPPORT D\'AUDIT', M, 15);
      doc.setFontSize(9); doc.setFont('helvetica', 'normal');
      doc.text(`${ticket.numero} — Généré le ${new Date().toLocaleString('fr-FR')}`, M, 22);
      doc.text('AutoAssist — Centre d\'Investigation', M, 28);
      y = 45;

      // Client info
      doc.setTextColor(60); doc.setFontSize(12); doc.setFont('helvetica', 'bold');
      doc.text('1. Informations du ticket', M, y); y += 8;
      doc.setFontSize(9); doc.setFont('helvetica', 'normal');
      const info = [
        ['Client', ticket.clientNom], ['N° Ticket', ticket.numero],
        ['Canal', ticket.canal], ['Statut', ticket.statut],
        ['Email', ticket.clientEmail || 'N/A'], ['Tél', ticket.clientTel || 'N/A'],
        ['Créé le', new Date(ticket.createdAt).toLocaleString('fr-FR')],
      ];
      info.forEach(([k, v]) => { doc.setFont('helvetica', 'bold'); doc.text(`${k}:`, M, y); doc.setFont('helvetica', 'normal'); doc.text(String(v), M + 35, y); y += 5; });
      y += 5;

      // Performance
      if (perf) {
        doc.setFontSize(12); doc.setFont('helvetica', 'bold');
        doc.text('2. Métriques de performance', M, y); y += 8;
        doc.setFontSize(9); doc.setFont('helvetica', 'normal');
        doc.text(`Durée totale: ${fmtDuration(perf.totalDuration)}`, M, y); y += 5;
        doc.text(`Assignation: ${perf.assignTime ? fmtDuration(perf.assignTime) : 'N/A'}`, M, y); y += 5;
        doc.text(`Résolution: ${perf.resolveTime ? fmtDuration(perf.resolveTime) : 'N/A'}`, M, y); y += 5;
        doc.text(`SLA: ${perf.slaOk ? 'CONFORME (< 4h)' : 'DÉPASSÉ'}`, M, y); y += 5;
        doc.text(`Nombre d'actions: ${perf.actionCount}`, M, y); y += 8;
      }

      // Decision IA
      if (decision) {
        doc.setFontSize(12); doc.setFont('helvetica', 'bold');
        doc.text('3. Pipeline décisionnel IA', M, y); y += 8;
        doc.setFontSize(9); doc.setFont('helvetica', 'normal');
        doc.text(`Décision: ${decision.decision}`, M, y); y += 5;
        doc.text(`Score de confiance: ${decision.score}%`, M, y); y += 5;
        doc.text(`Source: ${decision.source}`, M, y); y += 5;
        if (decision.rulesApplied.length > 0) { doc.text(`Règles: ${decision.rulesApplied.join(', ')}`, M, y); y += 5; }
        const motifLines = doc.splitTextToSize(`Motif: ${decision.motif}`, CW);
        doc.text(motifLines, M, y); y += motifLines.length * 4 + 3;
        if (decision.finalDecision) { doc.text(`Décision superviseur: ${decision.finalDecision}`, M, y); y += 5; }
        y += 5;
      }

      // Timeline
      if (y > 240) { doc.addPage(); y = 20; }
      doc.setFontSize(12); doc.setFont('helvetica', 'bold');
      doc.text(`4. Timeline (${timeline.length} événements)`, M, y); y += 8;
      doc.setFontSize(8); doc.setFont('helvetica', 'normal');
      timeline.forEach(ev => {
        if (y > 270) { doc.addPage(); y = 20; }
        const ts = new Date(ev.timestamp.endsWith('Z') ? ev.timestamp : ev.timestamp + 'Z').toLocaleString('fr-FR');
        doc.setFont('helvetica', 'bold'); doc.text(`[${ts}] ${ev.title}`, M, y);
        doc.setFont('helvetica', 'normal');
        const detailLines = doc.splitTextToSize(ev.detail, CW - 5);
        doc.text(detailLines, M + 2, y + 4); y += 4 + detailLines.length * 3.5 + 3;
      });

      // RGPD
      if (y > 240) { doc.addPage(); y = 20; }
      y += 3;
      doc.setFontSize(12); doc.setFont('helvetica', 'bold');
      doc.text(`5. Conformité RGPD — Score: ${rgpdScore}%`, M, y); y += 8;
      doc.setFontSize(9);
      rgpd.forEach(r => {
        if (y > 275) { doc.addPage(); y = 20; }
        const status = (r as any).na ? 'N/A' : r.ok ? '✓ CONFORME' : '✕ NON CONFORME';
        doc.setFont('helvetica', 'bold'); doc.text(`${status} — ${r.label}`, M, y);
        doc.setFont('helvetica', 'normal'); doc.text((r as any).desc || '', M + 2, y + 4); y += 10;
      });

      // Footer
      const pageCount = doc.getNumberOfPages();
      for (let i = 1; i <= pageCount; i++) {
        doc.setPage(i);
        doc.setFontSize(7); doc.setTextColor(150);
        doc.text(`AutoAssist — Rapport d'audit confidentiel — Page ${i}/${pageCount}`, M, 290);
      }

      doc.save(`audit_${ticket.numero}_${new Date().toISOString().slice(0, 10)}.pdf`);
    } catch { /* silent */ }
    finally { setExporting(false); }
  }, [ticket, decision, perf, timeline, rgpd, rgpdScore]);

  /* ── Pipeline steps ── */
  const pipelineSteps = useMemo(() => {
    if (!decision) return null;
    const src = decision.source;
    return [
      { label: 'Cache Redis', sub: 'SHA-256', active: src === 'CACHE', hit: src === 'CACHE', icon: <Database size={16} /> },
      { label: 'Rules Engine', sub: `${decision.rulesApplied.length} règle(s)`, active: src === 'RULES' || src === 'HYBRID', hit: decision.rulesApplied.length > 0, icon: <Zap size={16} /> },
      { label: 'LLM (Gemma)', sub: `Score: ${decision.score}%`, active: src === 'AI' || src === 'HYBRID', hit: src === 'AI' || src === 'HYBRID', icon: <Brain size={16} /> },
    ];
  }, [decision]);

  const canalIcon = { CALL: <Phone size={13} />, EMAIL: <Mail size={13} />, WEB: <Globe size={13} /> };
  const canalColor = { CALL: '#8B5CF6', EMAIL: '#3B82F6', WEB: '#10B981' };

  if (loading) return <div className="space-y-4"><CardSkeleton rows={3} /><CardSkeleton rows={6} /></div>;
  if (error || !ticket) return <ErrorState message={error || 'Ticket introuvable'} onRetry={fetchData} />;

  return (
    <motion.div variants={container} initial="hidden" animate="show" className="space-y-4">
      {/* ── Header ── */}
      <motion.div variants={item} className="flex items-center gap-3">
        <button onClick={() => navigate(-1)} className="btn-ghost h-8 w-8 rounded-lg flex items-center justify-center cursor-pointer">
          <ArrowLeft size={15} />
        </button>
        <div className="flex-1">
          <div className="flex items-center gap-2">
            <ShieldCheck size={18} className="text-[var(--primary)]" />
            <h1 className="text-lg font-extrabold tracking-tight" style={{ letterSpacing: '-0.03em' }}>Centre d'Audit</h1>
          </div>
          <p className="font-mono text-[9px] text-[var(--text-3)] uppercase tracking-wider">
            Investigation complète · {ticket.numero}
          </p>
        </div>
        <button onClick={handleExportPDF} disabled={exporting} className="btn-cta h-8 px-3.5 rounded-lg text-xs flex items-center gap-1.5 cursor-pointer text-white disabled:opacity-50">
          <Download size={13} className={exporting ? 'animate-bounce' : ''} /> {exporting ? 'Export…' : 'Export PDF'}
        </button>
        <button onClick={fetchData} className="btn-ghost h-8 px-3 rounded-lg text-xs flex items-center gap-1.5 cursor-pointer">
          <RefreshCw size={13} /> Actualiser
        </button>
      </motion.div>

      {/* ── Ticket Summary Banner ── */}
      <motion.div variants={item} className="relative overflow-hidden rounded-2xl p-5" style={{
        background: 'linear-gradient(135deg, rgba(99,91,255,0.08) 0%, rgba(0,212,170,0.04) 50%, rgba(167,139,250,0.06) 100%)',
        border: '1px solid var(--border)',
      }}>
        <div className="absolute top-0 left-0 right-0 h-[2px]" style={{ background: `linear-gradient(90deg, ${canalColor[ticket.canal] || '#635BFF'}, #A78BFA, #00D4AA)` }} />
        <div className="flex flex-wrap items-center gap-4">
          <div className="w-10 h-10 rounded-xl flex items-center justify-center" style={{ background: `${canalColor[ticket.canal]}20`, color: canalColor[ticket.canal] }}>
            {canalIcon[ticket.canal] || <Phone size={16} />}
          </div>
          <div className="flex-1 min-w-[200px]">
            <h2 className="text-base font-extrabold">{ticket.clientNom}</h2>
            <div className="flex items-center gap-3 mt-0.5 flex-wrap">
              <span className="font-mono text-[10px] text-[var(--primary)]">{ticket.numero}</span>
              <span className="font-mono text-[10px] text-[var(--text-3)]">Canal: {ticket.canal}</span>
              <span className="font-mono text-[10px] text-[var(--text-3)]">{ticket.clientEmail || ticket.clientTel || ''}</span>
            </div>
          </div>
          <div className="flex gap-2">
            <span className="badge border" style={{ background: 'var(--primary-soft)', color: 'var(--primary)', borderColor: 'rgba(99,91,255,0.2)' }}>{ticket.statut}</span>
            <span className="badge border" style={{ background: `${canalColor[ticket.canal]}15`, color: canalColor[ticket.canal] }}>{ticket.canal}</span>
            {perf && <span className={`badge border ${perf.slaOk ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' : 'bg-rose-500/10 text-rose-400 border-rose-500/20'}`}>
              SLA: {perf.slaOk ? '✓ OK' : '✕ Dépassé'}
            </span>}
          </div>
        </div>
        {perf && (
          <div className="flex items-center gap-6 mt-3 flex-wrap">
            {[
              { label: 'Durée totale', value: fmtDuration(perf.totalDuration), icon: <Timer size={11} /> },
              { label: 'Assignation', value: perf.assignTime ? fmtDuration(perf.assignTime) : 'N/A', icon: <Users size={11} /> },
              { label: 'Résolution', value: perf.resolveTime ? fmtDuration(perf.resolveTime) : 'N/A', icon: <CheckCircle2 size={11} /> },
              { label: 'Actions', value: `${perf.actionCount}`, icon: <Activity size={11} /> },
            ].map(m => (
              <div key={m.label} className="flex items-center gap-1.5">
                <span className="text-[var(--text-3)]">{m.icon}</span>
                <span className="font-mono text-[9px] text-[var(--text-3)]">{m.label}:</span>
                <span className="font-mono text-[10px] font-bold">{m.value}</span>
              </div>
            ))}
          </div>
        )}
      </motion.div>

      {/* ── Main grid: Timeline + Panels ── */}
      <div className="grid grid-cols-1 lg:grid-cols-[1fr_340px] gap-4">
        {/* LEFT: Timeline */}
        <motion.div variants={item} className="card-aurora rounded-2xl p-5">
          <div className="flex items-center gap-2 mb-4">
            <Clock size={14} className="text-[var(--primary)]" />
            <h3 className="text-sm font-bold">Timeline unifiée cross-service</h3>
            <span className="font-mono text-[9px] px-2 py-0.5 rounded-full bg-[var(--primary-soft)] text-[var(--primary)]">{timeline.length} événements</span>
          </div>

          <div className="flex flex-col">
            {timeline.map((ev, i) => (
              <motion.div key={ev.id} initial={{ opacity: 0, x: -10 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: i * 0.04 }} className="flex gap-3 pb-4 last:pb-0 group">
                {/* Vertical line + dot */}
                <div className="flex flex-col items-center w-6 flex-shrink-0">
                  <div className="w-3 h-3 rounded-full flex items-center justify-center mt-1 z-[1] ring-2 ring-[var(--surface)]" style={{ background: ev.color }}>
                    <span className="text-white text-[6px]">{ev.source === 'ticket' ? 'T' : ev.source === 'decision' ? 'D' : 'N'}</span>
                  </div>
                  {i < timeline.length - 1 && <div className="flex-1 w-px mt-1" style={{ background: `${ev.color}30` }} />}
                </div>

                {/* Content */}
                <div className="flex-1 min-w-0 pb-1">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="flex items-center gap-1 text-xs font-bold" style={{ color: ev.color }}>{ev.icon} {ev.title}</span>
                    <span className="font-mono text-[8px] px-1.5 py-0.5 rounded" style={{ background: `${ev.color}12`, color: ev.color, border: `1px solid ${ev.color}25` }}>
                      {ev.source === 'ticket' ? 'ticket-service' : ev.source === 'decision' ? 'decision-service' : 'notification-service'}
                    </span>
                  </div>
                  <p className="text-[11px] text-[var(--text-2)] mt-0.5 leading-relaxed">{ev.detail}</p>
                  <span className="font-mono text-[9px] text-[var(--text-3)] mt-0.5 block">
                    {new Date(ev.timestamp.endsWith('Z') ? ev.timestamp : ev.timestamp + 'Z').toLocaleString('fr-FR')}
                  </span>
                </div>
              </motion.div>
            ))}
            {timeline.length === 0 && (
              <div className="text-center py-8 text-xs text-[var(--text-3)]">Aucun événement enregistré</div>
            )}
          </div>
        </motion.div>

        {/* RIGHT: Panels */}
        <div className="flex flex-col gap-4">
          {/* Pipeline IA */}
          <motion.div variants={item} className="card-aurora rounded-2xl overflow-hidden relative">
            <div className="absolute top-0 left-0 right-0 h-[2px] bg-gradient-to-r from-teal-400 to-indigo-500" />
            <div className="p-5">
              <div className="flex items-center gap-2 mb-4">
                <Bot size={14} className="text-[var(--primary)]" />
                <h3 className="text-sm font-bold">Pipeline décisionnel</h3>
              </div>

              {pipelineSteps ? (
                <>
                  <div className="space-y-2">
                    {pipelineSteps.map((step, i) => (
                      <motion.div key={step.label} initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }} transition={{ delay: 0.2 + i * 0.12 }}>
                        <div className={`p-3 rounded-xl border transition-all ${step.active ? 'border-[var(--primary)] bg-[var(--primary-soft)]' : step.hit ? 'border-emerald-500/20 bg-emerald-500/5' : 'border-[var(--border)] bg-[var(--elevated)]'}`}>
                          <div className="flex items-center gap-2">
                            <div className={`w-7 h-7 rounded-lg flex items-center justify-center ${step.active ? 'bg-[var(--primary)] text-white' : step.hit ? 'bg-emerald-500/20 text-emerald-400' : 'bg-[var(--muted)] text-[var(--text-3)]'}`}>
                              {step.icon}
                            </div>
                            <div className="flex-1">
                              <div className="text-xs font-bold">{step.label}</div>
                              <div className="font-mono text-[9px] text-[var(--text-3)]">{step.sub}</div>
                            </div>
                            <span className={`text-[10px] font-mono font-bold ${step.active ? 'text-[var(--primary)]' : step.hit ? 'text-emerald-400' : 'text-[var(--text-3)]'}`}>
                              {step.active ? '● ACTIF' : step.hit ? '✓' : '○ SKIP'}
                            </span>
                          </div>
                        </div>
                        {i < pipelineSteps.length - 1 && (
                          <div className="flex justify-center py-1"><div className="w-px h-3 bg-[var(--border)]" /></div>
                        )}
                      </motion.div>
                    ))}
                  </div>

                  {/* Final decision */}
                  {decision?.finalDecision && (
                    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 0.6 }}
                      className="mt-3 p-3 rounded-xl border text-center"
                      style={{
                        background: decision.finalDecision === 'FAVORABLE' ? 'rgba(16,185,129,0.08)' : 'rgba(239,68,68,0.08)',
                        borderColor: decision.finalDecision === 'FAVORABLE' ? 'rgba(16,185,129,0.2)' : 'rgba(239,68,68,0.2)',
                      }}>
                      <UserCheck size={16} className="mx-auto mb-1" style={{ color: decision.finalDecision === 'FAVORABLE' ? '#10B981' : '#EF4444' }} />
                      <div className="text-xs font-bold" style={{ color: decision.finalDecision === 'FAVORABLE' ? '#10B981' : '#EF4444' }}>
                        Superviseur: {decision.finalDecision}
                      </div>
                      {decision.overrideMotif && <div className="text-[10px] text-[var(--text-2)] mt-0.5">{decision.overrideMotif}</div>}
                    </motion.div>
                  )}
                </>
              ) : (
                <div className="text-center py-6 text-xs text-[var(--text-3)]">
                  <Bot size={24} className="mx-auto mb-2 opacity-30" />
                  Aucune décision IA pour ce ticket
                </div>
              )}
            </div>
          </motion.div>

          {/* RGPD Compliance — Enhanced */}
          <motion.div variants={item} className="card-aurora rounded-2xl overflow-hidden relative">
            <div className="absolute top-0 left-0 right-0 h-[2px] bg-gradient-to-r from-amber-400 to-rose-500" />
            <div className="p-5">
              <div className="flex items-center gap-2 mb-1">
                <Shield size={14} className="text-amber-400" />
                <h3 className="text-sm font-bold">Conformité RGPD</h3>
              </div>
              {/* Score global */}
              <div className="mb-3">
                <div className="flex items-center justify-between mb-1">
                  <span className="font-mono text-[10px] text-[var(--text-3)]">Score de conformité</span>
                  <span className={`font-mono text-sm font-extrabold ${rgpdScore >= 80 ? 'text-emerald-400' : rgpdScore >= 50 ? 'text-amber-400' : 'text-rose-400'}`}>{rgpdScore}%</span>
                </div>
                <div className="h-2 rounded-full bg-[var(--muted)] overflow-hidden">
                  <motion.div initial={{ width: 0 }} animate={{ width: `${rgpdScore}%` }} transition={{ duration: 1, ease: 'easeOut' }}
                    className="h-full rounded-full" style={{ background: rgpdScore >= 80 ? '#10B981' : rgpdScore >= 50 ? '#F59E0B' : '#EF4444' }} />
                </div>
              </div>
              <div className="space-y-1.5">
                {rgpd.map(r => (
                  <div key={r.label} className={`p-2.5 rounded-lg border ${(r as any).na ? 'bg-zinc-500/5 border-zinc-500/10' : r.ok ? 'bg-emerald-500/5 border-emerald-500/15' : 'bg-rose-500/5 border-rose-500/15'}`}>
                    <div className="flex items-center gap-2">
                      <span style={{ color: (r as any).na ? 'var(--text-3)' : r.ok ? '#10B981' : '#EF4444' }}>{r.icon}</span>
                      <span className="text-[11px] font-medium flex-1">{r.label}</span>
                      <span className={`font-mono text-[8px] font-bold ${(r as any).na ? 'text-[var(--text-3)]' : r.ok ? 'text-emerald-400' : 'text-rose-400'}`}>
                        {(r as any).na ? 'N/A' : r.ok ? '✓ CONFORME' : '✕ NON'}
                      </span>
                    </div>
                    <div className="font-mono text-[8px] text-[var(--text-3)] mt-0.5 ml-[22px]">{(r as any).desc}</div>
                    {(r as any).recommendation && (
                      <div className="mt-1 ml-[22px] text-[9px] text-amber-400 flex items-center gap-1">
                        <AlertTriangle size={9} /> {(r as any).recommendation}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>
          </motion.div>

          {/* Navigate to ticket detail */}
          <motion.div variants={item}>
            <button onClick={() => navigate(`/tickets/${ticket.id}`)}
              className="w-full btn-ghost h-10 rounded-xl text-xs font-mono flex items-center justify-center gap-2 cursor-pointer border border-[var(--border)]">
              <FileText size={13} /> Voir le détail du ticket
            </button>
          </motion.div>
        </div>
      </div>
    </motion.div>
  );
}
