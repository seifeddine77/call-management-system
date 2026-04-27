import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'colors.dart';

/// Premium Material 3 themes for AutoAssist — Light & Dark
class AppTheme {
  AppTheme._();

  // ═══════════════════════════════════════════════════
  //  DARK THEME
  // ═══════════════════════════════════════════════════
  static ThemeData get darkTheme {
    const c = AppColorScheme.dark;
    return _buildTheme(c, Brightness.dark);
  }

  // ═══════════════════════════════════════════════════
  //  LIGHT THEME
  // ═══════════════════════════════════════════════════
  static ThemeData get lightTheme {
    const c = AppColorScheme.light;
    return _buildTheme(c, Brightness.light);
  }

  // ─── Shared builder ───
  static ThemeData _buildTheme(AppColorScheme c, Brightness brightness) {
    final isDark = brightness == Brightness.dark;
    final base = isDark ? ThemeData.dark(useMaterial3: true) : ThemeData.light(useMaterial3: true);

    return base.copyWith(
      scaffoldBackgroundColor: c.background,
      extensions: [c],
      colorScheme: ColorScheme(
        brightness: brightness,
        primary: c.primary,
        onPrimary: Colors.white,
        secondary: c.secondary,
        onSecondary: Colors.white,
        surface: c.surface,
        onSurface: c.text,
        error: c.danger,
        onError: Colors.white,
      ),
      textTheme: _textTheme(c),
      appBarTheme: AppBarTheme(
        backgroundColor: c.background,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        centerTitle: false,
        titleTextStyle: GoogleFonts.inter(
          fontSize: 18,
          fontWeight: FontWeight.w800,
          color: c.text,
          letterSpacing: -0.5,
        ),
        iconTheme: IconThemeData(color: c.text2, size: 20),
      ),
      bottomNavigationBarTheme: BottomNavigationBarThemeData(
        backgroundColor: c.surface,
        selectedItemColor: c.primary,
        unselectedItemColor: c.text3,
        type: BottomNavigationBarType.fixed,
        elevation: isDark ? 0 : 8,
        selectedLabelStyle: const TextStyle(fontSize: 10, fontWeight: FontWeight.w700),
        unselectedLabelStyle: const TextStyle(fontSize: 10),
      ),
      cardTheme: CardThemeData(
        color: c.surface,
        elevation: isDark ? 0 : 2,
        shadowColor: c.shadow,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: isDark ? BorderSide(color: c.border, width: 1) : BorderSide.none,
        ),
        margin: EdgeInsets.zero,
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: c.elevated,
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: c.border),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: c.border),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: c.primary, width: 1.5),
        ),
        errorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: c.danger),
        ),
        hintStyle: GoogleFonts.inter(color: c.text3, fontSize: 14),
        labelStyle: GoogleFonts.inter(color: c.text3, fontSize: 12),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: c.primary,
          foregroundColor: Colors.white,
          elevation: isDark ? 0 : 2,
          shadowColor: c.primary.withValues(alpha: 0.3),
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          textStyle: GoogleFonts.inter(fontSize: 14, fontWeight: FontWeight.w700),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: c.text2,
          side: BorderSide(color: c.border),
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          textStyle: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.w600),
        ),
      ),
      chipTheme: ChipThemeData(
        backgroundColor: c.elevated,
        selectedColor: c.primarySoft,
        side: BorderSide(color: c.border),
        labelStyle: GoogleFonts.inter(fontSize: 11, fontWeight: FontWeight.w600, color: c.text2),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      ),
      dividerTheme: DividerThemeData(color: c.border, thickness: 1, space: 0),
      dialogTheme: DialogThemeData(
        backgroundColor: c.surface,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        titleTextStyle: GoogleFonts.inter(fontSize: 16, fontWeight: FontWeight.w800, color: c.text),
      ),
      snackBarTheme: SnackBarThemeData(
        backgroundColor: c.elevated,
        contentTextStyle: GoogleFonts.inter(fontSize: 13, color: c.text),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        behavior: SnackBarBehavior.floating,
      ),
    );
  }

  static TextTheme _textTheme(AppColorScheme c) => TextTheme(
    displayLarge: GoogleFonts.inter(fontSize: 28, fontWeight: FontWeight.w900, color: c.text, letterSpacing: -1.5),
    displayMedium: GoogleFonts.inter(fontSize: 24, fontWeight: FontWeight.w800, color: c.text, letterSpacing: -1),
    headlineLarge: GoogleFonts.inter(fontSize: 20, fontWeight: FontWeight.w800, color: c.text, letterSpacing: -0.5),
    headlineMedium: GoogleFonts.inter(fontSize: 18, fontWeight: FontWeight.w700, color: c.text),
    titleLarge: GoogleFonts.inter(fontSize: 16, fontWeight: FontWeight.w700, color: c.text),
    titleMedium: GoogleFonts.inter(fontSize: 14, fontWeight: FontWeight.w600, color: c.text),
    titleSmall: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.w600, color: c.text2),
    bodyLarge: GoogleFonts.inter(fontSize: 14, fontWeight: FontWeight.w400, color: c.text),
    bodyMedium: GoogleFonts.inter(fontSize: 13, fontWeight: FontWeight.w400, color: c.text2),
    bodySmall: GoogleFonts.inter(fontSize: 11, fontWeight: FontWeight.w400, color: c.text3),
    labelLarge: GoogleFonts.inter(fontSize: 13, fontWeight: FontWeight.w700, color: c.text),
    labelMedium: GoogleFonts.inter(fontSize: 11, fontWeight: FontWeight.w600, color: c.text2),
    labelSmall: GoogleFonts.inter(fontSize: 9, fontWeight: FontWeight.w600, color: c.text3, letterSpacing: 0.8),
  );
}
