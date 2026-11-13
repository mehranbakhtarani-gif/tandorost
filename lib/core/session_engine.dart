import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// One source of truth for an active workout. Elapsed time is derived from
/// wall-clock timestamps, so tab changes and app suspension do not lose time.
class SessionEngine extends ChangeNotifier {
  static const _startedKey = 'mfit.session.startedAt';
  static const _elapsedKey = 'mfit.session.elapsedBeforeRun';
  static const _activeKey = 'mfit.session.active';

  DateTime? _startedAt;
  Duration _elapsedBeforeRun = Duration.zero;
  bool _isActive = false;
  Timer? _ticker;

  bool get isActive => _isActive;
  Duration get elapsed => _elapsedBeforeRun +
      (_isActive && _startedAt != null
          ? DateTime.now().difference(_startedAt!)
          : Duration.zero);

  Future<void> restore() async {
    final prefs = await SharedPreferences.getInstance();
    final started = prefs.getInt(_startedKey);
    _elapsedBeforeRun = Duration(seconds: prefs.getInt(_elapsedKey) ?? 0);
    _isActive = prefs.getBool(_activeKey) ?? false;
    _startedAt = started == null
        ? null
        : DateTime.fromMillisecondsSinceEpoch(started);
    if (_isActive && _startedAt == null) _isActive = false;
    if (_isActive) _startTicker();
    notifyListeners();
  }

  Future<void> start() async {
    if (_isActive) return;
    _startedAt = DateTime.now();
    _isActive = true;
    await _persist();
    _startTicker();
    notifyListeners();
  }

  Future<void> pause() async {
    if (!_isActive) return;
    _elapsedBeforeRun = elapsed;
    _startedAt = null;
    _isActive = false;
    _ticker?.cancel();
    await _persist();
    notifyListeners();
  }

  Future<void> reset() async {
    _ticker?.cancel();
    _startedAt = null;
    _elapsedBeforeRun = Duration.zero;
    _isActive = false;
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_startedKey);
    await prefs.remove(_elapsedKey);
    await prefs.setBool(_activeKey, false);
    notifyListeners();
  }

  void _startTicker() {
    _ticker?.cancel();
    _ticker = Timer.periodic(const Duration(seconds: 1), (_) => notifyListeners());
  }

  Future<void> _persist() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(_elapsedKey, _elapsedBeforeRun.inSeconds);
    await prefs.setBool(_activeKey, _isActive);
    if (_startedAt != null) {
      await prefs.setInt(_startedKey, _startedAt!.millisecondsSinceEpoch);
    } else {
      await prefs.remove(_startedKey);
    }
  }

  String get formatted {
    final seconds = elapsed.inSeconds;
    final h = (seconds ~/ 3600).toString().padLeft(2, '0');
    final m = ((seconds % 3600) ~/ 60).toString().padLeft(2, '0');
    final s = (seconds % 60).toString().padLeft(2, '0');
    return '$h:$m:$s';
  }

  @override
  void dispose() {
    _ticker?.cancel();
    super.dispose();
  }
}
