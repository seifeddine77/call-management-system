import 'dart:math' as math;
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import '../config/colors.dart';

/// Premium Custom-Painted Headset & AI Neural Logo matching the React version
class AppLogo extends StatefulWidget {
  final double size;
  final bool animated;

  const AppLogo({
    super.key,
    this.size = 64,
    this.animated = true,
  });

  @override
  State<AppLogo> createState() => _AppLogoState();
}

class _AppLogoState extends State<AppLogo> with SingleTickerProviderStateMixin {
  AnimationController? _controller;

  @override
  void initState() {
    super.initState();
    if (widget.animated) {
      _controller = AnimationController(
        vsync: this,
        duration: const Duration(seconds: 4),
      )..repeat();
    }
  }

  @override
  void dispose() {
    _controller?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;

    if (widget.animated && _controller != null) {
      return AnimatedBuilder(
        animation: _controller!,
        builder: (context, child) {
          return CustomPaint(
            size: Size(widget.size, widget.size),
            painter: _LogoPainter(
              colors: colors,
              animationValue: _controller!.value,
            ),
          );
        },
      );
    }

    return CustomPaint(
      size: Size(widget.size, widget.size),
      painter: _LogoPainter(
        colors: colors,
        animationValue: 0.0,
      ),
    );
  }
}

class _LogoPainter extends CustomPainter {
  final AppColorScheme colors;
  final double animationValue;

  _LogoPainter({
    required this.colors,
    required this.animationValue,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final double scale = size.width / 64.0;
    canvas.save();
    canvas.scale(scale, scale);

    // ── Setup Paint with Linear Gradient ──
    final gradient = ui.Gradient.linear(
      const Offset(0, 0),
      const Offset(64, 64),
      [
        const Color(0xFF2563EB), // Royal Blue
        const Color(0xFF3B82F6), // Sky Blue
        const Color(0xFF06B6D4), // Cyan
      ],
      [0.0, 0.5, 1.0],
    );

    final gradPaint = Paint()
      ..shader = gradient
      ..style = PaintingStyle.fill;

    // 1. Background Circle
    final bgPaint = Paint()
      ..color = colors.isDark ? const Color(0x142563EB) : const Color(0x0F2563EB) // matching 0.08 and 0.06 opacity
      ..style = PaintingStyle.fill;
    canvas.drawCircle(const Offset(32, 32), 30, bgPaint);

    final bgStrokePaint = Paint()
      ..shader = gradient
      ..strokeWidth = 1.5
      ..style = PaintingStyle.stroke
      ..color = Colors.white.withOpacity(0.7); // Apply 0.7 opacity to stroke
    canvas.drawCircle(const Offset(32, 32), 30, bgStrokePaint);

    // 2. Headset Headband Arc
    final arcPaint = Paint()
      ..shader = gradient
      ..strokeWidth = 3.0
      ..strokeCap = StrokeCap.round
      ..style = PaintingStyle.stroke;

    final arcPath = Path()
      ..moveTo(18, 30)
      ..cubicTo(18, 20, 46, 20, 46, 30);
    canvas.drawPath(arcPath, arcPaint);

    // 3. Left Earpiece
    final leftEarpieceRect = RRect.fromRectAndRadius(
      const Rect.fromLTWH(14, 28, 6, 12),
      const Radius.circular(3),
    );
    canvas.drawRRect(leftEarpieceRect, gradPaint);

    // 4. Right Earpiece
    final rightEarpieceRect = RRect.fromRectAndRadius(
      const Rect.fromLTWH(44, 28, 6, 12),
      const Radius.circular(3),
    );
    canvas.drawRRect(rightEarpieceRect, gradPaint);

    // 5. Microphone Arm
    final micArmPaint = Paint()
      ..shader = gradient
      ..strokeWidth = 2.5
      ..strokeCap = StrokeCap.round
      ..style = PaintingStyle.stroke;

    final micPath = Path()
      ..moveTo(44, 38)
      ..cubicTo(44, 46, 36, 48, 32, 48);
    canvas.drawPath(micPath, micArmPaint);

    // 6. Mic Tip
    canvas.drawCircle(const Offset(32, 48), 2.5, gradPaint);

    // 7. Neural Connection Lines
    final linePaint = Paint()
      ..color = (colors.isDark ? const Color(0xFF60A5FA) : const Color(0xFF3B82F6)).withOpacity(0.4)
      ..strokeWidth = 0.8
      ..style = PaintingStyle.stroke;

    canvas.drawLine(const Offset(25, 32), const Offset(32, 28), linePaint);
    canvas.drawLine(const Offset(39, 32), const Offset(32, 28), linePaint);

    // 8. AI Neural Nodes with Pulsing/Breathing Effects
    // Left neural node (animates opacity 0.5 -> 1 -> 0.5)
    final double leftNodePhase = animationValue * 2 * math.pi * (4.0 / 1.8);
    final double nodeLeftOpacity = 0.75 + 0.25 * math.sin(leftNodePhase);
    final leftNodePaint = Paint()
      ..color = const Color(0xFF06B6D4).withOpacity(nodeLeftOpacity)
      ..style = PaintingStyle.fill;
    canvas.drawCircle(const Offset(25, 32), 1.8, leftNodePaint);

    // Right neural node (animates opacity 0.5 -> 1 -> 0.5 with phase offset)
    final double rightNodePhase = animationValue * 2 * math.pi * (4.0 / 2.2);
    final double nodeRightOpacity = 0.75 + 0.25 * math.sin(rightNodePhase + math.pi / 4);
    final rightNodePaint = Paint()
      ..color = const Color(0xFF06B6D4).withOpacity(nodeRightOpacity)
      ..style = PaintingStyle.fill;
    canvas.drawCircle(const Offset(39, 32), 1.8, rightNodePaint);

    // Central brain node (animates radius 2.5 -> 3.5 -> 2.5)
    final double centralPhase = animationValue * 2 * math.pi * (4.0 / 2.0);
    final double centralRadius = 3.0 + 0.5 * math.sin(centralPhase);
    final centralNodePaint = Paint()
      ..color = colors.isDark ? const Color(0xFF60A5FA) : const Color(0xFF2563EB)
      ..style = PaintingStyle.fill;
    canvas.drawCircle(const Offset(32, 28), centralRadius, centralNodePaint);

    // 9. Tiny signal waves (left)
    final double leftWavePhase = animationValue * 2 * math.pi * (4.0 / 1.5);
    final double leftWaveOpacity = 0.4 + 0.2 * math.sin(leftWavePhase);
    final leftWavePaint = Paint()
      ..shader = gradient
      ..strokeWidth = 1.0
      ..style = PaintingStyle.stroke
      ..color = Colors.white.withOpacity(leftWaveOpacity);
    
    final leftWavePath = Path()
      ..moveTo(12, 32)
      ..cubicTo(10, 30, 10, 34, 12, 32);
    canvas.drawPath(leftWavePath, leftWavePaint);

    // 10. Tiny signal waves (right)
    final double rightWavePhase = (animationValue * 2 * math.pi * (4.0 / 1.5)) - math.pi;
    final double rightWaveOpacity = 0.4 + 0.2 * math.sin(rightWavePhase);
    final rightWavePaint = Paint()
      ..shader = gradient
      ..strokeWidth = 1.0
      ..style = PaintingStyle.stroke
      ..color = Colors.white.withOpacity(rightWaveOpacity);

    final rightWavePath = Path()
      ..moveTo(52, 32)
      ..cubicTo(54, 30, 54, 34, 52, 32);
    canvas.drawPath(rightWavePath, rightWavePaint);

    canvas.restore();
  }

  @override
  bool shouldRepaint(covariant _LogoPainter oldDelegate) {
    return oldDelegate.animationValue != animationValue ||
        oldDelegate.colors != colors;
  }
}
