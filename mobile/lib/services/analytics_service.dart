import '../config/api_config.dart';
import '../models/analytics.dart';
import 'api_client.dart';

/// Analytics service — mirrors services/analyticsService.ts
class AnalyticsService {
  final _api = ApiClient();

  static const _periodMap = {'week': '7d', 'month': '30d', 'year': '90d'};

  /// Convert UI period label to API parameter
  static String apiPeriod(String period) => _periodMap[period] ?? period;

  Future<KpiResponse> getKpis({String? period}) async {
    final p = period != null ? apiPeriod(period) : null;
    final res = await _api.dio.get(ApiConfig.kpisPath, queryParameters: p != null ? {'period': p} : null);
    return KpiResponse.fromJson(res.data);
  }

  Future<KpiComparison> getComparison({String? period}) async {
    final p = period != null ? apiPeriod(period) : null;
    final res = await _api.dio.get(ApiConfig.kpisComparisonPath, queryParameters: p != null ? {'period': p} : null);
    return KpiComparison.fromJson(res.data);
  }

  Future<List<HourlyData>> getTicketsPerDay({String? period}) async {
    final p = period != null ? apiPeriod(period) : null;
    final res = await _api.dio.get(ApiConfig.ticketsPerDayPath, queryParameters: p != null ? {'period': p} : null);
    return (res.data as List).map((d) => HourlyData.fromJson(d)).toList();
  }

  Future<List<DailyStat>> getDailyStats({String? period}) async {
    final p = period != null ? apiPeriod(period) : null;
    final res = await _api.dio.get(ApiConfig.dailyStatsPath, queryParameters: p != null ? {'period': p} : null);
    return (res.data as List).map((d) => DailyStat.fromJson(d)).toList();
  }
}
