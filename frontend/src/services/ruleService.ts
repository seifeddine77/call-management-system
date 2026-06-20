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
    // Sans paramètre, le backend applique la pagination Spring par défaut (20 éléments)
    // et la page « Moteur de règles » n'affichait que les 20 premières règles sur 59+.
    // On demande explicitement une grande page pour récupérer toutes les règles.
    const res = await api.get('/api/rules?page=0&size=1000');
    const data = res.data;
    return Array.isArray(data) ? data : (data.content ?? []);
  },

  getRuleById: async (id: string): Promise<Rule> => {
    const res = await api.get<Rule>(`/api/rules/${id}`);
    return res.data;
  },

  createRule: async (data: CreateRuleRequest): Promise<Rule> => {
    const cleaned = {
      ...data,
      conditions: {
        ...data.conditions,
        conditions: data.conditions.conditions.map(({ field, operator, value }) => ({ field, operator, value }))
      }
    };
    const res = await api.post<Rule>('/api/rules', cleaned);
    return res.data;
  },

  updateRule: async (id: string, data: CreateRuleRequest): Promise<Rule> => {
    const cleaned = {
      ...data,
      conditions: {
        ...data.conditions,
        conditions: data.conditions.conditions.map(({ field, operator, value }) => ({ field, operator, value }))
      }
    };
    const res = await api.put<Rule>(`/api/rules/${id}`, cleaned);
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
    const extract = (arr: unknown[] | undefined): string[] =>
      (arr || []).map((x) => {
        if (typeof x === 'string') return x;
        if (x && typeof x === 'object' && 'code' in x) {
          return (x as { code: string }).code;
        }
        return '';
      });
    const extractFields = (arr: unknown[] | undefined): FieldDef[] =>
      (arr || []).map((x) => {
        if (typeof x === 'string') {
          return { field: x, label: x, type: 'string' };
        }
        const obj = x as { field?: string; label?: string; type?: string; values?: string[] } || {};
        const rawType = obj.type || 'string';
        let type: 'string' | 'number' | 'enum' = 'string';
        if (rawType === 'number') {
          type = 'number';
        } else if (rawType === 'enum') {
          type = 'enum';
        }
        return {
          field: obj.field || '',
          label: obj.label || obj.field || '',
          type,
          values: obj.values,
        };
      });
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
