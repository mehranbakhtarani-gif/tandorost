import 'dart:ui' show FontFeature;
import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'core/session_engine.dart';
import 'features/nutrition/food_catalog.dart';
import 'features/nutrition/food_diary.dart';
import 'features/progress/progress_log.dart';

const ink = Color(0xFF17221C), muted = Color(0xFF78847B);
const canvas = Color(0xFFF4F6F0), lime = Color(0xFFB9EF45);
const green = Color(0xFF254D37), card = Colors.white;

void main() => runApp(const MFitApp());

class MFitApp extends StatelessWidget {
  const MFitApp({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(
    title: 'MFit', debugShowCheckedModeBanner: false,
    theme: ThemeData(
      useMaterial3: true, brightness: Brightness.light,
      scaffoldBackgroundColor: canvas,
      colorScheme: ColorScheme.fromSeed(seedColor: green, brightness: Brightness.light),
      inputDecorationTheme: InputDecorationTheme(
        filled: true, fillColor: const Color(0xFFF2F4EF),
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(16), borderSide: BorderSide.none),
        contentPadding: const EdgeInsets.symmetric(horizontal: 15, vertical: 14),
      ),
    ),
    home: const Directionality(textDirection: TextDirection.rtl, child: MFitShell()),
  );
}

class MFitShell extends StatefulWidget {
  const MFitShell({super.key});
  @override
  State<MFitShell> createState() => _MFitShellState();
}

class _MFitShellState extends State<MFitShell> {
  final session = SessionEngine();
  final diary = FoodDiary();
  final progress = ProgressLog();
  int tab = 0, water = 0;
  bool ready = false;
  List<FoodEntry> foodEntries = [];
  List<ProgressEntry> progressEntries = [];

  @override
  void initState() { super.initState(); _load(); }

  Future<void> _load() async {
    await session.restore();
    final prefs = await SharedPreferences.getInstance();
    final foods = await diary.readAll();
    final records = await progress.readAll();
    if (!mounted) return;
    setState(() {
      foodEntries = foods; progressEntries = records;
      water = prefs.getInt('mfit.water.today') ?? 0; ready = true;
    });
  }

  Future<void> _setWater(int value) async {
    setState(() => water = value.clamp(0, 16));
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt('mfit.water.today', water);
  }

  @override
  void dispose() { session.dispose(); super.dispose(); }

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: canvas,
    body: SafeArea(bottom: false, child: Column(children: [
      Expanded(child: AnimatedSwitcher(
        duration: const Duration(milliseconds: 280),
        switchInCurve: Curves.easeOutCubic,
        child: KeyedSubtree(key: ValueKey(tab), child: _page()),
      )),
      _dock(),
    ])),
  );

  Widget _page() {
    if (!ready) return const Center(child: CircularProgressIndicator(color: green));
    switch (tab) {
      case 1: return ActivityPage(session: session);
      case 2: return NutritionPage(diary: diary, entries: foodEntries, water: water, onWater: _setWater, onChanged: (v) => setState(() => foodEntries = v));
      case 3: return const RoutePage();
      case 4: return ProgressPage(log: progress, entries: progressEntries, onChanged: (v) => setState(() => progressEntries = v));
      default: return HomePage(session: session, foods: foodEntries,
        onActivity: () => setState(() => tab = 1),
        onNutrition: () => setState(() => tab = 2),
        onProgress: () => setState(() => tab = 4));
    }
  }

  Widget _dock() {
    const items = [
      [Icons.home_rounded, 'خانه'], [Icons.directions_run_rounded, 'فعالیت'],
      [Icons.restaurant_rounded, 'تغذیه'], [Icons.map_outlined, 'نقشه'],
      [Icons.insights_rounded, 'پیشرفت'],
    ];
    return Container(
      margin: const EdgeInsets.fromLTRB(14, 7, 14, 10),
      padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 8),
      decoration: BoxDecoration(
        color: Colors.white.withOpacity(.95), borderRadius: BorderRadius.circular(25),
        border: Border.all(color: Colors.white, width: 1.2),
        boxShadow: [BoxShadow(color: green.withOpacity(.10), blurRadius: 25, offset: const Offset(0, 8))],
      ),
      child: Row(children: List.generate(items.length, (i) {
        final active = tab == i;
        return Expanded(child: GestureDetector(
          behavior: HitTestBehavior.opaque, onTap: () => setState(() => tab = i),
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 230), curve: Curves.easeOutCubic,
            padding: const EdgeInsets.symmetric(vertical: 8),
            decoration: BoxDecoration(color: active ? lime : Colors.transparent, borderRadius: BorderRadius.circular(17)),
            child: Column(mainAxisSize: MainAxisSize.min, children: [
              Icon(items[i][0] as IconData, color: active ? ink : muted, size: 21),
              const SizedBox(height: 3),
              Text(items[i][1] as String, style: TextStyle(fontSize: 10, fontWeight: active ? FontWeight.w800 : FontWeight.w500, color: active ? ink : muted)),
            ]),
          ),
        ));
      })),
    );
  }
}

class PageHeader extends StatelessWidget {
  final String eyebrow, title;
  const PageHeader(this.eyebrow, this.title, {super.key});
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(21, 18, 21, 17),
    child: Row(children: [
      Container(width: 43, height: 43, decoration: BoxDecoration(color: lime, borderRadius: BorderRadius.circular(15)), child: const Icon(Icons.bolt_rounded, color: ink, size: 27)),
      const SizedBox(width: 11),
      Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(eyebrow.toUpperCase(), style: const TextStyle(color: muted, fontSize: 9, letterSpacing: 1.7, fontWeight: FontWeight.w800)),
        Text(title, style: const TextStyle(color: ink, fontSize: 23, fontWeight: FontWeight.w900)),
      ])),
      Container(padding: const EdgeInsets.all(11), decoration: BoxDecoration(color: card, borderRadius: BorderRadius.circular(14)), child: const Icon(Icons.notifications_none_rounded, color: green)),
    ]),
  );
}

class LiveStrip extends StatelessWidget {
  final SessionEngine session;
  const LiveStrip({super.key, required this.session});
  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: session,
    builder: (_, __) => session.isActive ? Container(
      margin: const EdgeInsets.fromLTRB(20, 0, 20, 14), padding: const EdgeInsets.symmetric(horizontal: 15, vertical: 11),
      decoration: BoxDecoration(color: green, borderRadius: BorderRadius.circular(17)),
      child: Row(children: [
        Container(width: 8, height: 8, decoration: const BoxDecoration(color: lime, shape: BoxShape.circle)),
        const SizedBox(width: 9),
        const Expanded(child: Text('تمرین در حال اجرا', style: TextStyle(color: Colors.white, fontWeight: FontWeight.w700, fontSize: 12))),
        Text(session.formatted, style: const TextStyle(color: lime, fontWeight: FontWeight.w900, fontFeatures: [FontFeature.tabularFigures()])),
      ]),
    ) : const SizedBox.shrink(),
  );
}

class HomePage extends StatelessWidget {
  final SessionEngine session;
  final List<FoodEntry> foods;
  final VoidCallback onActivity, onNutrition, onProgress;
  const HomePage({super.key, required this.session, required this.foods, required this.onActivity, required this.onNutrition, required this.onProgress});
  @override
  Widget build(BuildContext context) {
    final today = DateTime.now();
    final entries = foods.where((e) => _sameDay(e.loggedAt, today)).toList();
    final kcal = entries.fold<double>(0, (s, e) => s + e.food.forGrams(e.grams)['kcal']!);
    return ListView(padding: const EdgeInsets.only(bottom: 22), children: [
      const PageHeader('SPORT PERFORMANCE', 'روزت رو قدرتمند بساز'),
      LiveStrip(session: session),
      Padding(padding: const EdgeInsets.symmetric(horizontal: 18), child: Container(
        padding: const EdgeInsets.all(21),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(28),
          gradient: const LinearGradient(begin: Alignment.topRight, end: Alignment.bottomLeft, colors: [Color(0xFF315F42), Color(0xFF173D2B), Color(0xFF10291E)]),
          boxShadow: [BoxShadow(color: green.withOpacity(.18), blurRadius: 20, offset: const Offset(0, 9))],
        ),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [const Expanded(child: Text('YOUR DAILY EDGE', style: TextStyle(color: lime, fontSize: 10, letterSpacing: 1.7, fontWeight: FontWeight.w900)), _tag('KEEP MOVING')]),
          const SizedBox(height: 17),
          const Text('Push your\nlimits.', style: TextStyle(color: Colors.white, fontSize: 35, height: 1.02, fontWeight: FontWeight.w900, letterSpacing: -1)),
          const SizedBox(height: 9),
          const Text('هر روز کمی قوی‌تر از دیروز.', style: TextStyle(color: Colors.white70, fontSize: 12)),
          const SizedBox(height: 20),
          Row(children: [
            Expanded(child: FilledButton.icon(onPressed: onActivity, icon: const Icon(Icons.play_arrow_rounded), label: const Text('شروع تمرین'), style: FilledButton.styleFrom(backgroundColor: lime, foregroundColor: ink, padding: const EdgeInsets.symmetric(vertical: 13)))),
            const SizedBox(width: 10),
            _heroMetric('TODAY', entries.length.toString(), 'وعده ثبت‌شده'),
          ]),
        ]),
      )),
      const SizedBox(height: 23),
      const SectionLabel('عملکرد امروز', 'DAILY PERFORMANCE'),
      Padding(padding: const EdgeInsets.symmetric(horizontal: 18), child: Row(children: [
        const Expanded(child: MetricTile(icon: Icons.directions_walk_rounded, label: 'قدم‌ها', value: '—', unit: 'اتصال حسگر در مرحله بعد', accent: green)),
        const SizedBox(width: 9),
        Expanded(child: MetricTile(icon: Icons.local_fire_department_rounded, label: 'کالری غذا', value: '—', unit: 'از ثبت تغذیه', accent: Color(0xFFEA914A))),
        const SizedBox(width: 9),
        Expanded(child: AnimatedBuilder(animation: session, builder: (_, __) => MetricTile(icon: Icons.timer_outlined, label: 'تمرین', value: session.formatted.substring(0, 5), unit: 'زمان جلسه', accent: const Color(0xFF4E8CC7)))),
      ])),
      const SizedBox(height: 22),
      Padding(padding: const EdgeInsets.symmetric(horizontal: 20), child: Row(children: [const Expanded(child: SectionLabel('مسیر پیشرفت', 'YOUR PROGRESS')), TextButton(onPressed: onProgress, child: const Text('تحلیل کامل'))])),
      Padding(padding: const EdgeInsets.symmetric(horizontal: 18), child: GestureDetector(
        onTap: onProgress,
        child: Container(padding: const EdgeInsets.all(17), decoration: BoxDecoration(color: card, borderRadius: BorderRadius.circular(23)), child: const Row(children: [
          Icon(Icons.insights_rounded, color: green, size: 29), SizedBox(width: 13),
          Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text('اندازه‌گیری کن، بهتر شو', style: TextStyle(color: ink, fontWeight: FontWeight.w900, fontSize: 14)),
            SizedBox(height: 4), Text('وزن، دور کمر و روند فعالیتت را ثبت کن', style: TextStyle(color: muted, fontSize: 11)),
          ])),
          Icon(Icons.arrow_back_ios_new_rounded, size: 15, color: green),
        ])),
      )),
      const SizedBox(height: 20),
      Padding(padding: const EdgeInsets.symmetric(horizontal: 20), child: Row(children: [const Expanded(child: SectionLabel('تغذیه امروز', 'SMART NUTRITION')), TextButton(onPressed: onNutrition, child: const Text('ثبت غذا'))])),
      Padding(padding: const EdgeInsets.symmetric(horizontal: 18), child: GestureDetector(
        onTap: onNutrition,
        child: Container(padding: const EdgeInsets.all(17), decoration: BoxDecoration(color: card, borderRadius: BorderRadius.circular(22)), child: Row(children: [
          const Icon(Icons.restaurant_menu_rounded, color: green, size: 25), const SizedBox(width: 12),
          Expanded(child: Text(entries.length.toString() + ' مورد ثبت شده · ' + kcal.round().toString() + ' کیلوکالری', style: const TextStyle(color: ink, fontWeight: FontWeight.w700, fontSize: 12))),
          const Icon(Icons.add_circle, color: green),
        ])),
      )),
    ]);
  }
}

class ActivityPage extends StatelessWidget {
  final SessionEngine session;
  const ActivityPage({super.key, required this.session});
  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: session,
    builder: (_, __) => ListView(padding: const EdgeInsets.only(bottom: 24), children: [
      const PageHeader('MOVE WITH PURPOSE', 'فعالیت'),
      Padding(padding: const EdgeInsets.symmetric(horizontal: 18), child: Container(
        padding: const EdgeInsets.all(22), decoration: BoxDecoration(color: green, borderRadius: BorderRadius.circular(28)),
        child: Column(children: [
          Row(children: [const Expanded(child: Text('LIVE WORKOUT', style: TextStyle(color: lime, letterSpacing: 1.5, fontSize: 10, fontWeight: FontWeight.w900)), _tag(session.isActive ? 'LIVE' : 'READY')]),
          const SizedBox(height: 23),
          Text(session.formatted, style: const TextStyle(color: Colors.white, fontSize: 48, fontWeight: FontWeight.w300, letterSpacing: 2, fontFeatures: [FontFeature.tabularFigures()])),
          const SizedBox(height: 7),
          const Text('مدت زمان جلسه تمرین', style: TextStyle(color: Colors.white70, fontSize: 11)),
          const SizedBox(height: 24),
          Row(children: [
            Expanded(child: FilledButton.icon(onPressed: session.isActive ? session.pause : session.start, icon: Icon(session.isActive ? Icons.pause_rounded : Icons.play_arrow_rounded), label: Text(session.isActive ? 'توقف موقت' : 'شروع تمرین'), style: FilledButton.styleFrom(backgroundColor: lime, foregroundColor: ink, padding: const EdgeInsets.symmetric(vertical: 14)))),
            const SizedBox(width: 10),
            IconButton.filledTonal(onPressed: session.reset, icon: const Icon(Icons.restart_alt_rounded), style: IconButton.styleFrom(backgroundColor: Colors.white.withOpacity(.15), foregroundColor: Colors.white)),
          ]),
        ]),
      )),
      const SizedBox(height: 22),
      const SectionLabel('انتخاب نوع فعالیت', 'CHOOSE YOUR SESSION'),
      const Padding(padding: EdgeInsets.symmetric(horizontal: 18), child: Column(children: [
        ActivityOption(icon: Icons.directions_walk_rounded, title: 'پیاده‌روی', detail: 'هوازی · شدت متوسط', time: '۲۰–۴۰ دقیقه'),
        ActivityOption(icon: Icons.directions_run_rounded, title: 'دویدن', detail: 'استقامت و ظرفیت هوازی', time: '۱۵–۳۰ دقیقه'),
        ActivityOption(icon: Icons.fitness_center_rounded, title: 'تمرین قدرتی', detail: 'قدرت و عضله‌سازی', time: '۲۰ دقیقه'),
        ActivityOption(icon: Icons.self_improvement_rounded, title: 'کشش و انعطاف', detail: 'ریکاوری و دامنه حرکتی', time: '۸–۱۲ دقیقه'),
      ])),
      const Padding(padding: EdgeInsets.fromLTRB(22, 12, 22, 0), child: Text('زمان‌سنج مرکزی است و با جابه‌جایی تب متوقف نمی‌شود. ثبت GPS و اعلان دائمی اندروید در مرحله بعد فعال می‌شوند.', style: TextStyle(color: muted, fontSize: 11, height: 1.5))),
    ]),
  );
}

class NutritionPage extends StatefulWidget {
  final FoodDiary diary; final List<FoodEntry> entries; final int water;
  final ValueChanged<int> onWater; final ValueChanged<List<FoodEntry>> onChanged;
  const NutritionPage({super.key, required this.diary, required this.entries, required this.water, required this.onWater, required this.onChanged});
  @override
  State<NutritionPage> createState() => _NutritionPageState();
}

class _NutritionPageState extends State<NutritionPage> {
  final search = TextEditingController(), grams = TextEditingController(text: '100');
  FoodNutrition? selected; bool saving = false;
  @override
  void dispose() { search.dispose(); grams.dispose(); super.dispose(); }
  List<FoodEntry> get todayEntries => widget.entries.where((e) => _sameDay(e.loggedAt, DateTime.now())).toList();
  Map<String, double> get totals {
    final result = {'kcal': 0.0, 'protein': 0.0, 'carbs': 0.0, 'fat': 0.0, 'fiber': 0.0};
    for (final e in todayEntries) { e.food.forGrams(e.grams).forEach((k, v) => result[k] = result[k]! + v); }
    return result;
  }

  @override
  Widget build(BuildContext context) {
    final t = totals, matches = FoodCatalog.search(search.text);
    final amount = double.tryParse(grams.text) ?? 0;
    final preview = selected?.forGrams(amount) ?? const <String, double>{};
    return ListView(padding: const EdgeInsets.only(bottom: 25), children: [
      const PageHeader('EAT SMARTER', 'تغذیه هوشمند'),
      Padding(padding: const EdgeInsets.symmetric(horizontal: 18), child: Container(
        padding: const EdgeInsets.all(20), decoration: BoxDecoration(color: green, borderRadius: BorderRadius.circular(26)),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const Text('دریافت امروز', style: TextStyle(color: Colors.white70, fontSize: 12)),
          const SizedBox(height: 5),
          Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
            Text(t['kcal']!.round().toString(), style: const TextStyle(color: Colors.white, fontSize: 38, fontWeight: FontWeight.w900)),
            const Padding(padding: EdgeInsets.only(bottom: 7, right: 7), child: Text('kcal', style: TextStyle(color: lime, fontWeight: FontWeight.w800))),
            const Spacer(), Text(todayEntries.length.toString() + ' ثبت امروز', style: const TextStyle(color: Colors.white70, fontSize: 11)),
          ]),
          const SizedBox(height: 13),
          Row(children: [
            _MacroValue('پروتئین', t['protein']!, 'g', lime),
            _MacroValue('کربوهیدرات', t['carbs']!, 'g', Colors.white),
            _MacroValue('چربی', t['fat']!, 'g', const Color(0xFFFFC17B)),
            _MacroValue('فیبر', t['fiber']!, 'g', const Color(0xFF9ED8FF)),
          ]),
        ]),
      )),
      const SizedBox(height: 22), const SectionLabel('ثبت خوراکی', 'FOOD DATABASE'),
      Padding(padding: const EdgeInsets.symmetric(horizontal: 18), child: Container(
        padding: const EdgeInsets.all(17), decoration: BoxDecoration(color: card, borderRadius: BorderRadius.circular(23)),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          TextField(controller: search, onChanged: (_) => setState(() => selected = null), decoration: const InputDecoration(prefixIcon: Icon(Icons.search_rounded, color: green), hintText: 'نام غذا را جست‌وجو کن؛ مثل تخم‌مرغ یا برنج')),
          if (search.text.trim().isNotEmpty) ...[
            const SizedBox(height: 8),
            if (matches.isEmpty) const Padding(padding: EdgeInsets.all(9), child: Text('در پایگاه فعلی پیدا نشد؛ بانک غذایی قابل گسترش است.', style: TextStyle(color: muted, fontSize: 11)))
            else ...matches.map((food) => ListTile(
              dense: true, contentPadding: EdgeInsets.zero,
              title: Text(food.name, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 13)),
              subtitle: Text(food.kcal.round().toString() + ' kcal در ۱۰۰ گرم', style: const TextStyle(color: muted, fontSize: 10)),
              trailing: Icon(selected?.id == food.id ? Icons.check_circle : Icons.add_circle_outline, color: green),
              onTap: () => setState(() => selected = food),
            )),
          ],
          if (selected != null) ...[
            const Divider(height: 22),
            Text(selected!.name, style: const TextStyle(fontWeight: FontWeight.w900, color: green)),
            const SizedBox(height: 9),
            Row(children: [
              Expanded(child: TextField(controller: grams, keyboardType: const TextInputType.numberWithOptions(decimal: true), onChanged: (_) => setState(() {}), decoration: const InputDecoration(labelText: 'مقدار مصرف', suffixText: 'گرم'))),
              const SizedBox(width: 10),
              FilledButton(onPressed: saving || amount <= 0 ? null : _addEntry, style: FilledButton.styleFrom(backgroundColor: lime, foregroundColor: ink), child: const Text('ثبت')),
            ]),
            const SizedBox(height: 12),
            Wrap(spacing: 7, runSpacing: 7, children: [
              _chip('کالری', preview['kcal'] ?? 0, 'kcal'), _chip('پروتئین', preview['protein'] ?? 0, 'g'),
              _chip('کربوهیدرات', preview['carbs'] ?? 0, 'g'), _chip('چربی', preview['fat'] ?? 0, 'g'), _chip('فیبر', preview['fiber'] ?? 0, 'g'),
            ]),
            const SizedBox(height: 8),
            Text(selected!.sourceLabel, style: const TextStyle(color: muted, fontSize: 9)),
          ],
        ]),
      )),
      const SizedBox(height: 22),
      Padding(padding: const EdgeInsets.symmetric(horizontal: 20), child: Row(children: [const Expanded(child: SectionLabel('خوراکی‌های امروز', 'TODAY LOG')), Text(todayEntries.length.toString() + ' مورد', style: const TextStyle(color: muted, fontSize: 10))])),
      Padding(padding: const EdgeInsets.symmetric(horizontal: 18), child: Column(children: todayEntries.isEmpty
        ? [const EmptyHint(icon: Icons.no_meals_rounded, text: 'هنوز خوراکی ثبت نکردی. اولین مورد را اضافه کن.')]
        : todayEntries.map((e) {
          final n = e.food.forGrams(e.grams);
          return Container(
            margin: const EdgeInsets.only(bottom: 8), padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(color: card, borderRadius: BorderRadius.circular(17)),
            child: Row(children: [
              Container(width: 38, height: 38, decoration: BoxDecoration(color: lime.withOpacity(.3), borderRadius: BorderRadius.circular(12)), child: const Icon(Icons.restaurant_menu_rounded, color: green)),
              const SizedBox(width: 10),
              Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(e.food.name, style: const TextStyle(color: ink, fontWeight: FontWeight.w800, fontSize: 12)),
                Text(e.grams.round().toString() + ' گرم · P ' + n['protein']!.toStringAsFixed(1) + 'g · C ' + n['carbs']!.toStringAsFixed(1) + 'g · F ' + n['fat']!.toStringAsFixed(1) + 'g', style: const TextStyle(color: muted, fontSize: 9)),
              ])),
              Text(n['kcal']!.round().toString() + ' kcal', style: const TextStyle(color: green, fontWeight: FontWeight.w900, fontSize: 11)),
            ]),
          );
        }).toList())),
      const SizedBox(height: 20), const SectionLabel('آب مصرفی', 'HYDRATION'),
      Padding(padding: const EdgeInsets.symmetric(horizontal: 18), child: Container(
        padding: const EdgeInsets.all(16), decoration: BoxDecoration(color: card, borderRadius: BorderRadius.circular(20)),
        child: Row(children: [
          const Icon(Icons.water_drop_rounded, color: Color(0xFF4B9BD1), size: 25), const SizedBox(width: 10),
          Expanded(child: Text(widget.water.toString() + ' لیوان از هدف ۸ لیوان', style: const TextStyle(color: ink, fontWeight: FontWeight.w700, fontSize: 12))),
          IconButton(onPressed: () => widget.onWater(widget.water - 1), icon: const Icon(Icons.remove_circle_outline, color: muted)),
          IconButton(onPressed: () => widget.onWater(widget.water + 1), icon: const Icon(Icons.add_circle, color: green)),
        ]),
      )),
      const Padding(padding: EdgeInsets.fromLTRB(22, 12, 22, 0), child: Text('مقادیر از پایگاه داخلی و تخمینی‌اند؛ نوع پخت، برند و وزن خوراکی می‌توانند نتیجه را تغییر دهند.', style: TextStyle(color: muted, fontSize: 10, height: 1.5))),
    ]);
  }

  Widget _chip(String label, double value, String unit) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 7),
    decoration: BoxDecoration(color: const Color(0xFFF1F5E9), borderRadius: BorderRadius.circular(11)),
    child: Text(label + ' ' + value.toStringAsFixed(1) + unit, style: const TextStyle(color: green, fontSize: 10, fontWeight: FontWeight.w700)),
  );

  Future<void> _addEntry() async {
    if (selected == null) return;
    setState(() => saving = true);
    await widget.diary.add(FoodEntry(food: selected!, grams: double.parse(grams.text), loggedAt: DateTime.now()));
    final updated = await widget.diary.readAll();
    if (!mounted) return;
    widget.onChanged(updated);
    setState(() { saving = false; search.clear(); grams.text = '100'; selected = null; });
    ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('خوراکی ثبت شد و ارزش غذایی محاسبه شد.')));
  }
}

class ProgressPage extends StatefulWidget {
  final ProgressLog log; final List<ProgressEntry> entries;
  final ValueChanged<List<ProgressEntry>> onChanged;
  const ProgressPage({super.key, required this.log, required this.entries, required this.onChanged});
  @override
  State<ProgressPage> createState() => _ProgressPageState();
}

class _ProgressPageState extends State<ProgressPage> {
  ProgressMetric metric = ProgressMetric.weightKg;
  final input = TextEditingController();
  bool saving = false;
  @override
  void dispose() { input.dispose(); super.dispose(); }
  List<ProgressEntry> get values => widget.entries.where((e) => e.metric == metric).toList()..sort((a,b) => a.date.compareTo(b.date));

  @override
  Widget build(BuildContext context) {
    final data = values;
    final delta = data.length < 2 ? null : data.last.value - data.first.value;
    final unit = _metricUnit(metric);
    return ListView(padding: const EdgeInsets.only(bottom: 24), children: [
      const PageHeader('TRACK YOUR EVOLUTION', 'تحلیل پیشرفت'),
      Padding(padding: const EdgeInsets.symmetric(horizontal: 18), child: Container(
        padding: const EdgeInsets.all(18), decoration: BoxDecoration(color: green, borderRadius: BorderRadius.circular(24)),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const Text('YOUR TREND', style: TextStyle(color: lime, letterSpacing: 1.6, fontWeight: FontWeight.w900, fontSize: 10)),
          const SizedBox(height: 9),
          Text(_metricLabel(metric), style: const TextStyle(color: Colors.white, fontSize: 21, fontWeight: FontWeight.w900)),
          const SizedBox(height: 5),
          Text(data.isEmpty ? 'هنوز داده‌ای ثبت نشده' : data.length.toString() + ' اندازه‌گیری ثبت شده', style: const TextStyle(color: Colors.white70, fontSize: 11)),
          const SizedBox(height: 14),
          Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
            Text(data.isEmpty ? '—' : data.last.value.toStringAsFixed(1), style: const TextStyle(color: Colors.white, fontSize: 38, fontWeight: FontWeight.w900)),
            Padding(padding: const EdgeInsets.only(bottom: 7, right: 6), child: Text(unit, style: const TextStyle(color: lime, fontWeight: FontWeight.w800))),
            const Spacer(),
            if (delta != null) Text((delta > 0 ? '+' : '') + delta.toStringAsFixed(1) + ' ' + unit, style: const TextStyle(color: lime, fontWeight: FontWeight.w800, fontSize: 12)),
          ]),
        ]),
      )),
      const SizedBox(height: 20), const SectionLabel('انتخاب شاخص', 'SELECT A METRIC'),
      Padding(padding: const EdgeInsets.symmetric(horizontal: 18), child: Wrap(spacing: 8, runSpacing: 8, children: ProgressMetric.values.map((m) => ChoiceChip(
        label: Text(_metricLabel(m), style: TextStyle(color: metric == m ? ink : muted, fontSize: 11, fontWeight: FontWeight.w700)),
        selected: metric == m, selectedColor: lime, backgroundColor: card, side: BorderSide.none,
        onSelected: (_) => setState(() => metric = m),
      )).toList())),
      const SizedBox(height: 18),
      Padding(padding: const EdgeInsets.symmetric(horizontal: 18), child: Container(
        padding: const EdgeInsets.fromLTRB(16, 17, 16, 12),
        decoration: BoxDecoration(color: card, borderRadius: BorderRadius.circular(22)),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const Text('روند ثبت‌شده', style: TextStyle(color: ink, fontWeight: FontWeight.w900, fontSize: 14)),
          const SizedBox(height: 15),
          SizedBox(height: 170, width: double.infinity, child: data.length < 2
            ? const EmptyHint(icon: Icons.show_chart_rounded, text: 'برای نمایش نمودار، حداقل دو اندازه‌گیری ثبت کن.')
            : CustomPaint(painter: ProgressChartPainter(data.map((e) => e.value).toList()))),
          if (data.length >= 2) Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
            Text(_shortDate(data.first.date), style: const TextStyle(color: muted, fontSize: 9)),
            Text(_shortDate(data.last.date), style: const TextStyle(color: muted, fontSize: 9)),
          ]),
        ]),
      )),
      const SizedBox(height: 20), const SectionLabel('ثبت اندازه‌گیری جدید', 'ADD REAL DATA'),
      Padding(padding: const EdgeInsets.symmetric(horizontal: 18), child: Container(
        padding: const EdgeInsets.all(17), decoration: BoxDecoration(color: card, borderRadius: BorderRadius.circular(22)),
        child: Row(children: [
          Expanded(child: TextField(controller: input, keyboardType: const TextInputType.numberWithOptions(decimal: true), decoration: InputDecoration(labelText: _metricLabel(metric), suffixText: unit))),
          const SizedBox(width: 10),
          FilledButton(onPressed: saving ? null : _save, style: FilledButton.styleFrom(backgroundColor: lime, foregroundColor: ink, padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 15)),
            child: saving ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)) : const Text('ثبت')),
        ]),
      )),
      const Padding(padding: EdgeInsets.fromLTRB(22, 12, 22, 0), child: Text('نمودار فقط از داده‌هایی ساخته می‌شود که خودت ثبت کرده‌ای؛ تا قبل از داشتن داده کافی، روند ساختگی نمایش داده نمی‌شود.', style: TextStyle(color: muted, fontSize: 10, height: 1.5))),
      if (data.isNotEmpty) ...[
        const SizedBox(height: 18), const SectionLabel('سوابق', 'MEASUREMENT HISTORY'),
        Padding(padding: const EdgeInsets.symmetric(horizontal: 18), child: Column(children: data.reversed.take(8).map((e) => Container(
          margin: const EdgeInsets.only(bottom: 7), padding: const EdgeInsets.all(13),
          decoration: BoxDecoration(color: card, borderRadius: BorderRadius.circular(15)),
          child: Row(children: [Expanded(child: Text(_shortDate(e.date), style: const TextStyle(color: muted, fontSize: 11))), Text(e.value.toStringAsFixed(1) + ' ' + unit, style: const TextStyle(color: green, fontWeight: FontWeight.w900, fontSize: 12))]),
        )).toList())),
      ],
    ]);
  }

  Future<void> _save() async {
    final value = double.tryParse(input.text.trim().replaceAll(',', '.'));
    if (value == null || value <= 0) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('یک مقدار معتبر وارد کن.')));
      return;
    }
    setState(() => saving = true);
    await widget.log.add(ProgressEntry(date: DateTime.now(), metric: metric, value: value));
    final all = await widget.log.readAll();
    if (!mounted) return;
    widget.onChanged(all);
    setState(() { saving = false; input.clear(); });
    ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('اندازه‌گیری ذخیره شد.')));
  }
}

class RoutePage extends StatelessWidget {
  const RoutePage({super.key});
  @override
  Widget build(BuildContext context) => ListView(padding: const EdgeInsets.only(bottom: 24), children: [
    const PageHeader('EXPLORE YOUR ROUTE', 'نقشه و مسیر'),
    Padding(padding: const EdgeInsets.symmetric(horizontal: 18), child: Container(
      height: 430, clipBehavior: Clip.antiAlias,
      decoration: BoxDecoration(color: const Color(0xFFE7EDE2), borderRadius: BorderRadius.circular(27)),
      child: Stack(children: [
        Positioned.fill(child: CustomPaint(painter: RoutePainter())),
        Positioned(right: 16, top: 16, child: _tag('ROUTE PREVIEW', dark: true)),
        Positioned(right: 17, left: 17, bottom: 17, child: Container(
          padding: const EdgeInsets.all(16), decoration: BoxDecoration(color: Colors.white.withOpacity(.96), borderRadius: BorderRadius.circular(19)),
          child: const Row(children: [
            Icon(Icons.route_rounded, color: green, size: 26), SizedBox(width: 12),
            Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text('مسیر فعالیت', style: TextStyle(color: ink, fontWeight: FontWeight.w900)),
              Text('نمونه نمایشی · بدون داده GPS', style: TextStyle(color: muted, fontSize: 10)),
            ])),
            Icon(Icons.lock_clock_rounded, color: muted),
          ]),
        )),
      ]),
    )),
    const Padding(padding: EdgeInsets.fromLTRB(22, 14, 22, 0), child: Text('اتصال GPS واقعی، ثبت پس‌زمینه و مسیر روی نقشه در مرحله سرویس اندروید فعال می‌شود.', style: TextStyle(color: muted, fontSize: 11, height: 1.5))),
  ]);
}

class SectionLabel extends StatelessWidget {
  final String title, eyebrow;
  const SectionLabel(this.title, this.eyebrow, {super.key});
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(21, 0, 21, 12),
    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text(eyebrow, style: const TextStyle(color: muted, fontSize: 9, letterSpacing: 1.5, fontWeight: FontWeight.w900)),
      const SizedBox(height: 3), Text(title, style: const TextStyle(color: ink, fontSize: 17, fontWeight: FontWeight.w900)),
    ]),
  );
}

class MetricTile extends StatelessWidget {
  final IconData icon; final String label, value, unit; final Color accent;
  const MetricTile({super.key, required this.icon, required this.label, required this.value, required this.unit, required this.accent});
  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(12), decoration: BoxDecoration(color: card, borderRadius: BorderRadius.circular(19)),
    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Icon(icon, color: accent, size: 20), const SizedBox(height: 12),
      Text(value, style: const TextStyle(color: ink, fontSize: 18, fontWeight: FontWeight.w900)),
      Text(label, style: const TextStyle(color: ink, fontSize: 10, fontWeight: FontWeight.w800)),
      const SizedBox(height: 4), Text(unit, maxLines: 2, style: const TextStyle(color: muted, fontSize: 8, height: 1.3)),
    ]),
  );
}

class ActivityOption extends StatelessWidget {
  final IconData icon; final String title, detail, time;
  const ActivityOption({super.key, required this.icon, required this.title, required this.detail, required this.time});
  @override
  Widget build(BuildContext context) => Container(
    margin: const EdgeInsets.only(bottom: 9), padding: const EdgeInsets.all(14),
    decoration: BoxDecoration(color: card, borderRadius: BorderRadius.circular(18)),
    child: Row(children: [
      Container(width: 43, height: 43, decoration: BoxDecoration(color: lime.withOpacity(.35), borderRadius: BorderRadius.circular(14)), child: Icon(icon, color: green)),
      const SizedBox(width: 11),
      Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(title, style: const TextStyle(color: ink, fontWeight: FontWeight.w900, fontSize: 13)),
        Text(detail, style: const TextStyle(color: muted, fontSize: 10)),
      ])),
      Text(time, style: const TextStyle(color: green, fontSize: 10, fontWeight: FontWeight.w800)),
    ]),
  );
}

class _MacroValue extends StatelessWidget {
  final String title, unit; final double value; final Color color;
  const _MacroValue(this.title, this.value, this.unit, this.color);
  @override
  Widget build(BuildContext context) => Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
    Text(value.toStringAsFixed(1), style: TextStyle(color: color, fontSize: 17, fontWeight: FontWeight.w900)),
    Text(title, style: const TextStyle(color: Colors.white70, fontSize: 9)),
    Text(unit, style: const TextStyle(color: Colors.white38, fontSize: 8)),
  ]));
}

class EmptyHint extends StatelessWidget {
  final IconData icon; final String text;
  const EmptyHint({super.key, required this.icon, required this.text});
  @override
  Widget build(BuildContext context) => Center(child: Padding(
    padding: const EdgeInsets.symmetric(vertical: 18, horizontal: 12),
    child: Column(mainAxisSize: MainAxisSize.min, children: [
      Icon(icon, color: muted, size: 27), const SizedBox(height: 8),
      Text(text, textAlign: TextAlign.center, style: const TextStyle(color: muted, fontSize: 10, height: 1.5)),
    ]),
  ));
}

class ProgressChartPainter extends CustomPainter {
  final List<double> values;
  ProgressChartPainter(this.values);
  @override
  void paint(Canvas canvas, Size size) {
    if (values.length < 2) return;
    const left = 7.0, right = 7.0, top = 12.0, bottom = 12.0;
    final w = size.width - left - right, h = size.height - top - bottom;
    final minV = values.reduce((a,b) => a < b ? a : b), maxV = values.reduce((a,b) => a > b ? a : b);
    final range = (maxV - minV).abs() < .0001 ? 1.0 : maxV - minV;
    final points = List<Offset>.generate(values.length, (i) => Offset(left + w * i / (values.length - 1), top + h * (1 - (values[i] - minV) / range)));
    final grid = Paint()..color = const Color(0xFFE8EDE5)..strokeWidth = 1;
    for (var i = 0; i < 4; i++) { final y = top + h * i / 3; canvas.drawLine(Offset(left, y), Offset(size.width - right, y), grid); }
    final path = Path()..moveTo(points.first.dx, points.first.dy);
    for (var i = 1; i < points.length; i++) {
      final a = points[i - 1], b = points[i], mid = (a.dx + b.dx) / 2;
      path.cubicTo(mid, a.dy, mid, b.dy, b.dx, b.dy);
    }
    final fill = Path.from(path)..lineTo(points.last.dx, size.height - bottom)..lineTo(points.first.dx, size.height - bottom)..close();
    canvas.drawPath(fill, Paint()..shader = const LinearGradient(begin: Alignment.topCenter, end: Alignment.bottomCenter, colors: [Color(0x66B9EF45), Color(0x00B9EF45)]).createShader(Offset.zero & size));
    canvas.drawPath(path, Paint()..color = green..style = PaintingStyle.stroke..strokeWidth = 3..strokeCap = StrokeCap.round..strokeJoin = StrokeJoin.round);
    for (final p in points) { canvas.drawCircle(p, 4.5, Paint()..color = lime); canvas.drawCircle(p, 2.2, Paint()..color = green); }
  }
  @override
  bool shouldRepaint(covariant ProgressChartPainter oldDelegate) => oldDelegate.values != values;
}

class RoutePainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final road = Paint()..color = const Color(0xFFD4DCCF)..strokeWidth = 2;
    for (var i = 1; i < 8; i++) { final y = size.height * i / 8; canvas.drawLine(Offset(0, y), Offset(size.width, y + (i.isEven ? 19 : -15)), road); }
    for (var i = 1; i < 7; i++) { final x = size.width * i / 7; canvas.drawLine(Offset(x, 0), Offset(x + (i.isEven ? 14 : -18), size.height), road); }
    final path = Path()..moveTo(size.width * .16, size.height * .75)
      ..cubicTo(size.width * .25, size.height * .48, size.width * .45, size.height * .82, size.width * .54, size.height * .55)
      ..cubicTo(size.width * .63, size.height * .28, size.width * .79, size.height * .56, size.width * .84, size.height * .29);
    canvas.drawPath(path, Paint()..color = green.withOpacity(.15)..style = PaintingStyle.stroke..strokeWidth = 11..strokeCap = StrokeCap.round);
    canvas.drawPath(path, Paint()..color = green..style = PaintingStyle.stroke..strokeWidth = 4..strokeCap = StrokeCap.round);
    canvas.drawCircle(Offset(size.width * .16, size.height * .75), 8, Paint()..color = green);
    canvas.drawCircle(Offset(size.width * .84, size.height * .29), 10, Paint()..color = lime);
  }
  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

Widget _tag(String text, {bool dark = false}) => Container(
  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
  decoration: BoxDecoration(color: dark ? green : Colors.white.withOpacity(.13), borderRadius: BorderRadius.circular(30), border: Border.all(color: dark ? green : Colors.white24)),
  child: Text(text, style: TextStyle(color: dark ? Colors.white : lime, fontSize: 9, fontWeight: FontWeight.w900, letterSpacing: .8)),
);

Widget _heroMetric(String eyebrow, String value, String label) => Container(
  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 9),
  decoration: BoxDecoration(color: Colors.white.withOpacity(.10), borderRadius: BorderRadius.circular(14)),
  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
    Text(eyebrow, style: const TextStyle(color: lime, fontSize: 8, letterSpacing: 1.2, fontWeight: FontWeight.w900)),
    Text(value, style: const TextStyle(color: Colors.white, fontSize: 17, fontWeight: FontWeight.w900)),
    Text(label, style: const TextStyle(color: Colors.white70, fontSize: 8)),
  ]),
);

bool _sameDay(DateTime a, DateTime b) => a.year == b.year && a.month == b.month && a.day == b.day;
String _shortDate(DateTime d) => d.month.toString() + '/' + d.day.toString();
String _metricLabel(ProgressMetric m) {
  switch (m) {
    case ProgressMetric.weightKg: return 'وزن';
    case ProgressMetric.waistCm: return 'دور کمر';
    case ProgressMetric.steps: return 'قدم‌ها';
    case ProgressMetric.activeCalories: return 'کالری فعال';
    case ProgressMetric.distanceKm: return 'مسافت';
    case ProgressMetric.sleepHours: return 'خواب';
  }
}
String _metricUnit(ProgressMetric m) {
  switch (m) {
    case ProgressMetric.weightKg: return 'kg';
    case ProgressMetric.waistCm: return 'cm';
    case ProgressMetric.steps: return 'قدم';
    case ProgressMetric.activeCalories: return 'kcal';
    case ProgressMetric.distanceKm: return 'km';
    case ProgressMetric.sleepHours: return 'ساعت';
  }
}
