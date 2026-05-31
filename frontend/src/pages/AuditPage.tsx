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

const parseSafeDate = (dateStr: string) => {
  const normalized = dateStr.endsWith('Z') ? dateStr : `${dateStr}Z`;
  return new Date(normalized).getTime();
};

interface RgpdCheck {
  label: string;
  desc: string;
  ok: boolean | null;
  icon: React.ReactNode;
  recommendation?: string;
  na?: boolean;
}

const getRgpdChecks = (
  ticket: Ticket | null,
  notifications: NotificationLog[],
  decision: DecisionResult | null
): RgpdCheck[] => {
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

  const checks: RgpdCheck[] = [];

  // 1. Consentement
  checks.push({
    label: getConsentLabel(ticket.canal),
    desc: 'Art. 6 RGPD — Base légale du traitement',
    ok: hasConsent,
    icon: hasConsent ? <CheckCircle2 size={14} /> : <XCircle size={14} />,
    recommendation: getConsentRecommendation(hasConsent, isCall),
  });

  // 2. Audio & Transcription (only if isCall)
  if (isCall) {
    checks.push({
      label: 'Audio enregistré & stocké',
      desc: 'Art. 32 — Sécurité du traitement des données vocales',
      ok: hasAudio,
      icon: hasAudio ? <Lock size={14} /> : <AlertTriangle size={14} />,
      recommendation: hasAudio ? undefined : 'Aucun enregistrement audio associé à cet appel',
    });
    checks.push({
      label: 'Chiffrement audio (AES-256-GCM)',
      desc: 'Art. 32 — Chiffrement et confidentialité des données vocales stockées',
      ok: hasAudio,
      icon: hasAudio ? <Lock size={14} className="text-emerald-400" /> : <AlertTriangle size={14} className="text-rose-400" />,
      recommendation: hasAudio ? undefined : 'Configurer AUDIO_ENCRYPTION_KEY pour sécuriser les enregistrements',
    });
    checks.push({
      label: 'Transcription générée (Whisper)',
      desc: 'Art. 22 — Transparence du traitement automatisé',
      ok: hasTranscription,
      icon: hasTranscription ? <FileText size={14} /> : <AlertTriangle size={14} />,
      recommendation: hasTranscription ? undefined : 'Lancer la transcription pour documenter l\'échange',
    });
  }

  // 3. Photos (only if not CALL and not EMAIL)
  if (!isCall && !isEmail) {
    const hasPhotos = !!(ticket.photosPaths && ticket.photosPaths !== '[]');
    checks.push({
      label: 'Photos sinistre sécurisées',
      desc: 'Art. 32 — Protection des données visuelles',
      ok: hasPhotos ? true : null,
      icon: hasPhotos ? <Camera size={14} /> : <AlertTriangle size={14} />,
      na: !hasPhotos,
      recommendation: undefined,
    });
  }

  // 4. Traçabilité décisionnelle
  checks.push({
    label: 'Traçabilité décisionnelle IA',
    desc: 'Art. 22 — Décision automatisée explicable',
    ok: hasDecisionTrace,
    icon: hasDecisionTrace ? <Bot size={14} /> : <XCircle size={14} />,
    recommendation: hasDecisionTrace ? undefined : 'Lancer l\'analyse IA pour documenter la décision',
  });

  // 5. Notifications
  checks.push({
    label: 'Notification client envoyée',
    desc: 'Art. 13/14 — Droit à l\'information',
    ok: hasNotif,
    icon: hasNotif ? <Mail size={14} /> : <XCircle size={14} />,
    recommendation: hasNotif ? undefined : 'Informer le client du traitement de sa demande',
  });

  // 6. Minimisation des données
  checks.push({
    label: 'Minimisation des données',
    desc: 'Art. 5(1)(c) — Données adéquates et pertinentes',
    ok: dataMinimization,
    icon: dataMinimization ? <CheckCircle2 size={14} /> : <AlertTriangle size={14} />,
    recommendation: dataMinimization ? undefined : 'Réduire le volume de données collectées',
  });

  // 7. Droit à l'effacement (if isClosed)
  if (isClosed) {
    const closedDays = Math.floor((Date.now() - parseSafeDate(ticket.updatedAt)) / 86400000);
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
};

const statusLabels: Record<string, string> = {
  SENT: '✓ Envoyé',
  FAILED: '✕ Échec',
};

const getStatusLabel = (status: string) => statusLabels[status] || '◷ En attente';

/* ── Helpers to eliminate nested ternaries (SonarCloud S3358) ── */

const SOURCE_ABBREV: Record<string, string> = { ticket: 'T', decision: 'D', notification: 'N' };
const SOURCE_SERVICE: Record<string, string> = { ticket: 'ticket-service', decision: 'decision-service', notification: 'notification-service' };

const getConsentLabel = (canal: string): string => {
  if (canal === 'CALL') return 'Consentement enregistrement (IVR)';
  if (canal === 'EMAIL') return 'Consentement implicite (email initié)';
  return 'Consentement explicite (formulaire)';
};

const getConsentRecommendation = (hasConsent: boolean, isCall: boolean): string | undefined => {
  if (hasConsent) return undefined;
  return isCall ? 'Vérifier le message IVR de consentement' : 'Le client doit accepter les conditions de traitement';
};

const getPdfBadgeLabel = (isNa: boolean | undefined, isOk: boolean | null): string => {
  if (isNa) return 'N/A';
  if (isOk) return '✓ CONFORME';
  return '✕ REFUSÉ';
};

const getStepBorderClass = (active: boolean, hit: boolean): string => {
  if (active) return 'border-[var(--primary)] bg-[var(--primary-soft)]';
  if (hit) return 'border-emerald-500/20 bg-emerald-500/5';
  return 'border-[var(--border)] bg-[var(--elevated)]';
};

const getStepIconClass = (active: boolean, hit: boolean): string => {
  if (active) return 'bg-[var(--primary)] text-white';
  if (hit) return 'bg-emerald-500/20 text-emerald-400';
  return 'bg-[var(--muted)] text-[var(--text-3)]';
};

const getStepTextClass = (active: boolean, hit: boolean): string => {
  if (active) return 'text-[var(--primary)]';
  if (hit) return 'text-emerald-400';
  return 'text-[var(--text-3)]';
};

const getStepStatusText = (active: boolean, hit: boolean): string => {
  if (active) return '● ACTIF';
  if (hit) return '✓';
  return '○ SKIP';
};

const getRgpdScoreClass = (score: number): string => {
  if (score >= 80) return 'text-emerald-400';
  if (score >= 50) return 'text-amber-400';
  return 'text-rose-400';
};

const getRgpdScoreColor = (score: number): string => {
  if (score >= 80) return '#10B981';
  if (score >= 50) return '#F59E0B';
  return '#EF4444';
};

const getRgpdItemBorderClass = (na: boolean | undefined, ok: boolean | null): string => {
  if (na) return 'bg-zinc-500/5 border-zinc-500/10';
  if (ok) return 'bg-emerald-500/5 border-emerald-500/15';
  return 'bg-rose-500/5 border-rose-500/15';
};

const getRgpdItemIconColor = (na: boolean | undefined, ok: boolean | null): string => {
  if (na) return 'var(--text-3)';
  if (ok) return '#10B981';
  return '#EF4444';
};

const getRgpdItemStatusClass = (na: boolean | undefined, ok: boolean | null): string => {
  if (na) return 'text-[var(--text-3)]';
  if (ok) return 'text-emerald-400';
  return 'text-rose-400';
};

const getRgpdItemStatusText = (na: boolean | undefined, ok: boolean | null): string => {
  if (na) return 'N/A';
  if (ok) return '✓ CONFORME';
  return '✕ NON';
};

const drawPDFGeneralInfo = (doc: jsPDF, ticket: Ticket, _W: number, M: number, CW: number, y: number, drawSectionTitle: (title: string, currentY: number) => number) => {
  y = drawSectionTitle("1. INFORMATIONS GÉNÉRALES DU DOSSIER", y);
  
  // Background rounded card for details
  doc.setFillColor(247, 248, 250);
  doc.setDrawColor(230, 233, 238);
  doc.setLineWidth(0.3);
  doc.roundedRect(M, y, CW, 38, 2, 2, 'FD');

  doc.setTextColor(17, 19, 24);
  doc.setFontSize(9);
  
  const details = [
    ['Client référent', ticket.clientNom, 'Canal d\'entrée', ticket.canal],
    ['Numéro de dossier', ticket.numero, 'Statut du ticket', ticket.statut],
    ['Adresse Email', ticket.clientEmail || 'Non spécifiée', 'N° Téléphone', ticket.clientTel || 'Non spécifié'],
    ['Date de création', new Date(ticket.createdAt).toLocaleString('fr-FR'), 'Conseiller en charge', ticket.operateurName || 'Non assigné']
  ];

  let rowY = y + 7;
  details.forEach(([k1, v1, k2, v2]) => {
    doc.setFont('helvetica', 'bold');
    doc.text(`${k1} :`, M + 5, rowY);
    doc.setFont('helvetica', 'normal');
    doc.text(String(v1), M + 38, rowY);

    doc.setFont('helvetica', 'bold');
    doc.text(`${k2} :`, M + 105, rowY);
    doc.setFont('helvetica', 'normal');
    doc.text(String(v2), M + 142, rowY);
    
    rowY += 7;
  });
  return y + 46;
};

const drawPDFPerformance = (doc: jsPDF, perf: Readonly<{ totalDuration: number; assignTime: number | null; resolveTime: number | null; slaOk: boolean; actionCount?: number }>, M: number, CW: number, y: number, drawSectionTitle: (title: string, currentY: number) => number) => {
  y = drawSectionTitle("2. ANALYSE ET INDEX DE PERFORMANCE (SLA)", y);

  // Three columns metrics layout
  const colW = (CW - 6) / 3;
  
  // Box 1: Total duration
  doc.setFillColor(247, 248, 250);
  doc.roundedRect(M, y, colW, 18, 2, 2, 'F');
  doc.setFontSize(7.5);
  doc.setFont('helvetica', 'bold');
  doc.setTextColor(74, 81, 104);
  doc.text("DURÉE TOTALE", M + 4, y + 5);
  doc.setFontSize(10.5);
  doc.setFont('helvetica', 'bold');
  doc.setTextColor(17, 19, 24);
  doc.text(fmtDuration(perf.totalDuration), M + 4, y + 12);

  // Box 2: Assignment duration
  doc.setFillColor(247, 248, 250);
  doc.roundedRect(M + colW + 3, y, colW, 18, 2, 2, 'F');
  doc.setFontSize(7.5);
  doc.setFont('helvetica', 'bold');
  doc.setTextColor(74, 81, 104);
  doc.text("TEMPS D'ASSIGNATION", M + colW + 7, y + 5);
  doc.setFontSize(10.5);
  doc.setFont('helvetica', 'bold');
  doc.setTextColor(17, 19, 24);
  doc.text(perf.assignTime ? fmtDuration(perf.assignTime) : 'Immédiat', M + colW + 7, y + 12);

  // Box 3: Resolution duration
  doc.setFillColor(247, 248, 250);
  doc.roundedRect(M + 2 * colW + 6, y, colW, 18, 2, 2, 'F');
  doc.setFontSize(7.5);
  doc.setFont('helvetica', 'bold');
  doc.setTextColor(74, 81, 104);
  doc.text("TEMPS DE RÉSOLUTION", M + 2 * colW + 10, y + 5);
  doc.setFontSize(10.5);
  doc.setFont('helvetica', 'bold');
  doc.setTextColor(17, 19, 24);
  doc.text(perf.resolveTime ? fmtDuration(perf.resolveTime) : 'N/A', M + 2 * colW + 10, y + 12);

  y += 24;

  // SLA Card
  doc.setLineWidth(0.3);
  if (perf.slaOk) {
    doc.setFillColor(240, 253, 250); // Soft emerald green bg
    doc.setDrawColor(153, 246, 228);  // Soft emerald border
    doc.roundedRect(M, y, CW, 10, 1.5, 1.5, 'FD');
    doc.setTextColor(15, 118, 110);   // Dark green text
    doc.setFontSize(8.5);
    doc.setFont('helvetica', 'bold');
    doc.text("✓ CONFORMITÉ SLA : Dossier traité dans les temps impartis (< 4 heures)", M + 5, y + 6.5);
  } else {
    doc.setFillColor(254, 242, 242); // Soft red bg
    doc.setDrawColor(254, 202, 202);  // Soft red border
    doc.roundedRect(M, y, CW, 10, 1.5, 1.5, 'FD');
    doc.setTextColor(185, 28, 28);    // Dark red text
    doc.setFontSize(8.5);
    doc.setFont('helvetica', 'bold');
    doc.text("✕ DÉPASSEMENT SLA : Temps de traitement hors objectif (> 4 heures)", M + 5, y + 6.5);
  }
  return y + 18;
};

const drawPDFDecision = (doc: jsPDF, decision: DecisionResult, M: number, CW: number, y: number, drawSectionTitle: (title: string, currentY: number) => number) => {
  y = drawSectionTitle("3. PIPELINE DÉCISIONNEL & INTELLIGENCE ARTIFICIELLE", y);

  // General Card with Left Indigo Accent
  doc.setFillColor(247, 248, 250);
  doc.roundedRect(M, y, CW, 45, 2, 2, 'F');
  doc.setFillColor(99, 91, 255);
  doc.rect(M, y, 2.5, 45, 'F'); // Left accent line

  doc.setTextColor(17, 19, 24);
  doc.setFontSize(9);
  
  doc.setFont('helvetica', 'bold');
  doc.text("Décision automatique calculée :", M + 7, y + 6);
  doc.setFont('helvetica', 'normal');
  
  // Decision status color-coding
  const isFavorable = decision.decision.toLowerCase().includes('favor') || decision.decision.toLowerCase().includes('accept');
  const isDefavorable = decision.decision.toLowerCase().includes('défavor') || decision.decision.toLowerCase().includes('refus');
  
  if (isFavorable) {
    doc.setTextColor(0, 196, 140);
    doc.setFont('helvetica', 'bold');
  } else if (isDefavorable) {
    doc.setTextColor(255, 71, 87);
    doc.setFont('helvetica', 'bold');
  } else {
    doc.setTextColor(255, 176, 32);
    doc.setFont('helvetica', 'bold');
  }
  doc.text(decision.decision, M + 60, y + 6);

  // Restore normal text color
  doc.setTextColor(17, 19, 24);
  doc.setFont('helvetica', 'bold');
  doc.text("Score de confiance IA :", M + 7, y + 13);
  doc.setFont('helvetica', 'normal');
  doc.text(`${decision.score} %`, M + 60, y + 13);

  // Confidence Score Progress Bar Mockup
  doc.setFillColor(220, 224, 230);
  doc.roundedRect(M + 80, y + 10.5, 50, 2.5, 1, 1, 'F');
  
  if (isFavorable) doc.setFillColor(0, 196, 140);
  else if (isDefavorable) doc.setFillColor(255, 71, 87);
  else doc.setFillColor(255, 176, 32);
  
  doc.roundedRect(M + 80, y + 10.5, 50 * (decision.score / 100), 2.5, 1, 1, 'F');

  doc.setTextColor(17, 19, 24);
  doc.setFont('helvetica', 'bold');
  doc.text("Source de décision :", M + 7, y + 20);
  doc.setFont('helvetica', 'normal');
  doc.text(decision.source, M + 60, y + 20);

  if (decision.rulesApplied.length > 0) {
    doc.setFont('helvetica', 'bold');
    doc.text("Règles appliquées :", M + 7, y + 27);
    doc.setFont('helvetica', 'normal');
    doc.text(decision.rulesApplied.join(', '), M + 60, y + 27);
  }

  // Draw a clean box for the explanation Motif
  doc.setFillColor(238, 241, 246);
  doc.roundedRect(M + 7, y + 31, CW - 14, 11, 1.5, 1.5, 'F');
  
  doc.setFont('helvetica', 'oblique');
  doc.setFontSize(8);
  doc.setTextColor(74, 81, 104);
  const motifLines = doc.splitTextToSize(`Explication (Motif) : ${decision.motif}`, CW - 20);
  doc.text(motifLines, M + 10, y + 35);

  return y + 52;
};

const drawPDFTimeline = (doc: jsPDF, timeline: ReadonlyArray<{ id: string; timestamp: string; title: string; detail: string }>, M: number, CW: number, y: number, drawSectionTitle: (title: string, currentY: number) => number) => {
  y = drawSectionTitle(`4. HISTORIQUE & TRACABILITÉ D'AUDIT (${timeline.length} événements)`, y);
  
  const timelineStartY = y;
  let timelineEndY = y;
  
  doc.setFontSize(8);
  timeline.forEach((ev) => {
    if (y > 260) {
      // Draw connecting line for this page
      doc.setDrawColor(226, 229, 235);
      doc.setLineWidth(0.6);
      doc.line(M + 4, timelineStartY, M + 4, y - 10);

      doc.addPage(); 
      y = 20;
    }

    const ts = new Date(ev.timestamp.endsWith('Z') ? ev.timestamp : ev.timestamp + 'Z').toLocaleString('fr-FR');
    
    // Node circle
    doc.setFillColor(99, 91, 255);
    doc.circle(M + 4, y + 1, 1.5, 'F');

    doc.setTextColor(17, 19, 24);
    doc.setFont('helvetica', 'bold');
    doc.text(`[${ts}] ${ev.title}`, M + 9, y + 2);
    
    doc.setFont('helvetica', 'normal');
    doc.setTextColor(74, 81, 104);
    const detailLines = doc.splitTextToSize(ev.detail, CW - 15);
    doc.text(detailLines, M + 9, y + 5.5);
    
    y += 5.5 + detailLines.length * 3.5 + 4;
    timelineEndY = y - 6;
  });

  // Draw the connecting vertical line passing behind all node dots
  if (timelineEndY > timelineStartY) {
    doc.setDrawColor(226, 229, 235);
    doc.setLineWidth(0.6);
    doc.line(M + 4, timelineStartY, M + 4, timelineEndY);
  }
  return y + 8;
};

const drawPDFRgpd = (doc: jsPDF, rgpd: ReadonlyArray<{ na?: boolean; ok: boolean | null; label: string; desc?: string }>, rgpdScore: number, M: number, CW: number, y: number, drawSectionTitle: (title: string, currentY: number) => number) => {
  y = drawSectionTitle(`5. CONFORMITÉ RÉGLEMENTAIRE & RGPD`, y);

  // Score Header Box
  const getScoreColor = (score: number) => {
    if (score >= 90) return { bg: [240, 253, 250], border: [153, 246, 228], text: [15, 118, 110] }; // emerald
    if (score >= 70) return { bg: [255, 251, 235], border: [253, 230, 138], text: [180, 83, 9] };   // amber
    return { bg: [254, 242, 242], border: [254, 202, 202], text: [185, 28, 28] };                // rose
  };
  
  const scColor = getScoreColor(rgpdScore);
  doc.setFillColor(scColor.bg[0], scColor.bg[1], scColor.bg[2]);
  doc.setDrawColor(scColor.border[0], scColor.border[1], scColor.border[2]);
  doc.setLineWidth(0.3);
  doc.roundedRect(M, y, CW, 14, 2, 2, 'FD');

  doc.setFont('helvetica', 'bold');
  doc.setFontSize(10);
  doc.setTextColor(scColor.text[0], scColor.text[1], scColor.text[2]);
  doc.text(`INDICE GLOBAL DE CONFORMITÉ RGPD : ${rgpdScore} %`, M + 6, y + 8.5);

  y += 22;

  // List each compliance item
  rgpd.forEach((r) => {
    if (y > 262) { doc.addPage(); y = 20; }

    const isNa = r.na;
    const isOk = r.ok;
    
    // Draw badge box
    if (isNa) {
      doc.setFillColor(243, 244, 246); // gray
      doc.setDrawColor(229, 231, 235);
      doc.setTextColor(107, 114, 128);
    } else if (isOk) {
      doc.setFillColor(240, 253, 250); // emerald green
      doc.setDrawColor(153, 246, 228);
      doc.setTextColor(15, 118, 110);
    } else {
      doc.setFillColor(254, 242, 242); // rose red
      doc.setDrawColor(254, 202, 202);
      doc.setTextColor(185, 28, 28);
    }

    doc.setLineWidth(0.2);
    doc.roundedRect(M, y, 32, 6, 1, 1, 'FD');
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(7.5);
    
    const badgeLabel = getPdfBadgeLabel(isNa, isOk);
    doc.text(badgeLabel, M + 4, y + 4.2);

    // Description title
    doc.setTextColor(17, 19, 24);
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(9);
    doc.text(r.label, M + 36, y + 4.5);

    // Description detail
    doc.setTextColor(74, 81, 104);
    doc.setFont('helvetica', 'normal');
    doc.setFontSize(8);
    const descLines = doc.splitTextToSize(r.desc || '', CW - 38);
    doc.text(descLines, M + 36, y + 9);
    
    y += 9 + descLines.length * 3.5 + 4;
  });
  return y;
};

/* ── Subcomponents extracted for reducing cognitive complexity (SonarCloud S3776) ── */

interface AuditTimelineProps {
  timeline: TimelineEvent[];
}

function AuditTimeline({ timeline }: Readonly<AuditTimelineProps>) {
  return (
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
                <span className="text-white text-[6px]">{SOURCE_ABBREV[ev.source] || 'N'}</span>
              </div>
              {i < timeline.length - 1 && <div className="flex-1 w-px mt-1" style={{ background: `${ev.color}30` }} />}
            </div>

            {/* Content */}
            <div className="flex-1 min-w-0 pb-1">
              <div className="flex items-center gap-2 flex-wrap">
                <span className="flex items-center gap-1 text-xs font-bold" style={{ color: ev.color }}>{ev.icon} {ev.title}</span>
                <span className="font-mono text-[8px] px-1.5 py-0.5 rounded" style={{ background: `${ev.color}12`, color: ev.color, border: `1px solid ${ev.color}25` }}>
                  {SOURCE_SERVICE[ev.source] || ev.source}
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
  );
}

interface AuditPipelineIaProps {
  decision: DecisionResult | null;
  pipelineSteps: Array<{
    label: string;
    sub: string;
    active: boolean;
    hit: boolean;
    icon: React.ReactNode;
  }> | null;
}

function AuditPipelineIa({ decision, pipelineSteps }: Readonly<AuditPipelineIaProps>) {
  return (
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
                  <div className={`p-3 rounded-xl border transition-all ${getStepBorderClass(step.active, step.hit)}`}>
                    <div className="flex items-center gap-2">
                      <div className={`w-7 h-7 rounded-lg flex items-center justify-center ${getStepIconClass(step.active, step.hit)}`}>
                        {step.icon}
                      </div>
                      <div className="flex-1">
                        <div className="text-xs font-bold">{step.label}</div>
                        <div className="font-mono text-[9px] text-[var(--text-3)]">{step.sub}</div>
                      </div>
                      <span className={`text-[10px] font-mono font-bold ${getStepTextClass(step.active, step.hit)}`}>
                        {getStepStatusText(step.active, step.hit)}
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
  );
}

interface AuditRgpdComplianceProps {
  rgpd: Array<{
    label: string;
    desc: string;
    ok: boolean | null;
    icon: React.ReactNode;
    recommendation?: string;
    na?: boolean;
  }>;
  rgpdScore: number;
}

function AuditRgpdCompliance({ rgpd, rgpdScore }: Readonly<AuditRgpdComplianceProps>) {
  return (
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
            <span className={`font-mono text-sm font-extrabold ${getRgpdScoreClass(rgpdScore)}`}>{rgpdScore}%</span>
          </div>
          <div className="h-2 rounded-full bg-[var(--muted)] overflow-hidden">
            <motion.div initial={{ width: 0 }} animate={{ width: `${rgpdScore}%` }} transition={{ duration: 1, ease: 'easeOut' }}
              className="h-full rounded-full" style={{ background: getRgpdScoreColor(rgpdScore) }} />
          </div>
        </div>
        <div className="space-y-1.5">
          {rgpd.map(r => (
            <div key={r.label} className={`p-2.5 rounded-lg border ${getRgpdItemBorderClass(r.na, r.ok)}`}>
              <div className="flex items-center gap-2">
                <span style={{ color: getRgpdItemIconColor(r.na, r.ok) }}>{r.icon}</span>
                <span className="text-[11px] font-medium flex-1">{r.label}</span>
                <span className={`font-mono text-[8px] font-bold ${getRgpdItemStatusClass(r.na, r.ok)}`}>
                  {getRgpdItemStatusText(r.na, r.ok)}
                </span>
              </div>
              <div className="font-mono text-[8px] text-[var(--text-3)] mt-0.5 ml-[22px]">{r.desc}</div>
              {r.recommendation && (
                <div className="mt-1 ml-[22px] text-[9px] text-amber-400 flex items-center gap-1">
                  <AlertTriangle size={9} /> {r.recommendation}
                </div>
              )}
            </div>
          ))}
        </div>
      </div>
    </motion.div>
  );
}

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
        const meta = (log.metadata || {}) as Record<string, string | undefined>;
        return meta.ticketId === ticketId
          || meta.ticketNumero === t.numero
          || meta.numero === t.numero
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
      let detail = '';
      if (a.action === 'STATUT_CHANGE' && a.oldValue && a.newValue) {
        detail = `${a.oldValue} → ${a.newValue}`;
        if (a.notes) {
          detail += ` · ${a.notes}`;
        }
      } else if (a.action === 'ASSIGNE' && a.newValue) {
        detail = `Opérateur: ${a.newValue}`;
        if (a.userName) {
          detail += ` · Par: ${a.userName}`;
        }
      } else {
        detail = a.userName || 'Système';
        if (a.notes) {
          detail += ` · ${a.notes}`;
        }
      }

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
      const rulesSuffix = decision.rulesApplied.length > 0 ? ` · Règles: ${decision.rulesApplied.join(', ')}` : '';
      events.push({
        id: `decision-created`,
        timestamp: decision.createdAt || ticket.updatedAt,
        source: 'decision',
        type: 'DECISION_IA',
        title: `Décision IA : ${decision.decision}`,
        detail: `Score: ${decision.score}% · Source: ${decision.source}${rulesSuffix} · ${decision.motif}`,
        color: '#10B981',
        icon: <Bot size={12} />,
      });
      if (decision.finalDecision) {
        events.push({
          id: `decision-finalized`,
          timestamp: decision.overriddenAt || ticket.updatedAt,
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
      const statusLabel = getStatusLabel(n.statut);
      const subjectSuffix = n.sujet ? ` · ${n.sujet}` : '';
      events.push({
        id: `notif-${n.id || i}`,
        timestamp: n.createdAt,
        source: 'notification',
        type: n.type,
        title: `Email: ${n.type.replaceAll('_', ' ')}`,
        detail: `→ ${n.destinataire} · ${statusLabel}${subjectSuffix}`,
        color: '#3B82F6',
        icon: <Mail size={12} />,
      });
    });

    // Sort chronologically
    return events.sort((a, b) => {
      const ta = parseSafeDate(a.timestamp);
      const tb = parseSafeDate(b.timestamp);
      return ta - tb;
    });
  }, [ticket, decision, notifications]);

  /* ── RGPD compliance checks (canal-aware, based on real project fields) ── */
  const rgpd = useMemo(() => {
    return getRgpdChecks(ticket, notifications, decision);
  }, [ticket, notifications, decision]);

  const rgpdScore = useMemo(() => {
    const applicable = rgpd.filter(r => !r.na);
    if (applicable.length === 0) return 0;
    return Math.round((applicable.filter(r => r.ok).length / applicable.length) * 100);
  }, [rgpd]);

  /* ── Performance metrics ── */
  const perf = useMemo(() => {
    if (!ticket) return null;
    const created = parseSafeDate(ticket.createdAt);
    const updated = parseSafeDate(ticket.updatedAt);
    const assignAction = (ticket.actions || []).find(a => a.action === 'ASSIGNE');
    const resolveAction = (ticket.actions || []).find(a => a.action === 'STATUT_CHANGE' && a.newValue === 'RESOLU');
    const assignTime = assignAction ? parseSafeDate(assignAction.createdAt) - created : null;
    const resolveTime = resolveAction ? parseSafeDate(resolveAction.createdAt) - created : null;
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
      let y: number;

      // Page numbers & Header/Footer builder
      const drawHeaderFooter = (pdfDoc: typeof doc, pageNum: number, totalPages: number) => {
        // Top accent line for subsequent pages
        if (pageNum > 1) {
          pdfDoc.setFillColor(99, 91, 255);
          pdfDoc.rect(0, 0, W, 3, 'F');
          
          pdfDoc.setFont('helvetica', 'normal');
          pdfDoc.setFontSize(7.5);
          pdfDoc.setTextColor(139, 146, 165);
          pdfDoc.text(`Rapport d'Audit Complet — Ticket: ${ticket.numero}`, M, 10);
          
          pdfDoc.setDrawColor(226, 229, 235);
          pdfDoc.setLineWidth(0.3);
          pdfDoc.line(M, 12, W - M, 12);
        }

        // Draw Footer
        pdfDoc.setDrawColor(226, 229, 235);
        pdfDoc.setLineWidth(0.3);
        pdfDoc.line(M, 283, W - M, 283);

        pdfDoc.setFont('helvetica', 'normal');
        pdfDoc.setFontSize(7.5);
        pdfDoc.setTextColor(139, 146, 165);
        pdfDoc.text("Document confidentiel — Généré par le moteur d'audit AutoAssist", M, 288);
        pdfDoc.text(`Page ${pageNum} / ${totalPages}`, W - M - 15, 288);
      };

      // Header Banner (Page 1)
      doc.setFillColor(99, 91, 255); 
      doc.rect(0, 0, W, 4, 'F'); // Top accent
      
      doc.setFillColor(14, 16, 23); // Dark Space Background
      doc.rect(0, 4, W, 36, 'F');

      // Teal block accent next to title
      doc.setFillColor(0, 212, 170);
      doc.rect(M, 13, 1.5, 18, 'F');

      doc.setTextColor(255); 
      doc.setFontSize(16); 
      doc.setFont('helvetica', 'bold');
      doc.text("RAPPORT D'AUDIT & SÉCURITÉ", M + 5, 20);
      
      doc.setFontSize(8.5); 
      doc.setFont('helvetica', 'normal');
      doc.setTextColor(0, 212, 170);
      doc.text("AUTOASSIST COMPLIANCE & DECISION TRACKING", M + 5, 26);
      doc.setTextColor(139, 146, 165);
      doc.text("Investigation complète · RGPD & Aide à la décision IA", M + 5, 31);

      // Top right metadata
      doc.setTextColor(255);
      doc.setFont('helvetica', 'bold');
      doc.setFontSize(11);
      doc.text(ticket.numero, W - M - 42, 19);
      doc.setFont('helvetica', 'normal');
      doc.setFontSize(7.5);
      doc.setTextColor(139, 146, 165);
      doc.text(`Généré le : ${new Date().toLocaleString('fr-FR')}`, W - M - 42, 24);
      doc.text(`Canal : ${ticket.canal} · Statut : ${ticket.statut}`, W - M - 42, 29);

      y = 52;

      // Section header drawer
      const drawSectionTitle = (title: string, currentY: number) => {
        // Little violet indicator square
        doc.setFillColor(99, 91, 255);
        doc.rect(M, currentY, 3, 3, 'F');
        doc.setTextColor(17, 19, 24);
        doc.setFontSize(11);
        doc.setFont('helvetica', 'bold');
        doc.text(title, M + 5, currentY + 2.8);
        
        // Horizontal divider
        doc.setDrawColor(226, 229, 235);
        doc.setLineWidth(0.3);
        doc.line(M, currentY + 5, W - M, currentY + 5);
        return currentY + 11;
      };

      // ── Section 1: Ticket Information ──
      y = drawPDFGeneralInfo(doc, ticket, W, M, CW, y, drawSectionTitle);

      // ── Section 2: Performance metrics ──
      if (perf) {
        y = drawPDFPerformance(doc, perf, M, CW, y, drawSectionTitle);
      }

      // ── Section 3: IA Decision ──
      if (decision) {
        y = drawPDFDecision(doc, decision, M, CW, y, drawSectionTitle);
      }

      // ── Section 4: Timeline ──
      if (y > 200) { doc.addPage(); y = 20; }
      y = drawPDFTimeline(doc, timeline, M, CW, y, drawSectionTitle);

      // ── Section 5: GDPR / RGPD Compliance ──
      if (y > 210) { doc.addPage(); y = 20; }
      drawPDFRgpd(doc, rgpd, rgpdScore, M, CW, y, drawSectionTitle);

      // Apply dynamic Page Numbers & Header/Footers on all pages
      const totalPages = doc.getNumberOfPages();
      for (let i = 1; i <= totalPages; i++) {
        doc.setPage(i);
        drawHeaderFooter(doc, i, totalPages);
      }

      doc.save(`audit_${ticket.numero}_${new Date().toISOString().slice(0, 10)}.pdf`);
    } catch (err: unknown) {
      /* PDF export failed — toast is not shown as it would confuse users mid-generation */
      console.debug('[AuditPage] PDF export error (non-critical):', err);
    }
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
        <AuditTimeline timeline={timeline} />

        {/* RIGHT: Panels */}
        <div className="flex flex-col gap-4">
          {/* Pipeline IA */}
          <AuditPipelineIa decision={decision} pipelineSteps={pipelineSteps} />

          {/* RGPD Compliance — Enhanced */}
          <AuditRgpdCompliance rgpd={rgpd} rgpdScore={rgpdScore} />

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
