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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

private val Ink = Color(0xFF17191B)
private val Lime = Color(0xFFD7FF00)
private val Paper = Color(0xFFF4F5F1)
private val Muted = Color(0xFF777D7B)

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("mfit_native", MODE_PRIVATE) }
    private var tracking by mutableStateOf(false)
    private var liveDistance by mutableFloatStateOf(0f)
    private var liveSeconds by mutableLongStateOf(0L)
    private var routePoints by mutableStateOf<List<Pair<Double, Double>>>(emptyList())
    private var activityHistory by mutableStateOf<List<String>>(emptyList())
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
        setContent { MFitApp(tracking, liveDistance, liveSeconds, lastActivity, imported, routePoints, activityHistory, 
            onStart = { requestTracking() }, onStop = { stopTracking() },
            onImport = { importLauncher.launch(arrayOf("application/json", "text/*")) }) }
    }
    private fun refreshTrack() {
        liveDistance = prefs.getFloat("live_distance", 0f)
        liveSeconds = prefs.getLong("live_seconds", 0L)
        routePoints = try { val a = JSONArray(prefs.getString("route_points", "[]")); (0 until a.length()).mapNotNull { i -> val p = a.optJSONArray(i); if (p != null && p.length() >= 2) p.getDouble(0) to p.getDouble(1) else null } } catch (_: Exception) { emptyList() }
        tracking = prefs.getBoolean("tracking_active", false)
        activityHistory = try { val a = JSONArray(prefs.getString("activity_history", "[]")); (0 until a.length()).mapNotNull { i -> a.optString(i).takeIf { it.isNotBlank() } } } catch (_: Exception) { emptyList() }
        lastActivity = prefs.getString("last_activity", "—") ?: "—"
    }
    private fun requestTracking() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) startTracking() else permissionLauncher.launch(missing.toTypedArray())
    }
    private fun startTracking() { prefs.edit().putFloat("live_distance", 0f).putLong("live_seconds", 0L).putString("route_points", "[]").putBoolean("tracking_active", true).apply(); refreshTrack(); ContextCompat.startForegroundService(this, Intent(this, TrackingService::class.java)); tracking = true }
    private fun stopTracking() { stopService(Intent(this, TrackingService::class.java)); tracking = false; prefs.edit().putBoolean("tracking_active", false).apply(); refreshTrack() }
    override fun onDestroy() { unregisterReceiver(receiver); super.onDestroy() }
}

private enum class Tab(val label: String) { HOME("خانه"), ACTIVITY("فعالیت"), FOOD("تغذیه"), SLEEP("خواب"), PROFILE("پروفایل") }

@Composable
private fun MFitApp(tracking: Boolean, distance: Float, seconds: Long, lastActivity: String, imported: Boolean, routePoints: List<Pair<Double, Double>>,
                    onStart: () -> Unit, onStop: () -> Unit, onImport: () -> Unit, activityHistory: List<String>) {
    var tab by remember { mutableStateOf(Tab.HOME) }
    var water by remember { mutableIntStateOf(0) }
    var meal by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("100") }
    var mealLog by remember { mutableStateOf(listOf<String>()) }
    var sleep by remember { mutableFloatStateOf(8f) }
    var weight by remember { mutableStateOf("96") }
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("mfit_native", Context.MODE_PRIVATE) }
    LaunchedEffect(Unit) {
        water = prefs.getInt("water_today", 0)
        val dayKey = java.time.LocalDate.now().toString()
        if (prefs.getString("meal_log_date", "") != dayKey) { prefs.edit().putString("meals_today", "").putString("meal_log_date", dayKey).apply() }
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
                        ActivityPanel(tracking, distance, seconds, routePoints, activityHistory, onStart, onStop)
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

@Composable private fun ActivityPanel(tracking: Boolean, distance: Float, seconds: Long, routePoints: List<Pair<Double, Double>>, activityHistory: List<String>, onStart: () -> Unit, onStop: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(3.0)
            controller.setCenter(GeoPoint(20.0, 0.0))
            onResume()
        }
    }
    DisposableEffect(mapView) { onDispose { mapView.onPause(); mapView.onDetach() } }
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxWidth().height(260.dp), update = { map ->
                map.overlays.removeAll { it is Polyline || it is Marker }
                if (routePoints.isNotEmpty()) {
                    val geo = routePoints.map { GeoPoint(it.first, it.second) }
                    val line = Polyline().apply {
                        setPoints(geo)
                        outlinePaint.color = android.graphics.Color.rgb(155, 190, 0)
                        outlinePaint.strokeWidth = 11f
                    }
                    map.overlays.add(line)
                    val pin = Marker(map).apply {
                        position = geo.last()
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = if (tracking) "موقعیت فعلی" else "پایان مسیر"
                    }
                    map.overlays.add(pin)
                    map.controller.setCenter(geo.last())
                    if (map.zoomLevelDouble < 14.0) map.controller.setZoom(16.0)
                }
                map.invalidate()
            })
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (routePoints.isEmpty()) "نقشه آماده ثبت مسیر" else "${routePoints.size} نقطه ثبت‌شده", fontSize = 12.sp, color = Muted)
                Text("© OpenStreetMap contributors", fontSize = 10.sp, color = Muted)
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Metric("مسافت", "%.2f".format(distance / 1000f), "کیلومتر", Modifier.weight(1f))
        Metric("زمان", "%02d:%02d".format(seconds / 60, seconds % 60), "دقیقه:ثانیه", Modifier.weight(1f))
    }
    if (activityHistory.isNotEmpty()) {
        SectionTitle("سابقه فعالیت‌ها", "آخرین فعالیت‌های ثبت‌شده با GPS")
        activityHistory.take(7).forEach { item ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).padding(13.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(item.substringBefore("|"), fontSize = 12.sp, color = Ink, fontWeight = FontWeight.SemiBold)
                Text(item.substringAfter("|", ""), fontSize = 11.sp, color = Muted)
            }
        }
    } else {
        Text("بعد از پایان اولین فعالیت، سابقه‌اش اینجا نمایش داده می‌شود.", color = Muted, fontSize = 11.sp)
    }
    Button(onClick = if(tracking) onStop else onStart, modifier = Modifier.fillMaxWidth().height(58.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if(tracking) Color(0xFFB3261E) else Lime, contentColor = Ink),
        shape = RoundedCornerShape(18.dp)) {
        Icon(if(tracking) Icons.Default.Stop else Icons.Default.PlayArrow, null)
        Spacer(Modifier.width(8.dp))
        Text(if(tracking) "پایان فعالیت" else "شروع ثبت فعالیت", fontWeight = FontWeight.Bold)
    }
}

private data class Food(val name: String, val kcal: Double, val protein: Double, val carbs: Double, val sugar: Double, val fat: Double, val fiber: Double, val portions: List<Pair<String, Double>> = listOf("۱۰۰ گرم" to 100.0))
private val foods = listOf(
    Food("تخم مرغ کامل",155.0,13.0,1.1,1.1,11.0,0.0),
    Food("سفیده تخم مرغ",52.0,10.9,0.7,0.7,0.2,0.0),
    Food("سیب خام با پوست",52.0,0.3,13.8,10.4,0.2,2.4),
    Food("موز خام",89.0,1.1,22.8,12.2,0.3,2.6),
    Food("ماست یونانی ساده کم چرب",73.0,10.0,3.9,3.6,2.0,0.0),
    Food("سینه مرغ پخته",165.0,31.0,0.0,0.0,3.6,0.0),
    Food("برنج سفید پخته",130.0,2.7,28.2,0.1,0.3,0.4),
    Food("سیب زمینی آبپز",87.0,1.9,20.1,0.9,0.1,1.8),
    Food("نان سنگک",260.0,9.0,52.0,2.0,1.5,4.0),
    Food("عدس پخته",116.0,9.0,20.1,1.8,0.4,7.9),
    Food("نخود پخته",164.0,8.9,27.4,4.8,2.6,7.6),
    Food("لوبیا چیتی پخته",143.0,9.0,26.2,0.3,0.7,9.0),
    Food("گردو",654.0,15.2,13.7,2.6,65.2,6.7),
    Food("بادام",579.0,21.2,21.6,4.4,49.9,12.5),
    Food("خیار خام",15.0,0.7,3.6,1.7,0.1,0.5),
    Food("گوجه فرنگی خام",18.0,0.9,3.9,2.6,0.2,1.2),
    Food("جو دوسر خشک",389.0,16.9,66.3,0.9,6.9,10.6),
    Food("شیر کم چرب",42.0,3.4,5.0,5.0,1.0,0.0),
    Food("پنیر فتا",264.0,14.2,4.1,4.1,21.3,0.0),
    Food("روغن زیتون",884.0,0.0,0.0,0.0,100.0,0.0),
    Food("نان بربری",275.0,9.0,56.0,2.5,1.2,2.7, listOf("یک کف دست" to 30.0,"یک چهارم نان" to 75.0)),
    Food("نان لواش",290.0,9.0,57.0,2.0,1.5,2.0, listOf("یک ورق" to 25.0,"دو ورق" to 50.0)),
    Food("برنج کته پخته",130.0,2.7,28.2,0.1,0.3,0.4, listOf("نصف لیوان" to 80.0,"یک لیوان" to 160.0,"یک کفگیر" to 150.0)),
    Food("قرمه سبزی",145.0,8.0,8.0,2.0,9.0,3.0, listOf("یک ملاقه" to 180.0,"یک بشقاب کوچک" to 250.0)),
    Food("خورش قیمه",155.0,8.0,13.0,2.0,8.0,2.5, listOf("یک ملاقه" to 180.0,"یک بشقاب کوچک" to 250.0)),
    Food("کباب کوبیده",265.0,18.0,2.0,0.5,21.0,0.0, listOf("یک سیخ متوسط" to 100.0,"نصف سیخ" to 50.0)),
    Food("جوجه کباب",165.0,29.0,1.0,0.5,4.0,0.0, listOf("یک سیخ" to 150.0,"نصف سیخ" to 75.0)),
    Food("عدس پلو پخته",155.0,5.5,29.0,1.0,2.0,3.5, listOf("یک کفگیر" to 150.0,"یک لیوان" to 180.0)),
    Food("لوبیا پلو پخته",165.0,5.0,27.0,2.0,4.0,3.0, listOf("یک کفگیر" to 150.0,"یک لیوان" to 180.0)),
    Food("ماکارونی پخته",158.0,5.8,30.9,0.6,0.9,1.8, listOf("یک لیوان" to 140.0,"یک بشقاب" to 250.0)),
    Food("آش رشته",95.0,4.5,14.0,1.5,2.5,3.5, listOf("یک کاسه متوسط" to 300.0,"نصف کاسه" to 150.0)),
    Food("کشک بادمجان",165.0,6.0,10.0,3.0,11.0,3.0, listOf("سه قاشق غذاخوری" to 90.0,"یک پیاله" to 180.0)),
    Food("میرزا قاسمی",105.0,4.0,8.0,4.0,6.0,3.0, listOf("سه قاشق غذاخوری" to 90.0,"یک پیاله" to 180.0)),
    Food("املت گوجه",115.0,6.0,4.0,2.5,8.0,1.0, listOf("یک بشقاب کوچک" to 200.0,"نصف بشقاب" to 100.0)),
    Food("خوراک لوبیا چیتی",125.0,7.5,20.0,2.0,1.5,7.0, listOf("یک کاسه" to 250.0,"نصف کاسه" to 125.0)),
    Food("سالاد شیرازی",25.0,1.0,5.0,3.0,0.2,1.5, listOf("یک کاسه" to 180.0,"نصف کاسه" to 90.0))
)

@Composable private fun FoodPanel(meal: String, onMeal: (String) -> Unit, amount: String, onAmount: (String) -> Unit,
                                  log: List<String>, onAdd: (String) -> Unit) {
    var selected by remember { mutableStateOf<Food?>(null) }
    var portion by remember { mutableStateOf<String?>(null) }
    val grams = (amount.replace(',', '.').toDoubleOrNull() ?: 0.0).coerceIn(0.0, 5000.0)
    val factor = grams / 100.0
    val matches = if (meal.isBlank()) foods.take(6) else foods.filter { it.name.contains(meal.trim(), ignoreCase = true) }.take(7)
    val kcal = selected?.let { it.kcal * factor } ?: 0.0
    val protein = selected?.let { it.protein * factor } ?: 0.0
    val carbs = selected?.let { it.carbs * factor } ?: 0.0
    val sugar = selected?.let { it.sugar * factor } ?: 0.0
    val fat = selected?.let { it.fat * factor } ?: 0.0
    val fiber = selected?.let { it.fiber * factor } ?: 0.0
    val todayKcal = log.sumOf { it.split(" • ").getOrNull(2)?.removeSuffix(" kcal")?.toDoubleOrNull() ?: 0.0 }
    val todayProtein = log.sumOf { it.split(" • ").getOrNull(3)?.removeSuffix("g P")?.toDoubleOrNull() ?: 0.0 }
    val todayCarbs = log.sumOf { it.split(" • ").getOrNull(4)?.removeSuffix("g C")?.toDoubleOrNull() ?: 0.0 }
    val todayFat = log.sumOf { it.split(" • ").getOrNull(5)?.removeSuffix("g F")?.toDoubleOrNull() ?: 0.0 }
    val todaySugar = log.sumOf { it.split(" • ").getOrNull(6)?.removeSuffix("g S")?.toDoubleOrNull() ?: 0.0 }
    val todayFiber = log.sumOf { it.split(" • ").getOrNull(7)?.removeSuffix("g Fib")?.toDoubleOrNull() ?: 0.0 }
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        SectionTitle("ثبت تغذیه", "مقدار را وارد کن؛ محاسبه خودکار انجام می‌شود")
        OutlinedTextField(value = meal, onValueChange = { onMeal(it); selected = foods.firstOrNull { f -> f.name.equals(it.trim(), true) } },
            label = { Text("جست‌وجوی غذا یا ماده غذایی") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            trailingIcon = { Icon(Icons.Default.Search, null) })
        Text(if (meal.isBlank()) "غذاهای پرکاربرد" else if(matches.isEmpty()) "موردی در فهرست آفلاین پیدا نشد" else "انتخاب نتیجه", color = Muted, fontSize = 12.sp)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            matches.forEach { food ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
                    .background(if(selected == food) Color(0xFFEAF2D4) else Color.White)
                    .clickable { selected = food; onMeal(food.name); portion = null }.padding(horizontal = 13.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(food.name, fontSize = 13.sp, fontWeight = if(selected == food) FontWeight.Bold else FontWeight.Medium, color = Ink)
                    Text("${food.kcal.toInt()} kcal / 100g", fontSize = 11.sp, color = Muted)
                }
            }
        }
        if (selected != null) {
            Text("اندازه‌های معمول این غذا", color = Muted, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                selected!!.portions.forEach { (label, g) ->
                    FilterChip(selected = portion == label, onClick = { portion = label; onAmount(g.toInt().toString()) }, label = { Text(label) })
                }
            }
        }
        OutlinedTextField(value = amount, onValueChange = { onAmount(it); portion = null }, label = { Text("مقدار مصرف‌شده (گرم)") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, suffix = { Text("g") })
        Card(colors = CardDefaults.cardColors(containerColor = Ink), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(selected?.name ?: "ابتدا یک غذا انتخاب کن", color = Color.LightGray, fontSize = 13.sp)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(if(selected == null) "—" else kcal.toInt().toString(), color = Lime, fontSize = 38.sp, fontWeight = FontWeight.Black)
                    Text("کیلوکالری", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(bottom = 7.dp))
                    Spacer(Modifier.weight(1f))
                    Text(if(selected == null) "بر اساس 100 گرم" else "${grams.toInt()} گرم", color = Color.LightGray, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
                }
                HorizontalDivider(color = Color.White.copy(alpha = .16f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    NutrientValue("پروتئین", protein, Color.White)
                    NutrientValue("کربوهیدرات", carbs, Color.White)
                    NutrientValue("چربی", fat, Color.White)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    NutrientValue("قند", sugar, Color.White)
                    NutrientValue("فیبر", fiber, Color.White)
                }
            }
        }
        Button(onClick = {
            selected?.let { food ->
                onAdd("${food.name} • ${grams.toInt()} g • ${kcal.toInt()} kcal • ${"%.1f".format(java.util.Locale.US, protein)}g P • ${"%.1f".format(java.util.Locale.US, carbs)}g C • ${"%.1f".format(java.util.Locale.US, fat)}g F • ${"%.1f".format(java.util.Locale.US, sugar)}g S • ${"%.1f".format(java.util.Locale.US, fiber)}g Fib")
            }
        }, enabled = selected != null && grams > 0.0, modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ink), shape = RoundedCornerShape(17.dp)) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(7.dp)); Text("ثبت در وعده امروز", color = Color.White)
        }
        SectionTitle("جمع ثبت‌شده امروز", "مجموع غذاهایی که در این روز وارد کرده‌ای")
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${todayKcal.toInt()} kcal", fontSize = 27.sp, fontWeight = FontWeight.Black, color = Ink)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    NutrientValue("پروتئین", todayProtein, Ink)
                    NutrientValue("کربوهیدرات", todayCarbs, Ink)
                    NutrientValue("چربی", todayFat, Ink)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    NutrientValue("قند", todaySugar, Ink)
                    NutrientValue("فیبر", todayFiber, Ink)
                }
            }
        }
        SectionTitle("موارد ثبت‌شده", "امروز")
        if(log.isEmpty()) Text("هنوز غذایی ثبت نشده.", color = Muted, fontSize = 12.sp)
        log.asReversed().forEach { entry ->
            Text(entry, modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp)).padding(12.dp), fontSize = 12.sp, color = Ink)
        }
        Text("مقادیر این فهرست، مرجع عمومی و تقریبی بر پایه ۱۰۰ گرم بخش خوراکی هستند. نوع پخت و برند می‌تواند مقدار واقعی را تغییر دهد.", color = Muted, fontSize = 10.sp)
    }
}

@Composable private fun NutrientValue(label: String, value: Double, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = if(color == Color.White) Color.LightGray else Muted, fontSize = 10.sp)
        Text("${"%.1f".format(java.util.Locale.US, value)} g", color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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