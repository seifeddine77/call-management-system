import { useState, useEffect, useCallback } from 'react';
import { 
  RefreshCw, Plus, AlertTriangle, Building2, Pencil, Trash2, 
  MapPin, Phone, Mail, Package
} from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import toast from 'react-hot-toast';
import { agenceService } from '../services/agenceService';
import { userService } from '../services/userService';
import Modal from '../components/ui/Modal';
import { ErrorState, EmptyState } from '../components/ui/States';
import type { Agence, AgenceRequest } from '../types/agence';
import type { User as UserType } from '../types/user';

interface FormErrors { nom?: string; email?: string; }

export default function AgencesPage() {
  const [agences, setAgences] = useState<Agence[]>([]);
  const [users, setUsers] = useState<UserType[]>([]);
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
      setError(err instanceof Error ? err.message : 'Erreur de chargement');
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
      toast.success('🏢 Nouvelle agence enregistrée avec succès !');
    } catch { toast.error('Erreur lors de la création'); }
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
      toast.success('🏢 Agence mise à jour avec succès');
    } catch { toast.error('Erreur lors de la modification'); }
    finally { setSaving(false); }
  };

  const handleDeleteConfirm = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      await agenceService.deleteAgence(deleteTarget.id);
      setAgences(prev => prev.filter(a => a.id !== deleteTarget.id));
      toast.success('🔒 Agence archivée avec succès');
    } catch { toast.error('Erreur lors de la suppression'); }
    finally {
      setDeleting(false);
      setDeleteTarget(null);
    }
  };

  const openEdit = (a: Agence) => {
    setForm({ nom: a.nom, adresse: a.adresse || '', telephone: a.telephone || '', email: a.email || '', responsableId: a.responsableId || '', stockDescription: a.stockDescription || '' });
    setEditingId(a.id);
  };

  const getSaveButtonText = () => {
    if (saving) return 'Sauvegarde...';
    return showCreate ? "Créer l'agence" : 'Enregistrer';
  };

  const renderAgencesContent = () => {
    if (loading) {
      return (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {Array.from({ length: 3 }, (_, idx) => idx).map((idx) => (
            <div key={idx} className="h-60 bg-[var(--muted)]/40 border border-border/40 rounded-2xl animate-pulse" />
          ))}
        </div>
      );
    }
    if (agences.length === 0) {
      return (
        <div className="card-aurora rounded-2xl border border-border p-8">
          <EmptyState icon={<Building2 size={36} />} title="Aucune agence active" subtitle="Enregistrez votre première agence régionale pour organiser les interventions techniques." />
        </div>
      );
    }
    return (
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        <AnimatePresence mode="popLayout">
          {agences.map((a, idx) => {
            const initials = a.responsableName 
              ? a.responsableName.split(' ').map(n => n[0]).join('').toUpperCase()
              : '—';
              
            return (
              <motion.div
                key={a.id}
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, scale: 0.95 }}
                transition={{ duration: 0.25, delay: idx * 0.03 }}
                whileHover={{ y: -4, scale: 1.01 }}
                className="card-aurora rounded-2xl border border-border bg-gradient-to-b from-[var(--surface)] to-[var(--elevated)] overflow-hidden shadow-lg p-5 flex flex-col justify-between h-[280px] relative transition-shadow duration-300 hover:shadow-[0_10px_25px_-5px_rgba(0,0,0,0.2)]"
              >
                <div className="space-y-4">
                  {/* Card Top Title & Actions */}
                  <div className="flex items-start justify-between">
                    <div className="space-y-1">
                      <div className="font-semibold text-[15px] text-txt tracking-wide flex items-center gap-1.5">
                        <Building2 size={15} className="text-emerald-400" />
                        {a.nom}
                      </div>
                      <div className="text-[10.5px] text-txt-3 flex items-center gap-1">
                        <MapPin size={11} className="text-emerald-400/60" />
                        <span className="truncate max-w-[200px]">{a.adresse || 'Adresse non spécifiée'}</span>
                      </div>
                    </div>
                    
                    {/* Dropdown/Quick buttons */}
                    <div className="flex items-center gap-1 bg-[var(--surface)] border border-border rounded-xl p-0.5">
                      <button 
                        onClick={() => openEdit(a)} 
                        className="h-7 w-7 rounded-lg text-txt-3 hover:text-txt hover:bg-[var(--elevated)] transition-colors flex items-center justify-center cursor-pointer"
                      >
                        <Pencil size={11} />
                      </button>
                      <button 
                        onClick={() => setDeleteTarget(a)} 
                        className="h-7 w-7 rounded-lg text-rose-400 hover:bg-rose-500/10 transition-colors flex items-center justify-center cursor-pointer"
                      >
                        <Trash2 size={11} />
                      </button>
                    </div>
                  </div>

                  {/* Metadata contact grid */}
                  <div className="grid grid-cols-2 gap-3 pt-1 border-t border-border/40">
                    <div className="font-mono text-[9px] text-txt-3 tracking-wide space-y-0.5">
                      <div className="uppercase font-extrabold text-[8px] text-txt-3 flex items-center gap-1">
                        <Phone size={8} /> Téléphone
                      </div>
                      <div className="text-txt-2 text-[10px] font-semibold">{a.telephone || '—'}</div>
                    </div>
                    <div className="font-mono text-[9px] text-txt-3 tracking-wide space-y-0.5">
                      <div className="uppercase font-extrabold text-[8px] text-txt-3 flex items-center gap-1">
                        <Mail size={8} /> Email
                      </div>
                      <div className="text-txt-2 text-[10px] font-semibold truncate max-w-[120px]">{a.email || '—'}</div>
                    </div>
                  </div>

                  {/* Stock Description Badge */}
                  {a.stockDescription && (
                    <div className="bg-emerald-500/[0.03] border border-emerald-500/10 p-2.5 rounded-xl flex items-start gap-2">
                      <Package size={13} className="text-emerald-400 mt-0.5 flex-shrink-0" />
                      <div className="space-y-0.5 leading-none">
                        <span className="font-mono text-[8px] text-emerald-400 font-extrabold uppercase tracking-widest block">Matériel & Stock</span>
                        <span className="text-[10px] text-txt-2 font-medium line-clamp-1">{a.stockDescription}</span>
                      </div>
                    </div>
                  )}
                </div>

                {/* Card Bottom: Responsable & Availability metrics */}
                <div className="border-t border-border/40 pt-3 flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <div className="w-8 h-8 rounded-lg flex items-center justify-center text-[10.5px] font-bold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                      {initials}
                    </div>
                    <div className="leading-tight">
                      <span className="text-[9px] text-txt-3 font-mono block">RESPONSABLE</span>
                      <span className="text-[11px] font-semibold text-txt truncate max-w-[120px] block">
                        {a.responsableName || 'Non assigné'}
                      </span>
                    </div>
                  </div>
                  
                  {/* Simulated occupancy / active workload rating */}
                  <div className="text-right leading-tight">
                    <span className="text-[9px] text-txt-3 font-mono block">DISPONIBILITÉ</span>
                    <span className="text-[10px] font-mono font-bold text-emerald-400 bg-emerald-500/10 px-2 py-0.5 rounded-lg border border-emerald-500/20 inline-block mt-0.5">
                      100% OK
                    </span>
                  </div>
                </div>
              </motion.div>
            );
          })}
        </AnimatePresence>
      </div>
    );
  };

  if (error) return <ErrorState message={error} onRetry={fetchData} />;

  return (
    <div className="flex flex-col gap-6 max-w-[1600px] mx-auto w-full px-1 py-2">
      {/* Header */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-extrabold tracking-tight text-gradient-title">
            Console des Agences Régionales
          </h1>
          <p className="text-xs text-txt-3 font-mono mt-1 flex items-center gap-1.5">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse animate-duration-1000" />
            {agences.length} agence{agences.length > 1 ? 's' : ''} active{agences.length > 1 ? 's' : ''} sur le territoire
          </p>
        </div>
        
        <div className="flex items-center gap-2.5">
          <motion.button 
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
            onClick={fetchData} 
            className="btn-ghost h-9 w-9 p-0 rounded-xl text-xs flex items-center justify-center cursor-pointer border border-border bg-[var(--surface)] hover:bg-[var(--elevated)] shadow-sm" 
            disabled={loading}
          >
            <RefreshCw size={12} className={loading ? 'animate-spin text-indigo-400' : 'text-txt-3'} />
          </motion.button>
          
          <motion.button 
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
            onClick={() => { setForm({ nom: '', adresse: '', telephone: '', email: '', responsableId: '', stockDescription: '' }); setShowCreate(true); }} 
            className="btn-cta h-9 px-4 rounded-xl text-xs text-white flex items-center gap-1.5 cursor-pointer font-bold shadow-md bg-gradient-to-r from-emerald-500 to-teal-500"
          >
            <Plus size={13} /> Ajouter une agence
          </motion.button>
        </div>
      </div>

      {/* Grid Layout of Cards instead of boring table */}
      {renderAgencesContent()}

      {/* Create/Edit Modal */}
      <Modal open={showCreate || !!editingId} onClose={() => { setShowCreate(false); setEditingId(null); setFormErrors({}); }} title={showCreate ? 'Création d\'une Agence' : 'Modification de l\'Agence'}>
        <div className="space-y-4 pt-1">
          <div>
            <label htmlFor="agence-nom" className="field-label block font-semibold text-[10px] uppercase tracking-wider mb-1 text-txt-3">Nom de l'établissement *</label>
            <input id="agence-nom" className={`aurora-input ${formErrors.nom ? 'border-rose-500/60 shadow-[0_0_10px_rgba(239,68,68,0.05)]' : ''}`} placeholder="Ex: Agence Paris-Sud" value={form.nom} onChange={e => { setForm(p => ({ ...p, nom: e.target.value })); setFormErrors(e2 => ({ ...e2, nom: undefined })); }} />
            {formErrors.nom && <span className="text-rose-400 text-[10px] mt-1 block font-semibold">⚠️ {formErrors.nom}</span>}
          </div>
          <div>
            <label htmlFor="agence-adresse" className="field-label block font-semibold text-[10px] uppercase tracking-wider mb-1 text-txt-3">Adresse physique</label>
            <input id="agence-adresse" className="aurora-input" placeholder="Ex: 12 avenue des Champs-Élysées, 75008 Paris" value={form.adresse} onChange={e => setForm(p => ({ ...p, adresse: e.target.value }))} />
          </div>
          
          <div className="grid grid-cols-2 gap-3.5">
            <div>
              <label htmlFor="agence-tel" className="field-label block font-semibold text-[10px] uppercase tracking-wider mb-1 text-txt-3">Téléphone d'agence</label>
              <input id="agence-tel" className="aurora-input" placeholder="Ex: 01 45 67 89 10" value={form.telephone} onChange={e => setForm(p => ({ ...p, telephone: e.target.value }))} />
            </div>
            <div>
              <label htmlFor="agence-email" className="field-label block font-semibold text-[10px] uppercase tracking-wider mb-1 text-txt-3">Email de contact</label>
              <input id="agence-email" className={`aurora-input ${formErrors.email ? 'border-rose-500/60' : ''}`} type="email" placeholder="Ex: contact.paris@autoassist.fr" value={form.email} onChange={e => { setForm(p => ({ ...p, email: e.target.value })); setFormErrors(e2 => ({ ...e2, email: undefined })); }} />
              {formErrors.email && <span className="text-rose-400 text-[10px] mt-1 block font-semibold">⚠️ {formErrors.email}</span>}
            </div>
          </div>

          <div>
            <label htmlFor="agence-responsable" className="field-label block font-semibold text-[10px] uppercase tracking-wider mb-1 text-txt-3">Responsable désigné (Superviseurs uniquement)</label>
            <select id="agence-responsable" className="aurora-select font-semibold" value={form.responsableId || ''} onChange={e => setForm(p => ({ ...p, responsableId: e.target.value }))}>
              <option value="">-- Aucun responsable assigné --</option>
              {users.map(u => (
                <option key={u.id} value={u.id}>{u.prenom} {u.nom} (Superviseur)</option>
              ))}
            </select>
          </div>

          <div>
            <label htmlFor="agence-stock" className="field-label block font-semibold text-[10px] uppercase tracking-wider mb-1 text-txt-3">Description du stock de secours</label>
            <input id="agence-stock" className="aurora-input" placeholder="Ex: Pièces automobiles de rechange, kits de remorquage, routeurs LTE" value={form.stockDescription || ''} onChange={e => setForm(p => ({ ...p, stockDescription: e.target.value }))} />
          </div>

          <div className="flex justify-end gap-2.5 pt-4.5 border-t border-border mt-3">
            <button onClick={() => { setShowCreate(false); setEditingId(null); setFormErrors({}); }} className="btn-ghost h-9 px-4 rounded-xl text-xs cursor-pointer font-bold">
              Annuler
            </button>
            <motion.button 
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
              onClick={showCreate ? handleCreate : handleUpdate} 
              disabled={saving} 
              className="btn-cta h-9 px-5 rounded-xl text-xs text-white cursor-pointer font-bold bg-emerald-500 hover:bg-emerald-600 disabled:opacity-50 disabled:pointer-events-none"
            >
              {getSaveButtonText()}
            </motion.button>
          </div>
        </div>
      </Modal>

      {/* Delete/Archive Confirmation Modal */}
      <Modal open={!!deleteTarget} onClose={() => setDeleteTarget(null)} title="Archivage de l'établissement" size="sm">
        <div className="text-center space-y-4 py-2">
          <div className="w-14 h-14 rounded-2xl mx-auto flex items-center justify-center bg-rose-500/10 border border-rose-500/20 shadow-sm animate-bounce">
            <AlertTriangle size={24} className="text-rose-400" />
          </div>
          
          <div className="space-y-1">
            <p className="text-sm font-bold text-txt">Archiver cette agence ?</p>
            <p className="text-xs text-txt-3 font-semibold font-mono text-rose-400 bg-rose-500/5 py-1 px-2.5 border border-rose-500/10 rounded-xl max-w-[280px] mx-auto truncate">
              {deleteTarget?.nom}
            </p>
          </div>
          
          <p className="text-[11px] text-txt-3 max-w-[300px] mx-auto leading-relaxed">
            Cette mesure de sécurité désactivera l'agence et suspendra temporairement le rattachement de ses opérateurs. Les données resteront sauvegardées.
          </p>

          <div className="flex gap-2.5 pt-2">
            <button onClick={() => setDeleteTarget(null)} className="btn-ghost flex-1 h-10 rounded-xl text-xs cursor-pointer font-bold border border-border">
              Annuler
            </button>
            <button 
              onClick={handleDeleteConfirm} 
              disabled={deleting} 
              className="flex-1 h-10 rounded-xl text-xs font-bold cursor-pointer bg-rose-500/20 text-rose-400 border border-rose-500/30 hover:bg-rose-500/30 transition-all disabled:opacity-50"
            >
              {deleting ? 'Archivage...' : 'Archiver'}
            </button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
