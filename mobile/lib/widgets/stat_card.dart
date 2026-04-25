import 'dart:math';
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import '../config/colors.dart';

/// Reusable KPI stat card with optional sparkline and ring
class StatCard extends StatelessWidget {
  final IconData icon;
  final Color iconColor;
  final String label;
  final String value;
  final String? trend;
  final bool? trendUp;
  final List<double>? sparkData;
  final double? ringValue; // 0.0 - 1.0

  const StatCard({
    super.key,
    required this.icon,
    required this.iconColor,
    required this.label,
    required this.value,
    this.trend,
    this.trendUp,
    this.sparkData,
    this.ringValue,
  });

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
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
          // Header
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
            Text(label.toUpperCase(), style: GoogleFonts.inter(
              fontSize: 9, fontWeight: FontWeight.w600, color: c.text3, letterSpacing: 0.8,
            )),
          ]),
          const SizedBox(height: 8),

          // Value + trend
          Row(
            crossAxisAlignment: CrossAxisAlignment.baseline,
            textBaseline: TextBaseline.alphabetic,
            children: [
              Text(value, style: GoogleFonts.inter(
                fontSize: 24, fontWeight: FontWeight.w900, color: c.text,
                letterSpacing: -1,
              )),
              if (trend != null) ...[
                const SizedBox(width: 8),
                Row(children: [
                  Icon(
                    trendUp == true ? Icons.trending_up : trendUp == false ? Icons.trending_down : Icons.remove,
                    size: 12,
                    color: trendUp == true ? c.success : trendUp == false ? c.danger : c.text3,
                  ),
                  const SizedBox(width: 2),
                  Text(trend!, style: GoogleFonts.inter(
                    fontSize: 10, fontWeight: FontWeight.w600,
                    color: trendUp == true ? c.success : trendUp == false ? c.danger : c.text3,
                  )),
                ]),
              ],
            ],
          ),

          // Sparkline
          if (sparkData != null && sparkData!.isNotEmpty) ...[
            const SizedBox(height: 8),
            SizedBox(
              height: 28,
              child: CustomPaint(
                size: const Size(double.infinity, 28),
                painter: _SparklinePainter(data: sparkData!, color: iconColor),
              ),
            ),
          ],

          // Ring
          if (ringValue != null) ...[
            const SizedBox(height: 8),
            Center(
              child: SizedBox(
                width: 56, height: 56,
                child: CustomPaint(
                  painter: _RingPainter(value: ringValue!, color: iconColor, trackColor: c.muted),
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

/// Mini bar sparkline painter
class _SparklinePainter extends CustomPainter {
  final List<double> data;
  final Color color;

  _SparklinePainter({required this.data, required this.color});

  @override
  void paint(Canvas canvas, Size size) {
    if (data.isEmpty) return;
    final maxVal = data.reduce(max);
    if (maxVal == 0) return;

    final barWidth = (size.width - (data.length - 1) * 2) / data.length;
    final paint = Paint()..style = PaintingStyle.fill;

    for (int i = 0; i < data.length; i++) {
      final h = max(2.0, (data[i] / maxVal) * size.height);
      final opacity = 0.3 + (data[i] / maxVal) * 0.7;
      paint.color = color.withOpacity(opacity);

      final rect = RRect.fromRectAndRadius(
        Rect.fromLTWH(i * (barWidth + 2), size.height - h, barWidth, h),
        const Radius.circular(2),
      );
      canvas.drawRRect(rect, paint);
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => true;
}

/// Circular ring painter
class _RingPainter extends CustomPainter {
  final double value;
  final Color color;
  final Color trackColor;

  _RingPainter({required this.value, required this.color, required this.trackColor});

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final radius = size.width / 2 - 4;

    // Background ring
    canvas.drawCircle(center, radius, Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 4
      ..color = trackColor);

    // Value arc
    canvas.drawArc(
      Rect.fromCircle(center: center, radius: radius),
      -pi / 2,
      2 * pi * value,
      false,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 4
        ..strokeCap = StrokeCap.round
        ..color = color,
    );
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => true;
}
