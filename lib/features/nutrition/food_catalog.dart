/// Offline starter catalogue. Nutrients are per 100 g edible portion.
/// Values are estimates; UI must retain the source/estimate label.
class FoodNutrition {
  final String id;
  final String name;
  final List<String> aliases;
  final double kcal, protein, carbs, fat, fiber;
  final String sourceLabel;

  const FoodNutrition({
    required this.id,
    required this.name,
    this.aliases = const [],
    required this.kcal,
    required this.protein,
    required this.carbs,
    required this.fat,
    required this.fiber,
    this.sourceLabel = 'برآورد از پایگاه داخلی · در صورت نیاز قابل اصلاح',
  });

  Map<String, double> forGrams(double grams) {
    final factor = grams / 100;
    return {
      'kcal': kcal * factor,
      'protein': protein * factor,
      'carbs': carbs * factor,
      'fat': fat * factor,
      'fiber': fiber * factor,
    };
  }
}

class FoodCatalog {
  static const foods = <FoodNutrition>[
    FoodNutrition(id: 'egg', name: 'تخم‌مرغ کامل', aliases: ['تخم مرغ', 'egg'], kcal: 143, protein: 12.6, carbs: 0.7, fat: 9.5, fiber: 0),
    FoodNutrition(id: 'apple', name: 'سیب با پوست', aliases: ['سیب', 'apple'], kcal: 52, protein: 0.3, carbs: 13.8, fat: 0.2, fiber: 2.4),
    FoodNutrition(id: 'chicken', name: 'سینه مرغ پخته', aliases: ['مرغ', 'سینه مرغ', 'chicken breast'], kcal: 165, protein: 31, carbs: 0, fat: 3.6, fiber: 0),
    FoodNutrition(id: 'greek-yogurt', name: 'ماست یونانی ساده', aliases: ['ماست یونانی', 'یونانی'], kcal: 97, protein: 9, carbs: 3.9, fat: 5, fiber: 0),
    FoodNutrition(id: 'rice', name: 'برنج سفید پخته', aliases: ['برنج', 'کته', 'چلو', 'rice'], kcal: 130, protein: 2.7, carbs: 28.2, fat: 0.3, fiber: 0.4),
    FoodNutrition(id: 'potato', name: 'سیب‌زمینی آب‌پز', aliases: ['سیب زمینی', 'سیب‌زمینی', 'potato'], kcal: 87, protein: 1.9, carbs: 20.1, fat: 0.1, fiber: 1.8),
    FoodNutrition(id: 'lentils', name: 'عدس پخته', aliases: ['عدسی', 'عدس'], kcal: 116, protein: 9, carbs: 20.1, fat: 0.4, fiber: 7.9),
    FoodNutrition(id: 'cucumber', name: 'خیار', aliases: ['خیار'], kcal: 15, protein: 0.7, carbs: 3.6, fat: 0.1, fiber: 0.5),
    FoodNutrition(id: 'tomato', name: 'گوجه‌فرنگی', aliases: ['گوجه', 'گوجه فرنگی'], kcal: 18, protein: 0.9, carbs: 3.9, fat: 0.2, fiber: 1.2),
    FoodNutrition(id: 'walnut', name: 'مغز گردو', aliases: ['گردو'], kcal: 654, protein: 15.2, carbs: 13.7, fat: 65.2, fiber: 6.7),
    FoodNutrition(id: 'banana', name: 'موز', aliases: ['banana'], kcal: 89, protein: 1.1, carbs: 22.8, fat: 0.3, fiber: 2.6),
    FoodNutrition(id: 'oats', name: 'جو دوسر خشک', aliases: ['جو دوسر', 'اوتمیل', 'oats'], kcal: 389, protein: 16.9, carbs: 66.3, fat: 6.9, fiber: 10.6),
  ];

  static String _normalize(String value) => value
      .toLowerCase()
      .replaceAll('ي', 'ی')
      .replaceAll('ك', 'ک')
      .replaceAll(RegExp(r'[\u200c\s]+'), ' ')
      .trim();

  static List<FoodNutrition> search(String query) {
    final q = _normalize(query);
    if (q.isEmpty) return const [];
    return foods.where((food) {
      final terms = [food.name, ...food.aliases].map(_normalize);
      return terms.any((term) => term.contains(q) || q.contains(term));
    }).toList();
  }
}

class FoodEntry {
  final FoodNutrition food;
  final double grams;
  final DateTime loggedAt;
  const FoodEntry({required this.food, required this.grams, required this.loggedAt});

  Map<String, dynamic> toJson() => {
    'foodId': food.id, 'grams': grams, 'loggedAt': loggedAt.toIso8601String(),
  };
}
