import { useState, useEffect, useRef } from 'react';
import axios from 'axios';

const GATEWAY = import.meta.env.VITE_API_URL || '';
const MAX_PHOTOS = 5;
const MAX_SIZE_MB = 10;

interface Agence { id: string; nom: string; ville: string; }

/* ─── Theme helpers ─── */
type Theme = 'light' | 'dark';
const getSystemTheme = (): Theme =>
  window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';

export default function PublicFormPage() {
  const [form, setForm] = useState({ nom: '', telephone: '', email: '', description: '', agenceId: '' });
  const [agences, setAgences] = useState<Agence[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [success, setSuccess] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [theme, setTheme] = useState<Theme>(getSystemTheme);
  const [focusedField, setFocusedField] = useState<string | null>(null);
  const formRef = useRef<HTMLFormElement>(null);

  // ── Photos sinistre ──
  const [photoFiles, setPhotoFiles] = useState<File[]>([]);
  const [photoPreviews, setPhotoPreviews] = useState<string[]>([]);
  const photoInputRef = useRef<HTMLInputElement>(null);

  const handlePhotoSelect = (files: FileList | null) => {
    if (!files) return;
    const remaining = MAX_PHOTOS - photoFiles.length;
    const valid = Array.from(files)
      .filter(f => f.type.startsWith('image/') && f.size <= MAX_SIZE_MB * 1024 * 1024)
      .slice(0, remaining);
    if (valid.length < files.length) setError(`Seules les images < ${MAX_SIZE_MB}MB sont acceptées (max ${MAX_PHOTOS}).`);
    setPhotoFiles(prev => [...prev, ...valid]);
    valid.forEach(file => {
      const reader = new FileReader();
      reader.onload = e => setPhotoPreviews(prev => [...prev, e.target?.result as string]);
      reader.readAsDataURL(file);
    });
  };

  const removePhoto = (index: number) => {
    setPhotoFiles(prev => prev.filter((_, i) => i !== index));
    setPhotoPreviews(prev => prev.filter((_, i) => i !== index));
  };

  useEffect(() => {
    axios.get<Agence[]>(`${GATEWAY}/api/agences`).then(r => {
      const data = r.data;
      setAgences(Array.isArray(data) ? data : (data as any).content ?? []);
    }).catch(() => setAgences([]));
  }, []);

  const t = theme === 'dark' ? dk : lt;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!form.nom.trim() || !form.description.trim() || !form.agenceId) {
      setError('Veuillez remplir tous les champs obligatoires.');
      return;
    }
    setSubmitting(true);
    try {
      const res = await axios.post(`${GATEWAY}/api/channels/web`, {
        nom: form.nom.trim(),
        telephone: form.telephone.trim() || null,
        email: form.email.trim() || null,
        description: form.description.trim(),
        agenceId: form.agenceId,
        priorite: 3,
      });

      // Upload photos if any
      if (photoFiles.length > 0 && res.data.id) {
        const formData = new FormData();
        photoFiles.forEach(f => formData.append('files', f));
        await axios.post(`${GATEWAY}/api/tickets/${res.data.id}/photos`, formData, {
          headers: { 'Content-Type': 'multipart/form-data' },
        });
      }

      setSuccess(res.data.numero);
      setForm({ nom: '', telephone: '', email: '', description: '', agenceId: '' });
      setPhotoFiles([]);
      setPhotoPreviews([]);
    } catch {
      setError("Erreur lors de l'envoi. Veuillez réessayer.");
    } finally {
      setSubmitting(false);
    }
  };

  const isFieldActive = (name: string) => focusedField === name || form[name as keyof typeof form] !== '';

  return (
    <>
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800;900&display=swap');
        *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
        body { overflow-x: hidden; }

        .cf-page { min-height: 100vh; display: flex; font-family: 'Inter', sans-serif; transition: background .4s; }

        /* ═══ LEFT PANEL ═══ */
        .cf-left {
          flex: 0 0 44%; display: flex; flex-direction: column; justify-content: center; align-items: center;
          padding: 60px 48px; position: relative; overflow: hidden;
          background: ${t.leftBg};
          color: #fff;
        }
        .cf-left::before {
          content: ''; position: absolute; inset: 0;
          background: radial-gradient(circle at 30% 70%, rgba(139,92,246,.25) 0%, transparent 60%),
                      radial-gradient(circle at 80% 20%, rgba(99,102,241,.2) 0%, transparent 50%);
        }
        .cf-left-content { position: relative; z-index: 1; max-width: 380px; }
        .cf-logo {
          width: 56px; height: 56px; border-radius: 16px;
          background: rgba(255,255,255,.15); backdrop-filter: blur(12px);
          display: flex; align-items: center; justify-content: center;
          font-size: 26px; margin-bottom: 28px;
          border: 1px solid rgba(255,255,255,.1);
        }
        .cf-left h1 { font-size: 36px; font-weight: 900; letter-spacing: -.03em; line-height: 1.15; margin-bottom: 14px; }
        .cf-left p { color: rgba(255,255,255,.7); font-size: 15px; line-height: 1.6; margin-bottom: 40px; }

        .cf-features { display: flex; flex-direction: column; gap: 16px; }
        .cf-feature {
          display: flex; align-items: center; gap: 14px; padding: 14px 18px;
          background: rgba(255,255,255,.06); backdrop-filter: blur(8px);
          border: 1px solid rgba(255,255,255,.08); border-radius: 14px;
          transition: background .25s, border-color .25s;
        }
        .cf-feature:hover { background: rgba(255,255,255,.1); border-color: rgba(255,255,255,.15); }
        .cf-feature-icon {
          width: 40px; height: 40px; border-radius: 10px;
          background: rgba(139,92,246,.2); display: flex; align-items: center; justify-content: center;
          font-size: 18px; flex-shrink: 0;
        }
        .cf-feature-text h4 { font-size: 13px; font-weight: 700; margin-bottom: 2px; }
        .cf-feature-text span { font-size: 11px; color: rgba(255,255,255,.5); }

        /* ═══ RIGHT PANEL ═══ */
        .cf-right {
          flex: 1; display: flex; flex-direction: column; justify-content: center; align-items: center;
          padding: 40px 48px; background: ${t.rightBg}; transition: background .4s;
        }
        .cf-form-wrap { width: 100%; max-width: 440px; }
        .cf-form-header { margin-bottom: 32px; }
        .cf-form-header h2 { font-size: 26px; font-weight: 800; color: ${t.textPrimary}; letter-spacing: -.02em; margin-bottom: 6px; }
        .cf-form-header p { font-size: 13px; color: ${t.textSecondary}; }

        /* Theme toggle */
        .cf-theme-toggle {
          position: absolute; top: 20px; right: 20px; width: 40px; height: 40px;
          border-radius: 12px; border: 1px solid ${t.border}; background: ${t.inputBg};
          display: flex; align-items: center; justify-content: center; cursor: pointer;
          font-size: 18px; transition: all .25s; z-index: 10;
        }
        .cf-theme-toggle:hover { background: ${t.inputBgHover}; transform: scale(1.05); }

        /* ═══ FORM FIELDS ═══ */
        .cf-field { position: relative; margin-bottom: 20px; }
        .cf-field label {
          position: absolute; left: 16px; top: 50%; transform: translateY(-50%);
          font-size: 14px; color: ${t.textSecondary}; pointer-events: none;
          transition: all .2s cubic-bezier(.4,0,.2,1); font-weight: 500;
        }
        .cf-field.active label, .cf-field:focus-within label {
          top: 8px; transform: translateY(0); font-size: 10px; font-weight: 600;
          color: ${t.accent}; letter-spacing: .04em;
        }
        .cf-field.textarea-field label { top: 18px; transform: none; }
        .cf-field.textarea-field.active label, .cf-field.textarea-field:focus-within label {
          top: 8px; font-size: 10px; color: ${t.accent};
        }
        .cf-field.select-field label {
          top: 8px; transform: translateY(0); font-size: 10px; font-weight: 600;
          color: ${t.textSecondary}; letter-spacing: .04em;
        }

        .cf-input, .cf-textarea, .cf-select {
          width: 100%; padding: 24px 16px 8px; border-radius: 12px;
          background: ${t.inputBg}; border: 1.5px solid ${t.border};
          color: ${t.textPrimary}; font-size: 14px; font-family: 'Inter', sans-serif;
          outline: none; transition: all .25s cubic-bezier(.4,0,.2,1);
        }
        .cf-input:focus, .cf-textarea:focus, .cf-select:focus {
          border-color: ${t.accent}; background: ${t.inputBgFocus};
          box-shadow: 0 0 0 3px ${t.accentGlow};
        }
        .cf-input:hover, .cf-textarea:hover, .cf-select:hover { background: ${t.inputBgHover}; }
        .cf-textarea { min-height: 100px; resize: vertical; padding-top: 28px; }
        .cf-select { cursor: pointer; appearance: none; padding-right: 40px; }
        .cf-select option { background: ${t.optionBg}; color: ${t.optionText}; padding: 10px 14px; }
        .cf-select-arrow {
          position: absolute; right: 14px; top: 50%; transform: translateY(-50%);
          pointer-events: none; color: ${t.textSecondary}; font-size: 12px;
        }

        .cf-required { color: #ef4444; margin-left: 2px; }

        /* Row layout */
        .cf-row { display: flex; gap: 12px; }
        .cf-row .cf-field { flex: 1; }

        /* ═══ SUBMIT BUTTON ═══ */
        .cf-submit {
          width: 100%; padding: 14px 0; margin-top: 6px; border-radius: 12px; border: none;
          background: ${t.btnBg}; color: #fff; font-size: 15px; font-weight: 700;
          font-family: 'Inter', sans-serif; cursor: pointer; position: relative; overflow: hidden;
          transition: all .3s cubic-bezier(.4,0,.2,1);
          box-shadow: ${t.btnShadow};
        }
        .cf-submit:hover:not(:disabled) { transform: translateY(-1px); box-shadow: ${t.btnShadowHover}; }
        .cf-submit:active:not(:disabled) { transform: translateY(0); }
        .cf-submit:disabled { opacity: .6; cursor: not-allowed; }
        .cf-submit::after {
          content: ''; position: absolute; inset: 0;
          background: linear-gradient(90deg, transparent, rgba(255,255,255,.1), transparent);
          transform: translateX(-100%); transition: transform .5s;
        }
        .cf-submit:hover::after { transform: translateX(100%); }

        .cf-privacy { text-align: center; margin-top: 16px; font-size: 11px; color: ${t.textMuted}; }

        /* ═══ ERROR / SUCCESS ═══ */
        .cf-error {
          padding: 12px 16px; border-radius: 10px; margin-bottom: 18px;
          background: rgba(239,68,68,.08); border: 1px solid rgba(239,68,68,.2);
          color: #ef4444; font-size: 13px; font-weight: 500;
          display: flex; align-items: center; gap: 8px;
        }
        .cf-success {
          text-align: center; padding: 40px 24px;
          animation: fadeUp .5s cubic-bezier(.4,0,.2,1);
        }
        .cf-success-check {
          width: 64px; height: 64px; margin: 0 auto 20px; border-radius: 50%;
          background: rgba(16,185,129,.1); border: 2px solid rgba(16,185,129,.3);
          display: flex; align-items: center; justify-content: center; font-size: 28px;
          animation: popIn .4s cubic-bezier(.175,.885,.32,1.275);
        }
        .cf-success h3 { font-size: 20px; font-weight: 800; color: #10b981; margin-bottom: 8px; }
        .cf-success .cf-ticket-num {
          display: inline-block; padding: 6px 16px; border-radius: 8px;
          background: ${t.inputBg}; border: 1px solid ${t.border};
          font-family: 'JetBrains Mono', monospace; font-size: 15px; font-weight: 700;
          color: ${t.accent}; margin: 8px 0 6px; letter-spacing: .04em;
        }
        .cf-success p { color: ${t.textSecondary}; font-size: 13px; margin-bottom: 4px; }
        .cf-success-btn {
          margin-top: 20px; padding: 10px 28px; border-radius: 10px;
          background: ${t.inputBg}; border: 1.5px solid ${t.border};
          color: ${t.accent}; font-size: 13px; font-weight: 600; cursor: pointer;
          font-family: 'Inter', sans-serif; transition: all .25s;
        }
        .cf-success-btn:hover { background: ${t.inputBgHover}; border-color: ${t.accent}; }

        /* Footer */
        .cf-footer {
          margin-top: 32px; padding-top: 20px; border-top: 1px solid ${t.border};
          text-align: center; font-size: 11px; color: ${t.textMuted};
        }

        @keyframes fadeUp { from { opacity: 0; transform: translateY(16px); } to { opacity: 1; transform: translateY(0); } }
        @keyframes popIn { from { opacity: 0; transform: scale(.5); } to { opacity: 1; transform: scale(1); } }

        /* ═══ RESPONSIVE ═══ */
        @media (max-width: 768px) {
          .cf-page { flex-direction: column; }
          .cf-left { flex: none; padding: 40px 24px; min-height: auto; }
          .cf-left h1 { font-size: 26px; }
          .cf-features { flex-direction: row; flex-wrap: wrap; }
          .cf-feature { flex: 1; min-width: 140px; }
          .cf-right { padding: 32px 20px; }
          .cf-row { flex-direction: column; gap: 0; }
        }
      `}</style>

      <div className="cf-page">
        {/* ═══ LEFT BRANDING PANEL ═══ */}
        <div className="cf-left">
          <div className="cf-left-content">
            <div className="cf-logo">🚗</div>
            <h1>Votre assistance automobile, simplifiée.</h1>
            <p>
              Soumettez votre demande en quelques secondes.
              Notre équipe d'experts prendra en charge votre dossier rapidement.
            </p>

            <div className="cf-features">
              <div className="cf-feature">
                <div className="cf-feature-icon">⚡</div>
                <div className="cf-feature-text">
                  <h4>Prise en charge rapide</h4>
                  <span>Réponse sous 30 minutes</span>
                </div>
              </div>
              <div className="cf-feature">
                <div className="cf-feature-icon">🔧</div>
                <div className="cf-feature-text">
                  <h4>Techniciens experts</h4>
                  <span>Réseau de garages certifiés</span>
                </div>
              </div>
              <div className="cf-feature">
                <div className="cf-feature-icon">📞</div>
                <div className="cf-feature-text">
                  <h4>Support 24/7</h4>
                  <span>Disponible jour et nuit</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* ═══ RIGHT FORM PANEL ═══ */}
        <div className="cf-right" style={{ position: 'relative' }}>
          <button className="cf-theme-toggle"
            onClick={() => setTheme(th => th === 'dark' ? 'light' : 'dark')}
            title={theme === 'dark' ? 'Mode clair' : 'Mode sombre'}
          >{theme === 'dark' ? '☀️' : '🌙'}</button>

          <div className="cf-form-wrap">
            <div className="cf-form-header">
              <h2>Demande d'assistance</h2>
              <p>Remplissez le formulaire ci-dessous, c'est simple et rapide.</p>
            </div>

            {error && <div className="cf-error">⚠ {error}</div>}

            {success ? (
              <div className="cf-success">
                <div className="cf-success-check">✓</div>
                <h3>Demande envoyée !</h3>
                <p>Votre numéro de ticket :</p>
                <div className="cf-ticket-num">{success}</div>
                <p>Un opérateur prendra en charge votre demande sous 30 minutes.</p>
                <button className="cf-success-btn" onClick={() => setSuccess(null)}>
                  ↻ Nouvelle demande
                </button>
              </div>
            ) : (
              <form ref={formRef} onSubmit={handleSubmit}>
                {/* Nom */}
                <div className={`cf-field ${isFieldActive('nom') ? 'active' : ''}`}>
                  <label>Nom complet<span className="cf-required">*</span></label>
                  <input className="cf-input" type="text" value={form.nom}
                    onChange={e => setForm(f => ({...f, nom: e.target.value}))}
                    onFocus={() => setFocusedField('nom')} onBlur={() => setFocusedField(null)} required />
                </div>

                {/* Tel + Email row */}
                <div className="cf-row">
                  <div className={`cf-field ${isFieldActive('telephone') ? 'active' : ''}`}>
                    <label>Téléphone</label>
                    <input className="cf-input" type="tel" value={form.telephone}
                      onChange={e => setForm(f => ({...f, telephone: e.target.value}))}
                      onFocus={() => setFocusedField('telephone')} onBlur={() => setFocusedField(null)} />
                  </div>
                  <div className={`cf-field ${isFieldActive('email') ? 'active' : ''}`}>
                    <label>Email</label>
                    <input className="cf-input" type="email" value={form.email}
                      onChange={e => setForm(f => ({...f, email: e.target.value}))}
                      onFocus={() => setFocusedField('email')} onBlur={() => setFocusedField(null)} />
                  </div>
                </div>

                {/* Agence */}
                <div className="cf-field select-field">
                  <label>Agence<span className="cf-required">*</span></label>
                  <select className="cf-select" value={form.agenceId}
                    onChange={e => setForm(f => ({...f, agenceId: e.target.value}))} required>
                    <option value="">— Sélectionner —</option>
                    {agences.map(a => <option key={a.id} value={a.id}>{a.nom}{a.ville ? ` — ${a.ville}` : ''}</option>)}
                  </select>
                  <span className="cf-select-arrow">▾</span>
                </div>

                {/* Description */}
                <div className={`cf-field textarea-field ${isFieldActive('description') ? 'active' : ''}`}>
                  <label>Description du problème<span className="cf-required">*</span></label>
                  <textarea className="cf-textarea" value={form.description}
                    onChange={e => setForm(f => ({...f, description: e.target.value}))}
                    onFocus={() => setFocusedField('description')} onBlur={() => setFocusedField(null)} required />
                </div>

                {/* Photos sinistre */}
                <div style={{ marginBottom: 20 }}>
                  <div style={{
                    fontSize: 10, fontWeight: 600, color: t.textSecondary,
                    letterSpacing: '.04em', marginBottom: 8, textTransform: 'uppercase' as const,
                  }}>📸 Photos du sinistre <span style={{ fontWeight: 400, opacity: .6 }}>(optionnel — max {MAX_PHOTOS})</span></div>
                  <div
                    onClick={() => photoInputRef.current?.click()}
                    onDragOver={e => { e.preventDefault(); (e.currentTarget as HTMLDivElement).style.borderColor = t.accent; }}
                    onDragLeave={e => { e.preventDefault(); (e.currentTarget as HTMLDivElement).style.borderColor = t.border; }}
                    onDrop={e => { e.preventDefault(); (e.currentTarget as HTMLDivElement).style.borderColor = t.border; handlePhotoSelect(e.dataTransfer.files); }}
                    style={{
                      border: `2px dashed ${t.border}`, borderRadius: 12, padding: '20px 16px',
                      textAlign: 'center', cursor: 'pointer', transition: 'all .25s',
                      background: t.inputBg,
                    }}
                  >
                    <input ref={photoInputRef} type="file" multiple accept="image/*"
                      style={{ display: 'none' }} onChange={e => handlePhotoSelect(e.target.files)} />
                    <div style={{ fontSize: 28, marginBottom: 4 }}>📷</div>
                    <div style={{ fontSize: 13, color: t.textSecondary, fontWeight: 500 }}>Glisser-déposer ou cliquer pour ajouter</div>
                    <div style={{ fontSize: 10, color: t.textMuted, marginTop: 2 }}>JPEG, PNG, WebP · max {MAX_SIZE_MB}MB par photo</div>
                  </div>

                  {photoPreviews.length > 0 && (
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginTop: 12 }}>
                      {photoPreviews.map((src, i) => (
                        <div key={i} style={{ position: 'relative' }}>
                          <img src={src} alt={`Photo ${i+1}`} style={{
                            width: 64, height: 64, borderRadius: 10, objectFit: 'cover',
                            border: `1.5px solid ${t.border}`,
                          }} />
                          <button type="button" onClick={() => removePhoto(i)} style={{
                            position: 'absolute', top: -6, right: -6, width: 20, height: 20,
                            borderRadius: '50%', background: '#ef4444', color: '#fff', border: 'none',
                            fontSize: 10, cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center',
                          }}>✕</button>
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                <button type="submit" className="cf-submit" disabled={submitting}>
                  {submitting
                    ? (photoFiles.length > 0 ? '⏳ Envoi du formulaire et des photos...' : '⏳ Envoi en cours...')
                    : (photoFiles.length > 0 ? `Envoyer ma demande + ${photoFiles.length} photo(s) →` : 'Envoyer ma demande →')}
                </button>

                <p className="cf-privacy">
                  🔒 Vos données sont sécurisées et ne seront jamais partagées.
                </p>
              </form>
            )}

            <div className="cf-footer">© 2026 AutoAssist — Système de gestion des appels</div>
          </div>
        </div>
      </div>
    </>
  );
}

/* ═══ THEME TOKENS ═══ */
const dk = {
  leftBg: 'linear-gradient(160deg, #0c0a1d 0%, #1a1145 40%, #130f2a 100%)',
  rightBg: '#0a0a0f',
  textPrimary: '#f1f5f9',
  textSecondary: '#64748b',
  textMuted: '#3b404d',
  inputBg: 'rgba(255,255,255,.04)',
  inputBgHover: 'rgba(255,255,255,.07)',
  inputBgFocus: 'rgba(255,255,255,.06)',
  border: 'rgba(255,255,255,.08)',
  accent: '#8b5cf6',
  accentGlow: 'rgba(139,92,246,.12)',
  btnBg: 'linear-gradient(135deg, #8b5cf6 0%, #6366f1 100%)',
  btnShadow: '0 4px 20px rgba(139,92,246,.25)',
  btnShadowHover: '0 8px 30px rgba(139,92,246,.35)',
  optionBg: '#1e1b2e',
  optionText: '#e2e8f0',
};

const lt = {
  leftBg: 'linear-gradient(160deg, #312e81 0%, #4f46e5 40%, #6366f1 100%)',
  rightBg: '#fafbfc',
  textPrimary: '#111827',
  textSecondary: '#6b7280',
  textMuted: '#d1d5db',
  inputBg: '#fff',
  inputBgHover: '#f9fafb',
  inputBgFocus: '#fff',
  border: '#e5e7eb',
  accent: '#6366f1',
  accentGlow: 'rgba(99,102,241,.12)',
  btnBg: 'linear-gradient(135deg, #6366f1 0%, #4f46e5 100%)',
  btnShadow: '0 4px 20px rgba(99,102,241,.2)',
  btnShadowHover: '0 8px 30px rgba(99,102,241,.3)',
  optionBg: '#ffffff',
  optionText: '#111827',
};
