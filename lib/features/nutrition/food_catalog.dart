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
    FoodNutrition(id: 'bread-sangak', name: 'نان سنگک', aliases: ['سنگک'], kcal: 265, protein: 9, carbs: 55, fat: 1.2, fiber: 4.5),
    FoodNutrition(id: 'bread-lavash', name: 'نان لواش', aliases: ['لواش'], kcal: 277, protein: 9.1, carbs: 56, fat: 1.2, fiber: 2.2),
    FoodNutrition(id: 'bread-barbari', name: 'نان بربری', aliases: ['بربری'], kcal: 270, protein: 9.5, carbs: 53, fat: 2.2, fiber: 3.5),
    FoodNutrition(id: 'whole-wheat-bread', name: 'نان سبوس‌دار', aliases: ['نان سبوس دار'], kcal: 247, protein: 13, carbs: 41, fat: 4.2, fiber: 7),
    FoodNutrition(id: 'milk-lowfat', name: 'شیر کم‌چرب', aliases: ['شیر'], kcal: 42, protein: 3.4, carbs: 5, fat: 1, fiber: 0),
    FoodNutrition(id: 'yogurt-lowfat', name: 'ماست کم‌چرب', aliases: ['ماست'], kcal: 63, protein: 5.3, carbs: 7, fat: 1.6, fiber: 0),
    FoodNutrition(id: 'cheese-feta', name: 'پنیر سفید', aliases: ['پنیر'], kcal: 264, protein: 14.2, carbs: 4.1, fat: 21.3, fiber: 0),
    FoodNutrition(id: 'doogh', name: 'دوغ ساده', aliases: ['دوغ'], kcal: 20, protein: 1.1, carbs: 1.7, fat: 0.9, fiber: 0),
    FoodNutrition(id: 'orange', name: 'پرتقال', aliases: ['orange'], kcal: 47, protein: 0.9, carbs: 11.8, fat: 0.1, fiber: 2.4),
    FoodNutrition(id: 'pomegranate', name: 'انار', aliases: ['pomegranate'], kcal: 83, protein: 1.7, carbs: 18.7, fat: 1.2, fiber: 4),
    FoodNutrition(id: 'carrot', name: 'هویج خام', aliases: ['هویج'], kcal: 41, protein: 0.9, carbs: 9.6, fat: 0.2, fiber: 2.8),
    FoodNutrition(id: 'lettuce', name: 'کاهو', aliases: ['کاهو'], kcal: 15, protein: 1.4, carbs: 2.9, fat: 0.2, fiber: 1.3),
    FoodNutrition(id: 'spinach', name: 'اسفناج پخته', aliases: ['اسفناج'], kcal: 23, protein: 3, carbs: 3.8, fat: 0.3, fiber: 2.4),
    FoodNutrition(id: 'beans', name: 'لوبیا چیتی پخته', aliases: ['لوبیا', 'لوبیا چیتی'], kcal: 143, protein: 9, carbs: 26, fat: 0.5, fiber: 9),
    FoodNutrition(id: 'chickpeas', name: 'نخود پخته', aliases: ['نخود'], kcal: 164, protein: 8.9, carbs: 27.4, fat: 2.6, fiber: 7.6),
    FoodNutrition(id: 'salmon', name: 'ماهی سالمون پخته', aliases: ['سالمون', 'ماهی'], kcal: 206, protein: 22.1, carbs: 0, fat: 12.4, fiber: 0),
    FoodNutrition(id: 'tuna', name: 'تن ماهی آبکش‌شده', aliases: ['تن ماهی'], kcal: 116, protein: 25.5, carbs: 0, fat: 0.8, fiber: 0),
    FoodNutrition(id: 'beef', name: 'گوشت گوساله کم‌چرب پخته', aliases: ['گوشت', 'گوشت قرمز'], kcal: 217, protein: 26, carbs: 0, fat: 12, fiber: 0),
    FoodNutrition(id: 'kebab', name: 'کباب کوبیده پخته', aliases: ['کوبیده', 'کباب'], kcal: 250, protein: 17, carbs: 2, fat: 19, fiber: 0),
    FoodNutrition(id: 'ghormeh', name: 'قرمه‌سبزی', aliases: ['قورمه سبزی', 'قرمه سبزی'], kcal: 145, protein: 8, carbs: 7, fat: 9, fiber: 2),
    FoodNutrition(id: 'gheimeh', name: 'خورش قیمه', aliases: ['قیمه'], kcal: 135, protein: 7, carbs: 12, fat: 6, fiber: 2),
    FoodNutrition(id: 'fesenjan', name: 'خورش فسنجان', aliases: ['فسنجان'], kcal: 230, protein: 8, carbs: 12, fat: 17, fiber: 2),
    FoodNutrition(id: 'abgoosht', name: 'آبگوشت بدون نان', aliases: ['دیزی', 'آبگوشت'], kcal: 150, protein: 10, carbs: 10, fat: 7, fiber: 2),
    FoodNutrition(id: 'kotlet', name: 'کتلت خانگی', aliases: ['کتلت'], kcal: 235, protein: 11, carbs: 17, fat: 14, fiber: 2),
    FoodNutrition(id: 'tahdig', name: 'ته‌دیگ برنج', aliases: ['ته دیگ'], kcal: 250, protein: 4, carbs: 39, fat: 8, fiber: 1),
    FoodNutrition(id: 'pasta', name: 'ماکارونی پخته', aliases: ['ماکارونی', 'پاستا'], kcal: 158, protein: 5.8, carbs: 30.9, fat: 0.9, fiber: 1.8),
    FoodNutrition(id: 'barley-soup', name: 'سوپ جو', aliases: ['سوپ'], kcal: 55, protein: 2.5, carbs: 8, fat: 1.5, fiber: 1.5),
    FoodNutrition(id: 'lentil-rice', name: 'عدس‌پلو', aliases: ['عدس پلو'], kcal: 165, protein: 6, carbs: 29, fat: 3, fiber: 4),
    FoodNutrition(id: 'rice-raw', name: 'برنج سفید خام', aliases: ['برنج خام'], kcal: 365, protein: 7.1, carbs: 80, fat: 0.7, fiber: 1.3),
    FoodNutrition(id: 'egg-white', name: 'سفیده تخم‌مرغ', aliases: ['سفیده'], kcal: 52, protein: 10.9, carbs: 0.7, fat: 0.2, fiber: 0),
    FoodNutrition(id: 'dates', name: 'خرما', aliases: ['خرما'], kcal: 282, protein: 2.5, carbs: 75, fat: 0.4, fiber: 8),
    FoodNutrition(id: 'raisins', name: 'کشمش', aliases: ['کشمش'], kcal: 299, protein: 3.1, carbs: 79, fat: 0.5, fiber: 3.7),
    FoodNutrition(id: 'olive-oil', name: 'روغن زیتون', aliases: ['روغن زیتون'], kcal: 884, protein: 0, carbs: 0, fat: 100, fiber: 0),
    FoodNutrition(id: 'almonds', name: 'بادام', aliases: ['بادام'], kcal: 579, protein: 21.2, carbs: 21.6, fat: 49.9, fiber: 12.5),
    FoodNutrition(id: 'pistachio', name: 'پسته', aliases: ['پسته'], kcal: 562, protein: 20.2, carbs: 27.2, fat: 45.3, fiber: 10.6),
    FoodNutrition(id: 'tomato-paste', name: 'رب گوجه‌فرنگی', aliases: ['رب گوجه', 'رب'], kcal: 82, protein: 4.3, carbs: 18.9, fat: 0.5, fiber: 4.1),
    FoodNutrition(id: 'chicken-thigh', name: 'ران مرغ پخته بدون پوست', aliases: ['ران مرغ'], kcal: 209, protein: 26, carbs: 0, fat: 10.9, fiber: 0),
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
    'foodId': food.id, 'foodName': food.name, 'kcal': food.kcal, 'protein': food.protein, 'carbs': food.carbs, 'fat': food.fat, 'fiber': food.fiber, 'sourceLabel': food.sourceLabel, 'grams': grams, 'loggedAt': loggedAt.toIso8601String(),
  };
}
