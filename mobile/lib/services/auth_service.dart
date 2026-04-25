import '../config/api_config.dart';
import '../models/auth.dart';
import 'api_client.dart';

/// Authentication service — mirrors services/authService.ts
class AuthService {
  final _api = ApiClient();

  Future<AuthUser> login(String email, String password) async {
    final res = await _api.dio.post(ApiConfig.loginPath, data: {
      'email': email,
      'password': password,
    });
    final user = AuthUser.fromJson(res.data);
    await _api.saveTokens(user);
    return user;
  }

  Future<void> logout(String? refreshToken) async {
    try {
      if (refreshToken != null) {
        await _api.dio.post(ApiConfig.logoutPath, data: {'refreshToken': refreshToken});
      }
    } catch (_) {} finally {
      await _api.clearTokens();
    }
  }

  Future<AuthUser?> loadStoredUser() async {
    return await _api.loadUser();
  }
}
