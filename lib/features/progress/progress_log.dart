import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';

enum ProgressMetric { weightKg, waistCm, steps, activeCalories, distanceKm, sleepHours }

class ProgressEntry {
  final DateTime date;
  final ProgressMetric metric;
  final double value;
  const ProgressEntry({required this.date, required this.metric, required this.value});

  Map<String, dynamic> toJson() => {
    'date': date.toIso8601String(), 'metric': metric.name, 'value': value,
  };

  factory ProgressEntry.fromJson(Map<String, dynamic> json) => ProgressEntry(
    date: DateTime.parse(json['date'] as String),
    metric: ProgressMetric.values.firstWhere((m) => m.name == json['metric']),
    value: (json['value'] as num).toDouble(),
  );
}

class ProgressLog {
  static const _storageKey = 'mfit.progress.entries.v1';

  Future<List<ProgressEntry>> readAll() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_storageKey);
    if (raw == null || raw.isEmpty) return [];
    try {
      final decoded = jsonDecode(raw) as List<dynamic>;
      return decoded
          .map((item) => ProgressEntry.fromJson(item as Map<String, dynamic>))
          .toList()
        ..sort((a, b) => a.date.compareTo(b.date));
    } catch (_) {
      return [];
    }
  }

  Future<void> add(ProgressEntry entry) async {
    final entries = await readAll();
    entries.removeWhere((e) =>
        e.metric == entry.metric &&
        e.date.year == entry.date.year &&
        e.date.month == entry.date.month &&
        e.date.day == entry.date.day);
    entries.add(entry);
    entries.sort((a, b) => a.date.compareTo(b.date));
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      _storageKey,
      jsonEncode(entries.map((e) => e.toJson()).toList()),
    );
  }

  Future<List<ProgressEntry>> forMetric(ProgressMetric metric) async =>
      (await readAll()).where((e) => e.metric == metric).toList();

  /// Returns null until at least two real measurements exist.
  Future<double?> change(ProgressMetric metric) async {
    final values = await forMetric(metric);
    if (values.length < 2) return null;
    return values.last.value - values.first.value;
  }
}
