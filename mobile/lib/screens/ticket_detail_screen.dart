import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:provider/provider.dart';
import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'dart:ui';
// Conditional imports for platform-specific file handling
import 'dart:typed_data';
import '../config/colors.dart';
import '../models/ticket.dart';
import '../models/decision.dart';
import '../services/ticket_service.dart';
import '../services/decision_service.dart';
import '../services/api_client.dart';
import '../config/api_config.dart';
import '../providers/auth_provider.dart';
import 'package:flutter/services.dart';
import 'audit_screen.dart';
import '../utils/file_saver.dart';
import 'dart:io';
import 'package:path_provider/path_provider.dart';
import 'package:open_filex/open_filex.dart';
import 'package:image_picker/image_picker.dart';
import 'package:cached_network_image/cached_network_image.dart';


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

  // Audio Player Simulated States
  bool _isPlaying = false;
  double _audioProgress = 0.0; // 0.0 to 1.0
  int _audioDurationSeconds = 48; // default mock duration
  Timer? _audioTimer;
  int _audioCurrentTime = 0;

  // Secure access token for network images
  String? _accessToken;

  // Photo proofs uploading and selection states
  final ImagePicker _picker = ImagePicker();
  final List<XFile> _localPhotos = [];
  bool _uploadingPhotos = false;

  // Live streaming transcription simulated states
  bool _isRecording = false;
  int _recordingElapsed = 0;
  Timer? _recordingTimer;
  String _liveDictationText = '';
  int _phraseIndex = 0;
  final List<String> _simulatedPhrases = [
    "Bonjour, je vous contacte suite à un sinistre survenu ce matin.",
    "Ma voiture a glissé sur une plaque de verglas et a percuté le pare-choc arrière d'un autre véhicule.",
    "Les dégâts sur mon capot avant sont assez importants et le radiateur semble fuir.",
    "Je souhaiterais déclarer l'accident et demander une assistance de remorquage vers le garage agréé le plus proche."
  ];

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
    _loadToken();
  }

  @override
  void dispose() {
    _audioTimer?.cancel();
    _recordingTimer?.cancel();
    for (final ctrl in _textControllers.values) {
      ctrl.dispose();
    }
    super.dispose();
  }

  Future<void> _loadToken() async {
    final token = await _api.getAccessToken();
    if (mounted) {
      setState(() {
        _accessToken = token;
      });
    }
  }

  void _toggleRecording() {
    final c = context.colors;
    if (_isRecording) {
      _recordingTimer?.cancel();
      setState(() {
        _isRecording = false;
        if (_liveDictationText.isNotEmpty && _ticket != null) {
          final currentTrans = _ticket!.transcription ?? '';
          final newTrans = currentTrans.isEmpty
              ? _liveDictationText
              : '$currentTrans\n[00:45] $_liveDictationText';
          _ticket = _ticket!.copyWith(transcription: newTrans);
        }
      });
      HapticFeedback.mediumImpact();
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: const Text('🛑 Transcription arrêtée et sauvegardée'), backgroundColor: c.success),
      );
    } else {
      setState(() {
        _isRecording = true;
        _recordingElapsed = 0;
        _liveDictationText = '';
        _phraseIndex = 0;
      });
      _recordingTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
        setState(() {
          _recordingElapsed++;
          if (_recordingElapsed % 3 == 0 && _phraseIndex < _simulatedPhrases.length) {
            final nextPhrase = _simulatedPhrases[_phraseIndex];
            _liveDictationText = _liveDictationText.isEmpty
                ? nextPhrase
                : '$_liveDictationText $nextPhrase';
            _phraseIndex++;
          }
        });
      });
      HapticFeedback.lightImpact();
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: const Text('🎙 Transcription live Whisper démarrée'), backgroundColor: c.primary),
      );
    }
  }

  List<Map<String, String>> _parseTranscription(String text) {
    final List<Map<String, String>> segments = [];
    final regExp = RegExp(r'\[(\d{1,2}:\d{2}(?::\d{2})?)\]\s*');
    final matches = regExp.allMatches(text).toList();

    int lastIdx = 0;
    if (matches.isNotEmpty) {
      for (int i = 0; i < matches.length; i++) {
        final match = matches[i];
        if (lastIdx < match.start && lastIdx == 0) {
          final pre = text.substring(lastIdx, match.start).trim();
          if (pre.isNotEmpty) {
            segments.add({'time': '00:00', 'text': pre});
          }
        }
        final timeStr = match.group(1) ?? '00:00';
        final contentStart = match.end;
        final contentEnd = (i + 1 < matches.length) ? matches[i + 1].start : text.length;
        final segmentText = text.substring(contentStart, contentEnd).trim();
        segments.add({'time': timeStr, 'text': segmentText});
        lastIdx = contentEnd;
      }
    }

    if (segments.isEmpty && text.trim().isNotEmpty) {
      final sentences = text.split(RegExp(r'(?<=[.!?])\s+')).where((s) => s.trim().isNotEmpty).toList();
      const avgDuration = 5;
      for (int i = 0; i < sentences.length; i++) {
        final mins = (i * avgDuration) ~/ 60;
        final secs = (i * avgDuration) % 60;
        final timeStr = '$mins:${secs.toString().padLeft(2, '0')}';
        segments.add({'time': timeStr, 'text': sentences[i].trim()});
      }
    }

    return segments;
  }

  Widget _buildHighlightText(AppColorScheme c, String text, List<String> keywords) {
    if (keywords.isEmpty) {
      return Text(text, style: GoogleFonts.inter(fontSize: 11, color: c.text2, height: 1.5));
    }
    
    final sortedKeywords = List<String>.from(keywords)
      ..sort((a, b) => b.length.compareTo(a.length));
      
    final pattern = RegExp(
      '(${sortedKeywords.map((k) => RegExp.escape(k)).join('|')})',
      caseSensitive: false,
    );
    
    final matches = pattern.allMatches(text).toList();
    if (matches.isEmpty) {
      return Text(text, style: GoogleFonts.inter(fontSize: 11, color: c.text2, height: 1.5));
    }
    
    final List<InlineSpan> spans = [];
    int lastIdx = 0;
    
    for (final match in matches) {
      if (match.start > lastIdx) {
        spans.add(TextSpan(
          text: text.substring(lastIdx, match.start),
          style: GoogleFonts.inter(color: c.text2),
        ));
      }
      
      spans.add(WidgetSpan(
        alignment: PlaceholderAlignment.middle,
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
          decoration: BoxDecoration(
            color: c.primary.withOpacity(0.15),
            borderRadius: BorderRadius.circular(4),
            border: Border.all(color: c.primary.withOpacity(0.25)),
          ),
          child: Text(
            text.substring(match.start, match.end),
            style: GoogleFonts.inter(
              color: c.primary,
              fontWeight: FontWeight.bold,
              fontSize: 11,
            ),
          ),
        ),
      ));
      
      lastIdx = match.end;
    }
    
    if (lastIdx < text.length) {
      spans.add(TextSpan(
        text: text.substring(lastIdx),
        style: GoogleFonts.inter(color: c.text2),
      ));
    }
    
    return RichText(
      text: TextSpan(children: spans),
    );
  }

  Future<void> _pickPhotos() async {
    try {
      final List<XFile> picked = await _picker.pickMultiImage();
      if (picked.isNotEmpty) {
        setState(() {
          _localPhotos.addAll(picked);
        });
      }
    } catch (e) {
      debugPrint('[Photos] Error picking images: $e');
    }
  }

  Future<void> _uploadPhotos() async {
    if (_localPhotos.isEmpty) return;
    setState(() { _uploadingPhotos = true; });
    try {
      final updated = await _ticketService.uploadPhotos(widget.ticketId, _localPhotos);
      setState(() {
        _ticket = updated;
        _localPhotos.clear();
      });
      HapticFeedback.mediumImpact();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: const Text('📤 Preuves visuelles enregistrées avec succès'), backgroundColor: context.colors.success),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: const Text('Erreur lors de l\'envoi des photos'), backgroundColor: context.colors.danger),
        );
      }
    } finally {
      if (mounted) setState(() { _uploadingPhotos = false; });
    }
  }

  void _openLightbox(String path, int index, List<String> allPhotos) {
    showDialog(
      context: context,
      barrierColor: Colors.black.withOpacity(0.95),
      builder: (context) => Scaffold(
        backgroundColor: Colors.transparent,
        appBar: AppBar(
          backgroundColor: Colors.transparent,
          elevation: 0,
          leading: IconButton(
            icon: const Icon(Icons.close, color: Colors.white),
            onPressed: () => Navigator.pop(context),
          ),
          title: Text(
            'Photo ${index + 1} sur ${allPhotos.length}',
            style: GoogleFonts.inter(color: Colors.white, fontSize: 13, fontWeight: FontWeight.bold),
          ),
        ),
        body: Center(
          child: InteractiveViewer(
            clipBehavior: Clip.none,
            minScale: 0.5,
            maxScale: 4.0,
            child: CachedNetworkImage(
              imageUrl: '${ApiConfig.baseUrl}/api/tickets$path',
              httpHeaders: _accessToken != null ? {'Authorization': 'Bearer $_accessToken'} : null,
              placeholder: (context, url) => const CircularProgressIndicator(color: Colors.white),
              errorWidget: (context, url, error) => const Icon(Icons.error_outline, color: Colors.white, size: 48),
              fit: BoxFit.contain,
            ),
          ),
        ),
      ),
    );
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
        HapticFeedback.mediumImpact();
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
      if (mounted) {
        HapticFeedback.mediumImpact();
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('$emoji ${result.decision} — Score: ${result.score.toStringAsFixed(0)}%'), backgroundColor: c.success),
        );
      }
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
        finalDecision: action == 'accept' ? 'FAVORABLE' : 'DEFAVORABLE',
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
        HapticFeedback.mediumImpact();
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
        saveFileWeb(Uint8List.fromList(bytes), fileName);
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

  /// Platform-safe temporary path getter
  Future<String> _getTemporaryPath() async {
    if (kIsWeb) return '';
    final dir = await getTemporaryDirectory();
    return dir.path;
  }

  /// Platform-safe file writer
  Future<void> _writeFile(String path, List<int> bytes) async {
    if (kIsWeb) return;
    final file = File(path);
    await file.writeAsBytes(bytes);
  }

  /// Platform-safe file opener
  Future<void> _openFile(String path) async {
    if (kIsWeb) return;
    await OpenFilex.open(path);
  }


  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final auth = context.watch<AuthProvider>();
    return Scaffold(
      appBar: AppBar(
        title: Hero(tag: 'ticket-num-${_ticket?.id ?? widget.ticketId}', child: Material(type: MaterialType.transparency, child: Text(_ticket?.numero ?? 'Ticket', style: GoogleFonts.inter(fontSize: 16, fontWeight: FontWeight.w800)))),
        actions: [
          if (_ticket != null && auth.isSupervisor)
            IconButton(
              icon: const Icon(Icons.shield, size: 18),
              tooltip: 'Audit',
              onPressed: () => Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => AuditScreen(ticketId: _ticket!.id)),
              ),
            ),
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
    final isSupervisor = context.read<AuthProvider>().isSupervisor;
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
        if (isSupervisor) ...[
          _buildAuditSection(c, t),
          const SizedBox(height: 12),
        ],
        _buildTimeline(c, t),
        const SizedBox(height: 32),
      ],
    );
  }

  Widget _buildAuditSection(AppColorScheme c, Ticket t) {
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
          Row(
            children: [
              Icon(Icons.gavel_outlined, size: 18, color: c.primary),
              const SizedBox(width: 8),
              Text('CONFORMITÉ & DÉCISIONS', style: GoogleFonts.inter(
                fontSize: 10, fontWeight: FontWeight.w700, color: c.text3, letterSpacing: 0.5,
              )),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            'Journal de conformité RGPD, explicabilité des règles de scoring IA et historique complet des décisions pour ce sinistre.',
            style: GoogleFonts.inter(fontSize: 11, color: c.text2),
          ),
          const SizedBox(height: 12),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton.icon(
              onPressed: () => Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => AuditScreen(ticketId: t.id)),
              ),
              icon: const Icon(Icons.shield_outlined, size: 16, color: Colors.white),
              label: Text('Consulter le rapport d\'audit complet', style: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.w600, color: Colors.white)),
              style: ElevatedButton.styleFrom(
                backgroundColor: c.primary,
                padding: const EdgeInsets.symmetric(vertical: 12),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildClientHeader(AppColorScheme c, Ticket t) {
    final gradientColors = t.canal == 'CALL' 
      ? [const Color(0xFF10B981), const Color(0xFF6366F1)]
      : t.canal == 'EMAIL'
        ? [const Color(0xFF60A5FA), const Color(0xFF6366F1)]
        : [const Color(0xFF34D399), const Color(0xFF14B8A6)];

    return ClipRRect(
      borderRadius: BorderRadius.circular(16),
      child: Container(
        decoration: BoxDecoration(
          color: c.surface,
          border: Border.all(color: c.border),
          boxShadow: c.isDark ? [] : [BoxShadow(color: c.shadow, blurRadius: 8, offset: const Offset(0, 2))],
        ),
        child: Stack(
          children: [
            Positioned(
              top: 0, left: 0, right: 0,
              child: Container(
                height: 3,
                decoration: BoxDecoration(
                  gradient: LinearGradient(colors: gradientColors),
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.only(top: 16, bottom: 16, left: 16, right: 16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(children: [
                    Container(
                      width: 48, height: 48,
                      decoration: BoxDecoration(
                        color: c.canalColor(t.canal).withOpacity(0.12),
                        borderRadius: BorderRadius.circular(14),
                        border: Border.all(color: c.canalColor(t.canal).withOpacity(0.2)),
                      ),
                      child: Center(
                        child: Icon(
                          t.canal == 'CALL'
                              ? Icons.phone_outlined
                              : t.canal == 'EMAIL'
                                  ? Icons.mail_outlined
                                  : Icons.language_outlined,
                          size: 24,
                          color: c.canalColor(t.canal),
                        ),
                      ),
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
                    const SizedBox(width: 6),
                    _infoChip(c, 'AGENCE', t.agenceName ?? 'Non spécifiée'),
                    const SizedBox(width: 6),
                    _infoChip(c, 'OPÉRATEUR', t.operateurName ?? 'Non assigné'),
                  ]),
                  const SizedBox(height: 12),
                  const Divider(height: 1),
                  const SizedBox(height: 8),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text('RGPD AUDIO', style: GoogleFonts.inter(fontSize: 8, fontWeight: FontWeight.w600, color: c.text3, letterSpacing: 0.8)),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                        decoration: BoxDecoration(
                          color: (t.consentementEnregistrement ? c.success : c.danger).withOpacity(0.1),
                          borderRadius: BorderRadius.circular(6),
                          border: Border.all(color: (t.consentementEnregistrement ? c.success : c.danger).withOpacity(0.2)),
                        ),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(
                              t.consentementEnregistrement ? Icons.check_circle_outline : Icons.cancel_outlined,
                              size: 10,
                              color: t.consentementEnregistrement ? c.success : c.danger,
                            ),
                            const SizedBox(width: 4),
                            Text(
                              t.consentementEnregistrement ? '✓ Accordé' : '✕ Refusé',
                              style: GoogleFonts.inter(
                                fontSize: 9, 
                                fontWeight: FontWeight.w700, 
                                color: t.consentementEnregistrement ? c.success : c.danger
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
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
    if (t.canal == 'CALL') {
      return _buildCallSection(c, t);
    } else if (t.canal == 'EMAIL') {
      return _buildEmailSection(c, t);
    } else {
      return _buildWebSection(c, t);
    }
  }

  void _togglePlay() {
    if (_isPlaying) {
      _audioTimer?.cancel();
      setState(() => _isPlaying = false);
    } else {
      setState(() => _isPlaying = true);
      _audioTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
        setState(() {
          if (_audioCurrentTime < _audioDurationSeconds) {
            _audioCurrentTime++;
            _audioProgress = _audioCurrentTime / _audioDurationSeconds;
          } else {
            _isPlaying = false;
            _audioCurrentTime = 0;
            _audioProgress = 0.0;
            _audioTimer?.cancel();
          }
        });
      });
    }
  }

  Widget _buildCallSection(AppColorScheme c, Ticket t) {
    final displayFilename = t.audioPath != null ? t.audioPath!.split('/').last : 'call_record.wav';
    final List<int> waveformHeights = [30, 45, 60, 20, 15, 40, 55, 75, 50, 30, 25, 45, 60, 80, 40, 20, 35, 50, 70, 60, 40, 30, 20, 45, 55, 65, 30, 25, 40, 50, 60, 40, 35, 20, 15, 30];
    final keywords = t.transcriptionKeywords?.split(',').map((k) => k.trim()).where((k) => k.isNotEmpty).toList() ?? [];
    final segments = _parseTranscription(t.transcription ?? '');

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Card 1: Audio Player Twilio
        Container(
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
              Row(
                children: [
                  Icon(Icons.phone_in_talk_outlined, size: 16, color: c.canalCall),
                  const SizedBox(width: 8),
                  Text('☎ Enregistrement Audio Twilio', style: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.w800, color: c.text)),
                  const Spacer(),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                    decoration: BoxDecoration(color: c.canalCall.withOpacity(0.1), borderRadius: BorderRadius.circular(6), border: Border.all(color: c.canalCall.withOpacity(0.2))),
                    child: Text('SIP Stream', style: GoogleFonts.inter(fontSize: 8, fontWeight: FontWeight.w700, color: c.canalCall)),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(color: c.elevated, borderRadius: BorderRadius.circular(12), border: Border.all(color: c.borderSubtle)),
                child: Column(
                  children: [
                    Row(
                      children: [
                        Container(
                          width: 8, height: 8,
                          decoration: const BoxDecoration(color: Colors.green, shape: BoxShape.circle),
                        ),
                        const SizedBox(width: 6),
                        Text('Fichier d\'archive disponible', style: GoogleFonts.inter(fontSize: 9, fontWeight: FontWeight.w700, color: Colors.green)),
                        const Spacer(),
                        Expanded(
                          child: Text(
                            displayFilename, 
                            style: GoogleFonts.inter(fontSize: 8, color: c.text3), 
                            overflow: TextOverflow.ellipsis,
                            textAlign: TextAlign.right,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    
                    Row(
                      children: [
                        GestureDetector(
                          onTap: _togglePlay,
                          child: Container(
                            width: 36, height: 36,
                            decoration: BoxDecoration(color: c.primary, shape: BoxShape.circle),
                            child: Icon(_isPlaying ? Icons.pause : Icons.play_arrow, color: Colors.white, size: 20),
                          ),
                        ),
                        const SizedBox(width: 12),
                        
                        Expanded(
                          child: GestureDetector(
                            onHorizontalDragUpdate: (details) {
                              final double width = 160.0;
                              setState(() {
                                _audioProgress = (details.localPosition.dx / width).clamp(0.0, 1.0);
                                _audioCurrentTime = (_audioProgress * _audioDurationSeconds).round();
                              });
                            },
                            child: SizedBox(
                              height: 32,
                              child: Row(
                                mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                                crossAxisAlignment: CrossAxisAlignment.center,
                                children: List.generate(waveformHeights.length, (idx) {
                                  final double threshold = idx / waveformHeights.length;
                                  final isActive = _audioProgress >= threshold;
                                  return Container(
                                    width: 2,
                                    height: waveformHeights[idx] * 0.35,
                                    decoration: BoxDecoration(
                                      color: isActive ? c.primary : c.text3.withOpacity(0.2),
                                      borderRadius: BorderRadius.circular(1),
                                    ),
                                  );
                                }),
                              ),
                            ),
                          ),
                        ),
                        const SizedBox(width: 12),
                        
                        Text(
                          '${_formatAudioTime(_audioCurrentTime)} / ${_formatAudioTime(_audioDurationSeconds)}',
                          style: GoogleFonts.robotoMono(fontSize: 9, fontWeight: FontWeight.w600, color: c.text2),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 12),
              
              Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(color: c.elevated.withOpacity(0.4), borderRadius: BorderRadius.circular(12), border: Border.all(color: c.borderSubtle)),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    _pipelineStep(c, 'Audio Capturé', true),
                    _pipelineStep(c, 'Whisper STT', t.transcription != null),
                    _pipelineStep(c, 'Analyse IA', _decision != null),
                  ],
                ),
              ),
            ],
          ),
        ).animate().fadeIn(delay: 200.ms),
        const SizedBox(height: 12),

        // Card 2: Live Streaming Dictation (Microphone Simulation)
        Container(
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
              Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(6),
                    decoration: BoxDecoration(
                      color: c.primary.withOpacity(0.1),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Icon(Icons.keyboard_voice, size: 16, color: c.primary),
                  ),
                  const SizedBox(width: 8),
                  Text('🎙 Transcription Temps Réel', style: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.w800, color: c.text)),
                  const Spacer(),
                  if (_isRecording)
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                      decoration: BoxDecoration(
                        color: c.elevated,
                        borderRadius: BorderRadius.circular(6),
                        border: Border.all(color: c.borderSubtle),
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Container(
                            width: 6, height: 6,
                            decoration: const BoxDecoration(color: Colors.redAccent, shape: BoxShape.circle),
                          ).animate(onPlay: (controller) => controller.repeat())
                           .fadeIn(duration: 500.ms).fadeOut(duration: 500.ms),
                          const SizedBox(width: 6),
                          Text(
                            '${_formatAudioTime(_recordingElapsed)}',
                            style: GoogleFonts.robotoMono(fontSize: 9, fontWeight: FontWeight.bold, color: c.text2),
                          ),
                        ],
                      ),
                    ),
                  const SizedBox(width: 8),
                  ElevatedButton(
                    onPressed: _toggleRecording,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: _isRecording ? Colors.redAccent.withOpacity(0.1) : c.primary,
                      foregroundColor: _isRecording ? Colors.redAccent : Colors.white,
                      side: _isRecording ? const BorderSide(color: Colors.redAccent) : null,
                      elevation: _isRecording ? 0 : 2,
                      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 0),
                      minimumSize: const Size(0, 28),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                    ),
                    child: Text(
                      _isRecording ? 'Arrêter' : '🎙 Démarrer le live',
                      style: GoogleFonts.inter(fontSize: 10, fontWeight: FontWeight.bold),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),

              if (_isRecording) ...[
                Container(
                  padding: const EdgeInsets.all(10),
                  margin: const EdgeInsets.only(bottom: 12),
                  decoration: BoxDecoration(
                    color: Colors.redAccent.withOpacity(0.05),
                    border: Border.all(color: Colors.redAccent.withOpacity(0.15)),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        'En cours d\'écoute — Parlez dans le micro',
                        style: GoogleFonts.inter(fontSize: 9, color: Colors.redAccent, fontWeight: FontWeight.bold),
                      ),
                      Row(
                        mainAxisSize: MainAxisSize.min,
                        children: List.generate(5, (idx) {
                          return Container(
                            margin: const EdgeInsets.symmetric(horizontal: 1.5),
                            width: 2,
                            height: 10.0,
                            decoration: BoxDecoration(
                              color: Colors.redAccent,
                              borderRadius: BorderRadius.circular(1),
                            ),
                          ).animate(onPlay: (controller) => controller.repeat(reverse: true))
                           .scaleY(end: 2.2, duration: Duration(milliseconds: 250 + idx * 75), curve: Curves.easeInOut);
                        }),
                      ),
                    ],
                  ),
                ),
              ],

              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: c.elevated,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: c.borderSubtle),
                ),
                child: _liveDictationText.isNotEmpty
                    ? RichText(
                        text: TextSpan(
                          children: [
                            TextSpan(
                              text: _liveDictationText,
                              style: GoogleFonts.robotoMono(fontSize: 11, color: c.text2, height: 1.6),
                            ),
                            if (_isRecording)
                              WidgetSpan(
                                alignment: PlaceholderAlignment.middle,
                                child: Container(
                                  width: 2, height: 12,
                                  color: c.primary,
                                ).animate(onPlay: (controller) => controller.repeat())
                                 .fadeIn(duration: 400.ms).fadeOut(duration: 400.ms),
                              ),
                          ],
                        ),
                      )
                    : Center(
                        child: Padding(
                          padding: const EdgeInsets.symmetric(vertical: 24),
                          child: Column(
                            children: [
                              Text('🎙️', style: GoogleFonts.inter(fontSize: 28)),
                              const SizedBox(height: 8),
                              Text(
                                _isRecording ? 'En attente de parole...' : 'Cliquez sur "Démarrer le live" pour initier la dictée vocale Whisper.',
                                style: GoogleFonts.inter(fontSize: 10, color: c.text3),
                                textAlign: TextAlign.center,
                              ),
                            ],
                          ),
                        ),
                      ),
              ),
            ],
          ),
        ).animate().fadeIn(delay: 250.ms),
        const SizedBox(height: 12),
        
        // Card 3: Segmented Transcription Viewer with Keywords highlighting
        Container(
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
              Row(
                children: [
                  Icon(Icons.description_outlined, size: 16, color: c.primary),
                  const SizedBox(width: 8),
                  Text('Transcription de la conversation', style: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.w800, color: c.text)),
                  const Spacer(),
                  if (t.transcription != null)
                    Text(
                      '${t.transcription!.length} caractères',
                      style: GoogleFonts.robotoMono(fontSize: 8, color: c.text3, fontWeight: FontWeight.bold),
                    ),
                ],
              ),
              const SizedBox(height: 12),
              
              if (segments.isNotEmpty) ...[
                Container(
                  constraints: const BoxConstraints(maxHeight: 280),
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: c.elevated,
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: c.borderSubtle),
                  ),
                  child: ListView.separated(
                    shrinkWrap: true,
                    itemCount: segments.length,
                    separatorBuilder: (_, __) => Divider(height: 16, color: c.borderSubtle.withOpacity(0.5)),
                    itemBuilder: (ctx, idx) {
                      final seg = segments[idx];
                      return Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                            margin: const EdgeInsets.only(top: 2),
                            decoration: BoxDecoration(
                              color: c.primary.withOpacity(0.08),
                              borderRadius: BorderRadius.circular(6),
                              border: Border.all(color: c.primary.withOpacity(0.15)),
                            ),
                            child: Text(
                              seg['time'] ?? '00:00',
                              style: GoogleFonts.robotoMono(fontSize: 8, color: c.primary, fontWeight: FontWeight.bold),
                            ),
                          ),
                          const SizedBox(width: 10),
                          Expanded(
                            child: _buildHighlightText(c, seg['text'] ?? '', keywords),
                          ),
                        ],
                      );
                    },
                  ),
                ),
                
                if (keywords.isNotEmpty) ...[
                  const SizedBox(height: 12),
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: c.elevated.withOpacity(0.4),
                      borderRadius: BorderRadius.circular(10),
                      border: Border.all(color: c.borderSubtle),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'MOTS-CLÉS SÉMANTIQUES IDENTIFIÉS :',
                          style: GoogleFonts.inter(fontSize: 8, fontWeight: FontWeight.w800, color: c.text3),
                        ),
                        const SizedBox(height: 6),
                        Wrap(
                          spacing: 6,
                          runSpacing: 4,
                          children: keywords.map((k) => Container(
                            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                            decoration: BoxDecoration(
                              color: c.primarySoft,
                              borderRadius: BorderRadius.circular(12),
                              border: Border.all(color: c.primary.withOpacity(0.15)),
                            ),
                            child: Text(
                              k,
                              style: GoogleFonts.inter(fontSize: 9, fontWeight: FontWeight.bold, color: c.primary),
                            ),
                          )).toList(),
                        ),
                      ],
                    ),
                  ),
                ],
                
                const SizedBox(height: 12),
                const Divider(height: 1),
                const SizedBox(height: 8),
                Text(
                  '📋 Remarques et Annotations Opérateur',
                  style: GoogleFonts.inter(fontSize: 9, fontWeight: FontWeight.w700, color: c.text3),
                ),
                const SizedBox(height: 6),
                SizedBox(
                  height: 60,
                  child: TextField(
                    maxLines: 2,
                    style: GoogleFonts.inter(fontSize: 11, color: c.text2),
                    decoration: InputDecoration(
                      hintText: 'Saisissez ici des notes complémentaires sur l\'appel...',
                      hintStyle: GoogleFonts.inter(fontSize: 10, color: c.text3),
                      contentPadding: const EdgeInsets.all(10),
                      filled: true,
                      fillColor: c.elevated,
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: BorderSide(color: c.borderSubtle)),
                      enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: BorderSide(color: c.borderSubtle)),
                    ),
                  ),
                ),
              ] else ...[
                Center(
                  child: Padding(
                    padding: const EdgeInsets.symmetric(vertical: 36),
                    child: Column(
                      children: [
                        Text('📄', style: GoogleFonts.inter(fontSize: 32)),
                        const SizedBox(height: 8),
                        Text(
                          'Aucune transcription disponible pour le moment.',
                          style: GoogleFonts.inter(fontSize: 11, color: c.text3),
                          textAlign: TextAlign.center,
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ],
          ),
        ).animate().fadeIn(delay: 350.ms),
      ],
    );
  }

  Widget _pipelineStep(AppColorScheme c, String label, bool isDone) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: 14, height: 14,
          decoration: BoxDecoration(
            color: isDone ? c.success.withOpacity(0.1) : c.warning.withOpacity(0.1),
            shape: BoxShape.circle,
            border: Border.all(color: isDone ? c.success.withOpacity(0.3) : c.warning.withOpacity(0.3)),
          ),
          child: Center(
            child: Icon(
              isDone ? Icons.check : Icons.hourglass_empty,
              size: 8,
              color: isDone ? c.success : c.warning,
            ),
          ),
        ),
        const SizedBox(width: 4),
        Text(label, style: GoogleFonts.inter(fontSize: 8, fontWeight: FontWeight.w700, color: isDone ? c.success : c.text3)),
      ],
    );
  }

  String _formatAudioTime(int timeInSeconds) {
    final mins = timeInSeconds ~/ 60;
    final secs = timeInSeconds % 60;
    return '$mins:${secs.toString().padLeft(2, '0')}';
  }

  Widget _buildEmailSection(AppColorScheme c, Ticket t) {
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
          Row(
            children: [
              Icon(Icons.mail_outline, size: 16, color: c.canalEmail),
              const SizedBox(width: 8),
              Text('📧 Contenu du message email', style: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.w800, color: c.text)),
              const Spacer(),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(color: c.canalEmail.withOpacity(0.1), borderRadius: BorderRadius.circular(6), border: Border.all(color: c.canalEmail.withOpacity(0.2))),
                child: Text('IMAP Parsing', style: GoogleFonts.inter(fontSize: 8, fontWeight: FontWeight.w700, color: c.canalEmail)),
              ),
            ],
          ),
          const SizedBox(height: 12),
          
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: c.elevated,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: c.borderSubtle),
            ),
            child: Text(
              t.description ?? 'Aucun contenu textuel dans le courriel.',
              style: GoogleFonts.lora(fontSize: 12, color: c.text2, height: 1.6),
            ),
          ),
          
          if (t.clientEmail != null) ...[
            const SizedBox(height: 12),
            const Divider(height: 1),
            const SizedBox(height: 8),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text('Expéditeur : ${t.clientEmail}', style: GoogleFonts.robotoMono(fontSize: 8, color: c.text3)),
                Text('Date : ${_formatTicketDate(t.createdAt)}', style: GoogleFonts.robotoMono(fontSize: 8, color: c.text3)),
              ],
            ),
          ],
        ],
      ),
    ).animate().fadeIn(delay: 200.ms);
  }

  String _formatTicketDate(String dateStr) {
    try {
      final dt = DateTime.parse(dateStr);
      return '${dt.day.toString().padLeft(2, '0')}/${dt.month.toString().padLeft(2, '0')}/${dt.year} ${dt.hour.toString().padLeft(2, '0')}:${dt.minute.toString().padLeft(2, '0')}';
    } catch (_) {
      return dateStr;
    }
  }

  Widget _buildWebSection(AppColorScheme c, Ticket t) {
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
          Row(
            children: [
              Icon(Icons.public, size: 16, color: c.canalWeb),
              const SizedBox(width: 8),
              Text('🌐 Déclaration Formulaire Web', style: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.w800, color: c.text)),
              const Spacer(),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(color: c.canalWeb.withOpacity(0.1), borderRadius: BorderRadius.circular(6), border: Border.all(color: c.canalWeb.withOpacity(0.2))),
                child: Text('REST API', style: GoogleFonts.inter(fontSize: 8, fontWeight: FontWeight.w700, color: c.canalWeb)),
              ),
            ],
          ),
          const SizedBox(height: 12),
          
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: c.elevated,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: c.borderSubtle),
            ),
            child: Text(
              t.description ?? 'Aucune observation saisie par l\'internaute.',
              style: GoogleFonts.inter(fontSize: 12, color: c.text2, height: 1.6),
            ),
          ),
          
          const SizedBox(height: 12),
          const Divider(height: 1),
          const SizedBox(height: 8),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text('Mail de contact : ${t.clientEmail ?? 'Non spécifié'}', style: GoogleFonts.robotoMono(fontSize: 8, color: c.text3)),
              Text('Tel de contact : ${t.clientTel ?? 'Non spécifié'}', style: GoogleFonts.robotoMono(fontSize: 8, color: c.text3)),
            ],
          ),
        ],
      ),
    ).animate().fadeIn(delay: 200.ms);
  }

  Widget _buildPhotosSection(AppColorScheme c, Ticket t) {
    List<String> photos = [];
    if (t.photosPaths != null && t.photosPaths!.isNotEmpty) {
      if (t.photosPaths!.trim().startsWith('[')) {
        try {
          final List<dynamic> parsed = jsonDecode(t.photosPaths!);
          photos = parsed.map((p) => p.toString()).toList();
        } catch (_) {
          photos = t.photosPaths!.split(',').map((p) => p.trim()).where((p) => p.isNotEmpty).toList();
        }
      } else {
        photos = t.photosPaths!.split(',').map((p) => p.trim()).where((p) => p.isNotEmpty).toList();
      }
    }
    
    final isLocked = t.statut == 'FERME' || t.statut == 'RESOLU';

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
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
          Row(
            children: [
              const Icon(Icons.camera_alt_outlined, size: 16, color: Colors.pinkAccent),
              const SizedBox(width: 8),
              Text('📸 Preuves Visuelles (${photos.length})', style: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.w800, color: c.text)),
            ],
          ),
          const SizedBox(height: 12),
          
          if (photos.isNotEmpty) ...[
            SizedBox(
              height: 80,
              child: ListView.separated(
                scrollDirection: Axis.horizontal,
                itemCount: photos.length,
                separatorBuilder: (_, __) => const SizedBox(width: 8),
                itemBuilder: (_, i) {
                  final path = photos[i];
                  return GestureDetector(
                    onTap: () => _openLightbox(path, i, photos),
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(10),
                      child: Container(
                        width: 80, height: 80,
                        color: c.elevated,
                        child: CachedNetworkImage(
                          imageUrl: '${ApiConfig.baseUrl}/api/tickets$path',
                          httpHeaders: _accessToken != null ? {'Authorization': 'Bearer $_accessToken'} : null,
                          placeholder: (context, url) => Container(
                            color: c.elevated,
                            child: const Center(child: CircularProgressIndicator(strokeWidth: 2)),
                          ),
                          errorWidget: (context, url, error) => Container(
                            color: c.elevated,
                            child: Center(child: Text('🖼️', style: GoogleFonts.inter(fontSize: 16))),
                          ),
                          fit: BoxFit.cover,
                        ),
                      ),
                    ),
                  );
                },
              ),
            ),
            const SizedBox(height: 12),
          ],
          
          if (!isLocked) ...[
            GestureDetector(
              onTap: _pickPhotos,
              child: Container(
                width: double.infinity,
                padding: const EdgeInsets.symmetric(vertical: 16),
                decoration: BoxDecoration(
                  color: Colors.pinkAccent.withOpacity(0.02),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(
                    color: Colors.pinkAccent.withOpacity(0.3),
                    style: BorderStyle.solid,
                  ),
                ),
                child: Column(
                  children: [
                    const Icon(Icons.add_photo_alternate_outlined, size: 24, color: Colors.pinkAccent),
                    const SizedBox(height: 6),
                    Text(
                      'Ajouter des photos du sinistre',
                      style: GoogleFonts.inter(fontSize: 11, fontWeight: FontWeight.bold, color: c.text2),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      'Appareil photo ou galerie (JPEG, PNG)',
                      style: GoogleFonts.inter(fontSize: 9, color: c.text3),
                    ),
                  ],
                ),
              ),
            ),
          ],

          if (_localPhotos.isNotEmpty) ...[
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: c.elevated,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: c.borderSubtle),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'PHOTOS EN ATTENTE D\'ENVOI :',
                    style: GoogleFonts.inter(fontSize: 8, fontWeight: FontWeight.w800, color: c.text3),
                  ),
                  const SizedBox(height: 8),
                  SizedBox(
                    height: 60,
                    child: ListView.separated(
                      scrollDirection: Axis.horizontal,
                      itemCount: _localPhotos.length,
                      separatorBuilder: (_, __) => const SizedBox(width: 8),
                      itemBuilder: (_, idx) {
                        final file = _localPhotos[idx];
                        return Stack(
                          children: [
                            ClipRRect(
                              borderRadius: BorderRadius.circular(8),
                              child: Image.file(
                                File(file.path),
                                width: 60, height: 60,
                                fit: BoxFit.cover,
                              ),
                            ),
                            Positioned(
                              top: 2, right: 2,
                              child: GestureDetector(
                                onTap: () => setState(() => _localPhotos.removeAt(idx)),
                                child: Container(
                                  padding: const EdgeInsets.all(2),
                                  decoration: const BoxDecoration(
                                    color: Colors.black54,
                                    shape: BoxShape.circle,
                                  ),
                                  child: const Icon(Icons.close, size: 10, color: Colors.white),
                                ),
                              ),
                            ),
                          ],
                        );
                      },
                    ),
                  ),
                  const SizedBox(height: 12),
                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton.icon(
                      onPressed: _uploadingPhotos ? null : _uploadPhotos,
                      icon: _uploadingPhotos
                          ? const SizedBox(width: 12, height: 12, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                          : const Icon(Icons.cloud_upload_outlined, size: 14),
                      label: Text(
                        _uploadingPhotos ? 'Envoi...' : 'Enregistrer les ${_localPhotos.length} photos',
                        style: GoogleFonts.inter(fontSize: 11, fontWeight: FontWeight.bold),
                      ),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: c.primary,
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(vertical: 10),
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
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

  Widget _buildDecisionStatusBadge(AppColorScheme c, String decision) {
    final isFavorable = decision == 'FAVORABLE';
    final isDefavorable = decision == 'DEFAVORABLE';
    final color = isFavorable ? c.success : (isDefavorable ? c.danger : c.warning);
    final icon = isFavorable ? Icons.check_circle_outline : (isDefavorable ? Icons.cancel_outlined : Icons.warning_amber_outlined);
    final text = isFavorable ? 'FAVORABLE' : (isDefavorable ? 'DÉFAVORABLE' : 'ÉTUDE');

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: color.withOpacity(0.1),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: color.withOpacity(0.2)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 10, color: color),
          const SizedBox(width: 4),
          Text(
            'RECOMMANDATION : $text',
            style: GoogleFonts.inter(fontSize: 8, fontWeight: FontWeight.w900, color: color, letterSpacing: 0.5),
          ),
        ],
      ),
    );
  }

  Widget _buildDecisionResult(AppColorScheme c) {
    final d = _decision!;
    final color = c.decisionColor(d.decision);
    final isLocked = _ticket?.statut == 'FERME' || _ticket?.statut == 'RESOLU';
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: color.withOpacity(0.03),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: color.withOpacity(0.15)),
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
                Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.all(6),
                      decoration: BoxDecoration(
                        color: c.primarySoft,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Icon(Icons.psychology_outlined, size: 16, color: c.primary),
                    ),
                    const SizedBox(width: 8),
                    Text('🤖 Assistant de Décision IA', style: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.w800, color: c.text)),
                    const Spacer(),
                    _badge(c, d.source == 'AI' ? '🤖 IA' : d.source == 'RULES' ? '📏 Règles' : d.source == 'CIRCUIT_BREAKER' ? '⚡ Fallback' : '🔄 Hybride', c.primary),
                  ],
                ),
                const SizedBox(height: 16),
                
                Row(
                  crossAxisAlignment: CrossAxisAlignment.center,
                  children: [
                    ScoreGauge(score: d.score, colors: c),
                    const SizedBox(width: 16),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          _buildDecisionStatusBadge(c, d.decision),
                          const SizedBox(height: 8),
                          Text(
                            d.motif,
                            style: GoogleFonts.inter(fontSize: 11, color: c.text2, height: 1.5),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 16),

                if (d.rulesApplied.isNotEmpty) ...[
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: c.elevated.withOpacity(0.5),
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: c.borderSubtle),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'FILTRES RÉGLEMENTAIRES APPLIQUÉS :',
                          style: GoogleFonts.inter(fontSize: 8, fontWeight: FontWeight.w800, color: c.text3, letterSpacing: 0.5),
                        ),
                        const SizedBox(height: 8),
                        ...d.rulesApplied.map((r) => Padding(
                          padding: const EdgeInsets.only(bottom: 4),
                          child: Row(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text('✓ ', style: GoogleFonts.inter(fontSize: 10, color: c.success, fontWeight: FontWeight.bold)),
                              Expanded(
                                child: Text(
                                  r,
                                  style: GoogleFonts.robotoMono(fontSize: 9, color: c.text2),
                                ),
                              ),
                            ],
                          ),
                        )),
                      ],
                    ),
                  ),
                  const SizedBox(height: 12),
                ],

                if (d.ambiguous) ...[
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                    decoration: BoxDecoration(
                      color: c.warningSoft,
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(color: c.warning.withOpacity(0.2)),
                    ),
                    child: Row(
                      children: [
                        Icon(Icons.warning_amber_outlined, size: 14, color: c.warning),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            'Résultat ambigu — vérification manuelle requise',
                            style: GoogleFonts.inter(fontSize: 10, fontWeight: FontWeight.bold, color: c.warning),
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 12),
                ],

                // Technical status indicator
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      'Source : ${d.source == 'AI' ? "Algorithmique IA" : "Moteur de règles"}',
                      style: GoogleFonts.inter(fontSize: 9, color: c.text3),
                    ),
                    Text(
                      'État : ${d.cached ? "Reconstitution Cache 📦" : "Nouvelle exécution 🆕"}',
                      style: GoogleFonts.inter(fontSize: 9, color: c.text3),
                    ),
                  ],
                ),
      
                // Final decision display or interactive controls
                if (d.finalDecision != null) ...[
                  const SizedBox(height: 12),
                  const Divider(height: 1),
                  const SizedBox(height: 12),
                  Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: (d.finalDecision == 'FAVORABLE' ? c.success : c.danger).withOpacity(0.08),
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: (d.finalDecision == 'FAVORABLE' ? c.success : c.danger).withOpacity(0.18)),
                    ),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Icon(
                          Icons.gavel, 
                          size: 16, 
                          color: d.finalDecision == 'FAVORABLE' ? c.success : c.danger,
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                'Arbitrage définitif : ${d.finalDecision == 'FAVORABLE' ? '🟢 FAVORABLE' : '🔴 DÉFAVORABLE'}',
                                style: GoogleFonts.inter(fontSize: 11, fontWeight: FontWeight.bold, color: d.finalDecision == 'FAVORABLE' ? c.success : c.danger),
                              ),
                              if (d.overrideMotif != null && d.overrideMotif!.isNotEmpty) ...[
                                const SizedBox(height: 4),
                                Text(
                                  '"${d.overrideMotif}"',
                                  style: GoogleFonts.inter(fontSize: 10, color: c.text2, fontStyle: FontStyle.italic),
                                ),
                              ],
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 12),
                  SizedBox(
                    width: double.infinity,
                    child: OutlinedButton.icon(
                      onPressed: _downloading ? null : _downloadPDF,
                      icon: _downloading
                          ? SizedBox(width: 14, height: 14, child: CircularProgressIndicator(strokeWidth: 2, color: c.primary))
                          : Icon(Icons.picture_as_pdf, size: 16, color: c.primary),
                      label: Text(
                        _downloading ? 'Génération du PDF...' : 'Télécharger le courrier de réponse (PDF)',
                        style: GoogleFonts.inter(fontSize: 11, color: c.primary, fontWeight: FontWeight.bold),
                      ),
                      style: OutlinedButton.styleFrom(
                        side: BorderSide(color: c.primary.withOpacity(0.4)),
                        padding: const EdgeInsets.symmetric(vertical: 12),
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                      ),
                    ),
                  ),
                ] else if (!isLocked) ...[
                  const SizedBox(height: 12),
                  const Divider(height: 1),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      Expanded(
                        child: GestureDetector(
                          onTap: () => _handleOverride('accept'),
                          child: Container(
                            padding: const EdgeInsets.symmetric(vertical: 12),
                            decoration: BoxDecoration(
                              gradient: const LinearGradient(colors: [Color(0xFF00C48C), Color(0xFF00D4AA)]),
                              borderRadius: BorderRadius.circular(10),
                              boxShadow: [
                                BoxShadow(
                                  color: const Color(0xFF00D4AA).withOpacity(0.3),
                                  blurRadius: 8,
                                  offset: const Offset(0, 2),
                                ),
                              ],
                            ),
                            child: Center(
                              child: Text(
                                '✓ Accepter',
                                style: GoogleFonts.inter(fontSize: 11, fontWeight: FontWeight.bold, color: Colors.white),
                              ),
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: GestureDetector(
                          onTap: () => _handleOverride('reject'),
                          child: Container(
                            padding: const EdgeInsets.symmetric(vertical: 12),
                            decoration: BoxDecoration(
                              gradient: const LinearGradient(colors: [Color(0xFFFF4757), Color(0xFFFF6B81)]),
                              borderRadius: BorderRadius.circular(10),
                              boxShadow: [
                                BoxShadow(
                                  color: const Color(0xFFFF4757).withOpacity(0.3),
                                  blurRadius: 8,
                                  offset: const Offset(0, 2),
                                ),
                              ],
                            ),
                            child: Center(
                              child: Text(
                                '✕ Rejeter',
                                style: GoogleFonts.inter(fontSize: 11, fontWeight: FontWeight.bold, color: Colors.white),
                              ),
                            ),
                          ),
                        ),
                      ),
                    ],
                  ),
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

class ScoreGauge extends StatelessWidget {
  final double score; // 0 to 100
  final AppColorScheme colors;

  const ScoreGauge({super.key, required this.score, required this.colors});

  @override
  Widget build(BuildContext context) {
    final double scoreFraction = (score / 100.0).clamp(0.0, 1.0);
    return Stack(
      alignment: Alignment.center,
      children: [
        if (score >= 70)
          Container(
            width: 76, height: 76,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: const Color(0xFF00D4AA).withOpacity(0.05),
            ),
          ).animate(onPlay: (controller) => controller.repeat(reverse: true))
           .scale(begin: const Offset(0.9, 0.9), end: const Offset(1.1, 1.1), duration: 2.seconds, curve: Curves.easeInOut),
        SizedBox(
          width: 70, height: 70,
          child: CustomPaint(
            painter: _ScoreGaugePainter(score: scoreFraction, colors: colors),
          ),
        ),
        Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              '${score.round()}%',
              style: GoogleFonts.inter(
                fontSize: 15,
                fontWeight: FontWeight.w900,
                color: colors.text,
              ),
            ),
            Text(
              'INDICE',
              style: GoogleFonts.inter(
                fontSize: 6,
                fontWeight: FontWeight.bold,
                color: colors.text3,
                letterSpacing: 0.5,
              ),
            ),
          ],
        ),
      ],
    );
  }
}

class _ScoreGaugePainter extends CustomPainter {
  final double score;
  final AppColorScheme colors;

  _ScoreGaugePainter({required this.score, required this.colors});

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final radius = (size.width < size.height ? size.width : size.height) / 2 - 4;
    const strokeWidth = 5.0;

    final trackPaint = Paint()
      ..color = colors.borderSubtle
      ..style = PaintingStyle.stroke
      ..strokeWidth = strokeWidth;
    canvas.drawCircle(center, radius, trackPaint);

    final progressPaint = Paint()
      ..shader = const SweepGradient(
        colors: [Color(0xFF00D4AA), Color(0xFF635BFF), Color(0xFFA78BFA), Color(0xFF00D4AA)],
        stops: [0.0, 0.35, 0.7, 1.0],
        transform: GradientRotation(-3.141592653589793 / 2),
      ).createShader(Rect.fromCircle(center: center, radius: radius))
      ..style = PaintingStyle.stroke
      ..strokeWidth = strokeWidth
      ..strokeCap = StrokeCap.round;

    canvas.drawArc(
      Rect.fromCircle(center: center, radius: radius),
      -3.141592653589793 / 2,
      2 * 3.141592653589793 * score,
      false,
      progressPaint,
    );
  }

  @override
  bool shouldRepaint(covariant _ScoreGaugePainter oldDelegate) {
    return oldDelegate.score != score;
  }
}
