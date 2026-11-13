import 'dart:io';
import 'dart:ui' as ui;
import 'package:cross_file/cross_file.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';
import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';
import '../../core/gps_tracker.dart';

const _ink = Color(0xFF14231A);
const _lime = Color(0xFFC7FF35);
const _green = Color(0xFF254D37);

class RouteShareCardPage extends StatefulWidget {
  final GpsTracker tracker;
  const RouteShareCardPage({super.key, required this.tracker});
  @override
  State<RouteShareCardPage> createState() => _RouteShareCardPageState();
}

class _RouteShareCardPageState extends State<RouteShareCardPage> {
  final GlobalKey _cardKey = GlobalKey();
  bool _sharing = false;

  Future<void> _shareCard() async {
    if (_sharing || widget.tracker.route.length < 2) return;
    setState(() => _sharing = true);
    try {
      await WidgetsBinding.instance.endOfFrame;
      final boundary = _cardKey.currentContext?.findRenderObject() as RenderRepaintBoundary?;
      if (boundary == null) throw StateError('کارت آماده خروجی نیست');
      final image = await boundary.toImage(pixelRatio: 3);
      final bytes = await image.toByteData(format: ui.ImageByteFormat.png);
      image.dispose();
      if (bytes == null) throw StateError('ساخت تصویر کارت ناموفق بود');
      final directory = await getTemporaryDirectory();
      final file = File('${directory.path}/mfit-route-${DateTime.now().millisecondsSinceEpoch}.png');
      await file.writeAsBytes(bytes.buffer.asUint8List(), flush: true);
      await Share.shareXFiles([XFile(file.path, mimeType: 'image/png')], text: 'مسیر ثبت‌شده با MFit · Move your way');
    } catch (error) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('اشتراک‌گذاری انجام نشد: $error')));
    } finally {
      if (mounted) setState(() => _sharing = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: const Color(0xFFF2F5EF),
    appBar: AppBar(
      title: const Text('کارت مسیر من', style: TextStyle(fontWeight: FontWeight.w900)),
      backgroundColor: const Color(0xFFF2F5EF), foregroundColor: _ink, elevation: 0,
    ),
    body: AnimatedBuilder(
      animation: widget.tracker,
      builder: (context, _) {
        final tracker = widget.tracker;
        final points = List<LatLng>.unmodifiable(tracker.route);
        final hasRoute = points.length > 1;
        final center = points.isNotEmpty ? points.last : const LatLng(52.52, 13.405);
        final speed = tracker.speedMps * 3.6;
        return ListView(
          padding: const EdgeInsets.fromLTRB(16, 4, 16, 28),
          children: [
            RepaintBoundary(
              key: _cardKey,
              child: Container(
                decoration: BoxDecoration(
                  color: _ink, borderRadius: BorderRadius.circular(28),
                  boxShadow: const [BoxShadow(color: Color(0x260E2015), blurRadius: 22, offset: Offset(0, 12))],
                ),
                clipBehavior: Clip.antiAlias,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Padding(
                      padding: const EdgeInsets.fromLTRB(20, 20, 20, 14),
                      child: Row(children: [
                        Container(
                          width: 38, height: 38,
                          decoration: BoxDecoration(color: _lime, borderRadius: BorderRadius.circular(12)),
                          child: const Icon(Icons.route_rounded, color: _ink, size: 23),
                        ),
                        const SizedBox(width: 10),
                        const Text('MFIT', style: TextStyle(color: Colors.white, fontSize: 22, fontWeight: FontWeight.w900, letterSpacing: 2)),
                        const Spacer(),
                        const Text('MOVE YOUR WAY', style: TextStyle(color: Color(0xFFD8E5D9), fontSize: 9, fontWeight: FontWeight.w800, letterSpacing: 2)),
                      ]),
                    ),
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 16),
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(20),
                        child: SizedBox(
                          height: 350,
                          child: Stack(children: [
                            FlutterMap(
                              options: MapOptions(
                                initialCenter: center, initialZoom: hasRoute ? 15 : 12,
                                interactionOptions: const InteractionOptions(flags: InteractiveFlag.none),
                              ),
                              children: [
                                TileLayer(
                                  urlTemplate: 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
                                  userAgentPackageName: 'com.mfit.app', maxZoom: 19,
                                ),
                                TileLayer(
                                  urlTemplate: 'https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}',
                                  userAgentPackageName: 'com.mfit.app', maxZoom: 19,
                                ),
                                if (hasRoute) PolylineLayer(polylines: [
                                  Polyline(points: points, strokeWidth: 11, color: _lime.withOpacity(.28)),
                                  Polyline(points: points, strokeWidth: 5, color: _lime),
                                ]),
                                if (points.isNotEmpty) MarkerLayer(markers: [
                                  Marker(point: points.first, width: 30, height: 30, child: const Icon(Icons.trip_origin, color: Colors.white, size: 27)),
                                  if (hasRoute) Marker(point: points.last, width: 34, height: 34, child: const Icon(Icons.location_on, color: _lime, size: 32)),
                                ]),
                                RichAttributionWidget(attributions: [
                                  TextSourceAttribution('Imagery & labels © Esri, Maxar, Earthstar Geographics'),
                                ]),
                              ],
                            ),
                            Positioned(
                              top: 12, left: 12,
                              child: Container(
                                padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 7),
                                decoration: BoxDecoration(color: _ink.withOpacity(.82), borderRadius: BorderRadius.circular(20)),
                                child: Text(hasRoute ? 'GPS ROUTE' : 'YOUR ROUTE',
                                  style: const TextStyle(color: _lime, fontSize: 9, fontWeight: FontWeight.w900, letterSpacing: 1.3)),
                              ),
                            ),
                            if (!hasRoute) Positioned.fill(
                              child: Container(
                                color: _ink.withOpacity(.48), alignment: Alignment.center, padding: const EdgeInsets.all(24),
                                child: const Text('بعد از ثبت یک مسیر GPS واقعی، نقشه و مسیرت اینجا نمایش داده می‌شود.',
                                  textAlign: TextAlign.center,
                                  style: TextStyle(color: Colors.white, fontSize: 13, height: 1.6, fontWeight: FontWeight.w700)),
                              ),
                            ),
                          ]),
                        ),
                      ),
                    ),
                    Padding(
                      padding: const EdgeInsets.fromLTRB(22, 22, 22, 8),
                      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        const Text('DISTANCE', style: TextStyle(color: Color(0xFFB8C8BB), fontSize: 10, fontWeight: FontWeight.w800, letterSpacing: 2)),
                        const SizedBox(height: 1),
                        Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
                          Text(tracker.distanceLabel, style: const TextStyle(color: Colors.white, fontSize: 66, height: 1, fontWeight: FontWeight.w900, letterSpacing: -3)),
                          const Padding(padding: EdgeInsets.only(left: 8, bottom: 9),
                            child: Text('KM', style: TextStyle(color: _lime, fontSize: 19, fontWeight: FontWeight.w900))),
                        ]),
                        const SizedBox(height: 5),
                        const Text('WALKING • GPS TRACKED', style: TextStyle(color: Color(0xFFD8E5D9), fontSize: 10, fontWeight: FontWeight.w800, letterSpacing: 1.1)),
                      ]),
                    ),
                    Padding(
                      padding: const EdgeInsets.fromLTRB(20, 12, 20, 22),
                      child: Row(children: [
                        Expanded(child: _ShareMetric(icon: Icons.route_rounded, label: 'GPS POINTS', value: '${points.length}')),
                        Container(width: 1, height: 42, color: Colors.white24),
                        Expanded(child: _ShareMetric(icon: Icons.speed_rounded, label: 'LIVE SPEED', value: speed > .5 ? '${speed.toStringAsFixed(1)} km/h' : '—')),
                        Container(width: 1, height: 42, color: Colors.white24),
                        const Expanded(child: _ShareMetric(icon: Icons.calendar_today_rounded, label: 'DATE', value: 'TODAY')),
                      ]),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 18),
            FilledButton.icon(
              onPressed: hasRoute && !_sharing ? _shareCard : null,
              icon: _sharing
                ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                : const Icon(Icons.ios_share_rounded),
              label: Text(_sharing ? 'در حال آماده‌سازی کارت…' : 'اشتراک‌گذاری کارت مسیر'),
              style: FilledButton.styleFrom(
                backgroundColor: _green, foregroundColor: Colors.white, minimumSize: const Size.fromHeight(54),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(17)),
              ),
            ),
            const SizedBox(height: 10),
            const Text('کارت فقط از اطلاعات واقعی GPS استفاده می‌کند. زمان و کالری تا وقتی ثبت دقیق آن‌ها به موتور فعالیت اضافه نشده، به‌صورت عدد ساختگی نمایش داده نمی‌شوند.',
              textAlign: TextAlign.center, style: TextStyle(color: Color(0xFF758177), fontSize: 11, height: 1.6)),
          ],
        );
      },
    ),
  );
}

class _ShareMetric extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;
  const _ShareMetric({required this.icon, required this.label, required this.value});
  @override
  Widget build(BuildContext context) => Column(children: [
    Icon(icon, color: _lime, size: 19),
    const SizedBox(height: 7),
    Text(value, style: const TextStyle(color: Colors.white, fontSize: 13, fontWeight: FontWeight.w900)),
    const SizedBox(height: 3),
    Text(label, style: const TextStyle(color: Color(0xFFB8C8BB), fontSize: 8, fontWeight: FontWeight.w800, letterSpacing: .8)),
  ]);
}
