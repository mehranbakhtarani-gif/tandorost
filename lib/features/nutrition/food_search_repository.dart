import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:flutter/foundation.dart';
import 'food_catalog.dart';

/// Searches Open Food Facts for packaged products. Offline Iranian/common-food
/// entries remain available when network lookup is unavailable.
class FoodSearchRepository {
  static Future<List<FoodNutrition>> searchOnline(String query) async {
    final q = query.trim();
    if (q.length < 2) return const [];
    final uri = Uri.https('world.openfoodfacts.org', '/cgi/search.pl', {
      'search_terms': q,
      'search_simple': '1',
      'action': 'process',
      'json': '1',
      'page_size': '12',
      'fields': 'code,product_name,product_name_fa,nutriments',
    });
    final headers = <String, String>{'Accept': 'application/json'};
    if (!kIsWeb) headers['User-Agent'] = 'MFit/0.2 (food diary lookup)';
    final response = await http.get(uri, headers: headers).timeout(const Duration(seconds: 8));
    if (response.statusCode != 200) return const [];
    final decoded = jsonDecode(utf8.decode(response.bodyBytes)) as Map<String, dynamic>;
    final products = decoded['products'] as List<dynamic>? ?? const [];
    final result = <FoodNutrition>[];
    for (final raw in products) {
      if (raw is! Map<String, dynamic>) continue;
      final name = (raw['product_name_fa'] ?? raw['product_name'] ?? '').toString().trim();
      final code = (raw['code'] ?? '').toString();
      final n = raw['nutriments'] as Map<String, dynamic>? ?? const {};
      double number(String key) {
        final value = n[key];
        if (value is num) return value.toDouble();
        return double.tryParse(value?.toString() ?? '') ?? 0;
      }
      var kcal = number('energy-kcal_100g');
      if (kcal <= 0) {
        final kj = number('energy_100g');
        if (kj > 0) kcal = kj / 4.184;
      }
      if (name.isEmpty || code.isEmpty || kcal <= 0) continue;
      result.add(FoodNutrition(
        id: 'off-$code',
        name: name,
        aliases: [raw['product_name']?.toString() ?? ''],
        kcal: kcal,
        protein: number('proteins_100g'),
        carbs: number('carbohydrates_100g'),
        fat: number('fat_100g'),
        fiber: number('fiber_100g'),
        sourceLabel: 'Open Food Facts · مقادیر بر اساس برچسب محصول؛ ممکن است ناقص باشند',
      ));
    }
    return result;
  }
}
