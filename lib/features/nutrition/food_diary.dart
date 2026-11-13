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
        final matches = FoodCatalog.foods.where((item) => item.id == data['foodId']);
        final food = matches.isNotEmpty ? matches.first : FoodNutrition(
          id: data['foodId'] as String,
          name: data['foodName'] as String? ?? 'خوراکی ثبت‌شده',
          kcal: (data['kcal'] as num?)?.toDouble() ?? 0,
          protein: (data['protein'] as num?)?.toDouble() ?? 0,
          carbs: (data['carbs'] as num?)?.toDouble() ?? 0,
          fat: (data['fat'] as num?)?.toDouble() ?? 0,
          fiber: (data['fiber'] as num?)?.toDouble() ?? 0,
          sourceLabel: data['sourceLabel'] as String? ?? 'اطلاعات ذخیره‌شده',
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
