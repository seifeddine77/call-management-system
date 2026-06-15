# Tests de performance — AutoAssist

Cette partie couvre les **tests de performance** (type de test non-fonctionnel ISTQB)
à l'aide de deux outils professionnels :

| Outil | Interface | Usage |
|-------|-----------|-------|
| **Locust** | Web temps réel (`localhost:8089`) | Démo interactive, curseur d'utilisateurs, graphes live |
| **k6** | Métriques → **Grafana** (`localhost:3001`) | Test scripté, intégré au monitoring existant |

> ⚠️ **Cibles non destructives.** Les tests ciblent le chatbot (`/chat/test`, aucune
> écriture en base) et des endpoints en lecture seule. Les **temps de décision**
> (voies règles / IA / cache) sont mesurés séparément par un protocole contrôlé,
> car `POST /api/decisions/analyze` crée un enregistrement par appel.

---

## 1. Locust (interface web temps réel)

```bash
docker run --rm -p 8089:8089 \
  -v "${PWD}/tests/performance:/mnt/locust" \
  --network call-management-system_callmgmt-net \
  locustio/locust -f /mnt/locust/locustfile.py
```

1. Ouvrir <http://localhost:8089>
2. Régler **Number of users** (ex. 50) et **Spawn rate** (ex. 5/s)
3. Cliquer **Start** et observer en direct : RPS, temps de réponse (médiane / p95),
   taux d'échec, graphes temporels.
4. Onglet **Charts** pour les captures d'écran du rapport ; **Download Data** pour le CSV.

## 2. k6 + Grafana (test scripté, monitoring intégré)

```bash
docker run --rm -i \
  --network call-management-system_callmgmt-net \
  -e K6_PROMETHEUS_RW_SERVER_URL=http://prometheus:9090/api/v1/write \
  -e K6_PROMETHEUS_RW_TREND_STATS="p(95),p(99),min,max,avg" \
  -v "${PWD}/tests/performance:/scripts" \
  grafana/k6 run -o experimental-prometheus-rw /scripts/k6-load-test.js
```

- Profil de charge : montée progressive (ramp-up) → palier → descente.
- Visualisation : Grafana → dashboard **« k6 Load Testing »**.
- Critères de sortie (thresholds) définis dans le script : `p95 < 500ms` sur les
  stats, `< 5%` d'erreurs.

## 3. Temps de décision par voie (cache / règles / IA)

Mesure reproductible du routage du `decision-service` : Cache (Redis) → Moteur de
règles → IA (Gemma). Chaque appel est étiqueté par les champs `source`/`cached`
renvoyés par le service. **Non destructif** : les enregistrements de test sont
supprimés automatiquement à la fin.

```powershell
pwsh ./tests/performance/decision-latency.ps1
# ou en ajustant le nombre d'appels par voie :
pwsh ./tests/performance/decision-latency.ps1 -AiRuns 5 -RulesRuns 8 -CacheRuns 8
```

Résultat type (p95) : **Cache ~10-20 ms**, **Règles ~30-40 ms**, **IA ~10 s** —
soit un routage à 3 niveaux où le LLM coûteux n'est sollicité qu'en dernier recours
(~250× plus lent que les règles, ~750× plus lent que le cache).

> Pourquoi un script séparé et non k6/Locust : `POST /api/decisions/analyze` crée un
> enregistrement par appel. On le mesure donc de façon contrôlée + auto-nettoyée,
> plutôt qu'en charge massive.

---

## Interprétation attendue (architecture)

- **Endpoint stats** (lecture) : fort débit, faible latence.
- **Chatbot RAG** : latence élevée (~10-15 s) qui **augmente sous concurrence** car
  le LLM Gemma est **sérialisé** côté serveur (`asyncio.Lock` dans `rag_service.py`).
  Ce comportement est attendu et documenté — il illustre la limite d'un LLM local
  mono-instance, à opposer au routage cache/règles/IA du service de décision.
