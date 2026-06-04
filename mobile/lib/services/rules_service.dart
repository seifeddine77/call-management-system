import '../config/api_config.dart';
import '../models/rule.dart';
import 'api_client.dart';

/// RefItem model for paramétrage
class RefItem {
  final String id;
  final String category;
  final String code;
  final String label;
  final String? description;
  final int displayOrder;
  final bool actif;

  RefItem({
    required this.id,
    required this.category,
    required this.code,
    required this.label,
    this.description,
    required this.displayOrder,
    required this.actif,
  });

  factory RefItem.fromJson(Map<String, dynamic> json) {
    return RefItem(
      id: json['id'] ?? '',
      category: json['category'] ?? '',
      code: json['code'] ?? '',
      label: json['label'] ?? '',
      description: json['description'],
      displayOrder: json['displayOrder'] ?? 0,
      actif: json['actif'] ?? true,
    );
  }
}

/// RuleVersion model for history
class RuleVersion {
  final String id;
  final int version;
  final String action;
  final String? changedByName;
  final String? changedAt;
  final int? scoreImpact;

  RuleVersion({
    required this.id,
    required this.version,
    required this.action,
    this.changedByName,
    this.changedAt,
    this.scoreImpact,
  });

  factory RuleVersion.fromJson(Map<String, dynamic> json) {
    return RuleVersion(
      id: json['id'] ?? '',
      version: json['version'] ?? 1,
      action: json['action'] ?? '',
      changedByName: json['changedByName'],
      changedAt: json['changedAt'],
      scoreImpact: json['scoreImpact'],
    );
  }
}

/// Rules service — mirrors React ruleService.ts
class RulesService {
  final _api = ApiClient();

  // ── Rules CRUD ──

  Future<List<Rule>> getRules() async {
    final res = await _api.dio.get(ApiConfig.rulesPath);
    final data = res.data;
    final list = data is List ? data : (data['content'] as List? ?? []);
    return list.map<Rule>((r) => Rule.fromJson(r)).toList();
  }

  Future<Rule> getRuleById(String id) async {
    final res = await _api.dio.get('${ApiConfig.rulesPath}/$id');
    return Rule.fromJson(res.data);
  }

  Future<Rule> createRule(Map<String, dynamic> data) async {
    final res = await _api.dio.post(ApiConfig.rulesPath, data: data);
    return Rule.fromJson(res.data);
  }

  Future<Rule> updateRule(String id, Map<String, dynamic> data) async {
    final res = await _api.dio.put('${ApiConfig.rulesPath}/$id', data: data);
    return Rule.fromJson(res.data);
  }

  Future<void> deleteRule(String id) async {
    await _api.dio.delete('${ApiConfig.rulesPath}/$id');
  }

  Future<Rule> toggleRule(String id) async {
    final res = await _api.dio.patch('${ApiConfig.rulesPath}/$id/toggle');
    return Rule.fromJson(res.data);
  }

  Future<List<RuleVersion>> getHistory(String id) async {
    final res = await _api.dio.get('${ApiConfig.rulesPath}/$id/history');
    return (res.data as List).map((h) => RuleVersion.fromJson(h)).toList();
  }

  // ── References ──

  Future<ReferenceData> getReferences() async {
    final res = await _api.dio.get(ApiConfig.referencesPath);
    return ReferenceData.fromJson(res.data);
  }

  // ── Evaluate ──

  Future<EvaluateResponse> evaluate(Map<String, dynamic> data) async {
    final res = await _api.dio.post(ApiConfig.evaluatePath, data: data);
    return EvaluateResponse.fromJson(res.data);
  }

  // ── Reference Items CRUD (Paramétrage) ──

  Future<List<RefItem>> getRefItems({String? category}) async {
    final params = category != null
        ? '?category=$category&includeInactive=true'
        : '?includeInactive=true';
    final res = await _api.dio.get('${ApiConfig.refItemsPath}$params');
    return (res.data as List).map((r) => RefItem.fromJson(r)).toList();
  }

  Future<RefItem> createRefItem(Map<String, dynamic> data) async {
    final res = await _api.dio.post(ApiConfig.refItemsPath, data: data);
    return RefItem.fromJson(res.data);
  }

  Future<RefItem> updateRefItem(String id, Map<String, dynamic> data) async {
    final res = await _api.dio.put('${ApiConfig.refItemsPath}/$id', data: data);
    return RefItem.fromJson(res.data);
  }

  Future<void> toggleRefItem(String id) async {
    await _api.dio.patch('${ApiConfig.refItemsPath}/$id/toggle');
  }

  Future<void> deleteRefItem(String id) async {
    await _api.dio.delete('${ApiConfig.refItemsPath}/$id');
  }
}
