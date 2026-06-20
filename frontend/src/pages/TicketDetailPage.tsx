import { useState, useEffect, useCallback, useRef, useMemo, type ChangeEvent } from 'react';
import { safeRandom } from '../utils/cryptoRandom';
import { useParams, useNavigate } from 'react-router-dom';
import { 
  Play, Pause, User, Phone, Mail, Award, Clock, Sparkles, Brain,
  AlertTriangle, Shield, ShieldAlert, ArrowLeft, Download, Trash2, Camera,
  RefreshCw, Lock, HardDrive, MessageSquare, FileText, CheckCircle2, XCircle
} from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import toast from 'react-hot-toast';
import { ticketService } from '../services/ticketService';
import { decisionService } from '../services/decisionService';
import { ruleService } from '../services/ruleService';
import { agenceService } from '../services/agenceService';
import { userService } from '../services/userService';
import { useAuth } from '../contexts/AuthContext';
import { CardSkeleton, ErrorState } from '../components/ui/States';
import type { Ticket } from '../types/ticket';
import type { DecisionResult, DecisionRequest } from '../types/decision';
import type { ReferenceData, FieldDef } from '../types/rule';

/* ───── Module-level helpers (reduce cognitive complexity) ───── */

function validateMissingFields(
  formData: Record<string, string>,
  requiredFields: string[],
  fieldRefs: ReferenceData | null
): string[] {
  const missing: string[] = [];
  for (const key of requiredFields) {
    const val = formData[key];
    if (val === undefined || val === null || val === '') {
      const fd = (fieldRefs?.fields ?? []).find((f: FieldDef) => f.field === key);
      missing.push(fd?.label ?? key);
    }
  }
  return missing;
}

function buildAnalyzePayload(
  formData: Record<string, string>,
  fieldRefs: ReferenceData | null,
  id: string,
  pieceReference: string,
  transcription: string | null | undefined
): DecisionRequest {
  const data: Record<string, unknown> = {};
  for (const fd of (fieldRefs?.fields ?? [])) {
    const val = formData[fd.field];
    if (val !== undefined && val !== '') {
      data[fd.field] = fd.type === 'number' ? Number.parseInt(val, 10) : val;
    }
  }
  return {
    ticketId: id,
    typePiece: (data.typePiece as string) || '',
    ageVehiculeAns: (data.ageVehiculeAns as number) || 0,
    kilometrage: (data.kilometrage as number) || 0,
    categorieClient: (data.categorieClient as string) || '',
    typeSinistre: (data.typeSinistre as string) || '',
    pieceReference: (data.pieceReference as string) || pieceReference || undefined,
    transcriptionText: transcription || undefined,
  };
}

function resolveDecisionEmoji(decision: string): string {
  if (decision === 'FAVORABLE') return '🟢';
  if (decision === 'DEFAVORABLE') return '🔴';
  return '🟡';
}

function readFileAsDataURL(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = (e) => resolve(e.target?.result as string);
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}

function getAuthHeaders(): Record<string, string> {
  const headers: Record<string, string> = {};
  try {
    const stored = localStorage.getItem('aurora-auth');
    if (stored) {
      const { accessToken } = JSON.parse(stored);
      if (accessToken) headers['Authorization'] = `Bearer ${accessToken}`;
    }
  } catch {
    /* localStorage may be unavailable (private mode) — auth headers simply omitted */
  }
  return headers;
}

function generateWaveformBars(channelData: Float32Array, barCount = 48): number[] {
  const samplesPerBar = Math.floor(channelData.length / barCount);
  const bars: number[] = [];
  for (let i = 0; i < barCount; i++) {
    let sum = 0;
    for (let j = 0; j < samplesPerBar; j++) {
      sum += Math.abs(channelData[i * samplesPerBar + j]);
    }
    bars.push(sum / samplesPerBar);
  }
  const max = Math.max(...bars, 0.001);
  return bars.map(v => Math.round(15 + (v / max) * 85));
}

const CANAL_BADGE_MAP: Record<string, { l: string; c: string }> = {
  CALL: { l: '☎ Appel', c: 'bg-violet-500/10 text-violet-400 border-violet-500/20' },
  EMAIL: { l: '✉ Email', c: 'bg-blue-400/10 text-blue-400 border-blue-400/20' },
  WEB: { l: '⬡ Web', c: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' }
};

function getCanalBadge(canal: string) {
  return CANAL_BADGE_MAP[canal] || { l: canal, c: 'bg-[var(--muted)] text-txt-3' };
}

const STATUT_BADGE_MAP: Record<string, { l: string; c: string }> = {
  NOUVEAU: { l: 'Nouveau', c: 'bg-blue-500/10 text-blue-400 border-blue-500/20' },
  EN_COURS: { l: 'En cours', c: 'bg-amber-500/10 text-amber-400 border-amber-500/20' },
  RESOLU: { l: 'Résolu', c: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' },
  FERME: { l: 'Fermé', c: 'bg-zinc-500/10 text-zinc-400 border-zinc-500/20' }
};

function getStatutBadge(statut: string) {
  return STATUT_BADGE_MAP[statut] || { l: statut, c: '' };
}

const DECISION_BADGE_MAP: Record<string, { l: string; c: string }> = {
  FAVORABLE: { l: '✓ Favorable', c: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' },
  DEFAVORABLE: { l: '✕ Défavorable', c: 'bg-rose-500/10 text-rose-400 border-rose-500/20' },
  ETUDE: { l: '◷ Étude', c: 'bg-amber-500/10 text-amber-400 border-amber-500/20' }
};

function getDecisionBadge(d: string) {
  return DECISION_BADGE_MAP[d] || { l: d, c: '' };
}

interface DecisionStatusBadgeProps {
  decision: string;
  label: string;
}

function DecisionStatusBadge({ decision, label }: Readonly<DecisionStatusBadgeProps>) {
  if (decision === 'FAVORABLE') {
    return (
      <span className="inline-flex items-center gap-1.5 border text-[10px] font-extrabold uppercase tracking-wider px-2.5 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400 border-emerald-500/20">
        <CheckCircle2 size={10} className="text-emerald-400" />
        Recommandation : {label}
      </span>
    );
  }
  if (decision === 'DEFAVORABLE') {
    return (
      <span className="inline-flex items-center gap-1.5 border text-[10px] font-extrabold uppercase tracking-wider px-2.5 py-0.5 rounded-full bg-rose-500/10 text-rose-400 border-rose-500/20">
        <XCircle size={10} className="text-rose-400" />
        Recommandation : {label}
      </span>
    );
  }
  return (
    <span className="inline-flex items-center gap-1.5 border text-[10px] font-extrabold uppercase tracking-wider px-2.5 py-0.5 rounded-full bg-amber-500/10 text-amber-400 border-amber-500/20">
      <AlertTriangle size={10} className="text-amber-400" />
      Recommandation : {label}
    </span>
  );
}

interface TranscriptionViewerProps {
  transcription: string;
  keywords: string[];
}

function TranscriptionViewer({ transcription, keywords }: Readonly<TranscriptionViewerProps>) {
  const segments = useMemo(() => {
    const text = transcription;
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
      if (nextMatch) {
        segmentRegex.lastIndex = nextMatch.index;
      }
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
    return parts;
  }, [transcription]);

  const highlightText = useCallback((t: string) => {
    if (keywords.length === 0) return <span>{t}</span>;
    const escaped = keywords.map(k => k.replace(/[.*+?^${}()|[\]\\/]/g, String.raw`\\$&`)).join('|');
    const pattern = new RegExp('(' + escaped + ')', 'gi');
    const frags = t.split(pattern);
    const lowercasedKeywordsSet = new Set(keywords.map(k => k.toLowerCase()));
    let idx = 0;
    return frags.map((frag) => {
      const key = 'frag-' + (idx++);
      return lowercasedKeywordsSet.has(frag.toLowerCase()) ? (
        <mark key={key} className="bg-indigo-500/25 text-indigo-300 rounded px-1 py-0.5 font-bold border border-indigo-500/30">
          {frag}
        </mark>
      ) : (
        <span key={key}>{frag}</span>
      );
    });
  }, [keywords]);

  return (
    <div className="p-4 bg-[var(--elevated)] border border-border rounded-xl max-h-[280px] overflow-y-auto custom-scrollbar space-y-3 shadow-inner">
      {segments.map((seg) => (
        <div key={'part-' + seg.time} className="flex gap-3 group border-b border-border/20 pb-2.5 last:border-0 last:pb-0 hover:bg-[var(--surface)]/30 p-1.5 rounded-lg transition-colors">
          <span className="font-mono text-[9px] text-indigo-400 bg-indigo-500/10 border border-indigo-500/20 rounded-md px-2 py-0.5 h-fit mt-0.5 flex-shrink-0 cursor-default">
            {seg.time}
          </span>
          <div className="text-xs text-txt-2 leading-relaxed flex-1">{highlightText(seg.text)}</div>
        </div>
      ))}
    </div>
  );
}

/* ───── Live Transcription Component (Streaming STT) ───── */

interface StartMediaRecorderParams {
  ws: WebSocket;
  stream: MediaStream;
  recorderRef: { current: MediaRecorder | null };
  timerRef: { current: ReturnType<typeof setInterval> | null };
  setIsRecording: (v: boolean) => void;
  setElapsed: (fn: (p: number) => number) => void;
  setLiveText: (v: string) => void;
  setChunks: (v: number) => void;
}

function _startMediaRecorder(params: StartMediaRecorderParams) {
  const {
    ws,
    stream,
    recorderRef,
    timerRef,
    setIsRecording,
    setElapsed,
    setLiveText,
    setChunks
  } = params;
  const recorder = new MediaRecorder(stream, { mimeType: 'audio/webm;codecs=opus' });
  recorderRef.current = recorder;
  recorder.ondataavailable = (event) => {
    if (event.data.size > 0 && ws.readyState === WebSocket.OPEN) {
      ws.send(event.data);
    }
  };
  recorder.start(3000);
  setIsRecording(true);
  setElapsed(() => 0);
  setLiveText('');
  setChunks(0);
  timerRef.current = setInterval(() => { setElapsed(prev => prev + 1); }, 1000);
  toast.success('🎙 Transcription live démarrée');
}

function _processWsMessage(
  event: MessageEvent,
  setLiveText: React.Dispatch<React.SetStateAction<string>>,
  setChunks: React.Dispatch<React.SetStateAction<number>>,
  onTranscriptionUpdate?: (text: string) => void
) {
  try {
    const data = JSON.parse(event.data);
    if (data.type === 'partial') {
      setLiveText(prev => (prev ? prev + ' ' : '') + data.text);
      setChunks(data.chunks || 0);
    } else if (data.type === 'final') {
      setLiveText(prev => (prev ? prev + ' ' : '') + (data.text || ''));
      if (onTranscriptionUpdate && data.text) onTranscriptionUpdate(data.text);
    } else if (data.type === 'error') {
      toast.error(`Erreur STT: ${data.detail}`);
    }
  } catch {
    // Ignore malformed WebSocket messages — connection remains active
  }
}

function LiveTranscription({ ticketId, onTranscriptionUpdate }: Readonly<{ ticketId: string; onTranscriptionUpdate?: (text: string) => void }>) {
  const [isRecording, setIsRecording] = useState(false);
  const [liveText, setLiveText] = useState('');
  const [elapsed, setElapsed] = useState(0);
  const [chunks, setChunks] = useState(0);
  const wsRef = useRef<WebSocket | null>(null);
  const recorderRef = useRef<MediaRecorder | null>(null);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const AI_WS_URL = `ws://localhost:8000/ws/transcribe/${ticketId}`;

  const stopRecording = useCallback(() => {
    if (timerRef.current) {
      clearInterval(timerRef.current);
      timerRef.current = null;
    }

    if (recorderRef.current && recorderRef.current.state !== 'inactive') {
      recorderRef.current.stop();
      recorderRef.current.stream.getTracks().forEach(t => t.stop());
      recorderRef.current = null;
    }

    if (wsRef.current) {
      if (wsRef.current.readyState === WebSocket.OPEN) {
        wsRef.current.send('STOP');
      }
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

  const startRecording = useCallback(async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const ws = new WebSocket(AI_WS_URL);
      wsRef.current = ws;

      ws.onopen = () => {
        _startMediaRecorder({
          ws,
          stream,
          recorderRef,
          timerRef,
          setIsRecording,
          setElapsed: (fn) => setElapsed(fn),
          setLiveText,
          setChunks
        });
      };

      ws.onmessage = (e) => _processWsMessage(e, setLiveText, setChunks, onTranscriptionUpdate);
      ws.onerror = () => { toast.error('Erreur de connexion WebSocket'); stopRecording(); };
      ws.onclose = () => { if (isRecording) stopRecording(); };
    } catch {
      toast.error('Impossible d\'accéder au microphone');
    }
  }, [onTranscriptionUpdate, AI_WS_URL, isRecording, stopRecording]);

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
    <div className="card-aurora rounded-2xl p-5 relative overflow-hidden bg-gradient-to-br from-[var(--surface)] to-[var(--elevated)]">
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2">
          <div className="p-1.5 rounded-lg bg-indigo-500/10 text-indigo-400">
            <MessageSquare size={16} />
          </div>
          <h3 className="text-xs font-bold uppercase tracking-wider text-txt-2">🎙 Transcription temps réel</h3>
        </div>
        <div className="flex items-center gap-3">
          {isRecording && (
            <span className="font-mono text-[10px] text-txt-3 flex items-center gap-1 bg-[var(--muted)] px-2 py-0.5 rounded border border-border">
              <span className="w-1.5 h-1.5 rounded-full bg-rose-500 animate-pulse" />
              {formatTime(elapsed)} · {chunks} segments
            </span>
          )}
          <button
            onClick={isRecording ? stopRecording : startRecording}
            className={`h-8 px-4 rounded-xl text-[10px] font-mono font-medium flex items-center gap-2 cursor-pointer transition-all ${
              isRecording
                ? 'bg-rose-500/10 text-rose-400 border border-rose-500/30 hover:bg-rose-500/20 shadow-[0_0_15px_rgba(239,68,68,0.15)]'
                : 'btn-cta text-white shadow-lg shadow-indigo-500/25'
            }`}
          >
            {isRecording ? 'Arrêter l\'écoute' : '🎙 Démarrer le live'}
          </button>
        </div>
      </div>

      {isRecording && (
        <div className="mb-4 flex items-center justify-between p-3 bg-rose-500/5 border border-rose-500/10 rounded-xl">
          <div className="flex items-center gap-2">
            <span className="relative flex h-2 w-2">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-rose-400 opacity-75"></span>
              <span className="relative inline-flex rounded-full h-2 w-2 bg-rose-500"></span>
            </span>
            <span className="font-mono text-[10px] text-rose-400">En cours d'écoute — parlez dans votre micro</span>
          </div>
          {/* Siri-style Voice Visualizer */}
          <div className="flex items-end gap-[3px] h-3">
            {[1, 2, 3, 4, 5].map((bar) => (
              <span
                key={bar}
                className="w-[2px] bg-rose-400 rounded-full animate-bounce"
                style={{
                  height: `${20 + bar * 15}%`,
                  animationDuration: `${0.6 + bar * 0.1}s`,
                  animationDelay: `${bar * 0.05}s`
                }}
              />
            ))}
          </div>
        </div>
      )}

      {liveText ? (
        <div className="p-4 bg-[var(--elevated)] border border-border rounded-xl max-h-[220px] overflow-y-auto custom-scrollbar shadow-inner">
          <div className="text-xs text-txt-2 leading-relaxed whitespace-pre-wrap font-mono">
            {liveText}
            {isRecording && <span className="inline-block w-1.5 h-4 bg-primary ml-0.5 animate-pulse rounded-sm" />}
          </div>
        </div>
      ) : (
        <div className="text-center py-8 border border-dashed border-border rounded-xl bg-[var(--elevated)]/20">
          <div className="text-3xl mb-2 filter drop-shadow-md">🎙️</div>
          <div className="text-[11px] text-txt-3 max-w-[240px] mx-auto leading-relaxed">
            {isRecording ? 'En attente de parole...' : 'Cliquez sur "Démarrer le live" pour initier la dictée vocale assistée par Whisper'}
          </div>
        </div>
      )}
    </div>
  );
}

interface PremiumAudioPlayerProps {
  ticketId: string;
  filename?: string;
}

function PremiumAudioPlayer({ ticketId }: Readonly<PremiumAudioPlayerProps>) {
  const [isPlaying, setIsPlaying] = useState(false);
  const [progress, setProgress] = useState(0);
  const [duration, setDuration] = useState(0);
  const [currentTime, setCurrentTime] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [waveformData, setWaveformData] = useState<number[]>([]);

  const audioRef = useRef<HTMLAudioElement | null>(null);
  const animFrameRef = useRef<number | null>(null);

  // Build audio source URL (via gateway with JWT auth)
  const audioUrl = useMemo(() => {
    const base = import.meta.env.VITE_API_URL || '';
    return `${base}/api/audio/${ticketId}`;
  }, [ticketId]);

  // Fetch audio blob with JWT token (gateway requires auth)
  const [audioBlobUrl, setAudioBlobUrl] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    const fetchAudio = async () => {
      setIsLoading(true);
      setError(null);
      try {
        const headers = getAuthHeaders();
        const response = await fetch(audioUrl, { headers });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);

        const blob = await response.blob();
        if (cancelled) return;

        setAudioBlobUrl(URL.createObjectURL(blob));

        try {
          const arrayBuffer = await blob.arrayBuffer();
          const audioContext = new AudioContext();
          const audioBuffer = await audioContext.decodeAudioData(arrayBuffer);
          const normalized = generateWaveformBars(audioBuffer.getChannelData(0));
          if (!cancelled) setWaveformData(normalized);
          audioContext.close();
        } catch {
          const fallback = Array.from({ length: 48 }, () => Math.round(15 + safeRandom() * 70));
          if (!cancelled) setWaveformData(fallback);
        }

        setIsLoading(false);
      } catch (err: unknown) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : 'Erreur de chargement');
          setIsLoading(false);
          setWaveformData(Array.from({ length: 48 }, () => Math.round(15 + safeRandom() * 70)));
        }
      }
    };

    fetchAudio();

    return () => {
      cancelled = true;
      if (audioBlobUrl) URL.revokeObjectURL(audioBlobUrl);
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps -- cleanup intentionally captures audioBlobUrl from this render cycle
  }, [audioUrl]);

  // Update progress during playback
  const startProgressTracking = useCallback(() => {
    const update = () => {
      const audio = audioRef.current;
      if (audio && !audio.paused) {
        setCurrentTime(audio.currentTime);
        setProgress(audio.duration > 0 ? (audio.currentTime / audio.duration) * 100 : 0);
        animFrameRef.current = requestAnimationFrame(update);
      }
    };
    animFrameRef.current = requestAnimationFrame(update);
  }, []);

  const stopProgressTracking = useCallback(() => {
    if (animFrameRef.current) {
      cancelAnimationFrame(animFrameRef.current);
      animFrameRef.current = null;
    }
  }, []);

  // Audio event handlers
  useEffect(() => {
    const audio = audioRef.current;
    if (!audio) return;

    const onLoadedMetadata = () => setDuration(audio.duration);
    const onPlay = () => { setIsPlaying(true); startProgressTracking(); };
    const onPause = () => { setIsPlaying(false); stopProgressTracking(); };
    const onEnded = () => { setIsPlaying(false); setProgress(0); setCurrentTime(0); stopProgressTracking(); };

    audio.addEventListener('loadedmetadata', onLoadedMetadata);
    audio.addEventListener('play', onPlay);
    audio.addEventListener('pause', onPause);
    audio.addEventListener('ended', onEnded);

    return () => {
      audio.removeEventListener('loadedmetadata', onLoadedMetadata);
      audio.removeEventListener('play', onPlay);
      audio.removeEventListener('pause', onPause);
      audio.removeEventListener('ended', onEnded);
      stopProgressTracking();
    };
  }, [audioBlobUrl, startProgressTracking, stopProgressTracking]);

  const formatTime = (time: number) => {
    if (!Number.isFinite(time) || time < 0) return '0:00';
    const mins = Math.floor(time / 60);
    const secs = Math.floor(time % 60);
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  };

  const handlePlayPause = () => {
    const audio = audioRef.current;
    if (!audio) return;
    if (audio.paused) {
      audio.play().catch(() => setError('Impossible de lire l\'audio'));
    } else {
      audio.pause();
    }
  };

  const handleScrubberChange = (e: ChangeEvent<HTMLInputElement>) => {
    const audio = audioRef.current;
    if (!audio || !Number.isFinite(audio.duration)) return;
    const val = Number.parseFloat(e.target.value);
    const newTime = (val / 100) * audio.duration;
    audio.currentTime = newTime;
    setProgress(val);
    setCurrentTime(newTime);
  };

  const handleWaveformClick = (e: React.MouseEvent<HTMLDivElement>) => {
    const audio = audioRef.current;
    if (!audio || !Number.isFinite(audio.duration)) return;
    const rect = e.currentTarget.getBoundingClientRect();
    const clickX = e.clientX - rect.left;
    const ratio = Math.max(0, Math.min(1, clickX / rect.width));
    const newTime = ratio * audio.duration;
    audio.currentTime = newTime;
    setProgress(ratio * 100);
    setCurrentTime(newTime);
  };

  const handleWaveformKeyDown = (e: React.KeyboardEvent<HTMLDivElement>) => {
    const audio = audioRef.current;
    if (!audio || !Number.isFinite(audio.duration)) return;
    let newTime: number;
    if (e.key === 'ArrowRight' || e.key === 'ArrowUp') {
      e.preventDefault();
      newTime = Math.min(audio.duration, audio.currentTime + 5);
    } else if (e.key === 'ArrowLeft' || e.key === 'ArrowDown') {
      e.preventDefault();
      newTime = Math.max(0, audio.currentTime - 5);
    } else if (e.key === 'Home') {
      e.preventDefault();
      newTime = 0;
    } else if (e.key === 'End') {
      e.preventDefault();
      newTime = audio.duration;
    } else {
      return;
    }
    audio.currentTime = newTime;
    setProgress((newTime / audio.duration) * 100);
    setCurrentTime(newTime);
  };

  const barHeights = waveformData.length > 0
    ? waveformData
    : [30, 45, 60, 40, 25, 35, 55, 70, 80, 50, 40, 65, 90, 75, 45, 30, 40, 55, 75, 60, 45, 35, 50, 60, 70, 85, 95, 65, 40, 30, 20, 15];

  let playButtonClass = 'btn-cta text-white hover:shadow-[0_0_15px_rgba(99,91,255,0.4)]';
  if (error && !audioBlobUrl) {
    playButtonClass = 'bg-[var(--muted)] text-[var(--text-3)] cursor-not-allowed opacity-50';
  } else if (isPlaying) {
    playButtonClass = 'bg-rose-500/10 text-rose-400 border border-rose-500/30 hover:bg-rose-500/20 shadow-[0_0_15px_rgba(239,68,68,0.25)]';
  }

  return (
    <div className="p-4 rounded-xl bg-[var(--surface)] border border-[var(--border)] shadow-md backdrop-blur-md relative overflow-hidden">
      {/* Hidden <audio> element */}
      {audioBlobUrl && <audio ref={audioRef} src={audioBlobUrl} preload="metadata"><track kind="captions" /></audio>}

      {isLoading ? (
        <div className="flex items-center justify-center gap-3 py-4">
          <div className="w-5 h-5 border-2 border-[var(--primary)] border-t-transparent rounded-full animate-spin" />
          <span className="text-[10px] font-mono text-[var(--text-3)]">Chargement de l'audio...</span>
        </div>
      ) : (
        <div className="flex items-center gap-4">
          {/* Play/Pause Button with Glow */}
          <button
            type="button"
            onClick={handlePlayPause}
            disabled={!!error && !audioBlobUrl}
            className={`w-12 h-12 rounded-full flex items-center justify-center border-none transition-all duration-300 cursor-pointer shadow-md flex-shrink-0 ${playButtonClass}`}
          >
            {isPlaying ? (
              <Pause size={18} className="fill-current text-rose-400 animate-pulse" />
            ) : (
              <Play size={18} className="fill-current text-white ml-0.5" />
            )}
          </button>

          {/* Audio Waveform Visualization */}
          <div className="flex-1 flex flex-col gap-2">
            <div
              className="flex items-end justify-between h-8 gap-0.5 cursor-pointer"
              onClick={handleWaveformClick}
              onKeyDown={handleWaveformKeyDown}
              role="slider"
              tabIndex={0}
              aria-label="Waveform"
              aria-valuenow={progress}
              aria-valuemin={0}
              aria-valuemax={100}
            >
              {(() => {
                let idx = 0;
                return barHeights.map((h) => {
                  const barKey = 'wb-bar-' + idx;
                  const percent = (idx / barHeights.length) * 100;
                  idx++;
                  return (
                    <div
                      key={barKey}
                      className={`w-1 rounded-full transition-colors duration-300 ${
                        percent <= progress 
                          ? 'bg-[var(--primary)]' 
                          : 'bg-[var(--border)]'
                      }`}
                      style={{ height: `${h}%` }}
                    />
                  );
                });
              })()}
            </div>

            {/* Progress Slider / Scrubber */}
            <div className="flex items-center gap-3">
              <span className="font-mono text-[9px] text-[var(--text-3)] font-bold min-w-[28px]">
                {formatTime(currentTime)}
              </span>
              <input
                type="range"
                min="0"
                max="100"
                step="0.1"
                value={progress}
                onChange={handleScrubberChange}
                className="flex-1 h-1 bg-[var(--border)] rounded-lg appearance-none cursor-pointer accent-[var(--primary)] outline-none"
                style={{
                  background: `linear-gradient(to right, var(--primary) ${progress}%, var(--border) ${progress}%)`
                }}
              />
              <span className="font-mono text-[9px] text-[var(--text-3)] font-bold min-w-[28px]">
                {formatTime(duration)}
              </span>
            </div>
          </div>
        </div>
      )}

      {error && (
        <div className="mt-2 text-[9px] font-mono text-amber-400 bg-amber-500/5 border border-amber-500/10 rounded-lg px-3 py-1.5 flex items-center gap-1.5">
          <AlertTriangle size={10} />
          <span>Audio indisponible: {error}</span>
        </div>
      )}
    </div>
  );
}

interface DossierClientPanelProps {
  id: string | undefined;
  ticket: Ticket;
  agenceName: string | null;
  operateurName: string | null;
  fieldRefs: ReferenceData | null;
  formData: Record<string, string>;
  setFormData: React.Dispatch<React.SetStateAction<Record<string, string>>>;
  existingPhotos: string[];
  photoPreviews: string[];
  photoFiles: File[];
  photoInputRef: React.RefObject<HTMLInputElement | null>;
  uploadingPhotos: boolean;
  setLightboxIndex: React.Dispatch<React.SetStateAction<number | null>>;
  handlePhotoSelect: (files: FileList | null) => Promise<void>;
  removePhoto: (index: number) => void;
  handleUploadPhotos: () => Promise<void>;
}

function DossierClientPanel({
  id,
  ticket,
  agenceName,
  operateurName,
  fieldRefs,
  formData,
  setFormData,
  existingPhotos,
  photoPreviews,
  photoFiles,
  photoInputRef,
  uploadingPhotos,
  setLightboxIndex,
  handlePhotoSelect,
  removePhoto,
  handleUploadPhotos,
}: Readonly<DossierClientPanelProps>) {
  return (
    <div className="flex flex-col gap-6">
      
      {/* Card 1.1: General Client / Agency Metadata Info */}
      <div className="card-aurora rounded-2xl p-5 space-y-4">
        <div className="flex items-center justify-between border-b border-border pb-3">
          <h3 className="text-xs font-extrabold uppercase tracking-wider text-txt-2 flex items-center gap-2">
            <User size={14} className="text-primary" />
            Dossier Client
          </h3>
          <span className="font-mono text-[8px] bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 px-2 py-0.5 rounded">
            Garantie active
          </span>
        </div>

        <div className="space-y-3">
          <div className="flex flex-col gap-1 p-2 bg-[var(--elevated)] rounded-xl border border-border">
            <span className="font-mono text-[8px] text-txt-3 uppercase">Agence locale affiliée</span>
            <span className="text-xs font-semibold text-txt flex items-center gap-1.5">
              <Award size={12} className="text-teal-400" />
              {ticket.agenceName || agenceName || 'Non affiliée'}
            </span>
          </div>

          <div className="flex flex-col gap-1 p-2 bg-[var(--elevated)] rounded-xl border border-border">
            <span className="font-mono text-[8px] text-txt-3 uppercase">Conseiller en charge</span>
            <span className="text-xs font-semibold text-txt flex items-center gap-1.5">
              <Shield size={12} className="text-indigo-400" />
              {ticket.operateurName || operateurName || 'Non assigné'}
            </span>
          </div>

          <div className="grid grid-cols-2 gap-2">
            <div className="p-2 bg-[var(--elevated)] rounded-xl border border-border flex flex-col gap-0.5">
              <span className="font-mono text-[8px] text-txt-3 uppercase">RGPD Audio</span>
              <span className={`text-[10px] font-bold ${ticket.consentementEnregistrement ? 'text-emerald-400' : 'text-rose-400'}`}>
                {ticket.consentementEnregistrement ? '✓ Accordé' : '✕ Refusé'}
              </span>
            </div>
            <div className="p-2 bg-[var(--elevated)] rounded-xl border border-border flex flex-col gap-0.5">
              <span className="font-mono text-[8px] text-txt-3 uppercase">ID Ticket</span>
              <span className="text-[10px] font-mono text-txt-2 truncate">
                {id?.substring(0, 8)}...
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* Card 1.2: Dynamic Demand Input Form (Fiche Technique / Saisie de la demande) */}
      <div className="card-aurora rounded-2xl overflow-hidden relative shadow-md">
        <div className="absolute top-0 left-0 right-0 h-[2px] bg-gradient-to-r from-amber-400 to-orange-500" />
        <div className="p-5">
          <div className="flex items-center justify-between mb-4 border-b border-border pb-3">
            <h3 className="text-xs font-extrabold uppercase tracking-wider text-txt-2 flex items-center gap-2">
              <FileText size={14} className="text-amber-400" />
              Saisie de la demande
            </h3>
            {(ticket.statut === 'FERME' || ticket.statut === 'RESOLU') && (
              <span className="font-mono text-[9px] px-2 py-0.5 rounded bg-zinc-500/10 text-zinc-400 border border-zinc-500/20 flex items-center gap-1">
                <Lock size={10} />
                Verrouillé
              </span>
            )}
          </div>

          <div className="space-y-3.5">
            {(fieldRefs?.fields || []).filter((fd: FieldDef) => fd.field !== 'transcriptionText').map((fd: FieldDef) => {
              const isRequired = ['typePiece', 'ageVehiculeAns', 'kilometrage', 'categorieClient', 'typeSinistre'].includes(fd.field);
              const isLocked = ticket.statut === 'FERME' || ticket.statut === 'RESOLU';
              return (
                <div key={fd.field} className="space-y-1">
                  <label htmlFor={`detail-field-${fd.field}`} className="font-mono text-[9px] text-txt-2 uppercase tracking-wide flex items-center justify-between">
                    <span>{fd.label}</span>
                    {isRequired && <span className="text-[8px] text-amber-500 font-bold bg-amber-500/5 px-1.5 py-0.5 rounded border border-amber-500/10">Requis</span>}
                  </label>
                  {fd.type === 'enum' && fd.values && fd.values.length > 0 ? (
                    <select 
                      id={`detail-field-${fd.field}`}
                      value={formData[fd.field] || ''} 
                      onChange={e => setFormData(p => ({ ...p, [fd.field]: e.target.value }))} 
                      disabled={isLocked} 
                      className="aurora-select text-[11px] py-2 w-full disabled:opacity-50 font-mono font-medium rounded-xl"
                    >
                      <option value="">Sélectionner une option…</option>
                      {fd.values.map(v => <option key={v} value={v}>{v}</option>)}
                    </select>
                  ) : (
                    <div className="relative">
                      <input 
                        id={`detail-field-${fd.field}`}
                        type={fd.type === 'number' ? 'number' : 'text'}
                        placeholder={fd.type === 'number' ? 'Ex: 5' : 'Saisir une valeur...'}
                        value={formData[fd.field] || ''}
                        onChange={e => setFormData(p => ({ ...p, [fd.field]: e.target.value }))}
                        disabled={isLocked}
                        className="aurora-input text-[11px] py-2 px-3 w-full disabled:opacity-50 rounded-xl"
                      />
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      </div>

      {/* Card 1.3: Photos of the Incident (Upload & Preview Gallery) */}
      <div className="card-aurora rounded-2xl overflow-hidden relative shadow-md">
        <div className="absolute top-0 left-0 right-0 h-[2px] bg-gradient-to-r from-pink-400 to-rose-500" />
        <div className="p-5">
          <div className="flex items-center justify-between mb-4 border-b border-border pb-3">
            <h3 className="text-xs font-extrabold uppercase tracking-wider text-txt-2 flex items-center gap-2">
              <Camera size={14} className="text-pink-400" />
              Preuves Visuelles
            </h3>
            <span className="font-mono text-[9px] text-txt-3">
              {existingPhotos.length} enregistrée(s)
            </span>
          </div>

          {/* Grid of existing database photos */}
          {existingPhotos.length > 0 && (
            <div className="mb-4">
              <span className="font-mono text-[8px] text-txt-3 uppercase block mb-2 tracking-wide">Galerie du dossier :</span>
              <div className="grid grid-cols-4 gap-2">
                {existingPhotos.map((path, i) => {
                  const imgUrl = `${import.meta.env.VITE_API_URL || ''}/api/tickets${path}`;
                  return (
                    <button 
                      key={path} 
                      type="button"
                      className="relative aspect-square rounded-xl bg-[var(--elevated)] border border-border overflow-hidden cursor-pointer group shadow-sm transition-all hover:scale-105"
                      onClick={() => setLightboxIndex(i)}
                    >
                      <img 
                        src={imgUrl} 
                        alt={`Sinistre ${i+1}`} 
                        className="w-full h-full object-cover" 
                        onError={e => { 
                          (e.target as HTMLImageElement).style.display = 'none'; 
                          (e.target as HTMLImageElement).parentElement!.innerHTML = '<span class="text-sm flex items-center justify-center w-full h-full">🖼️</span>'; 
                        }} 
                      />
                      <span className="absolute bottom-1 right-1 font-mono text-[7px] bg-black/60 text-white px-1.5 py-0.5 rounded-md font-bold">
                        #{i + 1}
                      </span>
                      <div className="absolute inset-0 bg-black/30 opacity-0 group-hover:opacity-100 transition-all flex items-center justify-center">
                        <span className="text-white text-[10px] bg-black/50 p-1.5 rounded-full">🔍</span>
                      </div>
                    </button>
                  );
                })}
              </div>
            </div>
          )}

          {/* Custom Animated Drag & Drop Zone */}
          {ticket.statut !== 'FERME' && ticket.statut !== 'RESOLU' && (
            <label
              htmlFor="ticket-photo-upload"
              className="border-2 border-dashed border-border hover:border-pink-500/40 rounded-xl p-5 text-center cursor-pointer hover:bg-pink-500/[0.01] transition-all relative group block"
              onDragOver={e => { e.preventDefault(); e.currentTarget.classList.add('border-pink-500/50', 'bg-pink-500/5'); }}
              onDragLeave={e => { e.preventDefault(); e.currentTarget.classList.remove('border-pink-500/50', 'bg-pink-500/5'); }}
              onDrop={e => { e.preventDefault(); e.currentTarget.classList.remove('border-pink-500/50', 'bg-pink-500/5'); handlePhotoSelect(e.dataTransfer.files); }}
            >
              <input
                id="ticket-photo-upload"
                ref={photoInputRef}
                type="file"
                multiple
                accept="image/*"
                className="hidden"
                onChange={e => handlePhotoSelect(e.target.files)}
              />
              <div className="text-2xl mb-1.5 transition-transform duration-300 group-hover:scale-110">📸</div>
              <div className="text-[11px] text-txt-2 font-medium">Déposer des fichiers ou cliquer pour explorer</div>
              <div className="font-mono text-[8px] text-txt-3 mt-1 uppercase tracking-wide">JPEG, PNG, WebP · max 10MB</div>
            </label>
          )}

          {/* Local uploading file previews */}
          {photoPreviews.length > 0 && (
            <div className="mt-4 p-3 bg-[var(--elevated)] rounded-xl border border-border space-y-3">
              <span className="font-mono text-[8px] text-txt-3 uppercase block tracking-wide">Fichiers en attente d'envoi :</span>
              <div className="flex flex-wrap gap-2">
                {photoPreviews.map((src, i) => {
                  const file = photoFiles[i];
                  const key = file ? `${file.name}-${file.size}` : `upload-preview-${i}`;
                  return (
                    <div key={key} className="relative group rounded-lg overflow-hidden border border-border shadow">
                      <img src={src} alt={`Upload preview ${i + 1}`} className="w-12 h-12 object-cover" />
                      <button
                        type="button"
                        onClick={(e) => { e.stopPropagation(); removePhoto(i); }}
                        className="absolute inset-0 bg-black/40 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer text-white text-[10px]"
                      >
                        <Trash2 size={10} className="text-rose-400" />
                      </button>
                    </div>
                  );
                })}
              </div>
              <button
                type="button"
                onClick={handleUploadPhotos}
                disabled={uploadingPhotos}
                className="btn-cta h-8 px-4 rounded-xl text-[10px] font-mono text-white cursor-pointer disabled:opacity-50 w-full flex items-center justify-center gap-1.5"
              >
                {uploadingPhotos ? (
                  <>⏳ Envoi en cours...</>
                ) : (
                  <span>📤 Sauvegarder les {photoFiles.length} photos</span>
                )}
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

const getDecisionSourceLabel = (source: string): string => {
  if (source === 'AI') return '🤖 Algorithmique IA';
  if (source === 'RULES') return '📏 Moteur de règles';
  if (source === 'HYBRID') return '🔄 Hybride (Règles + IA)';
  return '⚡ Disjoncteur Fallback';
};

function useTicketDetailsState() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';

  const [ticket, setTicket] = useState<Ticket | null>(null);
  const [anonymizing, setAnonymizing] = useState(false);
  const [decision, setDecision] = useState<DecisionResult | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [analyzing, setAnalyzing] = useState(false);
  const [accepting, setAccepting] = useState(false);
  const [rejecting, setRejecting] = useState(false);
  const [downloading, setDownloading] = useState(false);

  // ── Motif opérateur ──
  const [motif, setMotif] = useState('');

  // ── Formulaire de saisie dynamique ──
  const [formData, setFormData] = useState<Record<string, string>>({});
  const [fieldRefs, setFieldRefs] = useState<ReferenceData | null>(null);
  const pieceReference = formData['pieceReference'] || '';

  // ── Photos sinistre ──
  const [photoFiles, setPhotoFiles] = useState<File[]>([]);
  const [photoPreviews, setPhotoPreviews] = useState<string[]>([]);
  const [uploadingPhotos, setUploadingPhotos] = useState(false);
  const [existingPhotos, setExistingPhotos] = useState<string[]>([]);
  const photoInputRef = useRef<HTMLInputElement>(null);

  // ── Résolution UUID → Nom ──
  const [agenceName, setAgenceName] = useState<string | null>(null);
  const [operateurName, setOperateurName] = useState<string | null>(null);

  // Lightbox State
  const [lightboxIndex, setLightboxIndex] = useState<number | null>(null);

  // Parse existing photos from ticket
  useEffect(() => {
    if (ticket?.photosPaths) {
      try { setExistingPhotos(JSON.parse(ticket.photosPaths)); } catch { setExistingPhotos([]); }
    }
  }, [ticket?.photosPaths]);

  const handlePhotoSelect = async (files: FileList | null) => {
    if (!files) return;
    const validFiles = Array.from(files).filter(
      f => f.type.startsWith('image/') && f.size <= 10 * 1024 * 1024
    );
    if (validFiles.length < files.length) {
      toast.error('Certains fichiers ignorés (non-image ou >10MB)');
    }
    setPhotoFiles(prev => [...prev, ...validFiles]);
    const previews = await Promise.all(validFiles.map(readFileAsDataURL));
    setPhotoPreviews(prev => [...prev, ...previews]);
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
    } catch {
      toast.error('Erreur upload photos');
    } finally {
      setUploadingPhotos(false);
    }
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
      } catch {
        /* No decision yet for this ticket — not an error */
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Ticket introuvable');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => { fetchTicket(); }, [fetchTicket]);

  // ── RGPD — Anonymisation (droit à l'oubli), réservée à l'ADMIN ──
  const handleAnonymize = async () => {
    if (!id || !isAdmin) return;
    const ok = window.confirm(
      "Anonymiser ce ticket (RGPD) ?\n\nCette action est IRRÉVERSIBLE : les données personnelles du client, "
      + "l'enregistrement audio, la transcription, la décision et les courriers seront définitivement effacés."
    );
    if (!ok) return;
    setAnonymizing(true);
    try {
      const updated = await ticketService.anonymize(id);
      setTicket(updated);
      toast.success('Ticket anonymisé — effacement RGPD en cours sur tous les services');
    } catch {
      toast.error("Échec de l'anonymisation");
    } finally {
      setAnonymizing(false);
    }
  };

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

  const handleAnalyze = async (forceReanalyze = false) => {
    if (!id) return;

    const requiredFields = ['typePiece', 'ageVehiculeAns', 'kilometrage', 'categorieClient', 'typeSinistre'];
    const missingFields = validateMissingFields(formData, requiredFields, fieldRefs);
    if (missingFields.length > 0) {
      toast.error(`Champs obligatoires manquants : ${missingFields.join(', ')}`);
      return;
    }

    setAnalyzing(true);
    try {
      if (forceReanalyze && decision) {
        try { await decisionService.deleteByTicket(id); } catch { /* ignore */ }
        setDecision(null);
      }
      const payload = buildAnalyzePayload(formData, fieldRefs, id, pieceReference, ticket?.transcription);
      const result = await decisionService.analyze(payload);
      setDecision(result);
      const emoji = resolveDecisionEmoji(result.decision);
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

  return {
    id,
    navigate,
    ticket,
    decision,
    loading,
    error,
    analyzing,
    accepting,
    rejecting,
    downloading,
    motif,
    formData,
    fieldRefs,
    photoFiles,
    photoPreviews,
    uploadingPhotos,
    existingPhotos,
    photoInputRef,
    agenceName,
    operateurName,
    lightboxIndex,
    setMotif,
    setFormData,
    setLightboxIndex,
    handlePhotoSelect,
    removePhoto,
    handleUploadPhotos,
    fetchTicket,
    handleAnalyze,
    handleAccept,
    handleReject,
    handleDownloadPDF,
    handleChangeStatut,
    isAdmin,
    anonymizing,
    handleAnonymize
  };
}

export default function TicketDetailPage() {
  const {
    id,
    navigate,
    ticket,
    decision,
    loading,
    error,
    analyzing,
    accepting,
    rejecting,
    downloading,
    motif,
    formData,
    fieldRefs,
    photoFiles,
    photoPreviews,
    uploadingPhotos,
    existingPhotos,
    photoInputRef,
    agenceName,
    operateurName,
    lightboxIndex,
    setMotif,
    setFormData,
    setLightboxIndex,
    handlePhotoSelect,
    removePhoto,
    handleUploadPhotos,
    fetchTicket,
    handleAnalyze,
    handleAccept,
    handleReject,
    handleDownloadPDF,
    handleChangeStatut,
    isAdmin,
    anonymizing,
    handleAnonymize
  } = useTicketDetailsState();

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && lightboxIndex !== null) {
        setLightboxIndex(null);
      }
    };
    globalThis.addEventListener('keydown', handleKeyDown);
    return () => globalThis.removeEventListener('keydown', handleKeyDown);
  }, [lightboxIndex, setLightboxIndex]);

  if (loading) {
    return (
      <div className="space-y-4 max-w-[1600px] mx-auto w-full px-2 py-4">
        <CardSkeleton rows={4} />
        <div className="grid grid-cols-1 lg:grid-cols-[340px_1fr_360px] gap-4">
          <CardSkeleton rows={8} />
          <CardSkeleton rows={12} />
          <CardSkeleton rows={6} />
        </div>
      </div>
    );
  }

  if (error || !ticket) return <ErrorState message={error || 'Ticket introuvable'} onRetry={fetchTicket} />;

  const cs = getCanalBadge(ticket.canal);
  const keywords = ticket.transcriptionKeywords?.split(',').map(k => k.trim()).filter(Boolean) || [];

  return (
    <div className="flex flex-col gap-6 max-w-[1600px] mx-auto w-full px-1 py-2 relative">
      <style>{`
        @keyframes shine {
          0% { background-position: -200% 0; }
          100% { background-position: 200% 0; }
        }
        .btn-premium-accept {
          background: linear-gradient(135deg, #00C48C 0%, #00D4AA 100%);
          position: relative;
          overflow: hidden;
          transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
        }
        .btn-premium-accept:hover {
          box-shadow: 0 0 20px rgba(0, 212, 170, 0.4);
          transform: translateY(-1px);
        }
        .btn-premium-accept::after {
          content: '';
          position: absolute;
          top: 0; left: -150%; width: 50%; height: 100%;
          background: linear-gradient(to right, rgba(255,255,255,0) 0%, rgba(255,255,255,0.2) 50%, rgba(255,255,255,0) 100%);
          transform: skewX(-25deg);
        }
        .btn-premium-accept:hover::after {
          animation: shine 1.2s infinite;
        }

        .btn-premium-reject {
          background: linear-gradient(135deg, #FF4757 0%, #FF6B81 100%);
          position: relative;
          overflow: hidden;
          transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
        }
        .btn-premium-reject:hover {
          box-shadow: 0 0 20px rgba(255, 71, 87, 0.4);
          transform: translateY(-1px);
        }
        
        .pulse-accent {
          box-shadow: 0 0 0 0 rgba(99, 91, 255, 0.5);
          animation: accentPulse 2s infinite;
        }
        @keyframes accentPulse {
          0% { box-shadow: 0 0 0 0 rgba(99, 91, 255, 0.4); }
          70% { box-shadow: 0 0 0 6px rgba(99, 91, 255, 0); }
          100% { box-shadow: 0 0 0 0 rgba(99, 91, 255, 0); }
        }
        .custom-scrollbar::-webkit-scrollbar {
          width: 5px;
          height: 5px;
        }
        .custom-scrollbar::-webkit-scrollbar-track {
          background: transparent;
        }
        .custom-scrollbar::-webkit-scrollbar-thumb {
          background: var(--muted);
          border-radius: 99px;
        }
        .custom-scrollbar::-webkit-scrollbar-thumb:hover {
          background: var(--text-3);
        }
      `}</style>

      {/* Breadcrumb & Navigation Actions */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2.5 text-xs">
          <button 
            onClick={() => navigate('/')} 
            className="flex items-center gap-1.5 text-txt-3 hover:text-txt cursor-pointer transition-colors font-medium bg-[var(--surface)] border border-border px-2.5 py-1.5 rounded-lg shadow-sm"
          >
            <ArrowLeft size={13} />
            Retour
          </button>
          <span className="text-txt-3 font-mono">/</span>
          <span className="font-mono text-txt-2 bg-indigo-500/5 text-indigo-400 border border-indigo-500/10 px-2.5 py-1 rounded-lg">
            {ticket.numero}
          </span>
        </div>

        {/* Dynamic header badge indicators */}
        <div className="flex items-center gap-2">
          {isAdmin && ticket.clientNom !== '***ANONYMISÉ***' && (
            <button
              type="button"
              onClick={handleAnonymize}
              disabled={anonymizing}
              title="Anonymiser ce ticket (RGPD — droit à l'oubli)"
              className="flex items-center gap-1.5 text-[10px] font-mono font-bold uppercase tracking-wider text-rose-400 bg-rose-500/10 border border-rose-500/20 hover:bg-rose-500/20 px-2.5 py-1 rounded-full cursor-pointer transition-all disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <ShieldAlert size={12} /> {anonymizing ? 'Anonymisation…' : 'Anonymiser (RGPD)'}
            </button>
          )}
          {ticket.priorite === 1 && (
            <span className="flex items-center gap-1 text-[10px] font-mono font-extrabold uppercase tracking-wider text-rose-400 bg-rose-500/10 border border-rose-500/20 px-2.5 py-1 rounded-full shadow-[0_0_10px_rgba(239,68,68,0.1)]">
              🔥 Urgent
            </span>
          )}
          <span className={`text-[10px] font-mono font-bold uppercase tracking-wider border px-2.5 py-1 rounded-full ${cs.c}`}>
            {cs.l}
          </span>
          <span className={`text-[10px] font-mono font-bold uppercase tracking-wider border px-2.5 py-1 rounded-full ${getStatutBadge(ticket.statut).c}`}>
            {getStatutBadge(ticket.statut).l}
          </span>
        </div>
      </div>

      {/* Modern Premium Command Header Banner */}
      <div className="card-aurora rounded-2xl p-6 relative overflow-hidden bg-gradient-to-r from-[var(--surface)] to-[var(--elevated)] shadow-lg">
        <div className="absolute top-0 left-0 right-0 h-[3px] bg-gradient-to-r from-indigo-500 via-violet-400 to-teal-400" />
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-5 relative z-10">
          <div className="space-y-1">
            <div className="flex items-center gap-2">
              <div className="font-mono text-[10px] text-txt-3 uppercase tracking-wider">
                Déclaration de sinistre · {new Date(ticket.createdAt.endsWith('Z') ? ticket.createdAt : ticket.createdAt + 'Z').toLocaleString('fr-FR')}
              </div>
            </div>
            <h1 className="text-2xl font-extrabold tracking-tight text-gradient-ai flex items-center gap-2">
              {ticket.clientNom}
              <span className="text-[10.5px] font-mono font-semibold tracking-wider text-primary bg-primary-soft px-2.5 py-0.5 rounded-full border border-primary/20 shadow-sm ml-2">
                CLIENT REF
              </span>
            </h1>
            <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-txt-2">
              {ticket.clientTel && (
                <a href={`tel:${ticket.clientTel}`} className="hover:text-primary transition-colors flex items-center gap-1.5 font-mono">
                  <Phone size={12} className="text-txt-3" />
                  {ticket.clientTel}
                </a>
              )}
              {ticket.clientEmail && (
                <a href={`mailto:${ticket.clientEmail}`} className="hover:text-primary transition-colors flex items-center gap-1.5 font-mono">
                  <Mail size={12} className="text-txt-3" />
                  {ticket.clientEmail}
                </a>
              )}
            </div>
          </div>

          {/* Action Zone - Status Modifier */}
          <div className="flex items-center gap-3 flex-shrink-0 bg-[var(--surface)] border border-border p-2 rounded-xl">
            <span className="font-mono text-[9px] text-txt-3 uppercase tracking-wider pl-2">Modifier Statut :</span>
            <select
              value={ticket.statut}
              onChange={(e) => handleChangeStatut(e.target.value)}
              disabled={ticket.statut === 'FERME'}
              className="aurora-select text-[11px] py-1.5 w-36 disabled:opacity-50 font-mono font-bold cursor-pointer"
            >
              <option value={ticket.statut}>
                {getStatutBadge(ticket.statut).l}
              </option>
              {ticket.statut === 'NOUVEAU' && <option value="EN_COURS">→ En cours</option>}
              {ticket.statut === 'NOUVEAU' && <option value="FERME">→ Fermé</option>}
              {ticket.statut === 'EN_COURS' && <option value="RESOLU">→ Résolu</option>}
              {ticket.statut === 'EN_COURS' && <option value="FERME">→ Fermé</option>}
              {ticket.statut === 'RESOLU' && <option value="FERME">→ Fermé</option>}
            </select>
          </div>
        </div>

        {ticket.description && (
          <div className="mt-5 p-4 bg-[var(--elevated)]/60 border border-border/80 rounded-xl text-xs text-txt-2 leading-relaxed shadow-inner">
            <span className="font-mono text-[9px] text-txt-3 uppercase tracking-wider block mb-1">📝 Objet initial de la réclamation</span>
            {ticket.description}
          </div>
        )}
      </div>

      {/* Main 3-Column Command Grid Layout */}
      <div className="grid grid-cols-1 lg:grid-cols-2 xl:grid-cols-[330px_1fr_360px] gap-6 items-start">
        
        {/* ========================================================================= */}
        {/* COLUMN 1: CLIENT & VEHICLE PROFILE, DEMAND FORM, AND PHOTOS               */}
        {/* ========================================================================= */}
        <DossierClientPanel
          id={id}
          ticket={ticket}
          agenceName={agenceName}
          operateurName={operateurName}
          fieldRefs={fieldRefs}
          formData={formData}
          setFormData={setFormData}
          existingPhotos={existingPhotos}
          photoPreviews={photoPreviews}
          photoFiles={photoFiles}
          photoInputRef={photoInputRef}
          uploadingPhotos={uploadingPhotos}
          setLightboxIndex={setLightboxIndex}
          handlePhotoSelect={handlePhotoSelect}
          removePhoto={removePhoto}
          handleUploadPhotos={handleUploadPhotos}
        />

        {/* ========================================================================= */}
        {/* COLUMN 2: CORE INCIDENT COMMUNICATIONS & ACTION HISTORY TIMELINE          */}
        {/* ========================================================================= */}
        <div className="flex flex-col gap-6">

          {/* Section 2.1: Communication Stream - Responsive dynamic layout by channel */}
          <div className="space-y-6">
            
            {/* CANAL EMAIL DETAILED CARD */}
            {ticket.canal === 'EMAIL' && (
              <div className="card-aurora rounded-2xl p-5 relative overflow-hidden">
                <div className="absolute top-0 left-0 right-0 h-[2px] bg-gradient-to-r from-blue-400 to-indigo-500" />
                <div className="flex items-center justify-between mb-4 border-b border-border pb-3">
                  <div className="flex items-center gap-2">
                    <div className="p-1.5 rounded-lg bg-blue-500/10 text-blue-400">
                      <Mail size={16} />
                    </div>
                    <h3 className="text-xs font-extrabold uppercase tracking-wider text-txt-2">📧 Contenu du message email</h3>
                  </div>
                  <span className="font-mono text-[9px] px-2 py-0.5 rounded bg-blue-500/10 text-blue-400 border border-blue-500/20">
                    IMAP Parsing
                  </span>
                </div>
                <div className="p-4 bg-[var(--elevated)] border border-border rounded-xl shadow-inner max-h-[400px] overflow-y-auto custom-scrollbar">
                  <div className="text-xs text-txt-2 leading-relaxed whitespace-pre-line font-serif">
                    {ticket.description || 'Aucun contenu textuel dans le courriel.'}
                  </div>
                </div>
                {ticket.clientEmail && (
                  <div className="mt-3 flex items-center justify-between text-[9px] font-mono text-txt-3 border-t border-border pt-3">
                    <span>Expéditeur : {ticket.clientEmail}</span>
                    <span>Date : {new Date(ticket.createdAt).toLocaleString('fr-FR')}</span>
                  </div>
                )}
              </div>
            )}

            {/* CANAL WEB DETAILED CARD */}
            {ticket.canal === 'WEB' && (
              <div className="card-aurora rounded-2xl p-5 relative overflow-hidden">
                <div className="absolute top-0 left-0 right-0 h-[2px] bg-gradient-to-r from-emerald-400 to-teal-500" />
                <div className="flex items-center justify-between mb-4 border-b border-border pb-3">
                  <div className="flex items-center gap-2">
                    <div className="p-1.5 rounded-lg bg-emerald-500/10 text-emerald-400">
                      <Sparkles size={16} />
                    </div>
                    <h3 className="text-xs font-extrabold uppercase tracking-wider text-txt-2">🌐 Déclaration Formulaire Web</h3>
                  </div>
                  <span className="font-mono text-[9px] px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                    REST API
                  </span>
                </div>
                <div className="p-4 bg-[var(--elevated)] border border-border rounded-xl shadow-inner">
                  <div className="text-xs text-txt-2 leading-relaxed whitespace-pre-line">
                    {ticket.description || 'Aucune observation saisie par l\'internaute.'}
                  </div>
                </div>
                <div className="mt-3 border-t border-border pt-3 grid grid-cols-2 gap-4 text-[9px] font-mono text-txt-3">
                  <div>Mail de contact : {ticket.clientEmail || 'Non spécifié'}</div>
                  <div className="text-right">Tel de contact : {ticket.clientTel || 'Non spécifié'}</div>
                </div>
              </div>
            )}

            {/* CANAL CALL - Twilio Audio & Live Transcription & Whisper */}
            {ticket.canal === 'CALL' && (
              <>
                {/* Audio Twilio Card */}
                <div className="card-aurora rounded-2xl p-5 relative overflow-hidden">
                  <div className="absolute top-0 left-0 right-0 h-[2px] bg-gradient-to-r from-emerald-400 to-indigo-500" />
                  <div className="flex items-center justify-between mb-4 border-b border-border pb-3">
                    <div className="flex items-center gap-2">
                      <div className="p-1.5 rounded-lg bg-emerald-500/10 text-emerald-400">
                        <Phone size={16} />
                      </div>
                      <h3 className="text-xs font-extrabold uppercase tracking-wider text-txt-2">☎ Enregistrement Audio Twilio</h3>
                    </div>
                    <span className="font-mono text-[9px] px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                      SIP Stream
                    </span>
                  </div>

                  {ticket.audioPath ? (
                    <div className="space-y-4">
                      <div className="p-3 bg-[var(--elevated)] border border-border rounded-xl">
                        <div className="flex items-center justify-between mb-3">
                          <div className="flex items-center gap-2">
                            <span className="relative flex h-2 w-2">
                              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                              <span className="relative inline-flex rounded-full h-2 w-2 bg-emerald-500"></span>
                            </span>
                            <span className="text-[10px] text-emerald-400 font-mono uppercase font-bold">Fichier d'archive disponible</span>
                          </div>
                          <div className="font-mono text-[9px] text-txt-3 bg-[var(--surface)] border border-border px-2 py-0.5 rounded-md truncate max-w-[180px]">
                            {ticket.audioPath.split('/').pop()}
                          </div>
                        </div>

                        {/* Premium custom scrubber audio player */}
                        <PremiumAudioPlayer ticketId={ticket.id} filename={ticket.audioPath ?? undefined} />
                      </div>

                      {/* Processing status visual pipeline */}
                      <div className="flex items-center justify-between bg-[var(--elevated)]/40 p-3 rounded-xl border border-border">
                        <div className="flex items-center gap-1.5">
                          <div className="w-5 h-5 rounded-full bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-[9px] text-emerald-400 font-bold">✓</div>
                          <span className="font-mono text-[8px] uppercase text-emerald-400 tracking-wider">Audio Capturé</span>
                        </div>
                        <div className="flex-1 h-px bg-border mx-3" />
                        <div className="flex items-center gap-1.5">
                          <div className={`w-5 h-5 rounded-full flex items-center justify-center text-[9px] font-bold ${ticket.transcription ? 'bg-emerald-500/10 border border-emerald-500/20 text-emerald-400' : 'bg-amber-500/10 border border-amber-500/20 text-amber-400'}`}>
                            {ticket.transcription ? '✓' : '⏳'}
                          </div>
                          <span className={`font-mono text-[8px] uppercase tracking-wider ${ticket.transcription ? 'text-emerald-400' : 'text-amber-400'}`}>
                            Whisper Transcrit
                          </span>
                        </div>
                        <div className="flex-1 h-px bg-border mx-3" />
                        <div className="flex items-center gap-1.5">
                          <div className={`w-5 h-5 rounded-full flex items-center justify-center text-[9px] font-bold ${ticket.transcription ? 'bg-emerald-500/10 border border-emerald-500/20 text-emerald-400' : 'bg-[var(--muted)] border border-border text-txt-3'}`}>
                            {ticket.transcription ? '✓' : '○'}
                          </div>
                          <span className={`font-mono text-[8px] uppercase tracking-wider ${ticket.transcription ? 'text-emerald-400' : 'text-txt-3'}`}>
                            NLP Évalué
                          </span>
                        </div>
                      </div>
                    </div>
                  ) : (
                    <div className="text-center py-8 border border-dashed border-border rounded-xl bg-[var(--elevated)]/20">
                      <div className="text-3xl mb-2 animate-bounce">📞</div>
                      <div className="text-xs text-txt-2 font-bold uppercase tracking-wider">Enregistrement automatique en cours</div>
                      <p className="text-[10px] text-txt-3 mt-1.5 max-w-[280px] mx-auto leading-relaxed">
                        L'appel via le numéro vert AutoAssist est actif. L'enregistrement audio et l'analyse NLP Whisper démarreront automatiquement à la fin de la communication.
                      </p>
                      <div className="mt-4 inline-flex items-center gap-2 bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 px-3 py-1 rounded-full text-[9px] font-mono">
                        <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />{' '}
                        Canal Twilio Connecté
                      </div>
                    </div>
                  )}
                </div>

                {/* Real-time Streaming STT Module */}
                <LiveTranscription ticketId={id!} onTranscriptionUpdate={() => fetchTicket()} />

                {/* Segmented Whisper Transcription Card */}
                <div className="card-aurora rounded-2xl p-5 relative overflow-hidden">
                  <div className="flex items-center justify-between mb-4 border-b border-border pb-3">
                    <h3 className="text-xs font-extrabold uppercase tracking-wider text-txt-2 flex items-center gap-2">
                      <FileText size={14} className="text-indigo-400" />
                      Transcription de la conversation (NLP)
                    </h3>
                    {ticket.transcription && (
                      <span className="font-mono text-[9px] px-2.5 py-0.5 rounded-full bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 font-bold">
                        {ticket.transcription.length} caractères
                      </span>
                    )}
                  </div>

                  {ticket.transcription ? (
                    <div className="space-y-4">
                      {/* Interactive block list of text speech fragments */}
                      <TranscriptionViewer transcription={ticket.transcription} keywords={keywords} />

                      {/* Extracted NLP Keywords list */}
                      {keywords.length > 0 && (
                        <div className="bg-[var(--elevated)]/30 p-3 rounded-xl border border-border space-y-2">
                          <div className="font-mono text-[8px] text-txt-3 uppercase tracking-wider font-bold">Mots-clés sémantiques identifiés :</div>
                          <div className="flex flex-wrap gap-1.5">
                            {keywords.map((kw) => (
                              <span key={kw} className="font-mono text-[9px] px-2.5 py-0.5 rounded-full bg-indigo-500/10 text-indigo-400 border border-indigo-500/25 font-bold shadow-sm">
                                {kw}
                              </span>
                            ))}
                          </div>
                        </div>
                      )}

                      {/* Operator observations field */}
                      <div className="border-t border-border pt-4 mt-2">
                        <label htmlFor="ticket-operator-notes" className="font-mono text-[9px] text-txt-3 uppercase tracking-wider block mb-2 font-bold">📋 Remarques et Annotations Opérateur</label>
                        <textarea 
                          id="ticket-operator-notes" 
                          className="aurora-input text-[11px] w-full h-[65px] resize-none rounded-xl py-2 px-3 focus:border-indigo-500/60" 
                          placeholder="Saisissez ici des notes complémentaires sur l'appel ou l'entretien..." 
                          defaultValue="" 
                        />
                      </div>
                    </div>
                  ) : (
                    <div className="text-center py-10 border border-dashed border-border rounded-xl bg-[var(--elevated)]/20">
                      <div className="text-3xl mb-2 filter drop-shadow-sm">📄</div>
                      <div className="text-xs text-txt-3 max-w-[220px] mx-auto leading-relaxed">
                        {ticket.audioPath ? 'Analyse audio Whisper en cours. Les segments de texte apparaîtront sous peu...' : 'En attente de la clôture de la ligne téléphonique pour générer l\'audit textuel.'}
                      </div>
                      {ticket.audioPath && (
                        <div className="mt-4 flex items-center justify-center gap-2 text-indigo-400 text-[10px] font-mono bg-indigo-500/5 border border-indigo-500/10 px-3 py-1 rounded-full w-fit mx-auto">
                          <RefreshCw size={11} className="animate-spin" />
                          Synthèse Whisper en cours...
                        </div>
                      )}
                    </div>
                  )}
                </div>
              </>
            )}
          </div>

          {/* Section 2.2: Ticket Event History Timeline (Actions) */}
          {ticket.actions && ticket.actions.length > 0 && (
            <div className="card-aurora rounded-2xl p-5 relative overflow-hidden shadow-md">
              <div className="absolute top-0 left-0 right-0 h-[2px] bg-gradient-to-r from-violet-400 to-indigo-600" />
              <div className="flex items-center justify-between mb-4 border-b border-border pb-3">
                <h3 className="text-xs font-extrabold uppercase tracking-wider text-txt-2 flex items-center gap-2">
                  <Clock size={14} className="text-violet-400" />
                  Historique d'instruction du dossier
                </h3>
                <span className="font-mono text-[9px] text-txt-3">
                  {ticket.actions.length} événements
                </span>
              </div>

              <div className="flex flex-col pl-1 space-y-4">
                {ticket.actions.map((action, i) => (
                  <div key={action.id} className="flex gap-4 group">
                    <div className="flex flex-col items-center flex-shrink-0 relative">
                      <div className="w-2.5 h-2.5 rounded-full bg-indigo-500/80 border-2 border-indigo-300/40 z-10 shadow-[0_0_8px_rgba(99,91,255,0.4)]" />
                      {i < ticket.actions.length - 1 && (
                        <div className="absolute top-2 w-[1.5px] bg-gradient-to-b from-indigo-500/30 to-transparent h-[calc(100%+16px)]" />
                      )}
                    </div>
                    <div className="flex-1 bg-[var(--elevated)]/30 border border-border/80 rounded-xl p-3 shadow-sm hover:bg-[var(--elevated)]/60 transition-colors">
                      <div className="flex items-center justify-between mb-1.5">
                        <div className="text-xs font-semibold text-txt">
                          {action.action === 'STATUT_CHANGE' && action.oldValue && action.newValue ? (
                            <span className="flex items-center gap-1.5">
                              Changement Statut:{' '}
                              <span className="font-mono text-[9px] bg-zinc-500/10 text-zinc-400 px-1.5 py-0.5 rounded border border-zinc-500/20">{action.oldValue}</span>{' '}
                              <span className="text-txt-3">➔</span>{' '}
                              <span className="font-mono text-[9px] bg-emerald-500/10 text-emerald-400 px-1.5 py-0.5 rounded border border-emerald-500/20">{action.newValue}</span>
                            </span>
                          ) : (
                            action.action
                          )}
                        </div>
                        <span className="font-mono text-[9px] text-txt-3 bg-[var(--muted)] border border-border px-2 py-0.5 rounded-md">
                          {action.userName}
                        </span>
                      </div>
                      
                      {action.notes && (
                        <div className="text-[11px] text-txt-2 leading-relaxed bg-[var(--elevated)] border border-border/50 p-2 rounded-lg font-medium shadow-inner mb-1.5">
                          {action.notes}
                        </div>
                      )}
                      
                      <div className="font-mono text-[8px] text-txt-3 text-right">
                        {new Date(action.createdAt.endsWith('Z') ? action.createdAt : action.createdAt + 'Z').toLocaleString('fr-FR')}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* ========================================================================= */}
        {/* COLUMN 3: AI DECISION ENGINE, TRUST RADIAL GAUGE & ACTIONS                */}
        {/* ========================================================================= */}
        <div className="flex flex-col gap-6 lg:col-span-2 xl:col-span-1">
          
          {/* Card 3.1: AI Decision Assistant & Trust Metrics Gauge */}
          <div className="card-aurora rounded-2xl overflow-hidden relative shadow-lg bg-gradient-to-br from-[var(--surface)] to-[var(--elevated)]">
            <div className="absolute top-0 left-0 right-0 h-[2.5px] bg-gradient-to-r from-teal-400 to-indigo-500" />
            <div className="p-5">
              <div className="flex items-center justify-between border-b border-border pb-3 mb-5">
                <div className="flex items-center gap-2">
                  <div className="p-1.5 rounded-lg bg-teal-500/10 text-teal-400">
                    <Brain size={16} />
                  </div>
                  <h3 className="text-xs font-extrabold uppercase tracking-wider text-txt-2">🤖 Assistant de Décision IA</h3>
                </div>
                <button
                  onClick={() => handleAnalyze(!!decision)}
                  disabled={analyzing || ticket.statut === 'FERME'}
                  className={`h-8 px-4 rounded-xl text-[10px] font-mono font-bold cursor-pointer disabled:opacity-50 flex items-center gap-1.5 transition-all ${
                    decision
                      ? 'border border-border text-txt-2 hover:bg-[var(--elevated)] hover:border-border-visible'
                      : 'btn-cta text-white shadow-md shadow-indigo-500/20'
                  }`}
                >
                  {(() => {
                    if (analyzing) {
                      return (
                        <>
                          <RefreshCw size={11} className="animate-spin" />
                          Calcul...
                        </>
                      );
                    }
                    if (decision) {
                      return <>🔄 Ré-évaluer</>;
                    }
                    return <>🚀 Analyser</>;
                  })()}
                </button>
              </div>

              {decision ? (
                <div className="space-y-5">
                  
                  {/* Score circle with numeric value in center */}
                  <div className="flex gap-4 items-center bg-[var(--elevated)]/60 border border-border p-4 rounded-2xl shadow-inner">
                    <div className="relative flex-shrink-0 flex items-center justify-center w-20 h-20">
                      {/* Outer Pulse effect */}
                      {decision.score >= 70 && (
                        <div className="absolute inset-0 rounded-full bg-teal-500/5 animate-ping" />
                      )}
                      <svg width="80" height="80" viewBox="0 0 80 80" className="relative z-10">
                        {/* Background track */}
                        <circle cx="40" cy="40" r="33" fill="none" stroke="var(--muted)" strokeWidth="6" />
                        {/* Glowing progress arc */}
                        <circle 
                          cx="40" 
                          cy="40" 
                          r="33" 
                          fill="none" 
                          stroke="url(#score-gradient)" 
                          strokeWidth="6"
                          strokeDasharray={`${(decision.score / 100) * 207} 207`}
                          strokeLinecap="round" 
                          transform="rotate(-90 40 40)"
                          className="transition-all duration-1000"
                          style={{ filter: 'drop-shadow(0 0 4px var(--primary-light))' }}
                        />
                        <defs>
                          <linearGradient id="score-gradient" x1="0%" y1="0%" x2="100%" y2="100%">
                            <stop offset="0%" stopColor="#00D4AA" />
                            <stop offset="50%" stopColor="#635BFF" />
                            <stop offset="100%" stopColor="#A78BFA" />
                          </linearGradient>
                        </defs>
                      </svg>
                      {/* Inner Numeric Content */}
                      <div className="absolute inset-0 flex items-center justify-center flex-col">
                        <span className="text-xl font-black text-gradient-ai">{Math.round(decision.score)}%</span>
                        <span className="font-mono text-[7px] text-txt-3 uppercase font-extrabold tracking-wider">Indice</span>
                      </div>
                    </div>

                    <div className="flex-1 space-y-1.5">
                      <DecisionStatusBadge decision={decision.decision} label={getDecisionBadge(decision.decision).l} />
                      <div className="text-[11px] text-txt-2 leading-relaxed font-medium">
                        {decision.motif}
                      </div>
                    </div>
                  </div>

                  {/* List of evaluated decision rules with glowing checklist */}
                  {decision.rulesApplied.length > 0 && (
                    <div className="space-y-2 bg-[var(--elevated)]/20 p-3.5 rounded-2xl border border-border/80 shadow-sm">
                      <div className="font-mono text-[8px] text-txt-3 uppercase tracking-wider font-extrabold">Filtres réglementaires appliqués :</div>
                      <div className="space-y-1.5">
                        {decision.rulesApplied.map((r) => (
                          <div 
                            key={r} 
                            className="flex items-start gap-2.5 p-2 bg-[var(--elevated)]/80 border border-border rounded-xl text-[11px] font-medium transition-colors hover:border-indigo-500/20"
                          >
                            <span className="text-emerald-400 font-extrabold text-xs flex-shrink-0 mt-0.5">✓</span>
                            <span className="text-txt-2 font-mono text-[10px]">{r}</span>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Metadata and Ambiguity Warnings */}
                  <div className="p-3 bg-[var(--elevated)]/30 border border-border rounded-xl space-y-2 text-[10px] font-mono text-txt-3">
                    <div className="flex items-center justify-between border-b border-border/40 pb-1.5">
                      <span>Source d'évaluation</span>
                      <span className="text-txt-2 font-semibold">
                        {getDecisionSourceLabel(decision.source)}
                      </span>
                    </div>
                    <div className="flex items-center justify-between border-b border-border/40 pb-1.5">
                      <span>État de la décision</span>
                      <span className="text-txt-2 font-semibold">{decision.cached ? '📦 Restitution Cache' : '🆕 Nouvelle exécution'}</span>
                    </div>
                    <div className="flex items-center justify-between">
                      <span>Ambiguïté sémantique</span>
                      {decision.ambiguous ? (
                        <span className="text-rose-400 font-bold bg-rose-500/5 px-2 py-0.5 rounded border border-rose-500/10 flex items-center gap-1">
                          <ShieldAlert size={10} /> Vérification requise
                        </span>
                      ) : (
                        <span className="text-emerald-400 font-bold bg-emerald-500/5 px-2 py-0.5 rounded border border-emerald-500/10 flex items-center gap-1">
                          <CheckCircle2 size={10} /> Évaluation fiable
                        </span>
                      )}
                    </div>
                  </div>

                  {/* Interactive operator decision control actions */}
                  {!decision.finalDecision && ticket.statut !== 'FERME' && ticket.statut !== 'RESOLU' && (
                    <div className="space-y-3.5 border-t border-border pt-4 mt-2">
                      <div className="space-y-1.5">
                        <label htmlFor="ticket-decision-motif" className="font-mono text-[9px] text-txt-2 uppercase tracking-wide block font-extrabold">
                          Justification requise (motif) *
                        </label>
                        <textarea
                          id="ticket-decision-motif"
                          className="aurora-input text-[11px] w-full h-[65px] resize-none rounded-xl py-2.5 px-3.5 focus:border-indigo-500/60"
                          placeholder="Saisissez obligatoirement le motif qualifiant pour acter cette décision..."
                          value={motif}
                          onChange={(e) => setMotif(e.target.value)}
                        />
                      </div>
                      
                      <div className="flex gap-3">
                        <button 
                          onClick={handleAccept} 
                          disabled={accepting || !motif.trim()} 
                          className="btn-premium-accept flex-1 h-10 rounded-xl font-mono text-[10px] font-bold text-white flex items-center justify-center gap-1.5 cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
                        >
                          {accepting ? '...' : (
                            <span>✓ Accepter</span>
                          )}
                        </button>
                        <button 
                          onClick={handleReject} 
                          disabled={rejecting || !motif.trim()} 
                          className="btn-premium-reject flex-1 h-10 rounded-xl font-mono text-[10px] font-bold text-white flex items-center justify-center gap-1.5 cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
                        >
                          {rejecting ? '...' : (
                            <span>✕ Rejeter</span>
                          )}
                        </button>
                      </div>
                    </div>
                  )}

                  {/* Locked Final Decision Box */}
                  {decision.finalDecision && (
                    <div className={`p-4 rounded-xl border text-center text-xs font-extrabold shadow-sm bg-[var(--elevated)] ${
                      decision.finalDecision === 'FAVORABLE' 
                        ? 'text-emerald-400 border-emerald-500/25 bg-emerald-500/[0.01]' 
                        : 'text-rose-400 border-rose-500/25 bg-rose-500/[0.01]'
                    }`}>
                      <div className="font-mono text-[8px] text-txt-3 uppercase block mb-1">Arbitrage définitif :</div>
                      <span className="text-sm tracking-wide">
                        {decision.finalDecision === 'FAVORABLE' ? '🟢 FAVORABLE' : '🔴 DEFAVORABLE'}
                      </span>
                      {decision.overrideMotif && (
                        <div className="mt-2 text-[10px] text-txt-2 font-mono font-medium max-w-[280px] mx-auto leading-relaxed border-t border-border/40 pt-2">
                          "{decision.overrideMotif}"
                        </div>
                      )}
                    </div>
                  )}

                  {/* Mail/PDF generator action button */}
                  {decision.finalDecision && (
                    <button 
                      onClick={handleDownloadPDF} 
                      disabled={downloading} 
                      className="w-full h-10 rounded-xl font-mono text-[10px] font-extrabold flex items-center justify-center gap-2 border border-border hover:bg-[var(--elevated)] hover:border-border-visible transition-colors cursor-pointer disabled:opacity-50"
                    >
                      {downloading ? (
                        <>
                          <RefreshCw size={12} className="animate-spin text-txt-3" />
                          Génération Courrier...
                        </>
                      ) : (
                        <>
                          <Download size={13} className="text-txt-3" />
                          <span>Télécharger courrier de réponse (PDF)</span>
                        </>
                      )}
                    </button>
                  )}
                </div>
              ) : (
                <div className="text-center py-12 border border-dashed border-border rounded-xl bg-[var(--elevated)]/20">
                  <div className="text-4xl mb-2.5 filter drop-shadow-md">🤖</div>
                  <div className="text-xs text-txt-2 font-bold uppercase tracking-wider">Aide décisionnelle en attente</div>
                  <p className="text-[10px] text-txt-3 mt-1.5 max-w-[220px] mx-auto leading-relaxed">
                    Les critères requis ne sont pas encore calculés. Veuillez cliquer sur "Analyser" ci-dessus pour lancer les moteurs de conformité.
                  </p>
                </div>
              )}
            </div>
          </div>

          {/* Card 3.2: General Technical Record Details */}
          <div className="card-aurora rounded-2xl p-5 shadow-md">
            <div className="flex items-center justify-between border-b border-border pb-3 mb-4">
              <h3 className="text-xs font-extrabold uppercase tracking-wider text-txt-2 flex items-center gap-2">
                <HardDrive size={14} className="text-indigo-400" />
                Métadonnées Système
              </h3>
              <span className="font-mono text-[8px] bg-zinc-500/10 text-zinc-400 border border-zinc-500/20 px-2 py-0.5 rounded">
                Audit Log
              </span>
            </div>

            <div className="space-y-2.5">
              {[
                { label: 'Identifiant global', value: ticket.id },
                { label: 'Date d\'ouverture', value: new Date(ticket.createdAt.endsWith('Z') ? ticket.createdAt : ticket.createdAt + 'Z').toLocaleString('fr-FR') },
                { label: 'Dernière mise à jour', value: new Date(ticket.updatedAt.endsWith('Z') ? ticket.updatedAt : ticket.updatedAt + 'Z').toLocaleString('fr-FR') },
                { label: 'Canal d\'ingestion', value: ticket.canal },
                { label: 'Accord d\'écoute', value: ticket.consentementEnregistrement ? 'Accord Explicite (RGPD)' : 'Refus d\'archivage' }
              ].map((info) => (
                <div key={info.label} className="flex flex-col gap-1 border-b border-border/40 pb-2.5 last:border-0 last:pb-0">
                  <span className="font-mono text-[8px] text-txt-3 uppercase tracking-wide">{info.label}</span>
                  <span className="font-mono text-[10px] text-txt-2 truncate block" title={info.value}>
                    {info.value}
                  </span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* Full screen dynamic Lightbox component for gallery photos */}
      <AnimatePresence>
        {lightboxIndex !== null && (
          <motion.div 
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 z-50 bg-black/95 flex items-center justify-center p-4 cursor-pointer"
            onClick={(e) => {
              if (e.target === e.currentTarget) {
                setLightboxIndex(null);
              }
            }}
          >
            <button 
              className="absolute top-6 right-6 text-white bg-white/10 hover:bg-white/20 p-2.5 rounded-full border border-white/15 transition-all text-sm font-mono cursor-pointer"
              onClick={() => setLightboxIndex(null)}
            >
              ✕ Fermer
            </button>
            <div 
              className="relative max-w-4xl max-h-[85vh] w-full flex items-center justify-center" 
            >
              {existingPhotos[lightboxIndex] && (
                <motion.img 
                  initial={{ scale: 0.95, y: 10 }}
                  animate={{ scale: 1, y: 0 }}
                  exit={{ scale: 0.95, y: 10 }}
                  src={`${import.meta.env.VITE_API_URL || ''}/api/tickets${existingPhotos[lightboxIndex]}`} 
                  alt={`Agrandissement sinistre ${lightboxIndex + 1}`}
                  className="max-w-full max-h-[80vh] object-contain rounded-2xl shadow-2xl border border-white/10" 
                />
              )}
              <div className="absolute -bottom-8 left-0 right-0 text-center font-mono text-xs text-white/60">
                Photo #{lightboxIndex + 1} sur {existingPhotos.length}
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}


