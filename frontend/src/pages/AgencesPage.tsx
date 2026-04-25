import { useState, useEffect, useCallback } from 'react';
import { RefreshCw, Plus, AlertTriangle } from 'lucide-react';
import toast from 'react-hot-toast';
import { agenceService } from '../services/agenceService';
import { userService } from '../services/userService';
import Modal from '../components/ui/Modal';
import { ErrorState, EmptyState, TableSkeleton } from '../components/ui/States';
import type { Agence, AgenceRequest } from '../types/agence';
import type { User } from '../types/user';

interface FormErrors { nom?: string; email?: string; }

export default function AgencesPage() {
  const [agences, setAgences] = useState<Agence[]>([]);
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState<AgenceRequest>({ nom: '', adresse: '', telephone: '', email: '', responsableId: '', stockDescription: '' });
  const [formErrors, setFormErrors] = useState<FormErrors>({});
  const [deleteTarget, setDeleteTarget] = useState<Agence | null>(null);
  const [deleting, setDeleting] = useState(false);

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [agencesData, usersData] = await Promise.all([
        agenceService.getAgences(),
        userService.getUsers()
      ]);
      setAgences(agencesData.content);
      // CDC 1.B — Seuls les SUPERVISEURS peuvent être responsables d'agence
      setUsers(usersData.filter(u => ['SUPERVISOR', 'SUPERVISEUR'].includes(u.role)));
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Erreur');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchData(); }, [fetchData]);

  const validateForm = (): boolean => {
    const errors: FormErrors = {};
    if (!form.nom || form.nom.trim().length === 0) errors.nom = "Le nom de l'agence est obligatoire";
    if (form.nom && form.nom.length > 200) errors.nom = "Le nom ne peut pas dépasser 200 caractères";
    
    if (form.email && form.email.trim().length > 0) {
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) {
        errors.email = "Format d'email invalide";
      }
    }
    setFormErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleCreate = async () => {
    if (!validateForm()) return;
    setSaving(true);
    try {
      let created = await agenceService.createAgence(form);
      if (form.responsableId) {
        try {
          created = await agenceService.assignResponsable(created.id, form.responsableId);
        } catch { toast.error("L'agence est créée mais l'assignation du responsable a échoué"); }
      }
      setAgences(prev => [...prev, created]);
      setShowCreate(false);
      setForm({ nom: '', adresse: '', telephone: '', email: '', responsableId: '', stockDescription: '' });
      setFormErrors({});
      toast.success('Agence créée !');
    } catch { toast.error('Erreur création'); }
    finally { setSaving(false); }
  };

  const handleUpdate = async () => {
    if (!editingId) return;
    if (!validateForm()) return;
    setSaving(true);
    try {
      let updated = await agenceService.updateAgence(editingId, form);
      const originalAgence = agences.find(a => a.id === editingId);
      if (form.responsableId && form.responsableId !== originalAgence?.responsableId) {
        try {
          updated = await agenceService.assignResponsable(updated.id, form.responsableId);
        } catch { toast.error("Mise à jour réussie mais l'assignation du responsable a échoué"); }
      }
      setAgences(prev => prev.map(a => a.id === editingId ? updated : a));
      setEditingId(null);
      setFormErrors({});
      toast.success('Agence modifiée');
    } catch { toast.error('Erreur modification'); }
    finally { setSaving(false); }
  };

  const handleDeleteConfirm = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      await agenceService.deleteAgence(deleteTarget.id);
      setAgences(prev => prev.filter(a => a.id !== deleteTarget.id));
      toast.success('Agence désactivée');
    } catch { toast.error('Erreur suppression'); }
    finally {
      setDeleting(false);
      setDeleteTarget(null);
    }
  };

  const openEdit = (a: Agence) => {
    setForm({ nom: a.nom, adresse: a.adresse || '', telephone: a.telephone || '', email: a.email || '', responsableId: a.responsableId || '', stockDescription: a.stockDescription || '' });
    setEditingId(a.id);
  };

  if (error) return <ErrorState message={error} onRetry={fetchData} />;

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2">
        <div>
          <h1 className="text-xl font-extrabold tracking-tight">Agences</h1>
          <p className="text-xs text-txt-3 font-mono mt-0.5">{agences.length} agence{agences.length > 1 ? 's' : ''}</p>
        </div>
        <div className="flex items-center gap-2">
          <button onClick={fetchData} className="btn-ghost h-9 px-3 rounded-lg text-xs flex items-center gap-1.5 cursor-pointer" disabled={loading}>
            <RefreshCw size={12} className={loading ? 'animate-spin' : ''} />
          </button>
          <button onClick={() => { setForm({ nom: '', adresse: '', telephone: '', email: '', responsableId: '', stockDescription: '' }); setShowCreate(true); }} className="btn-cta h-9 px-4 rounded-lg text-xs text-white flex items-center gap-1.5 cursor-pointer">
            <Plus size={12} /> Nouvelle agence
          </button>
        </div>
      </div>

      {loading ? <TableSkeleton rows={4} cols={5} /> : agences.length === 0 ? (
        <div className="card-aurora rounded-2xl"><EmptyState icon="🏢" title="Aucune agence" subtitle="Créez votre première agence" /></div>
      ) : (
        <div className="card-aurora rounded-2xl overflow-hidden">
          <div className="table-scroll-wrapper">
          <table className="aurora-table">
            <thead><tr><th>Nom</th><th>Adresse</th><th>Téléphone</th><th>Email</th><th>Responsable</th><th></th></tr></thead>
            <tbody>
              {agences.map((a) => (
                <tr key={a.id}>
                  <td className="font-semibold">{a.nom}</td>
                  <td className="text-xs text-txt-2">{a.adresse || '—'}</td>
                  <td className="font-mono text-xs text-txt-2">{a.telephone || '—'}</td>
                  <td className="font-mono text-xs text-txt-3">{a.email || '—'}</td>
                  <td className="text-xs">{a.responsableName || '—'}</td>
                  <td>
                    <div className="flex gap-1">
                      <button onClick={() => openEdit(a)} className="btn-ghost h-7 px-2.5 rounded text-[10px] cursor-pointer">✏</button>
                      <button onClick={() => setDeleteTarget(a)} className="h-7 px-2.5 rounded text-[10px] cursor-pointer text-rose-400 hover:bg-rose-500/10 transition-colors">🗑</button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          </div>
        </div>
      )}

      {/* Create/Edit Modal */}
      <Modal open={showCreate || !!editingId} onClose={() => { setShowCreate(false); setEditingId(null); setFormErrors({}); }} title={showCreate ? 'Nouvelle agence' : 'Modifier agence'}>
        <div className="space-y-3">
          <div>
            <label className="field-label block">Nom *</label>
            <input className={`aurora-input ${formErrors.nom ? 'border-rose-500/60' : ''}`} value={form.nom} onChange={e => { setForm(p => ({ ...p, nom: e.target.value })); setFormErrors(e2 => ({ ...e2, nom: undefined })); }} />
            {formErrors.nom && <span className="text-rose-400 text-[10px] mt-0.5 block">{formErrors.nom}</span>}
          </div>
          <div><label className="field-label block">Adresse</label><input className="aurora-input" value={form.adresse} onChange={e => setForm(p => ({ ...p, adresse: e.target.value }))} /></div>
          <div className="grid grid-cols-2 gap-3">
            <div><label className="field-label block">Téléphone</label><input className="aurora-input" value={form.telephone} onChange={e => setForm(p => ({ ...p, telephone: e.target.value }))} /></div>
            <div>
              <label className="field-label block">Email</label>
              <input className={`aurora-input ${formErrors.email ? 'border-rose-500/60' : ''}`} type="email" value={form.email} onChange={e => { setForm(p => ({ ...p, email: e.target.value })); setFormErrors(e2 => ({ ...e2, email: undefined })); }} />
              {formErrors.email && <span className="text-rose-400 text-[10px] mt-0.5 block">{formErrors.email}</span>}
            </div>
          </div>
          <div>
            <label className="field-label block">Responsable (Superviseur uniquement)</label>
            <select className="aurora-select" value={form.responsableId || ''} onChange={e => setForm(p => ({ ...p, responsableId: e.target.value }))}>
              <option value="">-- Aucun responsable --</option>
              {users.map(u => (
                <option key={u.id} value={u.id}>{u.prenom} {u.nom} ({u.role})</option>
              ))}
            </select>
          </div>
          <div><label className="field-label block">Description du stock</label><input className="aurora-input" placeholder="Ex: Pièces détachées, routeurs, etc." value={form.stockDescription || ''} onChange={e => setForm(p => ({ ...p, stockDescription: e.target.value }))} /></div>
          <div className="flex justify-end gap-2 pt-3 border-t border-border">
            <button onClick={() => { setShowCreate(false); setEditingId(null); setFormErrors({}); }} className="btn-ghost h-9 px-4 rounded-lg text-xs cursor-pointer">Annuler</button>
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
          <p className="text-sm font-semibold mb-1">Supprimer cette agence ?</p>
          <p className="text-xs text-txt-3 mb-1">
            <span className="font-mono text-rose-400">{deleteTarget?.nom}</span>
          </p>
          <p className="text-[11px] text-txt-3 mb-4">Cette action désactivera l'agence et bloquera l'accès à ses utilisateurs.</p>
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
