/**
 * Paramètres — compte personnel en self-service, accessible à TOUS les rôles.
 * Modifie son propre profil et son mot de passe via les endpoints `/api/users/me` (non gatés par rôle).
 * L'email est en lecture seule (identifiant de connexion). Source : userService (getMyProfile, etc.).
 */
import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import toast from 'react-hot-toast';
import { 
  User as UserIcon, Lock, Bell, Palette, LogOut, Sun, Moon, 
  Shield, Building, ToggleLeft, ToggleRight, CheckCircle2 
} from 'lucide-react';
import { useAuth } from '../contexts/AuthContext';
import { userService } from '../services/userService';
import { extractErrorMessage } from '../utils/errorUtils';

// ── Notification items defined outside component (no hooks inside map) ──
const NOTIFICATION_ITEMS = [
  { key: 'ticket_entrant',  label: 'Nouveau ticket incident entrant',             sub: 'Alerte sonore immédiate + signal visuel dans la file',                               defaultOn: true  },
  { key: 'decision_ia',    label: 'Décision IA et analyse de sinistre terminée', sub: "Signalement dès que la jauge de confiance s'illumine",                             defaultOn: true  },
  { key: 'sla_alert',      label: "Alerte d'embouteillage de support (SLA)",     sub: "Déclencher si un dossier attend en file depuis plus de 5 minutes",                  defaultOn: true  },
  { key: 'email_rapport',  label: "Rapport d'activité d'agence par courriel",    sub: "Synthèse des KPIs envoyée à votre adresse de secours à 18h00",                    defaultOn: false },
];

const DEFAULT_NOTIF_STATE: Record<string, boolean> = Object.fromEntries(
  NOTIFICATION_ITEMS.map(n => [n.key, n.defaultOn])
);

export default function SettingsPage() {
  const { user, logout, updateUserSession } = useAuth();
  const [activeTab, setActiveTab] = useState(0);
  const [saving, setSaving] = useState(false);

  // Profile form
  const [profile, setProfile] = useState({
    prenom: user?.prenom || '',
    nom: user?.nom || '',
    email: user?.email || '',
    telephone: '',
  });

  // Password form
  const [passwords, setPasswords] = useState({ current: '', newPassword: '', confirm: '' });
  const [myAgenceName, setMyAgenceName] = useState<string>('—');

  // Notifications (top-level state — fixing hook-in-map violation)
  const [notifStates, setNotifStates] = useState<Record<string, boolean>>(DEFAULT_NOTIF_STATE);

  const toggleNotif = (key: string) => {
    setNotifStates(prev => {
      const next = !prev[key];
      const item = NOTIFICATION_ITEMS.find(n => n.key === key);
      toast.success(`Alerte "${(item?.label ?? key).substring(0, 20)}..." ${next ? 'activée' : 'désactivée'}`);
      return { ...prev, [key]: next };
    });
  };

  // Load own profile via /me (self-service, accessible à tous les rôles y compris OPERATOR).
  // Le profil renvoie agenceName → plus besoin de l'appel getAgenceById (gaté SUP/ADMIN).
  useEffect(() => {
    userService.getMyProfile()
      .then(fullUser => {
        setProfile({
          prenom: fullUser.prenom || '',
          nom: fullUser.nom || '',
          email: fullUser.email || '',
          telephone: fullUser.telephone || '',
        });
        setMyAgenceName(fullUser.agenceName || '—');
      })
      .catch(() => {
        // Profile details failed to load — fallback to session data already in context
      });
  }, [user?.userId]);

  const handleSaveProfile = async () => {
    setSaving(true);
    try {
      if (!user?.userId) throw new Error('Non authentifié');
      await userService.updateMyProfile({
        prenom: profile.prenom,
        nom: profile.nom,
        email: profile.email,
        telephone: profile.telephone,
        role: user.role as 'OPERATEUR' | 'SUPERVISEUR' | 'ADMIN',
        password: '',
        agenceId: user.agenceId || undefined,
      });

      // Update local authentication session/context
      updateUserSession({
        prenom: profile.prenom,
        nom: profile.nom,
        email: profile.email,
      });

      toast.success('🎉 Profil personnel mis à jour avec succès !');
    } catch (err: unknown) {
      toast.error(extractErrorMessage(err, 'Erreur lors de la mise à jour du profil'));
    } finally {
      setSaving(false);
    }
  };

  const handleChangePassword = async () => {
    if (passwords.newPassword !== passwords.confirm) {
      toast.error('Les nouveaux mots de passe ne correspondent pas');
      return;
    }
    if (passwords.newPassword.length < 6) {
      toast.error('Le mot de passe doit contenir au moins 6 caractères');
      return;
    }
    setSaving(true);
    try {
      if (!user?.userId) throw new Error('Non authentifié');
      await userService.changeMyPassword({
        oldPassword: passwords.current,
        newPassword: passwords.newPassword,
      });
      toast.success('🔒 Mot de passe modifié avec succès !');
      setPasswords({ current: '', newPassword: '', confirm: '' });
    } catch (err: unknown) {
      toast.error(extractErrorMessage(err, 'Erreur lors du changement de mot de passe'));
    } finally {
      setSaving(false);
    }
  };

  const tabs = [
    { label: 'Profil personnel', icon: <UserIcon size={14} />, desc: 'Identité et agence de rattachement' },
    { label: 'Sécurité d\'accès', icon: <Lock size={14} />, desc: 'Mot de passe et habilitation' },
    { label: 'Alertes & Transmissions', icon: <Bell size={14} />, desc: 'Notifications sonores et push' },
    { label: 'Apparence système', icon: <Palette size={14} />, desc: 'Thèmes et préférences visuelles' },
  ];

  return (
    <div className="flex flex-col gap-6 max-w-[1600px] mx-auto w-full px-1 py-2">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-extrabold tracking-tight text-gradient-title">
          Préférences Générales
        </h1>
        <p className="text-xs text-txt-3 font-mono mt-1 flex items-center gap-1.5">
          <span className="w-1.5 h-1.5 rounded-full bg-indigo-500 animate-pulse mr-1" />{' '}
          Ajustement des paramètres système, profils d'accès et sécurité
        </p>
      </div>

      {/* Main split grid (Stripe-Style Tab Container) */}
      <div className="grid grid-cols-1 md:grid-cols-[260px_1fr] gap-6 items-start">
        {/* Navigation Sidebar */}
        <div className="card-aurora rounded-2xl border border-border bg-gradient-to-b from-[var(--surface)] to-[var(--elevated)] overflow-hidden shadow-lg p-2.5 flex flex-col gap-1">
          <div className="px-3.5 py-2 font-mono text-[9px] text-txt-3 uppercase tracking-widest font-extrabold mb-1">
            Menu Configuration
          </div>
          
          {tabs.map((t, i) => {
            const isActive = i === activeTab;
            return (
              <motion.button
                key={t.label}
                whileHover={{ x: 2 }}
                onClick={() => setActiveTab(i)}
                className={`w-full text-left px-3.5 py-3 rounded-xl text-[12.5px] font-medium transition-all cursor-pointer flex items-start gap-3 relative overflow-hidden
                  ${isActive 
                    ? 'bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 shadow-sm font-semibold' 
                    : 'text-txt-2 hover:bg-[var(--overlay)] border border-transparent'}`}
              >
                <span className={`p-1.5 rounded-lg border mt-0.5
                  ${isActive ? 'border-indigo-500/10 bg-indigo-500/5 text-indigo-400' : 'border-border/60 text-txt-3 bg-[var(--surface)]'}`}
                >
                  {t.icon}
                </span>
                <div className="min-w-0">
                  <div className={isActive ? 'text-indigo-600 dark:text-indigo-400 font-bold' : 'text-txt'}>{t.label}</div>
                  <div className="text-[10px] text-txt-3 font-normal mt-0.5 truncate">{t.desc}</div>
                </div>
              </motion.button>
            );
          })}
        </div>

        {/* Dynamic Panels */}
        <div className="flex flex-col gap-4">
          <AnimatePresence mode="wait">
            {activeTab === 0 && (
              <motion.div
                initial={{ opacity: 0, y: 5 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -5 }}
                transition={{ duration: 0.15 }}
                className="card-aurora rounded-2xl p-6 border border-border bg-gradient-to-b from-[var(--surface)] to-[var(--elevated)] shadow-xl space-y-5"
              >
                <div className="pb-3 border-b border-border/60 flex items-center justify-between">
                  <h3 className="text-sm font-bold text-txt tracking-wide">Fiche de Profil Collaborateur</h3>
                  <span className="font-mono text-[8px] font-extrabold uppercase tracking-widest text-indigo-400 bg-indigo-500/10 border border-indigo-500/20 px-2.5 py-0.5 rounded-full">
                    {user?.role}
                  </span>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <label htmlFor="settings-prenom" className="field-label block font-semibold text-[10px] uppercase tracking-wider mb-1.5 text-txt-3">Prénom</label>
                    <input id="settings-prenom" className="aurora-input" value={profile.prenom} onChange={e => setProfile(p => ({ ...p, prenom: e.target.value }))} />
                  </div>
                  <div>
                    <label htmlFor="settings-nom" className="field-label block font-semibold text-[10px] uppercase tracking-wider mb-1.5 text-txt-3">Nom</label>
                    <input id="settings-nom" className="aurora-input" value={profile.nom} onChange={e => setProfile(p => ({ ...p, nom: e.target.value }))} />
                  </div>
                  <div>
                    <label htmlFor="settings-email" className="field-label block font-semibold text-[10px] uppercase tracking-wider mb-1.5 text-txt-3">Email (identifiant — non modifiable)</label>
                    <input id="settings-email" className="aurora-input opacity-60 cursor-not-allowed" type="email" value={profile.email} readOnly title="L'email sert d'identifiant de connexion et n'est pas modifiable ici." />
                  </div>
                  <div>
                    <label htmlFor="settings-tel" className="field-label block font-semibold text-[10px] uppercase tracking-wider mb-1.5 text-txt-3">Téléphone Direct</label>
                    <input id="settings-tel" className="aurora-input" placeholder="Ex: 06 12 34 56 78" value={profile.telephone} onChange={e => setProfile(p => ({ ...p, telephone: e.target.value }))} />
                  </div>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-1 border-t border-border/40">
                  <div>
                    <span className="field-label block font-semibold text-[10px] uppercase tracking-wider mb-1.5 text-txt-3">Habilitation Système</span>
                    <div className="aurora-input bg-[var(--surface)] text-indigo-400 font-mono text-[10.5px] font-bold border-indigo-500/10 flex items-center gap-1.5 select-none">
                      <Shield size={12} />
                      {user?.role || '—'}
                    </div>
                  </div>
                  <div>
                    <span className="field-label block font-semibold text-[10px] uppercase tracking-wider mb-1.5 text-txt-3">Agence Principale</span>
                    <div className="aurora-input bg-[var(--surface)] text-txt-2 font-semibold flex items-center gap-1.5 select-none">
                      <Building size={12} className="text-txt-3" />
                      {myAgenceName}
                    </div>
                  </div>
                </div>

                <div className="flex justify-end pt-3 border-t border-border/50">
                  <motion.button 
                    whileHover={{ scale: 1.01 }}
                    whileTap={{ scale: 0.99 }}
                    onClick={handleSaveProfile} 
                    disabled={saving} 
                    className="btn-cta h-9.5 px-5 rounded-xl text-xs text-white cursor-pointer font-bold shadow-md bg-gradient-to-r from-indigo-500 to-violet-500 disabled:opacity-50"
                  >
                    {saving ? 'Sauvegarde...' : 'Sauvegarder les modifications'}
                  </motion.button>
                </div>
              </motion.div>
            )}

            {activeTab === 1 && (
              <motion.div
                initial={{ opacity: 0, y: 5 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -5 }}
                transition={{ duration: 0.15 }}
                className="space-y-4"
              >
                {/* Form Changer Mot de passe */}
                <div className="card-aurora rounded-2xl p-6 border border-border bg-gradient-to-b from-[var(--surface)] to-[var(--elevated)] shadow-xl space-y-4">
                  <div className="pb-3 border-b border-border/60">
                    <h3 className="text-sm font-bold text-txt tracking-wide">Modifier le Mot de passe</h3>
                  </div>

                  <div className="space-y-3.5 max-w-[440px]">
                    <div>
                      <label htmlFor="settings-curpwd" className="field-label block font-semibold text-[10px] uppercase tracking-wider mb-1.5 text-txt-3">Mot de passe actuel</label>
                      <input id="settings-curpwd" className="aurora-input" type="password" value={passwords.current} onChange={e => setPasswords(p => ({ ...p, current: e.target.value }))} />
                    </div>
                    <div>
                      <label htmlFor="settings-newpwd" className="field-label block font-semibold text-[10px] uppercase tracking-wider mb-1.5 text-txt-3">Nouveau mot de passe de sécurité</label>
                      <input id="settings-newpwd" className="aurora-input" type="password" placeholder="Minimum 6 caractères complexes" value={passwords.newPassword} onChange={e => setPasswords(p => ({ ...p, newPassword: e.target.value }))} />
                    </div>
                    <div>
                      <label htmlFor="settings-confirmpwd" className="field-label block font-semibold text-[10px] uppercase tracking-wider mb-1.5 text-txt-3">Confirmer le nouveau mot de passe</label>
                      <input id="settings-confirmpwd" className="aurora-input" type="password" value={passwords.confirm} onChange={e => setPasswords(p => ({ ...p, confirm: e.target.value }))} />
                    </div>
                    
                    <div className="pt-2">
                      <motion.button 
                        whileHover={{ scale: 1.01 }}
                        whileTap={{ scale: 0.99 }}
                        onClick={handleChangePassword} 
                        disabled={saving} 
                        className="btn-cta h-9.5 px-5 rounded-xl text-xs text-white cursor-pointer font-bold shadow-md bg-gradient-to-r from-indigo-500 to-violet-500 disabled:opacity-50"
                      >
                        {saving ? '...' : 'Changer le mot de passe'}
                      </motion.button>
                    </div>
                  </div>
                </div>

                {/* Danger zone */}
                <div className="card-aurora rounded-2xl p-6 border border-rose-500/10 bg-rose-500/[0.01] shadow-xl space-y-4">
                  <div className="pb-2 border-b border-rose-500/10">
                    <h3 className="text-sm font-bold text-rose-400 tracking-wide">Zone de sécurité critique</h3>
                  </div>
                  <p className="text-xs text-txt-2 max-w-[500px] leading-relaxed">
                    Déconnectez-vous de votre session en cours. Cette action videra vos jetons d'accès locaux.
                  </p>
                  
                  <motion.button 
                    whileHover={{ scale: 1.01 }}
                    whileTap={{ scale: 0.99 }}
                    onClick={logout} 
                    className="h-9.5 px-4.5 rounded-xl text-xs cursor-pointer bg-rose-500/10 text-rose-400 border border-rose-500/20 hover:bg-rose-500/20 transition-all flex items-center justify-center gap-1.5 font-bold"
                  >
                    <LogOut size={13} /> Se déconnecter de la console
                  </motion.button>
                </div>
              </motion.div>
            )}

            {activeTab === 2 && (
              <motion.div
                initial={{ opacity: 0, y: 5 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -5 }}
                transition={{ duration: 0.15 }}
                className="card-aurora rounded-2xl p-6 border border-border bg-gradient-to-b from-[var(--surface)] to-[var(--elevated)] shadow-xl space-y-5"
              >
                <div className="pb-3 border-b border-border/60">
                  <h3 className="text-sm font-bold text-txt tracking-wide">Abonnements de Notifications</h3>
                </div>

                <div className="divide-y divide-border/60">
                  {NOTIFICATION_ITEMS.map((n) => {
                    const isOn = notifStates[n.key];
                    return (
                      <div key={n.key} className="flex items-center justify-between py-4.5 first:pt-0 last:pb-0">
                        <div className="space-y-0.5">
                          <div className="text-[13px] font-semibold text-txt flex items-center gap-2">
                            {n.label}
                            {isOn && <span className="w-1 h-1 rounded-full bg-indigo-500 shadow-[0_0_5px_#6366f1]" />}
                          </div>
                          <div className="text-[11px] text-txt-3">{n.sub}</div>
                        </div>

                        <button
                          onClick={() => toggleNotif(n.key)}
                          className="cursor-pointer text-txt-3 hover:text-txt transition-all p-1"
                        >
                          {isOn ? (
                            <ToggleRight size={32} className="text-indigo-400 transition-all transform scale-110" />
                          ) : (
                            <ToggleLeft size={32} className="text-txt-3 transition-all" />
                          )}
                        </button>
                      </div>
                    );
                  })}
                </div>
              </motion.div>
            )}

            {activeTab === 3 && (
              <motion.div
                initial={{ opacity: 0, y: 5 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -5 }}
                transition={{ duration: 0.15 }}
                className="card-aurora rounded-2xl p-6 border border-border bg-gradient-to-b from-[var(--surface)] to-[var(--elevated)] shadow-xl space-y-5"
              >
                <div className="pb-3 border-b border-border/60">
                  <h3 className="text-sm font-bold text-txt tracking-wide">Personnalisation Apparence</h3>
                </div>
                
                <p className="text-xs text-txt-2 leading-relaxed">
                  Basculez le thème du centre d'assistance. Le mode sombre applique les filtres Aurora luminescents.
                </p>

                <div className="grid grid-cols-2 gap-4">
                  <motion.div 
                    whileHover={{ y: -2 }}
                    className="p-5 rounded-2xl border border-border/80 bg-[#F7F8FA] text-center cursor-pointer transition-colors shadow-sm hover:border-gray-400/30"
                  >
                    <div className="flex justify-center mb-2"><Sun size={26} className="text-amber-500 animate-spin animate-duration-10000" /></div>
                    <div className="text-xs font-bold text-gray-800 tracking-wide">Mode jour (Clair)</div>
                    <span className="font-mono text-[8px] text-gray-500 block mt-1">Luminosité standard</span>
                  </motion.div>
                  
                  <motion.div 
                    whileHover={{ y: -2 }}
                    className="p-5 rounded-2xl border border-indigo-500/20 bg-[#08090E] text-center cursor-pointer transition-colors shadow-md shadow-indigo-500/[0.02]"
                  >
                    <div className="flex justify-center mb-2"><Moon size={26} className="text-indigo-400" /></div>
                    <div className="text-xs font-bold text-txt tracking-wide">Mode nuit (Glow)</div>
                    <span className="font-mono text-[8px] text-indigo-400/80 font-extrabold block mt-1 flex items-center justify-center gap-0.5">
                      <CheckCircle2 size={8} /> SÉLECTIONNÉ
                    </span>
                  </motion.div>
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </div>
    </div>
  );
}
