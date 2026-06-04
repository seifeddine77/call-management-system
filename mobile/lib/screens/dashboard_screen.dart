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
  List<OperatorPerformance> _operatorPerformances = [];
  List<SupervisionAlert> _supervisionAlerts = [];
  int _ticketPage = 0;
  int _ticketTotalPages = 1;
  String _canalFilter = 'ALL';
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _fetchData();
  }

  Future<void> _fetchTickets() async {
    try {
      final ticketFilter = TicketFilter(
        page: _ticketPage,
        size: 5,
        canal: _canalFilter != 'ALL' ? _canalFilter : null,
      );
      final ticketPage = await _ticketService.getTickets(ticketFilter);
      setState(() {
        _tickets = ticketPage.content;
        _ticketTotalPages = ticketPage.totalPages;
      });
    } catch (e) {
      debugPrint('[DashboardScreen] Fetch tickets error: $e');
    }
  }

  Future<void> _fetchData() async {
    setState(() { _loading = true; _error = null; });
    try {
      KpiResponse? kpis;
      try { kpis = await _analyticsService.getKpis(period: '30d'); } catch (_) {}
      
      final ticketFilter = TicketFilter(
        page: _ticketPage,
        size: 5,
        canal: _canalFilter != 'ALL' ? _canalFilter : null,
      );
      final ticketPage = await _ticketService.getTickets(ticketFilter);
      
      List<HourlyData> sparkData = [];
      try { sparkData = await _analyticsService.getTicketsPerDay(period: '30d'); } catch (_) {}

      List<OperatorPerformance> operatorPerformances = [];
      List<SupervisionAlert> supervisionAlerts = [];
      
      final user = Provider.of<AuthProvider>(context, listen: false).user;
      if (user != null) {
        try {
          operatorPerformances = await _analyticsService.getOperatorPerformance(period: '30d');
        } catch (_) {}
        if (user.isSupervisor) {
          try {
            supervisionAlerts = await _analyticsService.getSupervisionAlerts();
          } catch (_) {}
        }
      }

      setState(() {
        _kpis = kpis;
        _tickets = ticketPage.content;
        _ticketTotalPages = ticketPage.totalPages;
        _sparkData = sparkData;
        _operatorPerformances = operatorPerformances;
        _supervisionAlerts = supervisionAlerts;
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
            
            const SizedBox(height: 12),

            // Role-Based widgets (Leaderboard & Alerts vs Ma Performance)
            if (user?.isSupervisor == true) ...[
              _buildLeaderboardWidget(c),
              const SizedBox(height: 12),
              _buildSystemAlertsWidget(c),
            ] else ...[
              _buildOperatorPerformanceWidget(c, _operatorPerformances.where((op) => op.operateurId == user?.userId).firstOrNull ??
                OperatorPerformance(
                  operateurId: user?.userId ?? '',
                  operateurName: '${user?.prenom} ${user?.nom}',
                  ticketsTraites: 0,
                  ticketsEnCours: 0,
                  ticketsResolu: 0,
                  tauxResolution: 0.0,
                  tempsMoyenMinutes: 0.0,
                  ticketsCall: 0,
                  ticketsEmail: 0,
                  ticketsWeb: 0,
                  scoreComposite: 0,
                  slaCompliance: 0.0,
                )
              ),
            ],
          ],

          const SizedBox(height: 16),

          // ── Recent Tickets ──
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text('Derniers tickets', style: GoogleFonts.inter(
                fontSize: 14, fontWeight: FontWeight.w800, color: c.text,
              )),
              if (_ticketTotalPages > 1)
                Text('Page ${_ticketPage + 1} / $_ticketTotalPages', style: GoogleFonts.inter(fontSize: 10, color: c.text3)),
            ],
          ),
          const SizedBox(height: 8),

          // Canal Filter Tabs
          SizedBox(
            height: 38,
            child: ListView(
              scrollDirection: Axis.horizontal,
              children: ['ALL', 'CALL', 'EMAIL', 'WEB'].map((canal) {
                final isSelected = _canalFilter == canal;
                return Padding(
                  padding: const EdgeInsets.only(right: 6),
                  child: GestureDetector(
                    onTap: () {
                      setState(() {
                        _canalFilter = canal;
                        _ticketPage = 0;
                      });
                      _fetchTickets();
                    },
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                      decoration: BoxDecoration(
                        color: isSelected ? c.primarySoft : c.surface,
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(color: isSelected ? c.primary.withOpacity(0.3) : c.border),
                      ),
                      child: Text(
                        canal == 'ALL' ? 'Tous' : canal,
                        style: GoogleFonts.inter(
                          fontSize: 11,
                          fontWeight: FontWeight.w600,
                          color: isSelected ? c.primary : c.text3,
                        ),
                      ),
                    ),
                  ),
                );
              }).toList(),
            ),
          ),
          const SizedBox(height: 8),

          if (_tickets.isEmpty)
            _buildEmptyState(c)
          else ...[
            ..._tickets.asMap().entries.map((e) => _buildTicketItem(c, e.value, e.key)),
            
            // Pagination controls
            if (_ticketTotalPages > 1) ...[
              const SizedBox(height: 8),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  OutlinedButton(
                    onPressed: _ticketPage > 0
                        ? () {
                            setState(() => _ticketPage--);
                            _fetchTickets();
                          }
                        : null,
                    style: OutlinedButton.styleFrom(
                      side: BorderSide(color: c.border),
                      padding: const EdgeInsets.symmetric(horizontal: 16),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                    ),
                    child: Text('Précédent', style: GoogleFonts.inter(fontSize: 11, color: _ticketPage > 0 ? c.primary : c.text3)),
                  ),
                  OutlinedButton(
                    onPressed: _ticketPage < _ticketTotalPages - 1
                        ? () {
                            setState(() => _ticketPage++);
                            _fetchTickets();
                          }
                        : null,
                    style: OutlinedButton.styleFrom(
                      side: BorderSide(color: c.border),
                      padding: const EdgeInsets.symmetric(horizontal: 16),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                    ),
                    child: Text('Suivant', style: GoogleFonts.inter(fontSize: 11, color: _ticketPage < _ticketTotalPages - 1 ? c.primary : c.text3)),
                  ),
                ],
              ),
            ],
          ],
        ],
      ),
    );
  }

  Widget _buildOperatorPerformanceWidget(AppColorScheme c, OperatorPerformance perf) {
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
              Icon(Icons.stars, color: c.primary, size: 18),
              const SizedBox(width: 8),
              Text('MA PERFORMANCE', style: GoogleFonts.inter(
                fontSize: 10, fontWeight: FontWeight.w600, color: c.text3, letterSpacing: 1,
              )),
              const Spacer(),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: c.primarySoft,
                  borderRadius: BorderRadius.circular(6),
                  border: Border.all(color: c.primary.withOpacity(0.2)),
                ),
                child: Text('Score: ${perf.scoreComposite}', style: GoogleFonts.inter(fontSize: 10, fontWeight: FontWeight.w800, color: c.primary)),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: [
              _buildMiniPerfIndicator(c, 'Traités', perf.ticketsTraites.toString(), Icons.assignment_outlined),
              _buildMiniPerfIndicator(c, 'Résolus', perf.ticketsResolu.toString(), Icons.check_circle_outline),
              _buildMiniPerfIndicator(c, 'En cours', perf.ticketsEnCours.toString(), Icons.hourglass_empty_outlined),
            ],
          ),
          const SizedBox(height: 16),
          const Divider(height: 1),
          const SizedBox(height: 12),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text('Taux de Résolution', style: GoogleFonts.inter(fontSize: 12, color: c.text2)),
              Text('${perf.tauxResolution.toStringAsFixed(1)}%', style: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.w800, color: c.success)),
            ],
          ),
          const SizedBox(height: 8),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text('Temps Moyen', style: GoogleFonts.inter(fontSize: 12, color: c.text2)),
              Text('${perf.tempsMoyenMinutes.toStringAsFixed(0)} min', style: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.w800, color: c.text)),
            ],
          ),
          const SizedBox(height: 8),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text('Respect SLA', style: GoogleFonts.inter(fontSize: 12, color: c.text2)),
              Text('${perf.slaCompliance.toStringAsFixed(1)}%', style: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.w800, color: c.info)),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildMiniPerfIndicator(AppColorScheme c, String label, String value, IconData icon) {
    return Column(
      children: [
        Icon(icon, size: 18, color: c.text3),
        const SizedBox(height: 4),
        Text(value, style: GoogleFonts.inter(fontSize: 16, fontWeight: FontWeight.w900, color: c.text)),
        const SizedBox(height: 2),
        Text(label, style: GoogleFonts.inter(fontSize: 9, color: c.text3)),
      ],
    );
  }

  Widget _buildLeaderboardWidget(AppColorScheme c) {
    final leaderboard = List<OperatorPerformance>.from(_operatorPerformances)
      ..sort((a, b) => b.scoreComposite.compareTo(a.scoreComposite));
    final topOps = leaderboard.take(4).toList();

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
              Icon(Icons.leaderboard, color: c.primary, size: 18),
              const SizedBox(width: 8),
              Text('LEADERBOARD OPÉRATEURS', style: GoogleFonts.inter(
                fontSize: 10, fontWeight: FontWeight.w600, color: c.text3, letterSpacing: 1,
              )),
            ],
          ),
          const SizedBox(height: 12),
          if (topOps.isEmpty)
            Center(
              child: Padding(
                padding: const EdgeInsets.symmetric(vertical: 12),
                child: Text('Aucune donnée de performance', style: GoogleFonts.inter(fontSize: 11, color: c.text3)),
              ),
            )
          else
            ...topOps.asMap().entries.map((e) {
              final rank = e.key + 1;
              final op = e.value;
              final opName = op.operateurName ?? 'Opérateur ${op.operateurId.length > 5 ? op.operateurId.substring(0, 5) : op.operateurId}';
              final rankEmoji = rank == 1 ? '🥇' : rank == 2 ? '🥈' : rank == 3 ? '🥉' : '🎖️';
              return Padding(
                padding: const EdgeInsets.only(bottom: 10),
                child: Row(
                  children: [
                    SizedBox(
                      width: 24,
                      child: Text(rankEmoji, style: const TextStyle(fontSize: 14)),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(opName, style: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.w700, color: c.text)),
                          Text('${op.ticketsTraites} traités · ${op.tauxResolution.toStringAsFixed(0)}% résolu · ${op.tempsMoyenMinutes.toStringAsFixed(0)} min',
                            style: GoogleFonts.inter(fontSize: 9, color: c.text3)),
                        ],
                      ),
                    ),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                      decoration: BoxDecoration(
                        color: c.primarySoft,
                        borderRadius: BorderRadius.circular(6),
                      ),
                      child: Text(
                        'Score: ${op.scoreComposite}',
                        style: GoogleFonts.inter(fontSize: 10, fontWeight: FontWeight.w800, color: c.primary),
                      ),
                    ),
                  ],
                ),
              );
            }),
        ],
      ),
    );
  }

  Widget _buildSystemAlertsWidget(AppColorScheme c) {
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
              Icon(Icons.warning_amber_rounded, color: c.danger, size: 18),
              const SizedBox(width: 8),
              Text('ANOMALIES SYSTÈME', style: GoogleFonts.inter(
                fontSize: 10, fontWeight: FontWeight.w600, color: c.text3, letterSpacing: 1,
              )),
              const Spacer(),
              if (_supervisionAlerts.isNotEmpty)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(
                    color: c.dangerSoft,
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Text(
                    '${_supervisionAlerts.length} alerte${_supervisionAlerts.length > 1 ? 's' : ''}',
                    style: GoogleFonts.inter(fontSize: 9, fontWeight: FontWeight.w800, color: c.danger),
                  ),
                ),
            ],
          ),
          const SizedBox(height: 12),
          if (_supervisionAlerts.isEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 8),
              child: Row(
                children: [
                  Icon(Icons.check_circle, size: 16, color: c.success),
                  const SizedBox(width: 8),
                  Text('Tout est opérationnel, aucune anomalie.', style: GoogleFonts.inter(fontSize: 11, color: c.success)),
                ],
              ),
            )
          else
            ..._supervisionAlerts.take(3).map((a) {
              final isCritical = a.severity == 'CRITICAL' || a.severity == 'CRITIQUE';
              final color = isCritical ? c.danger : c.warning;
              return Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Icon(
                      isCritical ? Icons.error_outline : Icons.warning_amber_outlined,
                      size: 16,
                      color: color,
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(a.message, style: GoogleFonts.inter(fontSize: 11, fontWeight: FontWeight.w600, color: c.text)),
                          Text('Type: ${a.type} · Nombre: ${a.count}', style: GoogleFonts.inter(fontSize: 9, color: c.text3)),
                        ],
                      ),
                    ),
                  ],
                ),
              );
            }),
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
      (Icons.phone_outlined, 'Appels', _kpis!.ticketsCall, c.canalCall),
      (Icons.mail_outlined, 'Emails', _kpis!.ticketsEmail, c.canalEmail),
      (Icons.language_outlined, 'Web', _kpis!.ticketsWeb, c.canalWeb),
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
                Icon(ch.$1, size: 16, color: ch.$4),
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
            color: c.canalColor(t.canal).withOpacity(0.12),
            borderRadius: BorderRadius.circular(10),
          ),
          child: Center(
            child: Icon(
              t.canal == 'CALL'
                  ? Icons.phone_outlined
                  : t.canal == 'EMAIL'
                      ? Icons.mail_outlined
                      : Icons.language_outlined,
              size: 18,
              color: c.canalColor(t.canal),
            ),
          ),
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
          Icon(Icons.assignment_outlined, size: 36, color: c.text3.withOpacity(0.4)),
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
