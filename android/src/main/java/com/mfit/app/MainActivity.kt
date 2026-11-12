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
        sleep = prefs.getFloat("sleep_hours", 8f)
    }
    MaterialTheme(colorScheme = lightColorScheme(primary = Ink, secondary = Lime, background = Paper, surface = Color.White)) {
        Scaffold(
            containerColor = Paper,
            bottomBar = {
                NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
                    Tab.values().forEach { item ->
                        val selected = tab == item
                        NavigationBarItem(
                            selected = selected, onClick = { tab = item },
                            icon = { Icon(when(item) {
                                Tab.HOME -> Icons.Default.Home
                                Tab.ACTIVITY -> Icons.Default.DirectionsRun
                                Tab.FOOD -> Icons.Default.Restaurant
                                Tab.SLEEP -> Icons.Default.Bedtime
                                Tab.PROFILE -> Icons.Default.Person
                            }, null) },
                            label = { Text(item.label, fontSize = 10.sp, fontWeight = if(selected) FontWeight.Bold else FontWeight.Normal) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Ink, indicatorColor = Lime, unselectedIconColor = Muted)
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 22.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("M F I T", fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp, color = Ink)
                        Text(when(tab) {
                            Tab.HOME -> "سلام، آماده‌ای؟"
                            Tab.ACTIVITY -> "وقت حرکته."
                            Tab.FOOD -> "سوخت بدنت."
                            Tab.SLEEP -> "زمان ریکاوری."
                            Tab.PROFILE -> "مسیر پیشرفت تو."
                        }, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                    }
                    Box(Modifier.size(44.dp).clip(CircleShape).background(Lime), contentAlignment = Alignment.Center) {
                        Text("M", fontWeight = FontWeight.Black, fontSize = 21.sp, color = Ink)
                    }
                }
                when(tab) {
                    Tab.HOME -> {
                        HomeHero(tracking, distance, seconds, onStart = { tab = Tab.ACTIVITY; onStart() })
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            DashboardStat("آب امروز", water.toString(), "لیوان", Icons.Default.WaterDrop, Modifier.weight(1f))
                            DashboardStat("آخرین فعالیت", if(lastActivity == "—") "—" else lastActivity, "کیلومتر", Icons.Default.DirectionsRun, Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("سلامت روزانه", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                                Text("چیزهای کوچکی که جمع می‌شوند.", fontSize = 12.sp, color = Muted)
                            }
                            Text("OVERVIEW", fontSize = 10.sp, letterSpacing = 1.5.sp, color = Muted, fontWeight = FontWeight.Bold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            DashboardMini("خواب", sleep.toInt().toString(), "ساعت", Icons.Default.Bedtime, Modifier.weight(1f))
                            DashboardMini("وزن", weight, "کیلوگرم", Icons.Default.MonitorWeight, Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { water++; prefs.edit().putInt("water_today", water).apply() },
                                modifier = Modifier.weight(1f).height(54.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Ink), shape = RoundedCornerShape(18.dp)
                            ) { Icon(Icons.Default.WaterDrop, null); Spacer(Modifier.width(7.dp)); Text("ثبت آب") }
                            OutlinedButton(
                                onClick = { tab = Tab.FOOD },
                                modifier = Modifier.weight(1f).height(54.dp),
                                shape = RoundedCornerShape(18.dp)
                            ) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(7.dp)); Text("ثبت غذا") }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("فعالیت بعدی؟", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                            TextButton(onClick = { tab = Tab.ACTIVITY }) { Text("مشاهده فعالیت", color = Ink) }
                        }
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                                .background(Color(0xFFE8EDE4)).clickable { tab = Tab.ACTIVITY }.padding(18.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Box(Modifier.size(52.dp).clip(CircleShape).background(Lime), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.DirectionsRun, null, tint = Ink, modifier = Modifier.size(28.dp))
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("پیاده‌روی و دویدن", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                                    Text("مسیرت رو با GPS ثبت کن", fontSize = 12.sp, color = Muted)
                                }
                                Icon(Icons.Default.ArrowForward, null, tint = Ink)
                            }
                        }
                    }
                    Tab.ACTIVITY -> {
                        ActivityPanel(tracking, distance, seconds, onStart, onStop)
                        SectionTitle("آخرین رکورد", "فعالیت ذخیره‌شده")
                        DashboardStat("مسافت آخرین فعالیت", lastActivity, "کیلومتر", Icons.Default.Route, Modifier.fillMaxWidth())
                    }
                    Tab.FOOD -> FoodPanel(meal, { meal = it }, amount, { amount = it }, mealLog, { value ->
                        mealLog = mealLog + value
                        prefs.edit().putString("meals_today", mealLog.joinToString("|")).apply()
                    })
                    Tab.SLEEP -> {
                        SectionTitle("خواب شبانه", "مدت و کیفیت استراحتت رو ثبت کن")
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Ink).padding(22.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("SLEEP DURATION", color = Lime, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                                Text(sleep.toInt().toString() + " ساعت", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Black)
                                Slider(value = sleep, onValueChange = { sleep = it; prefs.edit().putFloat("sleep_hours", it).apply() }, valueRange = 0f..12f, steps = 23,
                                    colors = SliderDefaults.colors(thumbColor = Lime, activeTrackColor = Lime, inactiveTrackColor = Color.DarkGray))
                                Text("مدت خواب رو با کشیدن نوار تنظیم کن.", color = Color.LightGray, fontSize = 12.sp)
                            }
                        }
                        Text("کیفیت خواب", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink)
                        var quality by remember { mutableStateOf(prefs.getString("sleep_quality", "خوب") ?: "خوب") }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("ضعیف", "معمولی", "خوب", "عالی").forEach { q ->
                                FilterChip(selected = quality == q, onClick = { quality = q; prefs.edit().putString("sleep_quality", q).apply() }, label = { Text(q) })
                            }
                        }
                    }
                    Tab.PROFILE -> {
                        SectionTitle("پروفایل", "اطلاعات شخصی و داده‌ها")
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White).padding(18.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("وزن فعلی", fontSize = 14.sp, color = Muted)
                                OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text("کیلوگرم") }, modifier = Modifier.fillMaxWidth())
                                Button(onClick = { prefs.edit().putString("weight", weight).apply() }, modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Ink), shape = RoundedCornerShape(16.dp)) { Text("ذخیره تغییرات") }
                            }
                        }
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White).padding(18.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("پشتیبان اطلاعات", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink)
                                Text(if(imported) "فایل اطلاعات قبلی دریافت شده است." else "اطلاعات نسخه وب را با فایل JSON وارد کن.", color = Muted, fontSize = 12.sp)
                                OutlinedButton(onClick = onImport) { Icon(Icons.Default.FileUpload, null); Spacer(Modifier.width(8.dp)); Text("وارد کردن فایل پشتیبان") }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun HomeHero(tracking: Boolean, distance: Float, seconds: Long, onStart: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(236.dp).clip(RoundedCornerShape(30.dp))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF22282A), Color(0xFF101415))))
            .clickable(onClick = onStart).padding(22.dp)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 9.dp.toPx()
            drawCircle(color = Color(0xFF3D4543), radius = 70.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            drawArc(color = Lime, startAngle = -90f, sweepAngle = 118f, useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                topLeft = androidx.compose.ui.geometry.Offset(size.width - 164.dp.toPx(), 20.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(140.dp.toPx(), 140.dp.toPx()))
            drawCircle(color = Lime.copy(alpha = .08f), radius = 105.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width - 94.dp.toPx(), 90.dp.toPx()))
        }
        Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(.66f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("YOUR HEALTH. YOUR PACE.", color = Lime, fontSize = 9.sp, letterSpacing = 1.7.sp, fontWeight = FontWeight.Bold)
            Text(if(tracking) "در حال ثبت فعالیت" else "قوی‌تر از دیروز.", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black, lineHeight = 32.sp)
            Text(if(tracking) "%.2f km  •  %02d:%02d".format(distance / 1000f, seconds / 60, seconds % 60) else "هر حرکت کوچیک، یک قدم رو به جلوست.", color = Color(0xFFB9C0BC), fontSize = 12.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(Lime), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, null, tint = Ink)
                }
                Text(if(tracking) "مشاهده فعالیت" else "شروع فعالیت", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text("M", Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp), color = Lime.copy(alpha = .85f), fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun DashboardStat(title: String, value: String, unit: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(22.dp)).background(Color.White).padding(15.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFF0F3E9)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Ink, modifier = Modifier.size(21.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 11.sp, color = Muted)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(value, fontSize = 22.sp, fontWeight = FontWeight.Black, color = Ink)
                Text(unit, fontSize = 10.sp, color = Muted, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

@Composable
private fun DashboardMini(title: String, value: String, unit: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(Color.White).padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 12.sp, color = Muted)
            Icon(icon, null, tint = Muted, modifier = Modifier.size(18.dp))
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(value, fontSize = 27.sp, fontWeight = FontWeight.Black, color = Ink)
            Text(unit, fontSize = 10.sp, color = Muted, modifier = Modifier.padding(bottom = 4.dp))
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