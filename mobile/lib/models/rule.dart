/// Rule data models — mirrors types/rule.ts

class Rule {
  final String id;
  final String nom;
  final String description;
  final String action;
  final String actionMotif;
  final int priorite;
  final RuleConditions conditions;
  final bool actif;
  final int version;

  Rule({
    required this.id,
    required this.nom,
    required this.description,
    required this.action,
    required this.actionMotif,
    required this.priorite,
    required this.conditions,
    required this.actif,
    required this.version,
  });

  factory Rule.fromJson(Map<String, dynamic> json) {
    return Rule(
      id: json['id'] ?? '',
      nom: json['nom'] ?? '',
      description: json['description'] ?? '',
      action: json['action'] ?? '',
      actionMotif: json['actionMotif'] ?? '',
      priorite: json['priorite'] ?? 10,
      conditions: RuleConditions.fromJson(json['conditions'] ?? {}),
      actif: json['actif'] ?? true,
      version: json['version'] ?? 1,
    );
  }
}

class RuleConditions {
  final String operator;
  final List<RuleCondition> conditions;

  RuleConditions({required this.operator, required this.conditions});

  factory RuleConditions.fromJson(Map<String, dynamic> json) {
    return RuleConditions(
      operator: json['operator'] ?? 'AND',
      conditions: (json['conditions'] as List?)?.map((c) => RuleCondition.fromJson(c)).toList() ?? [],
    );
  }
}

class RuleCondition {
  final String field;
  final String operator;
  final String value;

  RuleCondition({required this.field, required this.operator, required this.value});

  factory RuleCondition.fromJson(Map<String, dynamic> json) {
    return RuleCondition(
      field: json['field'] ?? '',
      operator: json['op'] ?? json['operator'] ?? '=',
      value: json['value']?.toString() ?? '',
    );
  }
}

class FieldDef {
  final String field;
  final String label;
  final String type;
  final List<String>? values;

  FieldDef({required this.field, required this.label, required this.type, this.values});

  factory FieldDef.fromJson(Map<String, dynamic> json) {
    return FieldDef(
      field: json['field'] ?? '',
      label: json['label'] ?? '',
      type: json['type'] ?? 'text',
      values: (json['values'] as List?)?.map((v) => v.toString()).toList(),
    );
  }
}

class ReferenceData {
  final List<FieldDef> fields;
  final List<String> operators;

  ReferenceData({required this.fields, required this.operators});

  factory ReferenceData.fromJson(Map<String, dynamic> json) {
    // Backend uses 'champsDisponibles' instead of 'fields'
    final fieldsList = json['champsDisponibles'] ?? json['fields'] ?? [];
    final opsList = json['operateursComparaison'] ?? json['operators'] ?? [];
    return ReferenceData(
      fields: (fieldsList as List).map((f) => FieldDef.fromJson(f is Map<String, dynamic> ? f : {'field': f.toString()})).toList(),
      operators: opsList is List
        ? opsList.map((o) => (o is Map ? o['code']?.toString() : o.toString()) ?? '').toList()
        : ['=', '!=', '<', '>', '<=', '>='],
    );
  }
}

class EvaluateResponse {
  final String decision;
  final double score;
  final String? motif;
  final int matchedRules;
  final List<String> rulesApplied;
  final bool ambiguous;

  EvaluateResponse({
    required this.decision,
    required this.score,
    this.motif,
    required this.matchedRules,
    required this.rulesApplied,
    required this.ambiguous,
  });

  factory EvaluateResponse.fromJson(Map<String, dynamic> json) {
    return EvaluateResponse(
      decision: json['decision'] ?? '',
      score: (json['score'] ?? 0).toDouble(),
      motif: json['motif'],
      matchedRules: json['matchedRules'] ?? 0,
      rulesApplied: List<String>.from(json['rulesApplied'] ?? []),
      ambiguous: json['ambiguous'] ?? false,
    );
  }
}
