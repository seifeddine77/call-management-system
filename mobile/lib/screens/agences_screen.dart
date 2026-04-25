import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:flutter_animate/flutter_animate.dart';
import '../config/colors.dart';
import '../models/agence.dart';
import '../models/user.dart';
import '../services/agence_service.dart';
import '../services/user_service.dart';

class AgencesScreen extends StatefulWidget {
  const AgencesScreen({super.key});

  @override
  State<AgencesScreen> createState() => _AgencesScreenState();
}

class _AgencesScreenState extends State<AgencesScreen> {
  final _agenceService = AgenceService();
  final _userService = UserService();
  List<Agence> _agences = [];
  List<User> _superviseurs = [];
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _fetch();
    _fetchSuperviseurs();
  }

  Future<void> _fetch() async {
    setState(() => _loading = true);
    try {
      final page = await _agenceService.getAgences();
      setState(() {
        _agences = page.content;
        _loading = false;
      });
    } catch (e) {
      debugPrint('[AgencesScreen] Fetch error: $e');
      setState(() => _loading = false);
    }
  }

  Future<void> _fetchSuperviseurs() async {
    try {
      final users = await _userService.getUsers(role: 'SUPERVISEUR');
      setState(() {
        _superviseurs = users;
      });
    } catch (_) {}
  }

  Future<void> _deleteAgence(Agence agence) async {
    final c = context.colors;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Supprimer cette agence ?'),
        content: Text('${agence.nom}\n\nCette action désactivera l\'agence.',
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
      await _agenceService.deleteAgence(agence.id);
      setState(() => _agences.removeWhere((a) => a.id == agence.id));
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: const Text('✅ Agence désactivée'), backgroundColor: c.success));
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: const Text('Erreur suppression'), backgroundColor: c.danger));
    }
  }

  void _showCreateEditDialog({Agence? existing}) {
    final c = context.colors;
    final isCreate = existing == null;

    final nomC = TextEditingController(text: existing?.nom ?? '');
    final adresseC = TextEditingController(text: existing?.adresse ?? '');
    final telC = TextEditingController(text: existing?.telephone ?? '');
    final emailC = TextEditingController(text: existing?.email ?? '');
    final stockC = TextEditingController(text: existing?.stockDescription ?? '');
    String selectedResp = existing?.responsableId ?? '';
    bool saving = false;

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
              Text(isCreate ? '🏢 Nouvelle agence' : '✏️ Modifier agence',
                  style: GoogleFonts.inter(fontSize: 16, fontWeight: FontWeight.w800, color: c.text)),
              const SizedBox(height: 16),

              _formTextField(c, 'Nom *', nomC),
              const SizedBox(height: 10),
              _formTextField(c, 'Adresse', adresseC),
              const SizedBox(height: 10),
              Row(children: [
                Expanded(child: _formTextField(c, 'Téléphone', telC, keyboardType: TextInputType.phone)),
                const SizedBox(width: 10),
                Expanded(child: _formTextField(c, 'Email', emailC, keyboardType: TextInputType.emailAddress)),
              ]),
              const SizedBox(height: 10),

              // Responsable dropdown (superviseurs only — CDC 1.B)
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text('Responsable (Superviseur)', style: GoogleFonts.inter(fontSize: 10, fontWeight: FontWeight.w600, color: c.text3)),
                const SizedBox(height: 4),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10),
                  decoration: BoxDecoration(color: c.elevated, borderRadius: BorderRadius.circular(10), border: Border.all(color: c.border)),
                  child: DropdownButtonHideUnderline(child: DropdownButton<String>(
                    value: selectedResp.isEmpty ? null : selectedResp,
                    hint: Text('-- Aucun responsable --', style: GoogleFonts.inter(fontSize: 12, color: c.text3)),
                    isExpanded: true,
                    dropdownColor: c.elevated,
                    style: GoogleFonts.inter(fontSize: 12, color: c.text),
                    items: [
                      const DropdownMenuItem(value: '', child: Text('-- Aucun responsable --')),
                      ..._superviseurs.map((u) => DropdownMenuItem(value: u.id, child: Text('${u.prenom} ${u.nom}', overflow: TextOverflow.ellipsis))),
                    ],
                    onChanged: (v) => setBS(() => selectedResp = v ?? ''),
                  )),
                ),
              ]),
              const SizedBox(height: 10),

              _formTextField(c, 'Description du stock', stockC),
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
                      if (nomC.text.trim().isEmpty) {
                        ScaffoldMessenger.of(ctx).showSnackBar(SnackBar(content: const Text('Le nom est obligatoire'), backgroundColor: c.danger));
                        return;
                      }
                      if (emailC.text.isNotEmpty && !emailC.text.contains('@')) {
                        ScaffoldMessenger.of(ctx).showSnackBar(SnackBar(content: const Text('Email invalide'), backgroundColor: c.danger));
                        return;
                      }

                      setBS(() => saving = true);
                      try {
                        dynamic createdOrUpdated;
                        if (isCreate) {
                          final created = await _agenceService.createAgence(AgenceRequest(
                            nom: nomC.text.trim(),
                            adresse: adresseC.text.trim(),
                            telephone: telC.text.trim(),
                            email: emailC.text.trim(),
                            stockDescription: stockC.text.isNotEmpty ? stockC.text.trim() : null,
                          ));
                          createdOrUpdated = created;
                        } else {
                          final updated = await _agenceService.updateAgence(existing.id, AgenceRequest(
                            nom: nomC.text.trim(),
                            adresse: adresseC.text.trim(),
                            telephone: telC.text.trim(),
                            email: emailC.text.trim(),
                            stockDescription: stockC.text.isNotEmpty ? stockC.text.trim() : null,
                          ));
                          createdOrUpdated = updated;
                        }

                        final agenceId = createdOrUpdated is Agence ? createdOrUpdated.id : existing?.id;
                        if (selectedResp.isNotEmpty && selectedResp != existing?.responsableId && agenceId != null) {
                          try {
                            await _agenceService.assignResponsable(agenceId, selectedResp);
                          } catch (_) {}
                        }

                        if (mounted) Navigator.pop(ctx);
                        _fetch();
                        if (mounted) ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(content: Text(isCreate ? '✅ Agence créée' : '✅ Agence modifiée'), backgroundColor: c.success));
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
      {TextInputType? keyboardType}) {
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text(label, style: GoogleFonts.inter(fontSize: 10, fontWeight: FontWeight.w600, color: c.text3)),
      const SizedBox(height: 4),
      TextField(
        controller: ctrl,
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

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Scaffold(
      appBar: AppBar(
        title: Text('Agences', style: GoogleFonts.inter(fontSize: 18, fontWeight: FontWeight.w800)),
        actions: [
          IconButton(onPressed: _fetch, icon: Icon(Icons.refresh, size: 18, color: c.text3)),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _showCreateEditDialog(),
        backgroundColor: c.primary,
        icon: const Icon(Icons.add_business, size: 18, color: Colors.white),
        label: Text('Nouvelle agence', style: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.w600, color: Colors.white)),
      ),
      body: _loading
        ? Center(child: CircularProgressIndicator(color: c.primary))
        : _agences.isEmpty
          ? Center(child: Column(mainAxisSize: MainAxisSize.min, children: [
              Container(padding: const EdgeInsets.all(16), decoration: BoxDecoration(color: c.primarySoft, shape: BoxShape.circle, boxShadow: [BoxShadow(color: c.primary.withOpacity(0.3), blurRadius: 24, spreadRadius: 2)]), child: Icon(Icons.business_outlined, size: 40, color: c.primary)),
              Text('Aucune agence', style: GoogleFonts.inter(fontWeight: FontWeight.w700, color: c.text)),
            ]))
          : RefreshIndicator(
              onRefresh: _fetch,
              child: ListView.builder(
                padding: const EdgeInsets.all(16),
                itemCount: _agences.length + 1,
                itemBuilder: (_, i) {
                  if (i == 0) {
                    return Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: Text('${_agences.length} agence${_agences.length > 1 ? 's' : ''}',
                          style: GoogleFonts.inter(fontSize: 10, color: c.text3)),
                    );
                  }
                  return _buildAgenceCard(_agences[i - 1], i - 1);
                },
              ),
            ),
    );
  }

  Widget _buildAgenceCard(Agence a, int index) {
    final c = context.colors;
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      decoration: BoxDecoration(
        color: c.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: c.border),
        boxShadow: c.isDark ? [] : [BoxShadow(color: c.shadow, blurRadius: 8, offset: const Offset(0, 2))],
      ),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(children: [
              Container(
                width: 44, height: 44,
                decoration: BoxDecoration(color: c.primary.withOpacity(0.1), borderRadius: BorderRadius.circular(12)),
                child: const Center(child: Text('🏢', style: TextStyle(fontSize: 20))),
              ),
              const SizedBox(width: 12),
              Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(a.nom, style: GoogleFonts.inter(fontSize: 14, fontWeight: FontWeight.w700, color: c.text)),
                if (a.adresse.isNotEmpty) Text(a.adresse, style: GoogleFonts.inter(fontSize: 11, color: c.text3)),
              ])),
              // Edit button
              IconButton(
                onPressed: () => _showCreateEditDialog(existing: a),
                icon: Icon(Icons.edit_outlined, size: 16, color: c.primary),
                constraints: const BoxConstraints(minWidth: 32, minHeight: 32),
                padding: EdgeInsets.zero,
              ),
              IconButton(
                onPressed: () => _deleteAgence(a),
                icon: Icon(Icons.delete_outline, size: 16, color: c.danger),
                constraints: const BoxConstraints(minWidth: 32, minHeight: 32),
                padding: EdgeInsets.zero,
              ),
            ]),
            const SizedBox(height: 10),

            // Info row
            Row(children: [
              _infoTag(c, '📞', a.telephone.isNotEmpty ? a.telephone : '—'),
              const SizedBox(width: 8),
              _infoTag(c, '📧', a.email.isNotEmpty ? a.email : '—'),
            ]),

            if (a.responsableName != null) ...[
              const SizedBox(height: 6),
              Row(children: [
                Icon(Icons.person_outline, size: 12, color: c.text3),
                const SizedBox(width: 4),
                Text('Responsable : ${a.responsableName}', style: GoogleFonts.inter(fontSize: 11, color: c.text2)),
              ]),
            ],

            if (a.stockDescription != null && a.stockDescription!.isNotEmpty) ...[
              const SizedBox(height: 4),
              Row(children: [
                Icon(Icons.inventory_2_outlined, size: 12, color: c.text3),
                const SizedBox(width: 4),
                Expanded(child: Text(a.stockDescription!, style: GoogleFonts.inter(fontSize: 10, color: c.text3), overflow: TextOverflow.ellipsis)),
              ]),
            ],
          ],
        ),
      ),
    ).animate().fadeIn(delay: Duration(milliseconds: 60 * index)).slideY(begin: 0.03);
  }

  Widget _infoTag(AppColorScheme c, String icon, String text) {
    return Expanded(child: Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(color: c.elevated, borderRadius: BorderRadius.circular(6)),
      child: Row(children: [
        Text(icon, style: const TextStyle(fontSize: 10)),
        const SizedBox(width: 4),
        Flexible(child: Text(text, style: GoogleFonts.inter(fontSize: 10, color: c.text2), overflow: TextOverflow.ellipsis)),
      ]),
    ));
  }
}
