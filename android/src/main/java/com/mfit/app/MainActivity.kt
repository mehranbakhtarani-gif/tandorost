package com.mfit.app

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import org.json.JSONObject

private val Ink = Color(0xFF17191B)
private val Lime = Color(0xFFD7FF00)
private val Paper = Color(0xFFF4F5F1)
private val Muted = Color(0xFF777D7B)

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("mfit_native", MODE_PRIVATE) }
    private var tracking by mutableStateOf(false)
    private var liveDistance by mutableFloatStateOf(0f)
    private var liveSeconds by mutableLongStateOf(0L)
    private var lastActivity by mutableStateOf("—")
    private var imported by mutableStateOf(false)
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true) startTracking()
    }
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { raw ->
                val obj = JSONObject(raw.readText())
                prefs.edit().putString("legacy_data", obj.toString()).putBoolean("legacy_imported", true).apply()
                imported = true
            }
        } catch (_: Exception) { imported = false }
    }
    private val receiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { refreshTrack() }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshTrack()
        lastActivity = prefs.getString("last_activity", "—") ?: "—"
        imported = prefs.getBoolean("legacy_imported", false)
        val filter = IntentFilter("com.mfit.app.TRACK_UPDATE")
        if (Build.VERSION.SDK_INT >= 33) { registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED) } else { registerReceiver(receiver, filter) }
        setContent { MFitApp(tracking, liveDistance, liveSeconds, lastActivity, imported,
            onStart = { requestTracking() }, onStop = { stopTracking() },
            onImport = { importLauncher.launch(arrayOf("application/json", "text/*")) }) }
    }
    private fun refreshTrack() {
        liveDistance = prefs.getFloat("live_distance", 0f)
        liveSeconds = prefs.getLong("live_seconds", 0L)
        tracking = liveSeconds > 0L
        lastActivity = prefs.getString("last_activity", "—") ?: "—"
    }
    private fun requestTracking() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) startTracking() else permissionLauncher.launch(missing.toTypedArray())
    }
    private fun startTracking() { ContextCompat.startForegroundService(this, Intent(this, TrackingService::class.java)); tracking = true }
    private fun stopTracking() { stopService(Intent(this, TrackingService::class.java)); tracking = false; refreshTrack() }
    override fun onDestroy() { unregisterReceiver(receiver); super.onDestroy() }
}

private enum class Tab(val label: String) { HOME("خانه"), ACTIVITY("فعالیت"), FOOD("تغذیه"), SLEEP("خواب"), PROFILE("پروفایل") }

@Composable
private fun MFitApp(tracking: Boolean, distance: Float, seconds: Long, lastActivity: String, imported: Boolean,
                    onStart: () -> Unit, onStop: () -> Unit, onImport: () -> Unit) {
    var tab by remember { mutableStateOf(Tab.HOME) }
    var water by remember { mutableIntStateOf(0) }
    var meal by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("1") }
    var mealLog by remember { mutableStateOf(listOf<String>()) }
    var sleep by remember { mutableFloatStateOf(8f) }
    var weight by remember { mutableStateOf("96") }
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("mfit_native", Context.MODE_PRIVATE) }
    LaunchedEffect(Unit) {
        water = prefs.getInt("water_today", 0)
        mealLog = prefs.getString("meals_today", "")?.split("|")?.filter { it.isNotBlank() } ?: emptyList()
        weight = prefs.getString("weight", "96") ?: "96"
    }
    MaterialTheme(colorScheme = lightColorScheme(primary = Ink, secondary = Lime, background = Paper, surface = Color.White)) {
        Scaffold(containerColor = Paper, bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                Tab.values().forEach { item ->
                    val selected = tab == item
                    NavigationBarItem(selected = selected, onClick = { tab = item },
                        icon = { Icon(when(item) {
                            Tab.HOME -> Icons.Default.Home
                            Tab.ACTIVITY -> Icons.Default.DirectionsRun
                            Tab.FOOD -> Icons.Default.Restaurant
                            Tab.SLEEP -> Icons.Default.Bedtime
                            Tab.PROFILE -> Icons.Default.Person
                        }, null) },
                        label = { Text(item.label, fontSize = 10.sp, fontWeight = if(selected) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Ink, indicatorColor = Lime, unselectedIconColor = Muted))
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("MFIT", fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp, color = Ink)
                        Text(when(tab) {
                            Tab.HOME -> "امروز رو بساز."
                            Tab.ACTIVITY -> "حرکت کن."
                            Tab.FOOD -> "هوشمندانه بخور."
                            Tab.SLEEP -> "ریکاوری کن."
                            Tab.PROFILE -> "مسیر تو."
                        }, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                    }
                    Box(Modifier.size(42.dp).clip(CircleShape).background(Lime), contentAlignment = Alignment.Center) { Text("M", fontWeight = FontWeight.Black, fontSize = 20.sp) }
                }
                when(tab) {
                    Tab.HOME -> {
                        HeroPanel(tracking, distance, seconds)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Metric("آب امروز", water.toString(), "لیوان", Modifier.weight(1f))
                            Metric("آخرین فعالیت", lastActivity, "کیلومتر", Modifier.weight(1f))
                        }
                        SectionTitle("عادت امروز", "هر روز کمی بهتر")
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { water++; prefs.edit().putInt("water_today", water).apply() },
                                colors = ButtonDefaults.buttonColors(containerColor = Ink), shape = RoundedCornerShape(14.dp)) {
                                Icon(Icons.Default.WaterDrop, null); Spacer(Modifier.width(6.dp)); Text("ثبت آب")
                            }
                            OutlinedButton(onClick = { tab = Tab.ACTIVITY }, shape = RoundedCornerShape(14.dp)) {
                                Icon(Icons.Default.DirectionsRun, null); Spacer(Modifier.width(6.dp)); Text("شروع فعالیت")
                            }
                        }
                        SectionTitle("خلاصه امروز", "پیشرفتت رو دنبال کن")
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Metric("خواب", sleep.toInt().toString(), "ساعت", Modifier.weight(1f))
                            Metric("وزن", weight, "کیلوگرم", Modifier.weight(1f))
                        }
                    }
                    Tab.ACTIVITY -> {
                        ActivityPanel(tracking, distance, seconds, onStart, onStop)
                        SectionTitle("آخرین رکورد", "فعالیت ذخیره‌شده")
                        Metric("مسافت آخرین فعالیت", lastActivity, "کیلومتر", Modifier.fillMaxWidth())
                    }
                    Tab.FOOD -> FoodPanel(meal, { meal = it }, amount, { amount = it }, mealLog, { value ->
                        mealLog = mealLog + value
                        prefs.edit().putString("meals_today", mealLog.joinToString("|")).apply()
                    })
                    Tab.SLEEP -> {
                        SectionTitle("ریکاوری", "خواب شبانه رو ثبت کن")
                        Text("مدت خواب (ساعت)", color = Muted)
                        Slider(value = sleep, onValueChange = { sleep = it }, valueRange = 0f..12f, steps = 23)
                        Text(sleep.toInt().toString() + " ساعت", fontSize = 34.sp, fontWeight = FontWeight.Black)
                        Text("کیفیت خواب", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("ضعیف", "معمولی", "خوب", "عالی").forEach {
                                AssistChip(onClick = { prefs.edit().putString("sleep_quality", it).apply() }, label = { Text(it) })
                            }
                        }
                    }
                    Tab.PROFILE -> {
                        SectionTitle("پروفایل", "اطلاعات و انتقال داده‌ها")
                        OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text("وزن فعلی (کیلوگرم)") }, modifier = Modifier.fillMaxWidth())
                        Button(onClick = { prefs.edit().putString("weight", weight).apply() }, colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Text("ذخیره پروفایل") }
                        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("انتقال اطلاعات نسخه وب", fontWeight = FontWeight.Bold)
                                Text(if(imported) "فایل اطلاعات قبلی دریافت شده است." else "فایل پشتیبان JSON نسخه PWA را انتخاب کن.", color = Muted)
                                OutlinedButton(onClick = onImport) { Text("وارد کردن فایل پشتیبان") }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable private fun HeroPanel(tracking: Boolean, distance: Float, seconds: Long) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Ink).padding(22.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("YOUR DAILY PERFORMANCE", color = Lime, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            Text(if(tracking) "در حال حرکت" else "امروز، روز توست", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text(if(tracking) "%.2f کیلومتر • %02d:%02d".format(distance / 1000f, seconds / 60, seconds % 60) else "قدم‌های کوچک، نتیجه‌های بزرگ.", color = Color.LightGray, fontSize = 14.sp)
            Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Color(0xFF44494A))) {
                Box(Modifier.fillMaxWidth(if(tracking) (distance / 5000f).coerceIn(.05f, 1f) else .34f).fillMaxHeight().clip(CircleShape).background(Lime))
            }
        }
    }
}

@Composable private fun ActivityPanel(tracking: Boolean, distance: Float, seconds: Long, onStart: () -> Unit, onStop: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFFE5E9E1)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.Map, null, tint = Ink, modifier = Modifier.size(38.dp))
            Text("ردیابی GPS", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("برای شروع، دسترسی موقعیت مکانی را فعال کن", color = Muted, fontSize = 12.sp)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Metric("مسافت", "%.2f".format(distance / 1000f), "کیلومتر", Modifier.weight(1f))
        Metric("زمان", "%02d:%02d".format(seconds / 60, seconds % 60), "دقیقه:ثانیه", Modifier.weight(1f))
    }
    Button(onClick = if(tracking) onStop else onStart, modifier = Modifier.fillMaxWidth().height(58.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if(tracking) Color(0xFFB3261E) else Lime, contentColor = Ink),
        shape = RoundedCornerShape(18.dp)) {
        Icon(if(tracking) Icons.Default.Stop else Icons.Default.PlayArrow, null)
        Spacer(Modifier.width(8.dp))
        Text(if(tracking) "پایان فعالیت" else "شروع ثبت فعالیت", fontWeight = FontWeight.Bold)
    }
}

private data class Food(val name: String, val kcal: Int, val protein: Int, val carbs: Int, val sugar: Int, val fat: Int, val fiber: Int)
private val foods = listOf(
    Food("تخم مرغ",78,6,1,0,5,0), Food("سیب",95,0,25,19,0,4), Food("موز",105,1,27,14,0,3),
    Food("ماست یونانی",100,10,4,4,5,0), Food("سینه مرغ پخته",165,31,0,0,4,0),
    Food("برنج پخته",205,4,45,0,0,1), Food("سیب زمینی آبپز",130,3,30,2,0,3),
    Food("نان سنگک",80,3,16,1,1,1), Food("عدس پخته",230,18,40,4,1,16),
    Food("گردو",185,4,4,1,18,2), Food("خیار",16,1,4,2,0,1), Food("گوجه",22,1,5,3,0,1)
)

@Composable private fun FoodPanel(meal: String, onMeal: (String) -> Unit, amount: String, onAmount: (String) -> Unit,
                                  log: List<String>, onAdd: (String) -> Unit) {
    var selected by remember { mutableStateOf(foods.first()) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("ثبت تغذیه", "مقادیر تخمینی به‌صورت خودکار")
        OutlinedTextField(value = meal, onValueChange = { value ->
            onMeal(value); foods.firstOrNull { it.name.contains(value, true) }?.let { selected = it }
        }, label = { Text("نام ماده غذایی") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            foods.take(4).forEach { food -> AssistChip(onClick = { selected = food; onMeal(food.name) }, label = { Text(food.name, fontSize = 10.sp) }) }
        }
        OutlinedTextField(value = amount, onValueChange = onAmount, label = { Text("تعداد سهم") }, modifier = Modifier.fillMaxWidth())
        val factor = (amount.toFloatOrNull() ?: 1f).coerceAtLeast(0f)
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(selected.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text((selected.kcal * factor).toInt().toString() + " کیلوکالری", fontSize = 26.sp, fontWeight = FontWeight.Black)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("پروتئین " + (selected.protein * factor).toInt() + "g")
                    Text("کربوهیدرات " + (selected.carbs * factor).toInt() + "g")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("قند " + (selected.sugar * factor).toInt() + "g")
                    Text("چربی " + (selected.fat * factor).toInt() + "g")
                }
                Text("فیبر " + (selected.fiber * factor).toInt() + "g", color = Muted)
            }
        }
        Button(onClick = { onAdd(selected.name + " • " + (selected.kcal * factor).toInt() + " kcal") },
            modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Text("ثبت وعده", color = Color.White) }
        SectionTitle("موارد ثبت‌شده", "امروز")
        log.forEach { Text("• " + it, modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp)).padding(12.dp)) }
    }
}

@Composable private fun SectionTitle(title: String, subtitle: String) {
    Column { Text(title, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Ink); Text(subtitle, fontSize = 12.sp, color = Muted) }
}
@Composable private fun Metric(title: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Color.White).padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, fontSize = 12.sp, color = Muted)
        Text(value, fontSize = 25.sp, fontWeight = FontWeight.Black, color = Ink)
        Text(unit, fontSize = 11.sp, color = Muted)
    }
}