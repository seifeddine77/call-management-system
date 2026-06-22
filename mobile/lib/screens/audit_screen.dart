import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:pdf/pdf.dart';
import 'package:pdf/widgets.dart' as pw;
import 'package:printing/printing.dart';
import '../config/colors.dart';
import '../models/ticket.dart';
import '../models/decision.dart';
import '../models/notification.dart';
import '../services/ticket_service.dart';
import '../services/decision_service.dart';
import '../services/notification_service.dart';

// ═══════════════════════════════════════════════════
//  AUDIT SCREEN — Centre d'Investigation (CDC 6)
//  Port from React AuditPage.tsx → Flutter
// ═══════════════════════════════════════════════════

/// Unified timeline event model
class _TimelineEvent {
  final String id;
  final String timestamp;
  final String source; // 'ticket', 'decision', 'notification'
  final String type;
  final String title;
  final String detail;
  final Color color;
  final IconData icon;

  _TimelineEvent({
    required this.id,
    required this.timestamp,
    required this.source,
    required this.type,
    required this.title,
    required this.detail,
    required this.color,
    required this.icon,
  });
}

/// RGPD compliance check
class _RgpdCheck {
  final String label;
  final String desc;
  final bool? ok;
  final IconData icon;
  final bool na;
  final String? recommendation;

  _RgpdCheck({
    required this.label,
    required this.desc,
    required this.ok,
    required this.icon,
    this.na = false,
    this.recommendation,
  });
}

/// Pipeline step
class _PipelineStep {
  final String label;
  final String sub;
  final bool active;
  final bool hit;
  final IconData icon;

  _PipelineStep({required this.label, required this.sub, required this.active, required this.hit, required this.icon});
}

class AuditScreen extends StatefulWidget {
  final String ticketId;
  const AuditScreen({super.key, required this.ticketId});

  @override
  State<AuditScreen> createState() => _AuditScreenState();
}

class _AuditScreenState extends State<AuditScreen> {
  final _ticketService = TicketService();
  final _decisionService = DecisionService();
  final _notificationService = NotificationService();

  Ticket? _ticket;
  DecisionResult? _decision;
  List<NotificationLog> _notifications = [];
  bool _loading = true;
  bool _exporting = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _fetchData();
  }

  Future<void> _fetchData() async {
    setState(() { _loading = true; _error = null; });
    try {
      final ticket = await _ticketService.getTicketById(widget.ticketId);
      DecisionResult? decision;
      try { decision = await _decisionService.getByTicket(widget.ticketId); } catch (_) {}
      List<NotificationLog> notifs = [];
      try {
        final allNotifs = await _notificationService.getLogs(size: 100);
        notifs = allNotifs.where((n) => n.destinataire == (ticket.clientEmail ?? '')).toList();
      } catch (_) {}
      setState(() {
        _ticket = ticket;
        _decision = decision;
        _notifications = notifs;
        _loading = false;
      });
    } catch (e) {
      setState(() { _error = e.toString(); _loading = false; });
    }
  }

  // ── Helpers ──

  static String _fmtDuration(int ms) {
    final m = (ms / 60000).round();
    if (m < 1) return '< 1min';
    if (m < 60) return '${m}min';
    final h = m ~/ 60;
    return '${h}h ${m % 60}min';
  }

  static DateTime _parseTs(String ts) {
    return DateTime.tryParse(ts.endsWith('Z') ? ts : '${ts}Z')?.toLocal() ?? DateTime.now();
  }

  static String _fmtDate(String ts) {
    final d = _parseTs(ts);
    return '${d.day.toString().padLeft(2, '0')}/${d.month.toString().padLeft(2, '0')}/${d.year} ${d.hour.toString().padLeft(2, '0')}:${d.minute.toString().padLeft(2, '0')}';
  }

  static const _actionLabels = {
    'CREE': 'Ticket créé',
    'ASSIGNE': 'Assigné à un opérateur',
    'STATUT_CHANGE': 'Changement de statut',
    'TRANSCRIPTION_AJOUTEE': 'Transcription audio ajoutée',
    'PHOTOS_AJOUTEES': 'Photos sinistre ajoutées',
    'ANONYMISE': 'Données anonymisées (RGPD)',
  };

  static const _actionIcons = {
    'CREE': Icons.bolt,
    'ASSIGNE': Icons.people,
    'STATUT_CHANGE': Icons.timeline,
    'TRANSCRIPTION_AJOUTEE': Icons.description,
    'PHOTOS_AJOUTEES': Icons.camera_alt,
    'ANONYMISE': Icons.lock,
  };

  // ── Build unified timeline ──
  List<_TimelineEvent> get _timeline {
    if (_ticket == null) return [];
    final events = <_TimelineEvent>[];

    // 1. Ticket actions
    for (final a in _ticket!.actions) {
      String detail;
      if (a.action == 'STATUT_CHANGE' && a.oldValue != null && a.newValue != null) {
        detail = '${a.oldValue} → ${a.newValue}${a.notes != null ? ' · ${a.notes}' : ''}';
      } else if (a.action == 'ASSIGNE' && a.newValue != null) {
        detail = 'Opérateur: ${a.newValue}${a.userName != null ? ' · Par: ${a.userName}' : ''}';
      } else {
        detail = '${a.userName ?? 'Système'}${a.notes != null ? ' · ${a.notes}' : ''}';
      }
      events.add(_TimelineEvent(
        id: 'action-${a.id}',
        timestamp: a.createdAt,
        source: 'ticket',
        type: a.action,
        title: _actionLabels[a.action] ?? a.action,
        detail: detail,
        color: const Color(0xFF8B5CF6),
        icon: _actionIcons[a.action] ?? Icons.timeline,
      ));
    }

    // 2. Decision
    if (_decision != null) {
      events.add(_TimelineEvent(
        id: 'decision-created',
        timestamp: _ticket!.updatedAt ?? _ticket!.createdAt,
        source: 'decision',
        type: 'DECISION_IA',
        title: 'Décision IA : ${_decision!.decision}',
        detail: 'Score: ${_decision!.score.toStringAsFixed(0)}% · Source: ${_decision!.source}'
            '${_decision!.rulesApplied.isNotEmpty ? ' · Règles: ${_decision!.rulesApplied.join(', ')}' : ''}'
            ' · ${_decision!.motif}',
        color: const Color(0xFF10B981),
        icon: Icons.smart_toy,
      ));
      if (_decision!.finalDecision != null) {
        events.add(_TimelineEvent(
          id: 'decision-finalized',
          timestamp: _ticket!.updatedAt ?? _ticket!.createdAt,
          source: 'decision',
          type: 'DECISION_FINALE',
          title: 'Décision superviseur : ${_decision!.finalDecision}',
          detail: _decision!.overrideMotif ?? 'Validée',
          color: const Color(0xFF10B981),
          icon: Icons.verified_user,
        ));
      }
    }

    // 3. Notifications
    for (int i = 0; i < _notifications.length; i++) {
      final n = _notifications[i];
      events.add(_TimelineEvent(
        id: 'notif-${n.id}',
        timestamp: n.createdAt,
        source: 'notification',
        type: n.type,
        title: 'Email: ${n.type.replaceAll('_', ' ')}',
        detail: '→ ${n.destinataire} · ${n.statut == 'SENT' ? '✓ Envoyé' : n.statut == 'FAILED' ? '✕ Échec' : '◷ En attente'}'
            '${n.sujet.isNotEmpty ? ' · ${n.sujet}' : ''}',
        color: const Color(0xFF3B82F6),
        icon: Icons.mail,
      ));
    }

    // Sort chronologically
    events.sort((a, b) => _parseTs(a.timestamp).compareTo(_parseTs(b.timestamp)));
    return events;
  }

  // ── RGPD checks ──
  List<_RgpdCheck> get _rgpdChecks {
    if (_ticket == null) return [];
    final t = _ticket!;
    final isCall = t.canal == 'CALL';
    final isEmail = t.canal == 'EMAIL';
    final hasAudio = t.audioPath != null && t.audioPath!.isNotEmpty;
    final hasTranscription = t.transcription != null && t.transcription!.isNotEmpty;
    final hasNotif = _notifications.isNotEmpty;
    final hasDecision = _decision != null;
    final dataMin = t.description == null || t.description!.length < 2000;
    final isClosed = t.statut == 'FERME' || t.statut == 'RESOLU';

    final checks = <_RgpdCheck>[];

    // 1. Consentement
    checks.add(_RgpdCheck(
      label: isCall ? 'Consentement enregistrement (IVR)' : isEmail ? 'Consentement implicite (email initié)' : 'Consentement explicite (formulaire)',
      desc: 'Art. 6 RGPD — Base légale du traitement',
      ok: true, // Assume consent from API
      icon: Icons.check_circle,
    ));

    // 2. Audio & Transcription (CALL only)
    if (isCall) {
      checks.add(_RgpdCheck(
        label: 'Audio enregistré & stocké',
        desc: 'Art. 32 — Sécurité du traitement des données vocales',
        ok: hasAudio,
        icon: hasAudio ? Icons.lock : Icons.warning_amber,
        recommendation: !hasAudio ? 'Aucun enregistrement audio associé à cet appel' : null,
      ));
      checks.add(_RgpdCheck(
        label: 'Transcription générée (Whisper)',
        desc: 'Art. 22 — Transparence du traitement automatisé',
        ok: hasTranscription,
        icon: hasTranscription ? Icons.description : Icons.warning_amber,
        recommendation: !hasTranscription ? 'Lancer la transcription pour documenter l\'échange' : null,
      ));
    }

    // 3. Photos (WEB only)
    if (!isCall && !isEmail) {
      final hasPhotos = t.photosPaths != null && t.photosPaths!.isNotEmpty && t.photosPaths != '[]';
      checks.add(_RgpdCheck(
        label: 'Photos sinistre sécurisées',
        desc: 'Art. 32 — Protection des données visuelles',
        ok: hasPhotos ? true : null,
        icon: hasPhotos ? Icons.camera_alt : Icons.warning_amber,
        na: !hasPhotos,
      ));
    }

    // 4. Traçabilité décisionnelle IA
    checks.add(_RgpdCheck(
      label: 'Traçabilité décisionnelle IA',
      desc: 'Art. 22 — Décision automatisée explicable',
      ok: hasDecision,
      icon: hasDecision ? Icons.smart_toy : Icons.cancel,
      recommendation: !hasDecision ? 'Lancer l\'analyse IA pour documenter la décision' : null,
    ));

    // 5. Notification client
    checks.add(_RgpdCheck(
      label: 'Notification client envoyée',
      desc: 'Art. 13/14 — Droit à l\'information',
      ok: hasNotif,
      icon: hasNotif ? Icons.mail : Icons.cancel,
      recommendation: !hasNotif ? 'Informer le client du traitement de sa demande' : null,
    ));

    // 6. Minimisation des données
    checks.add(_RgpdCheck(
      label: 'Minimisation des données',
      desc: 'Art. 5(1)(c) — Données adéquates et pertinentes',
      ok: dataMin,
      icon: dataMin ? Icons.check_circle : Icons.warning_amber,
      recommendation: !dataMin ? 'Réduire le volume de données collectées' : null,
    ));

    // 7. Droit à l'effacement
    if (isClosed) {
      final closedDays = DateTime.now().difference(_parseTs(t.updatedAt ?? t.createdAt)).inDays;
      final needsDeletion = closedDays > 90;
      checks.add(_RgpdCheck(
        label: 'Droit à l\'effacement (${closedDays}j depuis clôture)',
        desc: 'Art. 17 — Suppression après délai de rétention',
        ok: !needsDeletion,
        icon: needsDeletion ? Icons.visibility : Icons.shield,
        recommendation: needsDeletion ? 'Ticket clôturé depuis $closedDays jours — planifier l\'anonymisation' : null,
      ));
    }

    return checks;
  }

  int get _rgpdScore {
    final applicable = _rgpdChecks.where((r) => !r.na).toList();
    if (applicable.isEmpty) return 0;
    return ((applicable.where((r) => r.ok == true).length / applicable.length) * 100).round();
  }

  // ── Performance metrics ──
  Map<String, dynamic>? get _perf {
    if (_ticket == null) return null;
    final created = _parseTs(_ticket!.createdAt).millisecondsSinceEpoch;
    final updated = _parseTs(_ticket!.updatedAt ?? _ticket!.createdAt).millisecondsSinceEpoch;
    final actions = _ticket!.actions;
    final assignAction = actions.where((a) => a.action == 'ASSIGNE').firstOrNull;
    final resolveAction = actions.where((a) => a.action == 'STATUT_CHANGE' && a.newValue == 'RESOLU').firstOrNull;
    final assignTime = assignAction != null ? _parseTs(assignAction.createdAt).millisecondsSinceEpoch - created : null;
    final resolveTime = resolveAction != null ? _parseTs(resolveAction.createdAt).millisecondsSinceEpoch - created : null;
    final totalDuration = updated - created;
    final slaOk = totalDuration < 4 * 3600000;
    return {
      'totalDuration': totalDuration,
      'assignTime': assignTime,
      'resolveTime': resolveTime,
      'slaOk': slaOk,
      'actionCount': actions.length,
    };
  }

  // ── Pipeline steps ──
  List<_PipelineStep>? get _pipelineSteps {
    if (_decision == null) return null;
    final src = _decision!.source;
    return [
      _PipelineStep(label: 'Cache Redis', sub: 'SHA-256', active: src == 'CACHE', hit: src == 'CACHE', icon: Icons.storage),
      _PipelineStep(label: 'Rules Engine', sub: '${_decision!.rulesApplied.length} règle(s)', active: src == 'RULES' || src == 'HYBRID', hit: _decision!.rulesApplied.isNotEmpty, icon: Icons.bolt),
      _PipelineStep(label: 'LLM (Gemma)', sub: 'Score: ${_decision!.score.toStringAsFixed(0)}%', active: src == 'AI' || src == 'HYBRID', hit: src == 'AI' || src == 'HYBRID', icon: Icons.psychology),
    ];
  }

  // ── PDF Export ──
  Future<void> _exportPDF() async {
    if (_ticket == null) return;
    setState(() => _exporting = true);
    try {
      final t = _ticket!;
      final doc = pw.Document();

      doc.addPage(pw.MultiPage(
        pageFormat: PdfPageFormat.a4,
        margin: const pw.EdgeInsets.all(40),
        header: (context) => pw.Container(
          padding: const pw.EdgeInsets.only(bottom: 10),
          decoration: const pw.BoxDecoration(border: pw.Border(bottom: pw.BorderSide(width: 1, color: PdfColors.grey300))),
          child: pw.Row(
            mainAxisAlignment: pw.MainAxisAlignment.spaceBetween,
            children: [
              pw.Text('RAPPORT D\'AUDIT', style: pw.TextStyle(fontSize: 18, fontWeight: pw.FontWeight.bold, color: PdfColor.fromHex('#635BFF'))),
              pw.Text('${t.numero} — ${DateTime.now().toString().substring(0, 16)}', style: const pw.TextStyle(fontSize: 9, color: PdfColors.grey600)),
            ],
          ),
        ),
        footer: (context) => pw.Container(
          alignment: pw.Alignment.centerRight,
          child: pw.Text('AutoAssist — Page ${context.pageNumber}/${context.pagesCount}', style: const pw.TextStyle(fontSize: 8, color: PdfColors.grey500)),
        ),
        build: (context) => [
          // 1. Ticket info
          pw.Header(level: 1, text: '1. Informations du ticket'),
          _pdfInfoRow('Client', t.clientNom),
          _pdfInfoRow('N° Ticket', t.numero),
          _pdfInfoRow('Canal', t.canal),
          _pdfInfoRow('Statut', t.statut),
          _pdfInfoRow('Email', t.clientEmail ?? 'N/A'),
          _pdfInfoRow('Tél', t.clientTel ?? 'N/A'),
          _pdfInfoRow('Créé le', _fmtDate(t.createdAt)),
          pw.SizedBox(height: 10),

          // 2. Performance
          if (_perf != null) ...[
            pw.Header(level: 1, text: '2. Métriques de performance'),
            _pdfInfoRow('Durée totale', _fmtDuration(_perf!['totalDuration'])),
            _pdfInfoRow('Assignation', _perf!['assignTime'] != null ? _fmtDuration(_perf!['assignTime']) : 'N/A'),
            _pdfInfoRow('Résolution', _perf!['resolveTime'] != null ? _fmtDuration(_perf!['resolveTime']) : 'N/A'),
            _pdfInfoRow('SLA', _perf!['slaOk'] ? 'CONFORME (< 4h)' : 'DÉPASSÉ'),
            _pdfInfoRow('Actions', '${_perf!['actionCount']}'),
            pw.SizedBox(height: 10),
          ],

          // 3. Decision IA
          if (_decision != null) ...[
            pw.Header(level: 1, text: '3. Pipeline décisionnel IA'),
            _pdfInfoRow('Décision', _decision!.decision),
            _pdfInfoRow('Score', '${_decision!.score.toStringAsFixed(0)}%'),
            _pdfInfoRow('Source', _decision!.source),
            if (_decision!.rulesApplied.isNotEmpty) _pdfInfoRow('Règles', _decision!.rulesApplied.join(', ')),
            pw.Paragraph(text: 'Motif: ${_decision!.motif}', style: const pw.TextStyle(fontSize: 9)),
            if (_decision!.finalDecision != null) _pdfInfoRow('Superviseur', _decision!.finalDecision!),
            pw.SizedBox(height: 10),
          ],

          // 4. Timeline
          pw.Header(level: 1, text: '4. Timeline (${_timeline.length} événements)'),
          ..._timeline.map((ev) => pw.Container(
            margin: const pw.EdgeInsets.only(bottom: 6),
            child: pw.Column(crossAxisAlignment: pw.CrossAxisAlignment.start, children: [
              pw.Text('[${_fmtDate(ev.timestamp)}] ${ev.title}', style: pw.TextStyle(fontSize: 9, fontWeight: pw.FontWeight.bold)),
              pw.Text(ev.detail, style: const pw.TextStyle(fontSize: 8, color: PdfColors.grey700)),
            ]),
          )),
          pw.SizedBox(height: 10),

          // 5. RGPD
          pw.Header(level: 1, text: '5. Conformité RGPD — Score: $_rgpdScore%'),
          ..._rgpdChecks.map((r) => pw.Container(
            margin: const pw.EdgeInsets.only(bottom: 4),
            child: pw.Row(crossAxisAlignment: pw.CrossAxisAlignment.start, children: [
              pw.Text(r.na ? 'N/A' : r.ok == true ? '✓' : '✕', style: pw.TextStyle(fontSize: 9, fontWeight: pw.FontWeight.bold, color: r.na ? PdfColors.grey500 : r.ok == true ? PdfColor.fromHex('#10B981') : PdfColor.fromHex('#EF4444'))),
              pw.SizedBox(width: 6),
              pw.Expanded(child: pw.Column(crossAxisAlignment: pw.CrossAxisAlignment.start, children: [
                pw.Text(r.label, style: pw.TextStyle(fontSize: 9, fontWeight: pw.FontWeight.bold)),
                pw.Text(r.desc, style: const pw.TextStyle(fontSize: 8, color: PdfColors.grey600)),
              ])),
            ]),
          )),
        ],
      ));

      final bytes = await doc.save();

      // Printing.sharePdf accepte directement les octets : fonctionne sur Web
      // (téléchargement navigateur) ET en natif (feuille de partage), sans passer
      // par un fichier temporaire — getTemporaryDirectory()/dart:io n'existent pas sur Web.
      final filename = 'audit_${t.numero}_${DateTime.now().toIso8601String().substring(0, 10)}.pdf';
      if (mounted) {
        await Printing.sharePdf(bytes: bytes, filename: filename);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Erreur export PDF: $e'), backgroundColor: context.colors.danger),
        );
      }
    } finally {
      if (mounted) setState(() => _exporting = false);
    }
  }

  pw.Widget _pdfInfoRow(String label, String value) {
    return pw.Padding(
      padding: const pw.EdgeInsets.only(bottom: 3),
      child: pw.Row(children: [
        pw.SizedBox(width: 100, child: pw.Text('$label:', style: pw.TextStyle(fontSize: 9, fontWeight: pw.FontWeight.bold))),
        pw.Expanded(child: pw.Text(value, style: const pw.TextStyle(fontSize: 9))),
      ]),
    );
  }

  // ═══════════════════════════════════════════════════
  //  BUILD
  // ═══════════════════════════════════════════════════

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Scaffold(
      appBar: AppBar(
        title: Row(children: [
          Icon(Icons.shield, size: 18, color: c.primary),
          const SizedBox(width: 8),
          Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text('Centre d\'Audit', style: GoogleFonts.inter(fontSize: 15, fontWeight: FontWeight.w800, color: c.text)),
            if (_ticket != null)
              Text(_ticket!.numero, style: GoogleFonts.inter(fontSize: 9, color: c.text3, letterSpacing: 1)),
          ]),
        ]),
        actions: [
          IconButton(
            icon: _exporting
                ? SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2, color: c.primary))
                : Icon(Icons.picture_as_pdf, size: 18, color: c.primary),
            onPressed: _exporting ? null : _exportPDF,
            tooltip: 'Export PDF',
          ),
          IconButton(icon: Icon(Icons.refresh, size: 18, color: c.text3), onPressed: _fetchData),
        ],
      ),
      body: _loading
          ? Center(child: CircularProgressIndicator(color: c.primary))
          : _error != null
              ? Center(child: Column(mainAxisSize: MainAxisSize.min, children: [
                  Icon(Icons.error_outline, size: 40, color: c.danger),
                  const SizedBox(height: 8),
                  Text(_error!, style: GoogleFonts.inter(color: c.danger, fontSize: 12)),
                  TextButton(onPressed: _fetchData, child: const Text('Réessayer')),
                ]))
              : _ticket == null
                  ? const SizedBox()
                  : RefreshIndicator(onRefresh: _fetchData, child: _buildContent(c)),
    );
  }

  Widget _buildContent(AppColorScheme c) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        _buildSummaryBanner(c),
        const SizedBox(height: 12),
        _buildPipelineSection(c),
        const SizedBox(height: 12),
        _buildTimelineSection(c),
        const SizedBox(height: 12),
        _buildRgpdSection(c),
        const SizedBox(height: 12),
        _buildGoToTicketButton(c),
        const SizedBox(height: 32),
      ],
    );
  }

  // ── Summary Banner ──
  Widget _buildSummaryBanner(AppColorScheme c) {
    final t = _ticket!;
    final perf = _perf;
    final canalColor = c.canalColor(t.canal);
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        gradient: c.gradientCard,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: c.border),
        boxShadow: c.isDark ? [] : [BoxShadow(color: c.shadow, blurRadius: 10, offset: const Offset(0, 4))],
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        // Top bar gradient
        Container(
          height: 2,
          margin: const EdgeInsets.only(bottom: 12),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(1),
            gradient: LinearGradient(colors: [canalColor, c.primary, c.secondary]),
          ),
        ),
        // Client info row
        Row(children: [
          Container(
            width: 44, height: 44,
            decoration: BoxDecoration(
              color: canalColor.withOpacity(0.12),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: canalColor.withOpacity(0.2)),
            ),
            child: Center(
              child: Icon(
                t.canal == 'CALL'
                    ? Icons.phone_outlined
                    : t.canal == 'EMAIL'
                        ? Icons.mail_outlined
                        : Icons.language_outlined,
                size: 20,
                color: canalColor,
              ),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(t.clientNom, style: GoogleFonts.inter(fontSize: 15, fontWeight: FontWeight.w800, color: c.text)),
            Row(children: [
              Text(t.numero, style: GoogleFonts.inter(fontSize: 10, fontWeight: FontWeight.w600, color: c.primary)),
              const SizedBox(width: 8),
              Text(t.clientEmail ?? t.clientTel ?? '', style: GoogleFonts.inter(fontSize: 10, color: c.text3)),
            ]),
          ])),
        ]),
        const SizedBox(height: 10),
        // Badges
        Wrap(spacing: 6, runSpacing: 6, children: [
          _badge(c, t.statut, c.statutColor(t.statut)),
          _badge(c, t.canal, canalColor),
          if (perf != null)
            _badge(c, 'SLA: ${perf['slaOk'] ? '✓ OK' : '✕ Dépassé'}',
                perf['slaOk'] ? c.success : c.danger),
        ]),
        // Metrics row
        if (perf != null) ...[
          const SizedBox(height: 10),
          Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
            _metricChip(c, Icons.timer, 'Durée', _fmtDuration(perf['totalDuration'])),
            _metricChip(c, Icons.people, 'Assign.', perf['assignTime'] != null ? _fmtDuration(perf['assignTime']) : 'N/A'),
            _metricChip(c, Icons.check_circle, 'Résol.', perf['resolveTime'] != null ? _fmtDuration(perf['resolveTime']) : 'N/A'),
            _metricChip(c, Icons.timeline, 'Actions', '${perf['actionCount']}'),
          ]),
        ],
      ]),
    ).animate().fadeIn().slideY(begin: 0.05);
  }

  Widget _metricChip(AppColorScheme c, IconData icon, String label, String value) {
    return Column(children: [
      Icon(icon, size: 12, color: c.text3),
      const SizedBox(height: 2),
      Text(label, style: GoogleFonts.inter(fontSize: 8, color: c.text3)),
      Text(value, style: GoogleFonts.inter(fontSize: 10, fontWeight: FontWeight.w700, color: c.text)),
    ]);
  }

  // ── Pipeline Section ──
  Widget _buildPipelineSection(AppColorScheme c) {
    final steps = _pipelineSteps;
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: c.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: c.border),
        boxShadow: c.isDark ? [] : [BoxShadow(color: c.shadow, blurRadius: 8, offset: const Offset(0, 2))],
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        // Top accent bar
        Container(
          height: 2, width: double.infinity,
          margin: const EdgeInsets.only(bottom: 12),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(1),
            gradient: const LinearGradient(colors: [Color(0xFF2DD4BF), Color(0xFF6366F1)]),
          ),
        ),
        Row(children: [
          Icon(Icons.smart_toy, size: 14, color: c.primary),
          const SizedBox(width: 6),
          Text('Pipeline décisionnel', style: GoogleFonts.inter(fontSize: 13, fontWeight: FontWeight.w700, color: c.text)),
        ]),
        const SizedBox(height: 12),
        if (steps != null) ...[
          ...steps.asMap().entries.map((e) {
            final i = e.key;
            final s = e.value;
            return Column(children: [
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: s.active ? c.primarySoft : s.hit ? c.successSoft : c.elevated,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: s.active ? c.primary.withOpacity(0.3) : s.hit ? c.success.withOpacity(0.2) : c.border),
                ),
                child: Row(children: [
                  Container(
                    width: 32, height: 32,
                    decoration: BoxDecoration(
                      color: s.active ? c.primary : s.hit ? c.success.withOpacity(0.2) : c.muted,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Icon(s.icon, size: 16, color: s.active ? Colors.white : s.hit ? c.success : c.text3),
                  ),
                  const SizedBox(width: 10),
                  Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text(s.label, style: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.w700, color: c.text)),
                    Text(s.sub, style: GoogleFonts.inter(fontSize: 9, color: c.text3)),
                  ])),
                  Text(
                    s.active ? '● ACTIF' : s.hit ? '✓' : '○ SKIP',
                    style: GoogleFonts.inter(fontSize: 9, fontWeight: FontWeight.w700,
                        color: s.active ? c.primary : s.hit ? c.success : c.text3),
                  ),
                ]),
              ),
              if (i < steps.length - 1)
                Container(width: 1, height: 12, color: c.border),
            ]);
          }),
          // Final decision
          if (_decision?.finalDecision != null) ...[
            const SizedBox(height: 8),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: (_decision!.finalDecision == 'FAVORABLE' ? c.success : c.danger).withOpacity(0.08),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: (_decision!.finalDecision == 'FAVORABLE' ? c.success : c.danger).withOpacity(0.2)),
              ),
              child: Column(children: [
                Icon(Icons.verified_user, size: 18, color: _decision!.finalDecision == 'FAVORABLE' ? c.success : c.danger),
                const SizedBox(height: 4),
                Text('Superviseur: ${_decision!.finalDecision}',
                    style: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.w700,
                        color: _decision!.finalDecision == 'FAVORABLE' ? c.success : c.danger)),
                if (_decision!.overrideMotif != null)
                  Text(_decision!.overrideMotif!, style: GoogleFonts.inter(fontSize: 10, color: c.text2)),
              ]),
            ),
          ],
        ] else ...[
          Center(child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(children: [
              Icon(Icons.smart_toy, size: 28, color: c.text3.withOpacity(0.3)),
              const SizedBox(height: 8),
              Text('Aucune décision IA pour ce ticket', style: GoogleFonts.inter(fontSize: 11, color: c.text3)),
            ]),
          )),
        ],
      ]),
    ).animate().fadeIn(delay: 100.ms);
  }

  // ── Timeline Section ──
  Widget _buildTimelineSection(AppColorScheme c) {
    final events = _timeline;
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: c.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: c.border),
        boxShadow: c.isDark ? [] : [BoxShadow(color: c.shadow, blurRadius: 8, offset: const Offset(0, 2))],
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Icon(Icons.schedule, size: 14, color: c.primary),
          const SizedBox(width: 6),
          Text('Timeline unifiée', style: GoogleFonts.inter(fontSize: 13, fontWeight: FontWeight.w700, color: c.text)),
          const SizedBox(width: 8),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
            decoration: BoxDecoration(color: c.primarySoft, borderRadius: BorderRadius.circular(10)),
            child: Text('${events.length}', style: GoogleFonts.inter(fontSize: 9, fontWeight: FontWeight.w700, color: c.primary)),
          ),
        ]),
        const SizedBox(height: 12),
        if (events.isEmpty)
          Center(child: Padding(
            padding: const EdgeInsets.all(24),
            child: Text('Aucun événement enregistré', style: GoogleFonts.inter(fontSize: 11, color: c.text3)),
          ))
        else
          ...events.asMap().entries.map((e) {
            final i = e.key;
            final ev = e.value;
            return Padding(
              padding: const EdgeInsets.only(bottom: 4),
              child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                // Dot + line
                SizedBox(width: 24, child: Column(children: [
                  Container(
                    width: 12, height: 12,
                    margin: const EdgeInsets.only(top: 2),
                    decoration: BoxDecoration(
                      color: ev.color,
                      shape: BoxShape.circle,
                      border: Border.all(color: c.surface, width: 2),
                    ),
                    child: Center(child: Text(
                      ev.source == 'ticket' ? 'T' : ev.source == 'decision' ? 'D' : 'N',
                      style: const TextStyle(fontSize: 5, fontWeight: FontWeight.w900, color: Colors.white),
                    )),
                  ),
                  if (i < events.length - 1)
                    Container(width: 1, height: 36, color: ev.color.withOpacity(0.2)),
                ])),
                const SizedBox(width: 8),
                // Content
                Expanded(child: Padding(
                  padding: const EdgeInsets.only(bottom: 6),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Row(children: [
                      Icon(ev.icon, size: 11, color: ev.color),
                      const SizedBox(width: 4),
                      Flexible(child: Text(ev.title, style: GoogleFonts.inter(fontSize: 11, fontWeight: FontWeight.w700, color: ev.color))),
                      const SizedBox(width: 6),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 1),
                        decoration: BoxDecoration(
                          color: ev.color.withOpacity(0.08),
                          borderRadius: BorderRadius.circular(4),
                          border: Border.all(color: ev.color.withOpacity(0.15)),
                        ),
                        child: Text(
                          ev.source == 'ticket' ? 'ticket' : ev.source == 'decision' ? 'decision' : 'notif',
                          style: GoogleFonts.inter(fontSize: 7, fontWeight: FontWeight.w600, color: ev.color),
                        ),
                      ),
                    ]),
                    const SizedBox(height: 2),
                    Text(ev.detail, style: GoogleFonts.inter(fontSize: 10, color: c.text2, height: 1.4)),
                    Text(_fmtDate(ev.timestamp), style: GoogleFonts.inter(fontSize: 8, color: c.text3)),
                  ]),
                )),
              ]),
            ).animate().fadeIn(delay: (i * 40).ms).slideX(begin: -0.05);
          }),
      ]),
    ).animate().fadeIn(delay: 200.ms);
  }

  // ── RGPD Section ──
  Widget _buildRgpdSection(AppColorScheme c) {
    final checks = _rgpdChecks;
    final score = _rgpdScore;
    final scoreColor = score >= 80 ? c.success : score >= 50 ? c.warning : c.danger;

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: c.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: c.border),
        boxShadow: c.isDark ? [] : [BoxShadow(color: c.shadow, blurRadius: 8, offset: const Offset(0, 2))],
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        // Top accent
        Container(
          height: 2, width: double.infinity,
          margin: const EdgeInsets.only(bottom: 12),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(1),
            gradient: const LinearGradient(colors: [Color(0xFFFBBF24), Color(0xFFF43F5E)]),
          ),
        ),
        Row(children: [
          const Icon(Icons.shield, size: 14, color: Color(0xFFFBBF24)),
          const SizedBox(width: 6),
          Text('Conformité RGPD', style: GoogleFonts.inter(fontSize: 13, fontWeight: FontWeight.w700, color: c.text)),
        ]),
        const SizedBox(height: 8),
        // Score
        Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
          Text('Score de conformité', style: GoogleFonts.inter(fontSize: 9, color: c.text3)),
          Text('$score%', style: GoogleFonts.inter(fontSize: 15, fontWeight: FontWeight.w900, color: scoreColor)),
        ]),
        const SizedBox(height: 4),
        ClipRRect(
          borderRadius: BorderRadius.circular(4),
          child: LinearProgressIndicator(
            value: score / 100,
            minHeight: 6,
            backgroundColor: c.muted,
            valueColor: AlwaysStoppedAnimation(scoreColor),
          ),
        ),
        const SizedBox(height: 12),
        // Checks
        ...checks.map((r) => Container(
          margin: const EdgeInsets.only(bottom: 6),
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(
            color: r.na ? c.muted.withOpacity(0.3) : r.ok == true ? c.successSoft : c.dangerSoft,
            borderRadius: BorderRadius.circular(10),
            border: Border.all(color: r.na ? c.border : r.ok == true ? c.success.withOpacity(0.15) : c.danger.withOpacity(0.15)),
          ),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              Icon(r.icon, size: 14, color: r.na ? c.text3 : r.ok == true ? c.success : c.danger),
              const SizedBox(width: 6),
              Expanded(child: Text(r.label, style: GoogleFonts.inter(fontSize: 11, fontWeight: FontWeight.w600, color: c.text))),
              Text(
                r.na ? 'N/A' : r.ok == true ? '✓ CONFORME' : '✕ NON',
                style: GoogleFonts.inter(fontSize: 8, fontWeight: FontWeight.w700,
                    color: r.na ? c.text3 : r.ok == true ? c.success : c.danger),
              ),
            ]),
            Text(r.desc, style: GoogleFonts.inter(fontSize: 8, color: c.text3)),
            if (r.recommendation != null) ...[
              const SizedBox(height: 4),
              Row(children: [
                Icon(Icons.warning_amber, size: 10, color: c.warning),
                const SizedBox(width: 4),
                Flexible(child: Text(r.recommendation!, style: GoogleFonts.inter(fontSize: 9, color: c.warning))),
              ]),
            ],
          ]),
        )),
      ]),
    ).animate().fadeIn(delay: 300.ms);
  }

  // ── Go to ticket button ──
  Widget _buildGoToTicketButton(AppColorScheme c) {
    return SizedBox(
      width: double.infinity,
      child: OutlinedButton.icon(
        onPressed: () => Navigator.pop(context),
        icon: Icon(Icons.description, size: 14, color: c.text2),
        label: Text('Retour au ticket', style: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.w600, color: c.text2)),
        style: OutlinedButton.styleFrom(
          padding: const EdgeInsets.symmetric(vertical: 12),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          side: BorderSide(color: c.border),
        ),
      ),
    ).animate().fadeIn(delay: 400.ms);
  }

  // ── Badge helper ──
  Widget _badge(AppColorScheme c, String text, Color color) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: color.withOpacity(0.1),
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: color.withOpacity(0.2)),
      ),
      child: Text(text, style: GoogleFonts.inter(fontSize: 9, fontWeight: FontWeight.w600, color: color)),
    );
  }
}
