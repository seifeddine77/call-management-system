import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:shimmer/shimmer.dart';
import 'package:flutter_slidable/flutter_slidable.dart';
import '../config/colors.dart';
import '../models/ticket.dart';
import '../services/ticket_service.dart';
import '../models/decision.dart';
import '../services/decision_service.dart';
import 'ticket_detail_screen.dart';

class TicketListScreen extends StatefulWidget {
  const TicketListScreen({super.key});

  @override
  State<TicketListScreen> createState() => _TicketListScreenState();
}

class _TicketListScreenState extends State<TicketListScreen> {
  final _service = TicketService();
  final _decisionService = DecisionService();
  final TextEditingController _searchController = TextEditingController();
  final ScrollController _scrollController = ScrollController();
  Timer? _debounce;
  List<Ticket> _tickets = [];
  final Map<String, DecisionResult?> _decisions = {};
  bool _loading = true;
  bool _loadingMore = false;
  int _page = 0;
  int _totalPages = 0;
  int _totalElements = 0;
  String? _canal;
  String? _statut;
  int? _priorite;
  String _search = '';
  String _sort = 'createdAt,desc';
  bool _showFilters = false;
  String _recommandationFilter = 'ALL'; // 'ALL', 'FAVORABLE', 'DEFAVORABLE', 'ETUDE'

  List<Ticket> get _filteredTickets {
    if (_recommandationFilter == 'ALL') return _tickets;
    return _tickets.where((t) {
      final dec = _decisions[t.id];
      if (dec == null) return false;
      final finalDec = dec.finalDecision ?? dec.decision;
      return finalDec == _recommandationFilter;
    }).toList();
  }

  int get _activeFiltersCount {
    int cnt = 0;
    if (_canal != null) cnt++;
    if (_statut != null) cnt++;
    if (_priorite != null) cnt++;
    if (_recommandationFilter != 'ALL') cnt++;
    if (_search.isNotEmpty) cnt++;
    return cnt;
  }

  @override
  void initState() {
    super.initState();
    _fetch();
    _scrollController.addListener(_onScroll);
  }

  @override
  void dispose() {
    _searchController.dispose();
    _scrollController.dispose();
    _debounce?.cancel();
    super.dispose();
  }

  void _onScroll() {
    if (_scrollController.position.pixels >= _scrollController.position.maxScrollExtent - 200) {
      if (!_loading && !_loadingMore && _page < _totalPages - 1) {
        _loadMore();
      }
    }
  }

  Future<void> _fetch({bool refresh = false}) async {
    if (refresh) {
      HapticFeedback.lightImpact();
      _page = 0;
    }
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
      _fetchDecisions(data.content);
    } catch (e) {
      debugPrint('[TicketList] Fetch error: $e');
      setState(() => _loading = false);
    }
  }

  Future<void> _loadMore() async {
    setState(() => _loadingMore = true);
    try {
      _page++;
      final data = await _service.getTickets(TicketFilter(
        page: _page, size: 15,
        canal: _canal, statut: _statut, priorite: _priorite,
        search: _search.isNotEmpty ? _search : null,
        sort: _sort,
      ));
      setState(() {
        _tickets.addAll(data.content);
        _loadingMore = false;
      });
      _fetchDecisions(data.content);
    } catch (e) {
      debugPrint('[TicketList] Load more error: $e');
      setState(() {
        _page--;
        _loadingMore = false;
      });
    }
  }

  Future<void> _fetchDecisions(List<Ticket> ticketsList) async {
    final futures = ticketsList.map((t) async {
      try {
        final dec = await _decisionService.getByTicket(t.id);
        return MapEntry(t.id, dec);
      } catch (_) {
        return MapEntry(t.id, null);
      }
    });
    final results = await Future.wait(futures);
    if (mounted) {
      setState(() {
        for (final entry in results) {
          _decisions[entry.key] = entry.value;
        }
      });
    }
  }

  void _resetFilters() {
    HapticFeedback.lightImpact();
    _searchController.clear();
    setState(() { 
      _canal = null; 
      _statut = null; 
      _priorite = null; 
      _recommandationFilter = 'ALL';
      _search = ''; 
      _page = 0; 
      _sort = 'createdAt,desc'; 
    });
    _fetch();
  }

  Future<void> _confirmClose(Ticket t) async {
    HapticFeedback.mediumImpact();
    final bool? confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: context.colors.surface,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: Text('Clôturer le ticket', style: GoogleFonts.inter(fontWeight: FontWeight.w700, color: context.colors.text)),
        content: Text('Voulez-vous vraiment clôturer le ticket ${t.numero} ?', style: GoogleFonts.inter(color: context.colors.text2)),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: Text('Annuler', style: TextStyle(color: context.colors.text3))),
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: context.colors.danger, foregroundColor: Colors.white),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Clôturer'),
          ),
        ],
      ),
    );

    if (confirm == true) {
      try {
        await _service.updateStatut(t.id, 'FERME');
        HapticFeedback.selectionClick();
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Ticket ${t.numero} clôturé'), backgroundColor: context.colors.success));
        _fetch();
      } catch (e) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Erreur: $e'), backgroundColor: context.colors.danger));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Column(
      children: [
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
              IconButton(
                onPressed: () => _fetch(refresh: true),
                icon: _loading && _tickets.isEmpty
                    ? SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2, color: c.primary))
                    : Icon(Icons.refresh, size: 18, color: c.text3),
              ),
            ],
          ),
        ),

        // ── Search & Filter Panel Toggle ──
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
          child: Row(
            children: [
              Expanded(
                child: TextField(
                  controller: _searchController,
                  onChanged: (v) {
                    setState(() {}); 
                    if (_debounce?.isActive ?? false) _debounce!.cancel();
                    _debounce = Timer(const Duration(milliseconds: 400), () {
                      setState(() { _search = v; _page = 0; });
                      _fetch();
                    });
                  },
                  style: GoogleFonts.inter(fontSize: 13, color: c.text),
                  decoration: InputDecoration(
                    hintText: 'Rechercher par client, numéro…',
                    prefixIcon: Icon(Icons.search, size: 16, color: c.text3),
                    suffixIcon: _searchController.text.isNotEmpty
                        ? IconButton(
                            icon: Icon(Icons.clear, size: 16, color: c.text3),
                            onPressed: () {
                              HapticFeedback.selectionClick();
                              _searchController.clear();
                              if (_debounce?.isActive ?? false) _debounce!.cancel();
                              setState(() { _search = ''; _page = 0; });
                              _fetch();
                            },
                          )
                        : null,
                    isDense: true,
                    contentPadding: const EdgeInsets.symmetric(vertical: 10),
                  ),
                ),
              ),
              const SizedBox(width: 8),
              GestureDetector(
                onTap: () {
                  HapticFeedback.selectionClick();
                  setState(() => _showFilters = !_showFilters);
                },
                child: Container(
                  height: 40,
                  width: 40,
                  decoration: BoxDecoration(
                    color: _showFilters || _activeFiltersCount > 0 ? c.primarySoft : c.elevated,
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(color: _showFilters || _activeFiltersCount > 0 ? c.primary.withOpacity(0.3) : c.border),
                  ),
                  child: Stack(
                    alignment: Alignment.center,
                    children: [
                      Icon(Icons.filter_list, size: 20, color: _showFilters || _activeFiltersCount > 0 ? c.primary : c.text3),
                      if (_activeFiltersCount > 0)
                        Positioned(
                          right: 6,
                          top: 6,
                          child: Container(
                            padding: const EdgeInsets.all(2),
                            decoration: BoxDecoration(color: c.danger, shape: BoxShape.circle),
                            constraints: const BoxConstraints(minWidth: 12, minHeight: 12),
                            child: Text(
                              '$_activeFiltersCount',
                              style: const TextStyle(color: Colors.white, fontSize: 8, fontWeight: FontWeight.bold),
                              textAlign: TextAlign.center,
                            ),
                          ),
                        ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),

        // ── Grouped Filters Panel (Collapsible) ──
        if (_showFilters)
          Container(
            margin: const EdgeInsets.fromLTRB(16, 4, 16, 8),
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: c.surface,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: c.primary.withOpacity(0.12)),
              boxShadow: c.isDark ? [] : [BoxShadow(color: c.shadow, blurRadius: 10, offset: const Offset(0, 4))],
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // 1. STATUT group
                Text('STATUT', style: GoogleFonts.inter(fontSize: 9, fontWeight: FontWeight.w700, color: c.text3, letterSpacing: 0.8)),
                const SizedBox(height: 6),
                SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  child: Row(
                    children: [
                      _filterChipGroup(c, 'Tous', _statut == null, () { setState(() { _statut = null; _page = 0; }); _fetch(); }),
                      _filterChipGroup(c, 'Nouveau', _statut == 'NOUVEAU', () { setState(() { _statut = 'NOUVEAU'; _page = 0; }); _fetch(); }, color: c.statutColor('NOUVEAU')),
                      _filterChipGroup(c, 'En cours', _statut == 'EN_COURS', () { setState(() { _statut = 'EN_COURS'; _page = 0; }); _fetch(); }, color: c.statutColor('EN_COURS')),
                      _filterChipGroup(c, 'Résolu', _statut == 'RESOLU', () { setState(() { _statut = 'RESOLU'; _page = 0; }); _fetch(); }, color: c.statutColor('RESOLU')),
                      _filterChipGroup(c, 'Fermé', _statut == 'FERME', () { setState(() { _statut = 'FERME'; _page = 0; }); _fetch(); }, color: c.statutColor('FERME')),
                    ],
                  ),
                ),
                const SizedBox(height: 12),

                // 2. CANAL group
                Text('CANAL', style: GoogleFonts.inter(fontSize: 9, fontWeight: FontWeight.w700, color: c.text3, letterSpacing: 0.8)),
                const SizedBox(height: 6),
                Row(
                  children: [
                    _filterChipGroup(c, 'Tous', _canal == null, () { setState(() { _canal = null; _page = 0; }); _fetch(); }),
                    _filterChipGroup(c, 'Appel', _canal == 'CALL', () { setState(() { _canal = 'CALL'; _page = 0; }); _fetch(); }, color: c.canalColor('CALL'), icon: Icons.phone_outlined),
                    _filterChipGroup(c, 'Email', _canal == 'EMAIL', () { setState(() { _canal = 'EMAIL'; _page = 0; }); _fetch(); }, color: c.canalColor('EMAIL'), icon: Icons.mail_outlined),
                    _filterChipGroup(c, 'Web', _canal == 'WEB', () { setState(() { _canal = 'WEB'; _page = 0; }); _fetch(); }, color: c.canalColor('WEB'), icon: Icons.language_outlined),
                  ],
                ),
                const SizedBox(height: 12),

                // 3. PRIORITÉ group
                Text('PRIORITÉ', style: GoogleFonts.inter(fontSize: 9, fontWeight: FontWeight.w700, color: c.text3, letterSpacing: 0.8)),
                const SizedBox(height: 6),
                Row(
                  children: [
                    _filterChipGroup(c, 'Toutes', _priorite == null, () { setState(() { _priorite = null; _page = 0; }); _fetch(); }),
                    _filterChipGroup(c, 'P1', _priorite == 1, () { setState(() { _priorite = 1; _page = 0; }); _fetch(); }, color: c.danger),
                    _filterChipGroup(c, 'P2', _priorite == 2, () { setState(() { _priorite = 2; _page = 0; }); _fetch(); }, color: c.warning),
                    _filterChipGroup(c, 'P3', _priorite == 3, () { setState(() { _priorite = 3; _page = 0; }); _fetch(); }, color: c.success),
                  ],
                ),
                const SizedBox(height: 12),

                // 4. AVIS IA group
                Text('AVIS IA', style: GoogleFonts.inter(fontSize: 9, fontWeight: FontWeight.w700, color: c.text3, letterSpacing: 0.8)),
                const SizedBox(height: 6),
                SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  child: Row(
                    children: [
                      _filterChipGroup(c, 'Tous', _recommandationFilter == 'ALL', () { setState(() => _recommandationFilter = 'ALL'); }),
                      _filterChipGroup(c, 'Favorable', _recommandationFilter == 'FAVORABLE', () { setState(() => _recommandationFilter = 'FAVORABLE'); }, color: c.success, icon: Icons.check_circle_outline),
                      _filterChipGroup(c, 'Refusé', _recommandationFilter == 'DEFAVORABLE', () { setState(() => _recommandationFilter = 'DEFAVORABLE'); }, color: c.danger, icon: Icons.cancel_outlined),
                      _filterChipGroup(c, 'Étude', _recommandationFilter == 'ETUDE', () { setState(() => _recommandationFilter = 'ETUDE'); }, color: c.warning, icon: Icons.info_outline),
                    ],
                  ),
                ),
                const SizedBox(height: 12),

                // 4. TRI (SORT) & RESET
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('TRIER PAR', style: GoogleFonts.inter(fontSize: 9, fontWeight: FontWeight.w700, color: c.text3, letterSpacing: 0.8)),
                        const SizedBox(height: 6),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8),
                          decoration: BoxDecoration(color: c.elevated, borderRadius: BorderRadius.circular(8), border: Border.all(color: c.border)),
                          child: DropdownButton<String>(
                            value: _sort,
                            underline: const SizedBox(),
                            icon: Icon(Icons.arrow_drop_down, color: c.primary, size: 18),
                            style: GoogleFonts.inter(fontSize: 11, color: c.text, fontWeight: FontWeight.w600),
                            dropdownColor: c.surface,
                            onChanged: (v) {
                              if (v != null) {
                                HapticFeedback.selectionClick();
                                setState(() { _sort = v; _page = 0; });
                                _fetch();
                              }
                            },
                            items: const [
                              DropdownMenuItem(value: 'createdAt,desc', child: Text('↓ Plus récent')),
                              DropdownMenuItem(value: 'createdAt,asc', child: Text('↑ Plus ancien')),
                              DropdownMenuItem(value: 'priorite,asc', child: Text('↑ Priorité')),
                              DropdownMenuItem(value: 'priorite,desc', child: Text('↓ Priorité')),
                              DropdownMenuItem(value: 'statut,asc', child: Text('A→Z Statut')),
                            ],
                          ),
                        ),
                      ],
                    ),
                    if (_activeFiltersCount > 0)
                      TextButton.icon(
                        onPressed: _resetFilters,
                        icon: Icon(Icons.clear_all, size: 14, color: c.danger),
                        label: Text('Reset ($_activeFiltersCount)', style: GoogleFonts.inter(fontSize: 11, fontWeight: FontWeight.w700, color: c.danger)),
                        style: TextButton.styleFrom(
                          foregroundColor: c.danger,
                          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                          backgroundColor: c.danger.withOpacity(0.08),
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                        ),
                      ),
                  ],
                ),
              ],
            ),
          ).animate().fadeIn(duration: 200.ms).slideY(begin: -0.05, curve: Curves.easeOut),

        // ── Active Filters Indicators (when collapsed) ──
        if (!_showFilters && _activeFiltersCount > 0)
          SizedBox(
            height: 34,
            child: ListView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 16),
              children: [
                if (_statut != null)
                  _activeFilterIndicator(c, 'Statut: ${_statut!.replaceAll('_', ' ')}', () => setState(() => _statut = null), color: c.statutColor(_statut!)),
                if (_canal != null)
                  _activeFilterIndicator(c, 'Canal: $_canal', () => setState(() => _canal = null), color: c.canalColor(_canal!)),
                if (_priorite != null)
                  _activeFilterIndicator(c, 'Priorité: P$_priorite', () => setState(() => _priorite = null), color: _priorite == 1 ? c.danger : _priorite == 2 ? c.warning : c.success),
                if (_recommandationFilter != 'ALL')
                  _activeFilterIndicator(
                    c, 
                    'Avis: ${_recommandationFilter == 'FAVORABLE' ? 'Favorable' : _recommandationFilter == 'DEFAVORABLE' ? 'Refusé' : 'Étude'}', 
                    () => setState(() => _recommandationFilter = 'ALL'), 
                    color: _recommandationFilter == 'FAVORABLE' ? c.success : _recommandationFilter == 'DEFAVORABLE' ? c.danger : c.warning
                  ),
                if (_search.isNotEmpty)
                  _activeFilterIndicator(c, 'Recherche: "$_search"', () { _searchController.clear(); setState(() => _search = ''); }),
                GestureDetector(
                  onTap: _resetFilters,
                  child: Container(
                    margin: const EdgeInsets.only(right: 6, bottom: 4),
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(color: c.danger.withOpacity(0.08), borderRadius: BorderRadius.circular(6), border: Border.all(color: c.danger.withOpacity(0.2))),
                    child: Row(
                      children: [
                        Icon(Icons.clear_all, size: 10, color: c.danger),
                        const SizedBox(width: 4),
                        Text('Reset tout', style: GoogleFonts.inter(fontSize: 9.5, fontWeight: FontWeight.bold, color: c.danger)),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ).animate().fadeIn(duration: 150.ms),


        const SizedBox(height: 8),

        // ── List ──
        Expanded(
          child: _loading && _tickets.isEmpty
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
            : _filteredTickets.isEmpty
              ? Center(
                  child: Column(mainAxisSize: MainAxisSize.min, children: [
                    const Text('🔍', style: TextStyle(fontSize: 40)),
                    const SizedBox(height: 8),
                    Text('Aucun résultat', style: GoogleFonts.inter(fontWeight: FontWeight.w700, color: c.text)),
                    TextButton(onPressed: _resetFilters, child: const Text('Réinitialiser')),
                  ]),
                )
              : RefreshIndicator(
                  onRefresh: () => _fetch(refresh: true),
                  color: c.primary,
                  child: ListView.builder(
                    controller: _scrollController,
                    physics: const AlwaysScrollableScrollPhysics(),
                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                    itemCount: _filteredTickets.length + 1,
                    itemBuilder: (ctx, i) {
                      if (i == _filteredTickets.length) {
                        return _loadingMore
                            ? Padding(padding: const EdgeInsets.all(16), child: Center(child: SizedBox(width: 24, height: 24, child: CircularProgressIndicator(strokeWidth: 2, color: c.primary))))
                            : const SizedBox(height: 40);
                      }
                      return _buildTicketCard(c, _filteredTickets[i], i);
                    },
                  ),
                ),
        ),
      ],
    );
  }

  Widget _filterChipGroup(AppColorScheme c, String label, bool selected, VoidCallback onTap, {Color? color, IconData? icon}) {
    final chipColor = color ?? c.primary;
    return Padding(
      padding: const EdgeInsets.only(right: 6),
      child: GestureDetector(
        onTap: () {
          HapticFeedback.selectionClick();
          onTap();
        },
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 150),
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
          decoration: BoxDecoration(
            color: selected ? chipColor.withOpacity(0.12) : c.elevated,
            borderRadius: BorderRadius.circular(8),
            border: Border.all(color: selected ? chipColor.withOpacity(0.4) : c.border),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (icon != null) ...[
                Icon(icon, size: 12, color: selected ? chipColor : c.text3),
                const SizedBox(width: 4),
              ],
              if (label == 'P1' || label == 'P2' || label == 'P3') ...[
                Container(
                  width: 5, height: 5,
                  decoration: BoxDecoration(
                    color: color, 
                    shape: BoxShape.circle,
                    boxShadow: [BoxShadow(color: color!, blurRadius: 4, spreadRadius: 1)],
                  ),
                ),
                const SizedBox(width: 6),
              ],
              Text(label, style: GoogleFonts.inter(
                fontSize: 10.5, fontWeight: FontWeight.w600,
                color: selected ? chipColor : c.text2,
              )),
            ],
          ),
        ),
      ),
    );
  }

  Widget _activeFilterIndicator(AppColorScheme c, String text, VoidCallback onDismiss, {Color? color}) {
    final chipColor = color ?? c.primary;
    return Container(
      margin: const EdgeInsets.only(right: 6, bottom: 4),
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: chipColor.withOpacity(0.08),
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: chipColor.withOpacity(0.2)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(text, style: GoogleFonts.inter(fontSize: 9.5, fontWeight: FontWeight.w600, color: chipColor)),
          const SizedBox(width: 4),
          GestureDetector(
            onTap: () {
              HapticFeedback.selectionClick();
              onDismiss();
              _page = 0;
              _fetch();
            },
            child: Icon(Icons.close, size: 10, color: chipColor),
          ),
        ],
      ),
    );
  }

  Widget _buildTicketCard(AppColorScheme c, Ticket t, int index) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Slidable(
        key: ValueKey(t.id),
        startActionPane: ActionPane(
          motion: const ScrollMotion(),
          children: [
            SlidableAction(
              onPressed: (_) {
                HapticFeedback.mediumImpact();
                ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Ticket ${t.numero} assigné'), backgroundColor: c.primary));
              },
              backgroundColor: c.primary,
              foregroundColor: Colors.white,
              icon: Icons.person_add,
              label: 'M\'assigner',
              borderRadius: const BorderRadius.only(topLeft: Radius.circular(14), bottomLeft: Radius.circular(14)),
            ),
          ],
        ),
        endActionPane: ActionPane(
          motion: const ScrollMotion(),
          children: [
            SlidableAction(
              onPressed: (_) => _confirmClose(t),
              backgroundColor: c.danger,
              foregroundColor: Colors.white,
              icon: Icons.check_circle,
              label: 'Clôturer',
              borderRadius: const BorderRadius.only(topRight: Radius.circular(14), bottomRight: Radius.circular(14)),
            ),
          ],
        ),
        child: Container(
          decoration: BoxDecoration(
            color: c.surface,
            borderRadius: BorderRadius.circular(14),
            border: Border.all(color: c.border),
            boxShadow: c.isDark ? [] : [BoxShadow(color: c.shadow, blurRadius: 6, offset: const Offset(0, 2))],
          ),
          child: InkWell(
            borderRadius: BorderRadius.circular(14),
            onTap: () {
              HapticFeedback.lightImpact();
              Navigator.push(context, MaterialPageRoute(builder: (_) => TicketDetailScreen(ticketId: t.id)));
            },
            child: Padding(
              padding: const EdgeInsets.all(14),
              child: Row(
                children: [
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
                        size: 20,
                        color: c.canalColor(t.canal),
                      ),
                    ),
                  ),
                  const SizedBox(width: 12),
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
                          Hero(
                            tag: 'ticket-num-${t.id}', 
                            child: Material(
                              type: MaterialType.transparency, 
                              child: Text(t.numero, style: GoogleFonts.inter(fontSize: 10, fontWeight: FontWeight.w600, color: c.primary))
                            )
                          ),
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
                          if (_decisions[t.id] != null) ...[
                            const SizedBox(width: 8),
                            (() {
                              final dec = _decisions[t.id]!;
                              final finalDec = dec.finalDecision ?? dec.decision;
                              final isFavorable = finalDec == 'FAVORABLE';
                              final isDefavorable = finalDec == 'DEFAVORABLE';
                              final color = isFavorable ? c.success : (isDefavorable ? c.danger : c.warning);
                              final text = isFavorable ? '✓ Favorable' : (isDefavorable ? '✕ Refusé' : '⚠️ Étude');
                              return Container(
                                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                decoration: BoxDecoration(
                                  color: color.withOpacity(0.1),
                                  borderRadius: BorderRadius.circular(4),
                                  border: Border.all(color: color.withOpacity(0.2)),
                                ),
                                child: Text(text, style: GoogleFonts.inter(
                                  fontSize: 10, fontWeight: FontWeight.w700, color: color,
                                )),
                              );
                            })(),
                          ],
                        ]),
                      ],
                    ),
                  ),
                  Icon(Icons.chevron_right, size: 16, color: c.text3),
                ],
              ),
            ),
          ),
        ).animate().fadeIn(delay: Duration(milliseconds: 20 * (index % 10))).slideX(begin: 0.02),
      ),
    );
  }
}
