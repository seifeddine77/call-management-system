/**
 * Gestion des utilisateurs (SUPERVISOR / ADMIN). Création et désactivation réservées à l'ADMIN ;
 * un SUPERVISOR ne voit et n'édite que les comptes de son agence. Source : userService.
 */
import { useState, useEffect, useCallback, useMemo } from 'react';
import {
  RefreshCw, Plus, AlertTriangle, Pencil, Trash2, CalendarDays,
  User as UserIcon, Mail, Phone, Shield, Building, Clock,
  Search, ChevronLeft, ChevronRight
} from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import toast from 'react-hot-toast';
import { userService } from '../services/userService';
import { agenceService } from '../services/agenceService';
import { useAuth } from '../contexts/AuthContext';
import Modal from '../components/ui/Modal';
import { ErrorState, EmptyState } from '../components/ui/States';
import type { User, UserRequest } from '../types/user';

interface FormErrors { prenom?: string; nom?: string; email?: string; password?: string; agenceId?: string; }
interface AgenceOption { id: string; nom: string; }

/* ───── Module-level helpers ───── */

function validateUserForm(
  form: import('../types/user').UserRequest,
  isCreate: boolean
): FormErrors {
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
  if ((form.role === 'OPERATEUR' || form.role === 'SUPERVISEUR') && !form.agenceId) {
    const label = form.role === 'OPERATEUR' ? 'opérateur' : 'superviseur';
    errors.agenceId = `Un ${label} doit être rattaché à une agence`;
  }
  return errors;
}

interface UserRowProps {
  u: User;
  rb: { cls: string; avatar: string };
  initials: string;
  isSelected: boolean;
  onSelect: () => void;
}

function UserRow({ u, rb, initials, isSelected, onSelect }: Readonly<UserRowProps>) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 5 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, x: -10 }}
      role="button"
      tabIndex={0}
      onClick={onSelect}
      onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onSelect(); } }}
      className={`group flex items-center justify-between gap-4 px-5 py-3.5 cursor-pointer transition-all duration-300 hover:bg-indigo-500/[0.02] relative overflow-hidden
        ${isSelected ? 'bg-indigo-500/[0.04] before:absolute before:left-0 before:top-0 before:bottom-0 before:w-[3px] before:bg-indigo-500' : ''}`}
    >
      <div className="flex items-center gap-3.5 flex-1 min-w-0">
        {/* Avatar */}
        <div className={`w-10 h-10 rounded-xl flex items-center justify-center text-[12px] font-extrabold bg-gradient-to-br border border-border-subtle flex-shrink-0 transition-transform group-hover:scale-105 duration-300 ${rb.avatar}`}>
          {initials}
        </div>

        <div className="min-w-0">
          <div className="text-[13px] font-semibold text-txt tracking-wide group-hover:text-indigo-400 transition-colors">
            {u.prenom} {u.nom}
          </div>
          <div className="font-mono text-[9.5px] text-txt-3 mt-0.5 tracking-wider flex items-center gap-1.5">
            <Mail size={10} className="text-txt-3" />
            {u.email}
          </div>
        </div>
      </div>

      {/* Right metadata badges */}
      <div className="flex items-center gap-3.5">
        <span className={`font-mono text-[8.5px] font-extrabold uppercase tracking-widest px-2.5 py-1 rounded-xl border ${rb.cls}`}>
          {u.role}
        </span>
        
        <div className="flex items-center gap-1.5 font-mono text-[9px] bg-[var(--surface)] border border-border px-2.5 py-1 rounded-xl">
          <span className={`w-1.5 h-1.5 rounded-full ${u.actif ? 'bg-emerald-400 animate-pulse' : 'bg-zinc-500'}`} />
          <span className={u.actif ? 'text-emerald-400' : 'text-txt-3 font-semibold'}>
            {u.actif ? 'ACTIF' : 'INACTIF'}
          </span>
        </div>
      </div>
    </motion.div>
  );
}

interface UserDetailPanelProps {
  selectedUser: User;
  rb: { cls: string; avatar: string };
  agenceNames: Record<string, string>;
  onEdit: () => void;
  onDelete: () => void;
  canDelete: boolean;
}

function UserDetailPanel({ selectedUser, rb, agenceNames, onEdit, onDelete, canDelete }: Readonly<UserDetailPanelProps>) {
  return (
    <motion.div
      initial={{ opacity: 0, scale: 0.98 }}
      animate={{ opacity: 1, scale: 1 }}
      exit={{ opacity: 0, scale: 0.98 }}
      transition={{ duration: 0.2 }}
      className="space-y-4"
    >
      {/* Visual Avatar Card */}
      <div className="card-aurora rounded-2xl p-6 text-center border border-border bg-gradient-to-b from-[var(--surface)] to-[var(--elevated)] relative overflow-hidden shadow-lg">
        <div className="absolute top-0 left-0 right-0 h-[2.5px] bg-gradient-to-r from-indigo-500 via-purple-500 to-pink-500" />
        
        <div className="w-16 h-16 rounded-2xl mx-auto mb-4 flex items-center justify-center text-xl font-bold bg-gradient-to-br from-indigo-500/30 to-violet-500/10 text-indigo-300 border border-indigo-500/20 shadow-md">
          {`${(selectedUser.prenom || '')[0] || ''}${(selectedUser.nom || '')[0] || ''}`.toUpperCase()}
        </div>
        
        <div className="text-base font-extrabold text-txt tracking-wide">{selectedUser.prenom} {selectedUser.nom}</div>
        <div className="font-mono text-[10px] text-txt-3 mt-0.5 tracking-wider">{selectedUser.email}</div>
        
        <div className="flex items-center justify-center gap-2 mt-3.5">
          <span className={`font-mono text-[8.5px] font-extrabold uppercase tracking-widest px-2.5 py-1 rounded-xl border ${rb.cls}`}>
            {selectedUser.role}
          </span>
          <span className={`font-mono text-[8.5px] font-extrabold uppercase tracking-widest px-2.5 py-1 rounded-xl border flex items-center gap-1
            ${selectedUser.actif ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' : 'bg-zinc-500/10 text-zinc-400 border-zinc-500/20'}`}
          >
            <span className={`w-1 h-1 rounded-full ${selectedUser.actif ? 'bg-emerald-400' : 'bg-zinc-500'}`} />
            {selectedUser.actif ? 'Actif' : 'Inactif'}
          </span>
        </div>
      </div>

      {/* Metadata Details Card */}
      <div className="card-aurora rounded-2xl p-5 border border-border bg-[var(--surface)] shadow-md space-y-4">
        <div className="font-mono text-[9px] text-txt-3 uppercase tracking-widest font-extrabold pb-2 border-b border-border/60">
          Fiche d'identité
        </div>
        
        <div className="space-y-3">
          {[
            { label: 'Identifiant UUID', value: selectedUser.id, icon: <Shield size={12} className="text-indigo-400" /> },
            { label: 'Téléphone direct', value: selectedUser.telephone || '—', icon: <Phone size={12} className="text-indigo-400" /> },
            { label: 'Agence assignée', value: selectedUser.agenceId ? (agenceNames[selectedUser.agenceId] || selectedUser.agenceId) : '—', icon: <Building size={12} className="text-indigo-400" /> },
            { label: 'Date de création', value: selectedUser.createdAt ? new Date(selectedUser.createdAt).toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' }) : '—', icon: <Clock size={12} className="text-indigo-400" /> },
          ].map(info => (
            <div key={info.label} className="flex justify-between items-start text-xs border-b border-border/40 pb-2.5 last:border-0 last:pb-0">
              <span className="text-txt-3 flex items-center gap-1.5 font-medium">
                {info.icon}
                {info.label}
              </span>
              <span className="font-mono text-[10px] text-txt font-semibold text-right max-w-[180px] truncate select-all">{info.value}</span>
            </div>
          ))}
        </div>
      </div>

      {/* Planning presets / Calendar */}
      <div className="card-aurora rounded-2xl p-5 border border-border bg-[var(--surface)] shadow-md">
        <div className="font-mono text-[9px] text-txt-3 uppercase tracking-widest font-extrabold pb-2.5 border-b border-border/60 flex items-center justify-between">
          <span className="flex items-center gap-1.5"><CalendarDays size={13} className="text-indigo-400" /> Planning Hebdomadaire</span>
        </div>
        
        <div className="mt-3">
          {selectedUser.planning ? (
            <div className="space-y-2">
              {selectedUser.planning.split(',').map((segment: string) => {
                const trimmed = segment.trim();
                const [days, hours] = trimmed.split(' ');
                return (
                  <div key={`planning-segment-${trimmed}`} className="flex items-center justify-between p-2.5 bg-[var(--elevated)] border border-border/60 rounded-xl hover:border-indigo-500/20 transition-all duration-200">
                    <span className="font-mono text-[9.5px] font-bold px-2 py-0.5 rounded-lg bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">{days}</span>
                    <span className="text-xs font-semibold text-txt tracking-wide">{hours || ''}</span>
                  </div>
                );
              })}
            </div>
          ) : (
            <div className="text-xs text-txt-3 text-center py-5 font-mono italic">Aucun planning hebdomadaire défini</div>
          )}
        </div>
      </div>

      {/* Operations Buttons */}
      <div className="flex gap-2.5">
        <motion.button 
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.98 }}
          onClick={onEdit} 
          className="btn-ghost flex-1 h-9.5 rounded-xl text-xs cursor-pointer flex items-center justify-center gap-1.5 border border-border bg-[var(--surface)] hover:bg-[var(--elevated)] hover:text-txt transition-all font-bold"
        >
          <Pencil size={12} className="text-txt-2" /> Modifier
        </motion.button>
        {canDelete && (
          <motion.button
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
            onClick={onDelete}
            className="flex-1 h-9.5 rounded-xl text-xs cursor-pointer bg-rose-500/10 text-rose-400 border border-rose-500/20 hover:bg-rose-500/20 transition-all font-bold flex items-center justify-center gap-1.5"
          >
            <Trash2 size={12} /> Désactiver
          </motion.button>
        )}
      </div>
    </motion.div>
  );
}

/* ─── Users State Handlers (Extracted to module level for reduced cognitive complexity) ─── */

async function fetchUsersData(
  roleFilter: string,
  setLoading: (l: boolean) => void,
  setError: (e: string | null) => void,
  setUsers: (users: User[]) => void
) {
  setLoading(true);
  setError(null);
  try {
    const data = await userService.getUsers({ role: roleFilter !== 'ALL' ? roleFilter : undefined });
    setUsers(data);
  } catch (err: unknown) {
    setError(err instanceof Error ? err.message : 'Erreur de chargement');
  } finally {
    setLoading(false);
  }
}

async function createUserProfile(
  form: UserRequest,
  setSaving: (v: boolean) => void,
  setUsers: React.Dispatch<React.SetStateAction<User[]>>,
  setShowCreate: (v: boolean) => void,
  setForm: (f: UserRequest) => void,
  setFormErrors: (e: FormErrors) => void,
  validateForm: (isCreate: boolean) => boolean
) {
  if (!validateForm(true)) return;
  setSaving(true);
  try {
    const created = await userService.createUser(form);
    setUsers(prev => [created, ...prev]);
    setShowCreate(false);
    setForm({ nom: '', prenom: '', email: '', password: '', role: 'OPERATEUR', agenceId: '', telephone: '', planning: '' });
    setFormErrors({});
    toast.success('🎉 Utilisateur créé et invité avec succès !');
  } catch {
    toast.error('Erreur lors de la création');
  } finally {
    setSaving(false);
  }
}

interface UpdateUserProfileParams {
  selectedUserId: string;
  form: UserRequest;
  setSaving: (v: boolean) => void;
  setUsers: React.Dispatch<React.SetStateAction<User[]>>;
  setSelectedUser: (u: User) => void;
  setShowEdit: (v: boolean) => void;
  setFormErrors: (e: FormErrors) => void;
  validateForm: (isCreate: boolean) => boolean;
}

async function updateUserProfile(params: UpdateUserProfileParams) {
  const {
    selectedUserId,
    form,
    setSaving,
    setUsers,
    setSelectedUser,
    setShowEdit,
    setFormErrors,
    validateForm
  } = params;
  if (!validateForm(false)) return;
  setSaving(true);
  try {
    const updated = await userService.updateUser(selectedUserId, form);
    setUsers(prev => prev.map(u => u.id === selectedUserId ? updated : u));
    setSelectedUser(updated);
    setShowEdit(false);
    setFormErrors({});
    toast.success('👤 Utilisateur mis à jour !');
  } catch {
    toast.error('Erreur lors de la modification');
  } finally {
    setSaving(false);
  }
}

async function deleteUserProfile(
  deleteTarget: User,
  selectedUserId: string | undefined,
  setDeleting: (v: boolean) => void,
  setUsers: React.Dispatch<React.SetStateAction<User[]>>,
  setSelectedUser: (u: User | null) => void,
  setDeleteTarget: (u: User | null) => void
) {
  setDeleting(true);
  try {
    await userService.deleteUser(deleteTarget.id);
    setUsers(prev => prev.filter(u => u.id !== deleteTarget.id));
    if (selectedUserId === deleteTarget.id) setSelectedUser(null);
    toast.success('🔒 Utilisateur désactivé avec succès');
  } catch {
    toast.error('Erreur lors de la suppression');
  } finally {
    setDeleting(false);
    setDeleteTarget(null);
  }
}

function useUsersState() {
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
    await fetchUsersData(roleFilter, setLoading, setError, setUsers);
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
    const errors = validateUserForm(form, isCreate);
    setFormErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleCreate = async () => {
    await createUserProfile(form, setSaving, setUsers, setShowCreate, setForm, setFormErrors, validateForm);
  };

  const handleUpdate = async () => {
    if (!selectedUser) return;
    await updateUserProfile({
      selectedUserId: selectedUser.id,
      form,
      setSaving,
      setUsers,
      setSelectedUser,
      setShowEdit,
      setFormErrors,
      validateForm
    });
  };

  const handleDeleteConfirm = async () => {
    if (!deleteTarget) return;
    await deleteUserProfile(deleteTarget, selectedUser?.id, setDeleting, setUsers, setSelectedUser, setDeleteTarget);
  };

  return {
    users,
    loading,
    error,
    selectedUser,
    showCreate,
    showEdit,
    saving,
    roleFilter,
    form,
    formErrors,
    deleteTarget,
    deleting,
    agenceNames,
    agenceOptions,
    setRoleFilter,
    setForm,
    setFormErrors,
    setShowCreate,
    setShowEdit,
    setSelectedUser,
    setDeleteTarget,
    fetchUsers,
    handleCreate,
    handleUpdate,
    handleDeleteConfirm
  };
}

export default function UsersPage() {
  const {
    users,
    loading,
    error,
    selectedUser,
    showCreate,
    showEdit,
    saving,
    roleFilter,
    form,
    formErrors,
    setFormErrors,
    deleteTarget,
    deleting,
    agenceNames,
    agenceOptions,
    setRoleFilter,
    setForm,
    setShowCreate,
    setShowEdit,
    setSelectedUser,
    setDeleteTarget,
    fetchUsers,
    handleCreate,
    handleUpdate,
    handleDeleteConfirm
  } = useUsersState();

  // Contrôle d'accès : seul un ADMIN peut créer/supprimer des comptes (cf. backend X-User-Role).
  const { isAdmin } = useAuth();

  // Recherche + pagination — côté client (les users sont déjà chargés)
  const USERS_PAGE_SIZE = 8;
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);

  const filteredUsers = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return users;
    return users.filter(u =>
      `${u.prenom || ''} ${u.nom || ''}`.toLowerCase().includes(q) ||
      (u.email || '').toLowerCase().includes(q) ||
      (u.telephone || '').toLowerCase().includes(q)
    );
  }, [users, search]);

  const usersPageCount = Math.max(1, Math.ceil(filteredUsers.length / USERS_PAGE_SIZE));
  const usersSafePage = Math.min(page, usersPageCount - 1);
  const pagedUsers = filteredUsers.slice(usersSafePage * USERS_PAGE_SIZE, usersSafePage * USERS_PAGE_SIZE + USERS_PAGE_SIZE);

  useEffect(() => { setPage(0); }, [search, roleFilter]);

  const roleBadge = (role: string) => ({
    ADMIN: { cls: 'bg-amber-500/10 text-amber-400 border-amber-500/20', avatar: 'from-amber-500/20 to-amber-600/30 text-amber-400' },
    SUPERVISEUR: { cls: 'bg-violet-500/10 text-violet-400 border-violet-500/20', avatar: 'from-violet-500/20 to-violet-600/30 text-violet-400' },
    OPERATEUR: { cls: 'bg-indigo-500/10 text-indigo-400 border-indigo-500/20', avatar: 'from-indigo-500/20 to-indigo-600/30 text-indigo-400' },
  }[role] || { cls: 'bg-[var(--muted)] text-txt-3 border-border-subtle', avatar: 'from-zinc-500/10 to-zinc-600/20 text-txt-3' });

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
    <div className="flex flex-col gap-5 max-w-[1600px] mx-auto w-full px-1 py-2">
      {/* Header */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-extrabold tracking-tight text-gradient-title">
            Console des Collaborateurs
          </h1>
          <p className="text-xs text-txt-3 font-mono mt-1 flex items-center gap-1.5">
            <span className="w-1.5 h-1.5 rounded-full bg-indigo-500 animate-pulse" />
            {users.length} collaborateur{users.length > 1 ? 's' : ''} répertorié{users.length > 1 ? 's' : ''}
          </p>
        </div>
        <div className="flex items-center gap-2.5">
          <motion.button 
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
            onClick={fetchUsers} 
            className="btn-ghost h-9 w-9 p-0 rounded-xl text-xs flex items-center justify-center cursor-pointer border border-border bg-[var(--surface)] hover:bg-[var(--elevated)] shadow-sm" 
            disabled={loading}
          >
            <RefreshCw size={12} className={loading ? 'animate-spin text-indigo-400' : 'text-txt-3'} />
          </motion.button>
          {isAdmin && (
            <motion.button
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
              onClick={() => { setForm({ nom: '', prenom: '', email: '', password: '', role: 'OPERATEUR', agenceId: '', telephone: '', planning: '' }); setShowCreate(true); }}
              className="btn-cta h-9 px-4 rounded-xl text-xs text-white flex items-center gap-1.5 cursor-pointer font-bold shadow-md bg-gradient-to-r from-indigo-500 to-violet-500"
            >
              <Plus size={13} /> Créer un profil
            </motion.button>
          )}
        </div>
      </div>

      {/* Recherche + filtres par rôle */}
      <div className="flex flex-col sm:flex-row sm:items-center gap-3">
        <div className="relative flex-1 min-w-[220px]">
          <Search size={14} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-txt-3 pointer-events-none z-10" />
          <input
            type="text"
            value={search}
            onChange={e => setSearch(e.target.value)}
            placeholder="Rechercher un collaborateur (nom, email, téléphone)…"
            className="aurora-input w-full h-9 text-xs"
            style={{ paddingLeft: '38px' }}
          />
        </div>
        <div className="flex gap-2 flex-wrap">
          {[
            { key: 'ALL', label: 'Tous les profils' },
            { key: 'OPERATEUR', label: 'Opérateurs' },
            { key: 'SUPERVISEUR', label: 'Superviseurs' },
            { key: 'ADMIN', label: 'Administrateurs' }
          ].map(r => (
            <button
              key={r.key}
              onClick={() => setRoleFilter(r.key)}
              className={`chip cursor-pointer text-xs font-medium px-4.5 py-1.5 rounded-full border transition-all duration-300
                ${roleFilter === r.key
                  ? 'bg-indigo-500/10 text-indigo-400 border-indigo-500/30 font-semibold shadow-sm'
                  : 'bg-[var(--surface)] text-txt-3 border-border/80 hover:border-border hover:text-txt'
                }`}
            >
              {r.label}
            </button>
          ))}
        </div>
      </div>

      {/* Main content split panel */}
      <div className="grid grid-cols-1 lg:grid-cols-[1fr_360px] gap-6 items-start">
        {/* Left Side: Users list */}
        <div className="card-aurora rounded-2xl border border-border bg-gradient-to-b from-[var(--surface)] to-[var(--elevated)] overflow-hidden shadow-xl">
          <div className="px-5 py-4 border-b border-border/80 flex items-center justify-between bg-black/5">
            <span className="font-mono text-[10px] text-txt-3 uppercase tracking-widest font-extrabold flex items-center gap-1.5">
              <UserIcon size={12} className="text-indigo-400" />
              Répertoire actif
            </span>
          </div>

          <div className="divide-y divide-border/60">
            <AnimatePresence mode="popLayout">
              {loading ? (
                <div className="p-5 space-y-3">
                  {Array.from({ length: 4 }, (_, idx) => `skel-user-${idx}`).map((key) => (
                    <div key={key} className="h-16 bg-[var(--muted)]/40 border border-border/40 rounded-2xl animate-pulse" />
                  ))}
                </div>
              ) : filteredUsers.length === 0 ? (
                <EmptyState icon="user" title="Aucun collaborateur" subtitle="Aucun utilisateur ne correspond à ce filtre." />
              ) : (
                pagedUsers.map((u) => (
                  <UserRow
                    key={u.id}
                    u={u}
                    rb={roleBadge(u.role)}
                    initials={`${(u.prenom || '')[0] || ''}${(u.nom || '')[0] || ''}`.toUpperCase()}
                    isSelected={selectedUser?.id === u.id}
                    onSelect={() => setSelectedUser(u)}
                  />
                ))
              )}
            </AnimatePresence>
          </div>

          {/* Pagination */}
          {!loading && filteredUsers.length > USERS_PAGE_SIZE && (
            <div className="flex items-center justify-between px-5 py-3 border-t border-border/60">
              <span className="text-[10px] font-mono text-txt-3">
                {usersSafePage * USERS_PAGE_SIZE + 1}–{Math.min((usersSafePage + 1) * USERS_PAGE_SIZE, filteredUsers.length)} sur {filteredUsers.length}
              </span>
              <div className="flex items-center gap-1.5">
                <button
                  disabled={usersSafePage === 0}
                  onClick={() => setPage(p => Math.max(0, p - 1))}
                  className="w-7 h-7 rounded-lg flex items-center justify-center text-txt-3 hover:text-txt-1 hover:bg-[var(--overlay)] disabled:opacity-30 disabled:cursor-not-allowed cursor-pointer transition-all"
                  aria-label="Page précédente"
                >
                  <ChevronLeft size={14} />
                </button>
                <span className="text-[10px] font-mono text-txt-3 min-w-[64px] text-center">Page {usersSafePage + 1} / {usersPageCount}</span>
                <button
                  disabled={usersSafePage >= usersPageCount - 1}
                  onClick={() => setPage(p => Math.min(usersPageCount - 1, p + 1))}
                  className="w-7 h-7 rounded-lg flex items-center justify-center text-txt-3 hover:text-txt-1 hover:bg-[var(--overlay)] disabled:opacity-30 disabled:cursor-not-allowed cursor-pointer transition-all"
                  aria-label="Page suivante"
                >
                  <ChevronRight size={14} />
                </button>
              </div>
            </div>
          )}
        </div>

        {/* Right Side: Detail console */}
        <div className="flex flex-col gap-4 sticky top-6 h-max">
          <AnimatePresence mode="wait">
            {selectedUser ? (
              <UserDetailPanel
                selectedUser={selectedUser}
                rb={roleBadge(selectedUser.role)}
                agenceNames={agenceNames}
                onEdit={() => {
                  setForm({
                    nom: selectedUser.nom,
                    prenom: selectedUser.prenom,
                    email: selectedUser.email,
                    password: '',
                    role: selectedUser.role,
                    agenceId: selectedUser.agenceId || '',
                    telephone: selectedUser.telephone || '',
                    planning: selectedUser.planning || ''
                  });
                  setShowEdit(true);
                }}
                onDelete={() => setDeleteTarget(selectedUser)}
                canDelete={isAdmin}
              />
            ) : (
              <div className="card-aurora rounded-2xl p-6 border border-border text-center">
                <EmptyState icon="arrow-left" title="Détails du compte" subtitle="Sélectionnez un collaborateur pour explorer ses détails, son agence et son planning d'assistance." />
              </div>
            )}
          </AnimatePresence>
        </div>
      </div>

      {/* Create/Edit Modal */}
      <Modal open={showCreate || showEdit} onClose={() => { setShowCreate(false); setShowEdit(false); setFormErrors({}); }} title={showCreate ? 'Création de Compte Collaborateur' : 'Modification du Compte Collaborateur'}>
        <div className="space-y-4 pt-1">
          <div className="grid grid-cols-2 gap-3.5">
            <div>
              <label htmlFor="user-prenom" className="field-label block font-semibold text-[10px] uppercase tracking-wider mb-1 text-txt-3">Prénom *</label>
              <input id="user-prenom" className={`aurora-input ${formErrors.prenom ? 'border-rose-500/60 shadow-[0_0_10px_rgba(239,68,68,0.05)]' : ''}`} value={form.prenom} onChange={e => { setForm(p => ({ ...p, prenom: e.target.value })); setFormErrors(e2 => ({ ...e2, prenom: undefined })); }} />
              {formErrors.prenom && <span className="text-rose-400 text-[10px] mt-1 block font-semibold">⚠️ {formErrors.prenom}</span>}
            </div>
            <div>
              <label htmlFor="user-nom" className="field-label block font-semibold text-[10px] uppercase tracking-wider mb-1 text-txt-3">Nom *</label>
              <input id="user-nom" className={`aurora-input ${formErrors.nom ? 'border-rose-500/60 shadow-[0_0_10px_rgba(239,68,68,0.05)]' : ''}`} value={form.nom} onChange={e => { setForm(p => ({ ...p, nom: e.target.value })); setFormErrors(e2 => ({ ...e2, nom: undefined })); }} />
              {formErrors.nom && <span className="text-rose-400 text-[10px] mt-1 block font-semibold">⚠️ {formErrors.nom}</span>}
            </div>
          </div>
          <div>
            <label htmlFor="user-email" className="field-label block font-semibold text-[10px] uppercase tracking-wider mb-1 text-txt-3">Email de connexion *</label>
            <input id="user-email" className={`aurora-input ${formErrors.email ? 'border-rose-500/60' : ''}`} type="email" value={form.email} onChange={e => { setForm(p => ({ ...p, email: e.target.value })); setFormErrors(e2 => ({ ...e2, email: undefined })); }} />
            {formErrors.email && <span className="text-rose-400 text-[10px] mt-1 block font-semibold">⚠️ {formErrors.email}</span>}
          </div>
          {(showCreate || showEdit) && (
            <div>
              <label htmlFor="user-password" className="field-label block font-semibold text-[10px] uppercase tracking-wider mb-1 text-txt-3">{showCreate ? 'Mot de passe initial *' : 'Nouveau mot de passe (optionnel)'}</label>
              <input id="user-password" className={`aurora-input ${formErrors.password ? 'border-rose-500/60' : ''}`} type="password" placeholder={showEdit ? 'Laisser vide pour ne pas changer' : 'Minimum 6 caractères'} value={form.password} onChange={e => { setForm(p => ({ ...p, password: e.target.value })); setFormErrors(e2 => ({ ...e2, password: undefined })); }} />
              {formErrors.password && <span className="text-rose-400 text-[10px] mt-1 block font-semibold">⚠️ {formErrors.password}</span>}
            </div>
          )}
          
          <div className="grid grid-cols-2 gap-3.5">
            <div>
              <label htmlFor="user-role" className="field-label block font-semibold text-[10px] uppercase tracking-wider mb-1 text-txt-3">Rôle d'accès</label>
              <select id="user-role" className="aurora-select" value={form.role} onChange={e => setForm(p => ({ ...p, role: e.target.value as 'OPERATEUR' | 'SUPERVISEUR' | 'ADMIN' }))}>
                <option value="OPERATEUR">Opérateur</option>
                <option value="SUPERVISEUR">Superviseur d'agence</option>
                <option value="ADMIN">Administrateur</option>
              </select>
            </div>
            <div>
              <label htmlFor="user-telephone" className="field-label block font-semibold text-[10px] uppercase tracking-wider mb-1 text-txt-3">N° Téléphone</label>
              <input id="user-telephone" className="aurora-input" placeholder="Ex: 06 12 34 56 78" value={form.telephone} onChange={e => setForm(p => ({ ...p, telephone: e.target.value }))} />
            </div>
          </div>

          {/* CDC 1.A — Agence (obligatoire pour OPERATEUR/SUPERVISEUR) */}
          <div>
            <label htmlFor="user-agenceId" className="field-label block font-semibold text-[10px] uppercase tracking-wider mb-1 text-txt-3">
              Agence de rattachement {(form.role === 'OPERATEUR' || form.role === 'SUPERVISEUR') && <span className="text-rose-400">*</span>}
            </label>
            <select id="user-agenceId" className={`aurora-select ${formErrors.agenceId ? 'border-rose-500/60' : ''}`} value={form.agenceId || ''} onChange={e => { setForm(p => ({ ...p, agenceId: e.target.value })); setFormErrors(e2 => ({ ...e2, agenceId: undefined })); }}>
              <option value="">-- Aucune agence --</option>
              {agenceOptions.map(a => (
                <option key={a.id} value={a.id}>{a.nom}</option>
              ))}
            </select>
            {formErrors.agenceId && <span className="text-rose-400 text-[10px] mt-1 block font-semibold">⚠️ {formErrors.agenceId}</span>}
          </div>

          {/* Planning weekly presets */}
          <div>
            <span className="field-label block flex items-center gap-1 font-semibold text-[10px] uppercase tracking-wider mb-1 text-txt-3">
              <CalendarDays size={12} className="text-indigo-400" /> Définition du planning
            </span>
            <div className="flex gap-1.5 flex-wrap mb-2">
              {planningPresets.map(p => (
                <button 
                  key={p.label} 
                  type="button" 
                  onClick={() => setForm(f => ({ ...f, planning: p.value }))} 
                  className={`text-[9px] font-mono font-bold px-2 py-0.5 rounded border cursor-pointer transition-colors
                    ${form.planning === p.value 
                      ? 'bg-indigo-500/20 text-indigo-400 border-indigo-500/30' 
                      : 'text-txt-3 border-border hover:border-indigo-500/20 hover:text-txt'
                    }`}
                >
                  {p.label}
                </button>
              ))}
            </div>
            <input className="aurora-input font-mono text-xs" placeholder="Ex: Lundi-Vendredi 08:00-17:00" value={form.planning || ''} onChange={e => setForm(p => ({ ...p, planning: e.target.value }))} />
          </div>

          <div className="flex justify-end gap-2.5 pt-4.5 border-t border-border mt-3">
            <button onClick={() => { setShowCreate(false); setShowEdit(false); setFormErrors({}); }} className="btn-ghost h-9 px-4 rounded-xl text-xs cursor-pointer font-bold">
              Annuler
            </button>
            <motion.button 
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
              onClick={showCreate ? handleCreate : handleUpdate} 
              disabled={saving} 
              className="btn-cta h-9 px-5 rounded-xl text-xs text-white cursor-pointer font-bold bg-indigo-500 hover:bg-indigo-600 disabled:opacity-50 disabled:pointer-events-none"
            >
              {(() => {
                if (saving) return 'Sauvegarde...';
                return showCreate ? 'Créer le compte' : 'Enregistrer';
              })()}
            </motion.button>
          </div>
        </div>
      </Modal>

      {/* Delete/Deactivate Confirmation Modal */}
      <Modal open={!!deleteTarget} onClose={() => setDeleteTarget(null)} title="Désactivation du Compte" size="sm">
        <div className="text-center space-y-4 py-2">
          <div className="w-14 h-14 rounded-2xl mx-auto flex items-center justify-center bg-rose-500/10 border border-rose-500/20 shadow-sm animate-bounce">
            <AlertTriangle size={24} className="text-rose-400" />
          </div>
          
          <div className="space-y-1">
            <p className="text-sm font-bold text-txt">Confirmer la désactivation ?</p>
            <p className="text-xs text-txt-3 font-semibold font-mono text-rose-400 bg-rose-500/5 py-1 px-2.5 border border-rose-500/10 rounded-xl max-w-[280px] mx-auto truncate">
              {deleteTarget?.prenom} {deleteTarget?.nom}
            </p>
          </div>
          
          <p className="text-[11px] text-txt-3 max-w-[300px] mx-auto leading-relaxed">
            Cette mesure de sécurité bloquera l'accès à ce compte et l'exclura des plannings de support. Vous pourrez le réactiver dans l'administration de la base.
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
              {deleting ? 'Désactivation...' : 'Désactiver'}
            </button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
