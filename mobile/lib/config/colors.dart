import 'package:flutter/material.dart';

/// AutoAssist Aurora color system — Light & Dark palettes via ThemeExtension
@immutable
class AppColorScheme extends ThemeExtension<AppColorScheme> {
  // ── Background layers ──
  final Color background;
  final Color surface;
  final Color elevated;
  final Color overlay;
  final Color muted;

  // ── Text hierarchy ──
  final Color text;
  final Color text2;
  final Color text3;

  // ── Brand / Primary ──
  final Color primary;
  final Color primarySoft;
  final Color secondary;

  // ── Semantic ──
  final Color success;
  final Color successSoft;
  final Color warning;
  final Color warningSoft;
  final Color danger;
  final Color dangerSoft;
  final Color info;
  final Color infoSoft;

  // ── Canal badges ──
  final Color canalCall;
  final Color canalEmail;
  final Color canalWeb;

  // ── Border ──
  final Color border;
  final Color borderSubtle;

  // ── Gradients ──
  final LinearGradient gradientCta;
  final LinearGradient gradientCard;
  final LinearGradient gradientLogin;

  // ── Shadows ──
  final Color shadow;

  // ── Brightness flag ──
  final bool isDark;

  const AppColorScheme({
    required this.background,
    required this.surface,
    required this.elevated,
    required this.overlay,
    required this.muted,
    required this.text,
    required this.text2,
    required this.text3,
    required this.primary,
    required this.primarySoft,
    required this.secondary,
    required this.success,
    required this.successSoft,
    required this.warning,
    required this.warningSoft,
    required this.danger,
    required this.dangerSoft,
    required this.info,
    required this.infoSoft,
    required this.canalCall,
    required this.canalEmail,
    required this.canalWeb,
    required this.border,
    required this.borderSubtle,
    required this.gradientCta,
    required this.gradientCard,
    required this.gradientLogin,
    required this.shadow,
    required this.isDark,
  });

  // ═══════════════════════════════════════════════════
  //  DARK PALETTE — Aurora Night
  // ═══════════════════════════════════════════════════
  static const dark = AppColorScheme(
    isDark: true,
    background: Color(0xFF0B0E14),
    surface: Color(0xFF151921),
    elevated: Color(0xFF1E2230),
    overlay: Color(0x0DFFFFFF),
    muted: Color(0x1AFFFFFF),
    text: Color(0xFFF1F5F9),
    text2: Color(0xFF94A3B8),
    text3: Color(0xFF64748B),
    primary: Color(0xFF7C6FFF),
    primarySoft: Color(0x1A7C6FFF),
    secondary: Color(0xFF00D4AA),
    success: Color(0xFF00C48C),
    successSoft: Color(0x1A00C48C),
    warning: Color(0xFFFFB800),
    warningSoft: Color(0x1AFFB800),
    danger: Color(0xFFFF4757),
    dangerSoft: Color(0x1AFF4757),
    info: Color(0xFF3B82F6),
    infoSoft: Color(0x1A3B82F6),
    canalCall: Color(0xFF8B5CF6),
    canalEmail: Color(0xFF3B82F6),
    canalWeb: Color(0xFF10B981),
    border: Color(0x14FFFFFF),
    borderSubtle: Color(0x0AFFFFFF),
    shadow: Color(0x00000000),
    gradientCta: LinearGradient(
      begin: Alignment.topLeft,
      end: Alignment.bottomRight,
      colors: [Color(0xFF7C6FFF), Color(0xFF00D4AA)],
    ),
    gradientCard: LinearGradient(
      begin: Alignment.topLeft,
      end: Alignment.bottomRight,
      colors: [Color(0x147C6FFF), Color(0x0A00D4AA), Color(0x0FA78BFA)],
    ),
    gradientLogin: LinearGradient(
      begin: Alignment.topLeft,
      end: Alignment.bottomRight,
      colors: [Color(0xFF0B0E14), Color(0xFF1A1145), Color(0xFF0B0E14)],
    ),
  );

  // ═══════════════════════════════════════════════════
  //  LIGHT PALETTE — Aurora Day
  // ═══════════════════════════════════════════════════
  static const light = AppColorScheme(
    isDark: false,
    background: Color(0xFFF4F6FB),
    surface: Color(0xFFFFFFFF),
    elevated: Color(0xFFF0F2F8),
    overlay: Color(0x08000000),
    muted: Color(0x0F000000),
    text: Color(0xFF0F172A),
    text2: Color(0xFF475569),
    text3: Color(0xFF94A3B8),
    primary: Color(0xFF6358E1),
    primarySoft: Color(0x146358E1),
    secondary: Color(0xFF00B894),
    success: Color(0xFF00A676),
    successSoft: Color(0x1400A676),
    warning: Color(0xFFF5A623),
    warningSoft: Color(0x14F5A623),
    danger: Color(0xFFE74C5E),
    dangerSoft: Color(0x14E74C5E),
    info: Color(0xFF2563EB),
    infoSoft: Color(0x142563EB),
    canalCall: Color(0xFF7C3AED),
    canalEmail: Color(0xFF2563EB),
    canalWeb: Color(0xFF059669),
    border: Color(0x0F0F172A),
    borderSubtle: Color(0x080F172A),
    shadow: Color(0x14000000),
    gradientCta: LinearGradient(
      begin: Alignment.topLeft,
      end: Alignment.bottomRight,
      colors: [Color(0xFF6358E1), Color(0xFF00B894)],
    ),
    gradientCard: LinearGradient(
      begin: Alignment.topLeft,
      end: Alignment.bottomRight,
      colors: [Color(0x0A6358E1), Color(0x0800B894), Color(0x0CA78BFA)],
    ),
    gradientLogin: LinearGradient(
      begin: Alignment.topLeft,
      end: Alignment.bottomRight,
      colors: [Color(0xFFEDE9FE), Color(0xFFD5F5F0), Color(0xFFF0F2F8)],
    ),
  );

  // ─── Helper methods ───
  Color canalColor(String canal) {
    switch (canal) {
      case 'CALL': return canalCall;
      case 'EMAIL': return canalEmail;
      case 'WEB': return canalWeb;
      default: return text3;
    }
  }

  Color statutColor(String statut) {
    switch (statut) {
      case 'NOUVEAU': return info;
      case 'EN_COURS': return warning;
      case 'RESOLU': return success;
      case 'FERME': return text3;
      default: return text3;
    }
  }

  Color decisionColor(String decision) {
    switch (decision) {
      case 'FAVORABLE': return success;
      case 'DEFAVORABLE': return danger;
      case 'ETUDE': return warning;
      default: return text3;
    }
  }

  Color priorityColor(int priority) {
    switch (priority) {
      case 1: return danger;
      case 2: return warning;
      case 3: return success;
      default: return text3;
    }
  }

  // ─── ThemeExtension overrides ───
  @override
  ThemeExtension<AppColorScheme> copyWith({
    Color? background, Color? surface, Color? elevated,
    Color? overlay, Color? muted,
    Color? text, Color? text2, Color? text3,
    Color? primary, Color? primarySoft, Color? secondary,
    Color? success, Color? successSoft,
    Color? warning, Color? warningSoft,
    Color? danger, Color? dangerSoft,
    Color? info, Color? infoSoft,
    Color? canalCall, Color? canalEmail, Color? canalWeb,
    Color? border, Color? borderSubtle,
    LinearGradient? gradientCta, LinearGradient? gradientCard,
    LinearGradient? gradientLogin,
    Color? shadow, bool? isDark,
  }) {
    return AppColorScheme(
      background: background ?? this.background,
      surface: surface ?? this.surface,
      elevated: elevated ?? this.elevated,
      overlay: overlay ?? this.overlay,
      muted: muted ?? this.muted,
      text: text ?? this.text,
      text2: text2 ?? this.text2,
      text3: text3 ?? this.text3,
      primary: primary ?? this.primary,
      primarySoft: primarySoft ?? this.primarySoft,
      secondary: secondary ?? this.secondary,
      success: success ?? this.success,
      successSoft: successSoft ?? this.successSoft,
      warning: warning ?? this.warning,
      warningSoft: warningSoft ?? this.warningSoft,
      danger: danger ?? this.danger,
      dangerSoft: dangerSoft ?? this.dangerSoft,
      info: info ?? this.info,
      infoSoft: infoSoft ?? this.infoSoft,
      canalCall: canalCall ?? this.canalCall,
      canalEmail: canalEmail ?? this.canalEmail,
      canalWeb: canalWeb ?? this.canalWeb,
      border: border ?? this.border,
      borderSubtle: borderSubtle ?? this.borderSubtle,
      gradientCta: gradientCta ?? this.gradientCta,
      gradientCard: gradientCard ?? this.gradientCard,
      gradientLogin: gradientLogin ?? this.gradientLogin,
      shadow: shadow ?? this.shadow,
      isDark: isDark ?? this.isDark,
    );
  }

  @override
  ThemeExtension<AppColorScheme> lerp(covariant ThemeExtension<AppColorScheme>? other, double t) {
    if (other is! AppColorScheme) return this;
    return AppColorScheme(
      background: Color.lerp(background, other.background, t)!,
      surface: Color.lerp(surface, other.surface, t)!,
      elevated: Color.lerp(elevated, other.elevated, t)!,
      overlay: Color.lerp(overlay, other.overlay, t)!,
      muted: Color.lerp(muted, other.muted, t)!,
      text: Color.lerp(text, other.text, t)!,
      text2: Color.lerp(text2, other.text2, t)!,
      text3: Color.lerp(text3, other.text3, t)!,
      primary: Color.lerp(primary, other.primary, t)!,
      primarySoft: Color.lerp(primarySoft, other.primarySoft, t)!,
      secondary: Color.lerp(secondary, other.secondary, t)!,
      success: Color.lerp(success, other.success, t)!,
      successSoft: Color.lerp(successSoft, other.successSoft, t)!,
      warning: Color.lerp(warning, other.warning, t)!,
      warningSoft: Color.lerp(warningSoft, other.warningSoft, t)!,
      danger: Color.lerp(danger, other.danger, t)!,
      dangerSoft: Color.lerp(dangerSoft, other.dangerSoft, t)!,
      info: Color.lerp(info, other.info, t)!,
      infoSoft: Color.lerp(infoSoft, other.infoSoft, t)!,
      canalCall: Color.lerp(canalCall, other.canalCall, t)!,
      canalEmail: Color.lerp(canalEmail, other.canalEmail, t)!,
      canalWeb: Color.lerp(canalWeb, other.canalWeb, t)!,
      border: Color.lerp(border, other.border, t)!,
      borderSubtle: Color.lerp(borderSubtle, other.borderSubtle, t)!,
      shadow: Color.lerp(shadow, other.shadow, t)!,
      gradientCta: t < 0.5 ? gradientCta : other.gradientCta,
      gradientCard: t < 0.5 ? gradientCard : other.gradientCard,
      gradientLogin: t < 0.5 ? gradientLogin : other.gradientLogin,
      isDark: t < 0.5 ? isDark : other.isDark,
    );
  }
}

/// Convenient extension to access the AppColorScheme from BuildContext
extension AppColorsX on BuildContext {
  AppColorScheme get colors => Theme.of(this).extension<AppColorScheme>()!;
}
