import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:provider/provider.dart';
import '../config/colors.dart';
import '../providers/auth_provider.dart';
import '../providers/theme_provider.dart';
import '../services/user_service.dart';
import '../services/agence_service.dart';
import '../models/user.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  final _userService = UserService();
  final _agenceService = AgenceService();

  String _myAgenceName = '—';
  bool _saving = false;

  // Profile form controllers
  late TextEditingController _prenomC;
  late TextEditingController _nomC;
  late TextEditingController _emailC;
  late TextEditingController _phoneC;

  // Password form controllers
  final _currentPwC = TextEditingController();
  final _newPwC = TextEditingController();
  final _confirmPwC = TextEditingController();

  @override
  void initState() {
    super.initState();
    final user = context.read<AuthProvider>().user;
    _prenomC = TextEditingController(text: user?.prenom ?? '');
    _nomC = TextEditingController(text: user?.nom ?? '');
    _emailC = TextEditingController(text: user?.email ?? '');
    _phoneC = TextEditingController();
    _resolveAgence();
  }

  @override
  void dispose() {
    _prenomC.dispose();
    _nomC.dispose();
    _emailC.dispose();
    _phoneC.dispose();
    _currentPwC.dispose();
    _newPwC.dispose();
    _confirmPwC.dispose();
    super.dispose();
  }

  Future<void> _resolveAgence() async {
    final user = context.read<AuthProvider>().user;
    if (user?.agenceId != null && user!.agenceId!.isNotEmpty) {
      try {
        final agence = await _agenceService.getAgenceById(user.agenceId!);
        if (mounted) setState(() => _myAgenceName = agence.nom);
      } catch (_) {
        if (mounted) setState(() => _myAgenceName = user.agenceId ?? '—');
      }
    }
  }

  Future<void> _handleSaveProfile() async {
    final c = context.colors;
    final auth = context.read<AuthProvider>();
    final user = auth.user;
    if (user == null) return;

    setState(() => _saving = true);
    try {
      await _userService.updateUser(user.userId, UserRequest(
        prenom: _prenomC.text.trim(),
        nom: _nomC.text.trim(),
        email: _emailC.text.trim(),
        telephone: _phoneC.text.trim(),
        role: user.role,
        password: '',
        agenceId: user.agenceId,
      ));
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: const Text('✅ Profil mis à jour !'), backgroundColor: c.success),
        );
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: const Text('Erreur mise à jour profil'), backgroundColor: c.danger),
        );
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _handleChangePassword() async {
    final c = context.colors;
    final auth = context.read<AuthProvider>();
    final user = auth.user;
    if (user == null) return;

    if (_newPwC.text != _confirmPwC.text) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: const Text('Les mots de passe ne correspondent pas'), backgroundColor: c.danger),
      );
      return;
    }
    if (_newPwC.text.length < 6) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: const Text('Min 6 caractères'), backgroundColor: c.danger),
      );
      return;
    }

    setState(() => _saving = true);
    try {
      await _userService.changePassword(
        user.userId,
        oldPassword: _currentPwC.text,
        newPassword: _newPwC.text,
      );
      _currentPwC.clear();
      _newPwC.clear();
      _confirmPwC.clear();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: const Text('✅ Mot de passe changé !'), backgroundColor: c.success),
        );
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: const Text('Erreur changement de mot de passe'), backgroundColor: c.danger),
        );
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final auth = context.watch<AuthProvider>();
    final themeProvider = context.watch<ThemeProvider>();
    final user = auth.user;

    return Scaffold(
      appBar: AppBar(title: Text('Paramètres', style: GoogleFonts.inter(fontSize: 18, fontWeight: FontWeight.w800))),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // ── Profile Card ──
          Container(
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              color: c.surface,
              borderRadius: BorderRadius.circular(20),
              border: Border.all(color: c.border),
              boxShadow: c.isDark ? [] : [BoxShadow(color: c.shadow, blurRadius: 12, offset: const Offset(0, 4))],
            ),
            child: Column(
              children: [
                Container(
                  width: 64, height: 64,
                  decoration: BoxDecoration(
                    gradient: c.gradientCta,
                    borderRadius: BorderRadius.circular(20),
                    boxShadow: [BoxShadow(color: c.primary.withOpacity(0.3), blurRadius: 12, offset: const Offset(0, 4))],
                  ),
                  child: Center(child: Text(
                    '${user?.prenom.isNotEmpty == true ? user!.prenom[0] : ''}${user?.nom.isNotEmpty == true ? user!.nom[0] : ''}',
                    style: GoogleFonts.inter(fontSize: 22, fontWeight: FontWeight.w900, color: Colors.white),
                  )),
                ),
                const SizedBox(height: 12),
                Text('${user?.prenom} ${user?.nom}', style: GoogleFonts.inter(fontSize: 16, fontWeight: FontWeight.w800, color: c.text)),
                Text(user?.email ?? '', style: GoogleFonts.inter(fontSize: 12, color: c.text3)),
                const SizedBox(height: 4),
                Text('Agence : $_myAgenceName', style: GoogleFonts.inter(fontSize: 11, color: c.text2)),
                const SizedBox(height: 8),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: c.primarySoft,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Text(user?.role ?? '', style: GoogleFonts.inter(fontSize: 10, fontWeight: FontWeight.w600, color: c.primary)),
                ),
              ],
            ),
          ).animate().fadeIn().slideY(begin: 0.05),

          const SizedBox(height: 16),

          // ── Profile Editing ──
          _sectionTitle(c, 'MODIFIER LE PROFIL'),
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: c.surface,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: c.border),
            ),
            child: Column(children: [
              Row(children: [
                Expanded(child: _formField(c, 'Prénom', _prenomC)),
                const SizedBox(width: 10),
                Expanded(child: _formField(c, 'Nom', _nomC)),
              ]),
              const SizedBox(height: 10),
              _formField(c, 'Email', _emailC, keyboardType: TextInputType.emailAddress),
              const SizedBox(height: 10),
              _formField(c, 'Téléphone', _phoneC, keyboardType: TextInputType.phone),
              const SizedBox(height: 14),
              SizedBox(
                width: double.infinity,
                child: Container(
                  decoration: BoxDecoration(gradient: c.gradientCta, borderRadius: BorderRadius.circular(12)),
                  child: ElevatedButton(
                    onPressed: _saving ? null : _handleSaveProfile,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.transparent, shadowColor: Colors.transparent,
                      padding: const EdgeInsets.symmetric(vertical: 14),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                    child: Text(_saving ? 'Sauvegarde...' : 'Sauvegarder les modifications',
                        style: GoogleFonts.inter(fontSize: 13, fontWeight: FontWeight.w600, color: Colors.white)),
                  ),
                ),
              ),
            ]),
          ).animate().fadeIn(delay: 100.ms),

          const SizedBox(height: 16),

          // ── Password Change ──
          _sectionTitle(c, 'CHANGER LE MOT DE PASSE'),
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: c.surface,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: c.border),
            ),
            child: Column(children: [
              _formField(c, 'Mot de passe actuel', _currentPwC, obscure: true),
              const SizedBox(height: 10),
              _formField(c, 'Nouveau mot de passe', _newPwC, obscure: true),
              const SizedBox(height: 10),
              _formField(c, 'Confirmer', _confirmPwC, obscure: true),
              const SizedBox(height: 14),
              SizedBox(
                width: double.infinity,
                child: OutlinedButton(
                  onPressed: _saving ? null : _handleChangePassword,
                  style: OutlinedButton.styleFrom(
                    side: BorderSide(color: c.primary),
                    padding: const EdgeInsets.symmetric(vertical: 14),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                  ),
                  child: Text(_saving ? '...' : 'Changer le mot de passe',
                      style: GoogleFonts.inter(fontSize: 13, fontWeight: FontWeight.w600, color: c.primary)),
                ),
              ),
            ]),
          ).animate().fadeIn(delay: 200.ms),

          const SizedBox(height: 16),

          // ── Preferences ──
          _sectionTitle(c, 'PRÉFÉRENCES'),
          _settingsTile(c, Icons.notifications_outlined, 'Notifications',
            trailing: Icon(Icons.toggle_on, color: c.primary, size: 28)),

          // ── Theme Toggle ──
          _settingsTile(c, themeProvider.isDark ? Icons.dark_mode_outlined : Icons.light_mode_outlined,
            themeProvider.isDark ? 'Mode sombre' : 'Mode clair',
            trailing: GestureDetector(
              onTap: () => themeProvider.toggle(),
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 300),
                width: 52, height: 28,
                padding: const EdgeInsets.all(3),
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(14),
                  gradient: themeProvider.isDark ? c.gradientCta : null,
                  color: themeProvider.isDark ? null : c.muted,
                ),
                child: AnimatedAlign(
                  duration: const Duration(milliseconds: 300),
                  curve: Curves.easeOutBack,
                  alignment: themeProvider.isDark ? Alignment.centerRight : Alignment.centerLeft,
                  child: Container(
                    width: 22, height: 22,
                    decoration: BoxDecoration(
                      color: Colors.white,
                      shape: BoxShape.circle,
                      boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.15), blurRadius: 4)],
                    ),
                    child: Center(child: Icon(
                      themeProvider.isDark ? Icons.nightlight_round : Icons.wb_sunny,
                      size: 12,
                      color: themeProvider.isDark ? c.primary : const Color(0xFFF5A623),
                    )),
                  ),
                ),
              ),
            ),
          ),

          const SizedBox(height: 16),
          _sectionTitle(c, 'À PROPOS'),
          _settingsTile(c, Icons.info_outline, 'Version', trailing: Text('2.0.0', style: GoogleFonts.inter(fontSize: 12, color: c.text3))),

          const SizedBox(height: 24),

          // ── Logout ──
          SizedBox(
            width: double.infinity,
            child: OutlinedButton.icon(
              onPressed: () async {
                await auth.logout();
                if (context.mounted) Navigator.of(context).popUntil((route) => route.isFirst);
              },
              icon: Icon(Icons.logout, size: 16, color: c.danger),
              label: Text('Se déconnecter', style: GoogleFonts.inter(fontSize: 13, fontWeight: FontWeight.w600, color: c.danger)),
              style: OutlinedButton.styleFrom(
                side: BorderSide(color: c.danger, width: 1),
                padding: const EdgeInsets.symmetric(vertical: 14),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              ),
            ),
          ),

          const SizedBox(height: 16),
          Center(child: Text('AURORA v2.0 · AutoAssist', style: GoogleFonts.inter(fontSize: 9, color: c.text3, letterSpacing: 1))),
        ],
      ),
    );
  }

  Widget _sectionTitle(AppColorScheme c, String title) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Text(title, style: GoogleFonts.inter(fontSize: 9, fontWeight: FontWeight.w600, color: c.text3, letterSpacing: 1)),
    );
  }

  Widget _settingsTile(AppColorScheme c, IconData icon, String label, {VoidCallback? onTap, Widget? trailing}) {
    return Container(
      margin: const EdgeInsets.only(bottom: 4),
      decoration: BoxDecoration(
        color: c.surface,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: c.border),
      ),
      child: ListTile(
        contentPadding: const EdgeInsets.symmetric(horizontal: 16),
        leading: Icon(icon, size: 18, color: c.text2),
        title: Text(label, style: GoogleFonts.inter(fontSize: 13, fontWeight: FontWeight.w500, color: c.text)),
        trailing: trailing ?? Icon(Icons.chevron_right, size: 16, color: c.text3),
        onTap: onTap,
        dense: true,
      ),
    );
  }

  Widget _formField(AppColorScheme c, String label, TextEditingController ctrl,
      {bool obscure = false, TextInputType? keyboardType}) {
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text(label, style: GoogleFonts.inter(fontSize: 10, fontWeight: FontWeight.w600, color: c.text3)),
      const SizedBox(height: 4),
      TextField(
        controller: ctrl,
        obscureText: obscure,
        keyboardType: keyboardType,
        style: GoogleFonts.inter(fontSize: 12, color: c.text),
        decoration: InputDecoration(
          isDense: true,
          contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          filled: true,
          fillColor: c.elevated,
          border: OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: BorderSide(color: c.border)),
          enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: BorderSide(color: c.border)),
          focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: BorderSide(color: c.primary)),
        ),
      ),
    ]);
  }
}
