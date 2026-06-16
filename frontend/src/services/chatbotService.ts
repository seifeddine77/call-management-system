import api from './api';

export interface ChatbotStats {
  total_feedback: number;
  positive: number;
  negative: number;
  satisfaction_rate: string;
  recent_feedback: Array<{
    question: string;
    answer_preview: string;
    rating: 'positive' | 'negative';
    group_name: string;
    created_at?: string;
  }>;
  db_persisted: number;
}

interface ChatbotStatsResponse {
  feedback: ChatbotStats;
  total_queries: number;
  avg_response_time_ms: number;
  documents_count: number;
  last_refresh: string | null;
  conversation_groups: number;
}

export const chatbotService = {
  getStats: async (): Promise<ChatbotStats> => {
    const res = await api.get<ChatbotStatsResponse>('/api/chat/stats');
    return res.data.feedback;
  }
};

