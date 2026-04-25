/// Decision data models — mirrors types/decision.ts

class DecisionRequest {
  final String ticketId;
  final String? typePiece;
  final int? ageVehiculeAns;
  final int? kilometrage;
  final String? categorieClient;
  final String? typeSinistre;
  final String? pieceReference;
  final String? transcriptionText;

  DecisionRequest({
    required this.ticketId,
    this.typePiece,
    this.ageVehiculeAns,
    this.kilometrage,
    this.categorieClient,
    this.typeSinistre,
    this.pieceReference,
    this.transcriptionText,
  });

  Map<String, dynamic> toJson() {
    final map = <String, dynamic>{'ticketId': ticketId};
    if (typePiece != null) map['typePiece'] = typePiece;
    if (ageVehiculeAns != null) map['ageVehiculeAns'] = ageVehiculeAns;
    if (kilometrage != null) map['kilometrage'] = kilometrage;
    if (categorieClient != null) map['categorieClient'] = categorieClient;
    if (typeSinistre != null) map['typeSinistre'] = typeSinistre;
    if (pieceReference != null) map['pieceReference'] = pieceReference;
    if (transcriptionText != null) map['transcriptionText'] = transcriptionText;
    return map;
  }
}

class DecisionResult {
  final String decisionId;
  final String ticketId;
  final String decision;
  final double score;
  final String motif;
  final String source;
  final List<String> rulesApplied;
  final bool ambiguous;
  final bool cached;
  final String? finalDecision;
  final String? overrideMotif;

  DecisionResult({
    required this.decisionId,
    required this.ticketId,
    required this.decision,
    required this.score,
    required this.motif,
    required this.source,
    required this.rulesApplied,
    required this.ambiguous,
    required this.cached,
    this.finalDecision,
    this.overrideMotif,
  });

  factory DecisionResult.fromJson(Map<String, dynamic> json) {
    return DecisionResult(
      decisionId: json['decisionId'] ?? '',
      ticketId: json['ticketId'] ?? '',
      decision: json['decision'] ?? '',
      score: (json['score'] ?? 0).toDouble(),
      motif: json['motif'] ?? '',
      source: json['source'] ?? '',
      rulesApplied: List<String>.from(json['rulesApplied'] ?? []),
      ambiguous: json['ambiguous'] ?? false,
      cached: json['cached'] ?? false,
      finalDecision: json['finalDecision'],
      overrideMotif: json['overrideMotif'],
    );
  }
}

class OverrideRequest {
  final String finalDecision;
  final String motif;
  final String operateurId;
  final String operateurName;
  final String? clientNom;
  final String? clientEmail;

  OverrideRequest({
    required this.finalDecision,
    required this.motif,
    required this.operateurId,
    required this.operateurName,
    this.clientNom,
    this.clientEmail,
  });

  Map<String, dynamic> toJson() {
    final map = <String, dynamic>{
      'finalDecision': finalDecision,
      'motif': motif,
      'operateurId': operateurId,
      'operateurName': operateurName,
    };
    if (clientNom != null) map['clientNom'] = clientNom;
    if (clientEmail != null) map['clientEmail'] = clientEmail;
    return map;
  }
}
