import api from './api';
import type { KpiResponse, KpiComparison, HourlyData, DailyStat, OperatorPerformance, SupervisionAlert } from '../types/analytics';

export const analyticsService = {
  getKpis: async (period?: string): Promise<KpiResponse> => {
    const res = await api.get<KpiResponse>('/api/analytics/kpis', { params: { period } });
    return res.data;
  },

  getComparison: async (period?: string): Promise<KpiComparison> => {
    const res = await api.get<KpiComparison>('/api/analytics/kpis/comparison', { params: { period } });
    return res.data;
  },

  getTicketsPerDay: async (period?: string): Promise<HourlyData[]> => {
    const res = await api.get<HourlyData[]>('/api/analytics/tickets-per-day', { params: { period } });
    return res.data;
  },

  getDailyStats: async (period?: string): Promise<DailyStat[]> => {
    const res = await api.get<DailyStat[]>('/api/analytics/daily-stats', { params: { period } });
    return res.data;
  },

  exportCsv: async (period?: string): Promise<void> => {
    const res = await api.get('/api/analytics/export/csv', {
      params: { period },
      responseType: 'blob',
    });
    const url = globalThis.URL.createObjectURL(new Blob([res.data]));
    const a = document.createElement('a');
    a.href = url;
    a.download = `autoassist_analytics_${period || '30d'}.csv`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    globalThis.URL.revokeObjectURL(url);
  },

  // ── Supervision des opérateurs ──

  getOperatorPerformance: async (period?: string): Promise<OperatorPerformance[]> => {
    const res = await api.get<OperatorPerformance[]>('/api/tickets/operators/performance', { params: { period } });
    return res.data;
  },

  getSupervisionAlerts: async (): Promise<SupervisionAlert[]> => {
    const res = await api.get<SupervisionAlert[]>('/api/tickets/supervision/alerts');
    return res.data;
  },
};

