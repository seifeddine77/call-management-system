"""
Knowledge Base — Documents pour le RAG Chatbot AutoAssist.

Contient :
- Guides d'utilisation de la plateforme
- Procédures métier de gestion de réclamations
- Description des règles et du scoring IA
- Référentiel de pièces/sinistres/catégories
- FAQ et procédures opérationnelles
"""

KNOWLEDGE_DOCUMENTS = [
    # ═══════════════════════════════════════════════════
    # 📖 GUIDES D'UTILISATION
    # ═══════════════════════════════════════════════════
    {
        "id": "guide_creer_ticket",
        "category": "guide",
        "title": "Comment créer un ticket",
        "content": (
            "Les tickets dans AutoAssist sont créés AUTOMATIQUEMENT via 3 canaux d'acquisition : "
            "1. CANAL CALL (Twilio) : Quand un client appelle le numéro de l'agence, Twilio enregistre l'appel, "
            "l'IA Whisper transcrit l'audio, le NLP extrait la pièce et le sinistre, "
            "et un ticket est créé automatiquement avec toutes ces informations. "
            "2. CANAL EMAIL (IMAP) : Quand un client envoie un email à l'agence, "
            "le système lit automatiquement l'email, analyse le contenu par NLP, "
            "et crée un ticket automatiquement. "
            "3. CANAL WEB (formulaire public) : Le client remplit un formulaire en ligne "
            "(nom, téléphone, description du problème) et le ticket est créé automatiquement. "
            "Le ticket reçoit un numéro unique au format T-AAAAMMJJ-XXXXX et le statut NOUVEAU. "
            "L'opérateur NE crée PAS manuellement les tickets, ils arrivent automatiquement dans la file d'attente."
        ),
    },
    {
        "id": "guide_assigner_ticket",
        "category": "guide",
        "title": "Comment assigner un ticket à un opérateur",
        "content": (
            "Pour assigner un ticket à un opérateur : "
            "1. Ouvrez le ticket concerné depuis la liste des tickets. "
            "2. Cliquez sur le bouton 'Assigner'. "
            "3. Sélectionnez l'opérateur dans la liste déroulante. "
            "Seuls les opérateurs de la même agence sont proposés. "
            "4. Le statut du ticket passera automatiquement à EN_COURS. "
            "L'opérateur assigné sera notifié."
        ),
    },
    {
        "id": "guide_upload_audio",
        "category": "guide",
        "title": "Comment uploader un enregistrement audio",
        "content": (
            "Pour ajouter un enregistrement audio à un ticket : "
            "1. Ouvrez le ticket concerné. "
            "2. Cliquez sur 'Ajouter Audio'. "
            "3. Sélectionnez le fichier audio (MP3, WAV, M4A supportés, max 50 MB). "
            "4. Le système transcrit automatiquement l'audio en texte grâce à l'IA Whisper. "
            "5. L'analyse NLP extrait automatiquement : la pièce mentionnée, le type de sinistre, "
            "le sentiment du client et les mots-clés importants. "
            "Important : le consentement client à l'enregistrement doit être obtenu (RGPD)."
        ),
    },
    {
        "id": "guide_photos",
        "category": "guide",
        "title": "Comment uploader des photos de sinistre",
        "content": (
            "Pour ajouter des photos de sinistre à un ticket : "
            "1. Ouvrez le ticket concerné. "
            "2. Cliquez sur 'Ajouter Photos'. "
            "3. Sélectionnez les fichiers images (JPEG, PNG, WebP, max 10 MB par photo). "
            "4. Vous pouvez ajouter plusieurs photos en une seule fois. "
            "Les photos seront stockées et associées au ticket pour l'analyse."
        ),
    },
    {
        "id": "guide_analyser_decision",
        "category": "guide",
        "title": "Comment lancer l'analyse IA et obtenir une décision",
        "content": (
            "Pour lancer l'analyse IA sur un ticket : "
            "1. Ouvrez le ticket concerné. "
            "2. Cliquez sur 'Analyser'. "
            "3. Remplissez les informations de la demande : type de pièce, âge du véhicule, "
            "kilométrage, catégorie client, type de sinistre. "
            "4. Le système analyse en 3 étapes : "
            "   - Étape 1 : Cache Redis (vérifie si un cas identique a déjà été traité). "
            "   - Étape 2 : Moteur de règles (évalue les règles métier configurées). "
            "   - Étape 3 : IA Gemma 4 (analyse les cas ambigus quand le score des règles est < 60). "
            "5. Résultat : FAVORABLE, DEFAVORABLE ou ETUDE (cas complexe). "
            "6. L'opérateur peut ensuite ACCEPTER ou REJETER la recommandation IA avec un motif."
        ),
    },
    {
        "id": "guide_anonymisation",
        "category": "guide",
        "title": "Comment anonymiser un ticket (RGPD)",
        "content": (
            "Pour anonymiser les données personnelles d'un ticket (droit à l'oubli RGPD) : "
            "1. Seul un administrateur peut effectuer cette action. "
            "2. Ouvrez le ticket concerné. "
            "3. Cliquez sur 'Anonymiser'. "
            "4. Les données personnelles (nom client, téléphone, email) seront remplacées par '***ANONYMISÉ***'. "
            "5. La transcription audio et les fichiers audio seront supprimés. "
            "Cette action est irréversible."
        ),
    },

    # ═══════════════════════════════════════════════════
    # 🔄 STATUTS ET FLUX
    # ═══════════════════════════════════════════════════
    {
        "id": "flux_statuts",
        "category": "processus",
        "title": "Les statuts d'un ticket et leurs transitions",
        "content": (
            "Un ticket passe par 4 statuts dans l'ordre : "
            "NOUVEAU : Le ticket vient d'être créé, il n'est pas encore pris en charge. "
            "EN_COURS : Un opérateur a été assigné et travaille sur le ticket. "
            "RESOLU : Le problème a été résolu, la solution a été communiquée au client. "
            "FERME : Le ticket est clos définitivement (état final). "
            "Transitions autorisées : NOUVEAU peut aller vers EN_COURS ou FERME. "
            "EN_COURS peut aller vers RESOLU ou FERME. "
            "RESOLU peut aller vers FERME. "
            "FERME est un état terminal, aucune transition possible."
        ),
    },
    {
        "id": "flux_canaux",
        "category": "processus",
        "title": "Les canaux d'acquisition des tickets",
        "content": (
            "AutoAssist crée les tickets AUTOMATIQUEMENT via 3 canaux (pas de création manuelle) : "
            "CALL (Twilio) : Le client appelle le numéro de l'agence. Twilio enregistre l'appel audio, "
            "l'IA Whisper transcrit l'audio en texte, le NLP (spaCy) extrait automatiquement la pièce défectueuse, "
            "le type de sinistre et le sentiment du client. Un ticket est créé automatiquement avec canal=CALL. "
            "EMAIL (IMAP Listener) : Le client envoie un email. Le service EmailListenerService lit l'email, "
            "extrait le sujet et le corps du message, et crée un ticket automatiquement avec canal=EMAIL. "
            "WEB (Formulaire public) : Le client remplit un formulaire en ligne (nom, téléphone, description). "
            "Le ChannelAcquisitionController crée automatiquement un ticket avec canal=WEB. "
            "Dans les 3 cas, le ticket arrive dans la file d'attente avec le statut NOUVEAU, "
            "prêt à être assigné à un opérateur par le supervisor."
        ),
    },

    # ═══════════════════════════════════════════════════
    # 🤖 SYSTÈME IA ET DÉCISION
    # ═══════════════════════════════════════════════════
    {
        "id": "ia_decision",
        "category": "ia",
        "title": "Le système d'aide à la décision IA",
        "content": (
            "Le module d'aide à la décision fonctionne en 3 étapes : "
            "Étape 1 — Cache Redis : Le système vérifie si une demande identique (même type de pièce, "
            "même âge véhicule, même kilométrage, même catégorie client, même sinistre) a déjà été traitée. "
            "Si oui, la décision précédente est réutilisée instantanément. "
            "Étape 2 — Moteur de règles : Les règles métier configurées par l'administrateur sont évaluées. "
            "Chaque règle a des conditions (ex: SI ageVehicule > 10 ET typePiece = MECANIQUE ALORS DEFAVORABLE). "
            "Si le score est ≥ 60, la décision est considérée comme claire. "
            "Étape 3 — IA Gemma 4 : Si le score des règles est < 60 (cas ambigu), le LLM Gemma 4 "
            "est consulté via Ollama. Il analyse le contexte complet et propose une recommandation."
        ),
    },
    {
        "id": "ia_scoring",
        "category": "ia",
        "title": "Le scoring et les décisions possibles",
        "content": (
            "Le système de scoring fonctionne ainsi : "
            "Score 0-59 : Résultat ambigu, l'IA Gemma 4 est consultée. "
            "Score 60-100 : Résultat clair, la décision est donnée directement par les règles. "
            "Les décisions possibles sont : "
            "FAVORABLE : La demande de prise en charge est acceptée. Le client a droit au remplacement ou à la réparation. "
            "DEFAVORABLE : La demande est refusée. Les conditions de garantie ne sont pas remplies. "
            "ETUDE : Le cas est complexe et nécessite une analyse manuelle par un expert. "
            "Source de la décision : RULES (moteur de règles), AI (Gemma 4), HYBRID (combinaison des deux), "
            "CACHE (résultat en cache). "
            "L'opérateur peut toujours accepter ou rejeter la recommandation avec un motif."
        ),
    },
    {
        "id": "ia_nlp",
        "category": "ia",
        "title": "L'analyse NLP automatique",
        "content": (
            "L'analyse NLP (Natural Language Processing) est effectuée automatiquement sur le texte "
            "des tickets. Elle extrait : "
            "1. La référence de pièce : détectée par regex (format ALT-4892) ou par mots-clés. "
            "2. Le type de sinistre : PANNE, ACCIDENT, USURE, DEFAUT_FABRICATION ou VOL. "
            "3. Le sentiment du client : POSITIF, NEGATIF ou NEUTRE. "
            "4. Les mots-clés : extraits par spaCy (entités nommées + noms communs). "
            "L'analyse est déclenchée automatiquement après la transcription audio."
        ),
    },

    # ═══════════════════════════════════════════════════
    # 📏 RÈGLES MÉTIER
    # ═══════════════════════════════════════════════════
    {
        "id": "regles_fonctionnement",
        "category": "regles",
        "title": "Comment fonctionnent les règles métier",
        "content": (
            "Les règles métier sont configurées par l'administrateur dans le module Règles. "
            "Chaque règle contient : un nom, une description, une priorité (1=haute), "
            "une décision (FAVORABLE ou DEFAVORABLE), un score, et des conditions au format JSONB. "
            "Les conditions supportent les opérateurs : AND (toutes doivent être vraies), "
            "OR (au moins une doit être vraie). "
            "Les opérateurs de comparaison disponibles : = (égal), != (différent), < (inférieur), "
            "> (supérieur), <= (inférieur ou égal), >= (supérieur ou égal), "
            "CONTAINS (contient du texte), IN (dans une liste). "
            "Les champs évalués sont : typePiece, ageVehiculeAns, kilometrage, "
            "categorieClient, typeSinistre, pieceReference."
        ),
    },
    {
        "id": "regles_exemples",
        "category": "regles",
        "title": "Exemples de règles métier courantes",
        "content": (
            "Exemples de règles métier typiques dans AutoAssist : "
            "Règle 1 — Véhicule ancien : SI ageVehiculeAns > 10 ET typePiece = MECANIQUE ALORS DEFAVORABLE (score 80). "
            "Motif : Les pièces mécaniques sur les véhicules de plus de 10 ans ne sont généralement pas couvertes. "
            "Règle 2 — Client VIP : SI categorieClient = VIP ET typeSinistre = ACCIDENT ALORS FAVORABLE (score 90). "
            "Motif : Les clients VIP bénéficient d'une prise en charge prioritaire sur les accidents. "
            "Règle 3 — Kilométrage élevé : SI kilometrage > 200000 ALORS DEFAVORABLE (score 70). "
            "Motif : Les véhicules à kilométrage très élevé sont exclus de la garantie standard. "
            "Règle 4 — Défaut fabrication : SI typeSinistre = DEFAUT_FABRICATION ET ageVehiculeAns < 3 ALORS FAVORABLE (score 95). "
            "Motif : Les défauts de fabrication sur véhicules récents sont toujours couverts."
        ),
    },

    # ═══════════════════════════════════════════════════
    # 🔧 RÉFÉRENTIEL PIÈCES, SINISTRES, CATÉGORIES
    # ═══════════════════════════════════════════════════
    {
        "id": "ref_types_pieces",
        "category": "referentiel",
        "title": "Les types de pièces auto",
        "content": (
            "Les types de pièces gérés par AutoAssist sont regroupés en catégories : "
            "MECANIQUE : moteur, boîte de vitesses, embrayage, alternateur, démarreur, carburateur, "
            "turbo, courroie, joint, soupape, bielle, vilebrequin, culasse, pompe. "
            "ELECTRICITE : alternateur, démarreur, calculateur, batterie, faisceau. "
            "CARROSSERIE : pare-chocs, aile, capot, portière, rétroviseur. "
            "FREINAGE : plaquettes, disques, étriers, liquide de frein. "
            "SUSPENSION : amortisseur, ressort, bras, rotule. "
            "REFROIDISSEMENT : radiateur, ventilateur, thermostat, pompe à eau, compresseur."
        ),
    },
    {
        "id": "ref_types_sinistres",
        "category": "referentiel",
        "title": "Les types de sinistres",
        "content": (
            "Les types de sinistres reconnus par AutoAssist : "
            "PANNE : Le véhicule ne fonctionne plus ou a un dysfonctionnement. "
            "Mots-clés : panne, ne démarre plus, bloqué, défectueux, ne marche plus. "
            "ACCIDENT : Le véhicule a subi un choc ou une collision. "
            "Mots-clés : accident, collision, impact, accrochage, endommagé. "
            "USURE : Dégradation naturelle liée au temps ou à l'utilisation. "
            "Mots-clés : usure, usé, détérioré, abîmé, dégradé. "
            "DEFAUT_FABRICATION : Problème lié à la fabrication du véhicule. "
            "Mots-clés : défaut de fabrication, depuis achat, sortie usine, vice caché. "
            "VOL : Pièce volée ou véhicule volé. "
            "Mots-clés : volé, vol, disparition, dérobé."
        ),
    },
    {
        "id": "ref_categories_clients",
        "category": "referentiel",
        "title": "Les catégories de clients",
        "content": (
            "Les catégories de clients dans AutoAssist : "
            "STANDARD : Client avec un contrat de base. Délai de traitement normal. "
            "PREMIUM : Client avec un contrat amélioré. Délai de traitement prioritaire, "
            "garantie étendue sur certaines pièces. "
            "VIP : Client avec le plus haut niveau de contrat. Traitement prioritaire, "
            "prise en charge étendue même pour les cas marginaux, "
            "relais avec véhicule de remplacement."
        ),
    },

    # ═══════════════════════════════════════════════════
    # 🏢 ORGANISATION
    # ═══════════════════════════════════════════════════
    {
        "id": "org_agences",
        "category": "organisation",
        "title": "La gestion des agences",
        "content": (
            "Les agences sont les points de contact physiques du réseau AutoAssist. "
            "Chaque agence a : un nom, une adresse, un téléphone, un email et un responsable (SUPERVISOR). "
            "Quand une agence est créée, un groupe WhatsApp est automatiquement créé pour l'équipe. "
            "Les tickets sont associés à une agence. "
            "Les opérateurs ne voient que les tickets de leur agence (sauf les ADMIN qui voient tout). "
            "Les rôles : ADMIN (accès global), SUPERVISOR (responsable d'agence), OPERATOR (traite les tickets)."
        ),
    },
    {
        "id": "org_roles",
        "category": "organisation",
        "title": "Les rôles et permissions",
        "content": (
            "Les rôles dans AutoAssist : "
            "ADMIN : Accès complet à toutes les fonctionnalités. Peut gérer les agences, les utilisateurs, "
            "les règles métier, voir tous les tickets, anonymiser les données (RGPD). "
            "SUPERVISOR : Responsable d'une agence. Peut voir les tickets de son agence, "
            "assigner les tickets aux opérateurs, valider les décisions. "
            "OPERATOR : Opérateur de call center. Peut créer et traiter les tickets de son agence, "
            "uploader des audios, lancer l'analyse IA."
        ),
    },

    # ═══════════════════════════════════════════════════
    # 🔒 RGPD ET SÉCURITÉ
    # ═══════════════════════════════════════════════════
    {
        "id": "rgpd_general",
        "category": "securite",
        "title": "Conformité RGPD de la plateforme",
        "content": (
            "AutoAssist est conforme au RGPD (Règlement Général sur la Protection des Données) : "
            "1. Consentement : L'enregistrement audio nécessite le consentement explicite du client. "
            "2. Chiffrement : Les fichiers audio sont chiffrés sur le disque (AES-256). "
            "3. Anonymisation : Un ticket peut être anonymisé (droit à l'oubli) par un administrateur. "
            "4. Minimisation : Seules les données nécessaires au traitement sont collectées. "
            "5. Traçabilité : L'historique des actions sur chaque ticket est conservé dans un audit log."
        ),
    },

    # ═══════════════════════════════════════════════════
    # 📞 PROCÉDURES OPÉRATIONNELLES
    # ═══════════════════════════════════════════════════
    {
        "id": "proc_reclamation",
        "category": "procedure",
        "title": "Procédure complète de traitement d'une réclamation",
        "content": (
            "Procédure de bout en bout pour traiter une réclamation client : "
            "1. Réception automatique : Le client contacte l'agence par téléphone (Twilio), email (IMAP) ou formulaire web. "
            "2. Création automatique du ticket : Le système crée automatiquement un ticket (PAS l'opérateur). "
            "Pour les appels, Whisper transcrit l'audio et le NLP extrait les informations. "
            "3. File d'attente : Le ticket arrive dans la file d'attente avec statut NOUVEAU, trié par priorité. "
            "4. Assignation : Le supervisor assigne le ticket à un opérateur (statut passe à EN_COURS). "
            "5. Ajout de photos : L'opérateur peut ajouter des photos du sinistre si nécessaire. "
            "6. Analyse décisionnelle IA : L'opérateur lance l'analyse (Cache Redis → Règles métier → IA Gemma 4). "
            "7. Décision : Le système recommande FAVORABLE, DEFAVORABLE ou ETUDE avec un score de 0 à 100. "
            "8. Validation : L'opérateur accepte ou rejette la recommandation IA avec un motif. "
            "9. Courrier PDF : Un courrier professionnel est généré et envoyé au client par email. "
            "10. Clôture : Le ticket est marqué RESOLU puis FERME."
        ),
    },
    {
        "id": "decision_favorable_defavorable",
        "category": "ia",
        "title": "Différence entre décision FAVORABLE et DEFAVORABLE",
        "content": (
            "Les décisions dans AutoAssist : "
            "FAVORABLE : La réclamation du client est ACCEPTÉE. La pièce sera remplacée ou réparée "
            "aux frais de la garantie. Cela arrive quand les conditions sont remplies : véhicule récent, "
            "kilométrage raisonnable, sinistre couvert, ou client VIP/PREMIUM. Le score est généralement élevé (60-100). "
            "DEFAVORABLE : La réclamation est REFUSÉE. Le client ne bénéficie pas de la prise en charge. "
            "Motifs courants : véhicule trop ancien (>10 ans), kilométrage trop élevé (>200000 km), "
            "pièce d'usure normale, sinistre non couvert, ou exclusion de garantie. "
            "ETUDE : Le cas est complexe ou ambigu (score <60). Il nécessite une analyse manuelle "
            "par un expert ou un manager. L'IA Gemma 4 donne une recommandation mais la décision finale "
            "revient à l'humain. "
            "Dans tous les cas, l'opérateur peut ACCEPTER ou REJETER la recommandation IA avec un motif."
        ),
    },
    {
        "id": "proc_panne",
        "category": "procedure",
        "title": "Comment traiter une réclamation de type PANNE",
        "content": (
            "Pour une réclamation de type PANNE : "
            "1. Identifiez la pièce défectueuse mentionnée par le client. "
            "2. Vérifiez l'âge du véhicule et le kilométrage. "
            "3. Les pannes sur véhicules récents (< 5 ans) avec faible kilométrage (< 100000 km) "
            "sont généralement FAVORABLE. "
            "4. Les pannes sur véhicules anciens (> 10 ans) ou kilométrage élevé (> 200000 km) "
            "sont généralement DEFAVORABLE. "
            "5. En cas de doute, lancez l'analyse IA qui prendra en compte tous les facteurs."
        ),
    },
    {
        "id": "proc_accident",
        "category": "procedure",
        "title": "Comment traiter une réclamation de type ACCIDENT",
        "content": (
            "Pour une réclamation de type ACCIDENT : "
            "1. Demandez les photos du sinistre (obligatoire). "
            "2. Les accidents sont généralement couverts quelle que soit l'ancienneté du véhicule. "
            "3. La catégorie client influence : les VIP et PREMIUM ont une prise en charge étendue. "
            "4. Vérifiez si le client dispose d'un contrat STANDARD, PREMIUM ou VIP. "
            "5. Les accidents avec un constat officiel sont prioritaires."
        ),
    },

    # ═══════════════════════════════════════════════════
    # 📄 COURRIER PDF ET NOTIFICATIONS
    # ═══════════════════════════════════════════════════
    {
        "id": "guide_courrier_pdf",
        "category": "guide",
        "title": "Comment générer et envoyer un courrier PDF",
        "content": (
            "Après qu'une décision a été prise (FAVORABLE ou DEFAVORABLE) : "
            "1. Ouvrez le ticket concerné et allez dans la section 'Décision'. "
            "2. Cliquez sur 'Générer le courrier'. "
            "3. Le système génère automatiquement un PDF professionnel avec : "
            "en-tête de l'entreprise, numéro du ticket, nom du client, "
            "décision (en vert pour FAVORABLE, en rouge pour DEFAVORABLE), "
            "motif détaillé, et signature. "
            "4. Le PDF est téléchargeable immédiatement. "
            "5. Si l'email du client est renseigné, le courrier est automatiquement "
            "envoyé en pièce jointe par email via le notification-service."
        ),
    },
    {
        "id": "guide_notifications",
        "category": "guide",
        "title": "Les notifications dans AutoAssist",
        "content": (
            "AutoAssist dispose de plusieurs types de notifications : "
            "1. Notifications temps réel (WebSocket) : un badge vert clignote quand un nouveau ticket arrive. "
            "2. Notifications email : envoyées automatiquement quand un ticket est résolu ou quand un courrier est généré. "
            "3. Notifications WhatsApp : le chatbot notifie le groupe de l'agence quand un événement important survient. "
            "4. Le notification-service gère les logs de toutes les notifications (succès, échecs, tentatives). "
            "5. Un administrateur peut voir les logs de notifications et relancer les notifications échouées."
        ),
    },

    # ═══════════════════════════════════════════════════
    # 📊 ANALYTICS ET DASHBOARD
    # ═══════════════════════════════════════════════════
    {
        "id": "guide_analytics",
        "category": "guide",
        "title": "Le tableau de bord Analytics",
        "content": (
            "Le tableau de bord Analytics est accessible aux SUPERVISOR et ADMIN. "
            "Il affiche les KPIs suivants : "
            "1. Total des tickets, tickets résolus, temps moyen de traitement. "
            "2. Répartition par canal (CALL, EMAIL, WEB). "
            "3. Répartition des décisions IA (FAVORABLE, DEFAVORABLE, ETUDE). "
            "4. Comparaison période-sur-période avec pourcentage d'évolution (Delta %). "
            "5. Export CSV des données analytiques. "
            "Les données sont mises à jour en temps réel via les événements RabbitMQ."
        ),
    },

    # ═══════════════════════════════════════════════════
    # 🌐 FORMULAIRE PUBLIC
    # ═══════════════════════════════════════════════════
    {
        "id": "guide_formulaire_web",
        "category": "guide",
        "title": "Le formulaire web pour les clients",
        "content": (
            "Les clients peuvent soumettre une réclamation via le formulaire web public. "
            "Le formulaire est accessible sans authentification. "
            "Les champs à remplir sont : nom complet, numéro de téléphone, email (optionnel), "
            "et description du problème. "
            "Le formulaire crée un ticket avec canal WEB et statut NOUVEAU. "
            "Un email de confirmation est envoyé au client si l'email est renseigné."
        ),
    },

    # ═══════════════════════════════════════════════════
    # 🤖 CHATBOT WHATSAPP
    # ═══════════════════════════════════════════════════
    {
        "id": "guide_chatbot_whatsapp",
        "category": "guide",
        "title": "Comment utiliser le chatbot WhatsApp AutoAssist",
        "content": (
            "Le chatbot WhatsApp est disponible dans les groupes d'agence AutoAssist. "
            "Commandes rapides : "
            "/stats — affiche les statistiques des tickets (nouveau, en cours, résolu, fermé). "
            "/ticket T-xxx — affiche le détail d'un ticket par son numéro. "
            "/queue — affiche la file d'attente des tickets non assignés. "
            "/decision T-xxx — affiche la décision IA d'un ticket. "
            "/agence — affiche les informations de l'agence du groupe. "
            "/aide — affiche la liste des commandes. "
            "Vous pouvez aussi poser des questions en langage naturel, "
            "le chatbot utilise un système RAG (Retrieval-Augmented Generation) "
            "avec l'IA Gemma 4 pour répondre intelligemment. "
            "Réagissez avec 👍 ou 👎 pour évaluer la qualité des réponses."
        ),
    },

    # ═══════════════════════════════════════════════════
    # 🏗️ ARCHITECTURE & MICROSERVICES
    # ═══════════════════════════════════════════════════
    {
        "id": "arch_microservices",
        "category": "technique",
        "title": "Architecture technique d'AutoAssist",
        "content": (
            "AutoAssist est une plateforme microservices composée de : "
            "1. auth-service : Authentification JWT, gestion des utilisateurs et rôles (Spring Boot). "
            "2. organisation-service : Gestion des agences, opérateurs et superviseurs. "
            "3. ticket-service : Création et gestion des tickets, intégration Twilio pour les appels. "
            "4. rules-service : Moteur de règles métier, données de référence (pièces, sinistres). "
            "5. ai-service : RAG chatbot, transcription audio Whisper, analyse NLP, scoring IA (Python FastAPI). "
            "6. decision-service : Aide à la décision (cache Redis → règles → IA Gemma 4). "
            "7. notification-service : Notifications email, génération de courriers PDF. "
            "8. analytics-service : Tableaux de bord, KPIs, export CSV. "
            "9. whatsapp-service : Chatbot WhatsApp, commandes et RAG. "
            "10. frontend : Interface web React avec Material-UI. "
            "11. mobile : Application mobile Flutter. "
            "Infrastructure : Eureka (discovery), Gateway (routage), RabbitMQ (événements), "
            "PostgreSQL (base de données), Redis (cache), ChromaDB (vecteurs RAG)."
        ),
    },
    {
        "id": "arch_communication",
        "category": "technique",
        "title": "Communication entre les microservices",
        "content": (
            "Les microservices communiquent de deux façons : "
            "1. Synchrone (REST via Feign) : Les services s'appellent via l'API Gateway "
            "(port 8080). Eureka assure la découverte automatique des services. "
            "Exemple : le decision-service appelle le rules-service pour évaluer les règles. "
            "2. Asynchrone (RabbitMQ) : Les événements sont publiés sur des queues. "
            "Exemples d'événements : ticket.created, ticket.updated, agence.created, decision.made. "
            "Le notification-service écoute les événements pour envoyer des emails. "
            "Le whatsapp-service écoute agence.created pour créer des groupes WhatsApp. "
            "L'analytics-service écoute tous les événements pour mettre à jour les KPIs."
        ),
    },

    # ═══════════════════════════════════════════════════
    # ❓ FAQ OPÉRATIONNELLES
    # ═══════════════════════════════════════════════════
    {
        "id": "faq_ticket_introuvable",
        "category": "faq",
        "title": "FAQ : Pourquoi mon ticket est introuvable",
        "content": (
            "Si vous ne trouvez pas un ticket, vérifiez : "
            "1. Le format du numéro : les tickets ont le format T-AAAAMMJJ-XXXXX (ex: T-20260416-12345). "
            "2. Filtres actifs : assurez-vous que les filtres de statut ou de date ne masquent pas le ticket. "
            "3. Votre agence : les opérateurs ne voient que les tickets de leur agence. "
            "Un ADMIN ou SUPERVISOR peut voir tous les tickets. "
            "4. La pagination : par défaut, seuls les 10 derniers tickets sont affichés. "
            "Utilisez la barre de recherche pour chercher par numéro. "
            "5. Sur WhatsApp : tapez /ticket T-xxx pour chercher un ticket spécifique."
        ),
    },
    {
        "id": "faq_decision_incorrecte",
        "category": "faq",
        "title": "FAQ : Que faire si la décision IA est incorrecte",
        "content": (
            "Si vous pensez que la décision IA n'est pas correcte : "
            "1. L'opérateur peut toujours REJETER la recommandation IA en cliquant sur 'Rejeter'. "
            "2. Il doit fournir un motif de rejet (obligatoire) pour traçabilité. "
            "3. La décision finale est TOUJOURS celle de l'humain, pas de l'IA. "
            "4. Les rejets sont enregistrés et utilisés pour améliorer les règles métier. "
            "5. Si les décisions sont souvent incorrectes, l'administrateur doit ajuster "
            "les règles métier dans le module Règles (conditions, seuils, priorités). "
            "6. L'IA Gemma 4 apprend du contexte des règles, pas des rejets passés directement."
        ),
    },
    {
        "id": "faq_temps_reponse",
        "category": "faq",
        "title": "FAQ : Pourquoi le chatbot est lent à répondre",
        "content": (
            "Le temps de réponse du chatbot dépend du type de requête : "
            "Commandes rapides (/stats, /ticket, /queue) : 1-3 secondes (appel API direct). "
            "Questions en langage naturel : 8-25 secondes (pipeline complet). "
            "Le pipeline RAG comprend : recherche vectorielle ChromaDB (~200ms), "
            "récupération des données temps réel (~500ms), "
            "et inférence Gemma 4 sur CPU (~5-20s selon la complexité). "
            "La première requête après un redémarrage prend plus de temps (warmup du modèle). "
            "Les requêtes suivantes sont plus rapides grâce au cache."
        ),
    },
    {
        "id": "faq_problemes_courants",
        "category": "faq",
        "title": "FAQ : Résolution des problèmes courants",
        "content": (
            "Problèmes courants et solutions : "
            "1. 'Erreur de connexion' : Vérifiez votre connexion internet et réessayez. "
            "Le serveur peut être en maintenance. "
            "2. 'Ticket non trouvé' : Vérifiez le format du numéro (T-AAAAMMJJ-XXXXX). "
            "3. 'Analyse IA échouée' : Le service IA est peut-être surchargé. Attendez 30 secondes et relancez. "
            "4. 'Le chatbot ne répond pas' : Vérifiez que vous êtes dans un groupe AutoAssist. "
            "Les messages privés au bot ne sont pas traités. "
            "5. 'PDF non généré' : Assurez-vous qu'une décision a été prise sur le ticket avant de générer le courrier. "
            "6. 'Audio non transcrit' : Vérifiez le format du fichier (MP3, WAV, M4A) et la taille (<50 MB)."
        ),
    },

    # ═══════════════════════════════════════════════════
    # 📱 APPLICATION MOBILE
    # ═══════════════════════════════════════════════════
    {
        "id": "guide_mobile",
        "category": "guide",
        "title": "L'application mobile AutoAssist",
        "content": (
            "AutoAssist dispose d'une application mobile Flutter pour les opérateurs en déplacement : "
            "Fonctionnalités : "
            "1. Voir la liste des tickets de son agence. "
            "2. Consulter le détail d'un ticket (statut, client, transcription). "
            "3. Recevoir des notifications push quand un nouveau ticket est créé. "
            "4. Voir le tableau de bord avec les statistiques. "
            "L'application se connecte au même backend que le frontend web via l'API Gateway. "
            "Authentification : même identifiants que la plateforme web (JWT). "
            "L'app est compatible Android et iOS grâce à Flutter."
        ),
    },

    # ═══════════════════════════════════════════════════
    # 📞 TWILIO & APPELS
    # ═══════════════════════════════════════════════════
    {
        "id": "guide_twilio_appels",
        "category": "guide",
        "title": "Comment fonctionnent les appels téléphoniques (Twilio)",
        "content": (
            "Le canal téléphonique fonctionne avec Twilio : "
            "1. Le client appelle le numéro Twilio de l'agence. "
            "2. Twilio enregistre l'appel audio et envoie un webhook au ticket-service. "
            "3. Le fichier audio est transmis au ai-service pour transcription. "
            "4. Whisper (IA de transcription) convertit l'audio en texte. "
            "5. L'analyse NLP extrait automatiquement : la pièce défectueuse, "
            "le type de sinistre et le sentiment du client. "
            "6. Un ticket est créé automatiquement avec canal=CALL. "
            "7. La transcription et l'analyse sont attachées au ticket. "
            "Configuration : le numéro Twilio et les clés API sont configurés "
            "dans le ticket-service (application.yml). "
            "Chaque agence peut avoir son propre numéro Twilio."
        ),
    },

    # ═══════════════════════════════════════════════════
    # 📧 EMAIL & IMAP
    # ═══════════════════════════════════════════════════
    {
        "id": "guide_email_imap",
        "category": "guide",
        "title": "Comment fonctionnent les emails (IMAP)",
        "content": (
            "Le canal email utilise le protocole IMAP pour lire automatiquement les emails : "
            "1. Le EmailListenerService se connecte à la boîte email de l'agence via IMAP. "
            "2. Il surveille les nouveaux emails non lus. "
            "3. Quand un email arrive, il extrait le sujet et le corps du message. "
            "4. Un ticket est créé automatiquement avec canal=EMAIL. "
            "5. Le nom et l'email du client sont extraits de l'expéditeur. "
            "Configuration : les paramètres IMAP (hôte, port, login, mot de passe) "
            "sont configurés dans le ticket-service. "
            "Fréquence de vérification : toutes les 60 secondes."
        ),
    },

    # ═══════════════════════════════════════════════════
    # 👥 GESTION DES UTILISATEURS
    # ═══════════════════════════════════════════════════
    {
        "id": "guide_utilisateurs",
        "category": "guide",
        "title": "Comment gérer les utilisateurs et les comptes",
        "content": (
            "La gestion des utilisateurs se fait via le module Organisation : "
            "Créer un utilisateur : L'ADMIN crée un compte avec nom, prénom, email, "
            "mot de passe et rôle (ADMIN, SUPERVISOR, OPERATOR). "
            "Assigner à une agence : Chaque utilisateur SUPERVISOR ou OPERATOR "
            "doit être assigné à une agence spécifique. "
            "Connexion : Les utilisateurs se connectent avec email + mot de passe. "
            "Un token JWT est généré et utilisé pour toutes les requêtes. "
            "Réinitialisation : En cas d'oubli de mot de passe, contactez l'administrateur. "
            "Désactivation : Un compte peut être désactivé sans être supprimé. "
            "L'authentification est gérée par le auth-service avec Spring Security."
        ),
    },

    # ═══════════════════════════════════════════════════
    # 📊 EXPORT & RAPPORTS
    # ═══════════════════════════════════════════════════
    {
        "id": "guide_export_donnees",
        "category": "guide",
        "title": "Comment exporter les données et générer des rapports",
        "content": (
            "AutoAssist propose plusieurs options d'export : "
            "1. Export CSV des tickets : depuis le tableau de bord Analytics, "
            "cliquez sur 'Exporter CSV' pour télécharger la liste complète des tickets. "
            "2. Courrier PDF : chaque décision peut être exportée en PDF professionnel. "
            "3. Rapport d'activité : le tableau de bord affiche les KPIs avec "
            "comparaison période-sur-période et évolution (Delta %). "
            "4. Via WhatsApp : /stats donne un résumé rapide des statistiques. "
            "5. Via l'API : toutes les données sont accessibles via l'API REST "
            "pour intégration avec d'autres outils (Power BI, Excel, etc.)."
        ),
    },
    {
        "id": "guide_priorites_tickets",
        "category": "processus",
        "title": "Comment sont calculées les priorités des tickets",
        "content": (
            "Les priorités des tickets dans AutoAssist : "
            "La priorité détermine l'ordre de traitement dans la file d'attente. "
            "Facteurs influençant la priorité : "
            "1. Catégorie client : VIP > PREMIUM > STANDARD. "
            "2. Type de sinistre : ACCIDENT et DEFAUT_FABRICATION sont prioritaires. "
            "3. Ancienneté : les tickets les plus anciens sont traités en premier (FIFO). "
            "4. Canal : les appels téléphoniques (CALL) sont souvent plus urgents. "
            "Dans la file d'attente (/queue), les tickets sont triés par priorité "
            "puis par date de création. Le superviseur peut réorganiser manuellement."
        ),
    },
    {"id": "proc_usure", "category": "procedure", "title": "Comment traiter une réclamation de type USURE",
     "content": "Pour une réclamation de type USURE (dégradation naturelle) : 1. L'usure est considérée comme normale si le véhicule a plus de 5 ans ou plus de 100 000 km. 2. Les pièces d'usure courantes : plaquettes de frein, embrayage, courroie, amortisseurs, pneus. 3. Généralement DEFAVORABLE car l'usure n'est pas couverte par la garantie standard. 4. Exception : client VIP ou PREMIUM avec contrat étendu peut bénéficier d'une prise en charge partielle. 5. Exception : usure prématurée sur véhicule récent (<2 ans) → traiter comme DEFAUT_FABRICATION. 6. Toujours demander le kilométrage exact et l'âge du véhicule avant de lancer l'analyse IA."},
    {"id": "proc_vol", "category": "procedure", "title": "Comment traiter une réclamation de type VOL",
     "content": "Pour une réclamation de type VOL : 1. Exiger impérativement un dépôt de plainte (PV de police). 2. Le vol est couvert si le client a un contrat avec option vol/vandalisme. 3. Les clients VIP ont systématiquement la couverture vol incluse. 4. Vérifier la liste des pièces volées et leur valeur estimée. 5. Photos obligatoires : état du véhicule après vol, serrures forcées. 6. Décision généralement FAVORABLE si le PV est fourni et le contrat couvre le vol. 7. DEFAVORABLE si pas de PV, contrat sans option vol, ou suspicion de fraude. 8. Délai de déclaration : le vol doit être signalé dans les 48h."},
    {"id": "proc_defaut_fabrication", "category": "procedure", "title": "Comment traiter une réclamation DEFAUT DE FABRICATION",
     "content": "Pour une réclamation DEFAUT_FABRICATION : 1. Toujours FAVORABLE si le véhicule a moins de 3 ans (garantie constructeur). 2. Vérifier si le défaut est connu (rappel constructeur, bulletin technique). 3. Le kilométrage n'est PAS un critère pour les défauts de fabrication. 4. Demander la facture d'achat et le carnet d'entretien. 5. Si le véhicule a entre 3 et 5 ans, le score sera entre 50-70 → l'IA tranche. 6. Au-delà de 5 ans, difficile de prouver un défaut d'origine → souvent ETUDE. 7. Les défauts de fabrication sont prioritaires dans la file d'attente."},
    {"id": "sla_delais", "category": "processus", "title": "Les délais de traitement et SLA",
     "content": "Les délais de traitement dans AutoAssist (SLA — Service Level Agreement) : Client VIP : prise en charge en moins de 2 heures, résolution en 24h. Client PREMIUM : prise en charge en moins de 4 heures, résolution en 48h. Client STANDARD : prise en charge en moins de 24 heures, résolution en 72h. Les tickets ACCIDENT et DEFAUT_FABRICATION sont toujours prioritaires. Un ticket non pris en charge après le SLA déclenche une alerte au supervisor. Le temps moyen de traitement est suivi dans le tableau de bord Analytics."},
    {"id": "garanties_contrats", "category": "processus", "title": "Les types de garanties et contrats",
     "content": "Les garanties dans AutoAssist : Garantie constructeur : couvre les défauts de fabrication pendant 2 à 5 ans. Garantie contractuelle STANDARD : couvre les pannes mécaniques et électriques pour les véhicules de moins de 7 ans et moins de 150 000 km. Garantie PREMIUM : couvre en plus les pièces d'usure prématurée, accidents mineurs et véhicule de remplacement. Garantie VIP : couverture totale incluant vol, vandalisme, remorquage illimité, véhicule de remplacement, prise en charge prioritaire. Exclusions communes : modifications non homologuées, compétition, défaut d'entretien prouvé, utilisation professionnelle non déclarée."},
    {"id": "exclusions_garantie", "category": "processus", "title": "Les exclusions de garantie",
     "content": "Cas où la prise en charge est systématiquement DEFAVORABLE : 1. Véhicule modifié sans homologation (tuning moteur, suspension abaissée). 2. Défaut d'entretien prouvé (vidange non faite, courroie non changée). 3. Utilisation en compétition ou hors route non déclarée. 4. Kilométrage falsifié ou compteur modifié. 5. Sinistre causé par négligence avérée (conduite en surchauffe). 6. Pièces d'usure normale après la période de garantie. 7. Véhicule de plus de 15 ans (sauf contrat VIP spécial). 8. Fraude avérée ou fausse déclaration."},
    {"id": "remorquage", "category": "procedure", "title": "Prise en charge remorquage standard",
     "content": "Le remorquage dans AutoAssist : Client VIP : remorquage illimité, 24h/24, 7j/7, partout en Tunisie. Client PREMIUM : remorquage limité à 100 km, pendant les heures ouvrables. Client STANDARD : remorquage non inclus, à la charge du client. Pour déclencher un remorquage : le client appelle le numéro de l'agence, un ticket CALL est créé automatiquement, et l'opérateur coordonne avec le dépanneur. Le remorquage est toujours inclus dans la décision FAVORABLE pour les sinistres ACCIDENT."},
    {"id": "vehicule_remplacement", "category": "procedure", "title": "Véhicule de remplacement",
     "content": "Le véhicule de remplacement : Client VIP : véhicule de remplacement systématique dès la prise en charge, durée illimitée. Client PREMIUM : véhicule de remplacement pour les réparations de plus de 48h. Client STANDARD : pas de véhicule de remplacement inclus. Le véhicule de remplacement est noté dans le ticket et facturé au fournisseur. L'opérateur doit vérifier la disponibilité auprès du partenaire de location."},
    {"id": "escalade", "category": "processus", "title": "Procédure d'escalade d'un ticket",
     "content": "L'escalade d'un ticket : 1. Un opérateur peut escalader un ticket s'il ne peut pas le résoudre seul. 2. Le ticket est remonté au SUPERVISOR de l'agence. 3. Le SUPERVISOR peut réassigner à un autre opérateur plus expérimenté. 4. Si le SUPERVISOR ne peut pas résoudre, il escalade à l'ADMIN. 5. Les cas ETUDE (score < 60) sont automatiquement signalés au SUPERVISOR. 6. Un ticket non résolu après le SLA est automatiquement escaladé. 7. L'historique d'escalade est tracé dans l'audit log."},
    {"id": "reassignation", "category": "processus", "title": "Comment réassigner un ticket",
     "content": "Pour réassigner un ticket : 1. Seul le SUPERVISOR ou l'ADMIN peut réassigner. 2. Ouvrez le ticket et cliquez sur Réassigner. 3. Sélectionnez le nouvel opérateur dans la liste. 4. Le motif de réassignation est optionnel mais recommandé. 5. L'ancien opérateur est notifié. 6. Le nouvel opérateur voit tout l'historique. 7. Le statut reste EN_COURS après réassignation."},
    {"id": "guide_softphone", "category": "guide", "title": "Le softphone web intégré",
     "content": "AutoAssist dispose d'un softphone web intégré : 1. Accessible depuis l'interface web à /softphone. 2. L'opérateur sélectionne l'agence et saisit les infos client. 3. L'appel est enregistré automatiquement via le navigateur (MediaRecorder). 4. La transcription en temps réel s'affiche pendant l'appel (WebSocket + Whisper). 5. À la fin de l'appel, un ticket est créé automatiquement avec canal=CALL. 6. L'audio est envoyé au pipeline IA : Whisper → NLP → analyse automatique. 7. Le softphone fonctionne sans installation, directement dans le navigateur."},
    {"id": "guide_recherche_filtrage", "category": "guide", "title": "Comment rechercher et filtrer les tickets",
     "content": "La recherche et le filtrage : 1. Barre de recherche : cherchez par numéro (T-xxx), nom client, ou mot-clé. 2. Filtre par statut : NOUVEAU, EN_COURS, RESOLU, FERME. 3. Filtre par canal : CALL, EMAIL, WEB. 4. Filtre par date : aujourd'hui, semaine, mois, personnalisé. 5. Filtre par priorité : haute, moyenne, basse. 6. Filtre par agence (ADMIN uniquement). 7. Tri : par date, priorité, statut ou numéro. 8. Pagination : 10, 25, 50, 100 par page. 9. Sur WhatsApp : /ticket T-xxx pour chercher un ticket."},
    {"id": "kpis_metriques", "category": "guide", "title": "Les KPIs et métriques du tableau de bord",
     "content": "Les indicateurs clés (KPIs) : 1. Taux de résolution : tickets résolus vs total. 2. Temps moyen de traitement (TMT). 3. Temps moyen de première réponse. 4. Taux de satisfaction : basé sur les feedbacks 👍/👎 du chatbot. 5. Répartition par canal (CALL vs EMAIL vs WEB). 6. Répartition des décisions (FAVORABLE vs DEFAVORABLE vs ETUDE). 7. Taux d'accord IA : recommandations acceptées par les opérateurs. 8. Delta % : évolution par rapport à la période précédente. 9. Top sinistres les plus fréquents. 10. Performance par agence."},
    {"id": "securite_jwt", "category": "securite", "title": "Authentification JWT et sécurité",
     "content": "La sécurité : 1. Authentification JWT : chaque requête contient un token signé. 2. Le token expire après 24h. 3. Les mots de passe sont hashés avec BCrypt. 4. RBAC : chaque endpoint est protégé par rôle. 5. CORS configuré pour les origines autorisées en production. 6. Rate limiting sur les endpoints sensibles. 7. Les communications inter-services passent par le réseau Docker interne. 8. L'API Gateway (port 8080) est le seul point d'entrée externe."},
    {"id": "audit_tracabilite", "category": "securite", "title": "Audit trail et traçabilité",
     "content": "La traçabilité : 1. Chaque action sur un ticket est enregistrée avec horodatage et utilisateur. 2. Les décisions IA sont archivées avec le score, la source et le motif. 3. Les modifications de règles sont tracées. 4. Les connexions/déconnexions sont loguées. 5. Les notifications (succès/échec) sont conservées. 6. L'anonymisation RGPD est irréversible et tracée. 7. Les feedbacks chatbot sont persistés en DB. 8. Les logs sont consultables par l'ADMIN."},
    {"id": "resoudre_fermer_ticket", "category": "processus", "title": "Comment résoudre et fermer un ticket",
     "content": "Pour résoudre un ticket : 1. Ouvrez le ticket EN_COURS. 2. Assurez-vous qu'une décision a été prise. 3. Si FAVORABLE : la pièce a été commandée/remplacée, le client informé. 4. Si DEFAVORABLE : le courrier de refus envoyé. 5. Cliquez Résoudre → statut passe à RESOLU. 6. Pour fermer définitivement : cliquez Fermer → statut FERME. 7. Un ticket FERME ne peut plus être modifié (état terminal). 8. Le courrier PDF est automatiquement généré et envoyé par email."},
    {"id": "modifier_ticket", "category": "guide", "title": "Comment modifier les informations d'un ticket",
     "content": "Pour modifier un ticket : 1. Seuls les tickets NOUVEAU ou EN_COURS peuvent être modifiés. 2. L'opérateur ou le SUPERVISOR peut modifier : la description, les informations client, la priorité. 3. Le canal (CALL, EMAIL, WEB) ne peut pas être modifié. 4. Le numéro est immuable. 5. Ajouter des photos ou un audio est possible avant FERME. 6. Toute modification est tracée dans l'historique."},
    {"id": "multilingue", "category": "technique", "title": "Support multilingue d'AutoAssist",
     "content": "AutoAssist supporte le multilinguisme : 1. Interface web en français. 2. Chatbot WhatsApp : comprend français, arabe et anglais. 3. Le RAG détecte la langue et répond dans la même langue. 4. Whisper supporte français, arabe et anglais. 5. Embeddings multilingues (paraphrase-multilingual-MiniLM-L12-v2). 6. Règles métier et données de référence en français. 7. Courriers PDF en français."},
    {"id": "redis_cache_details", "category": "technique", "title": "Le cache Redis pour les décisions",
     "content": "Le cache Redis dans le système de décision : 1. Chaque demande est hashée : typePiece + ageVehicule + km + catClient + sinistre. 2. Si un hash identique existe → décision instantanée (< 50ms). 3. TTL configurable (défaut 24h). 4. Cache invalidé quand les règles changent. 5. Source affichée : CACHE. 6. Taux de hit cache visible dans Analytics. 7. Redis aussi utilisé pour rate limiting et sessions."},
    {"id": "modeles_courrier", "category": "guide", "title": "Les modèles de courrier PDF",
     "content": "Les courriers PDF : Courrier FAVORABLE : en-tête entreprise, confirmation de prise en charge, détail de la pièce, instructions pour la réparation/remplacement, signature. Courrier DEFAVORABLE : en-tête entreprise, notification de refus, motif détaillé, voies de recours possibles, signature. Les courriers sont envoyés automatiquement par email si l'adresse client est renseignée. Le PDF est téléchargeable depuis le détail du ticket."},
]


