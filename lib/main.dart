import 'dart:async';
import 'package:flutter/material.dart';

const bg = Color(0xFF071411);
const panel = Color(0xFF10231E);
const panel2 = Color(0xFF142B24);
const mint = Color(0xFF9BF2C7);
const lime = Color(0xFFD4F47A);
const muted = Color(0xFF8FA69B);

void main() => runApp(const MFitApp());

class MFitApp extends StatelessWidget {
  const MFitApp({super.key});
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'MFit',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        brightness: Brightness.dark,
        scaffoldBackgroundColor: bg,
        colorScheme: const ColorScheme.dark(
          primary: mint, secondary: lime, surface: panel,
        ),
        fontFamily: 'Roboto',
        useMaterial3: true,
      ),
      home: const Directionality(textDirection: TextDirection.rtl, child: MFitShell()),
    );
  }
}

class MFitShell extends StatefulWidget {
  const MFitShell({super.key});
  @override
  State<MFitShell> createState() => _MFitShellState();
}

class _MFitShellState extends State<MFitShell> {
  int tab = 0;
  int elapsed = 0;
  Timer? ticker;
  bool running = false;
  int water = 3;
  final foods = <Map<String, dynamic>>[];
  final foodController = TextEditingController();

  @override
  void dispose() {
    ticker?.cancel();
    foodController.dispose();
    super.dispose();
  }

  void toggleTimer() {
    if (running) {
      ticker?.cancel();
      setState(() => running = false);
    } else {
      ticker = Timer.periodic(const Duration(seconds: 1), (_) {
        if (mounted) setState(() => elapsed++);
      });
      setState(() => running = true);
    }
  }

  String get clock {
    final h = (elapsed ~/ 3600).toString().padLeft(2, '0');
    final m = ((elapsed % 3600) ~/ 60).toString().padLeft(2, '0');
    final s = (elapsed % 60).toString().padLeft(2, '0');
    return '$h:$m:$s';
  }

  @override
  Widget build(BuildContext context) {
    final pages = [
      _home(), _activity(), _nutrition(), _map(), _profile(),
    ];
    return Scaffold(
      body: SafeArea(
        bottom: false,
        child: Column(children: [
          Expanded(child: AnimatedSwitcher(
            duration: const Duration(milliseconds: 260),
            switchInCurve: Curves.easeOutCubic,
            switchOutCurve: Curves.easeInCubic,
            child: KeyedSubtree(key: ValueKey(tab), child: pages[tab]),
          )),
          _bottomNav(),
        ]),
      ),
    );
  }

  Widget _top(String eyebrow, String title, {Widget? action}) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(22, 18, 22, 18),
      child: Row(children: [
        Container(
          width: 42, height: 42,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(15),
            gradient: const LinearGradient(colors: [mint, Color(0xFF4AAE83)]),
          ),
          child: const Icon(Icons.bolt_rounded, color: bg, size: 26),
        ),
        const SizedBox(width: 12),
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(eyebrow, style: const TextStyle(color: muted, fontSize: 11, letterSpacing: 1.5)),
          Text(title, style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800, letterSpacing: -.5)),
        ])),
        action ?? Container(
          padding: const EdgeInsets.all(11),
          decoration: BoxDecoration(color: panel, borderRadius: BorderRadius.circular(14), border: Border.all(color: Colors.white10)),
          child: const Icon(Icons.notifications_none_rounded, color: mint, size: 20),
        ),
      ]),
    );
  }

  Widget _home() => ListView(
    key: const PageStorageKey('home'),
    padding: const EdgeInsets.only(bottom: 22),
    children: [
      _top('THURSDAY, 13 NOVEMBER', 'روزت بخیر'),
      Padding(
        padding: const EdgeInsets.symmetric(horizontal: 20),
        child: Container(
          padding: const EdgeInsets.all(20),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(28),
            gradient: const LinearGradient(begin: Alignment.topRight, end: Alignment.bottomLeft, colors: [Color(0xFF1D4938), Color(0xFF10251F), Color(0xFF0C1D18)]),
            border: Border.all(color: mint.withOpacity(.22)),
          ),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              const Expanded(child: Text('آمادگی امروز', style: TextStyle(color: mint, fontSize: 13, fontWeight: FontWeight.w600))),
              _pill('در مسیر پیشرفت', lime),
            ]),
            const SizedBox(height: 18),
            Row(crossAxisAlignment: CrossAxisAlignment.center, children: [
              Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                const Text('ادامه بده؛\\nنتیجه نزدیکه.', style: TextStyle(fontSize: 27, height: 1.25, fontWeight: FontWeight.w800)),
                const SizedBox(height: 12),
                const Text('هر قدم کوچک، یک برد بزرگه.', style: TextStyle(color: muted, fontSize: 12)),
                const SizedBox(height: 18),
                FilledButton(
                  onPressed: () => setState(() => tab = 1),
                  style: FilledButton.styleFrom(backgroundColor: mint, foregroundColor: bg, padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 12)),
                  child: const Text('شروع فعالیت', style: TextStyle(fontWeight: FontWeight.bold)),
                ),
              ])),
              SizedBox(width: 132, height: 132, child: CustomPaint(painter: RingPainter(), child: const Center(child: Column(mainAxisSize: MainAxisSize.min, children: [
                Text('۷۸', style: TextStyle(fontSize: 38, fontWeight: FontWeight.w800, color: Colors.white)),
                Text('امتیاز انرژی', style: TextStyle(fontSize: 10, color: muted)),
              ])))),
            ]),
          ]),
        ),
      ),
      const SizedBox(height: 22),
      _sectionTitle('خلاصه امروز', 'مشاهده گزارش'),
      Padding(padding: const EdgeInsets.symmetric(horizontal: 20), child: Row(children: [
        Expanded(child: _metric(Icons.directions_walk_rounded, 'قدم‌ها', '۴٬۸۲۶', 'هدف ۸۰۰۰', mint)),
        const SizedBox(width: 10),
        Expanded(child: _metric(Icons.local_fire_department_rounded, 'کالری فعال', '۲۸۶', 'کیلوکالری', lime)),
        const SizedBox(width: 10),
        Expanded(child: _metric(Icons.water_drop_rounded, 'آب', '$water/8', 'لیوان', const Color(0xFF7ACBFF))),
      ])),
      const SizedBox(height: 24),
      _sectionTitle('برنامه امروز', 'ویرایش'),
      Padding(padding: const EdgeInsets.symmetric(horizontal: 20), child: Column(children: [
        _plan(Icons.directions_run_rounded, 'پیاده‌روی سبک', '۲۰ دقیقه · فعالیت هوازی', 'در انتظار', mint, () => setState(() => tab = 1)),
        const SizedBox(height: 9),
        _plan(Icons.restaurant_rounded, 'وعده متعادل', 'پروتئین + سبزیجات', 'پیشنهاد تغذیه', lime, () => setState(() => tab = 2)),
      ])),
    ],
  );

  Widget _activity() => ListView(padding: const EdgeInsets.only(bottom: 24), children: [
    _top('MOVE WITH PURPOSE', 'فعالیت'),
    Padding(padding: const EdgeInsets.symmetric(horizontal: 20), child: Container(
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(color: panel, borderRadius: BorderRadius.circular(28), border: Border.all(color: mint.withOpacity(.18))),
      child: Column(children: [
        Row(children: [const Icon(Icons.timer_outlined, color: mint), const SizedBox(width: 8), const Text('تایمر تمرین', style: TextStyle(fontWeight: FontWeight.w700)), const Spacer(), _pill(running ? 'در حال اجرا' : 'آماده', running ? mint : muted)]),
        const SizedBox(height: 28),
        Text(clock, style: const TextStyle(fontSize: 48, fontWeight: FontWeight.w300, letterSpacing: 3, fontFeatures: [FontFeature.tabularFigures()])),
        const SizedBox(height: 8),
        const Text('زمان فعالیت مستقل', style: TextStyle(color: muted, fontSize: 12)),
        const SizedBox(height: 24),
        Row(children: [
          Expanded(child: FilledButton.icon(onPressed: toggleTimer, icon: Icon(running ? Icons.pause_rounded : Icons.play_arrow_rounded), label: Text(running ? 'مکث' : 'شروع'), style: FilledButton.styleFrom(backgroundColor: mint, foregroundColor: bg, padding: const EdgeInsets.symmetric(vertical: 14)))),
          const SizedBox(width: 10),
          OutlinedButton(onPressed: () { ticker?.cancel(); setState(() { elapsed = 0; running = false; }); }, style: OutlinedButton.styleFrom(foregroundColor: Colors.white70, side: const BorderSide(color: Colors.white24), padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 18)), child: const Icon(Icons.refresh_rounded)),
        ]),
      ]),
    )),
    const SizedBox(height: 22),
    _sectionTitle('فعالیت‌های پیشنهادی', 'همه فعالیت‌ها'),
    Padding(padding: const EdgeInsets.symmetric(horizontal: 20), child: Column(children: [
      _activityTile(Icons.directions_walk_rounded, 'پیاده‌روی', 'شروع آرام و پیوسته', '۲۰ دقیقه', mint),
      _activityTile(Icons.self_improvement_rounded, 'کشش و انعطاف', 'آماده‌سازی بدن', '۸ دقیقه', lime),
      _activityTile(Icons.fitness_center_rounded, 'تمرین قدرتی', 'تمرین با وزن بدن', '۱۵ دقیقه', const Color(0xFFFFB979)),
    ])),
  ]);

  Widget _nutrition() => ListView(padding: const EdgeInsets.only(bottom: 24), children: [
    _top('EAT SMARTER', 'تغذیه'),
    Padding(padding: const EdgeInsets.symmetric(horizontal: 20), child: Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(color: panel, borderRadius: BorderRadius.circular(26)),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const Text('دریافت انرژی امروز', style: TextStyle(color: muted, fontSize: 12)),
        const SizedBox(height: 8),
        const Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
          Text('۱٬۲۸۰', style: TextStyle(fontSize: 34, fontWeight: FontWeight.w800)),
          Padding(padding: EdgeInsets.only(bottom: 6, right: 6), child: Text('/ ۱۸۰۰ kcal', style: TextStyle(color: muted, fontSize: 12))),
        ]),
        const SizedBox(height: 14),
        ClipRRect(borderRadius: BorderRadius.circular(20), child: const LinearProgressIndicator(value: .71, minHeight: 8, backgroundColor: Color(0xFF263A33), color: mint)),
        const SizedBox(height: 18),
        const Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
          _Macro(label: 'پروتئین', value: '۷۸ g', color: mint),
          _Macro(label: 'کربوهیدرات', value: '۱۱۲ g', color: lime),
          _Macro(label: 'چربی', value: '۴۲ g', color: Color(0xFFFFB979)),
        ]),
      ]),
    )),
    const SizedBox(height: 22),
    _sectionTitle('ثبت خوراکی', 'پایگاه غذایی آزمایشی'),
    Padding(padding: const EdgeInsets.symmetric(horizontal: 20), child: Column(children: [
      TextField(
        controller: foodController,
        textDirection: TextDirection.rtl,
        decoration: InputDecoration(
          hintText: 'مثلاً تخم‌مرغ، سیب، ماست یونانی...',
          hintStyle: const TextStyle(color: muted, fontSize: 12),
          prefixIcon: const Icon(Icons.search_rounded, color: mint),
          filled: true, fillColor: panel,
          border: OutlineInputBorder(borderRadius: BorderRadius.circular(17), borderSide: BorderSide.none),
        ),
      ),
      const SizedBox(height: 12),
      Wrap(spacing: 8, runSpacing: 8, children: [
        _foodChip('تخم‌مرغ', '۷۸ kcal', () => _addFood('تخم‌مرغ', '۷۸ kcal')),
        _foodChip('سیب متوسط', '۹۵ kcal', () => _addFood('سیب متوسط', '۹۵ kcal')),
        _foodChip('ماست یونانی', '۱۳۰ kcal', () => _addFood('ماست یونانی', '۱۳۰ kcal')),
        _foodChip('سینه مرغ', '۱۶۵ kcal', () => _addFood('سینه مرغ', '۱۶۵ kcal')),
      ]),
      const SizedBox(height: 12),
      SizedBox(width: double.infinity, child: OutlinedButton.icon(
        onPressed: () { final v = foodController.text.trim(); if (v.isNotEmpty) _addFood(v, 'نیازمند تطبیق'); },
        icon: const Icon(Icons.add_rounded), label: const Text('افزودن خوراکی'),
        style: OutlinedButton.styleFrom(foregroundColor: mint, side: BorderSide(color: mint.withOpacity(.35)), padding: const EdgeInsets.symmetric(vertical: 13)),
      )),
      if (foods.isNotEmpty) ...[
        const SizedBox(height: 18),
        ...foods.map((f) => _foodRow(f['name'] as String, f['cal'] as String)),
      ],
    ])),
    const SizedBox(height: 22),
    _sectionTitle('آب مصرفی', 'هدف روزانه ۸ لیوان'),
    Padding(padding: const EdgeInsets.symmetric(horizontal: 20), child: Row(children: [
      Expanded(child: Text(' $water لیوان نوشیده‌ای', style: const TextStyle(color: muted))),
      IconButton(onPressed: () => setState(() { if (water > 0) water--; }), icon: const Icon(Icons.remove_circle_outline, color: muted)),
      IconButton(onPressed: () => setState(() { if (water < 8) water++; }), icon: const Icon(Icons.add_circle, color: mint)),
    ])),
  ]);

  void _addFood(String name, String cal) {
    setState(() { foods.insert(0, {'name': name, 'cal': cal}); foodController.clear(); });
  }

  Widget _map() => ListView(padding: const EdgeInsets.only(bottom: 24), children: [
    _top('EXPLORE YOUR ROUTE', 'نقشه و مسیر'),
    Padding(padding: const EdgeInsets.symmetric(horizontal: 20), child: Container(
      height: 390,
      clipBehavior: Clip.antiAlias,
      decoration: BoxDecoration(color: const Color(0xFF132821), borderRadius: BorderRadius.circular(28), border: Border.all(color: mint.withOpacity(.18))),
      child: Stack(children: [
        Positioned.fill(child: CustomPaint(painter: MapPainter())),
        Positioned(right: 18, top: 18, child: _pill('پیش‌نمایش نقشه', mint)),
        Positioned(right: 20, bottom: 22, left: 20, child: Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(color: const Color(0xE610201B), borderRadius: BorderRadius.circular(20), border: Border.all(color: Colors.white12)),
          child: Row(children: [
            Container(width: 42, height: 42, decoration: BoxDecoration(color: mint.withOpacity(.13), borderRadius: BorderRadius.circular(14)), child: const Icon(Icons.route_rounded, color: mint)),
            const SizedBox(width: 12),
            const Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text('مسیر پیاده‌روی', style: TextStyle(fontWeight: FontWeight.bold)),
              Text('۲٫۴ کیلومتر · حدود ۲۸ دقیقه', style: TextStyle(color: muted, fontSize: 11)),
            ])),
            const Icon(Icons.arrow_forward_ios_rounded, size: 15, color: mint),
          ]),
        )),
      ]),
    )),
    const Padding(padding: EdgeInsets.fromLTRB(22, 18, 22, 6), child: Text('ردیابی واقعی GPS در نسخه اندروید فعال می‌شود.', style: TextStyle(color: muted, fontSize: 12))),
  ]);

  Widget _profile() => ListView(padding: const EdgeInsets.only(bottom: 24), children: [
    _top('YOUR WELLNESS SPACE', 'پروفایل'),
    Padding(padding: const EdgeInsets.symmetric(horizontal: 20), child: Container(
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(color: panel, borderRadius: BorderRadius.circular(26)),
      child: Column(children: [
        Container(width: 82, height: 82, decoration: BoxDecoration(shape: BoxShape.circle, gradient: const LinearGradient(colors: [mint, Color(0xFF3A8F6C)]), boxShadow: [BoxShadow(color: mint.withOpacity(.18), blurRadius: 24)]), child: const Icon(Icons.person_rounded, size: 42, color: bg)),
        const SizedBox(height: 12),
        const Text('ورزشکار MFit', style: TextStyle(fontWeight: FontWeight.w800, fontSize: 20)),
        const SizedBox(height: 5),
        const Text('شروع مسیر سلامتی', style: TextStyle(color: muted, fontSize: 12)),
        const SizedBox(height: 22),
        const Divider(color: Colors.white10),
        _profileLine(Icons.monitor_weight_outlined, 'وزن', '— کیلوگرم'),
        _profileLine(Icons.height_rounded, 'قد', '— سانتی‌متر'),
        _profileLine(Icons.flag_outlined, 'هدف', 'سلامتی و تناسب اندام'),
      ]),
    )),
    const SizedBox(height: 18),
    Padding(padding: const EdgeInsets.symmetric(horizontal: 20), child: Column(children: [
      _setting(Icons.notifications_none_rounded, 'اعلان‌ها'),
      _setting(Icons.palette_outlined, 'ظاهر و شخصی‌سازی'),
      _setting(Icons.privacy_tip_outlined, 'حریم خصوصی و داده‌ها'),
      _setting(Icons.help_outline_rounded, 'راهنما و پشتیبانی'),
    ])),
  ]);

  Widget _bottomNav() {
    const items = [
      [Icons.grid_view_rounded, 'خانه'],
      [Icons.directions_run_rounded, 'فعالیت'],
      [Icons.restaurant_rounded, 'تغذیه'],
      [Icons.map_outlined, 'نقشه'],
      [Icons.person_outline_rounded, 'پروفایل'],
    ];
    return Container(
      decoration: BoxDecoration(
        color: const Color(0xF20A1915),
        border: Border(top: BorderSide(color: Colors.white.withOpacity(.08))),
      ),
      padding: const EdgeInsets.fromLTRB(8, 10, 8, 14),
      child: Row(children: List.generate(items.length, (i) {
        final active = tab == i;
        return Expanded(child: GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTap: () => setState(() => tab = i),
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 200),
            padding: const EdgeInsets.symmetric(vertical: 8),
            decoration: BoxDecoration(color: active ? mint.withOpacity(.12) : Colors.transparent, borderRadius: BorderRadius.circular(16)),
            child: Column(mainAxisSize: MainAxisSize.min, children: [
              Icon(items[i][0] as IconData, color: active ? mint : muted, size: 21),
              const SizedBox(height: 4),
              Text(items[i][1] as String, style: TextStyle(color: active ? mint : muted, fontSize: 10, fontWeight: active ? FontWeight.bold : FontWeight.normal)),
            ]),
          ),
        ));
      })),
    );
  }

  Widget _pill(String text, Color color) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
    decoration: BoxDecoration(color: color.withOpacity(.12), borderRadius: BorderRadius.circular(30), border: Border.all(color: color.withOpacity(.22))),
    child: Text(text, style: TextStyle(color: color, fontSize: 10, fontWeight: FontWeight.w600)),
  );

  Widget _sectionTitle(String title, String action) => Padding(
    padding: const EdgeInsets.fromLTRB(22, 0, 22, 13),
    child: Row(children: [
      Expanded(child: Text(title, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800))),
      Text(action, style: const TextStyle(color: mint, fontSize: 11)),
    ]),
  );

  Widget _metric(IconData icon, String label, String value, String unit, Color color) => Container(
    padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 10),
    decoration: BoxDecoration(color: panel, borderRadius: BorderRadius.circular(19), border: Border.all(color: Colors.white.withOpacity(.045))),
    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Icon(icon, color: color, size: 19),
      const SizedBox(height: 12),
      Text(value, style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 17)),
      Text(label, style: const TextStyle(color: muted, fontSize: 10)),
      const SizedBox(height: 5),
      Text(unit, style: TextStyle(color: color.withOpacity(.8), fontSize: 9)),
    ]),
  );

  Widget _plan(IconData icon, String title, String subtitle, String status, Color color, VoidCallback onTap) => GestureDetector(
    onTap: onTap,
    child: Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(color: panel, borderRadius: BorderRadius.circular(18)),
      child: Row(children: [
        Container(width: 42, height: 42, decoration: BoxDecoration(color: color.withOpacity(.12), borderRadius: BorderRadius.circular(14)), child: Icon(icon, color: color)),
        const SizedBox(width: 12),
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(title, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
          const SizedBox(height: 4),
          Text(subtitle, style: const TextStyle(color: muted, fontSize: 10)),
        ])),
        Text(status, style: TextStyle(color: color, fontSize: 10)),
        const SizedBox(width: 6),
        const Icon(Icons.chevron_left_rounded, color: muted, size: 18),
      ]),
    ),
  );

  Widget _activityTile(IconData icon, String title, String sub, String duration, Color color) => Container(
    margin: const EdgeInsets.only(bottom: 10),
    padding: const EdgeInsets.all(15),
    decoration: BoxDecoration(color: panel, borderRadius: BorderRadius.circular(18)),
    child: Row(children: [
      Container(width: 44, height: 44, decoration: BoxDecoration(color: color.withOpacity(.12), borderRadius: BorderRadius.circular(15)), child: Icon(icon, color: color)),
      const SizedBox(width: 12),
      Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(title, style: const TextStyle(fontWeight: FontWeight.bold)),
        Text(sub, style: const TextStyle(color: muted, fontSize: 11)),
      ])),
      Text(duration, style: const TextStyle(color: mint, fontSize: 11)),
    ]),
  );

  Widget _foodChip(String title, String kcal, VoidCallback onTap) => ActionChip(
    onPressed: onTap,
    backgroundColor: panel2,
    side: BorderSide(color: mint.withOpacity(.18)),
    label: Text('$title · $kcal', style: const TextStyle(color: Colors.white, fontSize: 11)),
  );

  Widget _foodRow(String title, String kcal) => Container(
    margin: const EdgeInsets.only(bottom: 8),
    padding: const EdgeInsets.all(13),
    decoration: BoxDecoration(color: panel2, borderRadius: BorderRadius.circular(14)),
    child: Row(children: [const Icon(Icons.restaurant_menu_rounded, color: mint, size: 18), const SizedBox(width: 10), Expanded(child: Text(title, style: const TextStyle(fontSize: 12))), Text(kcal, style: const TextStyle(color: lime, fontSize: 11))]),
  );

  Widget _profileLine(IconData icon, String title, String value) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 11),
    child: Row(children: [Icon(icon, color: mint, size: 19), const SizedBox(width: 12), Expanded(child: Text(title, style: const TextStyle(color: muted, fontSize: 12))), Text(value, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 12))]),
  );

  Widget _setting(IconData icon, String title) => Container(
    margin: const EdgeInsets.only(bottom: 8),
    padding: const EdgeInsets.all(16),
    decoration: BoxDecoration(color: panel, borderRadius: BorderRadius.circular(16)),
    child: Row(children: [Icon(icon, color: mint, size: 20), const SizedBox(width: 12), Expanded(child: Text(title, style: const TextStyle(fontSize: 13))), const Icon(Icons.chevron_left_rounded, color: muted)]),
  );
}

class _Macro extends StatelessWidget {
  final String label, value; final Color color;
  const _Macro({required this.label, required this.value, required this.color});
  @override
  Widget build(BuildContext context) => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
    Text(value, style: TextStyle(color: color, fontWeight: FontWeight.w800, fontSize: 14)),
    Text(label, style: const TextStyle(color: muted, fontSize: 10)),
  ]);
}

class RingPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final c = size.center(Offset.zero);
    final rect = Rect.fromCircle(center: c, radius: size.width * .40);
    canvas.drawArc(rect, -1.57, 6.283, false, Paint()..color = Colors.white.withOpacity(.10)..style = PaintingStyle.stroke..strokeWidth = 10);
    canvas.drawArc(rect, -1.57, 4.9, false, Paint()..shader = const SweepGradient(colors: [mint, lime]).createShader(rect)..style = PaintingStyle.stroke..strokeWidth = 10..strokeCap = StrokeCap.round);
  }
  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

class MapPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final p = Paint()..color = const Color(0xFF28483B)..strokeWidth = 1.2;
    final p2 = Paint()..color = const Color(0xFF203B31)..strokeWidth = 7..strokeCap = StrokeCap.round;
    for (var i = 0; i < 8; i++) {
      final y = size.height * (i + 1) / 9;
      canvas.drawLine(Offset(0, y), Offset(size.width, y + (i.isEven ? 22 : -18)), p);
    }
    for (var i = 0; i < 7; i++) {
      final x = size.width * (i + 1) / 8;
      canvas.drawLine(Offset(x, 0), Offset(x + (i.isEven ? 16 : -22), size.height), p);
    }
    final roads = Path()
      ..moveTo(size.width * .08, size.height * .25)
      ..lineTo(size.width * .34, size.height * .34)
      ..lineTo(size.width * .52, size.height * .20)
      ..lineTo(size.width * .75, size.height * .32)
      ..lineTo(size.width * .95, size.height * .24);
    canvas.drawPath(roads, p2);
    final route = Path()
      ..moveTo(size.width * .18, size.height * .72)
      ..cubicTo(size.width * .30, size.height * .48, size.width * .46, size.height * .75, size.width * .57, size.height * .54)
      ..cubicTo(size.width * .68, size.height * .34, size.width * .77, size.height * .52, size.width * .84, size.height * .31);
    canvas.drawPath(route, Paint()..color = mint..style = PaintingStyle.stroke..strokeWidth = 4..strokeCap = StrokeCap.round);
    final start = Offset(size.width * .18, size.height * .72);
    final end = Offset(size.width * .84, size.height * .31);
    canvas.drawCircle(start, 9, Paint()..color = mint);
    canvas.drawCircle(start, 4, Paint()..color = bg);
    canvas.drawCircle(end, 12, Paint()..color = lime.withOpacity(.2));
    canvas.drawCircle(end, 6, Paint()..color = lime);
  }
  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
