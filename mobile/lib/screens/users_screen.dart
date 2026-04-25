import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:flutter_animate/flutter_animate.dart';
import '../config/colors.dart';
import '../models/user.dart';
import '../models/agence.dart';
import '../services/user_service.dart';
import '../services/agence_service.dart';

class UsersScreen extends StatefulWidget {
  const UsersScreen({super.key});

  @override
  State<UsersScreen> createState() => _UsersScreenState();
}

class _UsersScreenState extends State<UsersScreen> {
  final _userService = UserService();
  final _agenceService = AgenceService();
  List<User> _users = [];
  List<Agence> _agences = [];
  bool _loading = true;
  String _roleFilter = 'ALL';
  User? _selected;

  @override
  void initState() {
    super.initState();
    _fetch();
    _fetchAgences();
  }

  Future<void> _fetch() async {
    setState(() => _loading = true);
    try {
      final users = await _userService.getUsers(role: _roleFilter != 'ALL' ? _roleFilter : null);
      setState(() {
        _users = users;
        _loading = false;
      });
    } catch (e) {
      debugPrint('[UsersScreen] Fetch error: $e');
      setState(() => _loading = false);
    }
  }

  Future<void> _fetchAgences() async {
    try {
      final page = await _agenceService.getAgences(size: 200);
      setState(() {
        _agences = page.content;
      });
    } catch (_) {}
  }

  String _agenceName(String? agenceId) {
    if (agenceId == null || agenceId.isEmpty) return '—';
    final a = _agences.where((a) => a.id == agenceId).firstOrNull;
    return a?.nom ?? agenceId.substring(0, 8);
  }

  Future<void> _deleteUser(User user) async {
    final c = context.colors;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Supprimer cet utilisateur ?'),
        content: Text('${user.prenom} ${user.nom}\n\nCette action désactivera le compte.',
            style: GoogleFonts.inter(fontSize: 13, color: c.text2)),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Annuler')),
          ElevatedButton(
            onPressed: () => Navigator.pop(ctx, true),
            style: ElevatedButton.styleFrom(backgroundColor: c.danger),
            child: const Text('Supprimer'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    try {
      await _userService.deleteUser(user.id);
      setState(() { _users.removeWhere((u) => u.id == user.id); if (_selected?.id == user.id) _selected = null; });
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: const Text('✅ Utilisateur désactivé'), backgroundColor: c.success));
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: const Text('Erreur suppression'), backgroundColor: c.danger));
    }
  }

  void _showCreateEditDialog({User? existing}) {
    final c = context.colors;
    final isCreate = existing == null;
    final form = UserRequest(
      nom: existing?.nom ?? '',
      prenom: existing?.prenom ?? '',
      email: existing?.email ?? '',
      password: '',
      role: existing?.role ?? 'OPERATEUR',
      agenceId: existing?.agenceId ?? '',
      telephone: existing?.telephone ?? '',
      planning: existing?.planning ?? '',
    );

    final prenomC = TextEditingController(text: form.prenom);
    final nomC = TextEditingController(text: form.nom);
    final emailC = TextEditingController(text: form.email);
    final passwordC = TextEditingController();
    final phoneC = TextEditingController(text: form.telephone);
    final planningC = TextEditingController(text: form.planning ?? '');
    String selectedRole = form.role;
    String selectedAgence = form.agenceId ?? '';
    bool saving = false;

    final planningPresets = [
      {'label': 'Temps plein', 'value': 'Lundi-Vendredi 08:00-17:00'},
      {'label': 'Matin', 'value': 'Lundi-Vendredi 06:00-14:00'},
      {'label': 'Après-midi', 'value': 'Lundi-Vendredi 14:00-22:00'},
      {'label': 'Nuit', 'value': 'Lundi-Vendredi 22:00-06:00'},
      {'label': 'Week-end', 'value': 'Samedi-Dimanche 08:00-18:00'},
    ];

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => StatefulBuilder(builder: (ctx, setBS) {
        return Container(
          padding: EdgeInsets.only(bottom: MediaQuery.of(ctx).viewInsets.bottom),
          decoration: BoxDecoration(
            color: c.surface,
            borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
          ),
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(20),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Center(child: Container(width: 40, height: 4, decoration: BoxDecoration(color: c.border, borderRadius: BorderRadius.circular(2)))),
              const SizedBox(height: 16),
              Text(isCreate ? '👤 Nouvel utilisateur' : '✏️ Modifier utilisateur',
                  style: GoogleFonts.inter(fontSize: 16, fontWeight: FontWeight.w800, color: c.text)),
              const SizedBox(height: 16),

              // Prénom + Nom
              Row(children: [
                Expanded(child: _formTextField(c, 'Prénom *', prenomC)),
                const SizedBox(width: 10),
                Expanded(child: _formTextField(c, 'Nom *', nomC)),
              ]),
              const SizedBox(height: 10),

              // Email
              _formTextField(c, 'Email *', emailC, keyboardType: TextInputType.emailAddress),
              const SizedBox(height: 10),

              // Password
              _formTextField(c, isCreate ? 'Mot de passe *' : 'Nouveau mot de passe (optionnel)', passwordC, obscure: true),
              const SizedBox(height: 10),

              // Rôle + Téléphone
              Row(children: [
                Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text('Rôle', style: GoogleFonts.inter(fontSize: 10, fontWeight: FontWeight.w600, color: c.text3)),
                  const SizedBox(height: 4),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10),
                    decoration: BoxDecoration(color: c.elevated, borderRadius: BorderRadius.circular(10), border: Border.all(color: c.border)),
                    child: DropdownButtonHideUnderline(child: DropdownButton<String>(
                      value: selectedRole,
                      isExpanded: true,
                      dropdownColor: c.elevated,
                      style: GoogleFonts.inter(fontSize: 12, color: c.text),
                      items: ['OPERATEUR', 'SUPERVISEUR', 'ADMIN'].map((r) =>
                        DropdownMenuItem(value: r, child: Text(r))).toList(),
                      onChanged: (v) => setBS(() => selectedRole = v!),
                    )),
                  ),
                ])),
                const SizedBox(width: 10),
                Expanded(child: _formTextField(c, 'Téléphone', phoneC, keyboardType: TextInputType.phone)),
              ]),
              const SizedBox(height: 10),

              // Agence dropdown
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text('Agence ${(selectedRole == 'OPERATEUR' || selectedRole == 'SUPERVISEUR') ? '*' : ''}',
                    style: GoogleFonts.inter(fontSize: 10, fontWeight: FontWeight.w600, color: c.text3)),
                const SizedBox(height: 4),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10),
                  decoration: BoxDecoration(color: c.elevated, borderRadius: BorderRadius.circular(10), border: Border.all(color: c.border)),
                  child: DropdownButtonHideUnderline(child: DropdownButton<String>(
                    value: selectedAgence.isEmpty ? null : selectedAgence,
                    hint: Text('-- Aucune agence --', style: GoogleFonts.inter(fontSize: 12, color: c.text3)),
                    isExpanded: true,
                    dropdownColor: c.elevated,
                    style: GoogleFonts.inter(fontSize: 12, color: c.text),
                    items: [
                      const DropdownMenuItem(value: '', child: Text('-- Aucune agence --')),
                      ..._agences.map((a) => DropdownMenuItem(value: a.id, child: Text(a.nom, overflow: TextOverflow.ellipsis))),
                    ],
                    onChanged: (v) => setBS(() => selectedAgence = v ?? ''),
                  )),
                ),
              ]),
              const SizedBox(height: 10),

              // Planning presets
              Text('📅 Planning', style: GoogleFonts.inter(fontSize: 10, fontWeight: FontWeight.w600, color: c.text3)),
              const SizedBox(height: 4),
              Wrap(spacing: 6, runSpacing: 6, children: planningPresets.map((p) =>
                GestureDetector(
                  onTap: () => setBS(() => planningC.text = p['value']!),
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(
                      color: planningC.text == p['value'] ? c.primarySoft : c.elevated,
                      borderRadius: BorderRadius.circular(6),
                      border: Border.all(color: planningC.text == p['value'] ? c.primary.withOpacity(0.3) : c.border),
                    ),
                    child: Text(p['label']!, style: GoogleFonts.inter(fontSize: 9, fontWeight: FontWeight.w600,
                        color: planningC.text == p['value'] ? c.primary : c.text3)),
                  ),
                ),
              ).toList()),
              const SizedBox(height: 6),
              _formTextField(c, 'Horaires personnalisés', planningC),
              const SizedBox(height: 20),

              // Buttons
              Row(children: [
                Expanded(child: OutlinedButton(
                  onPressed: () => Navigator.pop(ctx),
                  style: OutlinedButton.styleFrom(
                    side: BorderSide(color: c.border),
                    padding: const EdgeInsets.symmetric(vertical: 14),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                  ),
                  child: Text('Annuler', style: GoogleFonts.inter(fontSize: 13, color: c.text2)),
                )),
                const SizedBox(width: 10),
                Expanded(child: Container(
                  decoration: BoxDecoration(gradient: c.gradientCta, borderRadius: BorderRadius.circular(12)),
                  child: ElevatedButton(
                    onPressed: saving ? null : () async {
                      // Validation
                      if (prenomC.text.trim().isEmpty || nomC.text.trim().isEmpty) {
                        ScaffoldMessenger.of(ctx).showSnackBar(SnackBar(content: const Text('Prénom et Nom obligatoires'), backgroundColor: c.danger));
                        return;
                      }
                      if (emailC.text.trim().isEmpty || !emailC.text.contains('@')) {
                        ScaffoldMessenger.of(ctx).showSnackBar(SnackBar(content: const Text('Email invalide'), backgroundColor: c.danger));
                        return;
                      }
                      if (isCreate && passwordC.text.length < 6) {
                        ScaffoldMessenger.of(ctx).showSnackBar(SnackBar(content: const Text('Mot de passe min 6 caractères'), backgroundColor: c.danger));
                        return;
                      }
                      if ((selectedRole == 'OPERATEUR' || selectedRole == 'SUPERVISEUR') && selectedAgence.isEmpty) {
                        ScaffoldMessenger.of(ctx).showSnackBar(SnackBar(content: const Text('Agence obligatoire pour ce rôle'), backgroundColor: c.danger));
                        return;
                      }

                      setBS(() => saving = true);
                      try {
                        final request = UserRequest(
                          nom: nomC.text.trim(),
                          prenom: prenomC.text.trim(),
                          email: emailC.text.trim(),
                          password: passwordC.text,
                          role: selectedRole,
                          telephone: phoneC.text.trim(),
                          agenceId: selectedAgence.isNotEmpty ? selectedAgence : null,
                          planning: planningC.text.isNotEmpty ? planningC.text : null,
                        );

                        if (isCreate) {
                          await _userService.createUser(request);
                        } else {
                          await _userService.updateUser(existing.id, request);
                        }
                        if (mounted) Navigator.pop(ctx);
                        _fetch();
                        if (mounted) ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(content: Text(isCreate ? '✅ Utilisateur créé' : '✅ Utilisateur modifié'), backgroundColor: c.success));
                      } catch (e) {
                        if (mounted) ScaffoldMessenger.of(ctx).showSnackBar(
                          SnackBar(content: Text('Erreur: $e'), backgroundColor: c.danger));
                      } finally {
                        if (mounted) setBS(() => saving = false);
                      }
                    },
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.transparent, shadowColor: Colors.transparent,
                      padding: const EdgeInsets.symmetric(vertical: 14),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                    child: Text(saving ? '...' : (isCreate ? 'Créer' : 'Sauvegarder'),
                        style: GoogleFonts.inter(fontSize: 13, fontWeight: FontWeight.w600, color: Colors.white)),
                  ),
                )),
              ]),
              const SizedBox(height: 10),
            ]),
          ),
        );
      }),
    );
  }

  Widget _formTextField(AppColorScheme c, String label, TextEditingController ctrl,
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

  Color _roleColor(String role) {
    switch (role) {
      case 'ADMIN': return const Color(0xFFF59E0B);
      case 'SUPERVISEUR': return const Color(0xFF8B5CF6);
      default: return context.colors.primary;
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Scaffold(
      appBar: AppBar(title: Text('Utilisateurs', style: GoogleFonts.inter(fontSize: 18, fontWeight: FontWeight.w800))),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _showCreateEditDialog(),
        backgroundColor: c.primary,
        icon: const Icon(Icons.person_add, size: 18, color: Colors.white),
        label: Text('Inviter', style: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.w600, color: Colors.white)),
      ),
      body: Column(
        children: [
          // ── Filter chips ──
          SizedBox(
            height: 44,
            child: ListView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
              children: ['ALL', 'OPERATEUR', 'SUPERVISEUR', 'ADMIN'].map((r) =>
                Padding(
                  padding: const EdgeInsets.only(right: 6),
                  child: GestureDetector(
                    onTap: () { setState(() => _roleFilter = r); _fetch(); },
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                      decoration: BoxDecoration(
                        color: _roleFilter == r ? c.primarySoft : c.elevated,
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(color: _roleFilter == r ? c.primary.withOpacity(0.3) : c.border),
                      ),
                      child: Text(
                        r == 'ALL' ? 'Tous' : r == 'OPERATEUR' ? 'Opérateurs' : r == 'SUPERVISEUR' ? 'Superviseurs' : 'Admins',
                        style: GoogleFonts.inter(fontSize: 11, fontWeight: FontWeight.w600, color: _roleFilter == r ? c.primary : c.text3),
                      ),
                    ),
                  ),
                ),
              ).toList(),
            ),
          ),

          // ── Count ──
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Row(children: [
              Text('${_users.length} compte${_users.length > 1 ? 's' : ''}', style: GoogleFonts.inter(fontSize: 10, color: c.text3)),
              const Spacer(),
              IconButton(onPressed: _fetch, icon: _loading ? SizedBox(width: 14, height: 14, child: CircularProgressIndicator(strokeWidth: 2, color: c.primary)) : Icon(Icons.refresh, size: 16, color: c.text3)),
            ]),
          ),

          // ── List ──
          Expanded(
            child: _loading
              ? Center(child: CircularProgressIndicator(color: c.primary))
              : _users.isEmpty
                ? Center(child: Column(mainAxisSize: MainAxisSize.min, children: [
                    Container(padding: const EdgeInsets.all(16), decoration: BoxDecoration(color: c.primarySoft, shape: BoxShape.circle, boxShadow: [BoxShadow(color: c.primary.withOpacity(0.3), blurRadius: 24, spreadRadius: 2)]), child: Icon(Icons.person_outline, size: 40, color: c.primary)),
                    Text('Aucun utilisateur', style: GoogleFonts.inter(fontWeight: FontWeight.w700, color: c.text)),
                  ]))
                : RefreshIndicator(
                    onRefresh: _fetch,
                    child: ListView.builder(
                      padding: const EdgeInsets.symmetric(horizontal: 16),
                      itemCount: _users.length,
                      itemBuilder: (_, i) => _buildUserCard(_users[i], i),
                    ),
                  ),
          ),

          // ── Detail panel (if selected) ──
          if (_selected != null) _buildDetailPanel(_selected!),
        ],
      ),
    );
  }

  Widget _buildUserCard(User u, int index) {
    final c = context.colors;
    final isSelected = _selected?.id == u.id;
    return GestureDetector(
      onTap: () => setState(() => _selected = isSelected ? null : u),
      child: Container(
        margin: const EdgeInsets.only(bottom: 6),
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: isSelected ? c.primarySoft : c.surface,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: isSelected ? c.primary.withOpacity(0.3) : c.border),
          boxShadow: c.isDark ? [] : [BoxShadow(color: c.shadow, blurRadius: 6, offset: const Offset(0, 2))],
        ),
        child: Row(children: [
          Container(
            width: 40, height: 40,
            decoration: BoxDecoration(color: c.primary.withOpacity(0.1), borderRadius: BorderRadius.circular(12)),
            child: Center(child: Text(u.initials, style: GoogleFonts.inter(fontSize: 13, fontWeight: FontWeight.w700, color: c.primary))),
          ),
          const SizedBox(width: 12),
          Expanded(child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(u.fullName, style: GoogleFonts.inter(fontSize: 13, fontWeight: FontWeight.w600, color: c.text)),
              Text(u.email, style: GoogleFonts.inter(fontSize: 10, color: c.text3)),
            ],
          )),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
            decoration: BoxDecoration(
              color: _roleColor(u.role).withOpacity(0.1),
              borderRadius: BorderRadius.circular(6),
              border: Border.all(color: _roleColor(u.role).withOpacity(0.2)),
            ),
            child: Text(u.role, style: GoogleFonts.inter(fontSize: 9, fontWeight: FontWeight.w600, color: _roleColor(u.role))),
          ),
          const SizedBox(width: 6),
          Container(width: 8, height: 8, decoration: BoxDecoration(
            color: u.actif ? c.success : c.text3,
            shape: BoxShape.circle,
          )),
        ]),
      ),
    ).animate().fadeIn(delay: Duration(milliseconds: 50 * index));
  }

  Widget _buildDetailPanel(User u) {
    final c = context.colors;
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: c.surface,
        border: Border(top: BorderSide(color: c.border)),
      ),
      child: SafeArea(
        top: false,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Row(children: [
              Container(
                width: 48, height: 48,
                decoration: BoxDecoration(gradient: c.gradientCta, borderRadius: BorderRadius.circular(14)),
                child: Center(child: Text(u.initials, style: GoogleFonts.inter(fontSize: 18, fontWeight: FontWeight.w700, color: Colors.white))),
              ),
              const SizedBox(width: 12),
              Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(u.fullName, style: GoogleFonts.inter(fontSize: 14, fontWeight: FontWeight.w700, color: c.text)),
                Text(u.email, style: GoogleFonts.inter(fontSize: 11, color: c.text3)),
              ])),
              // Edit button
              IconButton(
                onPressed: () => _showCreateEditDialog(existing: u),
                icon: Icon(Icons.edit_outlined, size: 18, color: c.primary),
              ),
              IconButton(
                onPressed: () => _deleteUser(u),
                icon: Icon(Icons.delete_outline, size: 18, color: c.danger),
              ),
            ]),
            const SizedBox(height: 10),
            Row(children: [
              _detailItem(c, 'Téléphone', u.telephone.isNotEmpty ? u.telephone : '—'),
              _detailItem(c, 'Agence', _agenceName(u.agenceId)),
            ]),
            const SizedBox(height: 6),
            Row(children: [
              _detailItem(c, 'Statut', u.actif ? 'Actif ✅' : 'Inactif'),
              _detailItem(c, 'Créé le', u.createdAt != null ? u.createdAt!.substring(0, 10) : '—'),
            ]),
            if (u.planning != null && u.planning!.isNotEmpty) ...[
              const SizedBox(height: 6),
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(color: c.elevated, borderRadius: BorderRadius.circular(8)),
                child: Text('📅 ${u.planning}', style: GoogleFonts.inter(fontSize: 10, color: c.text2)),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _detailItem(AppColorScheme c, String label, String value) {
    return Expanded(child: Row(children: [
      Text('$label: ', style: GoogleFonts.inter(fontSize: 10, color: c.text3)),
      Flexible(child: Text(value, style: GoogleFonts.inter(fontSize: 10, fontWeight: FontWeight.w600, color: c.text2), overflow: TextOverflow.ellipsis)),
    ]));
  }
}
