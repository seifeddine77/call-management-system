import { useState, useEffect, type ChangeEvent } from 'react';
import axios from 'axios';
import { Car, Zap, Wrench, Phone, Sun, Moon, AlertTriangle, Camera, ArrowLeft, ArrowRight, Check, ShieldAlert, Sparkles, User, Info, FileText } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';

const GATEWAY = import.meta.env.VITE_API_URL || '';
const MAX_PHOTOS = 5;
const MAX_SIZE_MB = 10;

interface Agence { id: string; nom: string; ville: string; }

type Theme = 'light' | 'dark';
type SinistreType = 'accident' | 'bris' | 'panne' | 'autre';
const getSystemTheme = (): Theme =>
  globalThis.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';

/* ───── Module-level helpers ───── */

const TYPE_TEMPLATES: Record<string, string> = {
  accident: '[Accident] Collision survenue le... Circonstances : ',
  bris: '[Bris de glace] Impact sur le pare-brise/vitre... Détails : ',
  panne: '[Panne mécanique] Perte de puissance / voyant moteur... Symptômes : ',
  autre: "[Autre] Nature de l'assistance : ",
};

function validateNom(nom: string, touched: boolean): string {
  if (!touched) return '';
  if (!nom.trim()) return 'Le nom complet est obligatoire.';
  if (nom.trim().length < 3) return 'Le nom doit contenir au moins 3 caractères.';
  return '';
}

function validateTel(tel: string, touched: boolean): string {
  if (!touched) return '';
  if (tel.trim() && !/^\+?[0-9\s-]{8,15}$/.test(tel.trim().replace(/\s/g, ''))) {
    return 'Veuillez saisir un numéro de téléphone valide (8 à 15 chiffres).';
  }
  return '';
}

// Safe email regex: specific character classes with bounded quantifiers — avoids ReDoS
const SAFE_EMAIL_RE = /^[a-zA-Z0-9._%+-]{1,64}@[a-zA-Z0-9.-]{1,253}\.[a-zA-Z]{2,}$/;

function validateEmail(email: string, touched: boolean): string {
  if (!touched) return '';
  if (email.trim() && !SAFE_EMAIL_RE.test(email.trim())) {
    return 'Veuillez saisir une adresse e-mail valide.';
  }
  return '';
}

function validateDesc(desc: string, touched: boolean): string {
  if (!touched) return '';
  if (!desc.trim()) return 'La description est obligatoire.';
  if (desc.trim().length < 15) return 'Veuillez décrire le problème avec au moins 15 caractères.';
  return '';
}

function validateStep0(
  form: { nom: string; telephone: string; email: string }
): string | null {
  if (!form.nom.trim() || form.nom.trim().length < 3) {
    return 'Le nom complet doit comporter au moins 3 caractères.';
  }
  if (!form.telephone.trim() && !form.email.trim()) {
    return 'Veuillez fournir au moins un moyen de contact (Téléphone ou Email).';
  }
  if (form.telephone.trim() && !/^\+?[0-9\s-]{8,15}$/.test(form.telephone.trim().replace(/\s/g, ''))) {
    return 'Le format du numéro de téléphone est incorrect.';
  }
  if (form.email.trim() && !SAFE_EMAIL_RE.test(form.email.trim())) {
    return "L'adresse e-mail n'est pas valide.";
  }
  return null;
}

function readPublicFileAsDataURL(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = e => resolve(e.target?.result as string);
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}

const formatPhoneNumber = (val: string) => {
  let clean = val.replace(/[^\d+]/g, '');
  if (clean.startsWith('+')) {
    return clean.slice(0, 16);
  }
  if (clean.length > 10) clean = clean.slice(0, 10);
  const parts = [];
  for (let i = 0; i < clean.length; i += 2) {
    parts.push(clean.slice(i, i + 2));
  }
  return parts.join(' ');
};

const computeFieldClassName = (
  name: string,
  errorMsg: string | null,
  touched: Record<string, boolean>,
  formValue: string | boolean,
  isFieldActive: boolean
) => {
  let classes = `cf-field ${isFieldActive ? 'active' : ''}`;
  if (touched[name]) {
    if (errorMsg) {
      classes += ' has-error';
    } else if (formValue) {
      classes += ' is-valid';
    }
  }
  return classes;
};

const validateFormStep1 = (selectedType: string | null, agenceId: string): string | null => {
  if (!selectedType) return 'Veuillez sélectionner un type de sinistre.';
  if (!agenceId) return 'Veuillez sélectionner votre agence de référence.';
  return null;
};

const validateFormStep2 = (description: string): string | null => {
  if (!description.trim() || description.trim().length < 15) {
    return 'La description des circonstances doit comporter au moins 15 caractères.';
  }
  return null;
};

interface StepCoordonneesProps {
  form: { nom: string; telephone: string; email: string };
  setForm: React.Dispatch<React.SetStateAction<{ nom: string; telephone: string; email: string; description: string; agenceId: string; consentementTraitement: boolean }>>;
  touched: Record<string, boolean>;
  setTouched: React.Dispatch<React.SetStateAction<Record<string, boolean>>>;
  setFocusedField: React.Dispatch<React.SetStateAction<string | null>>;
  getNomError: () => string;
  getTelError: () => string;
  getEmailError: () => string;
  handleBlur: (field: string) => void;
  handleTelChange: (e: ChangeEvent<HTMLInputElement>) => void;
  getFieldClassName: (name: string, err: string) => string;
}

function StepCoordonnees({
  form,
  setForm,
  touched,
  setTouched,
  setFocusedField,
  getNomError,
  getTelError,
  getEmailError,
  handleBlur,
  handleTelChange,
  getFieldClassName,
}: Readonly<StepCoordonneesProps>) {
  return (
    <motion.div
      key="step0"
      initial={{ opacity: 0, x: 30 }}
      animate={{ opacity: 1, x: 0 }}
      exit={{ opacity: 0, x: -30 }}
      className="space-y-4"
    >
      <div className="flex items-center gap-1.5 mb-2">
        <User size={16} className="text-indigo-400" />
        <h3 className="text-sm font-bold">Vos coordonnées</h3>
      </div>

      {/* Nom */}
      <div className={getFieldClassName('nom', getNomError())}>
        <label htmlFor="pf-nom" className="cf-label">Nom complet <span className="cf-required">*</span></label>
        <input id="pf-nom" className="cf-input" type="text" value={form.nom}
          onChange={e => { setForm(f => ({...f, nom: e.target.value})); setTouched(p => ({...p, nom: true})); }}
          onFocus={() => setFocusedField('nom')} onBlur={() => handleBlur('nom')} required />
        {touched.nom && (
          <div className="cf-validation-badge">
            {getNomError() ? <ShieldAlert size={16} className="text-red-500" /> : <Check size={16} className="text-emerald-500" />}
          </div>
        )}
        {getNomError() && <div className="cf-validation-msg"><AlertTriangle size={12} /> {getNomError()}</div>}
      </div>

      {/* Téléphone */}
      <div className={getFieldClassName('telephone', getTelError())}>
        <label htmlFor="pf-tel" className="cf-label">Numéro de téléphone</label>
        <input id="pf-tel" className="cf-input" type="tel" value={form.telephone}
          onChange={handleTelChange}
          onFocus={() => setFocusedField('telephone')} onBlur={() => handleBlur('telephone')} />
        {touched.telephone && form.telephone && (
          <div className="cf-validation-badge">
            {getTelError() ? <ShieldAlert size={16} className="text-red-500" /> : <Check size={16} className="text-emerald-500" />}
          </div>
        )}
        {getTelError() && <div className="cf-validation-msg"><AlertTriangle size={12} /> {getTelError()}</div>}
      </div>

      {/* Email */}
      <div className={getFieldClassName('email', getEmailError())}>
        <label htmlFor="pf-email" className="cf-label">Adresse e-mail</label>
        <input id="pf-email" className="cf-input" type="email" value={form.email}
          onChange={e => { setForm(f => ({...f, email: e.target.value})); setTouched(p => ({...p, email: true})); }}
          onFocus={() => setFocusedField('email')} onBlur={() => handleBlur('email')} />
        {touched.email && form.email && (
          <div className="cf-validation-badge">
            {getEmailError() ? <ShieldAlert size={16} className="text-red-500" /> : <Check size={16} className="text-emerald-500" />}
          </div>
        )}
        {getEmailError() && <div className="cf-validation-msg"><AlertTriangle size={12} /> {getEmailError()}</div>}
      </div>
    </motion.div>
  );
}

interface StepNatureSinistreProps {
  selectedType: SinistreType | null;
  form: { agenceId: string };
  setForm: React.Dispatch<React.SetStateAction<{ nom: string; telephone: string; email: string; description: string; agenceId: string; consentementTraitement: boolean }>>;
  touched: Record<string, boolean>;
  setTouched: React.Dispatch<React.SetStateAction<Record<string, boolean>>>;
  agences: Agence[];
  handleTypeSelect: (type: SinistreType) => void;
}

function StepNatureSinistre({
  selectedType,
  form,
  setForm,
  touched,
  setTouched,
  agences,
  handleTypeSelect,
}: Readonly<StepNatureSinistreProps>) {
  return (
    <motion.div
      key="step1"
      initial={{ opacity: 0, x: 30 }}
      animate={{ opacity: 1, x: 0 }}
      exit={{ opacity: 0, x: -30 }}
      className="space-y-4"
    >
      <div className="flex items-center gap-1.5 mb-2">
         <Info size={16} className="text-indigo-400" />
         <h3 className="text-sm font-bold">Nature du sinistre & Agence</h3>
      </div>

      {/* Tactile Cards grid */}
      <div className="cf-type-grid">
        {([
          { id: 'accident', title: 'Accident / Choc', icon: <Car size={20} />, color: 'rgba(239, 68, 68, 0.15)', textC: 'text-rose-400 border-rose-500/20' },
          { id: 'bris', title: 'Bris de glace', icon: <Zap size={20} />, color: 'rgba(59, 130, 246, 0.15)', textC: 'text-blue-400 border-blue-500/20' },
          { id: 'panne', title: 'Panne mécanique', icon: <Wrench size={20} />, color: 'rgba(245, 158, 11, 0.15)', textC: 'text-amber-400 border-amber-500/20' },
          { id: 'autre', title: 'Autre motif', icon: <Phone size={20} />, color: 'rgba(139, 92, 246, 0.15)', textC: 'text-violet-400 border-violet-500/20' }
        ] as const).map(item => (
          <button 
            key={item.id}
            type="button"
            onClick={() => handleTypeSelect(item.id)}
            className={`cf-type-card ${selectedType === item.id ? 'selected' : ''}`}
          >
            <div className="cf-type-card-icon" style={{ backgroundColor: item.color }}>
              <span className={item.textC}>{item.icon}</span>
            </div>
            <span className="cf-type-card-title">{item.title}</span>
          </button>
        ))}
      </div>

      {/* Agence select dropdown */}
      <div className={`cf-field select-field ${touched.agenceId && form.agenceId ? 'is-valid' : ''}${touched.agenceId && !form.agenceId ? 'has-error' : ''}`}>
        <label htmlFor="pf-agence" className="cf-label">Votre agence de référence <span className="cf-required">*</span></label>
        <select id="pf-agence" className="cf-select" value={form.agenceId}
          onChange={e => { setForm(f => ({...f, agenceId: e.target.value})); setTouched(p => ({...p, agenceId: true})); }} required>
          <option value="">— Choisir l'agence —</option>
          {agences.map(a => <option key={a.id} value={a.id}>{a.nom}{a.ville ? ` — ${a.ville}` : ''}</option>)}
        </select>
        <span className="cf-select-arrow">▾</span>
        {touched.agenceId && (
          <div className="cf-validation-badge mr-6">
            {form.agenceId ? <Check size={16} className="text-emerald-500" /> : <ShieldAlert size={16} className="text-red-500" />}
          </div>
        )}
        {touched.agenceId && !form.agenceId && <div className="cf-validation-msg"><AlertTriangle size={12} /> L'agence est obligatoire.</div>}
      </div>
    </motion.div>
  );
}

interface StepDescriptionPhotosProps {
  form: { description: string };
  setForm: React.Dispatch<React.SetStateAction<{ nom: string; telephone: string; email: string; description: string; agenceId: string; consentementTraitement: boolean }>>;
  touched: Record<string, boolean>;
  setTouched: React.Dispatch<React.SetStateAction<Record<string, boolean>>>;
  setFocusedField: React.Dispatch<React.SetStateAction<string | null>>;
  getDescError: () => string;
  isFieldActive: (name: string) => boolean;
  handleBlur: (field: string) => void;
  photoPreviews: string[];
  handlePhotoSelect: (files: FileList | null) => void;
  removePhoto: (index: number) => void;
  themeBorder: string;
}

function StepDescriptionPhotos({
  form,
  setForm,
  touched,
  setTouched,
  setFocusedField,
  getDescError,
  isFieldActive,
  handleBlur,
  photoPreviews,
  handlePhotoSelect,
  removePhoto,
  themeBorder,
}: Readonly<StepDescriptionPhotosProps>) {
  return (
    <motion.div
      key="step2"
      initial={{ opacity: 0, x: 30 }}
      animate={{ opacity: 1, x: 0 }}
      exit={{ opacity: 0, x: -30 }}
      className="space-y-4"
    >
      <div className="flex items-center gap-1.5 mb-2">
        <FileText size={16} className="text-indigo-400" />
        <h3 className="text-sm font-bold">Description & Pièces Jointes</h3>
      </div>

       {/* Description textarea */}
      <div className={`cf-field textarea-field ${isFieldActive('description') ? 'active' : ''} ${touched.description && getDescError() ? 'has-error' : ''}${touched.description && !getDescError() ? 'is-valid' : ''}`}>
        <label htmlFor="pf-desc" className="cf-label">Description des circonstances <span className="cf-required">*</span></label>
        <textarea id="pf-desc" className="cf-textarea" value={form.description}
          onChange={e => { setForm(f => ({...f, description: e.target.value})); setTouched(p => ({...p, description: true})); }}
          onFocus={() => setFocusedField('description')} onBlur={() => handleBlur('description')} required />
        {touched.description && (
          <div className="cf-validation-badge">
            {getDescError() ? <ShieldAlert size={16} className="text-red-500" /> : <Check size={16} className="text-emerald-500" />}
          </div>
        )}
        <div className="flex justify-between items-center mt-1.5 px-1">
          <div>
            {getDescError() && <div className="cf-validation-msg"><AlertTriangle size={12} /> {getDescError()}</div>}
          </div>
          <div className="text-[10px] text-txt-3 font-mono tracking-wider">
            {form.description.length} caractères · {form.description.trim() ? form.description.trim().split(/\s+/).length : 0} mots
          </div>
        </div>
      </div>

      {/* Glassy Image Dropzone */}
      <div>
        <div className="text-[10px] uppercase font-bold text-txt-3 tracking-wider mb-2">
          Photos du véhicule / sinistre <span className="opacity-60">(optionnel · max {MAX_PHOTOS})</span>
        </div>
        
        <label
          onDragOver={e => { e.preventDefault(); e.currentTarget.style.borderColor = '#8b5cf6'; }}
          onDragLeave={e => { e.preventDefault(); e.currentTarget.style.borderColor = themeBorder; }}
          onDrop={e => { e.preventDefault(); e.currentTarget.style.borderColor = themeBorder; handlePhotoSelect(e.dataTransfer.files); }}
          className="cf-dropzone"
        >
          <input type="file" multiple accept="image/*"
            style={{ display: 'none' }} onChange={e => handlePhotoSelect(e.target.files)} />
          <div className="cf-dropzone-icon"><Camera size={22} /></div>
          <div className="text-xs font-bold text-txt-2">Glisser-déposer ou cliquer pour ajouter</div>
          <div className="text-[9px] text-txt-3">JPEG, PNG, WebP · max {MAX_SIZE_MB}Mo par photo</div>
        </label>

        {/* Polaroid Previews */}
        {photoPreviews.length > 0 && (
          <div className="cf-previews-container">
            {photoPreviews.map((src, i) => (
              <motion.div 
                key={`photo-preview-${i}-${src.substring(0, 50)}`}
                initial={{ scale: 0.8, opacity: 0 }}
                animate={{ scale: 1, opacity: 1 }}
                className="cf-polaroid"
              >
                <img src={src} alt={`Preview ${i+1}`} />
                <button type="button" onClick={() => removePhoto(i)} className="cf-polaroid-remove">✕</button>
              </motion.div>
            ))}
          </div>
        )}
      </div>
    </motion.div>
  );
}

interface StepRecapitulatifProps {
  form: { nom: string; telephone: string; email: string; agenceId: string; consentementTraitement: boolean };
  setForm: React.Dispatch<React.SetStateAction<{ nom: string; telephone: string; email: string; description: string; agenceId: string; consentementTraitement: boolean }>>;
  selectedType: SinistreType | null;
  agences: Agence[];
  photoFiles: File[];
}

function StepRecapitulatif({
  form,
  setForm,
  selectedType,
  agences,
  photoFiles,
}: Readonly<StepRecapitulatifProps>) {
  return (
    <motion.div
      key="step3"
      initial={{ opacity: 0, x: 30 }}
      animate={{ opacity: 1, x: 0 }}
      exit={{ opacity: 0, x: -30 }}
      className="space-y-4"
    >
      <div className="flex items-center gap-1.5 mb-2">
        <ShieldAlert size={16} className="text-indigo-400" />
        <h3 className="text-sm font-bold">Récapitulatif & Soumission</h3>
      </div>

      {/* Bento Summary Box */}
      <div className="cf-summary-box">
        <div className="cf-summary-row">
          <span className="cf-summary-label">Déclarant</span>
          <span className="cf-summary-val">{form.nom}</span>
        </div>
        {(form.telephone || form.email) && (
          <div className="cf-summary-row">
            <span className="cf-summary-label">Contact</span>
            <span className="cf-summary-val">{form.telephone || form.email}</span>
          </div>
        )}
        <div className="cf-summary-row">
          <span className="cf-summary-label">Sinistre</span>
          <span className="cf-summary-val uppercase font-bold text-indigo-400">{selectedType}</span>
        </div>
        <div className="cf-summary-row">
          <span className="cf-summary-label">Agence</span>
          <span className="cf-summary-val">
            {agences.find(a => a.id === form.agenceId)?.nom || 'Inconnue'}
          </span>
        </div>
        <div className="cf-summary-row">
          <span className="cf-summary-label">Photos jointes</span>
          <span className="cf-summary-val">{photoFiles.length} photo(s)</span>
        </div>
      </div>

      {/* RGPD Consent */}
      <label className="cf-checkbox-container">
        <input
          type="checkbox"
          className="cf-checkbox-input"
          checked={form.consentementTraitement}
          onChange={e => setForm(f => ({ ...f, consentementTraitement: e.target.checked }))}
          required
        />
        <span className="cf-checkbox-label">
          J'accepte que mes données personnelles soient traitées dans le cadre de ma demande d'assistance conformément au RGPD.
        </span>
      </label>
    </motion.div>
  );
}

export default function PublicFormPage() {
  const [currentStep, setCurrentStep] = useState(0);
  const [form, setForm] = useState({ nom: '', telephone: '', email: '', description: '', agenceId: '', consentementTraitement: false });
  const [selectedType, setSelectedType] = useState<SinistreType | null>(null);
  const [agences, setAgences] = useState<Agence[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [success, setSuccess] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [theme, setTheme] = useState<Theme>(getSystemTheme);
  const [focusedField, setFocusedField] = useState<string | null>(null);

  // ── Real-time touched tracking ──
  const [touched, setTouched] = useState<Record<string, boolean>>({});

  const handleBlur = (field: string) => {
    setTouched(prev => ({ ...prev, [field]: true }));
    setFocusedField(null);
  };

  const getFieldClassName = (name: string, errorMsg: string) => {
    return computeFieldClassName(name, errorMsg, touched, form[name as keyof typeof form], isFieldActive(name));
  };

  const handleTelChange = (e: ChangeEvent<HTMLInputElement>) => {
    const formatted = formatPhoneNumber(e.target.value);
    setForm(f => ({ ...f, telephone: formatted }));
    setTouched(prev => ({ ...prev, telephone: true }));
  };

  // ── Validation checks (delegated to module-level helpers) ──
  const getNomError = () => validateNom(form.nom, !!touched.nom);
  const getTelError = () => validateTel(form.telephone, !!touched.telephone);
  const getEmailError = () => validateEmail(form.email, !!touched.email);
  const getDescError = () => validateDesc(form.description, !!touched.description);

  // ── Photos sinistre ──
  const [photoFiles, setPhotoFiles] = useState<File[]>([]);
  const [photoPreviews, setPhotoPreviews] = useState<string[]>([]);

  const handlePhotoSelect = async (files: FileList | null) => {
    if (!files) return;
    const remaining = MAX_PHOTOS - photoFiles.length;
    const valid = Array.from(files)
      .filter(f => f.type.startsWith('image/') && f.size <= MAX_SIZE_MB * 1024 * 1024)
      .slice(0, remaining);
    if (valid.length < files.length) {
      setError(`Seules les images de moins de ${MAX_SIZE_MB}Mo sont acceptées (max ${MAX_PHOTOS}).`);
    }
    setPhotoFiles(prev => [...prev, ...valid]);
    const previews = await Promise.all(valid.map(readPublicFileAsDataURL));
    setPhotoPreviews(prev => [...prev, ...previews]);
  };

  const removePhoto = (index: number) => {
    setPhotoFiles(prev => prev.filter((_, i) => i !== index));
    setPhotoPreviews(prev => prev.filter((_, i) => i !== index));
  };

  useEffect(() => {
    axios.get<Agence[]>(`${GATEWAY}/api/agences`).then(r => {
      const data = r.data;
      setAgences(Array.isArray(data) ? data : (data as { content?: Agence[] }).content ?? []);
    }).catch(() => setAgences([]));
  }, []);

  const t = theme === 'dark' ? dk : lt;

  const handleTypeSelect = (type: SinistreType) => {
    setSelectedType(type);
    setForm(f => ({ ...f, description: TYPE_TEMPLATES[type] ?? '' }));
    setTouched(p => ({ ...p, description: true }));
  };

  const handleNext = () => {
    setError(null);
    if (currentStep === 0) {
      setTouched(prev => ({ ...prev, nom: true, telephone: true, email: true }));
      const err = validateStep0(form);
      if (err) { setError(err); return; }
    } else if (currentStep === 1) {
      const err = validateFormStep1(selectedType, form.agenceId);
      if (err) { setError(err); return; }
    } else if (currentStep === 2) {
      setTouched(prev => ({ ...prev, description: true }));
      const err = validateFormStep2(form.description);
      if (err) { setError(err); return; }
    }
    setCurrentStep(prev => Math.min(prev + 1, 3));
  };

  const handlePrev = () => {
    setError(null);
    setCurrentStep(prev => Math.max(prev - 1, 0));
  };


  const handleSubmit = async (e: React.SyntheticEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError(null);
    if (!form.consentementTraitement) {
      setError('Vous devez accepter les conditions de traitement de vos données.');
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
        // Priorité omise volontairement : détectée automatiquement côté back (TicketServiceImpl.detectPriority)
        // sur la description, de façon cohérente avec les canaux CALL et EMAIL.
        consentementTraitement: form.consentementTraitement,
      });

      if (photoFiles.length > 0 && res.data.id) {
        const formData = new FormData();
        photoFiles.forEach(f => formData.append('files', f));
        await axios.post(`${GATEWAY}/api/tickets/${res.data.id}/photos`, formData, {
          headers: { 'Content-Type': 'multipart/form-data' },
        });
      }

      setSuccess(res.data.numero);
      setForm({ nom: '', telephone: '', email: '', description: '', agenceId: '', consentementTraitement: false });
      setSelectedType(null);
      setPhotoFiles([]);
      setPhotoPreviews([]);
      setCurrentStep(0);
    } catch {
      setError("Erreur lors de l'envoi de votre demande. Veuillez réessayer.");
    } finally {
      setSubmitting(false);
    }
  };

  const isFieldActive = (name: string) => focusedField === name || form[name as keyof typeof form] !== '';

  return (
    <>
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;500;600;700;800;900&display=swap');
        *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
        body { overflow-x: hidden; }

        .cf-page { min-height: 100vh; display: flex; font-family: 'Outfit', sans-serif; transition: background .4s; }

        /* ═══ LEFT PANEL (Apple Minimalist Branding) ═══ */
        .cf-left {
          flex: 0 0 42%; display: flex; flex-direction: column; justify-content: center; align-items: center;
          padding: 60px 48px; position: relative; overflow: hidden;
          background: ${t.leftBg};
          color: #fff;
        }
        .cf-left::before {
          content: ''; position: absolute; inset: 0;
          background: radial-gradient(circle at 10% 90%, rgba(139,92,246,.3) 0%, transparent 60%),
                      radial-gradient(circle at 90% 10%, rgba(99,102,241,.25) 0%, transparent 50%);
        }
        .cf-left-content { position: relative; z-index: 1; max-width: 400px; }
        .cf-logo {
          width: 56px; height: 56px; border-radius: 18px;
          background: rgba(255,255,255,.08); backdrop-filter: blur(16px);
          display: flex; align-items: center; justify-content: center;
          font-size: 26px; margin-bottom: 32px;
          border: 1px solid rgba(255,255,255,.12);
          box-shadow: 0 8px 32px rgba(0,0,0,0.15);
        }
        .cf-left h1 { font-size: 38px; font-weight: 800; letter-spacing: -.03em; line-height: 1.15; margin-bottom: 16px; }
        .cf-left p { color: rgba(255,255,255,.7); font-size: 15px; line-height: 1.6; margin-bottom: 44px; }

        .cf-features { display: flex; flex-direction: column; gap: 18px; }
        .cf-feature {
          display: flex; align-items: center; gap: 16px; padding: 16px 20px;
          background: rgba(255,255,255,.04); backdrop-filter: blur(12px);
          border: 1px solid rgba(255,255,255,.06); border-radius: 16px;
          transition: all .3s ease;
        }
        .cf-feature:hover { background: rgba(255,255,255,.08); border-color: rgba(255,255,255,.12); transform: translateY(-2px); }
        .cf-feature-icon {
          width: 44px; height: 44px; border-radius: 12px;
          background: rgba(139,92,246,.25); display: flex; align-items: center; justify-content: center;
          color: #a78bfa; flex-shrink: 0;
        }
        .cf-feature-text h4 { font-size: 14px; font-weight: 700; margin-bottom: 2px; }
        .cf-feature-text span { font-size: 12px; color: rgba(255,255,255,.5); }

        /* ═══ RIGHT PANEL (Frosted Conversational UI) ═══ */
        .cf-right {
          flex: 1; display: flex; flex-direction: column; justify-content: center; align-items: center;
          padding: 40px 48px; background: ${t.rightBg}; transition: background .4s;
          position: relative;
        }
        .cf-form-wrap { width: 100%; max-width: 480px; }
        .cf-form-header { margin-bottom: 28px; }
        .cf-form-header h2 { font-size: 28px; font-weight: 800; color: ${t.textPrimary}; letter-spacing: -.02em; margin-bottom: 8px; }
        .cf-form-header p { font-size: 14px; color: ${t.textSecondary}; }

        /* Step Progress bar */
        .cf-progress-container { display: flex; align-items: center; gap: 12px; margin-bottom: 32px; }
        .cf-progress-track { flex: 1; height: 6px; background: ${t.border}; border-radius: 10px; overflow: hidden; }
        .cf-progress-fill { height: 100%; background: linear-gradient(90deg, #8b5cf6 0%, #6366f1 100%); transition: width 0.3s ease; }
        .cf-progress-text { font-size: 11px; font-weight: 700; color: ${t.accent}; font-mono: true; letter-spacing: 0.05em; }

        /* Theme toggle */
        .cf-theme-toggle {
          position: absolute; top: 24px; right: 24px; width: 44px; height: 44px;
          border-radius: 14px; border: 1px solid ${t.border}; background: ${t.inputBg};
          display: flex; align-items: center; justify-content: center; cursor: pointer;
          color: ${t.textSecondary}; transition: all .25s; z-index: 10;
        }
        .cf-theme-toggle:hover { background: ${t.inputBgHover}; color: ${t.accent}; transform: scale(1.05); }

        /* ═══ TACTILE CARDS ═══ */
        .cf-type-grid { display: grid; grid-cols: 2; grid-template-columns: repeat(2, 1fr); gap: 12px; margin-bottom: 20px; }
        .cf-type-card {
          border-radius: 16px; border: 2px solid ${t.border}; background: ${t.inputBg};
          padding: 20px 16px; text-align: center; cursor: pointer; transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
          display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px;
          width: 100%; font-family: inherit; outline: none;
        }
        .cf-type-card:hover { border-color: ${t.accent}; background: ${t.inputBgHover}; transform: translateY(-2px); }
        .cf-type-card.selected { border-color: ${t.accent}; background: ${t.accentGlow}; box-shadow: 0 0 0 3px ${t.accentGlow}; }
        .cf-type-card-icon {
          width: 44px; height: 44px; border-radius: 12px; display: flex; align-items: center; justify-content: center;
          font-size: 20px; transition: all 0.3s;
        }
        .cf-type-card-title { font-size: 13px; font-weight: 700; color: ${t.textPrimary}; }

        /* ═══ FORM FIELDS ═══ */
        .cf-field { position: relative; margin-bottom: 20px; }
        .cf-label {
          position: absolute; left: 16px; top: 50%; transform: translateY(-50%);
          font-size: 14px; color: ${t.textSecondary}; pointer-events: none;
          transition: all .25s cubic-bezier(.4,0,.2,1); font-weight: 500;
        }
        .cf-field.active .cf-label, .cf-field:focus-within .cf-label {
          top: 8px; transform: translateY(0); font-size: 10px; font-weight: 600;
          color: ${t.accent}; letter-spacing: .04em;
        }
        .cf-field.textarea-field .cf-label { top: 18px; transform: none; }
        .cf-field.textarea-field.active .cf-label, .cf-field.textarea-field:focus-within .cf-label {
          top: 8px; font-size: 10px; color: ${t.accent};
        }
        .cf-field.select-field .cf-label {
          top: 8px; transform: translateY(0); font-size: 10px; font-weight: 600;
          color: ${t.textSecondary}; letter-spacing: .04em;
        }

        /* ═══ VALIDATION FEEDBACK ═══ */
        .cf-field.has-error .cf-input,
        .cf-field.has-error .cf-textarea,
        .cf-field.has-error .cf-select {
          border-color: #ef4444 !important;
          box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.15) !important;
        }
        .cf-field.has-error .cf-label {
          color: #ef4444 !important;
        }
        .cf-field.is-valid .cf-input,
        .cf-field.is-valid .cf-textarea,
        .cf-field.is-valid .cf-select {
          border-color: #10b981 !important;
          box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.1) !important;
        }
        .cf-field.is-valid .cf-label {
          color: #10b981 !important;
        }
        .cf-validation-msg {
          font-size: 11px;
          color: #ef4444;
          font-weight: 500;
          margin-top: 5px;
          display: flex;
          align-items: center;
          gap: 4px;
          padding-left: 4px;
        }
        .cf-validation-badge {
          position: absolute;
          right: 16px;
          top: 50%;
          transform: translateY(-50%);
          display: flex;
          align-items: center;
          pointer-events: none;
          z-index: 5;
        }
        .cf-field.textarea-field .cf-validation-badge {
          top: 24px;
          transform: none;
        }

        .cf-input, .cf-textarea, .cf-select {
          width: 100%; padding: 24px 16px 8px; border-radius: 14px;
          background: ${t.inputBg}; border: 1.5px solid ${t.border};
          color: ${t.textPrimary}; font-size: 14px; font-family: inherit;
          outline: none; transition: all .25s cubic-bezier(.4,0,.2,1);
        }
        .cf-input:focus, .cf-textarea:focus, .cf-select:focus {
          border-color: ${t.accent}; background: ${t.inputBgFocus};
          box-shadow: 0 0 0 3px ${t.accentGlow};
        }
        .cf-input:hover, .cf-textarea:hover, .cf-select:hover { background: ${t.inputBgHover}; }
        .cf-textarea { min-height: 120px; resize: none; padding-top: 28px; line-height: 1.5; }
        .cf-select { cursor: pointer; appearance: none; padding-right: 40px; }
        .cf-select option { background: ${t.optionBg}; color: ${t.optionText}; padding: 10px 14px; }
        .cf-select-arrow {
          position: absolute; right: 16px; top: 50%; transform: translateY(-50%);
          pointer-events: none; color: ${t.textSecondary}; font-size: 12px;
        }

        .cf-required { color: #ef4444; margin-left: 2px; }
        .cf-row { display: flex; gap: 12px; }
        .cf-row .cf-field { flex: 1; }

        /* ═══ GLASSY DROPZONE ═══ */
        .cf-dropzone {
          border: 2px dashed ${t.border}; border-radius: 16px; padding: 28px 20px;
          text-align: center; cursor: pointer; transition: all .25s ease;
          background: ${t.inputBg}; backdrop-filter: blur(8px);
          display: flex; flex-direction: column; align-items: center; gap: 6px;
        }
        .cf-dropzone:hover { border-color: ${t.accent}; background: ${t.inputBgHover}; transform: scale(1.01); }
        .cf-dropzone-icon {
          width: 48px; height: 48px; border-radius: 12px; display: flex; align-items: center; justify-content: center;
          color: ${t.accent}; background: ${t.accentGlow}; margin-bottom: 4px;
        }

        /* ═══ NAVIGATION CONTROLS ═══ */
        .cf-controls { display: flex; justify-content: space-between; gap: 12px; margin-top: 28px; }
        .cf-btn-prev {
          flex: 0 0 100px; padding: 12px 0; border-radius: 12px; border: 1.5px solid ${t.border};
          background: ${t.inputBg}; color: ${t.textPrimary}; font-size: 13px; font-weight: 700;
          cursor: pointer; display: flex; align-items: center; justify-content: center; gap: 6px;
          transition: all .25s ease;
        }
        .cf-btn-prev:hover { background: ${t.inputBgHover}; border-color: ${t.textSecondary}; }
        
        .cf-btn-next {
          flex: 1; padding: 12px 0; border-radius: 12px; border: none;
          background: ${t.btnBg}; color: #fff; font-size: 13px; font-weight: 700;
          cursor: pointer; display: flex; align-items: center; justify-content: center; gap: 6px;
          transition: all .3s cubic-bezier(.4,0,.2,1);
          box-shadow: ${t.btnShadow};
        }
        .cf-btn-next:hover { transform: translateY(-1px); box-shadow: ${t.btnShadowHover}; }
        .cf-btn-next:active { transform: translateY(0); }

        /* ═══ POLAROIDS PREVIEWS ═══ */
        .cf-previews-container { display: flex; flex-wrap: wrap; gap: 10px; marginTop: 16px; }
        .cf-polaroid {
          position: relative; width: 68px; height: 68px; border-radius: 12px; overflow: hidden;
          border: 1.5px solid ${t.border}; box-shadow: 0 4px 12px rgba(0,0,0,0.15);
          transition: transform .25s ease;
        }
        .cf-polaroid:hover { transform: scale(1.05) rotate(2deg); }
        .cf-polaroid img { width: 100%; height: 100%; object-fit: cover; }
        .cf-polaroid-remove {
          position: absolute; top: 4px; right: 4px; width: 18px; height: 18px;
          border-radius: 50%; background: rgba(239, 68, 68, 0.9); color: #fff; border: none;
          font-size: 9px; cursor: pointer; display: flex; align-items: center; justify-content: center;
          transition: all .2s ease;
        }
        .cf-polaroid-remove:hover { background: #ef4444; transform: scale(1.1); }

        /* Checkbox styling */
        .cf-checkbox-container {
          display: flex; align-items: flex-start; gap: 12px; margin-bottom: 24px;
          cursor: pointer; user-select: none;
        }
        .cf-checkbox-input {
          width: 18px; height: 18px; border-radius: 6px;
          border: 1.5px solid ${t.border}; background: ${t.inputBg};
          cursor: pointer; appearance: none; display: flex; align-items: center; justify-content: center;
          transition: all .25s; flex-shrink: 0; margin-top: 2px;
        }
        .cf-checkbox-input:checked {
          background: ${t.accent}; border-color: ${t.accent};
        }
        .cf-checkbox-input:checked::before {
          content: '✓'; color: #fff; font-size: 11px; font-weight: bold;
        }
        .cf-checkbox-input:focus {
          box-shadow: 0 0 0 3px ${t.accentGlow};
        }
        .cf-checkbox-label {
          font-size: 12px; line-height: 1.5; color: ${t.textSecondary};
        }

        .cf-privacy { text-align: center; margin-top: 18px; font-size: 11px; color: ${t.textMuted}; }

        /* ═══ ERROR / SUCCESS ═══ */
        .cf-error {
          padding: 12px 16px; border-radius: 12px; margin-bottom: 20px;
          background: rgba(239,68,68,.08); border: 1px solid rgba(239,68,68,.18);
          color: #ef4444; font-size: 13px; font-weight: 500;
          display: flex; align-items: center; gap: 8px;
        }
        .cf-success {
          text-align: center; padding: 40px 24px;
        }
        .cf-success-check {
          width: 72px; height: 72px; margin: 0 auto 24px; border-radius: 50%;
          background: rgba(16,185,129,.1); border: 2px solid rgba(16,185,129,.25);
          display: flex; align-items: center; justify-content: center; font-size: 32px;
          color: #10b981;
          box-shadow: 0 0 20px rgba(16,185,129,0.15);
        }
        .cf-success h3 { font-size: 22px; font-weight: 800; color: #10b981; margin-bottom: 8px; }
        .cf-success .cf-ticket-num {
          display: inline-block; padding: 8px 20px; border-radius: 10px;
          background: ${t.inputBg}; border: 1.5px solid ${t.border};
          font-family: 'JetBrains Mono', monospace; font-size: 16px; font-weight: 700;
          color: ${t.accent}; margin: 12px 0 10px; letter-spacing: .04em;
        }
        .cf-success p { color: ${t.textSecondary}; font-size: 14px; margin-bottom: 6px; }
        .cf-success-btn {
          margin-top: 24px; padding: 12px 32px; border-radius: 12px;
          background: ${t.inputBg}; border: 1.5px solid ${t.border};
          color: ${t.accent}; font-size: 13px; font-weight: 600; cursor: pointer;
          transition: all .25s;
        }
        .cf-success-btn:hover { background: ${t.inputBgHover}; border-color: ${t.accent}; }

        /* Summary Bento Box */
        .cf-summary-box {
          background: ${t.inputBg}; border: 1.5px solid ${t.border}; border-radius: 16px;
          padding: 16px; margin-bottom: 20px; display: flex; flex-direction: column; gap: 10px;
        }
        .cf-summary-row { display: flex; justify-content: space-between; font-size: 12px; border-bottom: 1px solid ${t.border}; padding-bottom: 6px; }
        .cf-summary-row:last-child { border-bottom: none; padding-bottom: 0; }
        .cf-summary-label { color: ${t.textSecondary}; font-weight: 600; }
        .cf-summary-val { color: ${t.textPrimary}; font-weight: 700; text-align: right; }

        .cf-footer {
          margin-top: 36px; padding-top: 20px; border-top: 1px solid ${t.border};
          text-align: center; font-size: 11px; color: ${t.textMuted};
        }

        /* ═══ RESPONSIVE ═══ */
        @media (max-width: 768px) {
          .cf-page { flex-direction: column; }
          .cf-left { flex: none; padding: 40px 24px; min-height: auto; }
          .cf-left h1 { font-size: 28px; }
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
            <motion.div 
              initial={{ scale: 0.8, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              transition={{ duration: 0.5 }}
              className="cf-logo"
            >
              <Car size={26} />
            </motion.div>
            <motion.h1
              initial={{ y: 20, opacity: 0 }}
              animate={{ y: 0, opacity: 1 }}
              transition={{ delay: 0.1, duration: 0.5 }}
            >
              Votre assistance automobile, simplifiée.
            </motion.h1>
            <motion.p
              initial={{ y: 20, opacity: 0 }}
              animate={{ y: 0, opacity: 1 }}
              transition={{ delay: 0.2, duration: 0.5 }}
            >
              Déclarez votre sinistre en quelques clics grâce à notre assistant interactif intelligent.
            </motion.p>

            <div className="cf-features">
              {[
                { icon: <Zap size={18} />, title: "Prise en charge instantanée", subtitle: "Diagnostic en temps réel par IA" },
                { icon: <Wrench size={18} />, title: "Réseau premium", subtitle: "Garages agréés par l'État" },
                { icon: <Phone size={18} />, title: "Assistance 24/7", subtitle: "Toujours à vos côtés" }
              ].map((feat, idx) => (
                <motion.div 
                  key={feat.title}
                  initial={{ x: -20, opacity: 0 }}
                  animate={{ x: 0, opacity: 1 }}
                  transition={{ delay: 0.3 + idx * 0.1, duration: 0.4 }}
                  className="cf-feature"
                >
                  <div className="cf-feature-icon">{feat.icon}</div>
                  <div className="cf-feature-text">
                    <h4>{feat.title}</h4>
                    <span>{feat.subtitle}</span>
                  </div>
                </motion.div>
              ))}
            </div>
          </div>
        </div>

        {/* ═══ RIGHT CONVERSATIONAL PANEL ═══ */}
        <div className="cf-right">
          <button className="cf-theme-toggle"
            onClick={() => setTheme(th => th === 'dark' ? 'light' : 'dark')}
            title={theme === 'dark' ? 'Mode clair' : 'Mode sombre'}
          >
            {theme === 'dark' ? <Sun size={18} /> : <Moon size={18} />}
          </button>

          <div className="cf-form-wrap">
            {success ? (
              <motion.div 
                initial={{ scale: 0.9, opacity: 0 }}
                animate={{ scale: 1, opacity: 1 }}
                className="cf-success"
              >
                <div className="cf-success-check"><Check size={32} /></div>
                <h3>Demande transmise !</h3>
                <p>Votre numéro de dossier :</p>
                <div className="cf-ticket-num">{success}</div>
                <p className="mt-2 text-txt-3">Un opérateur de votre agence prend en charge votre cas sous 30 minutes.</p>
                <button className="cf-success-btn" onClick={() => setSuccess(null)}>
                  Déclarer un autre sinistre
                </button>
              </motion.div>
            ) : (
              <>
                <div className="cf-form-header">
                  <div className="flex items-center gap-2 mb-1">
                    <Sparkles size={16} className="text-indigo-400 animate-pulse" />
                    <span className="text-xs uppercase font-extrabold tracking-widest text-indigo-400">Assistant AutoAssist</span>
                  </div>
                  <h2>Déclaration de sinistre</h2>
                  <p>Suivez les étapes guidées pour nous faire part de votre situation.</p>
                </div>

                {/* Progress bar */}
                <div className="cf-progress-container">
                  <div className="cf-progress-track">
                    <div className="cf-progress-fill" style={{ width: `${(currentStep + 1) * 25}%` }} />
                  </div>
                  <span className="cf-progress-text">Étape {currentStep + 1} sur 4</span>
                </div>

                {error && <div className="cf-error"><AlertTriangle size={15} /> {error}</div>}

                <form onSubmit={handleSubmit}>
                  <AnimatePresence mode="wait">
                    {currentStep === 0 && (
                      <StepCoordonnees
                        form={form}
                        setForm={setForm}
                        touched={touched}
                        setTouched={setTouched}
                        setFocusedField={setFocusedField}
                        getNomError={getNomError}
                        getTelError={getTelError}
                        getEmailError={getEmailError}
                        handleBlur={handleBlur}
                        handleTelChange={handleTelChange}
                        getFieldClassName={getFieldClassName}
                      />
                    )}

                    {currentStep === 1 && (
                      <StepNatureSinistre
                        selectedType={selectedType}
                        form={form}
                        setForm={setForm}
                        touched={touched}
                        setTouched={setTouched}
                        agences={agences}
                        handleTypeSelect={handleTypeSelect}
                      />
                    )}

                    {currentStep === 2 && (
                      <StepDescriptionPhotos
                        form={form}
                        setForm={setForm}
                        touched={touched}
                        setTouched={setTouched}
                        setFocusedField={setFocusedField}
                        getDescError={getDescError}
                        isFieldActive={isFieldActive}
                        handleBlur={handleBlur}
                        photoPreviews={photoPreviews}
                        handlePhotoSelect={handlePhotoSelect}
                        removePhoto={removePhoto}
                        themeBorder={t.border}
                      />
                    )}

                    {currentStep === 3 && (
                      <StepRecapitulatif
                        form={form}
                        setForm={setForm}
                        selectedType={selectedType}
                        agences={agences}
                        photoFiles={photoFiles}
                      />
                    )}
                  </AnimatePresence>

                  {/* Navigation controls */}
                  <div className="cf-controls">
                    {currentStep > 0 && (
                      <button type="button" onClick={handlePrev} className="cf-btn-prev">
                        <ArrowLeft size={14} /> Retour
                      </button>
                    )}
                    {currentStep < 3 ? (
                      <button type="button" onClick={handleNext} className="cf-btn-next">
                        Suivant <ArrowRight size={14} />
                      </button>
                    ) : (
                      <button type="submit" className="cf-btn-next" disabled={submitting}>
                        {submitting
                          ? 'Envoi de votre demande...'
                          : 'Confirmer & Envoyer ✓'}
                      </button>
                    )}
                  </div>
                </form>
              </>
            )}

            <div className="cf-footer">© 2026 AutoAssist · Plateforme d'Assistance Mutuelle</div>
          </div>
        </div>
      </div>
    </>
  );
}

/* ═══ THEME TOKENS ═══ */
const dk = {
  leftBg: 'linear-gradient(160deg, #090a0f 0%, #150f38 45%, #080614 100%)',
  rightBg: '#050508',
  textPrimary: '#f8fafc',
  textSecondary: '#94a3b8',
  textMuted: '#475569',
  inputBg: 'rgba(255,255,255,.03)',
  inputBgHover: 'rgba(255,255,255,.06)',
  inputBgFocus: 'rgba(255,255,255,.05)',
  border: 'rgba(255,255,255,.07)',
  accent: '#a78bfa',
  accentGlow: 'rgba(139,92,246,.1)',
  btnBg: 'linear-gradient(135deg, #8b5cf6 0%, #6366f1 100%)',
  btnShadow: '0 4px 20px rgba(139,92,246,.2)',
  btnShadowHover: '0 8px 30px rgba(139,92,246,.3)',
  optionBg: '#0b0b14',
  optionText: '#e2e8f0',
};

const lt = {
  leftBg: 'linear-gradient(160deg, #312e81 0%, #4338ca 45%, #4f46e5 100%)',
  rightBg: '#f8fafc',
  textPrimary: '#0f172a',
  textSecondary: '#475569',
  textMuted: '#cbd5e1',
  inputBg: '#ffffff',
  inputBgHover: '#f1f5f9',
  inputBgFocus: '#ffffff',
  border: '#e2e8f0',
  accent: '#4f46e5',
  accentGlow: 'rgba(79,70,229,.08)',
  btnBg: 'linear-gradient(135deg, #4f46e5 0%, #3730a3 100%)',
  btnShadow: '0 4px 18px rgba(79,70,229,.15)',
  btnShadowHover: '0 8px 28px rgba(79,70,229,.25)',
  optionBg: '#ffffff',
  optionText: '#0f172a',
};
