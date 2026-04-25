/* ── Rule Types ── */
export interface Rule {
  id: string;
  nom: string;
  description: string;
  conditions: RuleConditions;
  action: 'FAVORABLE' | 'DEFAVORABLE' | 'ETUDE';
  actionMotif: string;
  priorite: number;
  actif: boolean;
  version: number;
  createdByName: string;
  createdAt: string;
}

export interface RuleConditions {
  operator: 'AND' | 'OR';
  conditions: RuleCondition[];
}

export interface RuleCondition {
  field: string;
  operator: string;
  value: string;
}

export interface CreateRuleRequest {
  nom: string;
  description: string;
  conditions: RuleConditions;
  action: 'FAVORABLE' | 'DEFAVORABLE' | 'ETUDE';
  actionMotif: string;
  priorite: number;
}

export interface RuleVersion {
  id: string;
  ruleId: string;
  conditions: RuleConditions;
  action: string;
  version: number;
  changedByName: string;
  changedAt: string;
}

export interface ReferenceData {
  typesPiece: string[];
  categoriesClient: string[];
  typesSinistre: string[];
  operators: string[];
  fields: FieldDef[];
}

export interface FieldDef {
  field: string;   // ex: "typePiece"
  label: string;   // ex: "Type de pièce"
  type: 'string' | 'number' | 'enum';
  values?: string[]; // enum values if type=enum
}

export interface EvaluateRequest {
  typePiece?: string;
  ageVehiculeAns?: number;
  kilometrage?: number;
  categorieClient?: string;
  typeSinistre?: string;
  data?: Record<string, unknown>;
}

export interface EvaluateResponse {
  decision: 'FAVORABLE' | 'DEFAVORABLE' | 'ETUDE';
  score: number;
  motif: string;
  rulesApplied: string[];
  totalRulesEvaluated: number;
  matchedRules: number;
  ambiguous: boolean;
}

