import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:flutter_animate/flutter_animate.dart';
import '../config/colors.dart';
import '../models/notification.dart';
import '../services/notification_service.dart';

class NotificationsScreen extends StatefulWidget {
  const NotificationsScreen({super.key});

  @override
  State<NotificationsScreen> createState() => _NotificationsScreenState();
}

class _NotificationsScreenState extends State<NotificationsScreen> {
  final _service = NotificationService();
  List<NotificationLog> _logs = [];
  bool _loading = true;
  String _filter = 'ALL';

  @override
  void initState() {
    super.initState();
    _fetch();
  }

  Future<void> _fetch() async {
    setState(() => _loading = true);
    try {
      String? statusParam;
      if (_filter == 'SENT') statusParam = 'SENT';
      if (_filter == 'FAILED') statusParam = 'FAILED';
      final logs = await _service.getLogs(status: statusParam);
      setState(() {
        _logs = logs;
        _loading = false;
      });
    } catch (e) {
      setState(() => _loading = false);
    }
  }

  Future<void> _retry(String id) async {
    final c = context.colors;
    try {
      await _service.retry(id);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: const Text('✅ Renvoi programmé'), backgroundColor: c.success));
        _fetch();
      }
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: const Text('Erreur'), backgroundColor: c.danger));
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Scaffold(
      appBar: AppBar(
        title: Text('Notifications', style: GoogleFonts.inter(fontSize: 18, fontWeight: FontWeight.w800)),
        actions: [
          IconButton(onPressed: _fetch, icon: Icon(Icons.refresh, size: 18, color: c.text3)),
        ],
      ),
      body: Column(
        children: [
          // ── Filters ──
          SizedBox(
            height: 44,
            child: ListView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
              children: [
                _chipFilter(c, 'Tous (${_logs.length})', 'ALL'),
                const SizedBox(width: 6),
                _chipFilter(c, '✅ Envoyés (${_logs.where((l) => l.statut == 'SENT').length})', 'SENT'),
                const SizedBox(width: 6),
                _chipFilter(c, '❌ Échoués (${_logs.where((l) => l.statut == 'FAILED').length})', 'FAILED'),
              ],
            ),
          ),

          // ── List ──
          Expanded(
            child: _loading
              ? Center(child: CircularProgressIndicator(color: c.primary))
              : _logs.isEmpty
                ? Center(child: Column(mainAxisSize: MainAxisSize.min, children: [
                    Container(padding: const EdgeInsets.all(16), decoration: BoxDecoration(color: c.primarySoft, shape: BoxShape.circle, boxShadow: [BoxShadow(color: c.primary.withOpacity(0.3), blurRadius: 24, spreadRadius: 2)]), child: Icon(Icons.notifications_outlined, size: 40, color: c.primary)),
                    Text('Aucune notification', style: GoogleFonts.inter(fontWeight: FontWeight.w700, color: c.text)),
                  ]))
                : RefreshIndicator(
                    onRefresh: _fetch,
                    child: ListView.builder(
                      padding: const EdgeInsets.all(16),
                      itemCount: _logs.length,
                      itemBuilder: (_, i) => _buildLogCard(_logs[i], i),
                    ),
                  ),
          ),
        ],
      ),
    );
  }

  Widget _chipFilter(AppColorScheme c, String label, String value) {
    final selected = _filter == value;
    return GestureDetector(
      onTap: () { setState(() => _filter = value); _fetch(); },
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
        decoration: BoxDecoration(
          color: selected ? c.primarySoft : c.elevated,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: selected ? c.primary.withOpacity(0.3) : c.border),
        ),
        child: Text(label, style: GoogleFonts.inter(fontSize: 11, fontWeight: FontWeight.w600, color: selected ? c.primary : c.text3)),
      ),
    );
  }

  Widget _buildLogCard(NotificationLog log, int index) {
    final c = context.colors;
    final isFailed = log.statut == 'FAILED';
    final isSent = log.statut == 'SENT';

    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: c.surface,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: isFailed ? c.danger.withOpacity(0.2) : c.border),
        boxShadow: c.isDark ? [] : [BoxShadow(color: c.shadow, blurRadius: 6, offset: const Offset(0, 2))],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(children: [
            // Type badge
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
              decoration: BoxDecoration(
                color: log.type == 'EMAIL' ? c.canalEmail.withOpacity(0.1) : c.canalCall.withOpacity(0.1),
                borderRadius: BorderRadius.circular(6),
                border: Border.all(color: log.type == 'EMAIL' ? c.canalEmail.withOpacity(0.2) : c.canalCall.withOpacity(0.2)),
              ),
              child: Text(log.type, style: GoogleFonts.inter(fontSize: 9, fontWeight: FontWeight.w600, color: log.type == 'EMAIL' ? c.canalEmail : c.canalCall)),
            ),
            const SizedBox(width: 8),
            Expanded(child: Text(log.destinataire, style: GoogleFonts.inter(fontSize: 12, color: c.text2), overflow: TextOverflow.ellipsis)),
            // Status icon
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
              decoration: BoxDecoration(
                color: isSent ? c.successSoft : isFailed ? c.dangerSoft : c.warningSoft,
                borderRadius: BorderRadius.circular(6),
              ),
              child: Text(
                isSent ? '✓ Envoyé' : isFailed ? '✕ Échoué' : '⏳ En attente',
                style: GoogleFonts.inter(fontSize: 9, fontWeight: FontWeight.w600, color: isSent ? c.success : isFailed ? c.danger : c.warning),
              ),
            ),
          ]),
          const SizedBox(height: 6),
          Text(log.sujet, style: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.w600, color: c.text)),
          const SizedBox(height: 4),
          Row(children: [
            Text(_formatTime(log.createdAt), style: GoogleFonts.inter(fontSize: 9, color: c.text3)),
            if (log.attempts > 1) ...[
              const SizedBox(width: 8),
              Text('${log.attempts} tentatives', style: GoogleFonts.inter(fontSize: 9, color: c.text3)),
            ],
            const Spacer(),
            if (isFailed)
              GestureDetector(
                onTap: () => _retry(log.id),
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: c.primarySoft,
                    borderRadius: BorderRadius.circular(6),
                    border: Border.all(color: c.primary.withOpacity(0.2)),
                  ),
                  child: Text('↻ Retry', style: GoogleFonts.inter(fontSize: 10, fontWeight: FontWeight.w600, color: c.primary)),
                ),
              ),
          ]),
          if (isFailed && log.errorMessage != null) ...[
            const SizedBox(height: 4),
            Text(log.errorMessage!, style: GoogleFonts.inter(fontSize: 9, color: c.danger), maxLines: 2, overflow: TextOverflow.ellipsis),
          ],
        ],
      ),
    ).animate().fadeIn(delay: Duration(milliseconds: 50 * index));
  }

  String _formatTime(String dateStr) {
    try {
      final dt = DateTime.parse(dateStr);
      return '${dt.day}/${dt.month}/${dt.year} ${dt.hour.toString().padLeft(2, '0')}:${dt.minute.toString().padLeft(2, '0')}';
    } catch (_) { return dateStr; }
  }
}
