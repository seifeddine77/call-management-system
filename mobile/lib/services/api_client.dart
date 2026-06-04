import 'dart:convert';
import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import '../config/api_config.dart';
import '../models/auth.dart';

/// Dio HTTP client with JWT interceptor and token refresh
class ApiClient {
  static final ApiClient _instance = ApiClient._internal();
  factory ApiClient() => _instance;

  late final Dio dio;
  final FlutterSecureStorage _storage = const FlutterSecureStorage();
  bool _isRefreshing = false;

  static const _tokenKey = 'aurora_access_token';
  static const _refreshKey = 'aurora_refresh_token';
  static const _userKey = 'aurora_user';

  ApiClient._internal() {
    dio = Dio(BaseOptions(
      baseUrl: ApiConfig.baseUrl,
      connectTimeout: ApiConfig.connectTimeout,
      receiveTimeout: ApiConfig.receiveTimeout,
      headers: {'Content-Type': 'application/json'},
    ));

    // Auth interceptor
    dio.interceptors.add(InterceptorsWrapper(
      onRequest: (options, handler) async {
        final token = await _storage.read(key: _tokenKey);
        if (token != null && token.isNotEmpty) {
          options.headers['Authorization'] = 'Bearer $token';
        }
        return handler.next(options);
      },
      onError: (error, handler) async {
        if (error.response?.statusCode == 401 && !_isRefreshing) {
          _isRefreshing = true;
          try {
            final refreshToken = await _storage.read(key: _refreshKey);
            if (refreshToken != null && refreshToken.isNotEmpty) {
              // Use a clean Dio without interceptors to avoid recursion
              final refreshDio = Dio(BaseOptions(
                baseUrl: ApiConfig.baseUrl,
                connectTimeout: ApiConfig.connectTimeout,
                receiveTimeout: ApiConfig.receiveTimeout,
                headers: {'Content-Type': 'application/json'},
              ));
              final res = await refreshDio.post(
                ApiConfig.refreshPath,
                data: {'refreshToken': refreshToken},
              );
              final newToken = res.data['accessToken'] ?? res.data['token'];
              final newRefresh = res.data['refreshToken'];
              if (newToken != null) {
                await _storage.write(key: _tokenKey, value: newToken);
                if (newRefresh != null) {
                  await _storage.write(key: _refreshKey, value: newRefresh);
                }
                if (kDebugMode) debugPrint('[ApiClient] Token refreshed successfully');
                // Retry original request with fresh Dio to avoid interceptor loop
                final opts = error.requestOptions;
                opts.headers['Authorization'] = 'Bearer $newToken';
                final retryDio = Dio(BaseOptions(
                  baseUrl: opts.baseUrl,
                  connectTimeout: ApiConfig.connectTimeout,
                  receiveTimeout: ApiConfig.receiveTimeout,
                ));
                final retryResponse = await retryDio.request(
                  opts.path,
                  data: opts.data,
                  queryParameters: opts.queryParameters,
                  options: Options(
                    method: opts.method,
                    headers: opts.headers,
                  ),
                );
                _isRefreshing = false;
                return handler.resolve(retryResponse);
              }
            }
            // No refresh token or refresh failed
            if (kDebugMode) debugPrint('[ApiClient] No valid refresh token, clearing session');
            await clearTokens();
          } catch (e) {
            if (kDebugMode) debugPrint('[ApiClient] Refresh failed: $e');
            await clearTokens();
          }
          _isRefreshing = false;
        }
        return handler.next(error);
      },
    ));
  }

  /// Save auth tokens after login
  Future<void> saveTokens(AuthUser user) async {
    await _storage.write(key: _tokenKey, value: user.accessToken);
    await _storage.write(key: _refreshKey, value: user.refreshToken);
    await _storage.write(key: _userKey, value: jsonEncode(user.toJson()));
  }

  /// Load stored user
  Future<AuthUser?> loadUser() async {
    final data = await _storage.read(key: _userKey);
    if (data != null) {
      try {
        return AuthUser.fromJson(jsonDecode(data));
      } catch (_) {}
    }
    return null;
  }

  /// Clear all stored tokens
  Future<void> clearTokens() async {
    await _storage.delete(key: _tokenKey);
    await _storage.delete(key: _refreshKey);
    await _storage.delete(key: _userKey);
  }

  /// Check if we have a stored token
  Future<bool> hasToken() async {
    final token = await _storage.read(key: _tokenKey);
    return token != null && token.isNotEmpty;
  }

  /// Get stored access token
  Future<String?> getAccessToken() async {
    return await _storage.read(key: _tokenKey);
  }
}
