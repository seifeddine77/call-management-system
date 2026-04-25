import { useState, useEffect, useCallback } from 'react';
import { RefreshCw, Plus, AlertTriangle } from 'lucide-react';
import toast from 'react-hot-toast';
import { userService } from '../services/userService';
import { agenceService } from '../services/agenceService';
import Modal from '../components/ui/Modal';
import { ErrorState, EmptyState } from '../components/ui/States';
import type { User, UserRequest } from '../types/user';

interface FormErrors { prenom?: string; nom?: string; email?: string; password?: string; agenceId?: string; }
interface AgenceOption { id: string; nom: string; }

export default function UsersPage() {
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedUser, setSelectedUser] = useState<User | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [showEdit, setShowEdit] = useState(false);
  const [saving, setSaving] = useState(false);
  const [roleFilter, setRoleFilter] = useState('ALL');
  const [form, setForm] = useState<UserRequest>({ nom: '', prenom: '', email: '', password: '', role: 'OPERATEUR', agenceId: '', telephone: '', planning: '' });
  const [formErrors, setFormErrors] = useState<FormErrors>({});
  const [deleteTarget, setDeleteTarget] = useState<User | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [agenceNames, setAgenceNames] = useState<Record<string, string>>({});
  const [agenceOptions, setAgenceOptions] = useState<AgenceOption[]>([]);

  const fetchUsers = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await userService.getUsers({ role: roleFilter !== 'ALL' ? roleFilter : undefined });
      setUsers(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Erreur');
    } finally {
      setLoading(false);
    }
  }, [roleFilter]);

  useEffect(() => { fetchUsers(); }, [fetchUsers]);

  // Build agence UUID → name map for display + options for dropdown
  useEffect(() => {
    agenceService.getAgences({ size: 200 })
      .then(data => {
        const map: Record<string, string> = {};
        const opts: AgenceOption[] = [];
        (data.content || []).forEach(a => { map[a.id] = a.nom; opts.push({ id: a.id, nom: a.nom }); });
        setAgenceNames(map);
        setAgenceOptions(opts);
      })
      .catch(() => {});
  }, []);

  const validateForm = (isCreate: boolean): boolean => {
    const errors: FormErrors = {};
    if (!form.prenom || form.prenom.trim().length === 0) errors.prenom = 'Le prénom est obligatoire';
    if (!form.nom || form.nom.trim().length === 0) errors.nom = 'Le nom est obligatoire';
    if (!form.email || form.email.trim().length === 0) {
      errors.email = "L'email est obligatoire";
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) {
      errors.email = "Format d'email invalide";
    }
    if (isCreate) {
      if (!form.password || form.password.length === 0) {
        errors.password = 'Le mot de passe est obligatoire';
      } else if (form.password.length < 6) {
        errors.password = 'Minimum 6 caractères';
      }
    } else if (form.password && form.password.length > 0 && form.password.length < 6) {
      errors.password = 'Minimum 6 caractères';
    }
    // CDC 1.A — Agence obligatoire pour OPERATEUR et SUPERVISEUR
    if ((form.role === 'OPERATEUR' || form.role === 'SUPERVISEUR') && !form.agenceId) {
      errors.agenceId = `Un ${form.role === 'OPERATEUR' ? 'opérateur' : 'superviseur'} doit être rattaché à une agence`;
    }
    setFormErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleCreate = async () => {
    if (!validateForm(true)) return;
    setSaving(true);
    try {
      const created = await userService.createUser(form);
      setUsers(prev => [created, ...prev]);
      setShowCreate(false);
      setForm({ nom: '', prenom: '', email: '', password: '', role: 'OPERATEUR', agenceId: '', telephone: '', planning: '' });
      setFormErrors({});
      toast.success('Utilisateur créé !');
    } catch { toast.error('Erreur création'); }
    finally { setSaving(false); }
  };

  const handleUpdate = async () => {
    if (!selectedUser) return;
    if (!validateForm(false)) return;
    setSaving(true);
    try {
      const updated = await userService.updateUser(selectedUser.id, form);
      setUsers(prev => prev.map(u => u.id === selectedUser.id ? updated : u));
      setSelectedUser(updated);
      setShowEdit(false);
      setFormErrors({});
      toast.success('Utilisateur modifié');
    } catch { toast.error('Erreur modification'); }
    finally { setSaving(false); }
  };

  const handleDeleteConfirm = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      await userService.deleteUser(deleteTarget.id);
      setUsers(prev => prev.filter(u => u.id !== deleteTarget.id));
      if (selectedUser?.id === deleteTarget.id) setSelectedUser(null);
      toast.success('Utilisateur désactivé');
    } catch { toast.error('Erreur suppression'); }
    finally {
      setDeleting(false);
      setDeleteTarget(null);
    }
  };

  const roleBadge = (role: string) => ({
    ADMIN: { cls: 'bg-amber-500/10 text-amber-400 border-amber-500/20' },
    SUPERVISEUR: { cls: 'bg-violet-500/10 text-violet-400 border-violet-500/20' },
    OPERATEUR: { cls: 'bg-indigo-500/10 text-indigo-400 border-indigo-500/20' },
  }[role] || { cls: 'bg-[var(--muted)] text-txt-3 border-border-subtle' });

  const planningPresets = [
    { label: 'Temps plein', value: 'Lundi-Vendredi 08:00-17:00' },
    { label: 'Matin', value: 'Lundi-Vendredi 06:00-14:00' },
    { label: 'Après-midi', value: 'Lundi-Vendredi 14:00-22:00' },
    { label: 'Nuit', value: 'Lundi-Vendredi 22:00-06:00' },
    { label: 'Week-end', value: 'Samedi-Dimanche 08:00-18:00' },
    { label: 'Temps plein +', value: 'Lundi-Vendredi 08:00-17:00, Samedi 09:00-13:00' },
  ];

  if (error) return <ErrorState message={error} onRetry={fetchUsers} />;

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-extrabold tracking-tight">Utilisateurs</h1>
          <p className="text-xs text-txt-3 font-mono mt-0.5">{users.length} compte{users.length > 1 ? 's' : ''}</p>
        </div>
        <div className="flex items-center gap-2">
          <button onClick={fetchUsers} className="btn-ghost h-9 px-3 rounded-lg text-xs flex items-center gap-1.5 cursor-pointer" disabled={loading}>
            <RefreshCw size={12} className={loading ? 'animate-spin' : ''} />
          </button>
          <button onClick={() => { setForm({ nom: '', prenom: '', email: '', password: '', role: 'OPERATEUR', agenceId: '', telephone: '' }); setShowCreate(true); }} className="btn-cta h-9 px-4 rounded-lg text-xs text-white flex items-center gap-1.5 cursor-pointer">
            <Plus size={12} /> Inviter
          </button>
        </div>
      </div>

      {/* Filter */}
      <div className="flex gap-1.5">
        {['ALL', 'OPERATEUR', 'SUPERVISEUR', 'ADMIN'].map(r => (
          <button key={r} onClick={() => setRoleFilter(r)} className={`chip cursor-pointer ${roleFilter === r ? 'active' : ''}`}>
            {r === 'ALL' ? 'Tous' : r === 'OPERATEUR' ? 'Opérateurs' : r === 'SUPERVISEUR' ? 'Superviseurs' : 'Admins'}
          </button>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-[1fr_340px] gap-4">
        {/* List */}
        <div className="card-aurora rounded-2xl overflow-hidden">
          {loading ? (
            <div className="p-5 space-y-3 animate-pulse">{Array.from({ length: 5 }).map((_, i) => <div key={i} className="h-14 bg-[var(--muted)] rounded-lg" />)}</div>
          ) : users.length === 0 ? (
            <EmptyState icon="👤" title="Aucun utilisateur" subtitle="Aucun utilisateur trouvé" />
          ) : (
            users.map((u) => {
              const rb = roleBadge(u.role);
              const initials = `${(u.prenom || '')[0] || ''}${(u.nom || '')[0] || ''}`.toUpperCase();
              return (
                <div
                  key={u.id}
                  onClick={() => setSelectedUser(u)}
                  className={`flex items-center gap-3.5 px-5 py-3.5 border-b border-border cursor-pointer transition-all hover:bg-[var(--overlay)] ${selectedUser?.id === u.id ? 'bg-indigo-500/[0.04] border-l-2 border-l-indigo-500' : ''}`}
                >
                  <div className="w-10 h-10 rounded-xl flex items-center justify-center text-sm font-bold bg-indigo-500/10 text-indigo-400 flex-shrink-0">{initials}</div>
                  <div className="flex-1 min-w-0">
                    <div className="text-[13px] font-semibold">{u.prenom} {u.nom}</div>
                    <div className="font-mono text-[10px] text-txt-3">{u.email}</div>
                  </div>
                  <span className={`font-mono text-[9px] px-2.5 py-1 rounded-lg border ${rb.cls}`}>{u.role}</span>
                  <div className={`w-2 h-2 rounded-full flex-shrink-0 ${u.actif ? 'bg-emerald-400' : 'bg-zinc-500'}`} />
                </div>
              );
            })
          )}
        </div>

        {/* Detail */}
        <div className="flex flex-col gap-3 sticky top-6 h-max">
          {selectedUser ? (
            <>
              <div className="card-aurora rounded-2xl p-5 text-center">
                <div className="w-16 h-16 rounded-2xl mx-auto mb-3 flex items-center justify-center text-xl font-bold bg-gradient-to-br from-indigo-500 to-violet-400 text-white">
                  {`${(selectedUser.prenom || '')[0] || ''}${(selectedUser.nom || '')[0] || ''}`.toUpperCase()}
                </div>
                <div className="text-base font-extrabold">{selectedUser.prenom} {selectedUser.nom}</div>
                <div className="font-mono text-[10px] text-txt-3 mb-2">{selectedUser.email}</div>
                <span className={`font-mono text-[9px] px-2.5 py-1 rounded-lg border ${roleBadge(selectedUser.role).cls}`}>{selectedUser.role}</span>
                <div className="flex items-center justify-center gap-1.5 mt-2 font-mono text-[9px]">
                  <div className={`w-1.5 h-1.5 rounded-full ${selectedUser.actif ? 'bg-emerald-400' : 'bg-zinc-500'}`} />
                  <span className={selectedUser.actif ? 'text-emerald-400' : 'text-txt-3'}>{selectedUser.actif ? 'Actif' : 'Inactif'}</span>
                </div>
              </div>
              <div className="card-aurora rounded-2xl p-5">
                <h3 className="text-sm font-bold mb-3">Détails</h3>
                <div className="space-y-2">
                  {[
                    { label: 'ID', value: selectedUser.id },
                    { label: 'Téléphone', value: selectedUser.telephone || '—' },
                    { label: 'Agence', value: selectedUser.agenceId ? (agenceNames[selectedUser.agenceId] || selectedUser.agenceId) : '—' },
                    { label: 'Créé le', value: selectedUser.createdAt ? new Date(selectedUser.createdAt).toLocaleDateString('fr-FR') : '—' },
                  ].map(info => (
                    <div key={info.label} className="flex justify-between text-xs border-b border-border pb-2 last:border-0 last:pb-0">
                      <span className="text-txt-3">{info.label}</span>
                      <span className="font-mono text-[10px]">{info.value}</span>
                    </div>
                  ))}
                </div>
              </div>
              {/* Planning CDC Module 1 */}
              <div className="card-aurora rounded-2xl p-5">
                <h3 className="text-sm font-bold mb-3">📅 Planning</h3>
                {selectedUser.planning ? (
                  <div className="space-y-1.5">
                    {selectedUser.planning.split(',').map((segment, i) => {
                      const trimmed = segment.trim();
                      const [days, hours] = trimmed.split(' ');
                      return (
                        <div key={i} className="flex items-center gap-2 p-2 bg-[var(--elevated)] border border-border rounded-lg">
                          <span className="font-mono text-[9px] px-2 py-0.5 rounded bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">{days}</span>
                          <span className="text-xs font-medium">{hours || ''}</span>
                        </div>
                      );
                    })}
                  </div>
                ) : (
                  <div className="text-xs text-txt-3 text-center py-4">Aucun planning défini</div>
                )}
              </div>
              <div className="flex gap-2">
                <button onClick={() => { setForm({ nom: selectedUser.nom, prenom: selectedUser.prenom, email: selectedUser.email, password: '', role: selectedUser.role as 'OPERATEUR' | 'SUPERVISEUR' | 'ADMIN', agenceId: selectedUser.agenceId || '', telephone: selectedUser.telephone || '', planning: selectedUser.planning || '' }); setShowEdit(true); }} className="btn-ghost flex-1 h-9 rounded-lg text-xs cursor-pointer">✏ Modifier</button>
                <button onClick={() => setDeleteTarget(selectedUser)} className="flex-1 h-9 rounded-lg text-xs cursor-pointer bg-rose-500/10 text-rose-400 border border-rose-500/20 hover:bg-rose-500/20 transition-colors">🗑 Supprimer</button>
              </div>
            </>
          ) : (
            <div className="card-aurora rounded-2xl p-5">
              <EmptyState icon="👈" title="Sélectionnez un utilisateur" subtitle="Cliquez sur un utilisateur pour voir ses détails" />
            </div>
          )}
        </div>
      </div>

      {/* Create/Edit Modal */}
      <Modal open={showCreate || showEdit} onClose={() => { setShowCreate(false); setShowEdit(false); setFormErrors({}); }} title={showCreate ? 'Nouvel utilisateur' : 'Modifier utilisateur'}>
        <div className="space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="field-label block">Prénom *</label>
              <input className={`aurora-input ${formErrors.prenom ? 'border-rose-500/60' : ''}`} value={form.prenom} onChange={e => { setForm(p => ({ ...p, prenom: e.target.value })); setFormErrors(e2 => ({ ...e2, prenom: undefined })); }} />
              {formErrors.prenom && <span className="text-rose-400 text-[10px] mt-0.5 block">{formErrors.prenom}</span>}
            </div>
            <div>
              <label className="field-label block">Nom *</label>
              <input className={`aurora-input ${formErrors.nom ? 'border-rose-500/60' : ''}`} value={form.nom} onChange={e => { setForm(p => ({ ...p, nom: e.target.value })); setFormErrors(e2 => ({ ...e2, nom: undefined })); }} />
              {formErrors.nom && <span className="text-rose-400 text-[10px] mt-0.5 block">{formErrors.nom}</span>}
            </div>
          </div>
          <div>
            <label className="field-label block">Email *</label>
            <input className={`aurora-input ${formErrors.email ? 'border-rose-500/60' : ''}`} type="email" value={form.email} onChange={e => { setForm(p => ({ ...p, email: e.target.value })); setFormErrors(e2 => ({ ...e2, email: undefined })); }} />
            {formErrors.email && <span className="text-rose-400 text-[10px] mt-0.5 block">{formErrors.email}</span>}
          </div>
          {(showCreate || showEdit) && (
            <div>
              <label className="field-label block">{showCreate ? 'Mot de passe *' : 'Nouveau mot de passe (optionnel)'}</label>
              <input className={`aurora-input ${formErrors.password ? 'border-rose-500/60' : ''}`} type="password" placeholder={showEdit ? 'Laisser vide pour ne pas changer' : ''} value={form.password} onChange={e => { setForm(p => ({ ...p, password: e.target.value })); setFormErrors(e2 => ({ ...e2, password: undefined })); }} />
              {formErrors.password && <span className="text-rose-400 text-[10px] mt-0.5 block">{formErrors.password}</span>}
            </div>
          )}
          <div className="grid grid-cols-2 gap-3">
            <div><label className="field-label block">Rôle</label>
              <select className="aurora-select" value={form.role} onChange={e => setForm(p => ({ ...p, role: e.target.value as 'OPERATEUR' | 'SUPERVISEUR' | 'ADMIN' }))}>
                <option value="OPERATEUR">Opérateur</option><option value="SUPERVISEUR">Superviseur</option><option value="ADMIN">Admin</option>
              </select>
            </div>
            <div><label className="field-label block">Téléphone</label><input className="aurora-input" value={form.telephone} onChange={e => setForm(p => ({ ...p, telephone: e.target.value }))} /></div>
          </div>
          {/* CDC 1.A — Agence (obligatoire pour OPERATEUR/SUPERVISEUR) */}
          <div>
            <label className="field-label block">Agence {(form.role === 'OPERATEUR' || form.role === 'SUPERVISEUR') && <span className="text-rose-400">*</span>}</label>
            <select className={`aurora-select ${formErrors.agenceId ? 'border-rose-500/60' : ''}`} value={form.agenceId || ''} onChange={e => { setForm(p => ({ ...p, agenceId: e.target.value })); setFormErrors(e2 => ({ ...e2, agenceId: undefined })); }}>
              <option value="">-- Aucune agence --</option>
              {agenceOptions.map(a => (
                <option key={a.id} value={a.id}>{a.nom}</option>
              ))}
            </select>
            {formErrors.agenceId && <span className="text-rose-400 text-[10px] mt-0.5 block">{formErrors.agenceId}</span>}
          </div>
          {/* Planning */}
          <div>
            <label className="field-label block">📅 Planning</label>
            <div className="flex gap-1.5 flex-wrap mb-1.5">
              {planningPresets.map(p => (
                <button key={p.label} type="button" onClick={() => setForm(f => ({ ...f, planning: p.value }))} className={`text-[9px] font-mono px-2 py-0.5 rounded border cursor-pointer transition-colors ${form.planning === p.value ? 'bg-indigo-500/20 text-indigo-400 border-indigo-500/30' : 'text-txt-3 border-border hover:border-indigo-500/20'}`}>{p.label}</button>
              ))}
            </div>
            <input className="aurora-input" placeholder="Ex: Lundi-Vendredi 08:00-17:00" value={form.planning || ''} onChange={e => setForm(p => ({ ...p, planning: e.target.value }))} />
          </div>
          <div className="flex justify-end gap-2 pt-3 border-t border-border">
            <button onClick={() => { setShowCreate(false); setShowEdit(false); setFormErrors({}); }} className="btn-ghost h-9 px-4 rounded-lg text-xs cursor-pointer">Annuler</button>
            <button onClick={showCreate ? handleCreate : handleUpdate} disabled={saving} className="btn-cta h-9 px-4 rounded-lg text-xs text-white cursor-pointer disabled:opacity-50">
              {saving ? '...' : showCreate ? 'Créer' : 'Sauvegarder'}
            </button>
          </div>
        </div>
      </Modal>

      {/* Delete Confirmation Modal */}
      <Modal open={!!deleteTarget} onClose={() => setDeleteTarget(null)} title="Confirmer la suppression" size="sm">
        <div className="text-center">
          <div className="w-14 h-14 rounded-2xl mx-auto mb-3 flex items-center justify-center bg-rose-500/10 border border-rose-500/20">
            <AlertTriangle size={24} className="text-rose-400" />
          </div>
          <p className="text-sm font-semibold mb-1">Supprimer cet utilisateur ?</p>
          <p className="text-xs text-txt-3 mb-1">
            <span className="font-mono text-rose-400">{deleteTarget?.prenom} {deleteTarget?.nom}</span>
          </p>
          <p className="text-[11px] text-txt-3 mb-4">Cette action désactivera le compte. L'utilisateur ne pourra plus se connecter.</p>
          <div className="flex gap-2">
            <button onClick={() => setDeleteTarget(null)} className="btn-ghost flex-1 h-10 rounded-xl text-xs cursor-pointer">Annuler</button>
            <button onClick={handleDeleteConfirm} disabled={deleting} className="flex-1 h-10 rounded-xl text-xs font-semibold cursor-pointer bg-rose-500/20 text-rose-400 border border-rose-500/30 hover:bg-rose-500/30 transition-colors disabled:opacity-50">
              {deleting ? 'Suppression...' : '🗑 Supprimer'}
            </button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
