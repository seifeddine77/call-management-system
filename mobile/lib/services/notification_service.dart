import '../config/api_config.dart';
import '../models/notification.dart';
import 'api_client.dart';

/// Notification service — mirrors services/notificationService.ts
class NotificationService {
  final _api = ApiClient();

  /// Get notification logs — optionally filtered by status
  Future<List<NotificationLog>> getLogs({String? status, int? page, int? size}) async {
    final params = <String, dynamic>{};
    if (status != null) params['status'] = status;
    if (page != null) params['page'] = page;
    if (size != null) params['size'] = size;

    final res = await _api.dio.get(
      '${ApiConfig.notificationsPath}/logs',
      queryParameters: params,
    );
    final data = res.data;
    // Handle both paginated and array responses
    final list = data is List ? data : (data['content'] as List? ?? []);
    return list.map<NotificationLog>((n) => NotificationLog.fromJson(n)).toList();
  }

  /// Retry a failed notification
  Future<void> retry(String id) async {
    await _api.dio.post('${ApiConfig.notificationsPath}/retry/$id');
  }
}
