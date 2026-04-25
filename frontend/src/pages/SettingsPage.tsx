import { useState, useEffect } from 'react';
import toast from 'react-hot-toast';
import { useAuth } from '../contexts/AuthContext';
import { userService } from '../services/userService';
import { agenceService } from '../services/agenceService';

export default function SettingsPage() {
  const { user, logout } = useAuth();
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

  // resolve agence name
  useEffect(() => {
    if (user?.agenceId) {
      agenceService.getAgenceById(user.agenceId)
        .then(a => setMyAgenceName(a.nom))
        .catch(() => setMyAgenceName(user.agenceId || '—'));
    }
  }, [user?.agenceId]);

  const handleSaveProfile = async () => {
    setSaving(true);
    try {
      if (!user?.userId) throw new Error('Non authentifié');
      await userService.updateUser(user.userId, {
        prenom: profile.prenom,
        nom: profile.nom,
        email: profile.email,
        telephone: profile.telephone,
        role: user.role as 'OPERATEUR' | 'SUPERVISEUR' | 'ADMIN',
        password: '',
        agenceId: user.agenceId || undefined,
      });
      toast.success('Profil mis à jour !');
    } catch {
      toast.error('Erreur mise à jour profil');
    } finally {
      setSaving(false);
    }
  };

  const handleChangePassword = async () => {
    if (passwords.newPassword !== passwords.confirm) {
      toast.error('Les mots de passe ne correspondent pas');
      return;
    }
    if (passwords.newPassword.length < 6) {
      toast.error('Le mot de passe doit contenir au moins 6 caractères');
      return;
    }
    setSaving(true);
    try {
      if (!user?.userId) throw new Error('Non authentifié');
      await userService.changePassword(user.userId, {
        oldPassword: passwords.current,
        newPassword: passwords.newPassword,
      });
      toast.success('Mot de passe changé !');
      setPasswords({ current: '', newPassword: '', confirm: '' });
    } catch {
      toast.error('Erreur changement de mot de passe');
    } finally {
      setSaving(false);
    }
  };

  const tabs = ['👤 Profil', '🔒 Sécurité', '🔔 Notifications', '🎨 Apparence'];

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h1 className="text-xl font-extrabold tracking-tight">Paramètres</h1>
        <p className="text-xs text-txt-3 font-mono mt-0.5">Compte · Sécurité · Préférences</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-[220px_1fr] gap-4">
        {/* Sidebar */}
        <div className="card-aurora rounded-2xl overflow-hidden flex md:flex-col flex-row flex-wrap">
          {tabs.map((t, i) => (
            <button
              key={t}
              onClick={() => setActiveTab(i)}
              className={`w-full text-left px-4 py-3 text-[12.5px] font-medium border-b border-border last:border-0 transition-all cursor-pointer
                ${i === activeTab ? 'bg-indigo-500/[0.08] text-indigo-400 border-l-2 border-l-indigo-500' : 'text-txt-2 hover:bg-[var(--overlay)]'}`}
            >
              {t}
            </button>
          ))}
        </div>

        {/* Content */}
        <div className="flex flex-col gap-4">
          {activeTab === 0 && (
            <div className="card-aurora rounded-2xl p-5">
              <h3 className="text-sm font-bold mb-4 pb-3 border-b border-border">Informations du profil</h3>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mb-4">
                <div><label className="field-label block">Prénom</label><input className="aurora-input" value={profile.prenom} onChange={e => setProfile(p => ({ ...p, prenom: e.target.value }))} /></div>
                <div><label className="field-label block">Nom</label><input className="aurora-input" value={profile.nom} onChange={e => setProfile(p => ({ ...p, nom: e.target.value }))} /></div>
                <div><label className="field-label block">Email</label><input className="aurora-input" type="email" value={profile.email} onChange={e => setProfile(p => ({ ...p, email: e.target.value }))} /></div>
                <div><label className="field-label block">Téléphone</label><input className="aurora-input" value={profile.telephone} onChange={e => setProfile(p => ({ ...p, telephone: e.target.value }))} /></div>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mb-4">
                <div><label className="field-label block">Rôle</label><input className="aurora-input text-indigo-400" value={user?.role || '—'} readOnly /></div>
                <div><label className="field-label block">Agence</label><input className="aurora-input" value={myAgenceName} readOnly /></div>
              </div>
              <button onClick={handleSaveProfile} disabled={saving} className="btn-cta h-9 px-4 rounded-lg text-xs text-white cursor-pointer disabled:opacity-50">
                {saving ? 'Sauvegarde...' : 'Sauvegarder les modifications'}
              </button>
            </div>
          )}

          {activeTab === 1 && (
            <>
              <div className="card-aurora rounded-2xl p-5">
                <h3 className="text-sm font-bold mb-4 pb-3 border-b border-border">Changer le mot de passe</h3>
                <div className="space-y-3 max-w-[400px]">
                  <div><label className="field-label block">Mot de passe actuel</label><input className="aurora-input" type="password" value={passwords.current} onChange={e => setPasswords(p => ({ ...p, current: e.target.value }))} /></div>
                  <div><label className="field-label block">Nouveau mot de passe</label><input className="aurora-input" type="password" value={passwords.newPassword} onChange={e => setPasswords(p => ({ ...p, newPassword: e.target.value }))} /></div>
                  <div><label className="field-label block">Confirmer</label><input className="aurora-input" type="password" value={passwords.confirm} onChange={e => setPasswords(p => ({ ...p, confirm: e.target.value }))} /></div>
                  <button onClick={handleChangePassword} disabled={saving} className="btn-cta h-9 px-4 rounded-lg text-xs text-white cursor-pointer disabled:opacity-50">
                    {saving ? '...' : 'Changer le mot de passe'}
                  </button>
                </div>
              </div>
              <div className="card-aurora rounded-2xl p-5">
                <h3 className="text-sm font-bold mb-2 text-rose-400">Zone de danger</h3>
                <p className="text-xs text-txt-2 mb-3">Se déconnecter de l'application.</p>
                <button onClick={logout} className="h-9 px-4 rounded-lg text-xs cursor-pointer bg-rose-500/10 text-rose-400 border border-rose-500/20 hover:bg-rose-500/20 transition-colors">
                  🚪 Se déconnecter
                </button>
              </div>
            </>
          )}

          {activeTab === 2 && (
            <div className="card-aurora rounded-2xl p-5">
              <h3 className="text-sm font-bold mb-4 pb-3 border-b border-border">Préférences de notifications</h3>
              {[
                { label: 'Nouveau ticket entrant', sub: 'Notification sonore + badge', defaultOn: true },
                { label: 'Décision IA terminée', sub: "Alerte quand l'analyse est disponible", defaultOn: true },
                { label: 'Ticket urgent en attente', sub: 'Alerte si un ticket attend > 5 min', defaultOn: true },
                { label: 'Rapport quotidien par email', sub: 'Synthèse KPIs envoyée à 18h00', defaultOn: false },
              ].map((n) => (
                <div key={n.label} className="flex items-center justify-between py-3 border-b border-border last:border-0">
                  <div>
                    <div className="text-[13px] font-medium">{n.label}</div>
                    <div className="text-[11px] text-txt-2">{n.sub}</div>
                  </div>
                  <div className={`toggle-switch cursor-pointer ${n.defaultOn ? 'on' : ''}`} onClick={e => (e.currentTarget as HTMLElement).classList.toggle('on')} />
                </div>
              ))}
            </div>
          )}

          {activeTab === 3 && (
            <div className="card-aurora rounded-2xl p-5">
              <h3 className="text-sm font-bold mb-4 pb-3 border-b border-border">Apparence</h3>
              <p className="text-xs text-txt-2 mb-3">Le thème peut être changé via le bouton ☀/🌙 dans la barre supérieure.</p>
              <div className="grid grid-cols-2 gap-3">
                <div className="p-4 rounded-xl border border-border bg-[#F7F8FA] text-center cursor-pointer hover:border-indigo-500/30 transition-colors">
                  <div className="text-2xl mb-1">☀️</div>
                  <div className="text-xs font-medium text-gray-800">Mode jour</div>
                </div>
                <div className="p-4 rounded-xl border border-border bg-[#0D0F18] text-center cursor-pointer hover:border-indigo-500/30 transition-colors">
                  <div className="text-2xl mb-1">🌙</div>
                  <div className="text-xs font-medium text-gray-200">Mode nuit</div>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
