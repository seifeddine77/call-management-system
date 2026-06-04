"""
Evaluation quantitative du chatbot RAG AutoAssist — v2 (post-ameliorations).

Ameliorations v2 evaluees :
  #9  Chunking avec overlap (150 chars)
  #10 Query Expansion (synonymes domaine assurance)
  #11 Retrieval elargi (top-10 -> re-rank -> top-5)
  #12 Contexte LLM enrichi (800 chars/doc)

Metriques :
  - ROUGE-1        : recouvrement des unigrammes
  - Similarite Sem : cosine similarity via embeddings multilingues

Usage:
    python evaluate_rag.py
"""
import sys
import io

# Force UTF-8 output on Windows
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")

import json
import re
import os
from collections import Counter
from datetime import datetime

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from app.rag.knowledge_base import KNOWLEDGE_DOCUMENTS

try:
    import chromadb
    from chromadb.utils import embedding_functions
except ImportError:
    print("chromadb non installe. Executez : pip install chromadb")
    sys.exit(1)

try:
    from sentence_transformers import SentenceTransformer, util as st_util
except ImportError:
    print("sentence-transformers non installe. Executez : pip install sentence-transformers")
    sys.exit(1)


# =============================================================
# 1. GOLD STANDARD — 20 questions
# =============================================================

GOLD_STANDARD = [
    # Processus metier — 4 questions
    {
        "id": 1, "category": "Processus metier (flux, statuts)",
        "question": "Comment creer un ticket dans AutoAssist ?",
        "reference": (
            "Les tickets dans AutoAssist sont crees automatiquement via 3 canaux d'acquisition : "
            "CALL (Twilio) avec transcription Whisper et analyse NLP, "
            "EMAIL (IMAP) avec extraction du sujet et du corps du message, "
            "et WEB (formulaire public) avec nom, telephone et description. "
            "Le ticket recoit un numero unique au format T-AAAAMMJJ-XXXXX et le statut NOUVEAU. "
            "L'operateur ne cree pas manuellement les tickets."
        ),
        "source_doc_id": "guide_creer_ticket",
    },
    {
        "id": 2, "category": "Processus metier (flux, statuts)",
        "question": "Quels sont les statuts possibles d'un ticket ?",
        "reference": (
            "Un ticket passe par 4 statuts : "
            "NOUVEAU (ticket vient d'etre cree, pas encore pris en charge), "
            "EN_COURS (un operateur a ete assigne et travaille sur le ticket), "
            "RESOLU (le probleme a ete resolu), "
            "FERME (ticket clos definitivement, etat terminal). "
            "Transitions autorisees : NOUVEAU vers EN_COURS ou FERME, "
            "EN_COURS vers RESOLU ou FERME, RESOLU vers FERME."
        ),
        "source_doc_id": "flux_statuts",
    },
    {
        "id": 3, "category": "Processus metier (flux, statuts)",
        "question": "Comment sont calculees les priorites des tickets ?",
        "reference": (
            "La priorite determine l'ordre de traitement dans la file d'attente. "
            "Facteurs : categorie client (VIP > PREMIUM > STANDARD), "
            "type de sinistre (ACCIDENT et DEFAUT_FABRICATION sont prioritaires), "
            "anciennete des tickets (FIFO), et canal (les appels telephoniques CALL sont plus urgents). "
            "Le superviseur peut reorganiser manuellement."
        ),
        "source_doc_id": "guide_priorites_tickets",
    },
    {
        "id": 4, "category": "Processus metier (flux, statuts)",
        "question": "Comment resoudre et fermer un ticket ?",
        "reference": (
            "Pour resoudre un ticket : ouvrir le ticket EN_COURS, verifier qu'une decision a ete prise, "
            "si FAVORABLE la piece a ete commandee et le client informe, si DEFAVORABLE le courrier de refus envoye. "
            "Cliquer Resoudre passe le statut a RESOLU. Pour fermer : cliquer Fermer passe a FERME. "
            "Un ticket FERME ne peut plus etre modifie. "
            "Le courrier PDF est automatiquement genere et envoye par email."
        ),
        "source_doc_id": "resoudre_fermer_ticket",
    },

    # Aide a la decision IA — 4 questions
    {
        "id": 5, "category": "Aide a la decision IA",
        "question": "Comment fonctionne le pipeline decisionnel ?",
        "reference": (
            "Le module d'aide a la decision fonctionne en 3 etapes : "
            "Etape 1 - Cache Redis : verification si une demande identique a deja ete traitee. "
            "Etape 2 - Moteur de regles : les regles metier sont evaluees, si le score est >= 60 la decision est claire. "
            "Etape 3 - IA Gemma 4 : si le score est < 60 (cas ambigu), le LLM analyse le contexte complet."
        ),
        "source_doc_id": "ia_decision",
    },
    {
        "id": 6, "category": "Aide a la decision IA",
        "question": "Quelle est la difference entre FAVORABLE et DEFAVORABLE ?",
        "reference": (
            "FAVORABLE : la reclamation est acceptee, la piece sera remplacee ou reparee. "
            "Conditions : vehicule recent, kilometrage raisonnable, sinistre couvert, client VIP/PREMIUM. "
            "DEFAVORABLE : la reclamation est refusee, le client ne beneficie pas de la prise en charge. "
            "Motifs : vehicule trop ancien (>10 ans), kilometrage trop eleve (>200000 km), piece d'usure. "
            "ETUDE : cas complexe necessitant analyse manuelle. "
            "L'operateur peut toujours accepter ou rejeter la recommandation IA."
        ),
        "source_doc_id": "decision_favorable_defavorable",
    },
    {
        "id": 7, "category": "Aide a la decision IA",
        "question": "Comment fonctionne le scoring et quelles sont les decisions possibles ?",
        "reference": (
            "Score 0-59 : resultat ambigu, l'IA Gemma 4 est consultee. "
            "Score 60-100 : resultat clair, decision donnee par les regles. "
            "Decisions possibles : FAVORABLE (acceptee), DEFAVORABLE (refusee), ETUDE (analyse manuelle). "
            "Source : RULES (moteur de regles), AI (Gemma 4), HYBRID, CACHE."
        ),
        "source_doc_id": "ia_scoring",
    },
    {
        "id": 8, "category": "Aide a la decision IA",
        "question": "Comment fonctionne l'analyse NLP automatique ?",
        "reference": (
            "L'analyse NLP extrait automatiquement du texte des tickets : "
            "la reference de piece (detectee par regex ou par mots-cles), "
            "le type de sinistre (PANNE, ACCIDENT, USURE, DEFAUT_FABRICATION, VOL), "
            "le sentiment du client (POSITIF, NEGATIF, NEUTRE), "
            "et les mots-cles (extraits par spaCy via entites nommees et noms communs). "
            "L'analyse est declenchee automatiquement apres la transcription audio."
        ),
        "source_doc_id": "ia_nlp",
    },

    # Regles metier — 3 questions
    {
        "id": 9, "category": "Regles metier",
        "question": "Comment fonctionnent les regles metier ?",
        "reference": (
            "Les regles metier sont configurees par l'administrateur dans le module Regles. "
            "Chaque regle contient un nom, description, priorite, decision (FAVORABLE ou DEFAVORABLE), "
            "score, et conditions au format JSONB. "
            "Les conditions supportent AND et OR. "
            "Operateurs de comparaison : =, !=, <, >, <=, >=, CONTAINS, IN. "
            "Champs evalues : typePiece, ageVehiculeAns, kilometrage, categorieClient, typeSinistre, pieceReference."
        ),
        "source_doc_id": "regles_fonctionnement",
    },
    {
        "id": 10, "category": "Regles metier",
        "question": "Comment configurer une regle metier avec un operateur IN ?",
        "reference": (
            "Les regles supportent l'operateur IN (dans une liste) dans les conditions JSONB. "
            "L'operateur IN verifie si la valeur du champ appartient a une liste de valeurs autorisees. "
            "Exemple d'utilisation : typeSinistre IN [ACCIDENT, DEFAUT_FABRICATION] "
            "pour cibler plusieurs types de sinistres dans une seule regle. "
            "La configuration se fait via le module Regles dans l'interface d'administration."
        ),
        "source_doc_id": "regles_fonctionnement",
    },
    {
        "id": 11, "category": "Regles metier",
        "question": "Quels sont des exemples de regles metier typiques ?",
        "reference": (
            "Exemples de regles : "
            "Vehicule ancien : SI ageVehiculeAns > 10 ET typePiece = MECANIQUE ALORS DEFAVORABLE (score 80). "
            "Client VIP : SI categorieClient = VIP ET typeSinistre = ACCIDENT ALORS FAVORABLE (score 90). "
            "Kilometrage eleve : SI kilometrage > 200000 ALORS DEFAVORABLE (score 70). "
            "Defaut fabrication : SI typeSinistre = DEFAUT_FABRICATION ET ageVehiculeAns < 3 ALORS FAVORABLE (score 95)."
        ),
        "source_doc_id": "regles_exemples",
    },

    # Procedures operationnelles — 4 questions
    {
        "id": 12, "category": "Procedures operationnelles",
        "question": "Quelle est la procedure pour traiter un sinistre de type VOL ?",
        "reference": (
            "Pour une reclamation de type VOL : "
            "exiger un depot de plainte (PV de police), "
            "verifier si le client a un contrat avec option vol/vandalisme, "
            "les clients VIP ont la couverture vol incluse, "
            "photos obligatoires de l'etat du vehicule, "
            "FAVORABLE si PV fourni et contrat couvre le vol, "
            "DEFAVORABLE si pas de PV ou contrat sans option vol, "
            "delai de declaration : 48h."
        ),
        "source_doc_id": "proc_vol",
    },
    {
        "id": 13, "category": "Procedures operationnelles",
        "question": "Comment traiter une reclamation de type PANNE ?",
        "reference": (
            "Pour une reclamation de type PANNE : "
            "identifier la piece defectueuse, verifier l'age du vehicule et le kilometrage. "
            "Les pannes sur vehicules recents (< 5 ans) avec faible kilometrage (< 100000 km) sont FAVORABLE. "
            "Les pannes sur vehicules anciens (> 10 ans) ou kilometrage eleve (> 200000 km) sont DEFAVORABLE. "
            "En cas de doute, lancer l'analyse IA."
        ),
        "source_doc_id": "proc_panne",
    },
    {
        "id": 14, "category": "Procedures operationnelles",
        "question": "Comment traiter une reclamation de type USURE ?",
        "reference": (
            "Pour une reclamation USURE : l'usure est normale si vehicule > 5 ans ou > 100 000 km. "
            "Pieces d'usure courantes : plaquettes de frein, embrayage, courroie, amortisseurs. "
            "Generalement DEFAVORABLE car l'usure n'est pas couverte par la garantie standard. "
            "Exception : client VIP ou PREMIUM avec contrat etendu, "
            "ou usure prematuree sur vehicule recent (< 2 ans) a traiter comme DEFAUT_FABRICATION."
        ),
        "source_doc_id": "proc_usure",
    },
    {
        "id": 15, "category": "Procedures operationnelles",
        "question": "Quelle est la procedure complete de traitement d'une reclamation ?",
        "reference": (
            "Procedure complete : "
            "1. Reception automatique via telephone, email ou formulaire web. "
            "2. Creation automatique du ticket. "
            "3. File d'attente avec statut NOUVEAU. "
            "4. Assignation par le superviseur (statut EN_COURS). "
            "5. Ajout de photos si necessaire. "
            "6. Analyse decisionnelle IA (Cache Redis -> Regles -> IA Gemma 4). "
            "7. Decision FAVORABLE, DEFAVORABLE ou ETUDE. "
            "8. Validation par l'operateur. "
            "9. Courrier PDF genere et envoye. "
            "10. Cloture (RESOLU puis FERME)."
        ),
        "source_doc_id": "proc_reclamation",
    },

    # Architecture technique — 2 questions
    {
        "id": 16, "category": "Architecture technique",
        "question": "Quelle est l'architecture technique d'AutoAssist ?",
        "reference": (
            "AutoAssist est une plateforme microservices composee de : "
            "auth-service (JWT, Spring Boot), organisation-service (agences), "
            "ticket-service (tickets, Twilio), rules-service (moteur de regles), "
            "ai-service (RAG, Whisper, NLP, FastAPI Python), "
            "decision-service (cache Redis -> regles -> IA), "
            "notification-service (email, PDF), analytics-service (KPIs, CSV), "
            "whatsapp-service (chatbot), frontend React, mobile Flutter. "
            "Infrastructure : Eureka, Gateway, RabbitMQ, PostgreSQL, Redis, ChromaDB."
        ),
        "source_doc_id": "arch_microservices",
    },
    {
        "id": 17, "category": "Architecture technique",
        "question": "Comment les microservices communiquent entre eux ?",
        "reference": (
            "Communication synchrone (REST via Feign) : les services s'appellent via l'API Gateway (port 8080), "
            "Eureka assure la decouverte automatique. "
            "Communication asynchrone (RabbitMQ) : les evenements sont publies sur des queues. "
            "Exemples : ticket.created, ticket.updated, agence.created, decision.made. "
            "Le notification-service ecoute les evenements pour envoyer des emails, "
            "le whatsapp-service ecoute pour creer des groupes, "
            "l'analytics-service ecoute pour mettre a jour les KPIs."
        ),
        "source_doc_id": "arch_communication",
    },

    # FAQ et depannage — 3 questions
    {
        "id": 18, "category": "FAQ et depannage",
        "question": "Pourquoi le chatbot est lent a repondre ?",
        "reference": (
            "Le temps de reponse depend du type de requete : "
            "commandes rapides (/stats, /ticket) : 1-3 secondes. "
            "Questions en langage naturel : 8-25 secondes (pipeline complet). "
            "Le pipeline comprend : recherche vectorielle ChromaDB (~200ms), "
            "donnees temps reel (~500ms), inference Gemma 4 sur CPU (~5-20s). "
            "La premiere requete prend plus de temps (warmup du modele). "
            "Les requetes suivantes sont plus rapides grace au cache."
        ),
        "source_doc_id": "faq_temps_reponse",
    },
    {
        "id": 19, "category": "FAQ et depannage",
        "question": "Que faire si la decision IA est incorrecte ?",
        "reference": (
            "L'operateur peut REJETER la recommandation IA en cliquant sur Rejeter. "
            "Il doit fournir un motif de rejet obligatoire pour tracabilite. "
            "La decision finale est toujours celle de l'humain, pas de l'IA. "
            "Les rejets sont enregistres pour ameliorer les regles metier. "
            "Si les decisions sont souvent incorrectes, l'administrateur doit ajuster les regles."
        ),
        "source_doc_id": "faq_decision_incorrecte",
    },
    {
        "id": 20, "category": "FAQ et depannage",
        "question": "Comment exporter les donnees analytics en CSV ?",
        "reference": (
            "AutoAssist propose l'export CSV des tickets depuis le tableau de bord Analytics : "
            "cliquer sur Exporter CSV pour telecharger la liste complete des tickets. "
            "Chaque decision peut etre exportee en PDF professionnel. "
            "Le tableau de bord affiche les KPIs avec comparaison periode-sur-periode. "
            "Via WhatsApp : /stats donne un resume rapide. "
            "Toutes les donnees sont accessibles via l'API REST pour integration avec Power BI ou Excel."
        ),
        "source_doc_id": "guide_export_donnees",
    },
]


# =============================================================
# 2. METRIQUES
# =============================================================

def compute_rouge1(generated: str, reference: str) -> dict:
    def tokenize(text):
        return re.findall(r'\w+', text.lower())
    gen_tokens = tokenize(generated)
    ref_tokens = tokenize(reference)
    gen_counts = Counter(gen_tokens)
    ref_counts = Counter(ref_tokens)
    overlap = sum((gen_counts & ref_counts).values())
    precision = overlap / len(gen_tokens) if gen_tokens else 0.0
    recall = overlap / len(ref_tokens) if ref_tokens else 0.0
    f1 = (2 * precision * recall) / (precision + recall) if (precision + recall) > 0 else 0.0
    return {"precision": precision, "recall": recall, "f1": f1}


def compute_cosine_similarity(model, text1: str, text2: str) -> float:
    emb1 = model.encode(text1, convert_to_tensor=True)
    emb2 = model.encode(text2, convert_to_tensor=True)
    return float(st_util.cos_sim(emb1, emb2)[0][0])


# =============================================================
# 3. RAG PIPELINE v2 (avec les 4 ameliorations)
# =============================================================

# Amelioration #9
CHUNK_MAX_CHARS = 600
CHUNK_OVERLAP_CHARS = 150

# Amelioration #12
CONTEXT_MAX_CHARS = 800

# Amelioration #10 — Synonymes domaine
_QUERY_SYNONYMS = {
    "ticket": ["dossier", "reclamation"],
    "statut": ["etat", "situation"],
    "creer": ["ouvrir", "soumettre"],
    "traiter": ["gerer", "resoudre"],
    "favorable": ["accepte", "approuve"],
    "defavorable": ["refuse", "rejete"],
    "decision": ["verdict", "recommandation"],
    "scoring": ["score", "note"],
    "panne": ["dysfonctionnement", "defaillance"],
    "erreur": ["bug", "incident"],
    "lent": ["lenteur", "performance"],
    "sinistre": ["accident", "dommage"],
    "vol": ["vol de vehicule"],
    "couverture": ["garantie", "indemnisation"],
    "regle": ["condition", "critere"],
    "operateur": ["agent", "conseiller"],
}


def expand_query(question: str) -> str:
    """Amelioration #10: expand query with domain synonyms."""
    lower = question.lower()
    terms = []
    for term, synonyms in _QUERY_SYNONYMS.items():
        if term in lower:
            terms.extend(synonyms[:2])
    if terms:
        return question + " " + " ".join(terms)
    return question


def chunk_document_v2(doc: dict) -> list:
    """Amelioration #9: chunking with overlap."""
    full_text = f"{doc['title']}. {doc['content']}"
    if len(full_text) <= CHUNK_MAX_CHARS:
        return [doc]

    sentences = re.split(r'(?<=[.!?])\s+', full_text)
    chunks = []
    current_chunk = ""
    chunk_idx = 0
    last_sentences = []

    for sentence in sentences:
        if len(current_chunk) + len(sentence) > CHUNK_MAX_CHARS and current_chunk:
            chunks.append({
                "id": f"{doc['id']}_chunk{chunk_idx}",
                "category": doc["category"],
                "title": doc["title"],
                "content": current_chunk.strip(),
            })
            chunk_idx += 1
            # Build overlap from previous sentences
            overlap_text = ""
            for prev in reversed(last_sentences):
                candidate = prev + " " + overlap_text
                if len(candidate) <= CHUNK_OVERLAP_CHARS:
                    overlap_text = candidate.strip()
                else:
                    break
            current_chunk = overlap_text + " " + sentence if overlap_text else sentence
        else:
            current_chunk += " " + sentence
        last_sentences.append(sentence)
        if len(last_sentences) > 5:
            last_sentences.pop(0)

    if current_chunk.strip():
        chunks.append({
            "id": f"{doc['id']}_chunk{chunk_idx}",
            "category": doc["category"],
            "title": doc["title"],
            "content": current_chunk.strip(),
        })
    return chunks


def keyword_score(query: str, document: str) -> float:
    query_words = set(re.findall(r'\w{3,}', query.lower()))
    doc_words = set(re.findall(r'\w{3,}', document.lower()))
    if not query_words:
        return 0.0
    return len(query_words & doc_words) / len(query_words)


def init_chromadb_v2(collection_name: str = "autoassist_eval_v2"):
    """Init ChromaDB with v2 chunking (with overlap)."""
    print("Initialisation ChromaDB v2 (chunking avec overlap)...")
    client = chromadb.Client()
    embedding_fn = embedding_functions.SentenceTransformerEmbeddingFunction(
        model_name="paraphrase-multilingual-MiniLM-L12-v2"
    )

    # Remove existing if present
    try:
        client.delete_collection(collection_name)
    except Exception:
        pass

    collection = client.create_collection(
        name=collection_name,
        embedding_function=embedding_fn,
    )

    ids, documents, metadatas = [], [], []
    total_chunks = 0

    for doc in KNOWLEDGE_DOCUMENTS:
        chunks = chunk_document_v2(doc)
        for chunk in chunks:
            ids.append(chunk["id"])
            documents.append(chunk["content"])
            metadatas.append({"category": chunk["category"], "title": chunk["title"]})
            total_chunks += 1

    collection.add(ids=ids, documents=documents, metadatas=metadatas)
    print(f"OK: {len(KNOWLEDGE_DOCUMENTS)} docs -> {total_chunks} chunks indexes (v2 avec overlap)")
    return collection


def retrieve_answer_v2(collection, query: str) -> str:
    """Ameliorations #10 + #11 + #12: expand + top-10 -> top-5 + 800 chars."""
    # #10 expand
    expanded = expand_query(query)

    # #11 retrieve 10 candidates
    results = collection.query(query_texts=[expanded], n_results=10)

    docs = []
    for i in range(len(results["ids"][0])):
        doc = {
            "content": results["documents"][0][i],
            "distance": results["distances"][0][i] if results.get("distances") else 1.0,
        }
        semantic_score = max(0, 1 - (doc["distance"] / 2.0))
        kw_score = keyword_score(query, doc["content"])  # keyword on original query
        doc["combined_score"] = (semantic_score * 0.7) + (kw_score * 0.3)
        docs.append(doc)

    docs.sort(key=lambda d: d["combined_score"], reverse=True)

    # #11 top-5, #12 800 chars
    return " ".join(d["content"][:CONTEXT_MAX_CHARS] for d in docs[:5])


# =============================================================
# 4. EVALUATION PRINCIPALE
# =============================================================

def run_evaluation():
    print("=" * 72)
    print("  EVALUATION RAG AutoAssist — v2 (post-ameliorations)")
    print(f"  Date : {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"  Gold Standard : {len(GOLD_STANDARD)} questions")
    print("=" * 72)
    print()
    print("Ameliorations v2 actives :")
    print("  #9  Chunking avec overlap (150 chars)")
    print("  #10 Query Expansion (synonymes domaine assurance)")
    print("  #11 Retrieval elargi : top-10 -> re-rank -> top-5")
    print("  #12 Contexte LLM enrichi : 800 chars/doc (etait 500)")
    print()

    collection = init_chromadb_v2()

    print("Chargement du modele Sentence-BERT...")
    sbert_model = SentenceTransformer("paraphrase-multilingual-MiniLM-L12-v2")
    print("OK\n")

    results = []
    category_scores = {}

    print(f"{'#':<3} {'Question':<48} {'R-1 F1':>8} {'R-1 Rec':>8} {'Cos.Sim':>8}")
    print("-" * 80)

    for item in GOLD_STANDARD:
        question = item["question"]
        reference = item["reference"]
        category = item["category"]

        rag_answer = retrieve_answer_v2(collection, question)

        rouge = compute_rouge1(rag_answer, reference)
        cosine_sim = compute_cosine_similarity(sbert_model, rag_answer, reference)

        result = {
            "id": item["id"],
            "question": question,
            "category": category,
            "rouge1_f1": rouge["f1"],
            "rouge1_precision": rouge["precision"],
            "rouge1_recall": rouge["recall"],
            "cosine_similarity": cosine_sim,
        }
        results.append(result)

        if category not in category_scores:
            category_scores[category] = {"rouge1_f1": [], "rouge1_recall": [], "cosine": [], "count": 0}
        category_scores[category]["rouge1_f1"].append(rouge["f1"])
        category_scores[category]["rouge1_recall"].append(rouge["recall"])
        category_scores[category]["cosine"].append(cosine_sim)
        category_scores[category]["count"] += 1

        print(f"{item['id']:<3} {question[:46]:<48} {rouge['f1']:>7.0%} {rouge['recall']:>7.0%} {cosine_sim:>7.0%}")

    # Par categorie
    print("\n" + "=" * 80)
    print("  RESULTATS PAR CATEGORIE")
    print("=" * 80)
    print(f"{'Categorie':<42} {'Nb Q':>4} {'R-1 F1':>9} {'R-1 Rec':>9} {'Cos.Sim':>9}")
    print("-" * 80)

    for cat, scores in category_scores.items():
        avg_f1 = sum(scores["rouge1_f1"]) / len(scores["rouge1_f1"])
        avg_recall = sum(scores["rouge1_recall"]) / len(scores["rouge1_recall"])
        avg_cosine = sum(scores["cosine"]) / len(scores["cosine"])
        print(f"{cat:<42} {scores['count']:>4} {avg_f1:>8.0%} {avg_recall:>8.0%} {avg_cosine:>8.0%}")

    all_f1 = [r["rouge1_f1"] for r in results]
    all_recall = [r["rouge1_recall"] for r in results]
    all_cosine = [r["cosine_similarity"] for r in results]
    avg_f1_global = sum(all_f1) / len(all_f1)
    avg_recall_global = sum(all_recall) / len(all_recall)
    avg_cosine_global = sum(all_cosine) / len(all_cosine)

    # Semantic Adequacy Rate (Cosine Similarity >= 0.70)
    semantic_adequacy_global = sum(1 for r in results if r["cosine_similarity"] >= 0.70) / len(results)

    print("-" * 80)
    print(f"{'GLOBAL (20 questions)':<42} {len(results):>4} {avg_f1_global:>8.0%} {avg_recall_global:>8.0%} {avg_cosine_global:>8.0%}")

    print()
    print("=" * 80)
    print("  SCORES v2 vs BASELINE v1")
    print("=" * 80)
    print(f"  ROUGE-1 F1         : baseline=28%  ->  v2={avg_f1_global:.1%}  ({'+' if avg_f1_global>0.28 else ''}{(avg_f1_global-0.28)*100:.1f}pp)")
    print(f"  ROUGE-1 Recall     : baseline=81%  ->  v2={avg_recall_global:.1%}  ({'+' if avg_recall_global>0.81 else ''}{(avg_recall_global-0.81)*100:.1f}pp)")
    print(f"  Cosine Sim         : baseline=74%  ->  v2={avg_cosine_global:.1%}  ({'+' if avg_cosine_global>0.74 else ''}{(avg_cosine_global-0.74)*100:.1f}pp)")
    print(f"  Semantic Adequacy  : baseline=75%  ->  v2={semantic_adequacy_global:.1%}  ({'+' if semantic_adequacy_global>0.75 else ''}{(semantic_adequacy_global-0.75)*100:.1f}pp)")
    print("=" * 80)

    # Export JSON
    output = {
        "evaluation_date": datetime.now().isoformat(),
        "version": "v2",
        "improvements": [
            "Chunking avec overlap (150 chars)",
            "Query Expansion (synonymes domaine assurance)",
            "Retrieval elargi top-10 -> top-5",
            "Contexte LLM enrichi 800 chars",
        ],
        "baseline_scores": {
            "rouge1_f1_mean": 0.28,
            "rouge1_recall_mean": 0.81,
            "cosine_similarity_mean": 0.74,
            "semantic_adequacy_mean": 0.75
        },
        "v2_scores": {
            "rouge1_f1_mean": round(avg_f1_global, 4),
            "rouge1_recall_mean": round(avg_recall_global, 4),
            "cosine_similarity_mean": round(avg_cosine_global, 4),
            "semantic_adequacy_mean": round(semantic_adequacy_global, 4)
        },
        "improvement_pp": {
            "rouge1_f1": round((avg_f1_global - 0.28) * 100, 1),
            "rouge1_recall": round((avg_recall_global - 0.81) * 100, 1),
            "cosine": round((avg_cosine_global - 0.74) * 100, 1),
            "semantic_adequacy": round((semantic_adequacy_global - 0.75) * 100, 1),
        },
        "total_questions": len(GOLD_STANDARD),
        "model_embeddings": "paraphrase-multilingual-MiniLM-L12-v2",
        "knowledge_docs_count": len(KNOWLEDGE_DOCUMENTS),
        "category_scores": {
            cat: {
                "count": scores["count"],
                "rouge1_f1_mean": round(sum(scores["rouge1_f1"]) / len(scores["rouge1_f1"]), 4),
                "rouge1_recall_mean": round(sum(scores["rouge1_recall"]) / len(scores["rouge1_recall"]), 4),
                "cosine_similarity_mean": round(sum(scores["cosine"]) / len(scores["cosine"]), 4),
            }
            for cat, scores in category_scores.items()
        },
        "detailed_results": results,
    }

    output_path = os.path.join(os.path.dirname(__file__), "rag_evaluation_results_v2.json")
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)

    print(f"\nResultats exportes : {output_path}")
    return output


if __name__ == "__main__":
    run_evaluation()
