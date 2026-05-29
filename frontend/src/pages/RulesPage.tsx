import { useState, useEffect, useCallback } from 'react';
import { AlertTriangle, GripVertical, Pencil, Plus, Settings, Wrench, Users, Zap, ClipboardList, Ruler, FlaskConical, ScrollText, Trash2 } from 'lucide-react';
import toast from 'react-hot-toast';
import { ruleService, type RefItem } from '../services/ruleService';
import { ErrorState, EmptyState } from '../components/ui/States';
import Modal from '../components/ui/Modal';
import type { Rule, CreateRuleRequest, ReferenceData, FieldDef, RuleVersion, EvaluateResponse, RuleCondition } from '../types/rule';
import { DndContext, closestCenter, KeyboardSensor, PointerSensor, useSensor, useSensors, type DragEndEvent } from '@dnd-kit/core';
import { arrayMove, SortableContext, sortableKeyboardCoordinates, useSortable, verticalListSortingStrategy } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';

// ─── CSS Badge/Text Styling Utility Helpers to eliminate nested ternaries ───
const getActionBadgeClass = (action: string) => {
  if (action === 'FAVORABLE') return 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20';
  if (action === 'DEFAVORABLE') return 'bg-rose-500/10 text-rose-400 border-rose-500/20';
  return 'bg-amber-500/10 text-amber-400 border-amber-500/20';
};

const getScoreImpactClass = (impact: number) => {
  if (impact > 0) return 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20';
  if (impact < 0) return 'bg-rose-500/10 text-rose-400 border-rose-500/20';
  return 'bg-txt-3/10 text-txt-3 border-txt-3/20';
};

const getScoreImpactTextClass = (impact: number) => {
  if (impact > 0) return 'text-emerald-400';
  if (impact < 0) return 'text-rose-400';
  return 'text-txt-2';
};

const getScoreImpactHistoryClass = (impact: number) => {
  if (impact > 0) return 'text-emerald-400';
  if (impact < 0) return 'text-rose-400';
  return 'text-txt-3';
};

const getTestDecisionBgClass = (decision: string) => {
  if (decision === 'FAVORABLE') return 'bg-emerald-500/10 border-emerald-500/20';
  if (decision === 'DEFAVORABLE') return 'bg-rose-500/10 border-rose-500/20';
  return 'bg-amber-500/10 border-amber-500/20';
};

const getTestDecisionTextClass = (decision: string) => {
  if (decision === 'FAVORABLE') return 'text-emerald-400';
  if (decision === 'DEFAVORABLE') return 'text-rose-400';
  return 'text-amber-400';
};

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
  { key: 'TYPE_PIECE', label: 'Types de pièces (garanties)', icon: <Wrench size={14} /> },
  { key: 'CATEGORIE_CLIENT', label: 'Catégories client', icon: <Users size={14} /> },
  { key: 'TYPE_SINISTRE', label: 'Types de sinistre (exclusions)', icon: <Zap size={14} /> },
  { key: 'FIELD_DEFINITION', label: 'Champs disponibles (conditions)', icon: <ClipboardList size={14} /> },
] as const;

// ─── Interactive Decision Flow SVG Tree ───
interface DecisionFlowTreeProps {
  rule: Rule;
  testForm: Record<string, string>;
}

function DecisionFlowTree({ rule, testForm }: Readonly<DecisionFlowTreeProps>) {
  const operator = rule.conditions.operator;
  const conditions = rule.conditions.conditions;
  const numConds = conditions.length;

  const evalCondition = (cond: RuleCondition) => {
    const fieldVal = testForm[cond.field];
    if (fieldVal === undefined || fieldVal === '') return null; // not evaluated

    const op = cond.operator;
    const condVal = cond.value;

    const numField = Number(fieldVal);
    const numCond = Number(condVal);
    const isNumeric = !isNaN(numField) && !isNaN(numCond);

    const val1 = isNumeric ? numField : String(fieldVal).trim().toLowerCase();
    const val2 = isNumeric ? numCond : String(condVal).trim().toLowerCase();

    switch (op) {
      case '=':
      case '==':
        return val1 === val2;
      case '!=':
        return val1 !== val2;
      case '<':
        return val1 < val2;
      case '>':
        return val1 > val2;
      case '<=':
        return val1 <= val2;
      case '>=':
        return val1 >= val2;
      default:
        return false;
    }
  };

  // Evaluate all conditions
  const evaluatedConds = conditions.map(c => evalCondition(c));

  // Determine overall status
  let ruleStatus: boolean | null = null;
  if (operator === 'AND') {
    const hasFalse = evaluatedConds.some(v => v === false);
    const allTrue = evaluatedConds.every(v => v === true);
    if (hasFalse) ruleStatus = false;
    else if (allTrue) ruleStatus = true;
  } else {
    // OR
    const hasTrue = evaluatedConds.some(v => v === true);
    const allFalse = evaluatedConds.every(v => v === false);
    if (hasTrue) ruleStatus = true;
    else if (allFalse) ruleStatus = false;
  }

  // Layout parameters
  const canvasWidth = 340;
  const isOr = operator === 'OR';

  // Dynamic height based on layout
  const canvasHeight = isOr ? Math.max(160, numConds * 85) : (numConds + 2) * 85;

  const getBezierPath = (x1: number, y1: number, x2: number, y2: number) => {
    const dx = Math.abs(x2 - x1) * 0.5;
    return `M ${x1} ${y1} C ${x1 + dx} ${y1}, ${x2 - dx} ${y2}, ${x2} ${y2}`;
  };

  return (
    <div className="flex flex-col gap-2">
      <div className="text-[10px] text-txt-3 font-mono flex items-center justify-between">
        <span>Simulateur Interactif</span>
        <span className="flex items-center gap-1">
          Statut : {ruleStatus === true ? (
            <span className="text-emerald-400 font-bold">✓ VALIDÉ</span>
          ) : ruleStatus === false ? (
            <span className="text-rose-400 font-bold">✕ ÉCHOUÉ</span>
          ) : (
            <span className="text-txt-3">EN ATTENTE</span>
          )}
        </span>
      </div>

      <div className="relative border border-border/40 rounded-xl bg-[var(--overlay)] overflow-hidden flex justify-center py-4">
        {/* Glow Effects Behind SVG */}
        <div className="absolute inset-0 pointer-events-none bg-[radial-gradient(ellipse_at_center,rgba(99,102,241,0.03),transparent)]" />

        <svg width={canvasWidth} height={canvasHeight} className="overflow-visible select-none z-10">
          <defs>
            {/* Filters for neon glow effects */}
            <filter id="glow-green" x="-20%" y="-20%" width="140%" height="140%">
              <feGaussianBlur stdDeviation="3" result="blur" />
              <feMerge>
                <feMergeNode in="blur" />
                <feMergeNode in="SourceGraphic" />
              </feMerge>
            </filter>
            <filter id="glow-red" x="-20%" y="-20%" width="140%" height="140%">
              <feGaussianBlur stdDeviation="3" result="blur" />
              <feMerge>
                <feMergeNode in="blur" />
                <feMergeNode in="SourceGraphic" />
              </feMerge>
            </filter>
            <filter id="glow-blue" x="-20%" y="-20%" width="140%" height="140%">
              <feGaussianBlur stdDeviation="3" result="blur" />
              <feMerge>
                <feMergeNode in="blur" />
                <feMergeNode in="SourceGraphic" />
              </feMerge>
            </filter>
          </defs>

          <style>{`
            @keyframes dash {
              to {
                stroke-dashoffset: -20;
              }
            }
            .animate-dash {
              stroke-dasharray: 6, 4;
              animation: dash 1s linear infinite;
            }
          `}</style>

          {isOr ? (
            // ─── OR (Parallel Vertical Fork) Layout ───
            <>
              {/* Connections START -> Conds & Conds -> ACTION */}
              {conditions.map((_, i) => {
                const startX = 50;
                const startY = canvasHeight / 2;
                const condX = 85;
                const condY = i * 85 + (canvasHeight - numConds * 60 - (numConds - 1) * 25) / 2 + 30;

                const actionX = 225;
                const actionY = condY;
                const endX = 265;
                const endY = canvasHeight / 2;

                const status = evaluatedConds[i];

                let strokeColor = 'var(--border)';
                let isAnimated = false;
                let strokeFilter = '';

                if (status === true) {
                  strokeColor = 'rgba(16, 185, 129, 0.8)'; // Favorable / green
                  isAnimated = true;
                  strokeFilter = 'url(#glow-green)';
                } else if (status === false) {
                  strokeColor = 'rgba(239, 68, 68, 0.8)'; // Defavorable / red
                  strokeFilter = 'url(#glow-red)';
                }

                return (
                  <g key={`path-or-${i}`}>
                    {/* Path Start -> Cond */}
                    <path
                      d={getBezierPath(startX, startY, condX, condY)}
                      fill="none"
                      stroke={strokeColor}
                      strokeWidth={status === true ? 2.5 : 1.5}
                      filter={strokeFilter}
                      className={isAnimated ? 'animate-dash' : ''}
                      strokeDasharray={isAnimated ? '4,4' : undefined}
                      style={{ transition: 'stroke 0.3s, stroke-width 0.3s' }}
                    />
                    {/* Path Cond -> Action */}
                    <path
                      d={getBezierPath(actionX, actionY, endX, endY)}
                      fill="none"
                      stroke={strokeColor}
                      strokeWidth={status === true ? 2.5 : 1.5}
                      filter={strokeFilter}
                      className={isAnimated ? 'animate-dash' : ''}
                      strokeDasharray={isAnimated ? '4,4' : undefined}
                      style={{ transition: 'stroke 0.3s, stroke-width 0.3s' }}
                    />
                  </g>
                );
              })}

              {/* Start Node */}
              <foreignObject
                x={0}
                y={canvasHeight / 2 - 18}
                width={50}
                height={36}
              >
                <div className="w-full h-full rounded-xl border border-indigo-500/20 bg-indigo-500/10 flex items-center justify-center font-mono text-[9px] font-bold text-indigo-400 shadow-[0_0_10px_rgba(99,102,241,0.15)]">
                  ENTRÉE
                </div>
              </foreignObject>

              {/* Condition Nodes */}
              {conditions.map((cond, i) => {
                const condY = i * 85 + (canvasHeight - numConds * 60 - (numConds - 1) * 25) / 2;
                const status = evaluatedConds[i];

                let borderStyle = 'border-border/60 bg-[var(--elevated)] text-txt-2';
                if (status === true) {
                  borderStyle = 'border-emerald-500/40 bg-emerald-500/10 text-emerald-400 shadow-[0_0_12px_rgba(16,185,129,0.15)]';
                } else if (status === false) {
                  borderStyle = 'border-rose-500/40 bg-rose-500/10 text-rose-400 shadow-[0_0_12px_rgba(239,68,68,0.15)]';
                }

                return (
                  <foreignObject
                    key={`cond-node-${i}`}
                    x={85}
                    y={condY}
                    width={140}
                    height={60}
                  >
                    <div className={`w-full h-full rounded-xl border flex flex-col items-center justify-center p-1.5 text-center transition-all duration-300 ${borderStyle}`}>
                      <div className="font-mono text-[8px] opacity-75 truncate max-w-full font-bold">{cond.field}</div>
                      <div className="flex items-center gap-1 mt-0.5">
                        <span className="font-bold text-[10px] text-indigo-400">{cond.operator}</span>
                        <span className="text-[10px] font-semibold truncate max-w-[80px]">"{cond.value}"</span>
                      </div>
                      {status !== null && (
                        <div className="text-[8px] mt-0.5 font-bold uppercase tracking-wider">
                          {status ? '✓ Satisfait' : '✕ Échoué'}
                        </div>
                      )}
                    </div>
                  </foreignObject>
                );
              })}

              {/* Action/Decision Node */}
              <foreignObject
                x={265}
                y={canvasHeight / 2 - 25}
                width={75}
                height={50}
              >
                <div className={`w-full h-full rounded-xl border flex flex-col items-center justify-center p-1 transition-all duration-300 ${
                  ruleStatus === true
                    ? 'border-emerald-500/40 bg-emerald-500/10 text-emerald-400 shadow-[0_0_15px_rgba(16,185,129,0.2)]'
                    : ruleStatus === false
                    ? 'border-rose-500/40 bg-rose-500/10 text-rose-400 shadow-[0_0_15px_rgba(239,68,68,0.2)]'
                    : 'border-border/60 bg-[var(--elevated)] text-txt-3'
                }`}>
                  <div className="text-[8px] uppercase tracking-wider opacity-75 font-semibold font-mono">ACTION</div>
                  <div className="text-[9px] font-black truncate max-w-full mt-0.5">{rule.action}</div>
                </div>
              </foreignObject>
            </>
          ) : (
            // ─── AND (Sequential Vertical Chain) Layout ───
            <>
              {/* Connections START -> Cond 0 -> Cond 1 -> ... -> ACTION */}
              {/* Connection START -> Cond 0 */}
              <line
                x1={170}
                y1={46}
                x2={170}
                y2={85}
                stroke={evaluatedConds[0] !== null ? (evaluatedConds[0] ? 'rgba(16, 185, 129, 0.8)' : 'rgba(239, 68, 68, 0.8)') : 'var(--border)'}
                strokeWidth={evaluatedConds[0] === true ? 2.5 : 1.5}
                filter={evaluatedConds[0] !== null ? (evaluatedConds[0] ? 'url(#glow-green)' : 'url(#glow-red)') : undefined}
                className={evaluatedConds[0] === true ? 'animate-dash' : ''}
                strokeDasharray={evaluatedConds[0] === true ? '4,4' : undefined}
                style={{ transition: 'stroke 0.3s, stroke-width 0.3s' }}
              />

              {/* Connections between Conds */}
              {conditions.map((_, i) => {
                if (i === numConds - 1) return null; // last one connects to ACTION

                const statusNext = evaluatedConds[i + 1];

                // In AND, the path to the next condition is active (green) ONLY IF ALL conditions up to here are true!
                const allPrevTrue = evaluatedConds.slice(0, i + 1).every(v => v === true);
                const anyPrevFalse = evaluatedConds.slice(0, i + 1).some(v => v === false);

                let strokeColor = 'var(--border)';
                let isAnimated = false;
                let strokeFilter = '';

                if (allPrevTrue && statusNext === true) {
                  strokeColor = 'rgba(16, 185, 129, 0.8)';
                  isAnimated = true;
                  strokeFilter = 'url(#glow-green)';
                } else if (anyPrevFalse || statusNext === false) {
                  strokeColor = 'rgba(239, 68, 68, 0.8)';
                  strokeFilter = 'url(#glow-red)';
                } else if (allPrevTrue && statusNext === null) {
                  strokeColor = 'rgba(99, 102, 241, 0.5)'; // indigo/blue for active evaluation point
                }

                return (
                  <line
                    key={`path-and-${i}`}
                    x1={170}
                    y1={(i + 1) * 85 + 60}
                    x2={170}
                    y2={(i + 2) * 85}
                    stroke={strokeColor}
                    strokeWidth={isAnimated ? 2.5 : 1.5}
                    filter={strokeFilter}
                    className={isAnimated ? 'animate-dash' : ''}
                    strokeDasharray={isAnimated ? '4,4' : undefined}
                    style={{ transition: 'stroke 0.3s, stroke-width 0.3s' }}
                  />
                );
              })}

              {/* Connection Last Cond -> ACTION */}
              {(() => {
                const allTrue = evaluatedConds.every(v => v === true);
                const hasFalse = evaluatedConds.some(v => v === false);

                let strokeColor = 'var(--border)';
                let isAnimated = false;
                let strokeFilter = '';

                if (allTrue) {
                  strokeColor = 'rgba(16, 185, 129, 0.8)';
                  isAnimated = true;
                  strokeFilter = 'url(#glow-green)';
                } else if (hasFalse) {
                  strokeColor = 'rgba(239, 68, 68, 0.8)';
                  strokeFilter = 'url(#glow-red)';
                }

                return (
                  <line
                    x1={170}
                    y1={numConds * 85 + 60}
                    x2={170}
                    y2={(numConds + 1) * 85}
                    stroke={strokeColor}
                    strokeWidth={isAnimated ? 2.5 : 1.5}
                    filter={strokeFilter}
                    className={isAnimated ? 'animate-dash' : ''}
                    strokeDasharray={isAnimated ? '4,4' : undefined}
                    style={{ transition: 'stroke 0.3s, stroke-width 0.3s' }}
                  />
                );
              })()}

              {/* Start Node */}
              <foreignObject
                x={130}
                y={10}
                width={80}
                height={36}
              >
                <div className="w-full h-full rounded-xl border border-indigo-500/20 bg-indigo-500/10 flex items-center justify-center font-mono text-[9px] font-bold text-indigo-400 shadow-[0_0_10px_rgba(99,102,241,0.15)]">
                  ENTRÉE
                </div>
              </foreignObject>

              {/* Condition Nodes */}
              {conditions.map((cond, i) => {
                const condY = (i + 1) * 85;
                const status = evaluatedConds[i];

                let borderStyle = 'border-border/60 bg-[var(--elevated)] text-txt-2';
                if (status === true) {
                  borderStyle = 'border-emerald-500/40 bg-emerald-500/10 text-emerald-400 shadow-[0_0_12px_rgba(16,185,129,0.15)]';
                } else if (status === false) {
                  borderStyle = 'border-rose-500/40 bg-rose-500/10 text-rose-400 shadow-[0_0_12px_rgba(239,68,68,0.15)]';
                }

                return (
                  <foreignObject
                    key={`cond-node-${i}`}
                    x={80}
                    y={condY}
                    width={180}
                    height={60}
                  >
                    <div className={`w-full h-full rounded-xl border flex flex-col items-center justify-center p-1.5 text-center transition-all duration-300 ${borderStyle}`}>
                      <div className="font-mono text-[8px] opacity-75 truncate max-w-full font-bold">{cond.field}</div>
                      <div className="flex items-center gap-1 mt-0.5">
                        <span className="font-bold text-[10px] text-indigo-400">{cond.operator}</span>
                        <span className="text-[10px] font-semibold truncate max-w-[120px]">"{cond.value}"</span>
                      </div>
                      {status !== null && (
                        <div className="text-[8px] mt-0.5 font-bold uppercase tracking-wider">
                          {status ? '✓ Satisfait' : '✕ Échoué'}
                        </div>
                      )}
                    </div>
                  </foreignObject>
                );
              })}

              {/* Action/Decision Node */}
              <foreignObject
                x={120}
                y={(numConds + 1) * 85}
                width={100}
                height={40}
              >
                <div className={`w-full h-full rounded-xl border flex flex-col items-center justify-center p-1 transition-all duration-300 ${
                  ruleStatus === true
                    ? 'border-emerald-500/40 bg-emerald-500/10 text-emerald-400 shadow-[0_0_15px_rgba(16,185,129,0.2)]'
                    : ruleStatus === false
                    ? 'border-rose-500/40 bg-rose-500/10 text-rose-400 shadow-[0_0_15px_rgba(239,68,68,0.2)]'
                    : 'border-border/60 bg-[var(--elevated)] text-txt-3'
                }`}>
                  <div className="text-[8px] uppercase tracking-wider opacity-75 font-semibold font-mono">ACTION</div>
                  <div className="text-[9px] font-black truncate max-w-full mt-0.5">{rule.action}</div>
                </div>
              </foreignObject>
            </>
          )}
        </svg>
      </div>
    </div>
  );
}

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
    nom: '', description: '', action: 'FAVORABLE', actionMotif: '', priorite: 10, scoreImpact: 0,
    conditions: { operator: 'AND', conditions: [{ field: '', operator: '=', value: '' }] },
  });

  // Edit form
  const [editForm, setEditForm] = useState<CreateRuleRequest>({
    nom: '', description: '', action: 'FAVORABLE', actionMotif: '', priorite: 10, scoreImpact: 0,
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
        nom: '', description: '', action: 'FAVORABLE', actionMotif: '', priorite: 10, scoreImpact: 0,
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
      scoreImpact: rule.scoreImpact ?? 0,
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
      <div>
        <label htmlFor="rule-nom" className="field-label block">Nom *</label>
        <input id="rule-nom" className={`aurora-input ${formErrors.nom ? 'border-rose-500/60' : ''}`} value={f.nom} onChange={e => { setF(p => ({ ...p, nom: e.target.value })); setFormErrors(e2 => ({ ...e2, nom: undefined })); }} placeholder="Ex: Règle garantie mécanique" />
        {formErrors.nom && <span className="text-rose-400 text-[10px] mt-0.5 block">{formErrors.nom}</span>}
      </div>
      <div className="grid grid-cols-2 gap-3">
        <div><label htmlFor="rule-priorite" className="field-label block">Priorité</label><input id="rule-priorite" className="aurora-input" type="number" value={f.priorite} onChange={e => setF(p => ({ ...p, priorite: parseInt(e.target.value) || 10 }))} /></div>
        <div>
          <label htmlFor="rule-scoreImpact" className="field-label block">Impact de Score *</label>
          <span className="text-[9px] text-txt-3 font-mono block mb-1">Plage : -50 à +50</span>
          <input id="rule-scoreImpact" className="aurora-input" type="number" min={-50} max={50} value={f.scoreImpact ?? 0} onChange={e => setF(p => ({ ...p, scoreImpact: Math.max(-50, Math.min(50, parseInt(e.target.value) || 0)) }))} placeholder="Ex: -15 ou +10" />
        </div>
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
            <button onClick={() => setActiveTab('rules')} className={`px-3 py-1.5 rounded-md text-[11px] font-semibold transition-all cursor-pointer flex items-center gap-1 ${activeTab === 'rules' ? 'bg-indigo-500/20 text-indigo-400 border border-indigo-500/30' : 'text-txt-3 hover:text-txt-1'}`}><Ruler size={12} /> Règles</button>
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
            <EmptyState icon="ruler" title="Aucune règle" subtitle="Créez votre première règle métier" action={
              <button onClick={() => setShowCreate(true)} className="btn-cta h-8 px-4 rounded-lg text-xs text-white cursor-pointer">+ Créer</button>
            } />
          ) : (
            rules.map((rule) => (
              <div
                key={rule.id}
                role="button"
                tabIndex={0}
                onClick={() => handleSelectRule(rule)}
                onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); handleSelectRule(rule); } }}
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
                      <span className={`badge border text-[9px] ${getActionBadgeClass(rule.action)}`}>
                        {rule.action}
                      </span>
                      <span className={`badge font-mono text-[9px] ${getScoreImpactClass(rule.scoreImpact)}`}>
                        Impact: {rule.scoreImpact > 0 ? `+${rule.scoreImpact}` : rule.scoreImpact}
                      </span>
                      <span className="font-mono text-[9px] text-txt-3">P{rule.priorite}</span>
                      <span className="font-mono text-[9px] text-txt-3">{rule.conditions.conditions.length} cond.</span>
                    </div>
                  </div>
                  <div className="flex items-center gap-2 flex-shrink-0">
                    <div
                      role="switch"
                      tabIndex={0}
                      aria-checked={rule.actif}
                      onClick={(e) => { e.stopPropagation(); handleToggle(rule.id); }}
                      onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); e.stopPropagation(); handleToggle(rule.id); } }}
                      className={`toggle-switch cursor-pointer ${rule.actif ? 'on' : ''} ${toggling === rule.id ? 'opacity-50' : ''}`}
                    />
                    <button onClick={(e) => { e.stopPropagation(); setDeleteTarget(rule); }} className="w-7 h-7 rounded-lg flex items-center justify-center text-[11px] text-txt-3 hover:text-rose-400 hover:bg-rose-500/10 transition-all cursor-pointer">
                      <Trash2 size={13} className="text-txt-3 hover:text-rose-400" />
                    </button>
                  </div>
                </div>
              </div>
            ))
          )}

          {/* Test panel — Dynamic fields from API */}
          <div className="card-aurora rounded-2xl p-5">
            <h3 className="text-sm font-bold mb-3 flex items-center gap-1.5"><FlaskConical size={14} /> Tester les règles</h3>
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
              <div className={`p-3.5 rounded-xl border ${getTestDecisionBgClass(testResult.decision)}`}>
                <div className={`text-sm font-bold ${getTestDecisionTextClass(testResult.decision)}`}>
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
                <div className="flex items-center gap-4 mb-4 pb-3 border-b border-border/30">
                  <div className="flex flex-col">
                    <span className="text-[10px] text-txt-3 font-mono uppercase">Priorité</span>
                    <span className="text-xs font-bold text-txt-1">P{selectedRule.priorite}</span>
                  </div>
                  <div className="flex flex-col">
                    <span className="text-[10px] text-txt-3 font-mono uppercase">Impact Score</span>
                    <span className={`text-xs font-bold ${getScoreImpactTextClass(selectedRule.scoreImpact)}`}>
                      {selectedRule.scoreImpact > 0 ? `+${selectedRule.scoreImpact}` : selectedRule.scoreImpact}
                    </span>
                  </div>
                  <div className="flex flex-col">
                    <span className="text-[10px] text-txt-3 font-mono uppercase">Version</span>
                    <span className="text-xs font-bold text-indigo-400">v{selectedRule.version}</span>
                  </div>
                  <div className="flex flex-col">
                    <span className="text-[10px] text-txt-3 font-mono uppercase">Créateur</span>
                    <span className="text-xs font-bold text-txt-2 truncate max-w-[100px]">{selectedRule.createdByName || 'N/A'}</span>
                  </div>
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
                    <span className={`font-mono text-[10px] px-2 py-0.5 rounded border ${getActionBadgeClass(selectedRule.action)}`}>ALORS</span>
                    <span className="text-xs ml-2">{selectedRule.action}</span>
                    <div className="font-mono text-[10px] text-txt-3 mt-1">{selectedRule.actionMotif}</div>
                  </div>
                </div>
              </div>
              <div className="card-aurora rounded-2xl p-5">
                <h3 className="text-sm font-bold mb-3 flex items-center gap-1.5"><Zap size={14} className="text-indigo-400 animate-pulse" /> Flux de Décision Interactif</h3>
                <DecisionFlowTree rule={selectedRule} testForm={testForm} />
              </div>
              <div className="card-aurora rounded-2xl p-5">
                <h3 className="text-sm font-bold mb-3 flex items-center gap-1.5"><ScrollText size={14} /> Historique</h3>
                {history.length === 0 ? (
                  <div className="text-xs text-txt-3 text-center py-4">Pas encore de modifications — modifiez cette règle pour créer un historique</div>
                ) : (
                  <div className="space-y-1.5">
                    {history.map((h) => (
                      <div key={h.id} className="flex items-center gap-2 p-2.5 bg-[var(--elevated)] border border-border rounded-lg text-xs">
                        <span className="font-mono text-[10px] text-indigo-400 bg-indigo-500/10 px-2 py-0.5 rounded border border-indigo-500/20">v{h.version}</span>
                        <span className="flex-1 text-txt-2">
                          {h.action}
                          {h.scoreImpact !== undefined && h.scoreImpact !== null ? (
                            <span className={`ml-2 text-[10px] font-mono ${getScoreImpactHistoryClass(h.scoreImpact)}`}>
                              ({h.scoreImpact > 0 ? `+${h.scoreImpact}` : h.scoreImpact})
                            </span>
                          ) : ''}
                        </span>
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
              <EmptyState icon="arrow-left" title="Sélectionner une règle" subtitle="Cliquez sur une règle pour voir ses détails" />
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
                    <span className="flex items-center">{cat.icon}</span> {cat.label}
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
                          <button
                            type="button"
                            onClick={() => handleToggleRef(item.id)}
                            className={`toggle-switch cursor-pointer ${item.actif ? 'on' : ''}`}
                            style={{ transform: 'scale(0.8)', border: 'none', background: 'none', padding: 0 }}
                            aria-label="Activer ou désactiver l'élément"
                          />
                          <button onClick={() => handleDeleteRef(item.id)} className="w-6 h-6 rounded flex items-center justify-center text-txt-3 hover:text-rose-400 hover:bg-rose-500/10 transition-all cursor-pointer"><Trash2 size={12} /></button>
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
              {deleting ? 'Suppression...' : 'Supprimer'}
            </button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
