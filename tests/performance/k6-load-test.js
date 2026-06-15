// ============================================================
// Test de performance / charge — AutoAssist (k6)
// ============================================================
// Outil : k6 (https://k6.io) — métriques envoyées vers Prometheus,
//         visualisées dans Grafana (dashboard "k6 Load Testing").
//
// Cibles NON DESTRUCTIVES (aucune écriture en base) :
//   - Chatbot RAG (ai-service)       → pipeline complet RAG + Gemma 4
//   - Statistiques tickets (lecture) → endpoint rapide, fort débit
//
// Lancement (conteneur Docker, sur le réseau du projet) :
//   docker run --rm -i \
//     --network call-management-system_callmgmt-net \
//     -e K6_PROMETHEUS_RW_SERVER_URL=http://prometheus:9090/api/v1/write \
//     -e K6_PROMETHEUS_RW_TREND_STATS="p(95),p(99),min,max,avg" \
//     -v "${PWD}/tests/performance:/scripts" \
//     grafana/k6 run -o experimental-prometheus-rw /scripts/k6-load-test.js
//
// Puis ouvrir Grafana (http://localhost:3001) → dashboard "k6 Load Testing".
// ============================================================
import http from 'k6/http';
import { check, sleep, group } from 'k6';

const AI_SERVICE = 'http://ai-service:8000';
const TICKET_SERVICE = 'http://ticket-service:8083';

const QUESTIONS = [
  "Quels sont les statuts d'un ticket ?",
  'Comment fonctionne le moteur de regles ?',
  'Quels sont les types de sinistres ?',
  'Comment est cree un ticket automatiquement ?',
];

// Profil de charge : montée progressive puis palier puis descente
export const options = {
  scenarios: {
    // Scénario léger à fort débit (endpoint stats en lecture)
    stats_lecture: {
      executor: 'ramping-vus',
      exec: 'consulterStats',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 20 }, // montée à 20 utilisateurs
        { duration: '1m', target: 20 },  // palier
        { duration: '15s', target: 0 },  // descente
      ],
      tags: { cible: 'stats_tickets' },
    },
    // Scénario lourd (chatbot RAG + Gemma — volontairement peu d'utilisateurs
    // car le LLM est sérialisé côté serveur ; on observe la file d'attente)
    chatbot_rag: {
      executor: 'ramping-vus',
      exec: 'interrogerChatbot',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 3 },
        { duration: '1m', target: 3 },
        { duration: '15s', target: 0 },
      ],
      // Laisse les requêtes Gemma en cours (~10s) se terminer à l'arrêt
      gracefulStop: '30s',
      tags: { cible: 'chatbot_rag' },
    },
  },
  thresholds: {
    // Critères de sortie ISTQB — SLO sur l'endpoint rapide en lecture
    'http_req_duration{cible:stats_tickets}': ['p(95)<500'],  // stats < 500ms p95
    'http_req_failed{cible:stats_tickets}': ['rate<0.01'],    // stats quasi 0 erreur
    // (Le chatbot sature volontairement Gemma : sa latence est observée,
    //  pas soumise à un seuil bloquant.)
  },
};

export function consulterStats() {
  const res = http.get(`${TICKET_SERVICE}/api/tickets/stats`, {
    tags: { name: 'Stats tickets (lecture)' },
  });
  check(res, { 'stats: status 200': (r) => r.status === 200 });
  sleep(Math.random() * 2 + 1);
}

export function interrogerChatbot() {
  const q = QUESTIONS[Math.floor(Math.random() * QUESTIONS.length)];
  // En k6, les paramètres de requête vont dans l'URL (le 2e arg sert aux en-têtes/tags)
  const url = `${AI_SERVICE}/chat/test?q=${encodeURIComponent(q)}`;
  const res = http.get(url, {
    tags: { name: 'Chatbot RAG /chat/test' },
    timeout: '120s',
  });
  check(res, { 'chatbot: status 200': (r) => r.status === 200 });
  sleep(Math.random() * 2 + 1);
}
