import '../config/api_config.dart';
import '../models/decision.dart';
import 'api_client.dart';

/// Decision service — mirrors services/decisionService.ts
class DecisionService {
  final _api = ApiClient();

  Future<DecisionResult> analyze(DecisionRequest data) async {
    final res = await _api.dio.post('${ApiConfig.decisionsPath}/analyze', data: data.toJson());
    return DecisionResult.fromJson(res.data);
  }

  Future<DecisionResult> accept(String id, OverrideRequest data) async {
    final res = await _api.dio.post('${ApiConfig.decisionsPath}/$id/accept', data: data.toJson());
    return DecisionResult.fromJson(res.data);
  }

  Future<DecisionResult> reject(String id, OverrideRequest data) async {
    final res = await _api.dio.post('${ApiConfig.decisionsPath}/$id/reject', data: data.toJson());
    return DecisionResult.fromJson(res.data);
  }

  Future<DecisionResult> getByTicket(String ticketId) async {
    final res = await _api.dio.get('${ApiConfig.decisionsPath}/ticket/$ticketId');
    return DecisionResult.fromJson(res.data);
  }

  Future<void> deleteByTicket(String ticketId) async {
    await _api.dio.delete('${ApiConfig.decisionsPath}/ticket/$ticketId');
  }
}
