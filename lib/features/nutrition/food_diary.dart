import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import 'food_catalog.dart';

/// Local food diary. Each entry stores a catalogue id, edible grams and time.
class FoodDiary {
  static const _key = 'mfit.food.diary.v1';

  Future<List<FoodEntry>> readAll() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_key);
    if (raw == null) return [];
    try {
      final rows = jsonDecode(raw) as List<dynamic>;
      return rows.map((row) {
        final data = row as Map<String, dynamic>;
        final food = FoodCatalog.foods.firstWhere(
          (item) => item.id == data['foodId'],
        );
        return FoodEntry(
          food: food,
          grams: (data['grams'] as num).toDouble(),
          loggedAt: DateTime.parse(data['loggedAt'] as String),
        );
      }).toList()..sort((a, b) => b.loggedAt.compareTo(a.loggedAt));
    } catch (_) {
      return [];
    }
  }

  Future<void> add(FoodEntry entry) async {
    final entries = await readAll();
    entries.insert(0, entry);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_key, jsonEncode(entries.map((e) => e.toJson()).toList()));
  }

  Future<void> removeAt(int index) async {
    final entries = await readAll();
    if (index < 0 || index >= entries.length) return;
    entries.removeAt(index);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_key, jsonEncode(entries.map((e) => e.toJson()).toList()));
  }
}
