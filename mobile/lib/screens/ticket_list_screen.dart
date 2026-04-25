import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:shimmer/shimmer.dart';
import '../config/colors.dart';
import '../models/ticket.dart';
import '../services/ticket_service.dart';
import 'ticket_detail_screen.dart';

class TicketListScreen extends StatefulWidget {
  const TicketListScreen({super.key});

  @override
  State<TicketListScreen> createState() => _TicketListScreenState();
}

class _TicketListScreenState extends State<TicketListScreen> {
  final _service = TicketService();
  List<Ticket> _tickets = [];
  bool _loading = true;
  int _page = 0;
  int _totalPages = 0;
  int _totalElements = 0;
  String? _canal;
  String? _statut;
  int? _priorite;
  String _search = '';
  String _sort = 'createdAt,desc'; // default: most recent first

  @override
  void initState() {
    super.initState();
    _fetch();
  }

  Future<void> _fetch() async {
    setState(() => _loading = true);
    try {
      final data = await _service.getTickets(TicketFilter(
        page: _page, size: 15,
        canal: _canal, statut: _statut, priorite: _priorite,
        search: _search.isNotEmpty ? _search : null,
        sort: _sort,
      ));
      setState(() {
        _tickets = data.content;
        _totalPages = data.totalPages;
        _totalElements = data.totalElements;
        _loading = false;
      });
    } catch (e) {
      debugPrint('[TicketList] Fetch error: $e');
      setState(() => _loading = false);
    }
  }

  void _resetFilters() {
    setState(() { _canal = null; _statut = null; _priorite = null; _search = ''; _page = 0; _sort = 'createdAt,desc'; });
    _fetch();
  }

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Column(
      children: [
        // ── Header ──
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text('Tickets', style: GoogleFonts.inter(fontSize: 20, fontWeight: FontWeight.w800, color: c.text)),
                Text('$_totalElements ticket${_totalElements > 1 ? 's' : ''} au total',
                  style: GoogleFonts.inter(fontSize: 10, color: c.text3)),
              ]),
              Row(children: [
                // Sort toggle
                GestureDetector(
                  onTap: () {
                    setState(() => _sort = _sort.contains('desc') ? 'createdAt,asc' : 'createdAt,desc');
                    _fetch();
                  },
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(color: c.elevated, borderRadius: BorderRadius.circular(6), border: Border.all(color: c.border)),
                    child: Row(children: [
                      Icon(_sort.contains('desc') ? Icons.arrow_downward : Icons.arrow_upward, size: 12, color: c.primary),
                      const SizedBox(width: 4),
                      Text(_sort.contains('desc') ? 'Récent' : 'Ancien',
                          style: GoogleFonts.inter(fontSize: 10, fontWeight: FontWeight.w600, color: c.primary)),
                    ]),
                  ),
                ),
                const SizedBox(width: 8),
                IconButton(
                  onPressed: _fetch,
                  icon: _loading
                      ? SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2, color: c.primary))
                      : Icon(Icons.refresh, size: 18, color: c.text3),
                ),
              ]),
            ],
          ),
        ),

        // ── Search ──
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
          child: TextField(
            onChanged: (v) { _search = v; _page = 0; _fetch(); },
            style: GoogleFonts.inter(fontSize: 13, color: c.text),
            decoration: InputDecoration(
              hintText: 'Rechercher par client, numéro…',
              prefixIcon: Icon(Icons.search, size: 16, color: c.text3),
              isDense: true,
              contentPadding: const EdgeInsets.symmetric(vertical: 10),
            ),
          ),
        ),

        // ── Status + Canal Filters ──
        SizedBox(
          height: 36,
          child: ListView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 16),
            children: [
              _filterChip(c, 'Tous', _canal == null && _statut == null && _priorite == null, () {
                _canal = null; _statut = null; _priorite = null; _page = 0; _fetch();
              }),
              // Status filters (CDC 2.C)
              _filterChip(c, '● Nouveau', _statut == 'NOUVEAU', () { _statut = _statut == 'NOUVEAU' ? null : 'NOUVEAU'; _page = 0; _fetch(); },
                  color: c.statutColor('NOUVEAU')),
              _filterChip(c, '◷ En cours', _statut == 'EN_COURS', () { _statut = _statut == 'EN_COURS' ? null : 'EN_COURS'; _page = 0; _fetch(); },
                  color: c.statutColor('EN_COURS')),
              _filterChip(c, '✓ Résolu', _statut == 'RESOLU', () { _statut = _statut == 'RESOLU' ? null : 'RESOLU'; _page = 0; _fetch(); },
                  color: c.statutColor('RESOLU')),
              _filterChip(c, '— Fermé', _statut == 'FERME', () { _statut = _statut == 'FERME' ? null : 'FERME'; _page = 0; _fetch(); },
                  color: c.statutColor('FERME')),
              const SizedBox(width: 6),
              // Canal filters
              _filterChip(c, '📞', _canal == 'CALL', () { _canal = _canal == 'CALL' ? null : 'CALL'; _page = 0; _fetch(); }),
              _filterChip(c, '📧', _canal == 'EMAIL', () { _canal = _canal == 'EMAIL' ? null : 'EMAIL'; _page = 0; _fetch(); }),
              _filterChip(c, '🌐', _canal == 'WEB', () { _canal = _canal == 'WEB' ? null : 'WEB'; _page = 0; _fetch(); }),
              const SizedBox(width: 6),
              // Priority filters
              _filterChip(c, '🔴 P1', _priorite == 1, () { _priorite = _priorite == 1 ? null : 1; _page = 0; _fetch(); }),
              _filterChip(c, '🟡 P2', _priorite == 2, () { _priorite = _priorite == 2 ? null : 2; _page = 0; _fetch(); }),
            ],
          ),
        ),

        const SizedBox(height: 8),

        // ── List ──
        Expanded(
          child: _loading
            ? ListView.builder(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                itemCount: 6,
                itemBuilder: (_, __) => Shimmer.fromColors(
                  baseColor: c.isDark ? c.surface : Colors.grey[200]!,
                  highlightColor: c.isDark ? c.elevated : Colors.white,
                  child: Container(
                    height: 70,
                    margin: const EdgeInsets.only(bottom: 8),
                    decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(14)),
                  ),
                ),
              )
            : _tickets.isEmpty
              ? Center(
                  child: Column(mainAxisSize: MainAxisSize.min, children: [
                    const Text('🔍', style: TextStyle(fontSize: 40)),
                    const SizedBox(height: 8),
                    Text('Aucun résultat', style: GoogleFonts.inter(fontWeight: FontWeight.w700, color: c.text)),
                    TextButton(onPressed: _resetFilters, child: const Text('Réinitialiser')),
                  ]),
                )
              : RefreshIndicator(
                  onRefresh: _fetch,
                  child: ListView.builder(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    itemCount: _tickets.length + (_totalPages > 1 ? 1 : 0),
                    itemBuilder: (ctx, i) {
                      if (i == _tickets.length) return _buildPagination(c);
                      return _buildTicketCard(c, _tickets[i], i);
                    },
                  ),
                ),
        ),
      ],
    );
  }

  Widget _filterChip(AppColorScheme c, String label, bool selected, VoidCallback onTap, {Color? color}) {
    final chipColor = color ?? c.primary;
    return Padding(
      padding: const EdgeInsets.only(right: 6),
      child: GestureDetector(
        onTap: onTap,
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
          decoration: BoxDecoration(
            color: selected ? chipColor.withOpacity(0.12) : c.elevated,
            borderRadius: BorderRadius.circular(8),
            border: Border.all(color: selected ? chipColor.withOpacity(0.3) : c.border),
          ),
          child: Text(label, style: GoogleFonts.inter(
            fontSize: 11, fontWeight: FontWeight.w600,
            color: selected ? chipColor : c.text2,
          )),
        ),
      ),
    );
  }

  Widget _buildTicketCard(AppColorScheme c, Ticket t, int index) {
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      decoration: BoxDecoration(
        color: c.surface,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: c.border),
        boxShadow: c.isDark ? [] : [BoxShadow(color: c.shadow, blurRadius: 6, offset: const Offset(0, 2))],
      ),
      child: InkWell(
        borderRadius: BorderRadius.circular(14),
        onTap: () {
          Navigator.push(context, MaterialPageRoute(builder: (_) => TicketDetailScreen(ticketId: t.id)));
        },
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Row(
            children: [
              // Canal icon
              Container(
                width: 40, height: 40,
                decoration: BoxDecoration(
                  color: c.canalColor(t.canal).withOpacity(0.1),
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: c.canalColor(t.canal).withOpacity(0.2)),
                ),
                child: Center(child: Text(
                  t.canal == 'CALL' ? '📞' : t.canal == 'EMAIL' ? '📧' : '🌐',
                  style: const TextStyle(fontSize: 18),
                )),
              ),
              const SizedBox(width: 12),

              // Info
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(children: [
                      Expanded(child: Text(t.clientNom, style: GoogleFonts.inter(fontSize: 13, fontWeight: FontWeight.w600, color: c.text))),
                      Text('P${t.priorite}', style: GoogleFonts.inter(
                        fontSize: 11, fontWeight: FontWeight.w700,
                        color: c.priorityColor(t.priorite),
                      )),
                    ]),
                    const SizedBox(height: 2),
                    Row(children: [
                      Hero(tag: 'ticket-num-${t.id}', child: Material(type: MaterialType.transparency, child: Text(t.numero, style: GoogleFonts.inter(fontSize: 10, fontWeight: FontWeight.w600, color: c.primary)))),
                      const SizedBox(width: 8),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                        decoration: BoxDecoration(
                          color: c.statutColor(t.statut).withOpacity(0.1),
                          borderRadius: BorderRadius.circular(4),
                        ),
                        child: Text(t.statut.replaceAll('_', ' '), style: GoogleFonts.inter(
                          fontSize: 10, fontWeight: FontWeight.w600, color: c.statutColor(t.statut),
                        )),
                      ),
                    ]),
                  ],
                ),
              ),

              Icon(Icons.chevron_right, size: 16, color: c.text3),
            ],
          ),
        ),
      ),
    ).animate().fadeIn(delay: Duration(milliseconds: 50 * index)).slideX(begin: 0.03);
  }

  Widget _buildPagination(AppColorScheme c) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 16),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          IconButton(
            onPressed: _page > 0 ? () { _page--; _fetch(); } : null,
            icon: const Icon(Icons.chevron_left, size: 18),
            color: c.text2,
          ),
          Text('${_page + 1} / $_totalPages', style: GoogleFonts.inter(fontSize: 11, color: c.text3)),
          IconButton(
            onPressed: _page < _totalPages - 1 ? () { _page++; _fetch(); } : null,
            icon: const Icon(Icons.chevron_right, size: 18),
            color: c.text2,
          ),
        ],
      ),
    );
  }
}
