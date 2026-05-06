import { useState, useEffect, useCallback } from 'react';
import { AlertTriangle, GripVertical, Pencil, Plus, Settings } from 'lucide-react';
import toast from 'react-hot-toast';
import { ruleService, type RefItem } from '../services/ruleService';
import { ErrorState, EmptyState } from '../components/ui/States';
import Modal from '../components/ui/Modal';
import type { Rule, CreateRuleRequest, ReferenceData, FieldDef, RuleVersion, EvaluateResponse, RuleCondition } from '../types/rule';
import { DndContext, closestCenter, KeyboardSensor, PointerSensor, useSensor, useSensors, type DragEndEvent } from '@dnd-kit/core';
import { arrayMove, SortableContext, sortableKeyboardCoordinates, useSortable, verticalListSortingStrategy } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';

// ─── Sortable Condition Row ───
function SortableConditionRow({ id, cond, index, refs, formErrors, onChange, onRemove }: Readonly<{
  id: string; cond: RuleCondition; index: number; refs: ReferenceData | null;
  formErrors: { conditions?: string }; onChange: (i: number, c: RuleCondition) => void; onRemove: (i: number) => void;
}>) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id });
  const style = { transform: CSS.Transform.toString(transform), transition, opacity: isDragging ? 0.5 : 1, zIndex: isDragging ? 50 : 'auto' };

  // Find field definition for smart value input
  const fieldDef = refs?.fields.find((f: FieldDef) => f.field === cond.field);
  const isEnum = fieldDef?.type === 'enum' && fieldDef.values && fieldDef.values.length > 0;

  return (
    <div ref={setNodeRef} style={style} className="grid grid-cols-[28px_1fr_80px_1fr_28px] gap-2 mb-2 items-center">
      <button type="button" {...attributes} {...listeners} className="cursor-grab active:cursor-grabbing text-txt-3 hover:text-indigo-400 flex items-center justify-center">
        <GripVertical size={14} />
      </button>
      <select className={`aurora-select text-xs ${formErrors.conditions && !cond.field ? 'border-rose-500/60' : ''}`} value={cond.field}
        onChange={e => { onChange(index, { ...cond, field: e.target.value, value: '' }); }}>
        <option value="">Champ…</option>
        {refs?.fields.map((f: FieldDef) => <option key={f.field} value={f.field}>{f.label}</option>)}
      </select>
      <select className="aurora-select text-xs" value={cond.operator}
        onChange={e => onChange(index, { ...cond, operator: e.target.value })}>
        {(refs?.operators || ['=', '!=', '<', '>', '<=', '>=']).map(o => <option key={o} value={o}>{o}</option>)}
      </select>
      {isEnum ? (
        <select className={`aurora-select text-xs ${formErrors.conditions && !cond.value ? 'border-rose-500/60' : ''}`}
          value={cond.value} onChange={e => onChange(index, { ...cond, value: e.target.value })}>
          <option value="">Valeur…</option>
          {fieldDef.values!.map(v => <option key={v} value={v}>{v}</option>)}
        </select>
      ) : (
        <input className={`aurora-input text-xs ${formErrors.conditions && !cond.value ? 'border-rose-500/60' : ''}`}
          type={fieldDef?.type === 'number' ? 'number' : 'text'}
          value={cond.value} onChange={e => onChange(index, { ...cond, value: e.target.value })} placeholder={fieldDef?.type === 'number' ? 'Ex: 5' : 'Valeur'} />
      )}
      <button type="button" onClick={() => onRemove(index)} className="text-txt-3 hover:text-rose-400 cursor-pointer text-center text-sm">✕</button>
    </div>
  );
}

// ─── Conditions Editor with DnD ───
function ConditionsEditor({ conditions, onChange, refs, formErrors }: Readonly<{
  conditions: { operator: 'AND' | 'OR'; conditions: RuleCondition[] };
  onChange: (c: { operator: 'AND' | 'OR'; conditions: RuleCondition[] }) => void;
  refs: ReferenceData | null; formErrors: { conditions?: string };
}>) {
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates })
  );

  // Stable IDs for sortable
  const ids = conditions.conditions.map((_, i) => `cond-${i}`);

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    const oldIndex = ids.indexOf(String(active.id));
    const newIndex = ids.indexOf(String(over.id));
    onChange({ ...conditions, conditions: arrayMove(conditions.conditions, oldIndex, newIndex) });
  };

  const handleChange = (i: number, c: RuleCondition) => {
    const updated = [...conditions.conditions];
    updated[i] = c;
    onChange({ ...conditions, conditions: updated });
  };

  const handleRemove = (i: number) => {
    const updated = conditions.conditions.filter((_, j) => j !== i);
    onChange({ ...conditions, conditions: updated.length ? updated : [{ field: '', operator: '=', value: '' }] });
  };

  const handleAdd = () => {
    onChange({ ...conditions, conditions: [...conditions.conditions, { field: '', operator: '=', value: '' }] });
  };

  return (
    <div>
      <div className="flex items-center gap-2 mb-2">
        <label className="field-label">Conditions ({conditions.conditions.length}) *</label>
        <select className="aurora-select text-[10px] w-20 h-6" value={conditions.operator}
          onChange={e => onChange({ ...conditions, operator: e.target.value as 'AND' | 'OR' })}>
          <option value="AND">ET (AND)</option>
          <option value="OR">OU (OR)</option>
        </select>
      </div>
      {formErrors.conditions && <span className="text-rose-400 text-[10px] mb-2 block">{formErrors.conditions}</span>}
      <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
        <SortableContext items={ids} strategy={verticalListSortingStrategy}>
          {conditions.conditions.map((cond, i) => (
            <SortableConditionRow key={ids[i]} id={ids[i]} cond={cond} index={i} refs={refs} formErrors={formErrors} onChange={handleChange} onRemove={handleRemove} />
          ))}
        </SortableContext>
      </DndContext>
      <button type="button" onClick={handleAdd} className="btn-ghost h-7 px-3 rounded text-[10px] cursor-pointer">+ Condition</button>
    </div>
  );
}

// ─── Form Errors ───
interface FormErrors { nom?: string; description?: string; conditions?: string; }

// ─── Reference categories config ───
const REF_CATEGORIES = [
  { key: 'TYPE_PIECE', label: 'Types de pièces (garanties)', icon: '🔧' },
  { key: 'CATEGORIE_CLIENT', label: 'Catégories client', icon: '👥' },
  { key: 'TYPE_SINISTRE', label: 'Types de sinistre (exclusions)', icon: '⚡' },
  { key: 'FIELD_DEFINITION', label: 'Champs disponibles (conditions)', icon: '📋' },
] as const;

// ─── Main Page ───
export default function RulesPage() {
  const [rules, setRules] = useState<Rule[]>([]);
  const [refs, setRefs] = useState<ReferenceData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedRule, setSelectedRule] = useState<Rule | null>(null);
  const [history, setHistory] = useState<RuleVersion[]>([]);
  const [creating, setCreating] = useState(false);
  const [showCreate, setShowCreate] = useState(false);
  const [toggling, setToggling] = useState<string | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<Rule | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [formErrors, setFormErrors] = useState<FormErrors>({});

  // Edit state
  const [editingRule, setEditingRule] = useState<Rule | null>(null);
  const [showEdit, setShowEdit] = useState(false);
  const [saving, setSaving] = useState(false);

  // Tab state
  const [activeTab, setActiveTab] = useState<'rules' | 'params'>('rules');

  // Paramétrage state
  const [refItems, setRefItems] = useState<RefItem[]>([]);
  const [refLoading, setRefLoading] = useState(false);
  const [addingRef, setAddingRef] = useState<string | null>(null); // category being added to
  const [newRefCode, setNewRefCode] = useState('');
  const [newRefLabel, setNewRefLabel] = useState('');
  const [newRefDesc, setNewRefDesc] = useState('');

  // Test panel
  const [testForm, setTestForm] = useState<Record<string, string>>({});
  const [testResult, setTestResult] = useState<EvaluateResponse | null>(null);
  const [testing, setTesting] = useState(false);

  // Create form
  const [form, setForm] = useState<CreateRuleRequest>({
    nom: '', description: '', action: 'FAVORABLE', actionMotif: '', priorite: 10,
    conditions: { operator: 'AND', conditions: [{ field: '', operator: '=', value: '' }] },
  });

  // Edit form
  const [editForm, setEditForm] = useState<CreateRuleRequest>({
    nom: '', description: '', action: 'FAVORABLE', actionMotif: '', priorite: 10,
    conditions: { operator: 'AND', conditions: [{ field: '', operator: '=', value: '' }] },
  });

  const fetchRules = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [rulesData, refsData] = await Promise.all([
        ruleService.getRules(),
        ruleService.getReferences(),
      ]);
      setRules(rulesData);
      setRefs(refsData);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Erreur chargement');
    } finally {
      setLoading(false);
    }
  }, []);

  const fetchRefItems = useCallback(async () => {
    setRefLoading(true);
    try {
      const items = await ruleService.getRefItems();
      setRefItems(items);
    } catch { toast.error('Erreur chargement références'); }
    finally { setRefLoading(false); }
  }, []);

  useEffect(() => { fetchRules(); }, [fetchRules]);
  useEffect(() => { if (activeTab === 'params') fetchRefItems(); }, [activeTab, fetchRefItems]);

  // ── Ref item handlers ──
  const handleAddRef = async (category: string) => {
    if (!newRefCode.trim() || !newRefLabel.trim()) { toast.error('Code et libellé obligatoires'); return; }
    try {
      await ruleService.createRefItem({ category, code: newRefCode.trim().toUpperCase(), label: newRefLabel.trim(), description: newRefDesc.trim() || undefined });
      toast.success('Référence ajoutée');
      setAddingRef(null); setNewRefCode(''); setNewRefLabel(''); setNewRefDesc('');
      fetchRefItems(); fetchRules(); // refresh refs in dropdowns
    } catch { toast.error('Erreur — code déjà existant ?'); }
  };

  const handleToggleRef = async (id: string) => {
    try {
      await ruleService.toggleRefItem(id);
      fetchRefItems(); fetchRules();
    } catch { toast.error('Erreur'); }
  };

  const handleDeleteRef = async (id: string) => {
    try {
      await ruleService.deleteRefItem(id);
      toast.success('Référence supprimée');
      fetchRefItems(); fetchRules();
    } catch { toast.error('Erreur suppression'); }
  };

  const handleToggle = async (id: string) => {
    setToggling(id);
    try {
      const updated = await ruleService.toggleRule(id);
      setRules(prev => prev.map(r => r.id === id ? updated : r));
      if (selectedRule?.id === id) setSelectedRule(updated);
      toast.success(updated.actif ? 'Règle activée' : 'Règle désactivée');
    } catch { toast.error('Erreur'); }
    finally { setToggling(null); }
  };

  const handleDeleteConfirm = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      await ruleService.deleteRule(deleteTarget.id);
      setRules(prev => prev.filter(r => r.id !== deleteTarget.id));
      if (selectedRule?.id === deleteTarget.id) setSelectedRule(null);
      toast.success('Règle supprimée');
    } catch { toast.error('Erreur suppression'); }
    finally {
      setDeleting(false);
      setDeleteTarget(null);
    }
  };

  const validateForm = (f: CreateRuleRequest): boolean => {
    const errors: FormErrors = {};
    if (!f.nom.trim()) errors.nom = 'Nom obligatoire';
    if (!f.description.trim()) errors.description = 'Description obligatoire';
    if (f.conditions.conditions.some(c => !c.field || !c.value))
      errors.conditions = 'Tous les champs et valeurs des conditions sont obligatoires';
    setFormErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleCreate = async () => {
    if (!validateForm(form)) return;
    setCreating(true);
    try {
      const created = await ruleService.createRule(form);
      setRules(prev => [created, ...prev]);
      setShowCreate(false);
      setForm({
        nom: '', description: '', action: 'FAVORABLE', actionMotif: '', priorite: 10,
        conditions: { operator: 'AND', conditions: [{ field: '', operator: '=', value: '' }] },
      });
      setFormErrors({});
      toast.success('Règle créée');
    } catch { toast.error('Erreur création'); }
    finally { setCreating(false); }
  };

  // ── Edit handlers ──
  const openEdit = (rule: Rule) => {
    setEditingRule(rule);
    setEditForm({
      nom: rule.nom,
      description: rule.description,
      action: rule.action,
      actionMotif: rule.actionMotif,
      priorite: rule.priorite,
      conditions: rule.conditions,
    });
    setFormErrors({});
    setShowEdit(true);
  };

  const handleUpdate = async () => {
    if (!editingRule || !validateForm(editForm)) return;
    setSaving(true);
    try {
      const updated = await ruleService.updateRule(editingRule.id, editForm);
      setRules(prev => prev.map(r => r.id === editingRule.id ? updated : r));
      setSelectedRule(updated);
      // Refresh history after update
      const h = await ruleService.getHistory(editingRule.id);
      setHistory(h);
      setShowEdit(false);
      setEditingRule(null);
      toast.success(`Règle modifiée → v${updated.version}`);
    } catch { toast.error('Erreur modification'); }
    finally { setSaving(false); }
  };

  const handleSelectRule = async (rule: Rule) => {
    setSelectedRule(rule);
    try {
      const h = await ruleService.getHistory(rule.id);
      setHistory(h);
    } catch { setHistory([]); }
  };

  const handleTest = async () => {
    setTesting(true);
    setTestResult(null);
    try {
      // Build data map dynamically from all fields
      const data: Record<string, unknown> = {};
      for (const fd of (refs?.fields || [])) {
        const val = testForm[fd.field];
        if (val !== undefined && val !== '') {
          data[fd.field] = fd.type === 'number' ? parseInt(val) : val;
        }
      }
      const result = await ruleService.evaluate({
        typePiece: (data.typePiece as string) || undefined,
        ageVehiculeAns: (data.ageVehiculeAns as number) || undefined,
        kilometrage: (data.kilometrage as number) || undefined,
        categorieClient: (data.categorieClient as string) || undefined,
        typeSinistre: (data.typeSinistre as string) || undefined,
        data,
      });
      setTestResult(result);
    } catch { toast.error('Erreur test'); }
    finally { setTesting(false); }
  };

  if (error) return <ErrorState message={error} onRetry={fetchRules} />;

  // ── Rule form fields (shared between create & edit) ──
  const renderFormFields = (f: CreateRuleRequest, setF: React.Dispatch<React.SetStateAction<CreateRuleRequest>>) => (
    <div className="space-y-3.5">
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label htmlFor="rule-nom" className="field-label block">Nom *</label>
          <input id="rule-nom" className={`aurora-input ${formErrors.nom ? 'border-rose-500/60' : ''}`} value={f.nom} onChange={e => { setF(p => ({ ...p, nom: e.target.value })); setFormErrors(e2 => ({ ...e2, nom: undefined })); }} placeholder="Ex: Règle garantie mécanique" />
          {formErrors.nom && <span className="text-rose-400 text-[10px] mt-0.5 block">{formErrors.nom}</span>}
        </div>
        <div><label htmlFor="rule-priorite" className="field-label block">Priorité</label><input id="rule-priorite" className="aurora-input" type="number" value={f.priorite} onChange={e => setF(p => ({ ...p, priorite: parseInt(e.target.value) || 10 }))} /></div>
      </div>
      <div>
        <label htmlFor="rule-desc" className="field-label block">Description *</label>
        <input id="rule-desc" className={`aurora-input ${formErrors.description ? 'border-rose-500/60' : ''}`} value={f.description} onChange={e => { setF(p => ({ ...p, description: e.target.value })); setFormErrors(e2 => ({ ...e2, description: undefined })); }} />
        {formErrors.description && <span className="text-rose-400 text-[10px] mt-0.5 block">{formErrors.description}</span>}
      </div>
      <div className="grid grid-cols-2 gap-3">
        <div><label htmlFor="rule-action" className="field-label block">Action</label>
          <select id="rule-action" className="aurora-select" value={f.action} onChange={e => setF(p => ({ ...p, action: e.target.value as 'FAVORABLE' | 'DEFAVORABLE' | 'ETUDE' }))}>
            <option value="FAVORABLE">Favorable</option><option value="DEFAVORABLE">Défavorable</option><option value="ETUDE">Étude</option>
          </select>
        </div>
        <div><label htmlFor="rule-motif" className="field-label block">Motif action</label><input id="rule-motif" className="aurora-input" value={f.actionMotif} onChange={e => setF(p => ({ ...p, actionMotif: e.target.value }))} /></div>
      </div>
      <ConditionsEditor conditions={f.conditions} onChange={c => setF(p => ({ ...p, conditions: c }))} refs={refs} formErrors={formErrors} />
    </div>
  );

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-extrabold tracking-tight">Moteur de règles</h1>
          <p className="text-xs text-txt-3 font-mono mt-0.5">{rules.length} règle{rules.length > 1 ? 's' : ''} configurées</p>
        </div>
        <div className="flex items-center gap-2">
          <div className="flex bg-[var(--elevated)] border border-border rounded-lg p-0.5">
            <button onClick={() => setActiveTab('rules')} className={`px-3 py-1.5 rounded-md text-[11px] font-semibold transition-all cursor-pointer ${activeTab === 'rules' ? 'bg-indigo-500/20 text-indigo-400 border border-indigo-500/30' : 'text-txt-3 hover:text-txt-1'}`}>📐 Règles</button>
            <button onClick={() => setActiveTab('params')} className={`px-3 py-1.5 rounded-md text-[11px] font-semibold transition-all cursor-pointer flex items-center gap-1 ${activeTab === 'params' ? 'bg-amber-500/20 text-amber-400 border border-amber-500/30' : 'text-txt-3 hover:text-txt-1'}`}><Settings size={12} /> Paramétrage</button>
          </div>
          {activeTab === 'rules' && <button onClick={() => setShowCreate(true)} className="btn-cta h-9 px-4 rounded-lg text-xs text-white cursor-pointer">+ Nouvelle règle</button>}
        </div>
      </div>

      {activeTab === 'rules' ? (
      <div className="grid grid-cols-1 lg:grid-cols-[1fr_350px] gap-4">
        {/* Rules list */}
        <div className="flex flex-col gap-3">
          {loading ? (
            <div className="space-y-3 animate-pulse">{Array.from({ length: 4 }).map((_, i) => <div key={i} className="h-20 bg-[var(--muted)] rounded-2xl" />)}</div>
          ) : rules.length === 0 ? (
            <EmptyState icon="📐" title="Aucune règle" subtitle="Créez votre première règle métier" action={
              <button onClick={() => setShowCreate(true)} className="btn-cta h-8 px-4 rounded-lg text-xs text-white cursor-pointer">+ Créer</button>
            } />
          ) : (
            rules.map((rule) => (
              <div
                key={rule.id}
                onClick={() => handleSelectRule(rule)}
                className={`card-aurora rounded-2xl p-4 cursor-pointer transition-all hover:border-indigo-500/30 ${selectedRule?.id === rule.id ? 'border border-indigo-500/40 bg-indigo-500/[0.03]' : ''}`}
              >
                <div className="flex items-center gap-3">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                      <span className="text-sm font-bold">{rule.nom}</span>
                      <span className="font-mono text-[9px] text-txt-3 bg-[var(--overlay)] px-2 py-0.5 rounded border border-border-subtle">v{rule.version}</span>
                    </div>
                    <div className="text-xs text-txt-2 line-clamp-1">{rule.description}</div>
                    <div className="flex items-center gap-2 mt-2">
                      <span className={`badge border text-[9px] ${rule.action === 'FAVORABLE' ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' : rule.action === 'DEFAVORABLE' ? 'bg-rose-500/10 text-rose-400 border-rose-500/20' : 'bg-amber-500/10 text-amber-400 border-amber-500/20'}`}>
                        {rule.action}
                      </span>
                      <span className="font-mono text-[9px] text-txt-3">P{rule.priorite}</span>
                      <span className="font-mono text-[9px] text-txt-3">{rule.conditions.conditions.length} cond.</span>
                    </div>
                  </div>
                  <div className="flex items-center gap-2 flex-shrink-0">
                    <div
                      onClick={(e) => { e.stopPropagation(); handleToggle(rule.id); }}
                      className={`toggle-switch cursor-pointer ${rule.actif ? 'on' : ''} ${toggling === rule.id ? 'opacity-50' : ''}`}
                    />
                    <button onClick={(e) => { e.stopPropagation(); setDeleteTarget(rule); }} className="w-7 h-7 rounded-lg flex items-center justify-center text-[11px] text-txt-3 hover:text-rose-400 hover:bg-rose-500/10 transition-all cursor-pointer">
                      🗑
                    </button>
                  </div>
                </div>
              </div>
            ))
          )}

          {/* Test panel — Dynamic fields from API */}
          <div className="card-aurora rounded-2xl p-5">
            <h3 className="text-sm font-bold mb-3">🧪 Tester les règles</h3>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2.5 mb-3">
              {(refs?.fields || []).filter((fd: FieldDef) => fd.field !== 'transcriptionText').map((fd: FieldDef) => (
                <div key={fd.field}>
                  <label className="field-label block">{fd.label}</label>
                  {fd.type === 'enum' && fd.values && fd.values.length > 0 ? (
                    <select className="aurora-select" value={testForm[fd.field] || ''} onChange={e => setTestForm(p => ({ ...p, [fd.field]: e.target.value }))}>
                      <option value="">Choisir…</option>
                      {fd.values.map(v => <option key={v} value={v}>{v}</option>)}
                    </select>
                  ) : (
                    <input className="aurora-input" type={fd.type === 'number' ? 'number' : 'text'}
                      placeholder={fd.type === 'number' ? 'Ex: 5' : 'Valeur'}
                      value={testForm[fd.field] || ''}
                      onChange={e => setTestForm(p => ({ ...p, [fd.field]: e.target.value }))} />
                  )}
                </div>
              ))}
            </div>
            <button onClick={handleTest} disabled={testing} className="btn-cta h-8 px-4 rounded-lg text-[10px] font-mono text-white cursor-pointer disabled:opacity-50 mb-3">
              {testing ? '⏳ Evaluation...' : '▶ Évaluer'}
            </button>
            {testResult && (
              <div className={`p-3.5 rounded-xl border ${testResult.decision === 'FAVORABLE' ? 'bg-emerald-500/10 border-emerald-500/20' : testResult.decision === 'DEFAVORABLE' ? 'bg-rose-500/10 border-rose-500/20' : 'bg-amber-500/10 border-amber-500/20'}`}>
                <div className={`text-sm font-bold ${testResult.decision === 'FAVORABLE' ? 'text-emerald-400' : testResult.decision === 'DEFAVORABLE' ? 'text-rose-400' : 'text-amber-400'}`}>
                  {testResult.decision} — Score: {Math.round(testResult.score)}%
                </div>
                {testResult.motif && (
                  <div className="text-[10px] text-txt-2 mt-1 italic">{testResult.motif}</div>
                )}
                <div className="text-[10px] text-txt-2 mt-1">
                  {testResult.matchedRules} règle{testResult.matchedRules > 1 ? 's' : ''} correspondante{testResult.matchedRules > 1 ? 's' : ''}
                  {testResult.rulesApplied?.length > 0 && ` : ${testResult.rulesApplied.join(', ')}`}
                  {testResult.ambiguous && ' · ⚠ Résultat ambigu'}
                </div>
              </div>
            )}
          </div>
        </div>

        {/* Detail panel */}
        <div className="flex flex-col gap-3 sticky top-6 h-max">
          {selectedRule ? (
            <>
              <div className="card-aurora rounded-2xl p-5">
                <div className="flex items-center justify-between mb-3">
                  <h3 className="text-sm font-bold">Conditions</h3>
                  <button onClick={() => openEdit(selectedRule)} className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[10px] font-semibold bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 hover:bg-indigo-500/20 transition-colors cursor-pointer">
                    <Pencil size={12} /> Modifier
                  </button>
                </div>
                <div className="space-y-2">
                  <div className="font-mono text-[10px] text-txt-3 mb-1">Opérateur : {selectedRule.conditions.operator}</div>
                  {selectedRule.conditions.conditions.map((cond, i) => (
                    <div key={i} className="flex items-center gap-2 p-2.5 bg-[var(--elevated)] border border-border rounded-lg">
                      <span className={`font-mono text-[10px] px-2 py-0.5 rounded border ${i === 0 ? 'bg-indigo-500/10 text-indigo-400 border-indigo-500/20' : 'bg-violet-500/10 text-violet-400 border-violet-500/20'}`}>
                        {i === 0 ? 'SI' : selectedRule.conditions.operator}
                      </span>
                      <span className="text-xs">{cond.field} <strong className="text-indigo-400">{cond.operator}</strong> <span className="text-emerald-400">"{cond.value}"</span></span>
                    </div>
                  ))}
                  <div className="p-2.5 bg-[var(--elevated)] border border-border rounded-lg">
                    <span className={`font-mono text-[10px] px-2 py-0.5 rounded border ${selectedRule.action === 'FAVORABLE' ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' : selectedRule.action === 'DEFAVORABLE' ? 'bg-rose-500/10 text-rose-400 border-rose-500/20' : 'bg-amber-500/10 text-amber-400 border-amber-500/20'}`}>ALORS</span>
                    <span className="text-xs ml-2">{selectedRule.action}</span>
                    <div className="font-mono text-[10px] text-txt-3 mt-1">{selectedRule.actionMotif}</div>
                  </div>
                </div>
              </div>
              <div className="card-aurora rounded-2xl p-5">
                <h3 className="text-sm font-bold mb-3">📜 Historique</h3>
                {history.length === 0 ? (
                  <div className="text-xs text-txt-3 text-center py-4">Pas encore de modifications — modifiez cette règle pour créer un historique</div>
                ) : (
                  <div className="space-y-1.5">
                    {history.map((h) => (
                      <div key={h.id} className="flex items-center gap-2 p-2.5 bg-[var(--elevated)] border border-border rounded-lg text-xs">
                        <span className="font-mono text-[10px] text-indigo-400 bg-indigo-500/10 px-2 py-0.5 rounded border border-indigo-500/20">v{h.version}</span>
                        <span className="flex-1 text-txt-2">{h.action}</span>
                        <span className="font-mono text-[9px] text-txt-3">{h.changedByName}</span>
                        <span className="font-mono text-[9px] text-txt-3">{h.changedAt ? new Date(h.changedAt).toLocaleDateString('fr-FR') : ''}</span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </>
          ) : (
            <div className="card-aurora rounded-2xl p-5">
              <EmptyState icon="👈" title="Sélectionner une règle" subtitle="Cliquez sur une règle pour voir ses détails" />
            </div>
          )}
        </div>
      </div>

      ) : (
      /* ── Paramétrage Tab ── */
      <div className="space-y-4">
        {refLoading ? (
          <div className="space-y-3 animate-pulse">{Array.from({ length: 3 }).map((_, i) => <div key={i} className="h-32 bg-[var(--muted)] rounded-2xl" />)}</div>
        ) : (
          REF_CATEGORIES.map(cat => {
            const items = refItems.filter(r => r.category === cat.key);
            return (
              <div key={cat.key} className="card-aurora rounded-2xl p-5">
                <div className="flex items-center justify-between mb-3">
                  <h3 className="text-sm font-bold flex items-center gap-2">
                    <span>{cat.icon}</span> {cat.label}
                    <span className="font-mono text-[10px] text-txt-3 bg-[var(--overlay)] px-2 py-0.5 rounded border border-border-subtle">{items.filter(i => i.actif).length} actifs / {items.length} total</span>
                  </h3>
                  <button onClick={() => { setAddingRef(addingRef === cat.key ? null : cat.key); setNewRefCode(''); setNewRefLabel(''); setNewRefDesc(''); }} className="flex items-center gap-1 px-3 py-1.5 rounded-lg text-[10px] font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 hover:bg-emerald-500/20 transition-colors cursor-pointer">
                    <Plus size={12} /> Ajouter
                  </button>
                </div>

                {/* Add form */}
                {addingRef === cat.key && (
                  <div className="grid grid-cols-1 sm:grid-cols-[1fr_1fr_2fr_auto] gap-2 mb-3 p-3 bg-[var(--elevated)] border border-border rounded-lg">
                    <input className="aurora-input text-xs" placeholder="Code (ex: HYDRAULIQUE)" value={newRefCode} onChange={e => setNewRefCode(e.target.value)} />
                    <input className="aurora-input text-xs" placeholder="Libellé" value={newRefLabel} onChange={e => setNewRefLabel(e.target.value)} />
                    <input className="aurora-input text-xs" placeholder="Description (optionnel)" value={newRefDesc} onChange={e => setNewRefDesc(e.target.value)} />
                    <div className="flex gap-1">
                      <button onClick={() => handleAddRef(cat.key)} className="btn-cta h-8 px-3 rounded-lg text-[10px] text-white cursor-pointer">✓</button>
                      <button onClick={() => setAddingRef(null)} className="btn-ghost h-8 px-3 rounded-lg text-[10px] cursor-pointer">✕</button>
                    </div>
                  </div>
                )}

                {/* Items list */}
                <div className="space-y-1">
                  {items.length === 0 ? (
                    <div className="text-xs text-txt-3 text-center py-4">Aucun élément — cliquez Ajouter</div>
                  ) : (
                    items.map(item => (
                      <div key={item.id} className={`flex items-center gap-3 p-2.5 rounded-lg border transition-all ${item.actif ? 'bg-[var(--elevated)] border-border' : 'bg-[var(--muted)] border-border opacity-50'}`}>
                        <span className="font-mono text-[10px] text-indigo-400 bg-indigo-500/10 px-2 py-0.5 rounded border border-indigo-500/20 min-w-[90px] text-center">{item.code}</span>
                        <span className="text-xs font-semibold flex-1">{item.label}</span>
                        {item.description && <span className="text-[10px] text-txt-3 flex-1 truncate">{item.description}</span>}
                        <div className="flex items-center gap-1.5 flex-shrink-0">
                          <div onClick={() => handleToggleRef(item.id)} className={`toggle-switch cursor-pointer ${item.actif ? 'on' : ''}`} style={{ transform: 'scale(0.8)' }} />
                          <button onClick={() => handleDeleteRef(item.id)} className="w-6 h-6 rounded flex items-center justify-center text-[10px] text-txt-3 hover:text-rose-400 hover:bg-rose-500/10 transition-all cursor-pointer">🗑</button>
                        </div>
                      </div>
                    ))
                  )}
                </div>
              </div>
            );
          })
        )}
      </div>
      )}

      {/* Create Modal */}
      <Modal open={showCreate} onClose={() => { setShowCreate(false); setFormErrors({}); }} title="Nouvelle règle" size="lg">
        {renderFormFields(form, setForm)}
        <div className="flex justify-end gap-2 pt-2 border-t border-border mt-4">
          <button onClick={() => { setShowCreate(false); setFormErrors({}); }} className="btn-ghost h-9 px-4 rounded-lg text-xs cursor-pointer">Annuler</button>
          <button onClick={handleCreate} disabled={creating} className="btn-cta h-9 px-4 rounded-lg text-xs text-white cursor-pointer disabled:opacity-50">{creating ? 'Création...' : 'Créer la règle'}</button>
        </div>
      </Modal>

      {/* Edit Modal */}
      <Modal open={showEdit} onClose={() => { setShowEdit(false); setEditingRule(null); setFormErrors({}); }} title={`Modifier — ${editingRule?.nom ?? ''}`} size="lg">
        {renderFormFields(editForm, setEditForm)}
        <div className="flex justify-end gap-2 pt-2 border-t border-border mt-4">
          <button onClick={() => { setShowEdit(false); setEditingRule(null); setFormErrors({}); }} className="btn-ghost h-9 px-4 rounded-lg text-xs cursor-pointer">Annuler</button>
          <button onClick={handleUpdate} disabled={saving} className="btn-cta h-9 px-4 rounded-lg text-xs text-white cursor-pointer disabled:opacity-50">{saving ? 'Enregistrement...' : 'Enregistrer les modifications'}</button>
        </div>
      </Modal>

      {/* Delete Confirmation Modal */}
      <Modal open={!!deleteTarget} onClose={() => setDeleteTarget(null)} title="Confirmer la suppression" size="sm">
        <div className="text-center">
          <div className="w-14 h-14 rounded-2xl mx-auto mb-3 flex items-center justify-center bg-rose-500/10 border border-rose-500/20">
            <AlertTriangle size={24} className="text-rose-400" />
          </div>
          <p className="text-sm font-semibold mb-1">Supprimer cette règle ?</p>
          <p className="text-xs text-txt-3 mb-1">
            <span className="font-mono text-rose-400">{deleteTarget?.nom}</span>
            <span className="text-txt-3 ml-1">v{deleteTarget?.version}</span>
          </p>
          <p className="text-[11px] text-txt-3 mb-4">Cette action est irréversible. La règle ne sera plus appliquée aux futures évaluations.</p>
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
