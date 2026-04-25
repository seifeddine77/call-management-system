import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:flutter_animate/flutter_animate.dart';
import '../config/colors.dart';
import '../models/rule.dart';
import '../services/rules_service.dart';

class RulesScreen extends StatefulWidget {
  const RulesScreen({super.key});

  @override
  State<RulesScreen> createState() => _RulesScreenState();
}

class _RulesScreenState extends State<RulesScreen> with TickerProviderStateMixin {
  final _service = RulesService();
  List<Rule> _rules = [];
  ReferenceData? _refs;
  Rule? _selected;
  bool _loading = true;
  List<RuleVersion> _history = [];

  // Tab state
  late TabController _tabCtrl;

  // Test panel
  final Map<String, String> _testForm = {};
  EvaluateResponse? _testResult;
  bool _testing = false;

  // Paramétrage
  List<RefItem> _refItems = [];
  bool _refLoading = false;

  @override
  void initState() {
    super.initState();
    _tabCtrl = TabController(length: 2, vsync: this);
    _tabCtrl.addListener(() {
      if (_tabCtrl.index == 1 && _refItems.isEmpty) _fetchRefItems();
    });
    _fetch();
  }

  @override
  void dispose() {
    _tabCtrl.dispose();
    super.dispose();
  }

  Future<void> _fetch() async {
    setState(() => _loading = true);
    try {
      final results = await Future.wait([
        _service.getRules(),
        _service.getReferences(),
      ]);
      setState(() {
        _rules = results[0] as List<Rule>;
        _refs = results[1] as ReferenceData;
        _loading = false;
      });
    } catch (e) {
      debugPrint('[RulesScreen] Fetch error: $e');
      setState(() => _loading = false);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Erreur chargement: $e'), backgroundColor: context.colors.danger),
        );
      }
    }
  }

  Future<void> _fetchRefItems() async {
    setState(() => _refLoading = true);
    try {
      final items = await _service.getRefItems();
      setState(() { _refItems = items; _refLoading = false; });
    } catch (e) {
      setState(() => _refLoading = false);
      if (mounted) _snack('Erreur chargement références', isError: true);
    }
  }

  Future<void> _selectRule(Rule rule) async {
    setState(() => _selected = _selected?.id == rule.id ? null : rule);
    if (_selected != null) {
      try {
        final h = await _service.getHistory(rule.id);
        setState(() => _history = h);
      } catch (_) {
        setState(() => _history = []);
      }
    }
  }

  Future<void> _toggleRule(Rule rule) async {
    try {
      final updated = await _service.toggleRule(rule.id);
      setState(() {
        _rules = _rules.map((r) => r.id == rule.id ? updated : r).toList();
        if (_selected?.id == rule.id) _selected = updated;
      });
      _snack(updated.actif ? 'Règle activée' : 'Règle désactivée');
    } catch (_) {
      _snack('Erreur', isError: true);
    }
  }

  Future<void> _deleteRule(Rule rule) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => _ConfirmDeleteDialog(rule: rule),
    );
    if (confirmed != true) return;
    try {
      await _service.deleteRule(rule.id);
      setState(() {
        _rules.removeWhere((r) => r.id == rule.id);
        if (_selected?.id == rule.id) _selected = null;
      });
      _snack('Règle supprimée');
    } catch (_) {
      _snack('Erreur suppression', isError: true);
    }
  }

  Future<void> _createRule() async {
    final result = await showDialog<Map<String, dynamic>>(
      context: context,
      builder: (ctx) => _RuleFormDialog(refs: _refs, title: 'Nouvelle règle'),
    );
    if (result == null) return;
    try {
      final created = await _service.createRule(result);
      setState(() => _rules.insert(0, created));
      _snack('Règle créée ✅');
    } catch (e) {
      _snack('Erreur création: $e', isError: true);
    }
  }

  Future<void> _editRule(Rule rule) async {
    final result = await showDialog<Map<String, dynamic>>(
      context: context,
      builder: (ctx) => _RuleFormDialog(
        refs: _refs,
        title: 'Modifier — ${rule.nom}',
        initial: rule,
      ),
    );
    if (result == null) return;
    try {
      final updated = await _service.updateRule(rule.id, result);
      setState(() {
        _rules = _rules.map((r) => r.id == rule.id ? updated : r).toList();
        _selected = updated;
      });
      // refresh history
      try {
        final h = await _service.getHistory(rule.id);
        setState(() => _history = h);
      } catch (_) {}
      _snack('Règle modifiée → v${updated.version}');
    } catch (e) {
      _snack('Erreur modification', isError: true);
    }
  }

  Future<void> _testRules() async {
    setState(() { _testing = true; _testResult = null; });
    try {
      final data = <String, dynamic>{};
      for (final fd in (_refs?.fields ?? [])) {
        final val = _testForm[fd.field];
        if (val != null && val.isNotEmpty) {
          data[fd.field] = fd.type == 'number' ? int.tryParse(val) ?? val : val;
        }
      }
      final result = await _service.evaluate(data);
      setState(() => _testResult = result);
    } catch (_) {
      _snack('Erreur évaluation', isError: true);
    } finally {
      if (mounted) setState(() => _testing = false);
    }
  }

  // ── RefItem handlers ──

  Future<void> _addRefItem(String category, String code, String label, String desc) async {
    try {
      await _service.createRefItem({
        'category': category,
        'code': code.toUpperCase(),
        'label': label,
        if (desc.isNotEmpty) 'description': desc,
      });
      _snack('Référence ajoutée ✅');
      _fetchRefItems();
      _fetch(); // refresh refs in dropdowns
    } catch (_) {
      _snack('Erreur — code déjà existant ?', isError: true);
    }
  }

  Future<void> _toggleRefItem(String id) async {
    try {
      await _service.toggleRefItem(id);
      _fetchRefItems();
      _fetch();
    } catch (_) {
      _snack('Erreur', isError: true);
    }
  }

  Future<void> _deleteRefItem(String id) async {
    try {
      await _service.deleteRefItem(id);
      _snack('Référence supprimée');
      _fetchRefItems();
      _fetch();
    } catch (_) {
      _snack('Erreur suppression', isError: true);
    }
  }

  void _snack(String msg, {bool isError = false}) {
    if (!mounted) return;
    final c = context.colors;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      content: Text(msg, style: GoogleFonts.inter(fontSize: 12)),
      backgroundColor: isError ? c.danger : c.success,
      duration: const Duration(seconds: 2),
    ));
  }

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Scaffold(
      appBar: AppBar(
        title: Text('Moteur de règles', style: GoogleFonts.inter(fontSize: 18, fontWeight: FontWeight.w800)),
        actions: [
          IconButton(onPressed: _fetch, icon: Icon(Icons.refresh, size: 18, color: c.text3)),
        ],
        bottom: TabBar(
          controller: _tabCtrl,
          indicatorColor: c.primary,
          labelColor: c.primary,
          unselectedLabelColor: c.text3,
          labelStyle: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.w600),
          tabs: const [
            Tab(text: '📐 Règles'),
            Tab(text: '⚙️ Paramétrage'),
          ],
        ),
      ),
      floatingActionButton: _tabCtrl.index == 0 ? FloatingActionButton(
        onPressed: _createRule,
        backgroundColor: c.primary,
        child: const Icon(Icons.add, color: Colors.white),
      ) : null,
      body: _loading
        ? Center(child: CircularProgressIndicator(color: c.primary))
        : TabBarView(
            controller: _tabCtrl,
            children: [
              _buildRulesTab(c),
              _buildParamsTab(c),
            ],
          ),
    );
  }

  // ═══════════════════════════════════════════════
  //  TAB 1 — Règles
  // ═══════════════════════════════════════════════

  Widget _buildRulesTab(AppColorScheme c) {
    return RefreshIndicator(
      onRefresh: _fetch,
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text('${_rules.length} règle${_rules.length > 1 ? 's' : ''} configurées',
            style: GoogleFonts.inter(fontSize: 10, color: c.text3)),
          const SizedBox(height: 12),

          // Rules list
          if (_rules.isEmpty)
            _emptyState(c, '📐', 'Aucune règle', 'Créez votre première règle métier')
          else
            ..._rules.asMap().entries.map((e) => _buildRuleCard(c, e.value, e.key)),

          // Selected rule detail + history
          if (_selected != null) ...[
            const SizedBox(height: 12),
            _buildRuleDetail(c, _selected!),
            const SizedBox(height: 8),
            _buildHistoryPanel(c),
          ],

          // Test panel
          const SizedBox(height: 16),
          _buildTestPanel(c),
          const SizedBox(height: 80),
        ],
      ),
    );
  }

  Widget _buildRuleCard(AppColorScheme c, Rule rule, int index) {
    final isSelected = _selected?.id == rule.id;
    final actionColor = c.decisionColor(rule.action);

    return GestureDetector(
      onTap: () => _selectRule(rule),
      child: Container(
        margin: const EdgeInsets.only(bottom: 8),
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: isSelected ? c.primarySoft : c.surface,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: isSelected ? c.primary.withOpacity(0.3) : c.border),
          boxShadow: c.isDark ? [] : [BoxShadow(color: c.shadow, blurRadius: 6, offset: const Offset(0, 2))],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(children: [
              Expanded(child: Text(rule.nom, style: GoogleFonts.inter(fontSize: 13, fontWeight: FontWeight.w700, color: c.text))),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(color: c.elevated, borderRadius: BorderRadius.circular(4)),
                child: Text('v${rule.version}', style: GoogleFonts.inter(fontSize: 9, color: c.text3)),
              ),
              const SizedBox(width: 6),
              // Toggle switch
              GestureDetector(
                onTap: () => _toggleRule(rule),
                child: Container(
                  width: 36, height: 20,
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(10),
                    color: rule.actif ? c.success.withOpacity(0.3) : c.text3.withOpacity(0.2),
                  ),
                  child: AnimatedAlign(
                    alignment: rule.actif ? Alignment.centerRight : Alignment.centerLeft,
                    duration: const Duration(milliseconds: 200),
                    child: Container(
                      width: 16, height: 16, margin: const EdgeInsets.all(2),
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: rule.actif ? c.success : c.text3,
                      ),
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 4),
              // Delete button
              GestureDetector(
                onTap: () => _deleteRule(rule),
                child: Container(
                  width: 28, height: 28,
                  decoration: BoxDecoration(borderRadius: BorderRadius.circular(6)),
                  child: Icon(Icons.delete_outline, size: 16, color: c.text3),
                ),
              ),
            ]),
            const SizedBox(height: 4),
            Text(rule.description, style: GoogleFonts.inter(fontSize: 11, color: c.text2), maxLines: 1, overflow: TextOverflow.ellipsis),
            const SizedBox(height: 6),
            Row(children: [
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(color: actionColor.withOpacity(0.1), borderRadius: BorderRadius.circular(4), border: Border.all(color: actionColor.withOpacity(0.2))),
                child: Text(rule.action, style: GoogleFonts.inter(fontSize: 9, fontWeight: FontWeight.w600, color: actionColor)),
              ),
              const SizedBox(width: 6),
              Text('P${rule.priorite}', style: GoogleFonts.inter(fontSize: 9, color: c.text3)),
              const SizedBox(width: 6),
              Text('${rule.conditions.conditions.length} cond.', style: GoogleFonts.inter(fontSize: 9, color: c.text3)),
            ]),
          ],
        ),
      ),
    ).animate().fadeIn(delay: Duration(milliseconds: 50 * index));
  }

  Widget _buildRuleDetail(AppColorScheme c, Rule rule) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: c.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: c.primary.withOpacity(0.2)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text('CONDITIONS', style: GoogleFonts.inter(fontSize: 9, fontWeight: FontWeight.w600, color: c.text3, letterSpacing: 1)),
              GestureDetector(
                onTap: () => _editRule(rule),
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                  decoration: BoxDecoration(
                    color: c.primarySoft,
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(color: c.primary.withOpacity(0.2)),
                  ),
                  child: Row(mainAxisSize: MainAxisSize.min, children: [
                    Icon(Icons.edit, size: 12, color: c.primary),
                    const SizedBox(width: 4),
                    Text('Modifier', style: GoogleFonts.inter(fontSize: 10, fontWeight: FontWeight.w600, color: c.primary)),
                  ]),
                ),
              ),
            ],
          ),
          const SizedBox(height: 4),
          Text('Opérateur : ${rule.conditions.operator}', style: GoogleFonts.inter(fontSize: 10, color: c.text3)),
          const SizedBox(height: 8),
          ...rule.conditions.conditions.asMap().entries.map((e) {
            final cond = e.value;
            final isFirst = e.key == 0;
            return Container(
              margin: const EdgeInsets.only(bottom: 4),
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(color: c.elevated, borderRadius: BorderRadius.circular(8), border: Border.all(color: c.borderSubtle)),
              child: Row(children: [
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(
                    color: isFirst ? c.primarySoft : c.canalCall.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(4),
                  ),
                  child: Text(isFirst ? 'SI' : rule.conditions.operator, style: GoogleFonts.inter(fontSize: 9, fontWeight: FontWeight.w600, color: isFirst ? c.primary : c.canalCall)),
                ),
                const SizedBox(width: 8),
                Expanded(child: Text.rich(TextSpan(children: [
                  TextSpan(text: cond.field, style: GoogleFonts.inter(fontSize: 11, color: c.text)),
                  TextSpan(text: ' ${cond.operator} ', style: GoogleFonts.inter(fontSize: 11, fontWeight: FontWeight.w700, color: c.primary)),
                  TextSpan(text: '"${cond.value}"', style: GoogleFonts.inter(fontSize: 11, color: c.success)),
                ]))),
              ]),
            );
          }),
          // ALORS block
          Container(
            margin: const EdgeInsets.only(top: 4),
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(color: c.elevated, borderRadius: BorderRadius.circular(8), border: Border.all(color: c.borderSubtle)),
            child: Row(children: [
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(color: c.decisionColor(rule.action).withOpacity(0.1), borderRadius: BorderRadius.circular(4)),
                child: Text('ALORS', style: GoogleFonts.inter(fontSize: 9, fontWeight: FontWeight.w600, color: c.decisionColor(rule.action))),
              ),
              const SizedBox(width: 8),
              Text(rule.action, style: GoogleFonts.inter(fontSize: 11, fontWeight: FontWeight.w600, color: c.text)),
              if (rule.actionMotif.isNotEmpty) ...[
                const SizedBox(width: 6),
                Flexible(child: Text(rule.actionMotif, style: GoogleFonts.inter(fontSize: 10, color: c.text3), overflow: TextOverflow.ellipsis)),
              ],
            ]),
          ),
        ],
      ),
    ).animate().fadeIn();
  }

  Widget _buildHistoryPanel(AppColorScheme c) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: c.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: c.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('📜 Historique', style: GoogleFonts.inter(fontSize: 13, fontWeight: FontWeight.w700, color: c.text)),
          const SizedBox(height: 8),
          if (_history.isEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 12),
              child: Text('Pas encore de modifications', style: GoogleFonts.inter(fontSize: 11, color: c.text3), textAlign: TextAlign.center),
            )
          else
            ..._history.map((h) => Container(
              margin: const EdgeInsets.only(bottom: 4),
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(color: c.elevated, borderRadius: BorderRadius.circular(8), border: Border.all(color: c.borderSubtle)),
              child: Row(children: [
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(color: c.primarySoft, borderRadius: BorderRadius.circular(4)),
                  child: Text('v${h.version}', style: GoogleFonts.inter(fontSize: 9, fontWeight: FontWeight.w600, color: c.primary)),
                ),
                const SizedBox(width: 8),
                Expanded(child: Text(h.action, style: GoogleFonts.inter(fontSize: 11, color: c.text2))),
                if (h.changedByName != null)
                  Text(h.changedByName!, style: GoogleFonts.inter(fontSize: 9, color: c.text3)),
                if (h.changedAt != null) ...[
                  const SizedBox(width: 4),
                  Text(_formatDate(h.changedAt!), style: GoogleFonts.inter(fontSize: 9, color: c.text3)),
                ],
              ]),
            )),
        ],
      ),
    ).animate().fadeIn(delay: 100.ms);
  }

  Widget _buildTestPanel(AppColorScheme c) {
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
          Text('🧪 Tester les règles', style: GoogleFonts.inter(fontSize: 13, fontWeight: FontWeight.w700, color: c.text)),
          const SizedBox(height: 12),
          if (_refs != null)
            Wrap(spacing: 8, runSpacing: 8, children: _refs!.fields.where((f) => f.field != 'transcriptionText').map((fd) =>
              SizedBox(
                width: 140,
                child: fd.type == 'enum' && fd.values != null && fd.values!.isNotEmpty
                  ? DropdownButtonFormField<String>(
                      decoration: InputDecoration(labelText: fd.label, isDense: true, contentPadding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8)),
                      value: _testForm[fd.field]?.isNotEmpty == true ? _testForm[fd.field] : null,
                      items: [
                        const DropdownMenuItem(value: '', child: Text('—')),
                        ...fd.values!.map((v) => DropdownMenuItem(value: v, child: Text(v, style: GoogleFonts.inter(fontSize: 11)))),
                      ],
                      onChanged: (v) => _testForm[fd.field] = v ?? '',
                      style: GoogleFonts.inter(fontSize: 11, color: c.text),
                      dropdownColor: c.elevated,
                    )
                  : TextField(
                      keyboardType: fd.type == 'number' ? TextInputType.number : TextInputType.text,
                      style: GoogleFonts.inter(fontSize: 11, color: c.text),
                      decoration: InputDecoration(labelText: fd.label, isDense: true, contentPadding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8)),
                      onChanged: (v) => _testForm[fd.field] = v,
                    ),
              ),
            ).toList()),
          const SizedBox(height: 12),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton(
              onPressed: _testing ? null : _testRules,
              child: Text(_testing ? '⏳ Evaluation...' : '▶ Évaluer', style: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.w600)),
            ),
          ),
          if (_testResult != null) ...[
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: c.decisionColor(_testResult!.decision).withOpacity(0.05),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: c.decisionColor(_testResult!.decision).withOpacity(0.2)),
              ),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text('${_testResult!.decision} — Score: ${_testResult!.score.toStringAsFixed(0)}%',
                  style: GoogleFonts.inter(fontSize: 14, fontWeight: FontWeight.w800, color: c.decisionColor(_testResult!.decision))),
                if (_testResult!.motif != null) Text(_testResult!.motif!, style: GoogleFonts.inter(fontSize: 11, color: c.text2, fontStyle: FontStyle.italic)),
                Text('${_testResult!.matchedRules} règle(s) · ${_testResult!.rulesApplied.join(", ")}${_testResult!.ambiguous ? ' · ⚠ Ambigu' : ''}',
                  style: GoogleFonts.inter(fontSize: 10, color: c.text3)),
              ]),
            ),
          ],
        ],
      ),
    ).animate().fadeIn(delay: 200.ms);
  }

  // ═══════════════════════════════════════════════
  //  TAB 2 — Paramétrage
  // ═══════════════════════════════════════════════

  static const _refCategories = [
    {'key': 'TYPE_PIECE', 'label': 'Types de pièces (garanties)', 'icon': '🔧'},
    {'key': 'CATEGORIE_CLIENT', 'label': 'Catégories client', 'icon': '👥'},
    {'key': 'TYPE_SINISTRE', 'label': 'Types de sinistre (exclusions)', 'icon': '⚡'},
    {'key': 'FIELD_DEFINITION', 'label': 'Champs disponibles (conditions)', 'icon': '📋'},
  ];

  Widget _buildParamsTab(AppColorScheme c) {
    if (_refLoading) {
      return const Center(child: CircularProgressIndicator());
    }
    return RefreshIndicator(
      onRefresh: _fetchRefItems,
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: _refCategories.map((cat) {
          final items = _refItems.where((r) => r.category == cat['key']).toList();
          return _buildRefCategory(c, cat, items);
        }).toList(),
      ),
    );
  }

  Widget _buildRefCategory(AppColorScheme c, Map<String, String> cat, List<RefItem> items) {
    final activeCount = items.where((i) => i.actif).length;
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: c.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: c.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(children: [
            Text(cat['icon']!, style: const TextStyle(fontSize: 16)),
            const SizedBox(width: 8),
            Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(cat['label']!, style: GoogleFonts.inter(fontSize: 13, fontWeight: FontWeight.w700, color: c.text)),
              Text('$activeCount actifs / ${items.length} total', style: GoogleFonts.inter(fontSize: 9, color: c.text3)),
            ])),
            GestureDetector(
              onTap: () => _showAddRefDialog(cat['key']!),
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                decoration: BoxDecoration(
                  color: c.success.withOpacity(0.1),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: c.success.withOpacity(0.2)),
                ),
                child: Row(mainAxisSize: MainAxisSize.min, children: [
                  Icon(Icons.add, size: 12, color: c.success),
                  const SizedBox(width: 4),
                  Text('Ajouter', style: GoogleFonts.inter(fontSize: 10, fontWeight: FontWeight.w600, color: c.success)),
                ]),
              ),
            ),
          ]),
          const SizedBox(height: 10),
          if (items.isEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 12),
              child: Text('Aucun élément — cliquez Ajouter', style: GoogleFonts.inter(fontSize: 11, color: c.text3), textAlign: TextAlign.center),
            )
          else
            ...items.map((item) => Container(
              margin: const EdgeInsets.only(bottom: 4),
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: item.actif ? c.elevated : c.elevated.withOpacity(0.4),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: c.borderSubtle),
              ),
              child: Row(children: [
                Container(
                  width: 80,
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(color: c.primarySoft, borderRadius: BorderRadius.circular(4)),
                  child: Text(item.code, style: GoogleFonts.inter(fontSize: 9, fontWeight: FontWeight.w600, color: c.primary), textAlign: TextAlign.center),
                ),
                const SizedBox(width: 8),
                Expanded(child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(item.label, style: GoogleFonts.inter(fontSize: 11, fontWeight: FontWeight.w600, color: item.actif ? c.text : c.text3)),
                    if (item.description != null && item.description!.isNotEmpty)
                      Text(item.description!, style: GoogleFonts.inter(fontSize: 9, color: c.text3), maxLines: 1, overflow: TextOverflow.ellipsis),
                  ],
                )),
                GestureDetector(
                  onTap: () => _toggleRefItem(item.id),
                  child: Container(
                    width: 32, height: 18,
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(9),
                      color: item.actif ? c.success.withOpacity(0.3) : c.text3.withOpacity(0.2),
                    ),
                    child: AnimatedAlign(
                      alignment: item.actif ? Alignment.centerRight : Alignment.centerLeft,
                      duration: const Duration(milliseconds: 200),
                      child: Container(
                        width: 14, height: 14, margin: const EdgeInsets.all(2),
                        decoration: BoxDecoration(shape: BoxShape.circle, color: item.actif ? c.success : c.text3),
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 4),
                GestureDetector(
                  onTap: () => _deleteRefItem(item.id),
                  child: Icon(Icons.delete_outline, size: 16, color: c.text3),
                ),
              ]),
            )),
        ],
      ),
    ).animate().fadeIn(delay: 100.ms);
  }

  void _showAddRefDialog(String category) {
    final codeCtrl = TextEditingController();
    final labelCtrl = TextEditingController();
    final descCtrl = TextEditingController();
    final c = context.colors;

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: c.surface,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: Text('Ajouter une référence', style: GoogleFonts.inter(fontSize: 16, fontWeight: FontWeight.w700, color: c.text)),
        content: Column(mainAxisSize: MainAxisSize.min, children: [
          TextField(
            controller: codeCtrl,
            decoration: InputDecoration(labelText: 'Code (ex: HYDRAULIQUE)', isDense: true),
            style: GoogleFonts.inter(fontSize: 12, color: c.text),
            textCapitalization: TextCapitalization.characters,
          ),
          const SizedBox(height: 8),
          TextField(
            controller: labelCtrl,
            decoration: InputDecoration(labelText: 'Libellé', isDense: true),
            style: GoogleFonts.inter(fontSize: 12, color: c.text),
          ),
          const SizedBox(height: 8),
          TextField(
            controller: descCtrl,
            decoration: InputDecoration(labelText: 'Description (optionnel)', isDense: true),
            style: GoogleFonts.inter(fontSize: 12, color: c.text),
          ),
        ]),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: Text('Annuler', style: GoogleFonts.inter(color: c.text3))),
          ElevatedButton(
            onPressed: () {
              if (codeCtrl.text.trim().isEmpty || labelCtrl.text.trim().isEmpty) {
                ScaffoldMessenger.of(ctx).showSnackBar(SnackBar(content: const Text('Code et libellé obligatoires'), backgroundColor: c.warning));
                return;
              }
              Navigator.pop(ctx);
              _addRefItem(category, codeCtrl.text.trim(), labelCtrl.text.trim(), descCtrl.text.trim());
            },
            child: const Text('Ajouter'),
          ),
        ],
      ),
    );
  }

  // ── Helpers ──

  Widget _emptyState(AppColorScheme c, String icon, String title, String subtitle) {
    return Container(
      padding: const EdgeInsets.all(32),
      decoration: BoxDecoration(
        color: c.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: c.border),
      ),
      child: Column(children: [
        Text(icon, style: const TextStyle(fontSize: 32)),
        const SizedBox(height: 8),
        Text(title, style: GoogleFonts.inter(fontSize: 14, fontWeight: FontWeight.w700, color: c.text)),
        Text(subtitle, style: GoogleFonts.inter(fontSize: 11, color: c.text3)),
      ]),
    );
  }

  String _formatDate(String iso) {
    try {
      final d = DateTime.parse(iso);
      return '${d.day.toString().padLeft(2, '0')}/${d.month.toString().padLeft(2, '0')}/${d.year}';
    } catch (_) {
      return iso;
    }
  }
}

// ═══════════════════════════════════════════════════
//  Rule Form Dialog (Create + Edit)
// ═══════════════════════════════════════════════════

class _RuleFormDialog extends StatefulWidget {
  final ReferenceData? refs;
  final String title;
  final Rule? initial;

  const _RuleFormDialog({required this.refs, required this.title, this.initial});

  @override
  State<_RuleFormDialog> createState() => _RuleFormDialogState();
}

class _RuleFormDialogState extends State<_RuleFormDialog> {
  late TextEditingController _nomCtrl;
  late TextEditingController _descCtrl;
  late TextEditingController _motifCtrl;
  late TextEditingController _prioCtrl;
  String _action = 'FAVORABLE';
  String _condOperator = 'AND';
  List<Map<String, String>> _conditions = [];

  @override
  void initState() {
    super.initState();
    final init = widget.initial;
    _nomCtrl = TextEditingController(text: init?.nom ?? '');
    _descCtrl = TextEditingController(text: init?.description ?? '');
    _motifCtrl = TextEditingController(text: init?.actionMotif ?? '');
    _prioCtrl = TextEditingController(text: (init?.priorite ?? 10).toString());
    _action = init?.action ?? 'FAVORABLE';
    _condOperator = init?.conditions.operator ?? 'AND';
    _conditions = init?.conditions.conditions.map((c) => {'field': c.field, 'operator': c.operator, 'value': c.value}).toList()
        ?? [{'field': '', 'operator': '=', 'value': ''}];
  }

  @override
  void dispose() {
    _nomCtrl.dispose();
    _descCtrl.dispose();
    _motifCtrl.dispose();
    _prioCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Dialog(
      backgroundColor: c.surface,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 500, maxHeight: 600),
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(widget.title, style: GoogleFonts.inter(fontSize: 16, fontWeight: FontWeight.w800, color: c.text)),
              const SizedBox(height: 16),
              Flexible(child: SingleChildScrollView(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  // Nom + Priorité
                  Row(children: [
                    Expanded(child: TextField(
                      controller: _nomCtrl,
                      decoration: InputDecoration(labelText: 'Nom *', isDense: true),
                      style: GoogleFonts.inter(fontSize: 12, color: c.text),
                    )),
                    const SizedBox(width: 10),
                    SizedBox(width: 70, child: TextField(
                      controller: _prioCtrl,
                      decoration: InputDecoration(labelText: 'Priorité', isDense: true),
                      keyboardType: TextInputType.number,
                      style: GoogleFonts.inter(fontSize: 12, color: c.text),
                    )),
                  ]),
                  const SizedBox(height: 10),
                  // Description
                  TextField(
                    controller: _descCtrl,
                    decoration: InputDecoration(labelText: 'Description *', isDense: true),
                    style: GoogleFonts.inter(fontSize: 12, color: c.text),
                  ),
                  const SizedBox(height: 10),
                  // Action + Motif
                  Row(children: [
                    Expanded(child: DropdownButtonFormField<String>(
                      decoration: InputDecoration(labelText: 'Action', isDense: true),
                      value: _action,
                      items: ['FAVORABLE', 'DEFAVORABLE', 'ETUDE'].map((a) => DropdownMenuItem(value: a, child: Text(a, style: GoogleFonts.inter(fontSize: 12)))).toList(),
                      onChanged: (v) => setState(() => _action = v!),
                      style: GoogleFonts.inter(fontSize: 12, color: c.text),
                      dropdownColor: c.elevated,
                    )),
                    const SizedBox(width: 10),
                    Expanded(child: TextField(
                      controller: _motifCtrl,
                      decoration: InputDecoration(labelText: 'Motif action', isDense: true),
                      style: GoogleFonts.inter(fontSize: 12, color: c.text),
                    )),
                  ]),
                  const SizedBox(height: 16),
                  // Conditions
                  Row(children: [
                    Text('Conditions (${_conditions.length}) *', style: GoogleFonts.inter(fontSize: 11, fontWeight: FontWeight.w600, color: c.text)),
                    const SizedBox(width: 8),
                    DropdownButton<String>(
                      value: _condOperator,
                      underline: const SizedBox(),
                      style: GoogleFonts.inter(fontSize: 10, color: c.primary),
                      items: ['AND', 'OR'].map((o) => DropdownMenuItem(value: o, child: Text(o == 'AND' ? 'ET (AND)' : 'OU (OR)'))).toList(),
                      onChanged: (v) => setState(() => _condOperator = v!),
                    ),
                  ]),
                  ..._conditions.asMap().entries.map((e) => _buildConditionRow(c, e.key, e.value)),
                  TextButton.icon(
                    onPressed: () => setState(() => _conditions.add({'field': '', 'operator': '=', 'value': ''})),
                    icon: Icon(Icons.add, size: 14, color: c.primary),
                    label: Text('+ Condition', style: GoogleFonts.inter(fontSize: 10, color: c.primary)),
                  ),
                ]),
              )),
              const SizedBox(height: 12),
              Divider(color: c.border),
              const SizedBox(height: 8),
              Row(mainAxisAlignment: MainAxisAlignment.end, children: [
                TextButton(
                  onPressed: () => Navigator.pop(context),
                  child: Text('Annuler', style: GoogleFonts.inter(color: c.text3)),
                ),
                const SizedBox(width: 8),
                ElevatedButton(
                  onPressed: _submit,
                  child: Text(widget.initial != null ? 'Enregistrer' : 'Créer la règle', style: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.w600)),
                ),
              ]),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildConditionRow(AppColorScheme c, int index, Map<String, String> cond) {
    final fields = widget.refs?.fields ?? [];
    final fieldDef = fields.where((f) => f.field == cond['field']).firstOrNull;
    final isEnum = fieldDef?.type == 'enum' && fieldDef?.values != null && fieldDef!.values!.isNotEmpty;

    return Padding(
      padding: const EdgeInsets.only(bottom: 6),
      child: Row(children: [
        // Field dropdown
        Expanded(flex: 3, child: DropdownButtonFormField<String>(
          decoration: InputDecoration(isDense: true, contentPadding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6)),
          value: cond['field']?.isNotEmpty == true ? cond['field'] : null,
          hint: Text('Champ…', style: GoogleFonts.inter(fontSize: 10)),
          items: fields.map((f) => DropdownMenuItem(value: f.field, child: Text(f.label, style: GoogleFonts.inter(fontSize: 10)))).toList(),
          onChanged: (v) => setState(() { cond['field'] = v ?? ''; cond['value'] = ''; }),
          style: GoogleFonts.inter(fontSize: 10, color: c.text),
          dropdownColor: c.elevated,
        )),
        const SizedBox(width: 4),
        // Operator
        SizedBox(width: 55, child: DropdownButtonFormField<String>(
          decoration: InputDecoration(isDense: true, contentPadding: const EdgeInsets.symmetric(horizontal: 4, vertical: 6)),
          value: cond['operator'] ?? '=',
          items: ['=', '!=', '<', '>', '<=', '>='].map((o) => DropdownMenuItem(value: o, child: Text(o, style: GoogleFonts.inter(fontSize: 10)))).toList(),
          onChanged: (v) => setState(() => cond['operator'] = v ?? '='),
          style: GoogleFonts.inter(fontSize: 10, color: c.text),
          dropdownColor: c.elevated,
        )),
        const SizedBox(width: 4),
        // Value (enum or text)
        Expanded(flex: 3, child: isEnum
          ? DropdownButtonFormField<String>(
              decoration: InputDecoration(isDense: true, contentPadding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6)),
              value: cond['value']?.isNotEmpty == true ? cond['value'] : null,
              hint: Text('Valeur…', style: GoogleFonts.inter(fontSize: 10)),
              items: fieldDef!.values!.map((v) => DropdownMenuItem(value: v, child: Text(v, style: GoogleFonts.inter(fontSize: 10)))).toList(),
              onChanged: (v) => setState(() => cond['value'] = v ?? ''),
              style: GoogleFonts.inter(fontSize: 10, color: c.text),
              dropdownColor: c.elevated,
            )
          : TextField(
              keyboardType: fieldDef?.type == 'number' ? TextInputType.number : TextInputType.text,
              decoration: InputDecoration(hintText: fieldDef?.type == 'number' ? 'Ex: 5' : 'Valeur', isDense: true, contentPadding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6)),
              style: GoogleFonts.inter(fontSize: 10, color: c.text),
              controller: TextEditingController(text: cond['value'] ?? ''),
              onChanged: (v) => cond['value'] = v,
            ),
        ),
        // Delete condition
        GestureDetector(
          onTap: () {
            if (_conditions.length > 1) {
              setState(() => _conditions.removeAt(index));
            }
          },
          child: Padding(
            padding: const EdgeInsets.all(4),
            child: Icon(Icons.close, size: 14, color: c.text3),
          ),
        ),
      ]),
    );
  }

  void _submit() {
    if (_nomCtrl.text.trim().isEmpty || _descCtrl.text.trim().isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: const Text('Nom et description obligatoires'), backgroundColor: context.colors.warning),
      );
      return;
    }
    if (_conditions.any((c) => (c['field'] ?? '').isEmpty || (c['value'] ?? '').isEmpty)) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: const Text('Tous les champs et valeurs des conditions sont obligatoires'), backgroundColor: context.colors.warning),
      );
      return;
    }
    Navigator.pop(context, {
      'nom': _nomCtrl.text.trim(),
      'description': _descCtrl.text.trim(),
      'action': _action,
      'actionMotif': _motifCtrl.text.trim(),
      'priorite': int.tryParse(_prioCtrl.text) ?? 10,
      'conditions': {
        'operator': _condOperator,
        'conditions': _conditions.map((c) => {
          'field': c['field'],
          'operator': c['operator'],
          'value': c['value'],
        }).toList(),
      },
    });
  }
}

// ═══════════════════════════════════════════════════
//  Delete Confirmation Dialog
// ═══════════════════════════════════════════════════

class _ConfirmDeleteDialog extends StatelessWidget {
  final Rule rule;
  const _ConfirmDeleteDialog({required this.rule});

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return AlertDialog(
      backgroundColor: c.surface,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      title: Row(children: [
        Container(
          width: 36, height: 36,
          decoration: BoxDecoration(color: c.danger.withOpacity(0.1), borderRadius: BorderRadius.circular(10)),
          child: Icon(Icons.warning_amber, size: 20, color: c.danger),
        ),
        const SizedBox(width: 10),
        Text('Supprimer ?', style: GoogleFonts.inter(fontSize: 16, fontWeight: FontWeight.w700, color: c.text)),
      ]),
      content: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(rule.nom, style: GoogleFonts.inter(fontSize: 13, fontWeight: FontWeight.w600, color: c.danger)),
        Text('v${rule.version}', style: GoogleFonts.inter(fontSize: 10, color: c.text3)),
        const SizedBox(height: 8),
        Text('Cette action est irréversible. La règle ne sera plus appliquée aux futures évaluations.',
          style: GoogleFonts.inter(fontSize: 11, color: c.text3)),
      ]),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context, false), child: Text('Annuler', style: GoogleFonts.inter(color: c.text3))),
        ElevatedButton(
          style: ElevatedButton.styleFrom(backgroundColor: c.danger),
          onPressed: () => Navigator.pop(context, true),
          child: Text('🗑 Supprimer', style: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.w600, color: Colors.white)),
        ),
      ],
    );
  }
}
