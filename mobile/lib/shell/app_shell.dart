import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_fonts/google_fonts.dart';
import 'dart:ui';
import 'package:provider/provider.dart';
import '../config/colors.dart';
import '../providers/auth_provider.dart';
import '../screens/dashboard_screen.dart';
import '../screens/ticket_list_screen.dart';
import '../screens/queue_screen.dart';
import '../screens/settings_screen.dart';
import '../screens/analytics_screen.dart';
import '../screens/users_screen.dart';
import '../screens/agences_screen.dart';
import '../screens/rules_screen.dart';
import '../screens/notifications_screen.dart';
import '../widgets/logo.dart'; // Import du logo

class AppShell extends StatefulWidget {
  const AppShell({super.key});

  @override
  State<AppShell> createState() => _AppShellState();
}

class _AppShellState extends State<AppShell> {
  int _currentIndex = 0;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final user = context.watch<AuthProvider>().user;

    final screens = <Widget>[
      const DashboardScreen(),
      const TicketListScreen(),
      const QueueScreen(),
      const _MoreScreen(),
    ];

    return Scaffold(
      appBar: AppBar(
        title: Row(children: [
          const AppLogo(size: 28, animated: true),
          const SizedBox(width: 10),
          Text('AutoAssist', style: GoogleFonts.inter(fontSize: 16, fontWeight: FontWeight.w800, color: c.text)),
        ]),
        actions: [
          if (user != null) ...[
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
              margin: const EdgeInsets.only(right: 8),
              decoration: BoxDecoration(
                color: c.primarySoft,
                borderRadius: BorderRadius.circular(8),
              ),
              child: Text(user.role, style: GoogleFonts.inter(fontSize: 9, fontWeight: FontWeight.w600, color: c.primary)),
            ),
            GestureDetector(
              onTap: () {
                HapticFeedback.lightImpact();
                Navigator.push(context, MaterialPageRoute(builder: (_) => const SettingsScreen()));
              },
              child: Container(
                width: 32, height: 32,
                margin: const EdgeInsets.only(right: 12),
                decoration: BoxDecoration(
                  gradient: LinearGradient(colors: [c.primary, c.canalCall]),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Center(child: Text(
                  '${user.prenom.isNotEmpty ? user.prenom[0] : ''}${user.nom.isNotEmpty ? user.nom[0] : ''}',
                  style: GoogleFonts.inter(fontSize: 11, fontWeight: FontWeight.w700, color: Colors.white),
                )),
              ),
            ),
          ],
        ],
      ),
      body: IndexedStack(index: _currentIndex, children: screens),
      bottomNavigationBar: ClipRRect(
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 10.0, sigmaY: 10.0),
          child: Container(
            decoration: BoxDecoration(
              color: c.surface.withOpacity(0.85),
              border: Border(top: BorderSide(color: c.border, width: 1)),
              boxShadow: c.isDark ? [] : [BoxShadow(color: c.shadow, blurRadius: 12, offset: const Offset(0, -2))],
            ),
            child: BottomNavigationBar(
              backgroundColor: Colors.transparent,
              elevation: 0,
              currentIndex: _currentIndex,
          onTap: (i) {
            HapticFeedback.lightImpact();
            setState(() => _currentIndex = i);
          },
          items: const [
            BottomNavigationBarItem(icon: Icon(Icons.dashboard_outlined), activeIcon: Icon(Icons.dashboard), label: 'Tableau'),
            BottomNavigationBarItem(icon: Icon(Icons.confirmation_number_outlined), activeIcon: Icon(Icons.confirmation_number), label: 'Tickets'),
            BottomNavigationBarItem(icon: Icon(Icons.queue_outlined), activeIcon: Icon(Icons.queue), label: 'File'),
            BottomNavigationBarItem(icon: Icon(Icons.more_horiz), activeIcon: Icon(Icons.more_horiz), label: 'Plus'),
            ],
          ),
        ),
      ),
      ),
    );
  }
}

/// Onglet "Plus" — destinations secondaires regroupées par section
/// (cohérent avec la sidebar groupée du front React : IA / Administration / Compte).
class _MoreScreen extends StatelessWidget {
  const _MoreScreen();

  void _push(BuildContext context, Widget screen) {
    HapticFeedback.lightImpact();
    Navigator.push(context, MaterialPageRoute(builder: (_) => screen));
  }

  Future<void> _confirmLogout(BuildContext context, AuthProvider auth) async {
    final c = context.colors;
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('Se déconnecter ?', style: GoogleFonts.inter(fontWeight: FontWeight.w700)),
        content: const Text("Vous devrez vous reconnecter pour accéder à l'application."),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Annuler')),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: Text('Déconnexion', style: TextStyle(color: c.danger, fontWeight: FontWeight.w700)),
          ),
        ],
      ),
    );
    if (ok == true) {
      HapticFeedback.mediumImpact();
      await auth.logout();
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final auth = context.watch<AuthProvider>();
    final user = auth.user;

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
      children: [
        if (user != null) ...[
          _userHeader(context, user),
          const SizedBox(height: 22),
        ],

        // ── Intelligence IA + Administration (SUPERVISOR & ADMIN) ──
        if (auth.isSupervisor) ...[
          _sectionLabel(context, 'Intelligence IA'),
          _buildMenuItem(context, _MenuItem(Icon(Icons.analytics_outlined, color: c.primary),
              'Analytique', 'KPIs et graphiques', () => _push(context, const AnalyticsScreen()))),
          _buildMenuItem(context, _MenuItem(const Icon(Icons.rule_folder_outlined, color: Colors.indigo),
              'Moteur de règles', 'Règles de décision', () => _push(context, const RulesScreen()))),
          const SizedBox(height: 14),
          _sectionLabel(context, 'Administration'),
          _buildMenuItem(context, _MenuItem(const Icon(Icons.people_outline, color: Colors.teal),
              'Utilisateurs', 'Gestion des comptes', () => _push(context, const UsersScreen()))),
          _buildMenuItem(context, _MenuItem(const Icon(Icons.location_city_outlined, color: Colors.deepOrange),
              'Agences', 'Gestion des agences', () => _push(context, const AgencesScreen()))),
          if (auth.isAdmin)
            _buildMenuItem(context, _MenuItem(const Icon(Icons.notifications_outlined, color: Colors.amber),
                'Notifications', 'Journal des envois', () => _push(context, const NotificationsScreen()))),
          const SizedBox(height: 14),
        ],

        // ── Compte (tous les rôles) ──
        _sectionLabel(context, 'Compte'),
        _buildMenuItem(context, _MenuItem(const Icon(Icons.settings_outlined, color: Colors.blueGrey),
            'Paramètres', 'Profil & Sécurité', () => _push(context, const SettingsScreen()))),
        _buildMenuItem(context, _MenuItem(const Icon(Icons.logout_outlined, color: Colors.redAccent),
            'Déconnexion', 'Se déconnecter', () => _confirmLogout(context, auth))),
      ],
    );
  }

  Widget _sectionLabel(BuildContext context, String text) {
    final c = context.colors;
    return Padding(
      padding: const EdgeInsets.fromLTRB(4, 0, 4, 10),
      child: Text(text.toUpperCase(),
          style: GoogleFonts.inter(fontSize: 10, fontWeight: FontWeight.w700, color: c.text3, letterSpacing: 1.2)),
    );
  }

  Widget _userHeader(BuildContext context, dynamic user) {
    final c = context.colors;
    final initials =
        '${user.prenom.isNotEmpty ? user.prenom[0] : ''}${user.nom.isNotEmpty ? user.nom[0] : ''}';
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: c.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: c.border),
        boxShadow: c.isDark ? [] : [BoxShadow(color: c.shadow, blurRadius: 10, offset: const Offset(0, 3))],
      ),
      child: Row(children: [
        Container(
          width: 46, height: 46,
          decoration: BoxDecoration(
            gradient: LinearGradient(colors: [c.primary, c.canalCall]),
            borderRadius: BorderRadius.circular(14),
          ),
          child: Center(child: Text(initials,
              style: GoogleFonts.inter(fontSize: 16, fontWeight: FontWeight.w700, color: Colors.white))),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text('${user.prenom} ${user.nom}'.trim(),
                maxLines: 1, overflow: TextOverflow.ellipsis,
                style: GoogleFonts.inter(fontSize: 15, fontWeight: FontWeight.w700, color: c.text)),
            const SizedBox(height: 2),
            Text(user.email,
                maxLines: 1, overflow: TextOverflow.ellipsis,
                style: GoogleFonts.inter(fontSize: 11, color: c.text3)),
          ]),
        ),
        const SizedBox(width: 8),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
          decoration: BoxDecoration(color: c.primarySoft, borderRadius: BorderRadius.circular(8)),
          child: Text(user.role,
              style: GoogleFonts.inter(fontSize: 9, fontWeight: FontWeight.w700, color: c.primary)),
        ),
      ]),
    );
  }

  Widget _buildMenuItem(BuildContext context, _MenuItem item) {
    final c = context.colors;
    final iconColor = (item.icon as Icon).color ?? c.primary;
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      decoration: BoxDecoration(
        color: c.surface,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: c.border),
        boxShadow: c.isDark ? [] : [BoxShadow(color: c.shadow, blurRadius: 8, offset: const Offset(0, 2))],
      ),
      child: ListTile(
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
        leading: Container(
          width: 40,
          height: 40,
          decoration: BoxDecoration(
            color: iconColor.withOpacity(0.12),
            borderRadius: BorderRadius.circular(10),
          ),
          child: Center(child: item.icon),
        ),
        title: Text(item.title, style: GoogleFonts.inter(fontSize: 14, fontWeight: FontWeight.w600, color: c.text)),
        subtitle: Text(item.subtitle, style: GoogleFonts.inter(fontSize: 11, color: c.text3)),
        trailing: Icon(Icons.chevron_right, size: 16, color: c.text3),
        onTap: item.onTap,
      ),
    );
  }
}

class _MenuItem {
  final Widget icon;
  final String title;
  final String subtitle;
  final VoidCallback onTap;
  _MenuItem(this.icon, this.title, this.subtitle, this.onTap);
}
