import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Theme state provider — persists user's light/dark preference
class ThemeProvider extends ChangeNotifier {
  static const _key = 'theme_mode';
  final _storage = const FlutterSecureStorage();

  ThemeMode _mode = ThemeMode.dark;
  ThemeMode get mode => _mode;
  bool get isDark => _mode == ThemeMode.dark;

  /// Load stored theme preference
  Future<void> init() async {
    try {
      final stored = await _storage.read(key: _key);
      _mode = stored == 'light' ? ThemeMode.light : ThemeMode.dark;
    } catch (_) {
      _mode = ThemeMode.dark;
    }
    notifyListeners();
  }

  /// Toggle between light and dark
  Future<void> toggle() async {
    _mode = isDark ? ThemeMode.light : ThemeMode.dark;
    notifyListeners();
    await _storage.write(key: _key, value: isDark ? 'dark' : 'light');
  }

  /// Set explicit mode
  Future<void> setMode(ThemeMode mode) async {
    _mode = mode;
    notifyListeners();
    await _storage.write(key: _key, value: isDark ? 'dark' : 'light');
  }
}
