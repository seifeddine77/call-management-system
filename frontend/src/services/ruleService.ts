import api from './api';
import type { Rule, CreateRuleRequest, RuleVersion, ReferenceData, FieldDef, EvaluateRequest, EvaluateResponse } from '../types/rule';

export interface RefItem {
  id: string;
  category: string;
  code: string;
  label: string;
  description?: string;
  displayOrder: number;
  actif: boolean;
}

export const ruleService = {
  getRules: async (): Promise<Rule[]> => {
    const res = await api.get('/api/rules');
    const data = res.data;
    return Array.isArray(data) ? data : (data.content ?? []);
  },

  getRuleById: async (id: string): Promise<Rule> => {
    const res = await api.get<Rule>(`/api/rules/${id}`);
    return res.data;
  },

  createRule: async (data: CreateRuleRequest): Promise<Rule> => {
    const res = await api.post<Rule>('/api/rules', data);
    return res.data;
  },

  updateRule: async (id: string, data: CreateRuleRequest): Promise<Rule> => {
    const res = await api.put<Rule>(`/api/rules/${id}`, data);
    return res.data;
  },

  deleteRule: async (id: string): Promise<void> => {
    await api.delete(`/api/rules/${id}`);
  },

  toggleRule: async (id: string): Promise<Rule> => {
    const res = await api.patch<Rule>(`/api/rules/${id}/toggle`);
    return res.data;
  },

  getHistory: async (id: string): Promise<RuleVersion[]> => {
    const res = await api.get<RuleVersion[]>(`/api/rules/${id}/history`);
    return res.data;
  },

  getReferences: async (): Promise<ReferenceData> => {
    const res = await api.get('/api/rules/references');
    const d = res.data;
    const extract = (arr: unknown[]): string[] =>
      arr?.map((x: any) => (typeof x === 'string' ? x : x.code)) ?? [];
    const extractFields = (arr: unknown[]): FieldDef[] =>
      arr?.map((x: any) => ({
        field: typeof x === 'string' ? x : x.field,
        label: x.label || x.field || x,
        type: x.type || 'string',
        values: x.values,
      })) ?? [];
    return {
      typesPiece: extract(d.typesPiece),
      categoriesClient: extract(d.categoriesClient),
      typesSinistre: extract(d.typesSinistre),
      operators: extract(d.operateursComparaison),
      fields: extractFields(d.champsDisponibles),
    };
  },

  evaluate: async (data: EvaluateRequest): Promise<EvaluateResponse> => {
    const res = await api.post<EvaluateResponse>('/api/rules/evaluate', data);
    return res.data;
  },

  // ── Reference Items CRUD (CDC 4.B — Paramétrage) ──
  getRefItems: async (category?: string): Promise<RefItem[]> => {
    const params = category ? `?category=${category}&includeInactive=true` : '?includeInactive=true';
    const res = await api.get<RefItem[]>(`/api/rules/reference-items${params}`);
    return res.data;
  },

  createRefItem: async (data: { category: string; code: string; label: string; description?: string; displayOrder?: number }): Promise<RefItem> => {
    const res = await api.post<RefItem>('/api/rules/reference-items', data);
    return res.data;
  },

  updateRefItem: async (id: string, data: { category: string; code: string; label: string; description?: string; displayOrder?: number }): Promise<RefItem> => {
    const res = await api.put<RefItem>(`/api/rules/reference-items/${id}`, data);
    return res.data;
  },

  toggleRefItem: async (id: string): Promise<RefItem> => {
    const res = await api.patch<RefItem>(`/api/rules/reference-items/${id}/toggle`);
    return res.data;
  },

  deleteRefItem: async (id: string): Promise<void> => {
    await api.delete(`/api/rules/reference-items/${id}`);
  },
};
