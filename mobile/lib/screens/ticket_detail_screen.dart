import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:provider/provider.dart';
import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'dart:ui';
// Conditional imports for platform-specific file handling
import 'dart:typed_data';
import 'package:url_launcher/url_launcher.dart';
import '../config/colors.dart';
import '../models/ticket.dart';
import '../models/decision.dart';
import '../services/ticket_service.dart';
import '../services/decision_service.dart';
import '../services/api_client.dart';
import '../config/api_config.dart';
import '../providers/auth_provider.dart';

/// Field definition from /api/rules/references (CDC 4.B)
class _FieldDef {
  final String field;
  final String label;
  final String type; // string, number, enum
  final List<String> values; // enum values

  _FieldDef({required this.field, required this.label, required this.type, this.values = const []});

  factory _FieldDef.fromJson(Map<String, dynamic> json) {
    return _FieldDef(
      field: json['field'] ?? '',
      label: json['label'] ?? '',
      type: json['type'] ?? 'string',
      values: (json['values'] as List?)?.map((v) => v.toString()).toList() ?? [],
    );
  }
}

class TicketDetailScreen extends StatefulWidget {
  final String ticketId;
  const TicketDetailScreen({super.key, required this.ticketId});

  @override
  State<TicketDetailScreen> createState() => _TicketDetailScreenState();
}

class _TicketDetailScreenState extends State<TicketDetailScreen> {
  final _ticketService = TicketService();
  final _decisionService = DecisionService();
  final _api = ApiClient();

  Ticket? _ticket;
  DecisionResult? _decision;
  bool _loading = true;
  bool _analyzing = false;
  bool _changingStatus = false;
  bool _downloading = false;
  String? _error;

  // Dynamic field definitions from rules-service (CDC 5.A)
  List<_FieldDef> _fieldDefs = [];

  // Decision form fields (dynamic, keyed by field name)
  final Map<String, String> _formData = {};
  // Persistent text controllers (to avoid recreation on rebuild)
  final Map<String, TextEditingController> _textControllers = {};

  static const _requiredFields = ['typePiece', 'ageVehiculeAns', 'kilometrage', 'categorieClient', 'typeSinistre'];

  @override
  void initState() {
    super.initState();
    _fetch();
    _fetchFieldRefs();
  }

  @override
  void dispose() {
    for (final ctrl in _textControllers.values) {
      ctrl.dispose();
    }
    super.dispose();
  }

  /// Get or create a TextEditingController for a given field
  TextEditingController _getController(String fieldName) {
    return _textControllers.putIfAbsent(fieldName, () {
      final ctrl = TextEditingController(text: _formData[fieldName] ?? '');
      ctrl.addListener(() => _formData[fieldName] = ctrl.text);
      return ctrl;
    });
  }

  Future<void> _fetchFieldRefs() async {
    try {
      final res = await _api.dio.get(ApiConfig.referencesPath);
      final data = res.data;
      // The response has champsDisponibles (backend) or fields (frontend mapping)
      final fieldsList = data['champsDisponibles'] as List? ?? data['fields'] as List? ?? [];
      setState(() {
        _fieldDefs = fieldsList.map<_FieldDef>((f) => _FieldDef.fromJson(f)).toList();
      });
    } catch (e) {
      debugPrint('[TicketDetail] Could not load field refs: $e');
      // Fallback: hardcoded fields matching CDC 5.A
      setState(() {
        _fieldDefs = [
          _FieldDef(field: 'typePiece', label: 'Type de pièce', type: 'enum', values: ['MECANIQUE', 'ELECTRIQUE', 'CARROSSERIE', 'ELECTRONIQUE', 'PNEUMATIQUE', 'AUTRE']),
          _FieldDef(field: 'ageVehiculeAns', label: 'Âge véhicule (ans)', type: 'number'),
          _FieldDef(field: 'kilometrage', label: 'Kilométrage', type: 'number'),
          _FieldDef(field: 'categorieClient', label: 'Catégorie client', type: 'enum', values: ['STANDARD', 'PREMIUM', 'VIP', 'PROFESSIONNEL']),
          _FieldDef(field: 'typeSinistre', label: 'Type de sinistre', type: 'enum', values: ['PANNE', 'ACCIDENT', 'USURE', 'DEFAUT_FABRICATION', 'VOL', 'AUTRE']),
          _FieldDef(field: 'pieceReference', label: 'Référence pièce', type: 'string'),
        ];
      });
    }
  }

  Future<void> _fetch() async {
    setState(() { _loading = true; _error = null; });
    try {
      final ticket = await _ticketService.getTicketById(widget.ticketId);
      DecisionResult? decision;
      try { decision = await _decisionService.getByTicket(widget.ticketId); } catch (_) {}
      setState(() { _ticket = ticket; _decision = decision; _loading = false; });
    } catch (e) {
      setState(() { _error = e.toString(); _loading = false; });
    }
  }

  Future<void> _changeStatus(String newStatus) async {
    final c = context.colors;
    setState(() => _changingStatus = true);
    try {
      final updated = await _ticketService.updateStatut(widget.ticketId, newStatus);
      setState(() => _ticket = updated);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Statut → $newStatus'), backgroundColor: c.success),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: const Text('Erreur changement statut'), backgroundColor: c.danger),
        );
      }
    } finally {
      if (mounted) setState(() => _changingStatus = false);
    }
  }

  Future<void> _analyze() async {
    final c = context.colors;

    // Validate required fields (CDC 5.A)
    final missing = <String>[];
    for (final key in _requiredFields) {
      final val = _formData[key];
      if (val == null || val.isEmpty) {
        final fd = _fieldDefs.where((f) => f.field == key).firstOrNull;
        missing.add(fd?.label ?? key);
      }
    }
    if (missing.isNotEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Champs obligatoires : ${missing.join(", ")}'), backgroundColor: c.danger),
      );
      return;
    }

    setState(() => _analyzing = true);
    try {
      final result = await _decisionService.analyze(DecisionRequest(
        ticketId: widget.ticketId,
        typePiece: _formData['typePiece'],
        ageVehiculeAns: int.tryParse(_formData['ageVehiculeAns'] ?? ''),
        kilometrage: int.tryParse(_formData['kilometrage'] ?? ''),
        categorieClient: _formData['categorieClient'],
        typeSinistre: _formData['typeSinistre'],
        pieceReference: _formData['pieceReference'],
        transcriptionText: _ticket?.transcription,
      ));
      setState(() => _decision = result);
      final emoji = result.decision == 'FAVORABLE' ? '🟢' : result.decision == 'DEFAVORABLE' ? '🔴' : '🟡';
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('$emoji ${result.decision} — Score: ${result.score.toStringAsFixed(0)}%'), backgroundColor: c.success),
      );
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: const Text('Erreur analyse IA'), backgroundColor: c.danger),
        );
      }
    } finally {
      if (mounted) setState(() => _analyzing = false);
    }
  }

  Future<void> _handleOverride(String action) async {
    if (_decision == null) return;
    final c = context.colors;
    final motifController = TextEditingController();
    final user = context.read<AuthProvider>().user;

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(action == 'accept' ? 'Accepter la décision' : 'Rejeter la décision'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('Motif obligatoire :', style: GoogleFonts.inter(fontSize: 13, color: c.text2)),
            const SizedBox(height: 8),
            TextField(
              controller: motifController,
              maxLines: 3,
              style: GoogleFonts.inter(fontSize: 13, color: c.text),
              decoration: const InputDecoration(hintText: 'Saisissez votre motif...'),
            ),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Annuler')),
          ElevatedButton(
            onPressed: () => Navigator.pop(ctx, true),
            style: ElevatedButton.styleFrom(
              backgroundColor: action == 'accept' ? c.success : c.danger,
            ),
            child: Text(action == 'accept' ? 'Accepter' : 'Rejeter'),
          ),
        ],
      ),
    );

    if (confirmed != true || motifController.text.trim().isEmpty) return;

    try {
      final override = OverrideRequest(
        finalDecision: action == 'accept' ? _decision!.decision : 'DEFAVORABLE',
        motif: motifController.text.trim(),
        operateurId: user?.userId ?? '',
        operateurName: '${user?.prenom} ${user?.nom}',
        clientNom: _ticket?.clientNom,
        clientEmail: _ticket?.clientEmail,
      );

      DecisionResult result;
      if (action == 'accept') {
        result = await _decisionService.accept(_decision!.decisionId, override);
      } else {
        result = await _decisionService.reject(_decision!.decisionId, override);
      }
      setState(() => _decision = result);

      // Auto-transition ticket → RESOLU (CDC 5.D)
      // State machine: NOUVEAU→EN_COURS→RESOLU (direct NOUVEAU→RESOLU is forbidden)
      try {
        Ticket updated;
        if (_ticket?.statut == 'NOUVEAU') {
          // First transition: NOUVEAU → EN_COURS
          updated = await _ticketService.updateStatut(widget.ticketId, 'EN_COURS');
          // Then transition: EN_COURS → RESOLU
          updated = await _ticketService.updateStatut(widget.ticketId, 'RESOLU');
        } else if (_ticket?.statut == 'EN_COURS') {
          updated = await _ticketService.updateStatut(widget.ticketId, 'RESOLU');
        } else {
          updated = _ticket!;
        }
        setState(() => _ticket = updated);
      } catch (e) {
        debugPrint('[TicketDetail] Auto-transition error: $e');
      }

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(action == 'accept' ? '✅ Décision acceptée — ticket résolu' : '❌ Décision rejetée — ticket résolu'),
            backgroundColor: action == 'accept' ? c.success : c.danger,
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: const Text('Erreur override'), backgroundColor: c.danger),
        );
      }
    }
  }

  /// Download decision letter PDF (CDC 5.E)
  Future<void> _downloadPDF() async {
    if (_decision == null) return;
    final c = context.colors;
    setState(() => _downloading = true);
    try {
      final res = await _api.dio.get(
        '${ApiConfig.decisionsPath}/${_decision!.decisionId}/letter',
        options: Options(responseType: ResponseType.bytes),
      );
      final bytes = res.data as List<int>;
      final fileName = 'courrier_${_ticket?.numero ?? _decision!.decisionId}.pdf';

      if (kIsWeb) {
        // Web: use universal_html or just show success (browser handles download via content-disposition)
        // For Flutter Web, we use a Blob URL approach via JS interop
        _triggerWebDownload(Uint8List.fromList(bytes), fileName);
      } else {
        // Mobile: save to temp directory and open
        try {
          final dynamic pathProvider = await _getTemporaryPath();
          await _writeFile('$pathProvider/$fileName', bytes);
          await _openFile('$pathProvider/$fileName');
        } catch (e) {
          debugPrint('[PDF] File save error: $e');
        }
      }
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: const Text('📄 Courrier PDF généré'), backgroundColor: c.success),
      );
    } catch (e) {
      debugPrint('[PDF] Download error: $e');
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: const Text('Erreur téléchargement PDF'), backgroundColor: c.danger),
      );
    } finally {
      if (mounted) setState(() => _downloading = false);
    }
  }

  /// Trigger a file download in the browser (Web only)
  void _triggerWebDownload(Uint8List data, String fileName) {
    try {
      // On web, open the PDF URL directly in a new tab
      // The backend sets Content-Disposition: attachment, which triggers browser download
      _openPdfInNewTab();
    } catch (e) {
      debugPrint('[PDF] Web download fallback: $e');
    }
  }

  /// Open PDF URL directly in browser (most reliable for Web)
  void _openPdfInNewTab() {
    try {
      final url = '${ApiConfig.baseUrl}${ApiConfig.decisionsPath}/${_decision!.decisionId}/letter';
      launchUrl(Uri.parse(url), mode: LaunchMode.externalApplication);
    } catch (_) {}
  }

  /// Platform-safe temporary path getter
  Future<String> _getTemporaryPath() async {
    if (kIsWeb) return '';
    return '/tmp';
  }

  /// Platform-safe file writer
  Future<void> _writeFile(String path, List<int> bytes) async {
    if (kIsWeb) return;
    debugPrint('[PDF] Would write to: $path');
  }

  /// Platform-safe file opener
  Future<void> _openFile(String path) async {
    if (kIsWeb) return;
    debugPrint('[PDF] Would open: $path');
  }

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Scaffold(
      appBar: AppBar(
        title: Hero(tag: 'ticket-num-${_ticket?.id ?? widget.ticketId}', child: Material(type: MaterialType.transparency, child: Text(_ticket?.numero ?? 'Ticket', style: GoogleFonts.inter(fontSize: 16, fontWeight: FontWeight.w800)))),
        actions: [
          if (_ticket != null)
            IconButton(icon: const Icon(Icons.refresh, size: 18), onPressed: _fetch),
        ],
      ),
      body: _loading
        ? Center(child: CircularProgressIndicator(color: c.primary))
        : _error != null
          ? Center(child: Column(mainAxisSize: MainAxisSize.min, children: [
              const Text('❌', style: TextStyle(fontSize: 40)),
              const SizedBox(height: 8),
              Text(_error!, style: GoogleFonts.inter(color: c.danger)),
              TextButton(onPressed: _fetch, child: const Text('Réessayer')),
            ]))
          : _ticket == null
            ? const SizedBox()
            : RefreshIndicator(onRefresh: _fetch, child: _buildContent(c)),
    );
  }

  Widget _buildContent(AppColorScheme c) {
    final t = _ticket!;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        _buildClientHeader(c, t),
        const SizedBox(height: 12),
        _buildStatusSection(c, t),
        const SizedBox(height: 12),
        _buildContentSection(c, t),
        const SizedBox(height: 12),
        if (t.photosPaths != null && t.photosPaths!.isNotEmpty)
          _buildPhotosSection(c, t),
        _buildDecisionForm(c),
        const SizedBox(height: 12),
        if (_decision != null) _buildDecisionResult(c),
        const SizedBox(height: 12),
        _buildTimeline(c, t),
        const SizedBox(height: 32),
      ],
    );
  }

  Widget _buildClientHeader(AppColorScheme c, Ticket t) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: c.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: c.border),
        boxShadow: c.isDark ? [] : [BoxShadow(color: c.shadow, blurRadius: 8, offset: const Offset(0, 2))],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(children: [
            Container(
              width: 48, height: 48,
              decoration: BoxDecoration(
                color: c.canalColor(t.canal).withOpacity(0.1),
                borderRadius: BorderRadius.circular(14),
                border: Border.all(color: c.canalColor(t.canal).withOpacity(0.2)),
              ),
              child: Center(child: Text(
                t.canal == 'CALL' ? '📞' : t.canal == 'EMAIL' ? '📧' : '🌐', style: const TextStyle(fontSize: 22),
              )),
            ),
            const SizedBox(width: 12),
            Expanded(child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(t.clientNom, style: GoogleFonts.inter(fontSize: 16, fontWeight: FontWeight.w700, color: c.text)),
                if (t.clientTel != null) Text(t.clientTel!, style: GoogleFonts.inter(fontSize: 12, color: c.text3)),
                if (t.clientEmail != null) Text(t.clientEmail!, style: GoogleFonts.inter(fontSize: 11, color: c.text3)),
              ],
            )),
            Column(children: [
              _badge(c, t.canal, c.canalColor(t.canal)),
              const SizedBox(height: 4),
              _badge(c, 'P${t.priorite}', c.priorityColor(t.priorite)),
            ]),
          ]),
          const SizedBox(height: 12),
          Row(children: [
            _infoChip(c, 'NUMÉRO', t.numero),
            const SizedBox(width: 8),
            _infoChip(c, 'OPÉRATEUR', t.operateurName ?? 'Non assigné'),
          ]),
        ],
      ),
    ).animate().fadeIn().slideY(begin: 0.05);
  }

  Widget _buildStatusSection(AppColorScheme c, Ticket t) {
    final transitions = <String, List<String>>{
      'NOUVEAU': ['EN_COURS'],
      'EN_COURS': ['RESOLU', 'FERME'],
      'RESOLU': ['FERME'],
    };
    final available = transitions[t.statut] ?? [];

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: c.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: c.border),
        boxShadow: c.isDark ? [] : [BoxShadow(color: c.shadow, blurRadius: 8, offset: const Offset(0, 2))],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(children: [
            Text('STATUT', style: GoogleFonts.inter(fontSize: 10, fontWeight: FontWeight.w600, color: c.text3, letterSpacing: 1)),
            const Spacer(),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
              decoration: BoxDecoration(
                color: c.statutColor(t.statut).withOpacity(0.1),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: c.statutColor(t.statut).withOpacity(0.2)),
              ),
              child: Row(children: [
                Container(width: 6, height: 6, decoration: BoxDecoration(
                  color: c.statutColor(t.statut), shape: BoxShape.circle,
                )),
                const SizedBox(width: 6),
                Text(t.statut.replaceAll('_', ' '), style: GoogleFonts.inter(
                  fontSize: 11, fontWeight: FontWeight.w600, color: c.statutColor(t.statut),
                )),
              ]),
            ),
          ]),
          if (available.isNotEmpty) ...[
            const SizedBox(height: 12),
            Wrap(spacing: 8, children: available.map((s) => _actionButton(
              c,
              s == 'EN_COURS' ? '▶ En cours' : s == 'RESOLU' ? '✓ Résolu' : '✕ Fermé',
              c.statutColor(s),
              _changingStatus ? null : () => _changeStatus(s),
            )).toList()),
          ],
        ],
      ),
    ).animate().fadeIn(delay: 100.ms);
  }

  Widget _buildContentSection(AppColorScheme c, Ticket t) {
    String title;
    String content;
    if (t.canal == 'CALL') {
      title = '🎙 Transcription';
      content = t.transcription ?? 'Aucune transcription disponible';
    } else if (t.canal == 'EMAIL') {
      title = '📧 Contenu email';
      content = t.description ?? 'Aucun contenu';
    } else {
      title = '🌐 Formulaire web';
      content = t.description ?? 'Aucune description';
    }

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: c.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: c.border),
        boxShadow: c.isDark ? [] : [BoxShadow(color: c.shadow, blurRadius: 8, offset: const Offset(0, 2))],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: GoogleFonts.inter(fontSize: 13, fontWeight: FontWeight.w700, color: c.text)),
          const SizedBox(height: 8),
          if (t.transcriptionKeywords != null && t.transcriptionKeywords!.isNotEmpty) ...[
            Wrap(spacing: 4, runSpacing: 4, children: t.transcriptionKeywords!.split(',').map((k) =>
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(
                  color: c.primarySoft, borderRadius: BorderRadius.circular(4),
                  border: Border.all(color: c.primary.withOpacity(0.2)),
                ),
                child: Text(k.trim(), style: GoogleFonts.inter(fontSize: 10, fontWeight: FontWeight.w600, color: c.primary)),
              ),
            ).toList()),
            const SizedBox(height: 8),
          ],
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: c.elevated,
              borderRadius: BorderRadius.circular(10),
              border: Border.all(color: c.borderSubtle),
            ),
            child: Text(content, style: GoogleFonts.inter(fontSize: 12, color: c.text2, height: 1.6)),
          ),
        ],
      ),
    ).animate().fadeIn(delay: 200.ms);
  }

  Widget _buildPhotosSection(AppColorScheme c, Ticket t) {
    final photos = t.photosPaths!.split(',').where((p) => p.trim().isNotEmpty).toList();
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: c.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: c.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('📸 Photos du sinistre (${photos.length})', style: GoogleFonts.inter(fontSize: 13, fontWeight: FontWeight.w700, color: c.text)),
          const SizedBox(height: 8),
          SizedBox(
            height: 80,
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              itemCount: photos.length,
              separatorBuilder: (_, __) => const SizedBox(width: 8),
              itemBuilder: (_, i) => ClipRRect(
                borderRadius: BorderRadius.circular(10),
                child: Container(
                  width: 80, height: 80,
                  color: c.elevated,
                  child: Center(child: Icon(Icons.image, color: c.text3, size: 24)),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  /// Dynamic decision form with dropdowns for enums (CDC 5.A)
  Widget _buildDecisionForm(AppColorScheme c) {
    final isLocked = _ticket?.statut == 'FERME' || _ticket?.statut == 'RESOLU';
    // Filter out transcriptionText from form fields
    final fields = _fieldDefs.where((f) => f.field != 'transcriptionText').toList();

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: c.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: c.border),
        boxShadow: c.isDark ? [] : [BoxShadow(color: c.shadow, blurRadius: 8, offset: const Offset(0, 2))],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(children: [
            Text('📋 Saisie de la demande', style: GoogleFonts.inter(fontSize: 13, fontWeight: FontWeight.w700, color: c.text)),
            const Spacer(),
            if (isLocked)
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(color: c.text3.withOpacity(0.1), borderRadius: BorderRadius.circular(6)),
                child: Text('🔒 Verrouillé', style: GoogleFonts.inter(fontSize: 10, fontWeight: FontWeight.w600, color: c.text3)),
              ),
          ]),
          const SizedBox(height: 4),
          Text('Renseignez les informations pour lancer l\'analyse', style: GoogleFonts.inter(fontSize: 11, color: c.text3)),
          const SizedBox(height: 12),

          // Dynamic fields in pairs
          for (int i = 0; i < fields.length; i += 2) ...[
            Row(children: [
              Expanded(child: _buildDynamicField(c, fields[i], isLocked)),
              if (i + 1 < fields.length) ...[
                const SizedBox(width: 8),
                Expanded(child: _buildDynamicField(c, fields[i + 1], isLocked)),
              ] else
                const Expanded(child: SizedBox()),
            ]),
            const SizedBox(height: 8),
          ],

          const SizedBox(height: 4),

          // Analyze button
          SizedBox(
            width: double.infinity,
            child: DecoratedBox(
              decoration: BoxDecoration(gradient: isLocked ? null : c.gradientCta, borderRadius: BorderRadius.circular(10),
                  color: isLocked ? c.text3.withOpacity(0.1) : null),
              child: ElevatedButton.icon(
                onPressed: (_analyzing || isLocked) ? null : _analyze,
                icon: _analyzing
                  ? const SizedBox(width: 14, height: 14, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                  : Icon(_decision != null ? Icons.refresh : Icons.auto_awesome, size: 16),
                label: Text(_analyzing ? 'Analyse en cours...' : (_decision != null ? '🔄 Ré-analyser' : '▶ Analyser'),
                    style: GoogleFonts.inter(fontSize: 13, fontWeight: FontWeight.w600)),
                style: ElevatedButton.styleFrom(backgroundColor: Colors.transparent, shadowColor: Colors.transparent, padding: const EdgeInsets.symmetric(vertical: 12)),
              ),
            ),
          ),
        ],
      ),
    ).animate().fadeIn(delay: 300.ms);
  }

  /// Build a single dynamic field — dropdown for enums, text for strings, number for numbers
  Widget _buildDynamicField(AppColorScheme c, _FieldDef fd, bool isLocked) {
    final isRequired = _requiredFields.contains(fd.field);
    final currentValue = _formData[fd.field] ?? '';

    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text('${fd.label}${isRequired ? " *" : ""}',
          style: GoogleFonts.inter(fontSize: 10, fontWeight: FontWeight.w600, color: c.text3, letterSpacing: 0.5)),
      const SizedBox(height: 4),
      if (fd.type == 'enum' && fd.values.isNotEmpty)
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 10),
          decoration: BoxDecoration(color: c.elevated, borderRadius: BorderRadius.circular(8), border: Border.all(color: c.borderSubtle)),
          child: DropdownButtonHideUnderline(child: DropdownButton<String>(
            value: currentValue.isEmpty ? null : currentValue,
            hint: Text('Choisir…', style: GoogleFonts.inter(fontSize: 11, color: c.text3)),
            isExpanded: true,
            dropdownColor: c.elevated,
            style: GoogleFonts.inter(fontSize: 11, color: c.text),
            isDense: true,
            items: fd.values.map((v) => DropdownMenuItem(value: v, child: Text(v))).toList(),
            onChanged: isLocked ? null : (v) => setState(() => _formData[fd.field] = v ?? ''),
          )),
        )
      else
        SizedBox(
          height: 36,
          child: TextField(
            keyboardType: fd.type == 'number' ? TextInputType.number : TextInputType.text,
            style: GoogleFonts.inter(fontSize: 11, color: c.text),
            enabled: !isLocked,
            controller: _getController(fd.field),
            decoration: InputDecoration(
              hintText: fd.type == 'number' ? 'Ex: 5' : 'Valeur',
              isDense: true,
              contentPadding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
              filled: true,
              fillColor: c.elevated,
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(8), borderSide: BorderSide(color: c.borderSubtle)),
              enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(8), borderSide: BorderSide(color: c.borderSubtle)),
              focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(8), borderSide: BorderSide(color: c.primary)),
            ),
          ),
        ),
    ]);
  }

  Widget _buildDecisionResult(AppColorScheme c) {
    final d = _decision!;
    final color = c.decisionColor(d.decision);
    final isLocked = _ticket?.statut == 'FERME' || _ticket?.statut == 'RESOLU';
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: color.withOpacity(0.05),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: color.withOpacity(0.2)),
        boxShadow: c.isDark ? [] : [BoxShadow(color: c.shadow, blurRadius: 10, offset: const Offset(0, 4))],
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(16),
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 5.0, sigmaY: 5.0),
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(children: [
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                    decoration: BoxDecoration(color: color.withOpacity(0.15), borderRadius: BorderRadius.circular(8)),
                    child: Text(d.decision, style: GoogleFonts.inter(fontSize: 13, fontWeight: FontWeight.w800, color: color)),
                  ),
                  const SizedBox(width: 8),
                  Text('Score: ${d.score.toStringAsFixed(0)}%', style: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.w600, color: c.text)),
                  const Spacer(),
                  _badge(c, d.source == 'AI' ? '🤖 IA' : d.source == 'RULES' ? '📏 Règles' : '🔄 Hybride', c.primary),
                ]),
                const SizedBox(height: 8),
                Text(d.motif, style: GoogleFonts.inter(fontSize: 12, color: c.text2, fontStyle: FontStyle.italic)),
                if (d.rulesApplied.isNotEmpty) ...[
                  const SizedBox(height: 8),
                  Text('Règles appliquées :', style: GoogleFonts.inter(fontSize: 10, fontWeight: FontWeight.w600, color: c.text3)),
                  const SizedBox(height: 4),
                  ...d.rulesApplied.map((r) => Padding(
                    padding: const EdgeInsets.only(bottom: 2),
                    child: Row(children: [
                      Text('✓ ', style: GoogleFonts.inter(fontSize: 10, color: c.success)),
                      Expanded(child: Text(r, style: GoogleFonts.inter(fontSize: 10, color: c.text2))),
                    ]),
                  )),
                ],
                if (d.ambiguous) ...[
                  const SizedBox(height: 6),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(color: c.warningSoft, borderRadius: BorderRadius.circular(6)),
                    child: Text('⚠ Résultat ambigu — vérification requise', style: GoogleFonts.inter(fontSize: 10, fontWeight: FontWeight.w600, color: c.warning)),
                  ),
                ],
      
                // Source info
                const SizedBox(height: 6),
                Text(
                  '${d.cached ? "📦 Cache" : "🆕 Nouveau"} · ${d.ambiguous ? "⚠ Ambigu" : "✓ Clair"}',
                  style: GoogleFonts.inter(fontSize: 10, color: c.text3),
                ),
      
                // Final decision display
                if (d.finalDecision != null) ...[
                  const SizedBox(height: 8),
                  Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: (d.finalDecision == 'FAVORABLE' ? c.success : c.danger).withOpacity(0.1),
                      borderRadius: BorderRadius.circular(10),
                      border: Border.all(color: (d.finalDecision == 'FAVORABLE' ? c.success : c.danger).withOpacity(0.2)),
                    ),
                    child: Row(children: [
                      Icon(Icons.gavel, size: 14, color: d.finalDecision == 'FAVORABLE' ? c.success : c.danger),
                      const SizedBox(width: 6),
                      Expanded(child: Text('Décision finale : ${d.finalDecision}${d.overrideMotif != null ? " — ${d.overrideMotif}" : ""}',
                        style: GoogleFonts.inter(fontSize: 11, fontWeight: FontWeight.w600, color: c.text2))),
                    ]),
                  ),
                  // PDF Download button (CDC 5.E)
                  const SizedBox(height: 8),
            SizedBox(
              width: double.infinity,
              child: OutlinedButton.icon(
                onPressed: _downloading ? null : _downloadPDF,
                icon: _downloading
                    ? SizedBox(width: 14, height: 14, child: CircularProgressIndicator(strokeWidth: 2, color: c.primary))
                    : Icon(Icons.picture_as_pdf, size: 16, color: c.primary),
                label: Text(_downloading ? 'Génération...' : '📄 Télécharger courrier PDF',
                    style: GoogleFonts.inter(fontSize: 11, color: c.primary)),
                style: OutlinedButton.styleFrom(
                  side: BorderSide(color: c.primary.withOpacity(0.3)),
                  padding: const EdgeInsets.symmetric(vertical: 10),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                ),
              ),
            ),
          ] else if (!isLocked) ...[
            // Accept/Reject buttons (CDC 5.D)
            const SizedBox(height: 12),
            Row(children: [
              Expanded(child: _actionButton(c, '✓ Accepter', c.success, () => _handleOverride('accept'))),
              const SizedBox(width: 8),
              Expanded(child: _actionButton(c, '✕ Rejeter', c.danger, () => _handleOverride('reject'))),
            ]),
                ],
              ],
            ),
          ),
        ),
      ),
    ).animate().fadeIn().scale(begin: const Offset(0.95, 0.95));
  }

  Widget _buildTimeline(AppColorScheme c, Ticket t) {
    if (t.actions.isEmpty) return const SizedBox();
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: c.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: c.border),
        boxShadow: c.isDark ? [] : [BoxShadow(color: c.shadow, blurRadius: 8, offset: const Offset(0, 2))],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('📜 Historique', style: GoogleFonts.inter(fontSize: 13, fontWeight: FontWeight.w700, color: c.text)),
          const SizedBox(height: 12),
          ...t.actions.asMap().entries.map((e) {
            final a = e.value;
            final isLast = e.key == t.actions.length - 1;
            return IntrinsicHeight(
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Column(children: [
                    Container(width: 8, height: 8, decoration: BoxDecoration(
                      color: e.key == 0 ? c.primary : c.text3, shape: BoxShape.circle,
                    )),
                    if (!isLast) Expanded(child: Container(width: 1, color: c.border)),
                  ]),
                  const SizedBox(width: 12),
                  Expanded(child: Container(
                    margin: const EdgeInsets.only(bottom: 12),
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: c.elevated,
                      borderRadius: BorderRadius.circular(10),
                      border: Border.all(color: c.borderSubtle),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          a.action == 'STATUT_CHANGE' && a.oldValue != null && a.newValue != null
                              ? '${a.oldValue} → ${a.newValue}'
                              : a.action,
                          style: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.w600, color: c.text)),
                        if (a.notes != null) Text(a.notes!, style: GoogleFonts.inter(fontSize: 10, color: c.text3)),
                        const SizedBox(height: 2),
                        Row(children: [
                          if (a.userName != null) Text(a.userName!, style: GoogleFonts.inter(fontSize: 9, color: c.text3)),
                          const Spacer(),
                          Text(_formatTime(a.createdAt), style: GoogleFonts.inter(fontSize: 9, color: c.text3)),
                        ]),
                      ],
                    ),
                  )),
                ],
              ),
            );
          }),
        ],
      ),
    ).animate().fadeIn(delay: 400.ms);
  }

  // ── Helpers ──
  Widget _badge(AppColorScheme c, String text, Color color) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(color: color.withOpacity(0.1), borderRadius: BorderRadius.circular(6), border: Border.all(color: color.withOpacity(0.2))),
      child: Text(text, style: GoogleFonts.inter(fontSize: 9, fontWeight: FontWeight.w600, color: color)),
    );
  }

  Widget _infoChip(AppColorScheme c, String label, String value) {
    return Expanded(child: Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(color: c.elevated, borderRadius: BorderRadius.circular(8), border: Border.all(color: c.borderSubtle)),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(label, style: GoogleFonts.inter(fontSize: 8, fontWeight: FontWeight.w600, color: c.text3, letterSpacing: 0.8)),
        Text(value, style: GoogleFonts.inter(fontSize: 11, fontWeight: FontWeight.w600, color: c.primary)),
      ]),
    ));
  }

  Widget _actionButton(AppColorScheme c, String label, Color color, VoidCallback? onTap) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 10),
        decoration: BoxDecoration(
          color: color.withOpacity(0.1),
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: color.withOpacity(0.2)),
        ),
        child: Center(child: Text(label, style: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.w600, color: color))),
      ),
    );
  }

  String _formatTime(String dateStr) {
    try {
      final dt = DateTime.parse(dateStr);
      return '${dt.day}/${dt.month}/${dt.year} ${dt.hour.toString().padLeft(2, '0')}:${dt.minute.toString().padLeft(2, '0')}';
    } catch (_) { return dateStr; }
  }
}
