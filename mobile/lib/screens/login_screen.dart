import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:provider/provider.dart';
import 'package:dio/dio.dart';
import '../config/colors.dart';
import '../providers/auth_provider.dart';
import '../widgets/logo.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _loading = false;
  bool _obscure = true;

  @override
  void dispose() {
    _emailController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _handleLogin() async {
    final email = _emailController.text.trim();
    final password = _passwordController.text;

    if (email.isEmpty || password.isEmpty) {
      _showError('Veuillez remplir tous les champs');
      return;
    }

    setState(() => _loading = true);
    try {
      await context.read<AuthProvider>().login(email, password);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: const Text('✅ Connexion réussie !'), backgroundColor: context.colors.success),
        );
      }
    } on DioException catch (e) {
      if (mounted) {
        if (e.response?.statusCode == 401) {
          _showError('Email ou mot de passe incorrect');
        } else if (e.type == DioExceptionType.connectionError ||
                   e.type == DioExceptionType.connectionTimeout) {
          _showError('Erreur réseau : impossible de joindre le serveur');
        } else {
          _showError('Erreur ${e.response?.statusCode ?? "réseau"}: ${e.message ?? e.toString()}');
        }
      }
    } catch (e) {
      if (mounted) _showError('Erreur inattendue: ${e.toString().substring(0, e.toString().length > 100 ? 100 : e.toString().length)}');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _showError(String msg) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(msg), backgroundColor: context.colors.danger),
    );
  }

  @override
  Widget build(BuildContext context) {
    final c = context.colors;

    return Scaffold(
      body: Container(
        decoration: BoxDecoration(gradient: c.gradientLogin),
        child: SafeArea(
          child: Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: 32),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  // ── Logo ──
                  const AppLogo(size: 68, animated: true)
                      .animate()
                      .scale(duration: 500.ms, curve: Curves.easeOutBack),
                  const SizedBox(height: 16),

                  // ── Brand ──
                  Text('AutoAssist', style: GoogleFonts.inter(
                    fontSize: 24, fontWeight: FontWeight.w900, color: c.text,
                    letterSpacing: -1,
                  )).animate().fadeIn(delay: 200.ms),
                  const SizedBox(height: 4),
                  Text("Centre d'appels intelligent", style: GoogleFonts.inter(
                    fontSize: 10, fontWeight: FontWeight.w600, color: c.text3,
                    letterSpacing: 2,
                  )).animate().fadeIn(delay: 300.ms),
                  const SizedBox(height: 48),

                  // ── Title ──
                  Align(
                    alignment: Alignment.centerLeft,
                    child: Text('Bienvenue.', style: GoogleFonts.inter(
                      fontSize: 28, fontWeight: FontWeight.w900, color: c.text,
                      letterSpacing: -1.5,
                    )),
                  ).animate().fadeIn(delay: 400.ms).slideX(begin: -0.1),
                  const SizedBox(height: 4),
                  Align(
                    alignment: Alignment.centerLeft,
                    child: Text('Connectez-vous pour accéder à votre espace', style: GoogleFonts.inter(
                      fontSize: 13, color: c.text2,
                    )),
                  ).animate().fadeIn(delay: 500.ms),
                  const SizedBox(height: 32),

                  // ── Glass card ──
                  Container(
                    decoration: BoxDecoration(
                      color: c.surface.withOpacity(c.isDark ? 0.6 : 0.9),
                      borderRadius: BorderRadius.circular(20),
                      border: Border.all(color: c.border),
                      boxShadow: c.isDark
                          ? []
                          : [BoxShadow(color: c.shadow, blurRadius: 24, offset: const Offset(0, 8))],
                    ),
                    padding: const EdgeInsets.all(24),
                    child: Column(
                      children: [
                        // Email
                        TextField(
                          controller: _emailController,
                          keyboardType: TextInputType.emailAddress,
                          style: GoogleFonts.inter(fontSize: 14, color: c.text),
                          decoration: InputDecoration(
                            labelText: 'Adresse email',
                            hintText: 'vous@autoassist.tn',
                            prefixIcon: Icon(Icons.email_outlined, size: 18, color: c.text3),
                          ),
                        ),
                        const SizedBox(height: 16),

                        // Password
                        TextField(
                          controller: _passwordController,
                          obscureText: _obscure,
                          style: GoogleFonts.inter(fontSize: 14, color: c.text),
                          onSubmitted: (_) => _handleLogin(),
                          decoration: InputDecoration(
                            labelText: 'Mot de passe',
                            hintText: '••••••••',
                            prefixIcon: Icon(Icons.lock_outline, size: 18, color: c.text3),
                            suffixIcon: IconButton(
                              icon: Icon(_obscure ? Icons.visibility_off : Icons.visibility, size: 18, color: c.text3),
                              onPressed: () => setState(() => _obscure = !_obscure),
                            ),
                          ),
                        ),
                        const SizedBox(height: 24),

                        // Submit
                        SizedBox(
                          width: double.infinity,
                          height: 48,
                          child: DecoratedBox(
                            decoration: BoxDecoration(
                              gradient: c.gradientCta,
                              borderRadius: BorderRadius.circular(12),
                              boxShadow: [BoxShadow(color: c.primary.withOpacity(0.3), blurRadius: 12, offset: const Offset(0, 4))],
                            ),
                            child: ElevatedButton(
                              onPressed: _loading ? null : _handleLogin,
                              style: ElevatedButton.styleFrom(
                                backgroundColor: Colors.transparent,
                                shadowColor: Colors.transparent,
                                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                              ),
                              child: _loading
                                  ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                                  : Row(
                                      mainAxisAlignment: MainAxisAlignment.center,
                                      children: [
                                        Text('Se connecter', style: GoogleFonts.inter(fontSize: 14, fontWeight: FontWeight.w700, color: Colors.white)),
                                        const SizedBox(width: 8),
                                        const Text('→', style: TextStyle(color: Colors.white70, fontSize: 16)),
                                      ],
                                    ),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ).animate().fadeIn(delay: 600.ms).slideY(begin: 0.1),

                  const SizedBox(height: 48),

                  // ── Footer ──
                  Text('AURORA v2.0 · Sécurisé par JWT · AES-256', style: GoogleFonts.inter(
                    fontSize: 9, color: c.text3, letterSpacing: 1,
                  )).animate().fadeIn(delay: 800.ms),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
