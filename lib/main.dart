import 'dart:async';
import 'package:flutter/material.dart';
import 'package:share_plus/share_plus.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'core/session_engine.dart';
import 'core/gps_tracker.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart' hide Path;
import 'features/nutrition/food_catalog.dart';
import 'features/nutrition/food_diary.dart';
import 'features/nutrition/food_search_repository.dart';
import 'features/progress/progress_log.dart';
import 'features/progress/route_share_card.dart';

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
  final gps = GpsTracker();
  final diary = FoodDiary();
  final progress = ProgressLog();
  int tab = 0, water = 0;
  bool ready = false;
  List<FoodEntry> foodEntries = [];
  List<ProgressEntry> progressEntries = [];

  @override
  void initState() { super.initState(); _load(); gps.initialize(); }

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
    setState(() => water = value.clamp(0, 16).toInt());
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt('mfit.water.today', water);
  }

  @override
  void dispose() { session.dispose(); gps.dispose(); super.dispose(); }

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
      case 1: return ActivityPage(session: session, gps: gps, progressEntries: progressEntries);
      case 2: return NutritionPage(diary: diary, entries: foodEntries, water: water, onWater: _setWater, onChanged: (v) => setState(() => foodEntries = v));
      case 3: return RoutePage(tracker: gps);
      case 4: return ProgressPage(log: progress, entries: progressEntries, onChanged: (v) => setState(() => progressEntries = v));
      default: return HomePage(session: session, foods: foodEntries, progressEntries: progressEntries,
        onActivity: () => setState(() => tab = 1),
        onNutrition: () => setState(() => tab = 2),
        onProgress: () => setState(() => tab = 4));
    }
  }

  Widget _dock() {
    const items = [
      [Icons.home_rounded, 'خانه'], [Icons.directions_run_rounded, 'فعالیت'],
      [Icons.restaurant_rounded, 'تغذیه'], [Icons.map_outlined, 'نقشه'],
      [Icons.person_outline_rounded, 'پروفایل'],
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
  final List<ProgressEntry> progressEntries;
  final VoidCallback onActivity, onNutrition, onProgress;
  const HomePage({super.key, required this.session, required this.foods, required this.progressEntries, required this.onActivity, required this.onNutrition, required this.onProgress});

  @override
  Widget build(BuildContext context) {
    final today = DateTime.now();
    final entries = foods.where((e) => _sameDay(e.loggedAt, today)).toList();
    final kcal = entries.fold<double>(0, (s, e) => s + e.food.forGrams(e.grams)['kcal']!);
    final weight = progressEntries.where((e) => e.metric == ProgressMetric.weightKg).toList();
    final waist = progressEntries.where((e) => e.metric == ProgressMetric.waistCm).toList();
    final steps = progressEntries.where((e) => e.metric == ProgressMetric.steps && _sameDay(e.date, today)).toList();
    return ListView(padding: const EdgeInsets.fromLTRB(0, 10, 0, 24), children: [
      Padding(padding: const EdgeInsets.fromLTRB(20, 8, 20, 18), child: Row(children: [
        Container(width: 42, height: 42, decoration: BoxDecoration(color: green, borderRadius: BorderRadius.circular(15)), child: const Center(child: Text('m', style: TextStyle(color: Colors.white, fontSize: 26, fontWeight: FontWeight.w900)))),
        const SizedBox(width: 10),
        const Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text('MFit', style: TextStyle(color: green, fontSize: 21, fontWeight: FontWeight.w900)),
          Text('حرکت، سلامتی، پیشرفت', style: TextStyle(color: muted, fontSize: 10)),
        ])),
        Container(width: 40, height: 40, decoration: BoxDecoration(color: Colors.white, shape: BoxShape.circle, boxShadow: [BoxShadow(color: green.withOpacity(.06), blurRadius: 12)]), child: const Icon(Icons.person_outline_rounded, color: green)),
      ])),
      Padding(padding: const EdgeInsets.symmetric(horizontal: 20), child: ClipPath(
        clipper: _FlowCardClipper(),
        child: Container(padding: const EdgeInsets.fromLTRB(21, 21, 21, 24), decoration: const BoxDecoration(gradient: LinearGradient(begin: Alignment.topRight, end: Alignment.bottomLeft, colors: [Color(0xFF315F42), Color(0xFF173D2B), Color(0xFF10291E)])),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [const Expanded(child: Text('MFIT · FASTING', style: TextStyle(color: lime, fontSize: 9, letterSpacing: 1.7, fontWeight: FontWeight.w900))), _tag('روزانه')]),
            const SizedBox(height: 18),
            const Text('روزه‌داری آگاهانه', style: TextStyle(color: Colors.white, fontSize: 25, fontWeight: FontWeight.w900)),
            const SizedBox(height: 6),
            const Text('بازه‌ی تغذیه و استراحتت را با آگاهی مدیریت کن.', style: TextStyle(color: Colors.white70, fontSize: 11)),
            const SizedBox(height: 17),
            Row(children: [
              const Expanded(child: Text('هنوز بازه‌ای ثبت نشده', style: TextStyle(color: Colors.white, fontSize: 11, fontWeight: FontWeight.w700))),
              FilledButton.tonalIcon(onPressed: onNutrition, icon: const Icon(Icons.restaurant_menu_rounded, size: 16), label: const Text('تغذیه'), style: FilledButton.styleFrom(backgroundColor: lime, foregroundColor: ink, visualDensity: VisualDensity.compact)),
            ]),
          ]),
        ),
      )),
      const SizedBox(height: 18),
      Padding(padding: const EdgeInsets.symmetric(horizontal: 18), child: Row(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Expanded(flex: 7, child: GestureDetector(onTap: onActivity, child: Container(
          constraints: const BoxConstraints(minHeight: 142), padding: const EdgeInsets.all(17),
          decoration: BoxDecoration(color: lime, borderRadius: const BorderRadius.only(topLeft: Radius.circular(27), topRight: Radius.circular(27), bottomLeft: Radius.circular(27), bottomRight: Radius.circular(8)), boxShadow: [BoxShadow(color: const Color(0xFF9BCB37).withOpacity(.18), blurRadius: 17, offset: const Offset(0, 7))]),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const Icon(Icons.directions_run_rounded, color: ink, size: 25), const Spacer(),
            const Text('حرکت امروز', style: TextStyle(color: ink, fontSize: 17, fontWeight: FontWeight.w900)),
            const SizedBox(height: 4), const Text('شروع فعالیت و ثبت مسیر', style: TextStyle(color: Color(0xFF43572A), fontSize: 10)),
            const SizedBox(height: 8), const Row(children: [Text('شروع کن', style: TextStyle(color: ink, fontSize: 11, fontWeight: FontWeight.w900)), SizedBox(width: 5), Icon(Icons.arrow_back_rounded, color: ink, size: 15)]),
          ]),
        ))),
        const SizedBox(width: 10),
        Expanded(flex: 4, child: GestureDetector(onTap: onNutrition, child: Container(
          constraints: const BoxConstraints(minHeight: 142), padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(color: Colors.white, borderRadius: const BorderRadius.only(topLeft: Radius.circular(27), topRight: Radius.circular(8), bottomLeft: Radius.circular(27), bottomRight: Radius.circular(27)), boxShadow: [BoxShadow(color: green.withOpacity(.05), blurRadius: 15, offset: const Offset(0, 6))]),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const Icon(Icons.local_fire_department_rounded, color: Color(0xFFEA914A), size: 23), const Spacer(),
            const Text('دریافت انرژی', style: TextStyle(color: ink, fontSize: 11, fontWeight: FontWeight.w800)),
            const SizedBox(height: 5), Text(kcal.round().toString(), style: const TextStyle(color: green, fontSize: 24, fontWeight: FontWeight.w900)),
            const Text('کیلوکالری', style: TextStyle(color: muted, fontSize: 9)),
          ]),
        ))),
      ])),
      const SizedBox(height: 22),
      Padding(padding: const EdgeInsets.symmetric(horizontal: 20), child: Row(children: [
        const Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text('مسیر پیشرفت', style: TextStyle(color: green, fontSize: 17, fontWeight: FontWeight.w900)), Text('بر اساس داده‌های ثبت‌شده', style: TextStyle(color: muted, fontSize: 10))])),
        TextButton(onPressed: onProgress, child: const Text('جزئیات')),
      ])),
      Padding(padding: const EdgeInsets.symmetric(horizontal: 18), child: GestureDetector(onTap: onProgress, child: Container(
        padding: const EdgeInsets.all(17), decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(23), boxShadow: [BoxShadow(color: green.withOpacity(.045), blurRadius: 16, offset: const Offset(0, 5))]),
        child: Column(children: [
          Row(children: [
            _homeValue('وزن', weight.isEmpty ? '—' : weight.last.value.toStringAsFixed(1), 'kg'),
            _homeDivider(),
            _homeValue('دور کمر', waist.isEmpty ? '—' : waist.last.value.toStringAsFixed(1), 'cm'),
            _homeDivider(),
            _homeValue('قدم امروز', steps.isEmpty ? '—' : steps.last.value.round().toString(), 'قدم'),
          ]),
          const SizedBox(height: 15),
          Row(children: [const Icon(Icons.insights_rounded, color: green, size: 17), const SizedBox(width: 7), const Expanded(child: Text('روندها فقط با ثبت اندازه‌گیری واقعی نمایش داده می‌شوند.', style: TextStyle(color: muted, fontSize: 9))), const Icon(Icons.chevron_left_rounded, color: green, size: 18)]),
        ]),
      ))),
      const SizedBox(height: 19),
      Padding(padding: const EdgeInsets.symmetric(horizontal: 20), child: Row(children: [const Expanded(child: Text('تغذیه امروز', style: TextStyle(color: green, fontSize: 16, fontWeight: FontWeight.w900)),), TextButton(onPressed: onNutrition, child: const Text('ثبت غذا'))])),
      Padding(padding: const EdgeInsets.symmetric(horizontal: 18), child: GestureDetector(onTap: onNutrition, child: Container(
        padding: const EdgeInsets.all(16), decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(20)),
        child: Row(children: [
          const Icon(Icons.restaurant_menu_rounded, color: green, size: 23), const SizedBox(width: 11),
          Expanded(child: Text(entries.isEmpty ? 'هنوز غذایی برای امروز ثبت نکرده‌ای' : entries.length.toString() + ' مورد ثبت شده', style: const TextStyle(color: ink, fontWeight: FontWeight.w700, fontSize: 11))),
          Text(kcal.round().toString() + ' kcal', style: const TextStyle(color: green, fontWeight: FontWeight.w900, fontSize: 12)), const SizedBox(width: 6), const Icon(Icons.add_circle, color: green),
        ]),
      ))),
    ]);
  }

  Widget _homeValue(String label, String value, String unit) => Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
    Text(label, style: const TextStyle(color: muted, fontSize: 9, fontWeight: FontWeight.w600)), const SizedBox(height: 5),
    Text(value, style: const TextStyle(color: green, fontSize: 19, fontWeight: FontWeight.w900)), Text(unit, style: const TextStyle(color: muted, fontSize: 8)),
  ]));
  Widget _homeDivider() => Container(width: 1, height: 43, margin: const EdgeInsets.symmetric(horizontal: 9), color: const Color(0xFFE9EEE7));
}

class _FlowCardClipper extends CustomClipper<Path> {
  @override
  Path getClip(Size size) => Path()
    ..moveTo(0, 25)..quadraticBezierTo(0, 0, 27, 0)..lineTo(size.width - 35, 0)
    ..quadraticBezierTo(size.width, 0, size.width, 35)..lineTo(size.width, size.height - 34)
    ..quadraticBezierTo(size.width, size.height, size.width - 35, size.height)..lineTo(40, size.height)
    ..quadraticBezierTo(0, size.height, 0, size.height - 40)..close();
  @override
  bool shouldReclip(covariant CustomClipper<Path> oldClipper) => false;
}

class ActivityPage extends StatefulWidget {
  final SessionEngine session;
  final GpsTracker gps;
  final List<ProgressEntry> progressEntries;
  const ActivityPage({super.key, required this.session, required this.gps, required this.progressEntries});
  @override
  State<ActivityPage> createState() => _ActivityPageState();
}

class _ActivityPageState extends State<ActivityPage> with SingleTickerProviderStateMixin {
  late final AnimationController _pulse;
  String _mapMode = 'street';
  int _selectedActivity = 0;
  bool _stopped = false;
  static const _activities = [
    [Icons.directions_walk_rounded, 'پیاده‌روی'], [Icons.directions_bike_rounded, 'دوچرخه'],
    [Icons.fitness_center_rounded, 'قدرتی'], [Icons.directions_run_rounded, 'دویدن'],
    [Icons.favorite_rounded, 'هوازی'], [Icons.self_improvement_rounded, 'یوگا'],
  ];

  @override
  void initState() { super.initState(); _pulse = AnimationController(vsync: this, duration: const Duration(milliseconds: 700)); widget.session.addListener(_syncPulse); _syncPulse(); }
  void _syncPulse() { if (widget.session.isActive) { if (!_pulse.isAnimating) _pulse.repeat(reverse: true); } else { _pulse.stop(); _pulse.value = 1; } }
  @override
  void dispose() { widget.session.removeListener(_syncPulse); _pulse.dispose(); super.dispose(); }

  Future<void> _start() async {
    final restarting = _stopped;
    final resumeGps = !restarting && widget.session.elapsed > Duration.zero;
    if (restarting) await widget.session.reset();
    await widget.session.start();
    await widget.gps.start(resume: resumeGps);
    if (mounted) setState(() => _stopped = false);
  }
  Future<void> _pause() async { await widget.session.pause(); await widget.gps.stop(); }
  Future<void> _continue() async { await widget.session.start(); await widget.gps.start(resume: true); if (mounted) setState(() => _stopped = false); }
  Future<void> _stop() async { await widget.session.pause(); await widget.gps.stop(); if (mounted) setState(() => _stopped = true); }
  Future<void> _share() async {
    final type = _activities[_selectedActivity][1] as String;
    await Share.share('گزارش فعالیت MFit\nنوع فعالیت: ' + type + '\nمدت: ' + widget.session.formatted + '\nمسافت: ' + widget.gps.distanceLabel + ' کیلومتر');
  }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: Listenable.merge([widget.session, widget.gps]),
    builder: (_, __) {
      final distance = widget.gps.distanceMeters / 1000;
      final speed = widget.gps.speedMps * 3.6;
      final todaySteps = widget.progressEntries.where((e) => e.metric == ProgressMetric.steps && _sameDay(e.date, DateTime.now())).toList();
      final steps = todaySteps.isEmpty ? '—' : todaySteps.last.value.round().toString();
      final active = widget.session.isActive;
      final paused = !active && widget.session.elapsed > Duration.zero && !_stopped;
      final hasRoute = widget.gps.route.isNotEmpty;
      final center = hasRoute ? widget.gps.route.last : const LatLng(0, 0);
      return ListView(padding: const EdgeInsets.fromLTRB(0, 12, 0, 24), children: [
        Padding(padding: const EdgeInsets.fromLTRB(20, 0, 20, 16), child: Row(children: [
          Container(width: 40, height: 40, decoration: BoxDecoration(color: green, borderRadius: BorderRadius.circular(14)), child: const Center(child: Text('m', style: TextStyle(color: Colors.white, fontSize: 25, fontWeight: FontWeight.w900)))),
          const SizedBox(width: 10),
          const Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text('MFit', style: TextStyle(color: green, fontSize: 20, fontWeight: FontWeight.w900)), Text('حرکت، سلامتی، پیشرفت', style: TextStyle(color: muted, fontSize: 10))])),
          Container(width: 38, height: 38, decoration: const BoxDecoration(color: Colors.white, shape: BoxShape.circle), child: const Icon(Icons.person_outline_rounded, color: green)),
        ])),
        Padding(padding: const EdgeInsets.symmetric(horizontal: 18), child: Row(children: [
          const Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text('فعالیت', style: TextStyle(color: Color(0xFF203E2C), fontSize: 27, fontWeight: FontWeight.w900)), Text('حرکت کن، پیشرفت کن', style: TextStyle(color: muted, fontSize: 11))])),
          Container(padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 7), decoration: BoxDecoration(color: const Color(0xFFDCEBDD), borderRadius: BorderRadius.circular(30)), child: Row(children: [Container(width: 6, height: 6, decoration: const BoxDecoration(color: Color(0xFF398B50), shape: BoxShape.circle)), const SizedBox(width: 6), const Text('GPS آماده', style: TextStyle(color: green, fontSize: 10, fontWeight: FontWeight.w800))])),
        ])),
        const SizedBox(height: 15),
        Padding(padding: const EdgeInsets.symmetric(horizontal: 18), child: Container(padding: const EdgeInsets.all(4), decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(30)), child: Row(children: [_periodTab('امروز', true), _periodTab('هفته', false), _periodTab('تاریخچه', false)]))),
        const SizedBox(height: 14),
        Padding(padding: const EdgeInsets.symmetric(horizontal: 14), child: Container(
          clipBehavior: Clip.antiAlias,
          decoration: BoxDecoration(borderRadius: BorderRadius.circular(25), gradient: const LinearGradient(begin: Alignment.topLeft, end: Alignment.bottomRight, colors: [Color(0xFFA8DDA0), Color(0xFF8BCB8D), Color(0xFF78B982)]), boxShadow: [BoxShadow(color: const Color(0xFF4C8C55).withOpacity(.12), blurRadius: 18, offset: const Offset(0, 7))]),
          child: Stack(children: [
            Positioned.fill(child: CustomPaint(painter: _ActivityWavesPainter())),
            Padding(padding: const EdgeInsets.fromLTRB(17, 14, 17, 13), child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              Align(alignment: Alignment.centerRight, child: Container(padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5), decoration: BoxDecoration(color: const Color(0xFF315B3D).withOpacity(.72), borderRadius: BorderRadius.circular(20), border: Border.all(color: Colors.white.withOpacity(.65))), child: Row(mainAxisSize: MainAxisSize.min, children: [
                FadeTransition(opacity: active ? _pulse : const AlwaysStoppedAnimation(1), child: Container(width: 8, height: 8, decoration: const BoxDecoration(color: Color(0xFF20B94C), shape: BoxShape.circle))),
                const SizedBox(width: 6), const Text('فعالیت زنده', style: TextStyle(color: Colors.white, fontSize: 11, fontWeight: FontWeight.w900)),
              ]))),
              const SizedBox(height: 7),
              Align(alignment: Alignment.centerRight, child: Container(padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5), decoration: BoxDecoration(color: const Color(0xFF315B3D).withOpacity(.72), borderRadius: BorderRadius.circular(20), border: Border.all(color: Colors.white.withOpacity(.65))), child: const Text('مسافت پیموده‌شده', style: TextStyle(color: Colors.white, fontSize: 10, fontWeight: FontWeight.w900)))),
              const SizedBox(height: 5),
              Text(distance.toStringAsFixed(2) + ' KM', textAlign: TextAlign.right, style: const TextStyle(color: Colors.white, fontSize: 42, height: 1.15, fontWeight: FontWeight.w900, letterSpacing: -.8)),
              const SizedBox(height: 10),
              Row(children: [_activityMetric('مدت', widget.session.formatted.substring(0, 5)), _activityMetric('کالری', '—'), _activityMetric('سرعت', speed.toStringAsFixed(1)), _activityMetric('ضربان', '—')]),
              const SizedBox(height: 11),
              const Align(alignment: Alignment.centerRight, child: Text('کنترل فعالیت', style: TextStyle(color: Colors.white, fontSize: 11, fontWeight: FontWeight.w900))),
              const SizedBox(height: 8),
              Row(children: [
                Expanded(child: _control(Icons.play_arrow_rounded, 'شروع', _start, enabled: !active && !paused)),
                const SizedBox(width: 5), Expanded(child: _control(Icons.pause_rounded, 'مکث', _pause, enabled: active)),
                const SizedBox(width: 5), Expanded(child: _control(Icons.play_circle_outline_rounded, 'ادامه', _continue, enabled: paused)),
                const SizedBox(width: 5), Expanded(child: _control(Icons.stop_rounded, 'توقف', _stop, enabled: active || paused)),
                const SizedBox(width: 5), Expanded(child: _control(Icons.share_rounded, 'اشتراک', _share, enabled: true)),
              ]),
              const SizedBox(height: 7),
              Text(active ? 'فعالیت در حال اجرا' : paused ? 'فعالیت موقتاً متوقف شده' : _stopped ? 'فعالیت پایان یافته' : 'آماده‌ی شروع فعالیت', textAlign: TextAlign.center, style: const TextStyle(color: Colors.white, fontSize: 9, fontWeight: FontWeight.w700)),
            ])),
          ]),
        )),
        const SizedBox(height: 19),
        const SectionLabel('نوع فعالیت', 'CHOOSE YOUR SESSION'),
        Padding(padding: const EdgeInsets.symmetric(horizontal: 18), child: GridView.builder(
          shrinkWrap: true, physics: const NeverScrollableScrollPhysics(), itemCount: _activities.length,
          gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(crossAxisCount: 3, crossAxisSpacing: 8, mainAxisSpacing: 8, childAspectRatio: 1.45),
          itemBuilder: (_, i) {
            final selected = i == _selectedActivity;
            return GestureDetector(onTap: () => setState(() => _selectedActivity = i), child: Container(
              decoration: BoxDecoration(color: selected ? const Color(0xFFD6EBD1) : Colors.white, borderRadius: BorderRadius.circular(17), border: Border.all(color: selected ? const Color(0xFF78A879) : Colors.white)),
              child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [Icon(_activities[i][0] as IconData, color: const Color(0xFF315D3E), size: 21), const SizedBox(height: 4), Text(_activities[i][1] as String, style: const TextStyle(color: Color(0xFF36543D), fontSize: 10, fontWeight: FontWeight.w700))]),
            ));
          },
        )),
        const SizedBox(height: 18),
        Padding(padding: const EdgeInsets.symmetric(horizontal: 20), child: Row(children: [const Expanded(child: Text('مسیر فعالیت', style: TextStyle(color: green, fontSize: 17, fontWeight: FontWeight.w900))), Text(distance.toStringAsFixed(2) + ' KM', style: const TextStyle(color: green, fontSize: 11, fontWeight: FontWeight.w800))])),
        const SizedBox(height: 9),
        Padding(padding: const EdgeInsets.symmetric(horizontal: 18), child: Container(padding: const EdgeInsets.all(8), decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(22)), child: Column(children: [
          Container(padding: const EdgeInsets.all(3), decoration: BoxDecoration(color: const Color(0xFFEEF2EC), borderRadius: BorderRadius.circular(30)), child: Row(children: [_mapModeButton('خیابانی', 'street'), _mapModeButton('ماهواره‌ای', 'satellite')])),
          const SizedBox(height: 8),
          ClipRRect(borderRadius: BorderRadius.circular(16), child: SizedBox(height: 155, child: hasRoute
            ? FlutterMap(key: ValueKey(_mapMode + widget.gps.route.length.toString()), options: MapOptions(initialCenter: center, initialZoom: 15), children: [
                TileLayer(urlTemplate: _mapMode == 'street' ? 'https://tile.openstreetmap.org/{z}/{x}/{y}.png' : 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', userAgentPackageName: 'com.mfit.app', maxZoom: 19),
                if (widget.gps.route.length > 1) PolylineLayer(polylines: [Polyline(points: widget.gps.route, strokeWidth: 5, color: const Color(0xFF4E9A60))]),
                MarkerLayer(markers: [Marker(point: widget.gps.route.first, width: 22, height: 22, child: const Icon(Icons.circle, color: Colors.white, size: 16)), Marker(point: widget.gps.route.last, width: 26, height: 26, child: const Icon(Icons.location_on, color: Color(0xFFD65E4B), size: 25))]),
              ])
            : CustomPaint(painter: RoutePainter(), child: Container(color: const Color(0xFFE6EBDD), alignment: Alignment.center, child: const Text('با شروع فعالیت، مسیر واقعی اینجا ثبت می‌شود', style: TextStyle(color: green, fontSize: 10, fontWeight: FontWeight.w700))))),
        ]))),
        const SizedBox(height: 18),
        Padding(padding: const EdgeInsets.symmetric(horizontal: 20), child: Row(children: [const Expanded(child: Text('خلاصه امروز', style: TextStyle(color: green, fontSize: 17, fontWeight: FontWeight.w900))), const Text('عملکرد روزانه', style: TextStyle(color: muted, fontSize: 10))])),
        const SizedBox(height: 9),
        Padding(padding: const EdgeInsets.symmetric(horizontal: 18), child: Row(children: [
          Expanded(child: _summaryTile(Icons.directions_walk_rounded, steps, 'قدم')), const SizedBox(width: 8),
          Expanded(child: _summaryTile(Icons.route_rounded, distance.toStringAsFixed(2), 'کیلومتر')), const SizedBox(width: 8),
          Expanded(child: _summaryTile(Icons.local_fire_department_rounded, '—', 'کالری')),
        ])),
      ]);
    },
  );

  Widget _periodTab(String text, bool selected) => Expanded(child: Container(padding: const EdgeInsets.symmetric(vertical: 9), decoration: BoxDecoration(color: selected ? const Color(0xFFDDEBDD) : Colors.transparent, borderRadius: BorderRadius.circular(25)), alignment: Alignment.center, child: Text(text, style: TextStyle(color: selected ? const Color(0xFF28543A) : muted, fontSize: 10, fontWeight: selected ? FontWeight.w800 : FontWeight.w500))));
  Widget _activityMetric(String label, String value) => Expanded(child: Column(children: [Text(label, style: const TextStyle(color: Colors.white70, fontSize: 9)), const SizedBox(height: 3), Text(value, style: const TextStyle(color: Colors.white, fontSize: 15, fontWeight: FontWeight.w900, fontFeatures: [FontFeature.tabularFigures()]))]));
  Widget _control(IconData icon, String label, VoidCallback onTap, {required bool enabled}) => Opacity(opacity: enabled ? 1 : .42, child: Material(color: Colors.white.withOpacity(.24), borderRadius: BorderRadius.circular(11), child: InkWell(onTap: enabled ? onTap : null, borderRadius: BorderRadius.circular(11), child: Container(padding: const EdgeInsets.symmetric(vertical: 9, horizontal: 1), decoration: BoxDecoration(borderRadius: BorderRadius.circular(11), border: Border.all(color: Colors.white.withOpacity(.65))), child: Column(children: [Icon(icon, color: Colors.white, size: 17), const SizedBox(height: 3), Text(label, style: const TextStyle(color: Colors.white, fontSize: 8, fontWeight: FontWeight.w800))])))));
  Widget _mapModeButton(String label, String mode) { final selected = _mapMode == mode; return Expanded(child: GestureDetector(onTap: () => setState(() => _mapMode = mode), child: Container(padding: const EdgeInsets.symmetric(vertical: 8), decoration: BoxDecoration(color: selected ? Colors.white : Colors.transparent, borderRadius: BorderRadius.circular(25)), alignment: Alignment.center, child: Text(label, style: const TextStyle(color: Color(0xFF34523C), fontSize: 10, fontWeight: FontWeight.w700))))); }
  Widget _summaryTile(IconData icon, String value, String label) => Container(padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 9), decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(16)), child: Column(children: [Icon(icon, color: const Color(0xFF5B9360), size: 18), const SizedBox(height: 6), Text(value, style: const TextStyle(color: Color(0xFF284A33), fontSize: 16, fontWeight: FontWeight.w900)), Text(label, style: const TextStyle(color: muted, fontSize: 9))]));
}

class _ActivityWavesPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final wave = Paint()..color = Colors.white.withOpacity(.20)..style = PaintingStyle.stroke..strokeWidth = 2;
    final band = Paint()..color = const Color(0xFFE9F9D9).withOpacity(.25)..style = PaintingStyle.stroke..strokeWidth = 29;
    final p1 = Path()..moveTo(-30, size.height * .55)..cubicTo(size.width * .12, size.height * .05, size.width * .38, size.height * .25, size.width * .55, size.height * .52)..cubicTo(size.width * .72, size.height * .8, size.width * .84, size.height * .85, size.width + 25, size.height * .28);
    final p2 = Path()..moveTo(-20, size.height * .75)..cubicTo(size.width * .22, size.height * .35, size.width * .36, size.height * .46, size.width * .58, size.height * .7)..cubicTo(size.width * .75, size.height * .92, size.width * .9, size.height * .95, size.width + 20, size.height * .55);
    canvas.drawPath(p1, band); canvas.drawPath(p2, wave);
  }
  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
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
  Timer? searchDebounce;
  bool onlineLoading = false;
  List<FoodNutrition> onlineFoods = [];
  int searchRequest = 0;
  @override
  void dispose() { searchDebounce?.cancel(); search.dispose(); grams.dispose(); super.dispose(); }

  void _onSearchChanged(String query) {
    setState(() { selected = null; onlineFoods = []; onlineLoading = query.trim().length >= 2; });
    searchDebounce?.cancel();
    final request = ++searchRequest;
    if (query.trim().length < 2) return;
    searchDebounce = Timer(const Duration(milliseconds: 450), () async {
      try {
        final found = await FoodSearchRepository.searchOnline(query);
        if (!mounted || request != searchRequest || query != search.text) return;
        final localNames = FoodCatalog.search(query).map((e) => e.name.toLowerCase()).toSet();
        setState(() { onlineFoods = found.where((e) => !localNames.contains(e.name.toLowerCase())).toList(); onlineLoading = false; });
      } catch (_) {
        if (mounted && request == searchRequest) setState(() => onlineLoading = false);
      }
    });
  }
  List<FoodEntry> get todayEntries => widget.entries.where((e) => _sameDay(e.loggedAt, DateTime.now())).toList();
  Map<String, double> get totals {
    final result = {'kcal': 0.0, 'protein': 0.0, 'carbs': 0.0, 'fat': 0.0, 'fiber': 0.0};
    for (final e in todayEntries) { e.food.forGrams(e.grams).forEach((k, v) => result[k] = result[k]! + v); }
    return result;
  }

  @override
  Widget build(BuildContext context) {
    final t = totals;
    final localMatches = FoodCatalog.search(search.text);
    final matches = [...localMatches, ...onlineFoods];
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
          TextField(controller: search, onChanged: _onSearchChanged, decoration: const InputDecoration(prefixIcon: Icon(Icons.search_rounded, color: green), hintText: 'نام غذا را جست‌وجو کن؛ مثل تخم‌مرغ یا برنج')),
          if (search.text.trim().isNotEmpty) ...[
            const SizedBox(height: 8),
            if (onlineLoading) const Padding(padding: EdgeInsets.all(9), child: Row(children: [SizedBox(width: 14, height: 14, child: CircularProgressIndicator(strokeWidth: 2)), SizedBox(width: 8), Text('جست‌وجو در بانک آنلاین غذا…', style: TextStyle(color: muted, fontSize: 10))]))
            else if (matches.isEmpty) const Padding(padding: EdgeInsets.all(9), child: Text('موردی پیدا نشد. نام انگلیسی یا نام محصول بسته‌بندی‌شده را هم امتحان کن.', style: TextStyle(color: muted, fontSize: 11)))
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
      const PageHeader('YOUR PROFILE · PERFORMANCE', 'پروفایل و پیشرفت'),
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
  final GpsTracker tracker;
  const RoutePage({super.key, required this.tracker});

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: tracker,
    builder: (_, __) {
      final points = tracker.route;
      final hasFix = points.isNotEmpty;
      final center = hasFix ? points.last : const LatLng(0, 0);
      return ListView(padding: const EdgeInsets.only(bottom: 24), children: [
        const PageHeader('REAL GPS TRACKING', 'نقشه و مسیر'),
        Padding(padding: const EdgeInsets.fromLTRB(18, 0, 18, 14), child: FilledButton.icon(
          onPressed: () => Navigator.of(context).push(MaterialPageRoute(
            builder: (_) => RouteShareCardPage(tracker: tracker),
          )),
          icon: const Icon(Icons.ios_share_rounded),
          label: const Text('ساخت کارت اشتراک‌گذاری مسیر'),
          style: FilledButton.styleFrom(backgroundColor: green, foregroundColor: Colors.white,
            minimumSize: const Size.fromHeight(48), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(15))),
        )),
        Padding(padding: const EdgeInsets.symmetric(horizontal: 18), child: Container(
          height: 440, clipBehavior: Clip.antiAlias,
          decoration: BoxDecoration(color: const Color(0xFFE7EDE2), borderRadius: BorderRadius.circular(27)),
          child: Stack(children: [
            FlutterMap(
              key: ValueKey(hasFix ? 'gps-map-ready' : 'gps-map-empty'),
              options: MapOptions(initialCenter: center, initialZoom: hasFix ? 16 : 2),
              children: [
                TileLayer(
                  urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
                  userAgentPackageName: 'com.mfit.app',
                  maxZoom: 19,
                ),
                if (points.length > 1) PolylineLayer(polylines: [
                  Polyline(points: points, strokeWidth: 5, color: const Color(0xFF254D37)),
                ]),
                if (hasFix) MarkerLayer(markers: [
                  Marker(point: points.first, width: 24, height: 24, child: const Icon(Icons.flag_circle, color: Color(0xFF254D37), size: 25)),
                  Marker(point: points.last, width: 30, height: 30, child: const Icon(Icons.my_location, color: Color(0xFFB9EF45), size: 28)),
                ]),
                RichAttributionWidget(attributions: [
                  TextSourceAttribution('© OpenStreetMap contributors'),
                ]),
              ],
            ),
            Positioned(right: 14, top: 14, child: _tag(tracker.isTracking ? 'GPS LIVE' : 'GPS MAP', dark: true)),
            if (!hasFix) Positioned.fill(child: IgnorePointer(child: Container(
              color: Colors.white.withOpacity(.35),
              child: Center(child: Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(color: Colors.white.withOpacity(.94), borderRadius: BorderRadius.circular(18)),
                child: const Text('برای نمایش مسیر، یک تمرین GPS را شروع کن', style: TextStyle(color: ink, fontWeight: FontWeight.w800, fontSize: 12)),
              )),
            ))),
          ]),
        )),
        const SizedBox(height: 15),
        Padding(padding: const EdgeInsets.symmetric(horizontal: 18), child: Row(children: [
          Expanded(child: MetricTile(icon: Icons.route_rounded, label: 'مسافت', value: tracker.distanceLabel, unit: 'کیلومتر', accent: green)),
          const SizedBox(width: 9),
          Expanded(child: MetricTile(icon: Icons.speed_rounded, label: 'سرعت', value: (tracker.speedMps * 3.6).toStringAsFixed(1), unit: 'km/h', accent: const Color(0xFF4E8CC7))),
          const SizedBox(width: 9),
          Expanded(child: MetricTile(icon: Icons.timer_outlined, label: 'Pace', value: tracker.paceLabel, unit: 'دقیقه / km', accent: const Color(0xFFEA914A))),
        ])),
        Padding(padding: const EdgeInsets.fromLTRB(18, 15, 18, 0), child: Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(color: card, borderRadius: BorderRadius.circular(19)),
          child: Row(children: [
            Icon(tracker.isTracking ? Icons.gps_fixed : Icons.gps_not_fixed, color: tracker.isTracking ? green : muted),
            const SizedBox(width: 10),
            Expanded(child: Text(tracker.status, style: const TextStyle(color: ink, fontSize: 11, fontWeight: FontWeight.w700))),
            if (tracker.isTracking)
              FilledButton(onPressed: tracker.stop, style: FilledButton.styleFrom(backgroundColor: const Color(0xFFFFE5E0), foregroundColor: const Color(0xFF9D3425)), child: const Text('پایان'))
            else
              FilledButton(onPressed: tracker.start, style: FilledButton.styleFrom(backgroundColor: lime, foregroundColor: ink), child: const Text('شروع GPS')),
          ]),
        )),
        const Padding(padding: EdgeInsets.fromLTRB(22, 12, 22, 0), child: Text('ثبت مسیر از GPS گوشی انجام می‌شود و در زمان فعال بودن سرویس Foreground اندروید می‌تواند با خاموش شدن صفحه ادامه یابد. برای این قابلیت، مجوز «همیشه مجاز» و اعلان‌ها باید فعال باشند.', style: TextStyle(color: muted, fontSize: 10, height: 1.5))),
      ]);
    },
  );
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
