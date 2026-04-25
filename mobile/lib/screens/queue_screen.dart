import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:provider/provider.dart';
import '../config/colors.dart';
import '../models/ticket.dart';
import '../services/ticket_service.dart';
import '../providers/auth_provider.dart';
import 'ticket_detail_screen.dart';

class QueueScreen extends StatefulWidget {
  const QueueScreen({super.key});

  @override
  State<QueueScreen> createState() => _QueueScreenState();
}

class _QueueScreenState extends State<QueueScreen> {
  final _service = TicketService();
  List<QueueEntry> _tickets = [];
  TicketStats? _stats;
  bool _loading = true;
  String? _assigning;

  @override
  void initState() {
    super.initState();
    _fetch();
  }

  Future<void> _fetch() async {
    setState(() => _loading = true);
    try {
      final results = await Future.wait([
        _service.getQueue(),
        _service.getStats(),
      ]);
      final queueData = results[0] as Map<String, dynamic>;
      setState(() {
        _tickets = (queueData['tickets'] as List?)?.map((t) => QueueEntry.fromJson(t)).toList() ?? [];
        _stats = results[1] as TicketStats;
        _loading = false;
      });
    } catch (e) {
      setState(() => _loading = false);
    }
  }

  Future<void> _handleAssign(QueueEntry entry) async {
    final user = context.read<AuthProvider>().user;
    if (user == null) return;
    setState(() => _assigning = entry.ticketId);
    try {
      await _service.assignTicket(entry.ticketId, user.userId);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: const Text('✅ Ticket pris en charge !'), backgroundColor: context.colors.success),
        );
        setState(() => _tickets.removeWhere((t) => t.ticketId == entry.ticketId));
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: const Text('❌ Impossible d\'assigner'), backgroundColor: context.colors.danger),
        );
      }
    } finally {
      if (mounted) setState(() => _assigning = null);
    }
  }

  String _timeSince(String dateStr) {
    try {
      final diff = DateTime.now().difference(DateTime.parse(dateStr));
      final mins = diff.inMinutes;
      if (mins < 60) return '${mins}min';
      return '${diff.inHours}h${mins % 60}m';
    } catch (_) {
      return '-';
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return RefreshIndicator(
      onRefresh: _fetch,
      color: c.primary,
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // ── Header ──
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text("File d'attente", style: GoogleFonts.inter(fontSize: 20, fontWeight: FontWeight.w800, color: c.text)),
                Text('${_tickets.length} ticket${_tickets.length > 1 ? 's' : ''} en attente',
                  style: GoogleFonts.inter(fontSize: 10, color: c.text3)),
              ]),
              IconButton(onPressed: _fetch, icon: Icon(Icons.refresh, size: 18, color: c.text3)),
            ],
          ),

          const SizedBox(height: 16),

          // ── Stats Row ──
          if (_stats != null)
            Row(
              children: [
                _statBox(c, 'Nouveaux', _stats!.ticketsNouveau, c.info),
                const SizedBox(width: 8),
                _statBox(c, 'En cours', _stats!.ticketsEnCours, c.warning),
                const SizedBox(width: 8),
                _statBox(c, 'Résolus', _stats!.ticketsResolu, c.success),
                const SizedBox(width: 8),
                _statBox(c, 'Fermés', _stats!.ticketsFerme, c.text3),
              ],
            ).animate().fadeIn().slideY(begin: 0.1),

          const SizedBox(height: 16),

          // ── Queue List ──
          if (_loading)
            Center(child: Padding(
              padding: const EdgeInsets.all(32),
              child: CircularProgressIndicator(color: c.primary),
            ))
          else if (_tickets.isEmpty)
            Container(
              padding: const EdgeInsets.all(40),
              decoration: BoxDecoration(
                color: c.surface,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: c.border),
              ),
              child: Column(children: [
                Container(padding: const EdgeInsets.all(16), decoration: BoxDecoration(color: c.success.withOpacity(0.1), shape: BoxShape.circle, boxShadow: [BoxShadow(color: c.success.withOpacity(0.3), blurRadius: 24, spreadRadius: 2)]), child: Icon(Icons.check_circle_outline, size: 40, color: c.success)),
                const SizedBox(height: 8),
                Text('File vide', style: GoogleFonts.inter(fontWeight: FontWeight.w700, color: c.text)),
                Text('Tous les tickets sont pris en charge !', style: GoogleFonts.inter(fontSize: 12, color: c.text3)),
              ]),
            )
          else
            ..._tickets.asMap().entries.map((e) => _buildQueueItem(e.value, e.key)),
        ],
      ),
    );
  }

  Widget _statBox(AppColorScheme c, String label, int value, Color color) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: c.surface,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: c.border),
          boxShadow: c.isDark ? [] : [BoxShadow(color: c.shadow, blurRadius: 6, offset: const Offset(0, 2))],
        ),
        child: Column(children: [
          Text(label.toUpperCase(), style: GoogleFonts.inter(fontSize: 8, fontWeight: FontWeight.w600, color: c.text3, letterSpacing: 0.5)),
          const SizedBox(height: 4),
          Text('$value', style: GoogleFonts.inter(fontSize: 22, fontWeight: FontWeight.w900, color: color)),
        ]),
      ),
    );
  }

  Widget _buildQueueItem(QueueEntry t, int index) {
    final c = context.colors;
    final isUrgent = t.priorite == 1;
    return GestureDetector(
      onTap: () {
        Navigator.of(context).push(
          MaterialPageRoute(builder: (_) => TicketDetailScreen(ticketId: t.ticketId)),
        );
      },
      child: Container(
      margin: const EdgeInsets.only(bottom: 8),
      decoration: BoxDecoration(
        color: c.surface,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: isUrgent ? c.danger.withOpacity(0.3) : c.border),
        boxShadow: c.isDark ? [] : [BoxShadow(color: c.shadow, blurRadius: 6, offset: const Offset(0, 2))],
      ),
      child: Stack(
        children: [
          if (isUrgent)
            Positioned(left: 0, top: 0, bottom: 0, child: Container(
              width: 3,
              decoration: BoxDecoration(
                color: c.danger,
                borderRadius: const BorderRadius.only(topLeft: Radius.circular(14), bottomLeft: Radius.circular(14)),
              ),
            )),
          Padding(
            padding: const EdgeInsets.all(14),
            child: Row(
              children: [
                // Position
                SizedBox(
                  width: 24,
                  child: Text('${t.position}', style: GoogleFonts.inter(fontSize: 11, color: c.text3), textAlign: TextAlign.center),
                ),
                const SizedBox(width: 8),

                // Canal icon
                Container(
                  width: 40, height: 40,
                  decoration: BoxDecoration(
                    color: c.canalColor(t.canal).withOpacity(0.1),
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(color: c.canalColor(t.canal).withOpacity(0.2)),
                  ),
                  child: Center(child: Text(
                    t.canal == 'CALL' ? '☎' : t.canal == 'EMAIL' ? '✉' : '⬡',
                    style: const TextStyle(fontSize: 16),
                  )),
                ),
                const SizedBox(width: 12),

                // Info
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(t.clientNom, style: GoogleFonts.inter(fontSize: 13, fontWeight: FontWeight.w600, color: c.text)),
                      Text(t.ticketNumero, style: GoogleFonts.inter(fontSize: 10, color: c.text3)),
                    ],
                  ),
                ),

                // Wait time
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: isUrgent ? c.dangerSoft : c.warningSoft,
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Text('⏱ ${_timeSince(t.createdAt)}', style: GoogleFonts.inter(
                    fontSize: 10, fontWeight: FontWeight.w600,
                    color: isUrgent ? c.danger : c.warning,
                  )),
                ),
                const SizedBox(width: 8),

                // Assign button
                GestureDetector(
                  onTap: _assigning == t.ticketId ? null : () => _handleAssign(t),
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                    decoration: BoxDecoration(
                      color: c.primarySoft,
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(color: c.primary.withOpacity(0.2)),
                    ),
                    child: Text(
                      _assigning == t.ticketId ? '...' : 'Prendre →',
                      style: GoogleFonts.inter(fontSize: 10, fontWeight: FontWeight.w600, color: c.primary),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    )).animate().fadeIn(delay: Duration(milliseconds: 80 * index)).slideX(begin: 0.05);
  }
}
