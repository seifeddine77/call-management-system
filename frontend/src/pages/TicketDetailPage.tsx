import { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { ticketService } from '../services/ticketService';
import { decisionService } from '../services/decisionService';
import { ruleService } from '../services/ruleService';
import { agenceService } from '../services/agenceService';
import { userService } from '../services/userService';
import { useAuth } from '../contexts/AuthContext';
import { CardSkeleton, ErrorState } from '../components/ui/States';
import type { Ticket } from '../types/ticket';
import type { DecisionResult } from '../types/decision';
import type { ReferenceData, FieldDef } from '../types/rule';

/* ───── Live Transcription Component (Streaming STT) ───── */
function LiveTranscription({ ticketId, onTranscriptionUpdate }: Readonly<{ ticketId: string; onTranscriptionUpdate?: (text: string) => void }>) {
  const [isRecording, setIsRecording] = useState(false);
  const [liveText, setLiveText] = useState('');
  const [elapsed, setElapsed] = useState(0);
  const [chunks, setChunks] = useState(0);
  const wsRef = useRef<WebSocket | null>(null);
  const recorderRef = useRef<MediaRecorder | null>(null);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const AI_WS_URL = `ws://localhost:8000/ws/transcribe/${ticketId}`;

  const startRecording = useCallback(async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });

      // Connect WebSocket to AI service
      const ws = new WebSocket(AI_WS_URL);
      wsRef.current = ws;

      ws.onopen = () => {
        // Start MediaRecorder once WebSocket is connected
        const recorder = new MediaRecorder(stream, { mimeType: 'audio/webm;codecs=opus' });
        recorderRef.current = recorder;

        recorder.ondataavailable = (event) => {
          if (event.data.size > 0 && ws.readyState === WebSocket.OPEN) {
            ws.send(event.data);
          }
        };

        // Send chunks every 3 seconds
        recorder.start(3000);
        setIsRecording(true);
        setElapsed(0);
        setLiveText('');
        setChunks(0);

        // Start timer
        timerRef.current = setInterval(() => {
          setElapsed(prev => prev + 1);
        }, 1000);

        toast.success('🎙 Transcription live démarrée');
      };

      ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          if (data.type === 'partial') {
            setLiveText(prev => (prev ? prev + ' ' : '') + data.text);
            setChunks(data.chunks || 0);
          } else if (data.type === 'final') {
            setLiveText(prev => (prev ? prev + ' ' : '') + (data.text || ''));
            if (onTranscriptionUpdate && data.text) {
              onTranscriptionUpdate(data.text);
            }
          } else if (data.type === 'error') {
            toast.error(`Erreur STT: ${data.detail}`);
          }
        } catch { /* ignore parse errors */ }
      };

      ws.onerror = () => {
        toast.error('Erreur de connexion WebSocket');
        stopRecording();
      };

      ws.onclose = () => {
        if (isRecording) stopRecording();
      };

    } catch (err) {
      toast.error('Impossible d\'accéder au microphone');
    }
  }, [ticketId]);

  const stopRecording = useCallback(() => {
    // Stop timer
    if (timerRef.current) {
      clearInterval(timerRef.current);
      timerRef.current = null;
    }

    // Stop recorder
    if (recorderRef.current && recorderRef.current.state !== 'inactive') {
      recorderRef.current.stop();
      recorderRef.current.stream.getTracks().forEach(t => t.stop());
      recorderRef.current = null;
    }

    // Send STOP and close WebSocket
    if (wsRef.current) {
      if (wsRef.current.readyState === WebSocket.OPEN) {
        wsRef.current.send('STOP');
      }
      // Don't close immediately, let the server send final response
      setTimeout(() => {
        if (wsRef.current) {
          wsRef.current.close();
          wsRef.current = null;
        }
      }, 2000);
    }

    setIsRecording(false);
    toast.success('🛑 Transcription arrêtée et sauvegardée');
  }, []);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
      if (recorderRef.current && recorderRef.current.state !== 'inactive') {
        recorderRef.current.stop();
        recorderRef.current.stream.getTracks().forEach(t => t.stop());
      }
      if (wsRef.current) wsRef.current.close();
    };
  }, []);

  const formatTime = (s: number) => `${Math.floor(s / 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}`;

  return (
    <div className="card-aurora rounded-2xl p-5">
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-sm font-bold">🎙 Transcription temps réel</h3>
        <div className="flex items-center gap-2">
          {isRecording && (
            <span className="font-mono text-[9px] text-txt-3">
              {formatTime(elapsed)} · {chunks} chunks
            </span>
          )}
          <button
            onClick={isRecording ? stopRecording : startRecording}
            className={`h-8 px-3.5 rounded-lg text-[10px] font-mono font-medium flex items-center gap-1.5 cursor-pointer transition-all ${
              isRecording
                ? 'bg-rose-500/20 text-rose-400 border border-rose-500/30 hover:bg-rose-500/30'
                : 'btn-cta text-white'
            }`}
          >
            {isRecording ? (
              <>
                <div className="w-2 h-2 rounded-full bg-rose-400 animate-pulse" />
                Arrêter
              </>
            ) : (
              <>🎙 Démarrer le live</>
            )}
          </button>
        </div>
      </div>

      {isRecording && (
        <div className="mb-3 flex items-center gap-2 p-2 bg-rose-500/5 border border-rose-500/10 rounded-lg">
          <div className="w-2 h-2 rounded-full bg-rose-400 animate-pulse flex-shrink-0" />
          <span className="font-mono text-[10px] text-rose-400">En cours d'écoute — parlez dans votre micro</span>
        </div>
      )}

      {liveText ? (
        <div className="p-3.5 bg-[var(--elevated)] border border-border rounded-lg max-h-[200px] overflow-y-auto">
          <div className="text-xs text-txt-2 leading-relaxed whitespace-pre-wrap">
            {liveText}
            {isRecording && <span className="inline-block w-1.5 h-4 bg-primary ml-0.5 animate-pulse rounded-sm" />}
          </div>
        </div>
      ) : (
        <div className="text-center py-6">
          <div className="text-2xl mb-2 opacity-30">{isRecording ? '🔊' : '🎙'}</div>
          <div className="text-[11px] text-txt-3">
            {isRecording ? 'En attente de parole...' : 'Cliquez sur "Démarrer le live" pour commencer la transcription en temps réel'}
          </div>
        </div>
      )}
    </div>
  );
}


export default function TicketDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();

  const [ticket, setTicket] = useState<Ticket | null>(null);
  const [decision, setDecision] = useState<DecisionResult | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [analyzing, setAnalyzing] = useState(false);
  const [accepting, setAccepting] = useState(false);
  const [rejecting, setRejecting] = useState(false);
  const [downloading, setDownloading] = useState(false);

  // ── Motif opérateur (CDC: forçage avec motif) ──
  const [motif, setMotif] = useState('');

  // ── Formulaire de saisie demande dynamique (CDC étape 6) ──
  const [formData, setFormData] = useState<Record<string, string>>({});
  const [fieldRefs, setFieldRefs] = useState<ReferenceData | null>(null);
  const pieceReference = formData['pieceReference'] || '';

  // ── Photos sinistre (CDC Module 5) ──
  const [photoFiles, setPhotoFiles] = useState<File[]>([]);
  const [photoPreviews, setPhotoPreviews] = useState<string[]>([]);
  const [uploadingPhotos, setUploadingPhotos] = useState(false);
  const [existingPhotos, setExistingPhotos] = useState<string[]>([]);
  const photoInputRef = useRef<HTMLInputElement>(null);

  // ── Résolution UUID → Nom (agence + opérateur) ──
  const [agenceName, setAgenceName] = useState<string | null>(null);
  const [operateurName, setOperateurName] = useState<string | null>(null);

  // Parse existing photos from ticket
  useEffect(() => {
    if (ticket?.photosPaths) {
      try { setExistingPhotos(JSON.parse(ticket.photosPaths)); } catch { setExistingPhotos([]); }
    }
  }, [ticket?.photosPaths]);

  const handlePhotoSelect = (files: FileList | null) => {
    if (!files) return;
    const validFiles = Array.from(files).filter(f => f.type.startsWith('image/') && f.size <= 10 * 1024 * 1024);
    if (validFiles.length < files.length) toast.error('Certains fichiers ignorés (non-image ou >10MB)');
    setPhotoFiles(prev => [...prev, ...validFiles]);
    // Generate previews
    validFiles.forEach(file => {
      const reader = new FileReader();
      reader.onload = (e) => setPhotoPreviews(prev => [...prev, e.target?.result as string]);
      reader.readAsDataURL(file);
    });
  };

  const removePhoto = (index: number) => {
    setPhotoFiles(prev => prev.filter((_, i) => i !== index));
    setPhotoPreviews(prev => prev.filter((_, i) => i !== index));
  };

  const handleUploadPhotos = async () => {
    if (!id || photoFiles.length === 0) return;
    setUploadingPhotos(true);
    try {
      const updated = await ticketService.uploadPhotos(id, photoFiles);
      setTicket(updated);
      setPhotoFiles([]);
      setPhotoPreviews([]);
      toast.success(`${photoFiles.length} photo(s) uploadée(s)`);
    } catch { toast.error('Erreur upload photos'); }
    finally { setUploadingPhotos(false); }
  };

  const fetchTicket = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError(null);
    try {
      const t = await ticketService.getTicketById(id);
      setTicket(t);
      try {
        const d = await decisionService.getByTicket(id);
        setDecision(d);
      } catch { /* no decision yet */ }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Ticket introuvable');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => { fetchTicket(); }, [fetchTicket]);

  // Resolve agence & opérateur names from UUIDs
  useEffect(() => {
    if (ticket?.agenceId) {
      agenceService.getAgenceById(ticket.agenceId as string)
        .then(a => setAgenceName(a.nom))
        .catch(() => setAgenceName(null));
    }
    if (ticket?.operateurId) {
      userService.getUserById(ticket.operateurId as string)
        .then(u => setOperateurName(`${u.prenom} ${u.nom}`))
        .catch(() => setOperateurName(null));
    } else {
      setOperateurName(null);
    }
  }, [ticket?.agenceId, ticket?.operateurId]);

  // Fetch dynamic field references
  useEffect(() => {
    ruleService.getReferences().then(setFieldRefs).catch(() => {});
  }, []);

  // L'enregistrement audio se fait directement via MediaRecorder API dans le JSX

  const handleAnalyze = async (forceReanalyze = false) => {
    if (!id) return;

    // ── Validation frontend (les 5 champs obligatoires du DecisionRequest) ──
    const requiredFields = ['typePiece', 'ageVehiculeAns', 'kilometrage', 'categorieClient', 'typeSinistre'];
    const missingFields: string[] = [];
    for (const key of requiredFields) {
      const val = formData[key];
      if (val === undefined || val === null || val === '') {
        const fd = (fieldRefs?.fields || []).find((f: FieldDef) => f.field === key);
        missingFields.push(fd?.label || key);
      }
    }
    if (missingFields.length > 0) {
      toast.error(`Champs obligatoires manquants : ${missingFields.join(', ')}`);
      return;
    }

    setAnalyzing(true);
    try {
      // Si ré-analyse, supprimer l'ancienne décision d'abord
      if (forceReanalyze && decision) {
        try { await decisionService.deleteByTicket(id); } catch { /* ignore */ }
        setDecision(null);
      }
      const data: Record<string, unknown> = {};
      for (const fd of (fieldRefs?.fields || [])) {
        const val = formData[fd.field];
        if (val !== undefined && val !== '') {
          data[fd.field] = fd.type === 'number' ? parseInt(val) : val;
        }
      }
      const result = await decisionService.analyze({
        ticketId: id,
        typePiece: (data.typePiece as string) || '',
        ageVehiculeAns: (data.ageVehiculeAns as number) || 0,
        kilometrage: (data.kilometrage as number) || 0,
        categorieClient: (data.categorieClient as string) || '',
        typeSinistre: (data.typeSinistre as string) || '',
        pieceReference: (data.pieceReference as string) || pieceReference || undefined,
        transcriptionText: ticket?.transcription || undefined,
      });
      setDecision(result);
      const emoji = result.decision === 'FAVORABLE' ? '🟢' : result.decision === 'DEFAVORABLE' ? '🔴' : '🟡';
      toast.success(`${emoji} ${result.decision} — Score: ${result.score}%`);
    } catch {
      toast.error('Erreur d\'analyse IA');
    } finally {
      setAnalyzing(false);
    }
  };

  const handleAccept = async () => {
    if (!decision || !user) return;
    if (!motif.trim()) { toast.error('Veuillez saisir un motif (obligatoire)'); return; }
    setAccepting(true);
    try {
      const updated = await decisionService.accept(decision.decisionId, {
        finalDecision: 'FAVORABLE',
        motif: motif.trim(),
        operateurId: user.userId,
        operateurName: (user.prenom + ' ' + user.nom),
        clientNom: ticket!.clientNom,
        clientEmail: ticket!.clientEmail || undefined,
      });
      setDecision(updated);
      toast.success('✅ Décision acceptée — ticket résolu automatiquement');
      // Bug 2 fix: Auto-transition ticket → RESOLU après décision finale
      try {
        const updatedTicket = await ticketService.updateStatut(id!, 'RESOLU');
        setTicket(updatedTicket);
      } catch { /* transition may already be done */ }
    } catch {
      toast.error('Erreur lors de l\'acceptation');
    } finally {
      setAccepting(false);
    }
  };

  const handleReject = async () => {
    if (!decision || !user) return;
    if (!motif.trim()) { toast.error('Veuillez saisir un motif (obligatoire)'); return; }
    setRejecting(true);
    try {
      const updated = await decisionService.reject(decision.decisionId, {
        finalDecision: 'DEFAVORABLE',
        motif: motif.trim(),
        operateurId: user.userId,
        operateurName: (user.prenom + ' ' + user.nom),
        clientNom: ticket!.clientNom,
        clientEmail: ticket!.clientEmail || undefined,
      });
      setDecision(updated);
      toast.success('❌ Décision rejetée — ticket résolu automatiquement');
      // Bug 2 fix: Auto-transition ticket → RESOLU après décision finale
      try {
        const updatedTicket = await ticketService.updateStatut(id!, 'RESOLU');
        setTicket(updatedTicket);
      } catch { /* transition may already be done */ }
    } catch {
      toast.error('Erreur lors du rejet');
    } finally {
      setRejecting(false);
    }
  };

  const handleDownloadPDF = async () => {
    if (!decision) return;
    setDownloading(true);
    try {
      const blob = await decisionService.downloadLetter(decision.decisionId);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `courrier_${ticket?.numero || id}.pdf`;
      a.click();
      URL.revokeObjectURL(url);
      toast.success('Courrier PDF téléchargé');
    } catch {
      toast.error('Erreur lors du téléchargement');
    } finally {
      setDownloading(false);
    }
  };

  const handleChangeStatut = async (statut: string) => {
    if (!id) return;
    try {
      const updated = await ticketService.updateStatut(id, statut);
      setTicket(updated);
      toast.success(`Statut changé en ${statut}`);
    } catch {
      toast.error('Erreur changement statut');
    }
  };

  const canalBadge = (canal: string) => ({ CALL: { l: '☎ Appel', c: 'bg-violet-500/10 text-violet-400 border-violet-500/20' }, EMAIL: { l: '✉ Email', c: 'bg-blue-400/10 text-blue-400 border-blue-400/20' }, WEB: { l: '⬡ Web', c: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' } }[canal] || { l: canal, c: 'bg-[var(--muted)] text-txt-3' });
  const statutBadge = (statut: string) => ({ NOUVEAU: { l: '● Nouveau', c: 'bg-blue-500/10 text-blue-400 border-blue-500/20' }, EN_COURS: { l: '◷ En cours', c: 'bg-amber-500/10 text-amber-400 border-amber-500/20' }, RESOLU: { l: '✓ Résolu', c: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' }, FERME: { l: '— Fermé', c: 'bg-zinc-500/10 text-zinc-400 border-zinc-500/20' } }[statut] || { l: statut, c: '' });
  const decisionBadge = (d: string) => ({ FAVORABLE: { l: '✓ Favorable', c: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' }, DEFAVORABLE: { l: '✕ Défavorable', c: 'bg-rose-500/10 text-rose-400 border-rose-500/20' }, ETUDE: { l: '◷ Étude', c: 'bg-amber-500/10 text-amber-400 border-amber-500/20' } }[d] || { l: d, c: '' });

  if (loading) return <div className="space-y-4"><CardSkeleton rows={4} /><div className="grid grid-cols-1 lg:grid-cols-[1fr_340px] gap-4"><CardSkeleton rows={8} /><CardSkeleton rows={6} /></div></div>;
  if (error || !ticket) return <ErrorState message={error || 'Ticket introuvable'} onRetry={fetchTicket} />;

  const cs = canalBadge(ticket.canal);
  const ss = statutBadge(ticket.statut);
  const keywords = ticket.transcriptionKeywords?.split(',').map(k => k.trim()).filter(Boolean) || [];

  return (
    <div className="flex flex-col gap-4">
      {/* Breadcrumb */}
      <div className="flex items-center gap-2 text-xs">
        <button onClick={() => navigate('/')} className="text-txt-3 hover:text-txt cursor-pointer">Dashboard</button>
        <span className="text-txt-3">/</span>
        <span className="text-primary font-mono">{ticket.numero}</span>
      </div>

      {/* Header */}
      <div className="card-aurora rounded-2xl p-5 relative overflow-hidden">
        <div className="absolute top-0 left-0 right-0 h-[2px] bg-gradient-to-r from-indigo-500 via-violet-400 to-teal-400" />
        <div className="flex items-start gap-4">
          <div className="flex-1">
            <div className="font-mono text-[10px] text-primary mb-1">{ticket.numero} · {new Date(ticket.createdAt.endsWith('Z') ? ticket.createdAt : ticket.createdAt + 'Z').toLocaleString('fr-FR')}</div>
            <h1 className="text-xl font-extrabold tracking-tight mb-0.5">{ticket.clientNom}</h1>
            <div className="font-mono text-[10px] text-txt-3">{ticket.clientTel && `${ticket.clientTel} · `}{ticket.clientEmail || ''}</div>
            <div className="flex gap-1.5 mt-3 flex-wrap">
              <span className={`badge border ${cs.c}`}>{cs.l}</span>
              <span className={`badge border ${ss.c}`}>{ss.l}</span>
              {ticket.priorite === 1 && <span className="badge border bg-rose-500/10 text-rose-400 border-rose-500/20">🔥 Urgent</span>}
            </div>
          </div>
          <div className="flex flex-col gap-1.5">
            {/* Bug 5 fix: Only show valid transitions */}
            <select
              value={ticket.statut}
              onChange={(e) => handleChangeStatut(e.target.value)}
              disabled={ticket.statut === 'FERME'}
              className="aurora-select text-[11px] py-1.5 w-32 disabled:opacity-50"
            >
              <option value={ticket.statut}>{({NOUVEAU:'Nouveau',EN_COURS:'En cours',RESOLU:'Résolu',FERME:'Fermé'} as Record<string,string>)[ticket.statut]}</option>
              {ticket.statut === 'NOUVEAU' && <option value="EN_COURS">→ En cours</option>}
              {ticket.statut === 'NOUVEAU' && <option value="FERME">→ Fermé</option>}
              {ticket.statut === 'EN_COURS' && <option value="RESOLU">→ Résolu</option>}
              {ticket.statut === 'EN_COURS' && <option value="FERME">→ Fermé</option>}
              {ticket.statut === 'RESOLU' && <option value="FERME">→ Fermé</option>}
            </select>
          </div>
        </div>
        {ticket.description && (
          <div className="mt-3.5 p-3 bg-[var(--elevated)] border border-border rounded-lg text-xs text-txt-2 leading-relaxed">
            {ticket.description}
          </div>
        )}
      </div>

      {/* Content grid */}
      <div className="grid grid-cols-1 lg:grid-cols-[1fr_340px] gap-4">
        <div className="flex flex-col gap-4">
          {/* ═══ Contenu selon le canal (CDC Modules 2 & 3) ═══ */}

          {/* CANAL EMAIL — Afficher le contenu de l'email parsé */}
          {ticket.canal === 'EMAIL' && (
            <div className="card-aurora rounded-2xl p-5">
              <div className="flex items-center justify-between mb-3">
                <h3 className="text-sm font-bold">📧 Contenu de l'email</h3>
                <span className="font-mono text-[9px] px-2 py-0.5 rounded bg-blue-500/10 text-blue-400 border border-blue-500/20">
                  Canal EMAIL — Parsing IMAP automatique
                </span>
              </div>
              <div className="p-3.5 bg-[var(--elevated)] border border-border rounded-lg">
                <div className="text-xs text-txt-2 leading-relaxed whitespace-pre-line">
                  {ticket.description || 'Aucun contenu disponible'}
                </div>
              </div>
              {ticket.clientEmail && (
                <div className="mt-2 font-mono text-[10px] text-txt-3">
                  Expéditeur : {ticket.clientEmail}
                </div>
              )}
            </div>
          )}

          {/* CANAL WEB — Afficher le formulaire soumis */}
          {ticket.canal === 'WEB' && (
            <div className="card-aurora rounded-2xl p-5">
              <div className="flex items-center justify-between mb-3">
                <h3 className="text-sm font-bold">🌐 Formulaire web soumis</h3>
                <span className="font-mono text-[9px] px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                  Canal WEB — API Rest publique
                </span>
              </div>
              <div className="p-3.5 bg-[var(--elevated)] border border-border rounded-lg">
                <div className="text-xs text-txt-2 leading-relaxed whitespace-pre-line">
                  {ticket.description || 'Aucune description disponible'}
                </div>
              </div>
              <div className="mt-2 flex gap-4 font-mono text-[10px] text-txt-3">
                {ticket.clientEmail && <span>Email : {ticket.clientEmail}</span>}
                {ticket.clientTel && <span>Tél : {ticket.clientTel}</span>}
              </div>
            </div>
          )}

          {/* CANAL CALL — Audio + Transcription (CDC Module 3 : Traitement de l'Appel) */}
          {ticket.canal === 'CALL' && (
            <>
              {/* Audio — CDC Module 3 : Enregistrement auto Twilio */}
              <div className="card-aurora rounded-2xl p-5">
                <div className="flex items-center justify-between mb-3">
                  <h3 className="text-sm font-bold">🎙 Enregistrement audio</h3>
                  <span className="font-mono text-[9px] px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                    🔄 Twilio — Enregistrement automatique
                  </span>
                </div>
                {ticket.audioPath ? (
                  <div className="p-3 bg-[var(--elevated)] border border-border rounded-lg">
                    <div className="flex items-center gap-2 mb-1">
                      <div className="w-2 h-2 rounded-full bg-emerald-400" />
                      <span className="text-xs text-success font-medium">Enregistrement disponible</span>
                    </div>
                    <div className="font-mono text-[10px] text-txt-3">Fichier : {ticket.audioPath.split('/').pop()}</div>
                    {/* Pipeline visuel Capturé → Transcrit → Analysé */}
                    <div className="mt-2 flex items-center gap-3">
                      <div className="flex items-center gap-1.5">
                        <div className="w-5 h-5 rounded-full bg-emerald-500/10 flex items-center justify-center text-[8px]">✓</div>
                        <span className="font-mono text-[9px] text-emerald-400">Capturé</span>
                      </div>
                      <div className="w-4 h-px bg-border" />
                      <div className="flex items-center gap-1.5">
                        <div className={`w-5 h-5 rounded-full flex items-center justify-center text-[8px] ${ticket.transcription ? 'bg-emerald-500/10' : 'bg-amber-500/10'}`}>
                          {ticket.transcription ? '✓' : '⏳'}
                        </div>
                        <span className={`font-mono text-[9px] ${ticket.transcription ? 'text-emerald-400' : 'text-amber-400'}`}>
                          {ticket.transcription ? 'Transcrit' : 'Transcription...'}
                        </span>
                      </div>
                      <div className="w-4 h-px bg-border" />
                      <div className="flex items-center gap-1.5">
                        <div className={`w-5 h-5 rounded-full flex items-center justify-center text-[8px] ${ticket.transcription ? 'bg-emerald-500/10' : 'bg-[var(--muted)]'}`}>
                          {ticket.transcription ? '✓' : '○'}
                        </div>
                        <span className={`font-mono text-[9px] ${ticket.transcription ? 'text-emerald-400' : 'text-txt-3'}`}>
                          {ticket.transcription ? 'Analysé' : 'NLP en attente'}
                        </span>
                      </div>
                    </div>
                  </div>
                ) : (
                  <div className="text-center py-8">
                    <div className="text-3xl mb-2 opacity-50">📞</div>
                    <div className="text-xs text-txt-2 font-medium">Enregistrement automatique en cours</div>
                    <div className="text-[10px] text-txt-3 mt-1">L'appel Twilio est enregistré automatiquement. L'audio sera disponible dès la fin de l'appel.</div>
                    <div className="mt-3 flex items-center justify-center gap-2">
                      <div className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
                      <span className="font-mono text-[10px] text-emerald-400">Appel actif — enregistrement</span>
                    </div>
                  </div>
                )}
              </div>

              {/* ── Transcription Live (CDC Module 3 : Streaming STT temps réel) ── */}
              <LiveTranscription ticketId={id!} onTranscriptionUpdate={() => fetchTicket()} />

              {/* Transcription — CDC Module 3 : Interface texte/audio synchronisée */}
              <div className="card-aurora rounded-2xl p-5">
                <div className="flex items-center justify-between mb-3">
                  <h3 className="text-sm font-bold">📝 Transcription</h3>
                  {ticket.transcription && (
                    <span className="font-mono text-[9px] px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                      ✓ {ticket.transcription.length} caractères
                    </span>
                  )}
                </div>
                {ticket.transcription ? (
                  <div>
                    {/* Segmented transcription with timestamps */}
                    <div className="p-3.5 bg-[var(--elevated)] border border-border rounded-lg max-h-[240px] overflow-y-auto mb-3 space-y-2">
                      {(() => {
                        const text = ticket.transcription;
                        const segmentRegex = /\[(\d{1,2}:\d{2}(?::\d{2})?)\]\s*/g;
                        const parts: { time: string; text: string }[] = [];
                        let lastIdx = 0;
                        let match;
                        while ((match = segmentRegex.exec(text)) !== null) {
                          if (lastIdx < match.index && lastIdx === 0) {
                            const pre = text.slice(lastIdx, match.index).trim();
                            if (pre) parts.push({ time: '00:00', text: pre });
                          }
                          lastIdx = match.index + match[0].length;
                          const nextMatch = segmentRegex.exec(text);
                          const endIdx = nextMatch ? nextMatch.index : text.length;
                          parts.push({ time: match[1], text: text.slice(lastIdx, endIdx).trim() });
                          lastIdx = endIdx;
                          if (nextMatch) { segmentRegex.lastIndex = nextMatch.index; }
                        }
                        if (parts.length === 0) {
                          const sentences = text.split(/(?<=[.!?])\s+/).filter(Boolean);
                          const avgDuration = 5;
                          sentences.forEach((s, i) => {
                            const mins = Math.floor((i * avgDuration) / 60);
                            const secs = (i * avgDuration) % 60;
                            parts.push({ time: `${mins}:${String(secs).padStart(2, '0')}`, text: s });
                          });
                        }
                        const highlightText = (text: string) => {
                          if (keywords.length === 0) return <span>{text}</span>;
                          const pattern = new RegExp(`(${keywords.map(k => k.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|')})`, 'gi');
                          const frags = text.split(pattern);
                          return frags.map((frag, i) =>
                            keywords.some(k => k.toLowerCase() === frag.toLowerCase())
                              ? <mark key={i} className="bg-indigo-500/20 text-indigo-300 rounded px-0.5 font-medium">{frag}</mark>
                              : <span key={i}>{frag}</span>
                          );
                        };
                        return parts.map((seg, i) => (
                          <div key={i} className="flex gap-2.5 group">
                            <button className="font-mono text-[9px] text-indigo-400 bg-indigo-500/10 border border-indigo-500/20 rounded px-1.5 py-0.5 h-fit mt-0.5 flex-shrink-0 cursor-pointer hover:bg-indigo-500/20 transition-colors">
                              {seg.time}
                            </button>
                            <div className="text-xs text-txt-2 leading-relaxed flex-1">{highlightText(seg.text)}</div>
                          </div>
                        ));
                      })()}
                    </div>
                    {keywords.length > 0 && (
                      <div className="flex items-center gap-1.5 flex-wrap mb-3">
                        <span className="font-mono text-[9px] text-txt-3 mr-1">Mots-clés :</span>
                        {keywords.map((kw) => (
                          <span key={kw} className="font-mono text-[9px] px-2 py-0.5 rounded bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">{kw}</span>
                        ))}
                      </div>
                    )}
                    <div className="border-t border-border pt-3 mt-1">
                      <label htmlFor="ticket-operator-notes" className="font-mono text-[9px] text-txt-3 uppercase tracking-wider block mb-1.5">📋 Notes de l'opérateur</label>
                      <textarea id="ticket-operator-notes" className="aurora-input text-[11px] w-full h-[60px] resize-none" placeholder="Ajoutez vos observations sur la transcription…" defaultValue="" />
                    </div>
                  </div>
                ) : (
                  <div className="text-center py-8">
                    <div className="text-3xl mb-2 opacity-30">📄</div>
                    <div className="text-xs text-txt-3">{ticket.audioPath ? 'Transcription en cours de traitement...' : 'En attente de la fin de l\'appel pour la transcription'}</div>
                    {ticket.audioPath && (
                      <div className="mt-3 flex items-center justify-center gap-2">
                        <div className="w-2 h-2 rounded-full bg-indigo-400 animate-pulse" />
                        <span className="font-mono text-[10px] text-indigo-400">Traitement IA en cours</span>
                      </div>
                    )}
                  </div>
                )}
              </div>
            </> /* Fin CALL */
          )}

          {/* Timeline */}
          {ticket.actions && ticket.actions.length > 0 && (
            <div className="card-aurora rounded-2xl p-5">
              <h3 className="text-sm font-bold mb-3">📋 Historique</h3>
              <div className="flex flex-col">
                {ticket.actions.map((action, i) => (
                  <div key={action.id} className="flex gap-3 pb-3 last:pb-0">
                    <div className="flex flex-col items-center w-5 flex-shrink-0">
                      <div className="w-2 h-2 rounded-full bg-primary mt-1.5 z-[1]" />
                      {i < ticket.actions.length - 1 && <div className="flex-1 w-px bg-border mt-1" />}
                    </div>
                    <div>
                      {/* Bug 6 fix: Show transition details */}
                      <div className="text-xs font-medium">
                        {action.action === 'STATUT_CHANGE' && action.oldValue && action.newValue
                          ? `${action.oldValue} → ${action.newValue}`
                          : action.action}
                      </div>
                      {action.notes && <div className="text-[11px] text-txt-2 mt-0.5">{action.notes}</div>}
                      <div className="font-mono text-[9px] text-txt-3 mt-0.5">
                        {new Date(action.createdAt.endsWith('Z') ? action.createdAt : action.createdAt + 'Z').toLocaleString('fr-FR')} · {action.userName}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Right panel */}
        <div className="flex flex-col gap-4">
          {/* ── Saisie de la Demande (CDC étape 6) ── */}
          <div className="card-aurora rounded-2xl overflow-hidden relative">
            <div className="absolute top-0 left-0 right-0 h-[2px] bg-gradient-to-r from-amber-400 to-orange-500" />
            <div className="p-5">
              <div className="flex items-center justify-between mb-3">
                <h3 className="text-sm font-bold">📋 Saisie de la demande</h3>
                {/* Bug 7: Show locked badge on closed tickets */}
                {(ticket.statut === 'FERME' || ticket.statut === 'RESOLU') && (
                  <span className="font-mono text-[9px] px-2 py-0.5 rounded bg-zinc-500/10 text-zinc-400 border border-zinc-500/20">🔒 Verrouillé</span>
                )}
              </div>
              <div className="space-y-2.5">
                {(fieldRefs?.fields || []).filter((fd: FieldDef) => fd.field !== 'transcriptionText').map((fd: FieldDef) => {
                  const isRequired = ['typePiece', 'ageVehiculeAns', 'kilometrage', 'categorieClient', 'typeSinistre'].includes(fd.field);
                  const isLocked = ticket.statut === 'FERME' || ticket.statut === 'RESOLU';
                  return (
                  <div key={fd.field}>
                    <label className="font-mono text-[9px] text-txt-3 uppercase tracking-wider block mb-1">{fd.label}{isRequired && <span className="text-rose-400 ml-0.5">*</span>}</label>
                    {fd.type === 'enum' && fd.values && fd.values.length > 0 ? (
                      <select value={formData[fd.field] || ''} onChange={e => setFormData(p => ({ ...p, [fd.field]: e.target.value }))} disabled={isLocked} className="aurora-select text-[11px] py-1.5 w-full disabled:opacity-50">
                        <option value="">Choisir…</option>
                        {fd.values.map(v => <option key={v} value={v}>{v}</option>)}
                      </select>
                    ) : (
                      <input type={fd.type === 'number' ? 'number' : 'text'}
                        placeholder={fd.type === 'number' ? 'Ex: 5' : 'Valeur'}
                        value={formData[fd.field] || ''}
                        onChange={e => setFormData(p => ({ ...p, [fd.field]: e.target.value }))}
                        disabled={isLocked}
                        className="aurora-input text-[11px] py-1.5 w-full disabled:opacity-50" />
                    )}
                  </div>
                  );
                })}
              </div>
            </div>
          </div>

          {/* Photos sinistre (CDC Module 5) */}
          <div className="card-aurora rounded-2xl overflow-hidden relative">
            <div className="absolute top-0 left-0 right-0 h-[2px] bg-gradient-to-r from-pink-400 to-rose-500" />
            <div className="p-5">
              <h3 className="text-sm font-bold mb-3">📸 Photos du sinistre</h3>

              {/* Existing photos */}
              {existingPhotos.length > 0 && (
                <div className="mb-3">
                  <span className="font-mono text-[9px] text-txt-3 uppercase tracking-wider block mb-1.5">Photos enregistrées ({existingPhotos.length})</span>
                  <div className="flex flex-wrap gap-2">
                    {existingPhotos.map((path, i) => {
                      const imgUrl = `${import.meta.env.VITE_API_URL || ''}/api/tickets${path}`;
                      return (
                        <div key={i} className="relative w-16 h-16 rounded-lg bg-[var(--elevated)] border border-border overflow-hidden cursor-pointer group"
                          onClick={() => window.open(imgUrl, '_blank')}>
                          <img src={imgUrl} alt={`Photo ${i+1}`} className="w-full h-full object-cover" onError={e => { (e.target as HTMLImageElement).style.display = 'none'; (e.target as HTMLImageElement).parentElement!.innerHTML = '<span class="text-lg flex items-center justify-center w-full h-full">🖼️</span>'; }} />
                          <span className="absolute bottom-0.5 right-0.5 font-mono text-[7px] bg-black/50 text-white px-1 rounded">{i + 1}</span>
                          <div className="absolute inset-0 bg-black/0 group-hover:bg-black/20 transition-all flex items-center justify-center opacity-0 group-hover:opacity-100">
                            <span className="text-white text-xs">🔍</span>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              )}

              {/* Upload zone */}
              <div
                className="border-2 border-dashed border-border rounded-xl p-4 text-center cursor-pointer hover:border-indigo-500/40 hover:bg-indigo-500/[0.02] transition-all"
                onClick={() => photoInputRef.current?.click()}
                onDragOver={e => { e.preventDefault(); e.currentTarget.classList.add('border-indigo-500/60', 'bg-indigo-500/5'); }}
                onDragLeave={e => { e.preventDefault(); e.currentTarget.classList.remove('border-indigo-500/60', 'bg-indigo-500/5'); }}
                onDrop={e => { e.preventDefault(); e.currentTarget.classList.remove('border-indigo-500/60', 'bg-indigo-500/5'); handlePhotoSelect(e.dataTransfer.files); }}
              >
                <input
                  ref={photoInputRef}
                  type="file"
                  multiple
                  accept="image/*"
                  className="hidden"
                  onChange={e => handlePhotoSelect(e.target.files)}
                />
                <div className="text-2xl mb-1">📷</div>
                <div className="text-[11px] text-txt-2 font-medium">Glisser-déposer ou cliquer pour ajouter</div>
                <div className="font-mono text-[9px] text-txt-3 mt-0.5">JPEG, PNG, WebP · max 10MB par photo</div>
              </div>

              {/* Previews */}
              {photoPreviews.length > 0 && (
                <div className="mt-3">
                  <div className="flex flex-wrap gap-2 mb-3">
                    {photoPreviews.map((src, i) => (
                      <div key={i} className="relative group">
                        <img src={src} alt={`Photo ${i + 1}`} className="w-16 h-16 rounded-lg object-cover border border-border" />
                        <button
                          onClick={(e) => { e.stopPropagation(); removePhoto(i); }}
                          className="absolute -top-1.5 -right-1.5 w-5 h-5 rounded-full bg-rose-500 text-white text-[10px] flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer"
                        >✕</button>
                      </div>
                    ))}
                  </div>
                  <button
                    onClick={handleUploadPhotos}
                    disabled={uploadingPhotos}
                    className="btn-cta h-8 px-4 rounded-lg text-[10px] font-mono text-white cursor-pointer disabled:opacity-50 w-full"
                  >
                    {uploadingPhotos ? '⏳ Upload en cours...' : `📤 Uploader ${photoFiles.length} photo(s)`}
                  </button>
                </div>
              )}
            </div>
          </div>

          {/* AI Decision */}
          <div className="card-aurora rounded-2xl overflow-hidden relative">
            <div className="absolute top-0 left-0 right-0 h-[2px] bg-gradient-to-r from-teal-400 to-indigo-500" />
            <div className="p-5">
              <div className="flex items-center justify-between mb-4">
                <h3 className="text-sm font-bold">🤖 Aide à la Décision</h3>
                <button
                  onClick={() => handleAnalyze(!!decision)}
                  disabled={analyzing || ticket.statut === 'FERME'}
                  className={`h-8 px-3.5 rounded-lg text-[10px] font-mono cursor-pointer disabled:opacity-50 ${
                    decision
                      ? 'border border-border text-txt-2 hover:bg-[var(--elevated)] transition-colors'
                      : 'btn-cta text-white'
                  }`}
                >
                  {analyzing ? '⏳ Analyse...' : decision ? '🔄 Ré-analyser' : '🔍 Analyser'}
                </button>
              </div>

              {decision ? (
                <div>
                  {/* Score ring */}
                  <div className="flex gap-3 items-center mb-4">
                    <div className="relative flex-shrink-0">
                      <svg width="80" height="80" viewBox="0 0 80 80">
                        <circle cx="40" cy="40" r="33" fill="none" stroke="var(--muted)" strokeWidth="7" />
                        <circle cx="40" cy="40" r="33" fill="none" stroke="url(#score-g)" strokeWidth="7"
                          strokeDasharray={`${(decision.score / 100) * 207} 207`}
                          strokeLinecap="round" transform="rotate(-90 40 40)"
                          className="transition-all duration-1000"
                        />
                        <defs><linearGradient id="score-g" x1="0%" y1="0%" x2="100%"><stop offset="0%" stopColor="#00D4AA" /><stop offset="100%" stopColor="#635BFF" /></linearGradient></defs>
                      </svg>
                      <div className="absolute inset-0 flex items-center justify-center flex-col">
                        <span className="text-xl font-extrabold text-gradient-ai">{Math.round(decision.score)}%</span>
                        <span className="font-mono text-[7px] text-txt-3">SCORE</span>
                      </div>
                    </div>
                    <div>
                      {(() => { const db = decisionBadge(decision.decision); return <span className={`badge border text-xs font-bold ${db.c}`}>{db.l}</span>; })()}
                      <div className="text-[10.5px] text-txt-2 mt-1 leading-relaxed">{decision.motif}</div>
                    </div>
                  </div>

                  {/* Keywords from transcription — CDC: "mots-clés transcription audio" */}
                  {keywords.length > 0 && (
                    <div className="mb-4">
                      <div className="font-mono text-[9px] text-txt-3 uppercase tracking-wider mb-2">Mots-clés transcription</div>
                      <div className="flex flex-wrap gap-1.5">
                        {keywords.map((kw) => (
                          <span key={kw} className="px-2 py-0.5 rounded-full bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 text-[10px] font-mono">
                            {kw}
                          </span>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Rules applied */}
                  {decision.rulesApplied.length > 0 && (
                    <div className="mb-4">
                      <div className="font-mono text-[9px] text-txt-3 uppercase tracking-wider mb-2">Règles appliquées</div>
                      <div className="space-y-1">
                        {decision.rulesApplied.map((r) => (
                          <div key={r} className="flex items-center gap-2 p-2 bg-[var(--elevated)] border border-border rounded-lg text-[11px]">
                            <span className="text-emerald-400">✓</span> {r}
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Bug 8: Better source labels */}
                  <div className="font-mono text-[9px] text-txt-3 mb-3">
                    Source : {decision.source === 'AI' ? '🤖 IA' : decision.source === 'RULES' ? '📏 Règles' : decision.source === 'HYBRID' ? '🔄 Règles + IA' : decision.source === 'CIRCUIT_BREAKER' ? '⚡ Fallback' : decision.source} · {decision.cached ? '📦 Cache' : '🆕 Nouveau'} · {decision.ambiguous ? '⚠ Vérification requise' : '✓ Claire'}
                  </div>

                  {/* Actions — CDC: Suivi ou forçage (avec motif) */}
                  {/* Bug 3 fix: Hide on FERME/RESOLU tickets */}
                  {!decision.finalDecision && ticket.statut !== 'FERME' && ticket.statut !== 'RESOLU' && (
                    <div className="space-y-2 mb-2">
                      <div>
                        <label htmlFor="ticket-decision-motif" className="font-mono text-[9px] text-txt-3 uppercase tracking-wider block mb-1">Motif de la décision *</label>
                        <textarea
                          id="ticket-decision-motif"
                          className="aurora-input text-[11px] w-full h-[50px] resize-none"
                          placeholder="Saisissez le motif de votre décision (obligatoire)…"
                          value={motif}
                          onChange={(e) => setMotif(e.target.value)}
                        />
                      </div>
                      <div className="flex gap-2">
                        <button onClick={handleAccept} disabled={accepting || !motif.trim()} className="btn-accept flex-1 h-9 rounded-lg font-mono text-[10px] font-medium flex items-center justify-center cursor-pointer disabled:opacity-50">
                          {accepting ? '...' : '✓ Accepter'}
                        </button>
                        <button onClick={handleReject} disabled={rejecting || !motif.trim()} className="btn-reject flex-1 h-9 rounded-lg font-mono text-[10px] font-medium flex items-center justify-center cursor-pointer disabled:opacity-50">
                          {rejecting ? '...' : '✕ Rejeter'}
                        </button>
                      </div>
                    </div>
                  )}
                  {decision.finalDecision && (
                    <div className={`p-2.5 rounded-lg border text-center text-xs font-bold mb-2 ${decision.finalDecision === 'FAVORABLE' ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' : 'bg-rose-500/10 text-rose-400 border-rose-500/20'}`}>
                      Décision finale : {decision.finalDecision} {decision.overrideMotif && `— ${decision.overrideMotif}`}
                    </div>
                  )}
                  {decision.finalDecision && (
                    <button onClick={handleDownloadPDF} disabled={downloading} className="btn-ghost w-full h-9 rounded-lg font-mono text-[10px] flex items-center justify-center cursor-pointer disabled:opacity-50">
                      {downloading ? '⏳ Génération...' : '📄 Télécharger courrier PDF'}
                    </button>
                  )}
                </div>
              ) : (
                <div className="text-center py-8">
                  <div className="text-3xl mb-2 opacity-30">🤖</div>
                  <div className="text-xs text-txt-3">Cliquez sur "Analyser" pour lancer l'aide à la décision IA</div>
                </div>
              )}
            </div>
          </div>

          {/* Ticket info */}
          <div className="card-aurora rounded-2xl p-5">
            <h3 className="text-sm font-bold mb-3">ℹ️ Informations</h3>
            <div className="space-y-2">
              {[
                { label: 'Numéro', value: ticket.numero },
                { label: 'Agence', value: ticket.agenceName || agenceName || '—' },
                { label: 'Opérateur', value: ticket.operateurName || operateurName || (ticket.operateurId ? 'Non identifié' : 'Non assigné') },
                { label: 'Créé le', value: new Date(ticket.createdAt.endsWith('Z') ? ticket.createdAt : ticket.createdAt + 'Z').toLocaleString('fr-FR') },
                { label: 'Mis à jour', value: new Date(ticket.updatedAt.endsWith('Z') ? ticket.updatedAt : ticket.updatedAt + 'Z').toLocaleString('fr-FR') },
                { label: 'Consentement', value: ticket.consentementEnregistrement ? 'Oui' : 'Non' },
              ].map((info) => (
                <div key={info.label} className="flex items-center justify-between text-xs border-b border-border pb-2 last:border-0 last:pb-0">
                  <span className="text-txt-3">{info.label}</span>
                  <span className="font-mono text-[10px] text-right max-w-[200px] truncate">{info.value}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

