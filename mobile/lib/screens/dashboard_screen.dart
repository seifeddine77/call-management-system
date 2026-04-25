import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:shimmer/shimmer.dart';
import 'package:provider/provider.dart';
import '../config/colors.dart';
import '../providers/auth_provider.dart';
import '../models/analytics.dart';
import '../models/ticket.dart';
import '../services/analytics_service.dart';
import '../services/ticket_service.dart';
import '../widgets/stat_card.dart';
import 'ticket_detail_screen.dart';

class DashboardScreen extends StatefulWidget {
  const DashboardScreen({super.key});

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> {
  final _analyticsService = AnalyticsService();
  final _ticketService = TicketService();

  KpiResponse? _kpis;
  List<Ticket> _tickets = [];
  List<HourlyData> _sparkData = [];
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _fetchData();
  }

  Future<void> _fetchData() async {
    setState(() { _loading = true; _error = null; });
    try {
      KpiResponse? kpis;
      try { kpis = await _analyticsService.getKpis(period: '30d'); } catch (_) {}
      final ticketPage = await _ticketService.getTickets(TicketFilter(page: 0, size: 5));
      List<HourlyData> sparkData = [];
      try { sparkData = await _analyticsService.getTicketsPerDay(period: '30d'); } catch (_) {}
      setState(() {
        _kpis = kpis;
        _tickets = ticketPage.content;
        _sparkData = sparkData;
        _loading = false;
      });
    } catch (e) {
      setState(() { _error = e.toString(); _loading = false; });
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final user = context.read<AuthProvider>().user;
    final hour = DateTime.now().hour;
    final greeting = hour < 12 ? 'Bonjour' : hour < 18 ? 'Bon après-midi' : 'Bonsoir';
    final firstName = user?.prenom ?? 'Utilisateur';

    return RefreshIndicator(
      onRefresh: _fetchData,
      color: c.primary,
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // ── Error State ──
          if (_error != null)
            Container(
              padding: const EdgeInsets.all(12),
              margin: const EdgeInsets.only(bottom: 12),
              decoration: BoxDecoration(
                color: c.dangerSoft,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: c.danger.withOpacity(0.2)),
              ),
              child: Text(_error!, style: GoogleFonts.inter(fontSize: 12, color: c.danger)),
            ),

          // ── Welcome Banner ──
          Container(
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              gradient: c.gradientCard,
              borderRadius: BorderRadius.circular(20),
              border: Border.all(color: c.border),
              boxShadow: c.isDark ? [] : [BoxShadow(color: c.shadow, blurRadius: 16, offset: const Offset(0, 4))],
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('$greeting, $firstName 👋', style: GoogleFonts.inter(
                  fontSize: 20, fontWeight: FontWeight.w800, color: c.text,
                  letterSpacing: -0.5,
                )),
                const SizedBox(height: 4),
                Text(
                  _formatDate(DateTime.now()),
                  style: GoogleFonts.inter(fontSize: 12, color: c.text2),
                ),
                if (_kpis != null) ...[
                  const SizedBox(height: 12),
                  _buildAiInsight(c),
                ],
              ],
            ),
          ).animate().fadeIn().slideY(begin: 0.1, duration: 400.ms),

          const SizedBox(height: 16),

          // ── KPI Cards ──
          if (_loading)
            ..._buildShimmers(c)
          else if (_kpis != null) ...[
            // Total Tickets
            StatCard(
              icon: Icons.confirmation_number_outlined,
              iconColor: c.primary,
              label: 'Total Tickets',
              value: _kpis!.totalTickets.toString(),
              sparkData: _sparkData.map((d) => d.count.toDouble()).toList(),
            ).animate().fadeIn(delay: 100.ms).slideX(begin: -0.05),

            const SizedBox(height: 12),

            Row(
              children: [
                // Resolution Rate
                Expanded(
                  child: StatCard(
                    icon: Icons.check_circle_outline,
                    iconColor: c.success,
                    label: 'Résolution',
                    value: '${_kpis!.tauxResolution.toStringAsFixed(1)}%',
                    ringValue: _kpis!.tauxResolution / 100,
                  ).animate().fadeIn(delay: 200.ms).slideX(begin: -0.05),
                ),
                const SizedBox(width: 12),
                // Average Time
                Expanded(
                  child: StatCard(
                    icon: Icons.timer_outlined,
                    iconColor: c.warning,
                    label: 'Temps moyen',
                    value: '${_kpis!.tempsMoyenMinutes.toStringAsFixed(0)}min',
                  ).animate().fadeIn(delay: 300.ms).slideX(begin: 0.05),
                ),
              ],
            ),

            const SizedBox(height: 12),

            // Canal breakdown
            _buildCanalBreakdown(c),

            const SizedBox(height: 12),

            // AI Decisions
            _buildDecisionDonut(c),
          ],

          const SizedBox(height: 16),

          // ── Recent Tickets ──
          Text('Derniers tickets', style: GoogleFonts.inter(
            fontSize: 14, fontWeight: FontWeight.w800, color: c.text,
          )),
          const SizedBox(height: 8),

          if (_tickets.isEmpty)
            _buildEmptyState(c)
          else
            ..._tickets.asMap().entries.map((e) => _buildTicketItem(c, e.value, e.key)),
        ],
      ),
    );
  }

  Widget _buildAiInsight(AppColorScheme c) {
    final perf = _kpis!.tauxResolution >= 90 ? 'excellente 🟢' : _kpis!.tauxResolution >= 70 ? 'bonne 🟡' : 'à améliorer 🔴';
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: c.primarySoft,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: c.primary.withOpacity(0.1)),
      ),
      child: Row(
        children: [
          Icon(Icons.auto_awesome, size: 14, color: c.primary),
          const SizedBox(width: 8),
          Expanded(
            child: Text.rich(
              TextSpan(children: [
                TextSpan(text: 'Aurora IA', style: GoogleFonts.inter(fontWeight: FontWeight.w700, fontSize: 11, color: c.primary)),
                TextSpan(text: ' — ${_kpis!.totalTickets} tickets · ${_kpis!.tauxResolution.toStringAsFixed(1)}% résolution · Performance $perf',
                  style: GoogleFonts.inter(fontSize: 11, color: c.text2)),
              ]),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildCanalBreakdown(AppColorScheme c) {
    final channels = [
      ('📞', 'Appels', _kpis!.ticketsCall, c.canalCall),
      ('📧', 'Emails', _kpis!.ticketsEmail, c.canalEmail),
      ('🌐', 'Web', _kpis!.ticketsWeb, c.canalWeb),
    ];
    final total = _kpis!.totalTickets > 0 ? _kpis!.totalTickets : 1;

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
          Text('RÉPARTITION PAR CANAL', style: GoogleFonts.inter(
            fontSize: 10, fontWeight: FontWeight.w600, color: c.text3, letterSpacing: 1,
          )),
          const SizedBox(height: 12),
          ...channels.map((ch) {
            final pct = (ch.$3 / total) * 100;
            return Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Row(children: [
                Text(ch.$1, style: const TextStyle(fontSize: 14)),
                const SizedBox(width: 8),
                SizedBox(width: 48, child: Text(ch.$2, style: GoogleFonts.inter(fontSize: 12, color: c.text2))),
                const SizedBox(width: 8),
                Expanded(
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(4),
                    child: LinearProgressIndicator(
                      value: pct / 100,
                      backgroundColor: c.muted,
                      valueColor: AlwaysStoppedAnimation(ch.$4),
                      minHeight: 6,
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                SizedBox(width: 24, child: Text('${ch.$3}', style: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.w600, color: c.text2), textAlign: TextAlign.right)),
              ]),
            );
          }),
        ],
      ),
    ).animate().fadeIn(delay: 400.ms);
  }

  Widget _buildDecisionDonut(AppColorScheme c) {
    final total = _kpis!.totalDecisions > 0 ? _kpis!.totalDecisions : 1;
    final items = [
      ('Favorable', _kpis!.decisionsAiFavorable, c.success),
      ('Défavorable', _kpis!.decisionsAiDefavorable, c.danger),
      ('Étude', _kpis!.decisionsAiEtude, c.warning),
    ];

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
          Text('DÉCISIONS IA', style: GoogleFonts.inter(
            fontSize: 10, fontWeight: FontWeight.w600, color: c.text3, letterSpacing: 1,
          )),
          const SizedBox(height: 12),
          ...items.map((d) => Padding(
            padding: const EdgeInsets.only(bottom: 6),
            child: Row(children: [
              Container(width: 8, height: 8, decoration: BoxDecoration(color: d.$3, shape: BoxShape.circle)),
              const SizedBox(width: 8),
              Expanded(child: Text(d.$1, style: GoogleFonts.inter(fontSize: 12, color: c.text2))),
              Text('${d.$2}', style: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.w700, color: c.text)),
              const SizedBox(width: 4),
              Text('(${((d.$2 / total) * 100).toStringAsFixed(0)}%)', style: GoogleFonts.inter(fontSize: 10, color: c.text3)),
            ]),
          )),
        ],
      ),
    ).animate().fadeIn(delay: 500.ms);
  }

  Widget _buildTicketItem(AppColorScheme c, Ticket t, int index) {
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      decoration: BoxDecoration(
        color: c.surface,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: c.border),
        boxShadow: c.isDark ? [] : [BoxShadow(color: c.shadow, blurRadius: 6, offset: const Offset(0, 2))],
      ),
      child: ListTile(
        contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
        leading: Container(
          width: 36, height: 36,
          decoration: BoxDecoration(
            color: c.canalColor(t.canal).withOpacity(0.1),
            borderRadius: BorderRadius.circular(10),
          ),
          child: Center(child: Text(
            t.canal == 'CALL' ? '📞' : t.canal == 'EMAIL' ? '📧' : '🌐',
            style: const TextStyle(fontSize: 16),
          )),
        ),
        title: Text(t.clientNom, style: GoogleFonts.inter(fontSize: 13, fontWeight: FontWeight.w600, color: c.text)),
        subtitle: Hero(tag: 'ticket-num-${t.id}', child: Material(type: MaterialType.transparency, child: Text(t.numero, style: GoogleFonts.inter(fontSize: 10, color: c.primary, fontWeight: FontWeight.w600)))),
        trailing: Container(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
          decoration: BoxDecoration(
            color: c.statutColor(t.statut).withOpacity(0.1),
            borderRadius: BorderRadius.circular(6),
            border: Border.all(color: c.statutColor(t.statut).withOpacity(0.2)),
          ),
          child: Text(t.statut.replaceAll('_', ' '), style: GoogleFonts.inter(
            fontSize: 10, fontWeight: FontWeight.w600, color: c.statutColor(t.statut),
          )),
        ),
        onTap: () {
          Navigator.push(context, MaterialPageRoute(builder: (_) => TicketDetailScreen(ticketId: t.id)));
        },
      ),
    ).animate().fadeIn(delay: Duration(milliseconds: 100 * index)).slideX(begin: 0.05);
  }

  List<Widget> _buildShimmers(AppColorScheme c) {
    return List.generate(3, (i) => Shimmer.fromColors(
      baseColor: c.isDark ? c.surface : Colors.grey[200]!,
      highlightColor: c.isDark ? c.elevated : Colors.white,
      child: Container(
        height: 80,
        margin: const EdgeInsets.only(bottom: 12),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(16),
        ),
      ),
    ));
  }

  Widget _buildEmptyState(AppColorScheme c) {
    return Container(
      padding: const EdgeInsets.all(32),
      decoration: BoxDecoration(
        color: c.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: c.border),
      ),
      child: Column(
        children: [
          const Text('📋', style: TextStyle(fontSize: 32)),
          const SizedBox(height: 8),
          Text('Aucun ticket', style: GoogleFonts.inter(fontSize: 14, fontWeight: FontWeight.w700, color: c.text)),
          Text('Les tickets apparaîtront ici', style: GoogleFonts.inter(fontSize: 12, color: c.text3)),
        ],
      ),
    );
  }

  String _formatDate(DateTime dt) {
    const days = ['lundi', 'mardi', 'mercredi', 'jeudi', 'vendredi', 'samedi', 'dimanche'];
    const months = ['janvier', 'février', 'mars', 'avril', 'mai', 'juin', 'juillet', 'août', 'septembre', 'octobre', 'novembre', 'décembre'];
    return '${days[dt.weekday - 1]} ${dt.day} ${months[dt.month - 1]} ${dt.year}';
  }
}
