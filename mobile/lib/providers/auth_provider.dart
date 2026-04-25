import 'package:flutter/foundation.dart';
import '../models/auth.dart';
import '../services/auth_service.dart';

/// Authentication state provider — mirrors contexts/AuthContext.tsx
class AuthProvider extends ChangeNotifier {
  final AuthService _authService = AuthService();

  AuthUser? _user;
  bool _isLoading = true;

  AuthUser? get user => _user;
  bool get isAuthenticated => _user != null;
  bool get isLoading => _isLoading;
  bool get isAdmin => _user?.isAdmin ?? false;
  bool get isSupervisor => _user?.isSupervisor ?? false;

  /// Try to restore session from secure storage
  Future<void> init() async {
    _isLoading = true;
    notifyListeners();
    try {
      _user = await _authService.loadStoredUser();
    } catch (_) {
      _user = null;
    }
    _isLoading = false;
    notifyListeners();
  }

  /// Login with email + password
  Future<void> login(String email, String password) async {
    _user = await _authService.login(email, password);
    notifyListeners();
  }

  /// Logout
  Future<void> logout() async {
    await _authService.logout(_user?.refreshToken);
    _user = null;
    notifyListeners();
  }
}
