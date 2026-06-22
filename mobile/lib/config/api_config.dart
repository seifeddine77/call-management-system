import 'package:flutter/foundation.dart' show kIsWeb;

/// API configuration constants
class ApiConfig {
  ApiConfig._();

  /// Base URL — auto-detects platform :
  /// - Web : '' (same-origin) → requêtes relatives `/api/...` servies par le proxy
  ///   nginx du conteneur mobile (location /api → api-gateway). Évite le cross-origin
  ///   et donc les conflits CORS (ex. double en-tête Access-Control-Allow-Origin sur
  ///   les routes ai-service FastAPI qui ajoutent leur propre CORS). Identique à React.
  /// - Android emulator : 10.0.2.2:8080
  /// - Physical device : change to your machine's local IP
  static String get baseUrl => kIsWeb
      ? ''
      : 'http://10.0.2.2:8080';

  /// Timeouts
  static const Duration connectTimeout = Duration(seconds: 15);
  static const Duration receiveTimeout = Duration(seconds: 30);

  /// Auth endpoints
  static const String loginPath = '/api/auth/login';
  static const String refreshPath = '/api/auth/refresh';
  static const String logoutPath = '/api/auth/logout';

  /// Ticket endpoints
  static const String ticketsPath = '/api/tickets';
  static const String queuePath = '/api/tickets/queue';
  static const String ticketStatsPath = '/api/tickets/stats';

  /// Analytics endpoints
  static const String kpisPath = '/api/analytics/kpis';
  static const String kpisComparisonPath = '/api/analytics/kpis/comparison';
  static const String ticketsPerDayPath = '/api/analytics/tickets-per-day';
  static const String dailyStatsPath = '/api/analytics/daily-stats';
  static const String exportCsvPath = '/api/analytics/export/csv';
  static const String operatorPerformancePath = '/api/tickets/operators/performance';
  static const String supervisionAlertsPath = '/api/tickets/supervision/alerts';
  static const String chatbotStatsPath = '/api/chat/stats';

  /// Decision endpoints
  static const String decisionsPath = '/api/decisions';

  /// Users endpoints
  static const String usersPath = '/api/users';

  /// Agences endpoints
  static const String agencesPath = '/api/agences';

  /// Rules endpoints
  static const String rulesPath = '/api/rules';
  static const String referencesPath = '/api/rules/references';
  static const String evaluatePath = '/api/rules/evaluate';
  static const String refItemsPath = '/api/rules/reference-items';

  /// Notifications
  static const String notificationsPath = '/api/notifications';

  /// Channels
  static const String channelsWebPath = '/api/channels/web';
}
