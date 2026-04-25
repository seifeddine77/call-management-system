import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:fl_chart/fl_chart.dart';
import '../config/colors.dart';
import '../models/analytics.dart';
import '../services/analytics_service.dart';

/// AnalyticsScreen — redesigned to match dashboard design system
class AnalyticsScreen extends StatefulWidget {
  const AnalyticsScreen({super.key});

  @override
  State<AnalyticsScreen> createState() => _AnalyticsScreenState();
}

class _AnalyticsScreenState extends State<AnalyticsScreen> {
  final _service = AnalyticsService();

  KpiResponse? _kpis;
  KpiComparison? _comparison;
  List<DailyStat> _daily = [];
  bool _loading = true;
  String? _error;
  String _period = 'month';

  static const _periodLabels = {
    'week': '7 derniers jours',
    'month': '30 derniers jours',
    'year': '90 derniers jours',
  };

  @override
  void initState() {
    super.initState();
    _fetch();
  }

  Future<void> _fetch() async {
    setState(() { _loading = true; _error = null; });
    try {
      final results = await Future.wait([
        _service.getComparison(period: _period),
        _service.getDailyStats(period: _period),
      ]);
      setState(() {
        _comparison = results[0] as KpiComparison;
        _kpis = _comparison!.current;
        _daily = results[1] as List<DailyStat>;
        _loading = false;
      });
    } catch (e) {
      setState(() { _loading = false; _error = e.toString(); });
    }
  }

  String _fmtDelta(double delta) {
    return '${delta >= 0 ? '+' : ''}${delta.toStringAsFixed(0)}%';
  }

  @override
  Widget build(BuildContext context) {
    final c = context.colors;

    return Scaffold(
      backgroundColor: c.background,
      body: RefreshIndicator(
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
                Text('Analytique', style: GoogleFonts.inter(
                  fontSize: 20, fontWeight: FontWeight.w800, color: c.text,
                )),
                const SizedBox(height: 2),
                Text('📅 ${_periodLabels[_period] ?? _period}',
                    style: GoogleFonts.inter(fontSize: 11, color: c.text3)),
              ]),
              Row(children: [
                ..._buildPeriodChips(c),
                const SizedBox(width: 4),
                IconButton(
                  icon: Icon(Icons.refresh, size: 18, color: c.text3),
                  onPressed: _fetch,
                  tooltip: 'Actualiser',
                ),
              ]),
            ],
          ).animate().fadeIn(),

          const SizedBox(height: 16),

          if (_error != null)
            _buildError(c)
          else if (_loading)
            Center(child: Padding(
              padding: const EdgeInsets.all(40),
              child: CircularProgressIndicator(color: c.primary),
            ))
          else if (_kpis != null) ...[
            // ═══ 1. KPI Cards ═══
            _buildKpiCards(c),
            const SizedBox(height: 16),

            // ═══ 2. Status Breakdown ═══
            _buildStatusBreakdown(c),
            const SizedBox(height: 16),

            // ═══ 3. Daily Volume Chart ═══
            _buildDailyVolumeChart(c),
            const SizedBox(height: 16),

            // ═══ 4. Decisions per Day ═══
            _buildDecisionsPerDayChart(c),
            const SizedBox(height: 16),

            // ═══ 5. Canal Breakdown ═══
            _buildCanalBreakdown(c),
            const SizedBox(height: 16),

            // ═══ 6. Decisions Donut ═══
            _buildDecisionsDonut(c),
            const SizedBox(height: 16),

            // ═══ 7. Canal per Day ═══
            _buildCanalPerDayChart(c),

            const SizedBox(height: 16),
          ],
        ],
      ),
      ),
    );
  }

  // ──────────────────────────────────────────
  //  Period chips — matches dashboard style
  // ──────────────────────────────────────────
  List<Widget> _buildPeriodChips(AppColorScheme c) {
    return [
      ('Sem', 'week'),
      ('Mois', 'month'),
      ('An', 'year'),
    ].map((e) {
      final selected = _period == e.$2;
      return Padding(
        padding: const EdgeInsets.only(left: 4),
        child: GestureDetector(
          onTap: () { setState(() => _period = e.$2); _fetch(); },
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
            decoration: BoxDecoration(
              color: selected ? c.primarySoft : c.surface,
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: selected ? c.primary.withOpacity(0.3) : c.border),
            ),
            child: Text(e.$1, style: GoogleFonts.inter(
              fontSize: 11, fontWeight: FontWeight.w600,
              color: selected ? c.primary : c.text3,
            )),
          ),
        ),
      );
    }).toList();
  }

  // ──────────────────────────────────────────
  //  Error state
  // ──────────────────────────────────────────
  Widget _buildError(AppColorScheme c) {
    return _card(c, child: Column(children: [
      const Text('⚠️', style: TextStyle(fontSize: 32)),
      const SizedBox(height: 8),
      Text('Erreur de chargement', style: GoogleFonts.inter(fontWeight: FontWeight.w700, color: c.text)),
      Text(_error ?? '', style: GoogleFonts.inter(fontSize: 11, color: c.text3)),
      const SizedBox(height: 12),
      ElevatedButton.icon(
        onPressed: _fetch,
        icon: const Icon(Icons.refresh, size: 14),
        label: const Text('Réessayer'),
        style: ElevatedButton.styleFrom(backgroundColor: c.primary, foregroundColor: Colors.white),
      ),
    ]));
  }

  // ──────────────────────────────────────────
  //  1. KPI Cards — uses same pattern as StatCard
  // ──────────────────────────────────────────
  Widget _buildKpiCards(AppColorScheme c) {
    final kpis = _kpis!;
    final comp = _comparison;
    final totalDecisions = kpis.totalDecisions;
    final scoreMoyen = totalDecisions > 0
        ? (kpis.decisionsAiFavorable / totalDecisions * 100).round()
        : 0;

    return Column(children: [
      // Row 1 : Total + Résolution
      Row(children: [
        Expanded(child: _kpiCard(c,
          icon: Icons.confirmation_number_outlined,
          iconColor: c.primary,
          label: 'Total tickets',
          value: kpis.totalTickets.toString(),
          trend: comp != null ? _Trend(_fmtDelta(comp.deltaTickets), comp.deltaTickets > 0 ? true : (comp.deltaTickets < 0 ? false : null)) : null,
        )),
        const SizedBox(width: 12),
        Expanded(child: _kpiCard(c,
          icon: Icons.check_circle_outline,
          iconColor: c.success,
          label: 'Résolution',
          value: '${kpis.tauxResolution.toStringAsFixed(1)}%',
          trend: comp != null ? _Trend(_fmtDelta(comp.deltaResolution), comp.deltaResolution > 0 ? true : (comp.deltaResolution < 0 ? false : null)) : null,
        )),
      ]).animate().fadeIn(),

      const SizedBox(height: 12),

      // Row 2 : Temps moy + Décisions IA
      Row(children: [
        Expanded(child: _kpiCard(c,
          icon: Icons.timer_outlined,
          iconColor: c.warning,
          label: 'Temps moy.',
          value: '${kpis.tempsMoyenMinutes.toStringAsFixed(0)}min',
          trend: comp != null && comp.deltaTempsMoyen != 0
              ? _Trend(_fmtDelta(comp.deltaTempsMoyen), comp.deltaTempsMoyen < 0 ? true : false)
              : null,
        )),
        const SizedBox(width: 12),
        Expanded(child: _kpiCard(c,
          icon: Icons.auto_awesome,
          iconColor: c.secondary,
          label: 'Décisions IA',
          value: totalDecisions.toString(),
          trend: comp != null ? _Trend(_fmtDelta(comp.deltaDecisions), comp.deltaDecisions > 0 ? true : (comp.deltaDecisions < 0 ? false : null)) : null,
        )),
      ]).animate().fadeIn(delay: 60.ms),

      const SizedBox(height: 12),

      // Row 3 : Score favorable
      _kpiCard(c,
        icon: Icons.emoji_events_outlined,
        iconColor: c.warning,
        label: 'Score favorable',
        value: '$scoreMoyen%',
        trend: _Trend(
          scoreMoyen >= 80 ? 'Excellent' : scoreMoyen >= 60 ? 'Bon' : scoreMoyen >= 40 ? 'Moyen' : 'Faible',
          scoreMoyen >= 60 ? true : (scoreMoyen < 40 ? false : null),
        ),
      ).animate().fadeIn(delay: 120.ms),
    ]);
  }

  Widget _kpiCard(AppColorScheme c, {
    required IconData icon,
    required Color iconColor,
    required String label,
    required String value,
    _Trend? trend,
  }) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: c.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: c.border),
        boxShadow: c.isDark ? [] : [BoxShadow(color: c.shadow, blurRadius: 8, offset: const Offset(0, 2))],
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        // Icon + Label
        Row(children: [
          Container(
            width: 32, height: 32,
            decoration: BoxDecoration(
              color: iconColor.withOpacity(0.1),
              borderRadius: BorderRadius.circular(8),
            ),
            child: Icon(icon, size: 16, color: iconColor),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Text(label.toUpperCase(), style: GoogleFonts.inter(
              fontSize: 9, fontWeight: FontWeight.w600, color: c.text3, letterSpacing: 0.8,
            ), overflow: TextOverflow.ellipsis),
          ),
        ]),
        const SizedBox(height: 10),
        // Value + Trend
        Row(crossAxisAlignment: CrossAxisAlignment.baseline, textBaseline: TextBaseline.alphabetic, children: [
          Text(value, style: GoogleFonts.inter(
            fontSize: 22, fontWeight: FontWeight.w900, color: c.text, letterSpacing: -0.5,
          )),
          if (trend != null) ...[
            const SizedBox(width: 8),
            if (trend.trendUp != null)
              Row(mainAxisSize: MainAxisSize.min, children: [
                Icon(
                  trend.trendUp! ? Icons.trending_up : Icons.trending_down,
                  size: 12,
                  color: trend.trendUp! ? c.success : c.danger,
                ),
                if (trend.label != null)
                  Text(' ${trend.label}', style: GoogleFonts.inter(
                    fontSize: 10, fontWeight: FontWeight.w600,
                    color: trend.trendUp! ? c.success : c.danger,
                  )),
              ])
            else if (trend.label != null)
              Row(mainAxisSize: MainAxisSize.min, children: [
                Icon(Icons.remove, size: 10, color: c.text3),
                Text(' ${trend.label}', style: GoogleFonts.inter(fontSize: 10, color: c.text3)),
              ]),
          ],
        ]),
      ]),
    );
  }

  // ──────────────────────────────────────────
  //  2. Status Breakdown
  // ──────────────────────────────────────────
  Widget _buildStatusBreakdown(AppColorScheme c) {
    final kpis = _kpis!;
    final total = kpis.totalTickets > 0 ? kpis.totalTickets : 1;
    final items = [
      (Icons.fiber_new, 'Nouveaux', kpis.ticketsNouveau, c.info),
      (Icons.sync, 'En cours', kpis.ticketsEnCours, c.warning),
      (Icons.check_circle, 'Résolus', kpis.ticketsResolu, c.success),
      (Icons.archive_outlined, 'Fermés', kpis.ticketsFerme, c.text3),
    ];

    return _card(c, title: 'RÉPARTITION PAR STATUT', child:
      Column(children: items.map((s) {
        final pct = s.$3 / total;
        return Padding(
          padding: const EdgeInsets.only(bottom: 10),
          child: Row(children: [
            Icon(s.$1, size: 16, color: s.$4),
            const SizedBox(width: 8),
            SizedBox(width: 64, child: Text(s.$2, style: GoogleFonts.inter(fontSize: 12, color: c.text2))),
            const SizedBox(width: 8),
            Expanded(
              child: ClipRRect(
                borderRadius: BorderRadius.circular(4),
                child: TweenAnimationBuilder<double>(
                  tween: Tween(begin: 0, end: pct),
                  duration: const Duration(milliseconds: 800),
                  curve: Curves.easeOutCubic,
                  builder: (ctx, val, _) => LinearProgressIndicator(
                    value: val, backgroundColor: c.muted,
                    valueColor: AlwaysStoppedAnimation(s.$4), minHeight: 6,
                  ),
                ),
              ),
            ),
            const SizedBox(width: 8),
            SizedBox(width: 28, child: Text('${s.$3}', style: GoogleFonts.inter(
              fontSize: 12, fontWeight: FontWeight.w700, color: c.text,
            ), textAlign: TextAlign.right)),
            const SizedBox(width: 4),
            SizedBox(width: 40, child: Text('(${(pct * 100).toStringAsFixed(1)}%)', style: GoogleFonts.inter(
              fontSize: 9, color: c.text3,
            ))),
          ]),
        );
      }).toList()),
    ).animate().fadeIn(delay: 180.ms);
  }

  // ──────────────────────────────────────────
  //  3. Daily Volume Bar Chart
  // ──────────────────────────────────────────
  Widget _buildDailyVolumeChart(AppColorScheme c) {
    if (_daily.isEmpty) return _emptyChart(c, 'VOLUME QUOTIDIEN');
    final sorted = ([..._daily]..sort((a, b) => a.date.compareTo(b.date))).take(14).toList();
    final maxVal = sorted.map((d) => d.totalTickets).reduce(math.max).toDouble();

    return _card(c, title: 'VOLUME QUOTIDIEN', child:
      SizedBox(
        height: 160,
        child: BarChart(BarChartData(
          alignment: BarChartAlignment.spaceAround,
          maxY: maxVal > 0 ? maxVal * 1.2 : 10,
          gridData: FlGridData(
            show: true, drawVerticalLine: false,
            horizontalInterval: maxVal > 0 ? maxVal / 4 : 2,
            getDrawingHorizontalLine: (v) => FlLine(color: c.borderSubtle, strokeWidth: 0.5),
          ),
          titlesData: FlTitlesData(
            leftTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
            rightTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
            topTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
            bottomTitles: AxisTitles(sideTitles: SideTitles(showTitles: true, reservedSize: 20,
              getTitlesWidget: (v, _) {
                final idx = v.toInt();
                if (idx >= 0 && idx < sorted.length && idx % 3 == 0) {
                  return Text(_fmtDate(sorted[idx].date),
                      style: GoogleFonts.inter(fontSize: 8, color: c.text3));
                }
                return const SizedBox();
              },
            )),
          ),
          barTouchData: BarTouchData(
            touchTooltipData: BarTouchTooltipData(
              getTooltipItem: (group, gIdx, rod, rIdx) {
                final d = sorted[group.x.toInt()];
                return BarTooltipItem('${d.totalTickets}',
                  GoogleFonts.inter(fontSize: 10, fontWeight: FontWeight.w700, color: Colors.white));
              },
            ),
          ),
          barGroups: sorted.asMap().entries.map((e) {
            final opacity = 0.4 + (e.key / sorted.length) * 0.6;
            return BarChartGroupData(x: e.key, barRods: [
              BarChartRodData(toY: e.value.totalTickets.toDouble(),
                  color: c.primary.withOpacity(opacity), width: 10,
                  borderRadius: BorderRadius.circular(3)),
            ]);
          }).toList(),
          borderData: FlBorderData(show: false),
        )),
      ),
    ).animate().fadeIn(delay: 240.ms);
  }

  // ──────────────────────────────────────────
  //  4. Decisions per Day — Stacked
  // ──────────────────────────────────────────
  Widget _buildDecisionsPerDayChart(AppColorScheme c) {
    if (_daily.isEmpty) return _emptyChart(c, 'DÉCISIONS PAR JOUR');
    final sorted = ([..._daily]..sort((a, b) => a.date.compareTo(b.date))).take(14).toList();
    final maxVal = sorted.map((d) => d.decisionsFavorable + d.decisionsDefavorable + d.decisionsEtude).reduce(math.max).toDouble();

    return _card(c, child:
      Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
          Text('DÉCISIONS PAR JOUR', style: GoogleFonts.inter(
            fontSize: 10, fontWeight: FontWeight.w600, color: c.text3, letterSpacing: 1,
          )),
          Row(children: [
            _legendDot(c.success, 'Fav'),
            const SizedBox(width: 6),
            _legendDot(c.danger, 'Déf'),
            const SizedBox(width: 6),
            _legendDot(c.warning, 'Étu'),
          ]),
        ]),
        const SizedBox(height: 12),
        SizedBox(
          height: 160,
          child: BarChart(BarChartData(
            alignment: BarChartAlignment.spaceAround,
            maxY: maxVal > 0 ? maxVal * 1.2 : 10,
            gridData: FlGridData(show: false),
            titlesData: FlTitlesData(
              leftTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
              rightTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
              topTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
              bottomTitles: AxisTitles(sideTitles: SideTitles(showTitles: true, reservedSize: 20,
                getTitlesWidget: (v, _) {
                  final idx = v.toInt();
                  if (idx >= 0 && idx < sorted.length && idx % 3 == 0) {
                    return Text(_fmtDate(sorted[idx].date),
                        style: GoogleFonts.inter(fontSize: 8, color: c.text3));
                  }
                  return const SizedBox();
                },
              )),
            ),
            barGroups: sorted.asMap().entries.map((e) {
              final d = e.value;
              return BarChartGroupData(x: e.key, barRods: [
                BarChartRodData(
                  toY: (d.decisionsFavorable + d.decisionsDefavorable + d.decisionsEtude).toDouble(),
                  width: 10,
                  borderRadius: const BorderRadius.only(topLeft: Radius.circular(3), topRight: Radius.circular(3)),
                  rodStackItems: [
                    BarChartRodStackItem(0, d.decisionsFavorable.toDouble(), c.success),
                    BarChartRodStackItem(d.decisionsFavorable.toDouble(),
                        (d.decisionsFavorable + d.decisionsDefavorable).toDouble(), c.danger),
                    BarChartRodStackItem((d.decisionsFavorable + d.decisionsDefavorable).toDouble(),
                        (d.decisionsFavorable + d.decisionsDefavorable + d.decisionsEtude).toDouble(), c.warning),
                  ],
                  color: Colors.transparent,
                ),
              ]);
            }).toList(),
            borderData: FlBorderData(show: false),
          )),
        ),
      ]),
    ).animate().fadeIn(delay: 300.ms);
  }

  // ──────────────────────────────────────────
  //  5. Canal Breakdown
  // ──────────────────────────────────────────
  Widget _buildCanalBreakdown(AppColorScheme c) {
    final kpis = _kpis!;
    final total = kpis.totalTickets > 0 ? kpis.totalTickets : 1;
    final channels = [
      (Icons.phone, 'Appels', kpis.ticketsCall, c.canalCall),
      (Icons.email_outlined, 'Emails', kpis.ticketsEmail, c.canalEmail),
      (Icons.language, 'Web', kpis.ticketsWeb, c.canalWeb),
    ];

    return _card(c, title: 'RÉPARTITION PAR CANAL', child:
      Column(children: channels.map((ch) {
        final pct = ch.$3 / total;
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
                  value: pct, backgroundColor: c.muted,
                  valueColor: AlwaysStoppedAnimation(ch.$4), minHeight: 6,
                ),
              ),
            ),
            const SizedBox(width: 8),
            SizedBox(width: 24, child: Text('${ch.$3}', style: GoogleFonts.inter(
              fontSize: 12, fontWeight: FontWeight.w700, color: c.text,
            ), textAlign: TextAlign.right)),
            const SizedBox(width: 4),
            SizedBox(width: 40, child: Text('(${(pct * 100).toStringAsFixed(1)}%)', style: GoogleFonts.inter(
              fontSize: 9, color: c.text3,
            ))),
          ]),
        );
      }).toList()),
    ).animate().fadeIn(delay: 360.ms);
  }

  // ──────────────────────────────────────────
  //  6. Decisions Donut
  // ──────────────────────────────────────────
  Widget _buildDecisionsDonut(AppColorScheme c) {
    final kpis = _kpis!;
    final total = kpis.totalDecisions > 0 ? kpis.totalDecisions : 1;
    final data = [
      ('Favorable', kpis.decisionsAiFavorable, c.success),
      ('Défavorable', kpis.decisionsAiDefavorable, c.danger),
      ('Étude', kpis.decisionsAiEtude, c.warning),
    ];

    return _card(c, title: 'RÉPARTITION DÉCISIONS', child:
      Row(children: [
        SizedBox(
          width: 100, height: 100,
          child: Stack(alignment: Alignment.center, children: [
            PieChart(PieChartData(
              sectionsSpace: 2,
              centerSpaceRadius: 28,
              sections: data.map((d) => PieChartSectionData(
                value: d.$2.toDouble(),
                color: d.$3,
                radius: 18,
                showTitle: false,
              )).toList(),
            )),
            Column(mainAxisSize: MainAxisSize.min, children: [
              Text('${kpis.totalDecisions}', style: GoogleFonts.inter(
                fontSize: 14, fontWeight: FontWeight.w900, color: c.text,
              )),
              Text('TOTAL', style: GoogleFonts.inter(
                fontSize: 7, fontWeight: FontWeight.w600, color: c.text3, letterSpacing: 0.5,
              )),
            ]),
          ]),
        ),
        const SizedBox(width: 16),
        Expanded(child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: data.map((d) => Padding(
            padding: const EdgeInsets.only(bottom: 6),
            child: Row(children: [
              Container(width: 8, height: 8, decoration: BoxDecoration(color: d.$3, shape: BoxShape.circle)),
              const SizedBox(width: 6),
              Expanded(child: Text(d.$1, style: GoogleFonts.inter(fontSize: 12, color: c.text2))),
              Text('${d.$2}', style: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.w700, color: c.text)),
              const SizedBox(width: 4),
              Text('(${((d.$2 / total) * 100).toStringAsFixed(1)}%)',
                  style: GoogleFonts.inter(fontSize: 9, color: c.text3)),
            ]),
          )).toList(),
        )),
      ]),
    ).animate().fadeIn(delay: 420.ms);
  }

  // ──────────────────────────────────────────
  //  7. Canal per Day — Stacked
  // ──────────────────────────────────────────
  Widget _buildCanalPerDayChart(AppColorScheme c) {
    if (_daily.isEmpty) return const SizedBox();
    final sorted = ([..._daily]..sort((a, b) => a.date.compareTo(b.date))).take(14).toList();
    final maxVal = sorted.map((d) => d.ticketsCall + d.ticketsEmail + d.ticketsWeb).reduce(math.max).toDouble();

    return _card(c, child:
      Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
          Text('CANAUX PAR JOUR', style: GoogleFonts.inter(
            fontSize: 10, fontWeight: FontWeight.w600, color: c.text3, letterSpacing: 1,
          )),
          Row(children: [
            _legendDot(c.canalCall, 'Appels'),
            const SizedBox(width: 6),
            _legendDot(c.canalEmail, 'Emails'),
            const SizedBox(width: 6),
            _legendDot(c.canalWeb, 'Web'),
          ]),
        ]),
        const SizedBox(height: 12),
        SizedBox(
          height: 120,
          child: BarChart(BarChartData(
            alignment: BarChartAlignment.spaceAround,
            maxY: maxVal > 0 ? maxVal * 1.2 : 10,
            gridData: FlGridData(show: false),
            titlesData: FlTitlesData(
              leftTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
              rightTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
              topTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
              bottomTitles: AxisTitles(sideTitles: SideTitles(showTitles: true, reservedSize: 20,
                getTitlesWidget: (v, _) {
                  final idx = v.toInt();
                  if (idx >= 0 && idx < sorted.length && idx % 3 == 0) {
                    return Text(_fmtDate(sorted[idx].date),
                        style: GoogleFonts.inter(fontSize: 8, color: c.text3));
                  }
                  return const SizedBox();
                },
              )),
            ),
            barGroups: sorted.asMap().entries.map((e) {
              final d = e.value;
              return BarChartGroupData(x: e.key, barRods: [
                BarChartRodData(
                  toY: (d.ticketsCall + d.ticketsEmail + d.ticketsWeb).toDouble(),
                  width: 10,
                  borderRadius: const BorderRadius.only(topLeft: Radius.circular(3), topRight: Radius.circular(3)),
                  rodStackItems: [
                    BarChartRodStackItem(0, d.ticketsCall.toDouble(), c.canalCall),
                    BarChartRodStackItem(d.ticketsCall.toDouble(),
                        (d.ticketsCall + d.ticketsEmail).toDouble(), c.canalEmail),
                    BarChartRodStackItem((d.ticketsCall + d.ticketsEmail).toDouble(),
                        (d.ticketsCall + d.ticketsEmail + d.ticketsWeb).toDouble(), c.canalWeb),
                  ],
                  color: Colors.transparent,
                ),
              ]);
            }).toList(),
            borderData: FlBorderData(show: false),
          )),
        ),
      ]),
    ).animate().fadeIn(delay: 480.ms);
  }

  // ──────────────────────────────────────────
  //  Shared card — EXACT same as dashboard
  // ──────────────────────────────────────────
  Widget _card(AppColorScheme c, {String? title, required Widget child}) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: c.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: c.border),
        boxShadow: c.isDark ? [] : [BoxShadow(color: c.shadow, blurRadius: 8, offset: const Offset(0, 2))],
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        if (title != null) ...[
          Text(title, style: GoogleFonts.inter(
            fontSize: 10, fontWeight: FontWeight.w600, color: c.text3, letterSpacing: 1,
          )),
          const SizedBox(height: 12),
        ],
        child,
      ]),
    );
  }

  Widget _emptyChart(AppColorScheme c, String title) {
    return _card(c, title: title, child: SizedBox(
      height: 120,
      child: Center(child: Text('Aucune donnée', style: GoogleFonts.inter(fontSize: 12, color: c.text3))),
    ));
  }

  Widget _legendDot(Color color, String label) {
    return Row(mainAxisSize: MainAxisSize.min, children: [
      Container(width: 6, height: 6, decoration: BoxDecoration(color: color, shape: BoxShape.circle)),
      const SizedBox(width: 3),
      Text(label, style: GoogleFonts.inter(fontSize: 8, color: context.colors.text3)),
    ]);
  }

  String _fmtDate(String dateStr) {
    try {
      final d = DateTime.parse(dateStr);
      return '${d.day}/${d.month}';
    } catch (_) {
      return dateStr.length >= 5 ? dateStr.substring(5) : dateStr;
    }
  }
}

// ── Internal data classes ──
class _Trend {
  final String? label;
  final bool? trendUp;
  const _Trend(this.label, this.trendUp);
}
