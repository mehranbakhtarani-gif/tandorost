import 'dart:async';
import 'dart:convert';
import 'dart:ui';
import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_background_service/flutter_background_service.dart';
import 'package:geolocator/geolocator.dart';
import 'package:latlong2/latlong.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:shared_preferences/shared_preferences.dart';

const _routeKey = 'mfit.gps.route.v1';
const _distanceKey = 'mfit.gps.distance.v1';

class GpsTracker extends ChangeNotifier {
  final FlutterBackgroundService _service = FlutterBackgroundService();
  StreamSubscription<Map<String, dynamic>?>? _updates;
  List<LatLng> route = [];
  double distanceMeters = 0;
  double speedMps = 0;
  bool isTracking = false;
  Future<void>? _initializing;
  String status = 'GPS هنوز شروع نشده';

  Future<void> initialize() => _initializing ??= _initialize();

  Future<void> _initialize() async {
    if (kIsWeb) {
      status = 'ثبت GPS در نسخه وب پشتیبانی نمی‌شود؛ نسخه Android لازم است';
      notifyListeners();
      return;
    }
    await _service.configure(
      androidConfiguration: AndroidConfiguration(
        onStart: gpsServiceOnStart,
        autoStart: false,
        autoStartOnBoot: false,
        isForegroundMode: true,
        initialNotificationTitle: 'MFit · تمرین فعال',
        initialNotificationContent: 'در انتظار دریافت موقعیت GPS',
        foregroundServiceNotificationId: 731,
      ),
      iosConfiguration: IosConfiguration(
        autoStart: false,
        onForeground: gpsServiceOnStart,
        onBackground: gpsServiceOnIosBackground,
      ),
    );
    final prefs = await SharedPreferences.getInstance();
    distanceMeters = prefs.getDouble(_distanceKey) ?? 0;
    final raw = prefs.getString(_routeKey);
    if (raw != null) {
      try {
        route = (jsonDecode(raw) as List).map((p) =>
          LatLng((p['lat'] as num).toDouble(), (p['lng'] as num).toDouble())).toList();
      } catch (_) { route = []; }
    }
    _updates = _service.on('gpsUpdate').listen((event) {
      if (event == null) return;
      distanceMeters = (event['distance'] as num?)?.toDouble() ?? distanceMeters;
      speedMps = (event['speed'] as num?)?.toDouble() ?? 0;
      final lat = (event['lat'] as num?)?.toDouble();
      final lng = (event['lng'] as num?)?.toDouble();
      if (lat != null && lng != null) {
        route = [...route, LatLng(lat, lng)];
      }
      isTracking = event['tracking'] == true;
      status = isTracking ? 'در حال ثبت مسیر واقعی' : 'ثبت مسیر متوقف است';
      notifyListeners();
    });
  }

  Future<bool> start() async {
    await initialize();
    if (kIsWeb) {
      status = 'برای GPS واقعی از نسخه Android استفاده کن';
      notifyListeners();
      return false;
    }
    if (!await Geolocator.isLocationServiceEnabled()) {
      status = 'مکان‌یاب گوشی خاموش است؛ GPS را روشن کن';
      notifyListeners();
      await Geolocator.openLocationSettings();
      return false;
    }
    var permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) permission = await Geolocator.requestPermission();
    if (permission == LocationPermission.denied || permission == LocationPermission.deniedForever) {
      status = 'مجوز مکان داده نشده است';
      notifyListeners();
      if (permission == LocationPermission.deniedForever) await Geolocator.openAppSettings();
      return false;
    }
    if (permission != LocationPermission.always) {
      final always = await Permission.locationAlways.request();
      if (!always.isGranted) {
        status = 'برای ادامه مسیر در پس‌زمینه، دسترسی مکان روی «همیشه مجاز» لازم است';
        notifyListeners();
        await Geolocator.openAppSettings();
        return false;
      }
    }
    await Permission.notification.request();
    route = [];
    distanceMeters = 0;
    speedMps = 0;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_routeKey, '[]');
    await prefs.setDouble(_distanceKey, 0);
    await _service.startService();
    isTracking = true;
    status = 'سرویس GPS در حال راه‌اندازی است';
    notifyListeners();
    return true;
  }

  Future<void> stop() async {
    if (kIsWeb) return;
    _service.invoke('stopTracking');
    isTracking = false;
    speedMps = 0;
    status = 'ثبت مسیر پایان یافت';
    notifyListeners();
  }

  String get distanceLabel => (distanceMeters / 1000).toStringAsFixed(2);
  String get paceLabel {
    if (speedMps < 0.5) return '—';
    final secondsPerKm = 1000 / speedMps;
    final minutes = secondsPerKm ~/ 60;
    final seconds = (secondsPerKm % 60).round().toString().padLeft(2, '0');
    return '$minutes:$seconds';
  }

  @override
  void dispose() {
    _updates?.cancel();
    super.dispose();
  }
}

@pragma('vm:entry-point')
Future<bool> gpsServiceOnIosBackground(ServiceInstance service) async {
  DartPluginRegistrant.ensureInitialized();
  return true;
}

@pragma('vm:entry-point')
void gpsServiceOnStart(ServiceInstance service) async {
  DartPluginRegistrant.ensureInitialized();
  WidgetsFlutterBinding.ensureInitialized();
  if (service is AndroidServiceInstance) {
    await service.setAsForegroundService();
    await service.setForegroundNotificationInfo(
      title: 'MFit · تمرین فعال',
      content: 'در حال دریافت موقعیت GPS',
    );
  }
  final prefs = await SharedPreferences.getInstance();
  var distance = 0.0;
  Position? previous;
  var route = <Map<String, double>>[];
  await prefs.setString(_routeKey, '[]');
  await prefs.setDouble(_distanceKey, 0);

  final stream = Geolocator.getPositionStream(
    locationSettings: const LocationSettings(
      accuracy: LocationAccuracy.bestForNavigation,
      distanceFilter: 5,
    ),
  ).listen((position) async {
    if (position.accuracy > 40) return;
    if (previous != null) {
      final step = Geolocator.distanceBetween(
        previous!.latitude, previous!.longitude,
        position.latitude, position.longitude,
      );
      if (step > 0.8 && step < 100) distance += step;
    }
    previous = position;
    route.add({'lat': position.latitude, 'lng': position.longitude});
    if (route.length > 4000) route = route.sublist(route.length - 4000);
    await prefs.setString(_routeKey, jsonEncode(route));
    await prefs.setDouble(_distanceKey, distance);
    final speed = position.speed.isFinite && position.speed > 0 ? position.speed : 0.0;
    service.invoke('gpsUpdate', {
      'lat': position.latitude, 'lng': position.longitude,
      'distance': distance, 'speed': speed, 'tracking': true,
    });
    if (service is AndroidServiceInstance) {
      await service.setForegroundNotificationInfo(
        title: 'MFit · تمرین در حال اجرا',
        content: '${(distance / 1000).toStringAsFixed(2)} km · ثبت مسیر GPS فعال',
      );
    }
  }, onError: (_) {
    service.invoke('gpsUpdate', {'tracking': false, 'distance': distance, 'speed': 0});
  });

  service.on('stopTracking').listen((_) async {
    await stream.cancel();
    service.invoke('gpsUpdate', {'tracking': false, 'distance': distance, 'speed': 0});
    if (service is AndroidServiceInstance) await service.stopSelf();
  });
}
