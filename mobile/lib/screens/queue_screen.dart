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
  bool _autoDispatching = false;

  // Filters
  String _activeFilter = 'QUEUE'; // 'QUEUE', 'NOUVEAU', 'EN_COURS', 'RESOLU', 'FERME'
  bool _showFilters = false;
  String _searchQuery = '';
  String _canalFilter = 'ALL';
  int? _priorityFilter;
  String _sortOption = 'createdAt,desc';

  @override
  void initState() {
    super.initState();
    _fetch();
  }

  Future<void> _fetch() async {
    setState(() => _loading = true);
    try {
      final results = await Future.wait([
        _activeFilter == 'QUEUE' 
            ? _service.getQueue() 
            : _service.getTickets(TicketFilter(
                statut: _activeFilter,
                search: _searchQuery.isNotEmpty ? _searchQuery : null,
                canal: _canalFilter != 'ALL' ? _canalFilter : null,
                priorite: _priorityFilter,
                sort: _sortOption,
                size: 50,
              )),
        _service.getStats(),
      ]);

      setState(() {
        if (_activeFilter == 'QUEUE') {
          final queueData = results[0] as Map<String, dynamic>;
          _tickets = (queueData['tickets'] as List?)?.map((t) => QueueEntry.fromJson(t)).toList() ?? [];
        } else {
          final ticketPage = results[0] as TicketPage;
          _tickets = ticketPage.content.asMap().entries.map((e) {
            final idx = e.key;
            final t = e.value;
            return QueueEntry(
              ticketId: t.id,
              ticketNumero: t.numero,
              clientNom: t.clientNom,
              canal: t.canal,
              priorite: t.priorite,
              position: idx + 1,
              createdAt: t.createdAt,
            );
          }).toList();
        }
        _stats = results[1] as TicketStats;
        _loading = false;
      });
    } catch (e) {
      debugPrint('[QueueScreen] Fetch error: $e');
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
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted) {
            Navigator.of(context).push(
              MaterialPageRoute(builder: (_) => TicketDetailScreen(ticketId: entry.ticketId)),
            );
          }
        });
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

  Future<void> _handleAutoDispatch() async {
    final user = context.read<AuthProvider>().user;
    if (user == null) return;
    
    if (_activeFilter != 'QUEUE') {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: const Text('⚠️ L\'Auto-Dispatch est uniquement disponible sur la File d\'attente'), backgroundColor: context.colors.warning),
      );
      return;
    }

    if (_tickets.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: const Text('ℹ️ Aucun ticket dans la file d\'attente'), backgroundColor: context.colors.info),
      );
      return;
    }

    final firstTicket = _tickets.first;
    setState(() => _autoDispatching = true);
    
    try {
      await _service.assignTicket(firstTicket.ticketId, user.userId);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('🤖 Auto-Dispatch : Ticket ${firstTicket.ticketNumero} assigné !'), backgroundColor: context.colors.success),
        );
        setState(() {
          _tickets.removeAt(0);
        });
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted) {
            Navigator.of(context).push(
              MaterialPageRoute(builder: (_) => TicketDetailScreen(ticketId: firstTicket.ticketId)),
            );
          }
        });
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: const Text('❌ Erreur d\'Auto-Dispatch'), backgroundColor: context.colors.danger),
        );
      }
    } finally {
      if (mounted) setState(() => _autoDispatching = false);
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
                Text(_activeFilter == 'QUEUE' ? "File d'attente" : "Tickets ${_activeFilter.replaceAll('_', ' ')}", 
                  style: GoogleFonts.inter(fontSize: 20, fontWeight: FontWeight.w800, color: c.text)),
                Text('${_tickets.length} ticket${_tickets.length > 1 ? 's' : ''} ${_activeFilter == 'QUEUE' ? 'en attente' : ''}',
                  style: GoogleFonts.inter(fontSize: 10, color: c.text3)),
              ]),
              Row(
                children: [
                  IconButton(
                    icon: Icon(_showFilters ? Icons.filter_alt : Icons.filter_alt_outlined, color: _showFilters ? c.primary : c.text3),
                    onPressed: () => setState(() => _showFilters = !_showFilters),
                  ),
                  if (_activeFilter == 'QUEUE')
                    IconButton(
                      icon: _autoDispatching 
                        ? SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2, color: c.primary))
                        : Icon(Icons.psychology, color: c.primary),
                      onPressed: _autoDispatching ? null : _handleAutoDispatch,
                      tooltip: 'Auto-Dispatch',
                    ),
                  IconButton(onPressed: _fetch, icon: Icon(Icons.refresh, size: 18, color: c.text3)),
                ],
              ),
            ],
          ),

          const SizedBox(height: 16),

          // ── Stats Row ──
          if (_stats != null)
            Row(
              children: [
                _statBox(c, 'NOUVEAU', 'Nouveaux', _stats!.ticketsNouveau, c.info),
                const SizedBox(width: 8),
                _statBox(c, 'EN_COURS', 'En cours', _stats!.ticketsEnCours, c.warning),
                const SizedBox(width: 8),
                _statBox(c, 'RESOLU', 'Résolus', _stats!.ticketsResolu, c.success),
                const SizedBox(width: 8),
                _statBox(c, 'FERME', 'Fermés', _stats!.ticketsFerme, c.text3),
              ],
            ).animate().fadeIn().slideY(begin: 0.1),

          const SizedBox(height: 16),

          // ── Expandable Advanced Filters ──
          if (_showFilters)
            Container(
              padding: const EdgeInsets.all(12),
              margin: const EdgeInsets.only(bottom: 12),
              decoration: BoxDecoration(
                color: c.surface,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: c.border),
                boxShadow: c.isDark ? [] : [BoxShadow(color: c.shadow, blurRadius: 8, offset: const Offset(0, 2))],
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('FILTRES AVANCÉS', style: GoogleFonts.inter(fontSize: 9, fontWeight: FontWeight.w700, color: c.text3, letterSpacing: 0.5)),
                  const SizedBox(height: 8),
                  
                  // Search
                  TextField(
                    onChanged: (v) {
                      _searchQuery = v;
                    },
                    onSubmitted: (_) => _fetch(),
                    style: GoogleFonts.inter(fontSize: 12, color: c.text),
                    decoration: InputDecoration(
                      hintText: 'Rechercher un client...',
                      hintStyle: GoogleFonts.inter(fontSize: 12, color: c.text3),
                      prefixIcon: Icon(Icons.search, size: 16, color: c.text3),
                      isDense: true,
                      contentPadding: const EdgeInsets.all(8),
                      filled: true,
                      fillColor: c.elevated,
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(8), borderSide: BorderSide(color: c.border)),
                      enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(8), borderSide: BorderSide(color: c.border)),
                    ),
                  ),
                  const SizedBox(height: 10),

                  // Canal & Priority
                  Row(
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text('Canal', style: GoogleFonts.inter(fontSize: 8, color: c.text3)),
                            const SizedBox(height: 4),
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 8),
                              decoration: BoxDecoration(color: c.elevated, borderRadius: BorderRadius.circular(8), border: Border.all(color: c.border)),
                              child: DropdownButtonHideUnderline(
                                child: DropdownButton<String>(
                                  value: _canalFilter,
                                  isExpanded: true,
                                  dropdownColor: c.surface,
                                  style: GoogleFonts.inter(fontSize: 11, color: c.text),
                                  items: ['ALL', 'CALL', 'EMAIL', 'WEB'].map((val) =>
                                    DropdownMenuItem(value: val, child: Text(val == 'ALL' ? 'Tous' : val))).toList(),
                                  onChanged: (v) {
                                    setState(() => _canalFilter = v ?? 'ALL');
                                    _fetch();
                                  },
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text('Priorité', style: GoogleFonts.inter(fontSize: 8, color: c.text3)),
                            const SizedBox(height: 4),
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 8),
                              decoration: BoxDecoration(color: c.elevated, borderRadius: BorderRadius.circular(8), border: Border.all(color: c.border)),
                              child: DropdownButtonHideUnderline(
                                child: DropdownButton<int?>(
                                  value: _priorityFilter,
                                  isExpanded: true,
                                  dropdownColor: c.surface,
                                  style: GoogleFonts.inter(fontSize: 11, color: c.text),
                                  items: [
                                    const DropdownMenuItem(value: null, child: Text('Toutes')),
                                    const DropdownMenuItem(value: 1, child: Text('P1 - Urgent')),
                                    const DropdownMenuItem(value: 2, child: Text('P2 - Moyen')),
                                    const DropdownMenuItem(value: 3, child: Text('P3 - Faible')),
                                  ],
                                  onChanged: (v) {
                                    setState(() => _priorityFilter = v);
                                    _fetch();
                                  },
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),

                  // Sort Option
                  Row(
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text('Trier par', style: GoogleFonts.inter(fontSize: 8, color: c.text3)),
                            const SizedBox(height: 4),
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 8),
                              decoration: BoxDecoration(color: c.elevated, borderRadius: BorderRadius.circular(8), border: Border.all(color: c.border)),
                              child: DropdownButtonHideUnderline(
                                child: DropdownButton<String>(
                                  value: _sortOption,
                                  isExpanded: true,
                                  dropdownColor: c.surface,
                                  style: GoogleFonts.inter(fontSize: 11, color: c.text),
                                  items: [
                                    const DropdownMenuItem(value: 'createdAt,desc', child: Text('Récent d\'abord')),
                                    const DropdownMenuItem(value: 'createdAt,asc', child: Text('Ancien d\'abord')),
                                    const DropdownMenuItem(value: 'priorite,asc', child: Text('Priorité (Haute d\'abord)')),
                                  ],
                                  onChanged: (v) {
                                    setState(() => _sortOption = v ?? 'createdAt,desc');
                                    _fetch();
                                  },
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 8),
                      Padding(
                        padding: const EdgeInsets.only(top: 14),
                        child: TextButton(
                          onPressed: () {
                            setState(() {
                              _searchQuery = '';
                              _canalFilter = 'ALL';
                              _priorityFilter = null;
                              _sortOption = 'createdAt,desc';
                            });
                            _fetch();
                          },
                          child: Text('Réinitialiser', style: GoogleFonts.inter(fontSize: 11, color: c.danger)),
                        ),
                      ),
                      Padding(
                        padding: const EdgeInsets.only(top: 14),
                        child: ElevatedButton(
                          onPressed: _fetch,
                          style: ElevatedButton.styleFrom(
                            backgroundColor: c.primary,
                            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                          ),
                          child: Text('Appliquer', style: GoogleFonts.inter(fontSize: 11, color: Colors.white)),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ).animate().fadeIn().slideY(begin: -0.1),

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

  Widget _statBox(AppColorScheme c, String filterVal, String label, int value, Color color) {
    final isSelected = _activeFilter == filterVal;
    return Expanded(
      child: GestureDetector(
        onTap: () {
          setState(() {
            _activeFilter = isSelected ? 'QUEUE' : filterVal;
          });
          _fetch();
        },
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 200),
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: isSelected ? color.withOpacity(0.12) : c.surface,
            borderRadius: BorderRadius.circular(14),
            border: Border.all(color: isSelected ? color : c.border, width: isSelected ? 2 : 1),
            boxShadow: c.isDark ? [] : [BoxShadow(color: c.shadow, blurRadius: 6, offset: const Offset(0, 2))],
          ),
          child: Column(children: [
            Text(label.toUpperCase(), style: GoogleFonts.inter(fontSize: 8, fontWeight: FontWeight.w600, color: isSelected ? color : c.text3, letterSpacing: 0.5)),
            const SizedBox(height: 4),
            Text('$value', style: GoogleFonts.inter(fontSize: 22, fontWeight: FontWeight.w900, color: color)),
          ]),
        ),
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
                    color: c.canalColor(t.canal).withOpacity(0.12),
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(color: c.canalColor(t.canal).withOpacity(0.2)),
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
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        Icons.access_time_outlined,
                        size: 11,
                        color: isUrgent ? c.danger : c.warning,
                      ),
                      const SizedBox(width: 4),
                      Text(
                        _timeSince(t.createdAt),
                        style: GoogleFonts.inter(
                          fontSize: 10,
                          fontWeight: FontWeight.w600,
                          color: isUrgent ? c.danger : c.warning,
                        ),
                      ),
                    ],
                  ),
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
