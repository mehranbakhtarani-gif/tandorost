package com.mfit.app

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.location.Location
import android.location.LocationManager
import android.location.LocationListener
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private val Ink = MFitPalette.Ink
private val Lime = MFitPalette.Lime
private val Paper = MFitPalette.Canvas
private val Muted = MFitPalette.Muted

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("mfit_native", MODE_PRIVATE) }
    private var tracking by mutableStateOf(false)
    private var sessionPaused by mutableStateOf(false)
    private var liveDistance by mutableFloatStateOf(0f)
    private var liveSeconds by mutableLongStateOf(0L)
    private var sessionStartedAt by mutableLongStateOf(0L)
    private var routePoints by mutableStateOf<List<Pair<Double, Double>>>(emptyList())
    private var currentLocation by mutableStateOf<Pair<Double, Double>?>(null)
    private var currentLocationListener: LocationListener? = null
    private var activityHistory by mutableStateOf<List<String>>(emptyList())
    private var lastActivity by mutableStateOf("—")
    private var imported by mutableStateOf(false)
    private var weatherPermissionGranted by mutableStateOf(false)
    private val weatherPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        weatherPermissionGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true) startTracking()
    }
    private val currentLocationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true) updateCurrentLocation()
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
        weatherPermissionGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val filter = IntentFilter("com.mfit.app.TRACK_UPDATE")
        if (Build.VERSION.SDK_INT >= 33) { registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED) } else { registerReceiver(receiver, filter) }
        setContent { MFitApp(tracking, sessionPaused, liveDistance, liveSeconds, sessionStartedAt, lastActivity, imported, routePoints, currentLocation, activityHistory,
            onStart = { requestTracking() }, onPause = { pauseTracking() }, onResume = { resumeTracking() }, onStop = { stopTracking() }, onRequestCurrentLocation = { requestCurrentLocation() },
            onImport = { importLauncher.launch(arrayOf("application/json", "text/*")) },
            weatherPermissionGranted = weatherPermissionGranted,
            onWeatherPermission = { weatherPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) }
    }
    private fun refreshTrack() {
        liveDistance = prefs.getFloat("live_distance", 0f)
        liveSeconds = prefs.getLong("live_seconds", 0L)
        sessionStartedAt = prefs.getLong("session_started_at", 0L)
        routePoints = try { val a = JSONArray(prefs.getString("route_points", "[]")); (0 until a.length()).mapNotNull { i -> val p = a.optJSONArray(i); if (p != null && p.length() >= 2) p.getDouble(0) to p.getDouble(1) else null } } catch (_: Exception) { emptyList() }
        tracking = prefs.getBoolean("tracking_active", false)
        sessionPaused = prefs.getBoolean("session_paused", false)
        activityHistory = try { val a = JSONArray(prefs.getString("activity_history", "[]")); (0 until a.length()).mapNotNull { i -> a.optString(i).takeIf { it.isNotBlank() } } } catch (_: Exception) { emptyList() }
        lastActivity = prefs.getString("last_activity", "—") ?: "—"
    }
    private fun requestTracking() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) startTracking() else permissionLauncher.launch(missing.toTypedArray())
    }
    private fun requestCurrentLocation() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            currentLocationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }
        updateCurrentLocation()
    }
    @Suppress("MissingPermission")
    private fun updateCurrentLocation() {
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        try {
            val lastKnown = manager.getProviders(true).mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }.maxByOrNull { it.time }
            if (lastKnown != null) currentLocation = lastKnown.latitude to lastKnown.longitude
            currentLocationListener?.let { runCatching { manager.removeUpdates(it) } }
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    currentLocation = location.latitude to location.longitude
                    runCatching { manager.removeUpdates(this) }
                    if (currentLocationListener === this) currentLocationListener = null
                }
                @Deprecated("Deprecated in Android")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
            }
            currentLocationListener = listener
            val provider = when {
                manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            }
            if (provider != null) manager.requestLocationUpdates(provider, 1000L, 0f, listener, mainLooper)
        } catch (_: SecurityException) {
            currentLocation = null
        } catch (_: Exception) {
            // Keep a previously available last-known location visible if a fresh fix is unavailable.
        }
    }
    private fun startTracking() {
        val started = System.currentTimeMillis()
        prefs.edit().putFloat("live_distance", 0f).putLong("live_seconds", 0L)
            .putLong("session_started_at", started).putString("route_points", "[]")
            .putBoolean("tracking_active", true).putBoolean("session_paused", false).apply()
        refreshTrack()
        ContextCompat.startForegroundService(this, Intent(this, TrackingService::class.java))
        tracking = true
        sessionPaused = false
    }
    private fun pauseTracking() {
        if (!tracking) return
        ContextCompat.startForegroundService(this, Intent(this, TrackingService::class.java).setAction("PAUSE"))
        tracking = false
        sessionPaused = true
        prefs.edit().putBoolean("session_paused", true).putBoolean("tracking_active", false).apply()
        refreshTrack()
    }
    private fun resumeTracking() {
        if (!sessionPaused) return
        val now = System.currentTimeMillis()
        val elapsed = prefs.getLong("live_seconds", 0L)
        prefs.edit().putLong("session_started_at", now - elapsed * 1000L)
            .putBoolean("tracking_active", true).putBoolean("session_paused", false).apply()
        ContextCompat.startForegroundService(this, Intent(this, TrackingService::class.java))
        tracking = true
        sessionPaused = false
        refreshTrack()
    }
    private fun stopTracking() {
        if (tracking) {
            stopService(Intent(this, TrackingService::class.java))
        } else if (sessionPaused) {
            ContextCompat.startForegroundService(this, Intent(this, TrackingService::class.java).setAction("FINISH"))
        }
        tracking = false
        sessionPaused = false
        prefs.edit().putBoolean("tracking_active", false).putBoolean("session_paused", false).apply()
        refreshTrack()
    }
    override fun onDestroy() {
        currentLocationListener?.let { listener -> runCatching { (getSystemService(LOCATION_SERVICE) as LocationManager).removeUpdates(listener) } }
        currentLocationListener = null
        unregisterReceiver(receiver)
        super.onDestroy()
    }
}


private data class CurrentWeather(
    val temperature: Double,
    val feelsLike: Double,
    val humidity: Int,
    val wind: Double,
    val code: Int
)

private suspend fun loadCurrentWeather(context: Context): CurrentWeather {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val location = manager.getProviders(true).mapNotNull { provider ->
        try { manager.getLastKnownLocation(provider) } catch (_: SecurityException) { null }
    }.maxByOrNull { it.time } ?: throw IllegalStateException("موقعیت فعلی پیدا نشد؛ GPS یا سرویس مکان‌یابی گوشی را روشن کن.")
    val endpoint = "https://api.open-meteo.com/v1/forecast?latitude=${location.latitude}&longitude=${location.longitude}&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m&timezone=auto"
    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8000
        readTimeout = 8000
        requestMethod = "GET"
    }
    return try {
        val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        val current = root.getJSONObject("current")
        CurrentWeather(
            current.getDouble("temperature_2m"),
            current.getDouble("apparent_temperature"),
            current.getInt("relative_humidity_2m"),
            current.getDouble("wind_speed_10m"),
            current.getInt("weather_code")
        )
    } finally {
        connection.disconnect()
    }
}

private fun weatherDescription(code: Int): String = when (code) {
    0 -> "آسمان صاف"
    1, 2 -> "کمی تا نیمه‌ابری"
    3 -> "ابری"
    45, 48 -> "مه‌آلود"
    51, 53, 55, 56, 57 -> "نم‌نم باران"
    61, 63, 65, 66, 67 -> "بارانی"
    71, 73, 75, 77 -> "بارش برف"
    80, 81, 82 -> "رگبار باران"
    85, 86 -> "رگبار برف"
    95, 96, 99 -> "رعدوبرق"
    else -> "شرایط جوی"
}

@Composable
private fun WeatherPanel(permissionGranted: Boolean, onRequestPermission: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var weather by remember { mutableStateOf<CurrentWeather?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(permissionGranted, refreshKey) {
        if (permissionGranted) {
            loading = true
            error = null
            val result = runCatching { withContext(Dispatchers.IO) { loadCurrentWeather(context) } }
            weather = result.getOrNull()
            error = result.exceptionOrNull()?.message
            loading = false
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            Modifier.fillMaxWidth()
                .glassSurface(RoundedCornerShape(22.dp), Color(0xFF324B4B))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("آب‌وهوای زنده", color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("شرایط بیرون برای برنامه‌ریزی فعالیت", color = Muted, fontSize = 11.sp)
                }
                Icon(Icons.Default.WbSunny, contentDescription = null, tint = MFitPalette.Eating, modifier = Modifier.size(28.dp))
            }
            if (weather != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${weather!!.temperature.toInt()}°", color = Ink, fontSize = 42.sp, fontWeight = FontWeight.Black)
                        Text(weatherDescription(weather!!.code), color = Muted, fontSize = 13.sp)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("حس‌شده ${weather!!.feelsLike.toInt()}°", color = Ink, fontSize = 12.sp)
                        Text("رطوبت ${weather!!.humidity}٪", color = Muted, fontSize = 11.sp)
                        Text("باد ${weather!!.wind.toInt()} km/h", color = Muted, fontSize = 11.sp)
                    }
                }
            } else {
                Text(
                    when {
                        loading -> "در حال دریافت وضعیت آب‌وهوا…"
                        !permissionGranted -> "برای نمایش هوای موقعیت فعلی، دسترسی مکان لازم است."
                        else -> error ?: "اطلاعات هواشناسی در دسترس نیست."
                    },
                    color = Muted, fontSize = 12.sp
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = { if (permissionGranted) refreshKey++ else onRequestPermission() },
                    enabled = !loading
                ) {
                    Icon(if (permissionGranted) Icons.Default.Refresh else Icons.Default.MyLocation, null, tint = Color(0xFFD9F28A))
                    Spacer(Modifier.width(6.dp))
                    Text(if (permissionGranted) "به‌روزرسانی" else "فعال‌کردن موقعیت", color = Lime)
                }
            }
        }
    }
}

private data class DailyWellnessSnapshot(
    val date: String, val water: Int, val sleep: Float, val meals: Int,
    val workoutCalories: Int, val habits: Int
)

private enum class Tab(val label: String) { HOME("خانه"), ACTIVITY("فعالیت"), FOOD("تغذیه"), ROUTINE("روتین"), PROFILE("پروفایل") }

@Composable
private fun MFitApp(tracking: Boolean, sessionPaused: Boolean, distance: Float, seconds: Long, sessionStartedAt: Long, lastActivity: String, imported: Boolean, routePoints: List<Pair<Double, Double>>, currentLocation: Pair<Double, Double>?, activityHistory: List<String>,
                    onStart: () -> Unit, onPause: () -> Unit, onResume: () -> Unit, onStop: () -> Unit, onRequestCurrentLocation: () -> Unit, onImport: () -> Unit,
                    weatherPermissionGranted: Boolean, onWeatherPermission: () -> Unit) {
    var tab by remember { mutableStateOf(Tab.HOME) }
    var displaySeconds by remember { mutableLongStateOf(seconds) }
    LaunchedEffect(tracking, sessionStartedAt) {
        if (tracking && sessionStartedAt > 0L) {
            while (true) {
                displaySeconds = ((System.currentTimeMillis() - sessionStartedAt) / 1000L).coerceAtLeast(0L)
                delay(1000L)
            }
        } else displaySeconds = seconds
    }
    val shownSeconds = if (tracking) displaySeconds else seconds
    LaunchedEffect(tab) { if (tab == Tab.ACTIVITY) onRequestCurrentLocation() }
    var water by remember { mutableIntStateOf(0) }
    var meal by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("100") }
    var mealLog by remember { mutableStateOf(listOf<String>()) }
    var sleep by remember { mutableFloatStateOf(8f) }
    var weight by remember { mutableStateOf("96") }
    var waist by remember { mutableStateOf("106") }
    var heightCm by remember { mutableStateOf("182") }
    var workoutLog by remember { mutableStateOf(listOf<String>()) }
    var habitsDone by remember { mutableStateOf(listOf(false, false, false, false)) }
    var weeklySnapshots by remember { mutableStateOf(listOf<DailyWellnessSnapshot>()) }
    var coachDataReady by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("mfit_native", Context.MODE_PRIVATE) }
    var profilePhotoUri by remember { mutableStateOf(prefs.getString("profile_photo_uri", null)) }
    var profilePhotoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val profilePhotoPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            catch (_: SecurityException) { }
            profilePhotoUri = uri.toString()
            prefs.edit().putString("profile_photo_uri", profilePhotoUri).apply()
        }
    }
    LaunchedEffect(profilePhotoUri) {
        profilePhotoBitmap = if (profilePhotoUri.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(profilePhotoUri))?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }
    }
    // One state/persistence path is shared by Home and Routine water controls.
    val setWaterCount: (Int) -> Unit = { next ->
        val safeCount = next.coerceAtLeast(0)
        water = safeCount
        prefs.edit().putInt("water_today", safeCount).apply()
    }
    val workoutCaloriesToday = workoutLog.sumOf { it.substringAfterLast("|", "0").toIntOrNull() ?: 0 }
    LaunchedEffect(activityHistory.firstOrNull()) {
        val today = java.time.LocalDate.now().toString()
        if (prefs.getString("workout_log_date", "") != today) {
            prefs.edit().putString("workout_log_date", today).putString("workout_log_today", "").apply()
        }
        workoutLog = prefs.getString("workout_log_today", "")?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
    }
    LaunchedEffect(Unit) {
        val routineDay = java.time.LocalDate.now().toString()
        if (prefs.getString("routine_day", "") != routineDay) {
            prefs.edit().putString("routine_day", routineDay).putInt("water_today", 0).putString("habits_done_today", "0000").apply()
        }
        water = prefs.getInt("water_today", 0)
        habitsDone = prefs.getString("habits_done_today", "0000")!!.map { it == '1' }.let { if (it.size == 4) it else listOf(false, false, false, false) }
        val dayKey = java.time.LocalDate.now().toString()
        if (prefs.getString("meal_log_date", "") != dayKey) { prefs.edit().putString("meals_today", "").putString("meal_log_date", dayKey).apply() }
        mealLog = prefs.getString("meals_today", "")?.split("|")?.filter { it.isNotBlank() } ?: emptyList()
        weight = prefs.getString("weight", "96") ?: "96"
        waist = prefs.getString("waist_cm", "106") ?: "106"
        heightCm = prefs.getString("height_cm", "182") ?: "182"
        sleep = prefs.getFloat("sleep_hours", 8f)
        val today = java.time.LocalDate.now().toString()
        if (prefs.getString("workout_log_date", "") != today) prefs.edit().putString("workout_log_date", today).putString("workout_log_today", "").apply()
        workoutLog = prefs.getString("workout_log_today", "")?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
        val saved = try { JSONArray(prefs.getString("weekly_snapshots", "[]")) } catch (_: Exception) { JSONArray() }
        weeklySnapshots = (0 until saved.length()).mapNotNull { i ->
            saved.optJSONObject(i)?.let { o ->
                DailyWellnessSnapshot(o.optString("date"), o.optInt("water"), o.optDouble("sleep", 0.0).toFloat(), o.optInt("meals"), o.optInt("workoutCalories"), o.optInt("habits"))
            }
        }.takeLast(7)
        coachDataReady = true
    }
    LaunchedEffect(coachDataReady, water, sleep, mealLog, workoutLog, habitsDone) {
        if (coachDataReady) {
            val today = java.time.LocalDate.now().toString()
            val snapshot = DailyWellnessSnapshot(today, water, sleep, mealLog.size, workoutCaloriesToday, habitsDone.count { it })
            weeklySnapshots = (weeklySnapshots.filterNot { it.date == today } + snapshot).takeLast(7)
            val json = JSONArray()
            weeklySnapshots.forEach { item ->
                json.put(JSONObject().put("date", item.date).put("water", item.water).put("sleep", item.sleep.toDouble())
                    .put("meals", item.meals).put("workoutCalories", item.workoutCalories).put("habits", item.habits))
            }
            prefs.edit().putString("weekly_snapshots", json.toString()).apply()
        }
    }
    MFitTheme {
        Scaffold(
            modifier = Modifier.background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    when (tab) {
                        Tab.HOME -> listOf(Color(0xFFE9EEE7), Color(0xFFE9EEE7), Color(0xFFE9EEE7))
                        Tab.ACTIVITY -> listOf(Color(0xFFE9EEE7), Color(0xFFE9EEE7), Color(0xFFE9EEE7))
                        Tab.FOOD -> listOf(Color(0xFF0D1819), Color(0xFF211B2B), Color(0xFF342A46))
                        Tab.ROUTINE -> listOf(Color(0xFF0D1819), Color(0xFF18302C), Color(0xFF24423A))
                        Tab.PROFILE -> listOf(Color(0xFF0D1819), Color(0xFF1B2730), Color(0xFF26363C))
                    }
                )
            ),
            containerColor = Color.Transparent,
            bottomBar = { MFitBottomBar(tab) { tab = it } }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                if (tab == Tab.HOME || tab == Tab.ACTIVITY) {
                    Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF245C3B)), contentAlignment = Alignment.Center) {
                            Text("m", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text("MFit", color = Color(0xFF234D35), fontSize = 20.sp, fontWeight = FontWeight.Black)
                            Text("حرکت، سلامتی، پیشرفت", color = Color(0xFF718174), fontSize = 10.sp)
                        }
                        Box(Modifier.size(38.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, null, tint = Color(0xFF315A40))
                        }
                    }
                    if (tab == Tab.HOME) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("خانه", color = Color(0xFF203E2C), fontSize = 27.sp, fontWeight = FontWeight.Black)
                            Text("روزت را با انرژی بساز", color = Color(0xFF728174), fontSize = 11.sp)
                        }
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 28.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("M F I T  /  WELLNESS", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp, color = Lime)
                            if (tab == Tab.HOME) Text(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, dd MMM", java.util.Locale.ENGLISH)).uppercase(), fontSize = 9.sp, letterSpacing = 1.6.sp, color = Muted)
                            Text(when(tab) {
                                Tab.HOME -> "روزت را زندگی کن."
                                Tab.ACTIVITY -> "وقت حرکته."
                                Tab.FOOD -> "سوخت بدنت."
                                Tab.ROUTINE -> "عادت‌های کوچک، نتیجه‌های بزرگ."
                                Tab.PROFILE -> "مسیر پیشرفت تو."
                            }, fontSize = if (tab == Tab.HOME) 29.sp else 25.sp, fontWeight = FontWeight.ExtraBold, color = Ink, lineHeight = 36.sp)
                        }
                        Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF31594D).copy(alpha = .34f)).border(1.dp, Color.White.copy(alpha = .24f), CircleShape), contentAlignment = Alignment.Center) {
                            Text("M", fontWeight = FontWeight.Black, fontSize = 21.sp, color = Ink)
                        }
                    }
                }
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        if (targetState.ordinal >= initialState.ordinal) {
                            (slideInHorizontally(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)) { it / 10 } + fadeIn(tween(300))) togetherWith
                                (slideOutHorizontally(animationSpec = tween(170)) { -it / 10 } + fadeOut(tween(130)))
                        } else {
                            (slideInHorizontally(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)) { -it / 10 } + fadeIn(tween(300))) togetherWith
                                (slideOutHorizontally(animationSpec = tween(170)) { it / 10 } + fadeOut(tween(130)))
                        }.using(SizeTransform(clip = false))
                    },
                    label = "mfit-tab-transition"
                ) { page ->
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    when(page) {
                    Tab.HOME -> {
                        FastingPanel(prefs)
                        val todayKcal = mealLog.sumOf { it.split(" • ").getOrNull(2)?.removeSuffix(" kcal")?.toDoubleOrNull() ?: 0.0 }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Stretch) {
                            Column(Modifier.weight(1.7f).clip(RoundedCornerShape(topStart = 27.dp, topEnd = 27.dp, bottomStart = 27.dp, bottomEnd = 8.dp))
                                .background(Lime).clickable { tab = Tab.ACTIVITY }.padding(17.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Icon(Icons.Default.DirectionsRun, null, tint = Color(0xFF17221C), modifier = Modifier.size(25.dp))
                                Spacer(Modifier.height(4.dp))
                                Text("حرکت امروز", color = Color(0xFF17221C), fontSize = 17.sp, fontWeight = FontWeight.Black)
                                Text("شروع فعالیت و ثبت مسیر", color = Color(0xFF43572A), fontSize = 10.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) { Text("شروع کن", color = Color(0xFF17221C), fontSize = 11.sp, fontWeight = FontWeight.Bold); Icon(Icons.Default.ArrowBack, null, tint = Color(0xFF17221C), modifier = Modifier.size(15.dp)) }
                            }
                            Column(Modifier.weight(1f).clip(RoundedCornerShape(topStart = 27.dp, topEnd = 8.dp, bottomStart = 27.dp, bottomEnd = 27.dp))
                                .background(Color.White).clickable { tab = Tab.FOOD }.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.LocalFireDepartment, null, tint = Color(0xFFEA914A), modifier = Modifier.size(23.dp))
                                Spacer(Modifier.height(4.dp))
                                Text("دریافت انرژی", color = Color(0xFF17221C), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(todayKcal.toInt().toString(), color = Color(0xFF254D37), fontSize = 24.sp, fontWeight = FontWeight.Black)
                                Text("کیلوکالری", color = Color(0xFF78847B), fontSize = 9.sp)
                            }
                        }
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(23.dp)).background(Color.White).clickable { tab = Tab.PROFILE }.padding(17.dp),
                            verticalArrangement = Arrangement.spacedBy(13.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("مسیر پیشرفت", color = Color(0xFF254D37), fontSize = 17.sp, fontWeight = FontWeight.Black)
                                    Text("بر اساس داده‌های ثبت‌شده", color = Color(0xFF78847B), fontSize = 10.sp)
                                }
                                TextButton(onClick = { tab = Tab.PROFILE }) { Text("جزئیات", color = Color(0xFF254D37)) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                HomeProgressValue("وزن", weight, "kg", Modifier.weight(1f))
                                HomeProgressValue("دور کمر", waist, "cm", Modifier.weight(1f))
                                HomeProgressValue("کالری تمرین", workoutCaloriesToday.toString(), "kcal", Modifier.weight(1f))
                            }
                            Text("روندها فقط با ثبت اندازه‌گیری واقعی نمایش داده می‌شوند.", color = Color(0xFF78847B), fontSize = 9.sp)
                        }
                        WeatherPanel(weatherPermissionGranted, onWeatherPermission)
                        SmartCoachPanel(
                            water = water, mealCount = mealLog.size, sleepHours = sleep,
                            workoutCalories = workoutCaloriesToday, habitsCompleted = habitsDone.count { it },
                            weeklyDays = weeklySnapshots.size, weeklyWorkoutCalories = weeklySnapshots.sumOf { it.workoutCalories },
                            weeklyAverageWater = if (weeklySnapshots.isEmpty()) 0 else weeklySnapshots.sumOf { it.water } / weeklySnapshots.size,
                            onFood = { tab = Tab.FOOD }, onRoutine = { tab = Tab.ROUTINE }
                        )
                        VoiceCoachPanel(
                            message = when {
                                sleep < 6f -> "امروز تمرین سبک را انتخاب کن و خواب را در اولویت بگذار."
                                water < 4 -> "یک لیوان آب بنوش و در طول روز آب‌رسانی را ادامه بده."
                                workoutCaloriesToday == 0 -> "برای شروع، یک پیاده‌روی کوتاه و راحت انتخاب خوبی است."
                                else -> "آفرین؛ استمرار را حفظ کن و برای ریکاوری هم زمان بگذار."
                            }, onActivity = { tab = Tab.ACTIVITY }
                        )
                        WeeklyAnalyticsPanel(weeklySnapshots)
                        AchievementPanel(weeklySnapshots, workoutLog.size, habitsDone.count { it })
                    }
                    Tab.ACTIVITY -> {
                        ActivityPanel(tracking, sessionPaused, distance, shownSeconds, routePoints, currentLocation, activityHistory,
                            onStart, onPause, onResume, onStop, onRequestCurrentLocation)
                        SectionTitle("آخرین رکورد", "فعالیت ذخیره‌شده")
                        DashboardStat("مسافت آخرین فعالیت", lastActivity, "کیلومتر", Icons.Default.Route, Modifier.fillMaxWidth())
                    }
                    Tab.FOOD -> FoodPanel(meal, { meal = it }, amount, { amount = it }, mealLog, { value ->
                        mealLog = mealLog + value
                        prefs.edit().putString("meals_today", mealLog.joinToString("|")).apply()
                    })
                    Tab.ROUTINE -> {
                        RoutinePanel(sleep, { sleep = it; prefs.edit().putFloat("sleep_hours", it).apply() },
                            weight.replace(",", ".").toDoubleOrNull()?.coerceIn(30.0, 250.0) ?: 70.0,
                            workoutLog, { record -> workoutLog = workoutLog + record; prefs.edit().putString("workout_log_today", workoutLog.joinToString("\n")).apply() },
                            water, setWaterCount,
                            habitsDone, { index, checked -> habitsDone = habitsDone.toMutableList().also { it[index] = checked }; prefs.edit().putString("habits_done_today", habitsDone.joinToString("") { if (it) "1" else "0" }).apply() })
                    }
                    Tab.PROFILE -> {
                        SectionTitle("پروفایل", "اطلاعات شخصی و داده‌ها")
                        Box(Modifier.fillMaxWidth().glassSurface(RoundedCornerShape(24.dp)).padding(18.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                if (profilePhotoBitmap != null) {
                                    androidx.compose.foundation.Image(
                                        bitmap = profilePhotoBitmap!!.asImageBitmap(),
                                        contentDescription = "عکس پروفایل",
                                        modifier = Modifier.size(76.dp).clip(CircleShape)
                                    )
                                } else {
                                    Box(Modifier.size(76.dp).clip(CircleShape).background(Color(0xFF31594D).copy(alpha = .28f)).border(1.dp, Color.White.copy(alpha = .28f), CircleShape), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Person, contentDescription = null, tint = Ink, modifier = Modifier.size(36.dp))
                                    }
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text("عکس پروفایل", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                                    Text("یک تصویر از گالری انتخاب کن؛ روی همین دستگاه ذخیره می‌شود.", fontSize = 11.sp, color = Muted)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedButton(onClick = { profilePhotoPicker.launch(arrayOf("image/*")) }) {
                                            Icon(Icons.Default.AddAPhoto, null)
                                            Spacer(Modifier.width(6.dp))
                                            Text(if (profilePhotoBitmap == null) "افزودن عکس" else "تغییر عکس")
                                        }
                                        if (profilePhotoUri != null) {
                                            TextButton(onClick = {
                                                profilePhotoUri = null
                                                profilePhotoBitmap = null
                                                prefs.edit().remove("profile_photo_uri").apply()
                                            }) { Text("حذف عکس", color = Color(0xFFB34D4D)) }
                                        }
                                    }
                                }
                            }
                        }
                        Box(Modifier.fillMaxWidth().glassSurface(RoundedCornerShape(24.dp)).padding(18.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("اندازه‌های بدن", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink)
                                OutlinedTextField(value = weight, onValueChange = { weight = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(6) }, label = { Text("وزن (کیلوگرم)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                OutlinedTextField(value = waist, onValueChange = { waist = it.filter(Char::isDigit).take(3) }, label = { Text("دور کمر (سانتی‌متر)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                OutlinedTextField(value = heightCm, onValueChange = { heightCm = it.filter(Char::isDigit).take(3) }, label = { Text("قد (سانتی‌متر)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                val heightM = (heightCm.toDoubleOrNull() ?: 0.0) / 100.0
                                val bmi = if (heightM > 0.5) (weight.replace(",", ".").toDoubleOrNull() ?: 0.0) / (heightM * heightM) else 0.0
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF536A6A).copy(alpha = .28f)).padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column { Text("شاخص توده بدنی تخمینی", fontSize = 11.sp, color = Muted); Text(if (bmi > 0) "%.1f".format(java.util.Locale.US, bmi) else "—", fontSize = 25.sp, fontWeight = FontWeight.Black, color = Ink) }
                                    Text("بر اساس قد ثبت‌شده", fontSize = 10.sp, color = Muted)
                                }
                                Button(onClick = { prefs.edit().putString("weight", weight).putString("waist_cm", waist).putString("height_cm", heightCm).apply() }, modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Ink), shape = RoundedCornerShape(16.dp)) { Text("ذخیره اندازه‌ها") }
                            }
                        }
                        Box(Modifier.fillMaxWidth().glassSurface(RoundedCornerShape(24.dp)).padding(18.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("پشتیبان اطلاعات", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink)
                                Text(if(imported) "فایل اطلاعات قبلی دریافت شده است." else "اطلاعات نسخه وب را با فایل JSON وارد کن.", color = Muted, fontSize = 12.sp)
                                OutlinedButton(onClick = onImport) { Icon(Icons.Default.FileUpload, null); Spacer(Modifier.width(8.dp)); Text("وارد کردن فایل پشتیبان") }
                            }
                        }
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
private fun MFitBottomBar(selectedTab: Tab, onSelect: (Tab) -> Unit) {
    val lightDock = selectedTab == Tab.HOME || selectedTab == Tab.ACTIVITY
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
            .then(if (lightDock) Modifier.clip(RoundedCornerShape(28.dp)).background(Color.White) else Modifier.glassSurface(RoundedCornerShape(28.dp), Color(0xFFEAF5F0))),
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        shadowElevation = 0.dp,
        border = null
    ) {
        Row(
            Modifier.fillMaxWidth().height(70.dp).padding(horizontal = 5.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Tab.values().forEach { item ->
                val selected = selectedTab == item
                val iconSize by animateDpAsState(if (selected) 23.dp else 19.dp, spring(stiffness = Spring.StiffnessMedium), label = "nav-icon-size")
                val iconScale by animateFloatAsState(if (selected) 1.08f else 1f, spring(stiffness = Spring.StiffnessLow), label = "nav-icon-scale")
                val indicatorColor by animateColorAsState(if (selected) (if (lightDock) Color(0xFFDDEBDD) else Lime) else Color.Transparent, tween(220), label = "nav-indicator")
                val labelAlpha by animateFloatAsState(if (selected) 1f else .72f, tween(180), label = "nav-label-alpha")
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(19.dp))
                        .clickable { onSelect(item) }.padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        Modifier.size(if (selected) 39.dp else 34.dp).clip(CircleShape)
                            .background(indicatorColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            when (item) {
                                Tab.HOME -> Icons.Default.Home
                                Tab.ACTIVITY -> Icons.Default.DirectionsRun
                                Tab.FOOD -> Icons.Default.Restaurant
                                Tab.ROUTINE -> Icons.Default.FitnessCenter
                                Tab.PROFILE -> Icons.Default.Person
                            },
                            contentDescription = item.label,
                            tint = if (lightDock) (if (selected) Color(0xFF367A45) else Color(0xFF8A968A)) else if (selected) MFitPalette.Canvas else Muted,
                            modifier = Modifier.size(iconSize).graphicsLayer { scaleX = iconScale; scaleY = iconScale }
                        )
                    }
                    Text(
                        item.label,
                        fontSize = 9.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (lightDock) (if (selected) Color(0xFF367A45) else Color(0xFF849084)) else if (selected) Lime else Muted,
                        modifier = Modifier.graphicsLayer { alpha = labelAlpha },
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun FastingPanel(prefs: android.content.SharedPreferences) {
    var startedAt by remember { mutableLongStateOf(prefs.getLong("fasting_started_at", 0L)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) {
        if (startedAt > 0L) while (true) { now = System.currentTimeMillis(); delay(1000L) }
    }
    val elapsed = if (startedAt > 0L) ((now - startedAt) / 1000L).coerceAtLeast(0L) else 0L
    val hours = elapsed / 3600L
    val minutes = (elapsed % 3600L) / 60L
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(29.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF315F42), Color(0xFF173D2B), Color(0xFF10291E))))
        .padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("MFIT · FASTING", color = Color(0xFFB9EF45), fontSize = 9.sp, letterSpacing = 1.7.sp, fontWeight = FontWeight.Bold)
            Text("روزه‌داری", color = Color.White.copy(alpha = .8f), fontSize = 10.sp)
        }
        Text("روزه‌داری آگاهانه", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Text(if (startedAt > 0L) "زمان سپری‌شده از شروع بازه" else "بازه‌ی تغذیه و استراحتت را با آگاهی مدیریت کن.", color = Color.White.copy(alpha = .75f), fontSize = 11.sp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (startedAt > 0L) {
                Text("%02d:%02d".format(hours, minutes), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                Button(onClick = { startedAt = 0L; prefs.edit().remove("fasting_started_at").putLong("last_fasting_seconds", elapsed).apply() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB9EF45), contentColor = Color(0xFF17221C))) { Text("پایان بازه") }
            } else {
                Text("هنوز بازه‌ای ثبت نشده", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Button(onClick = { startedAt = System.currentTimeMillis(); now = startedAt; prefs.edit().putLong("fasting_started_at", startedAt).apply() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB9EF45), contentColor = Color(0xFF17221C))) { Text("شروع فستینگ") }
            }
        }
    }
}

@Composable
private fun HomeProgressValue(title: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = Color(0xFF78847B), fontSize = 9.sp)
        Text(value, color = Color(0xFF254D37), fontSize = 20.sp, fontWeight = FontWeight.Black)
        Text(unit, color = Color(0xFF78847B), fontSize = 8.sp)
    }
}

@Composable
private fun HomeHero(tracking: Boolean, distance: Float, seconds: Long, onStart: () -> Unit) {
    val ringSweep by animateFloatAsState(if (tracking) 255f else 214f, spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy), label = "hero-ring-sweep")
    val glowPulse = rememberInfiniteTransition(label = "hero-glow").animateFloat(
        initialValue = .035f, targetValue = .11f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "hero-glow-pulse"
    )
    Box(
        Modifier.fillMaxWidth().height(292.dp).glassSurface(RoundedCornerShape(34.dp), Color(0xFF324B4B))
            .clickable(onClick = onStart).padding(22.dp)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val centerX = size.width - 94.dp.toPx()
            val centerY = size.height * .51f
            val outer = 146.dp.toPx()
            val middle = 116.dp.toPx()
            val inner = 86.dp.toPx()
            drawCircle(Ink.copy(alpha = .12f), outer / 2, androidx.compose.ui.geometry.Offset(centerX, centerY),
                style = androidx.compose.ui.graphics.drawscope.Stroke(10.dp.toPx()))
            drawArc(
                color = MFitPalette.Movement, startAngle = -90f, sweepAngle = ringSweep,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(centerX - outer / 2, centerY - outer / 2),
                size = androidx.compose.ui.geometry.Size(outer, outer),
                style = androidx.compose.ui.graphics.drawscope.Stroke(10.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
            drawCircle(Ink.copy(alpha = .12f), middle / 2, androidx.compose.ui.geometry.Offset(centerX, centerY),
                style = androidx.compose.ui.graphics.drawscope.Stroke(8.dp.toPx()))
            drawArc(
                color = MFitPalette.Water, startAngle = -90f, sweepAngle = 172f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(centerX - middle / 2, centerY - middle / 2),
                size = androidx.compose.ui.geometry.Size(middle, middle),
                style = androidx.compose.ui.graphics.drawscope.Stroke(8.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
            drawCircle(Ink.copy(alpha = .12f), inner / 2, androidx.compose.ui.geometry.Offset(centerX, centerY),
                style = androidx.compose.ui.graphics.drawscope.Stroke(7.dp.toPx()))
            drawArc(
                color = MFitPalette.Eating, startAngle = -90f, sweepAngle = 112f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(centerX - inner / 2, centerY - inner / 2),
                size = androidx.compose.ui.geometry.Size(inner, inner),
                style = androidx.compose.ui.graphics.drawscope.Stroke(7.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
            drawCircle(Lime.copy(alpha = glowPulse.value), 105.dp.toPx(), androidx.compose.ui.geometry.Offset(centerX, centerY))
        }
        Column(
            Modifier.align(Alignment.CenterStart).fillMaxWidth(.58f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("YOUR DAILY WELLNESS", color = Lime, fontSize = 9.sp, letterSpacing = 1.7.sp, fontWeight = FontWeight.Bold)
            Text(
                if (tracking) "در حال ثبت فعالیت" else "امروز، برای خودت وقت بذار.",
                color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Black, lineHeight = 31.sp
            )
            Text(
                if (tracking) "%.2f km  •  %02d:%02d".format(distance / 1000f, seconds / 60, seconds % 60)
                else "حرکت، تغذیه و ریکاوری؛ همه در یک مسیر.",
                color = Muted, fontSize = 11.sp, lineHeight = 17.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(Lime), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, null, tint = Ink)
                }
                Text(if (tracking) "مشاهده فعالیت" else "شروع فعالیت", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            "M",
            Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 2.dp),
            color = Lime.copy(alpha = .9f), fontSize = 12.sp, fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun VoiceCoachPanel(message: String, onActivity: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var speaking by remember { mutableStateOf(false) }
    val tts = remember {
        TextToSpeech(context) { status ->
            speaking = false
        }
    }
    DisposableEffect(tts) {
        onDispose { tts.stop(); tts.shutdown() }
    }
    Column(Modifier.fillMaxWidth().glassSurface(RoundedCornerShape(22.dp), Color(0xFFEAF2F0)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(Icons.Default.RecordVoiceOver, null, tint = Ink, modifier = Modifier.size(22.dp))
            Text("راهنمای صوتی MFit", color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Text(message, color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val result = tts.setLanguage(java.util.Locale("fa", "IR"))
                tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "mfit-daily-coach")
                speaking = true
            }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Ink)) {
                Icon(Icons.Default.VolumeUp, null); Spacer(Modifier.width(6.dp)); Text("پخش راهنمای امروز")
            }
            OutlinedButton(onClick = onActivity) { Icon(Icons.Default.DirectionsRun, null); Text("فعالیت") }
        }
        Text("پخش فارسی به پشتیبانی موتور گفتار نصب‌شده روی گوشی وابسته است.", color = Muted, fontSize = 9.sp)
    }
}

@Composable
private fun WeeklyAnalyticsPanel(snapshots: List<DailyWellnessSnapshot>) {
    val values = snapshots.takeLast(7)
    Column(Modifier.fillMaxWidth().glassSurface(RoundedCornerShape(24.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("تحلیل هفتگی", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        Text("روند ثبت آب و کالری تمرین در روزهای ذخیره‌شده", color = Muted, fontSize = 11.sp)
        if (values.isEmpty()) {
            Text("با ثبت فعالیت‌ها و عادت‌های روزانه، نمودار هفتگی ساخته می‌شود.", color = Muted, fontSize = 11.sp)
        } else {
            values.forEach { day ->
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(day.date.takeLast(5), color = Ink, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        Text("${day.workoutCalories} kcal تمرین • ${day.water} لیوان آب", color = Muted, fontSize = 9.sp)
                    }
                    Row(Modifier.fillMaxWidth().height(7.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.weight((day.water.coerceAtLeast(0) + 1).toFloat()).fillMaxHeight().clip(CircleShape).background(Color(0xFF8BAA9A)))
                        Box(Modifier.weight((day.workoutCalories.coerceAtLeast(0) / 20f + 1f)).fillMaxHeight().clip(CircleShape).background(Lime))
                    }
                }
            }
        }
    }
}

@Composable
private fun AchievementPanel(snapshots: List<DailyWellnessSnapshot>, workouts: Int, habits: Int) {
    val activeDays = snapshots.count { it.workoutCalories > 0 || it.habits > 0 }
    val badges = listOf(
        Triple("شروع مسیر", "اولین روز ثبت‌شده", activeDays >= 1),
        Triple("سه روز فعال", "ساختن یک ریتم اولیه", activeDays >= 3),
        Triple("هفت روز پیوسته", "یک هفته ثبت منظم", activeDays >= 7),
        Triple("تمرین‌دوست", "ثبت ۵ جلسه تمرین", workouts >= 5),
        Triple("عادت‌ساز", "تکمیل همه عادت‌های امروز", habits >= 4)
    )
    Column(Modifier.fillMaxWidth().glassSurface(RoundedCornerShape(24.dp), Color(0xFF324B4B)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("دستاوردها", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        badges.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { badge ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(15.dp))
                        .background(if (badge.third) Color(0xFF31594D).copy(alpha = .24f) else Color(0xFF31594D).copy(alpha = .13f)).padding(11.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(if (badge.third) Icons.Default.EmojiEvents else Icons.Default.Lock, null,
                            tint = if (badge.third) Color(0xFF758C20) else Muted, modifier = Modifier.size(19.dp))
                        Text(badge.first, color = Ink, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text(badge.second, color = Muted, fontSize = 9.sp)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SmartCoachPanel(
    water: Int, mealCount: Int, sleepHours: Float, workoutCalories: Int,
    habitsCompleted: Int, weeklyDays: Int, weeklyWorkoutCalories: Int, weeklyAverageWater: Int, onFood: () -> Unit, onRoutine: () -> Unit
) {
    val recovery = ((sleepHours / 8f).coerceIn(0f, 1f) * 55f +
        (habitsCompleted / 4f) * 25f +
        (if (water >= 6) 20f else water / 6f * 20f)).toInt().coerceIn(0, 100)
    val advice = when {
        sleepHours < 6f -> "امشب ریکاوری را در اولویت بگذار؛ خواب کافی از تمرین سنگین مهم‌تر است."
        water < 4 -> "تا اینجای روز آب کمی ثبت شده؛ یک یا دو لیوان آب به برنامه‌ات اضافه کن."
        mealCount == 0 -> "هنوز وعده‌ای ثبت نکرده‌ای؛ غذایت را وارد کن تا تحلیل تغذیه دقیق‌تر شود."
        workoutCalories == 0 -> "اگر انرژی داری، ۱۰ تا ۱۵ دقیقه پیاده‌روی سبک می‌تواند شروع خوبی باشد."
        else -> "ریتم خوبی داری؛ استمرار و خواب کافی را حفظ کن و لازم نیست هر روز شدت تمرین را بالا ببری."
    }
    Column(
        Modifier.fillMaxWidth().glassSurface(RoundedCornerShape(26.dp), Color(0xFFEAF5F0)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("M FIT • SMART COACH", color = Lime, fontSize = 9.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Bold)
                Text("مربی هوشمند روزانه", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }
            Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = .72f)).border(1.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                Text("$recovery", color = Lime, fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
        }
        Text("شاخص آمادگی تخمینی • $recovery از ۱۰۰", color = Muted, fontSize = 11.sp)
        Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(Ink.copy(alpha = .10f))) {
            Box(Modifier.fillMaxWidth(recovery / 100f).fillMaxHeight().clip(CircleShape).background(Lime))
        }
        Text(advice, color = Ink, fontSize = 13.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(Color(0xFF31594D).copy(alpha = .12f)).border(1.dp, Color.White.copy(alpha = .2f), RoundedCornerShape(17.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("گزارش ثبت‌شده اخیر", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("$weeklyDays روز", color = Lime, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("کالری تمرین", color = Color(0xFFB8C9C0), fontSize = 9.sp); Text("$weeklyWorkoutCalories kcal", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                Column { Text("میانگین آب", color = Color(0xFFB8C9C0), fontSize = 9.sp); Text("$weeklyAverageWater لیوان/روز", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            OutlinedButton(onClick = onFood, modifier = Modifier.weight(1f), border = androidx.compose.foundation.BorderStroke(1.dp, Lime.copy(alpha = .55f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink)) {
                Icon(Icons.Default.Restaurant, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("تحلیل تغذیه", fontSize = 11.sp)
            }
            OutlinedButton(onClick = onRoutine, modifier = Modifier.weight(1f), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .35f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                Icon(Icons.Default.Bedtime, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("ریکاوری و روتین", fontSize = 11.sp)
            }
        }
        Text("این امتیاز یک برآورد محلی بر اساس خواب، آب و عادت‌های ثبت‌شده است؛ داده حسگر یا تشخیص پزشکی نیست.", color = Muted, fontSize = 9.sp, lineHeight = 14.sp)
    }
}

@Composable
private fun DashboardStat(title: String, value: String, unit: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Row(modifier.glassSurface(RoundedCornerShape(22.dp), Color(0xFFEAF2F0)).padding(15.dp),
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
    Column(modifier.glassSurface(RoundedCornerShape(22.dp), Color(0xFFEAF2F0)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
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
    Box(Modifier.fillMaxWidth().glassSurface(RoundedCornerShape(24.dp), Color(0xFF324B4B)).padding(22.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("YOUR DAILY PERFORMANCE", color = Lime, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            Text(if(tracking) "در حال حرکت" else "امروز، روز توست", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text(if(tracking) "%.2f کیلومتر • %02d:%02d".format(distance / 1000f, seconds / 60, seconds % 60) else "قدم‌های کوچک، نتیجه‌های بزرگ.", color = Muted, fontSize = 14.sp)
            Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Ink.copy(alpha = .12f))) {
                Box(Modifier.fillMaxWidth(if(tracking) (distance / 5000f).coerceIn(.05f, 1f) else .34f).fillMaxHeight().clip(CircleShape).background(Lime))
            }
        }
    }
}

@Composable
private fun ActivityPanel(
    tracking: Boolean, paused: Boolean, distance: Float, seconds: Long,
    routePoints: List<Pair<Double, Double>>, currentLocation: Pair<Double, Double>?,
    activityHistory: List<String>, onStart: () -> Unit, onPause: () -> Unit,
    onResume: () -> Unit, onStop: () -> Unit, onRequestCurrentLocation: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedActivity by remember { mutableIntStateOf(0) }
    var satellite by remember { mutableStateOf(false) }
    val types = listOf(
        Icons.Default.DirectionsWalk to "پیاده‌روی", Icons.Default.DirectionsBike to "دوچرخه", Icons.Default.FitnessCenter to "قدرتی",
        Icons.Default.DirectionsRun to "دویدن", Icons.Default.Favorite to "هوازی", Icons.Default.SelfImprovement to "یوگا"
    )
    val pulse by rememberInfiniteTransition(label = "live-dot").animateFloat(.25f, 1f,
        infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "live-dot-alpha")
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
    val routeLine = remember(mapView) {
        Polyline().apply {
            outlinePaint.color = android.graphics.Color.rgb(78, 154, 96)
            outlinePaint.strokeWidth = 10f
            outlinePaint.isAntiAlias = true
            outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
            outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
        }
    }
    val marker = remember(mapView) { Marker(mapView).apply { setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM); title = "موقعیت فعلی" } }
    var hasCentered by remember(mapView) { mutableStateOf(false) }
    var renderedRoute by remember(mapView) { mutableStateOf<List<Pair<Double, Double>>>(emptyList()) }
    var renderedLocation by remember(mapView) { mutableStateOf<Pair<Double, Double>?>(null) }
    DisposableEffect(mapView) { onDispose { mapView.onPause(); mapView.onDetach() } }

    Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("فعالیت", color = Color(0xFF203E2C), fontSize = 27.sp, fontWeight = FontWeight.Black)
                Text("حرکت کن، پیشرفت کن", color = Color(0xFF728174), fontSize = 11.sp)
            }
            Row(Modifier.clip(RoundedCornerShape(30.dp)).background(Color(0xFFDCEBDD)).padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF398B50)))
                Spacer(Modifier.width(6.dp))
                Text("GPS آماده", color = Color(0xFF315A40), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(Modifier.fillMaxWidth().clip(CircleShape).background(Color.White).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf("امروز", "هفته", "تاریخچه").forEachIndexed { index, label ->
                Box(Modifier.weight(1f).clip(CircleShape).background(if (index == 0) Color(0xFFDDEBDD) else Color.Transparent).clickable { }.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                    Text(label, color = if (index == 0) Color(0xFF28543A) else Color(0xFF758276), fontSize = 10.sp, fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFA8DDA0), Color(0xFF8BCB8D), Color(0xFF78B982))))
            .padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Row(Modifier.clip(CircleShape).background(Color(0xFF315B3D).copy(alpha = .72f)).border(1.dp, Color.White.copy(alpha = .65f), CircleShape).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF20B94C).copy(alpha = if (tracking) pulse else 1f)))
                    Spacer(Modifier.width(6.dp))
                    Text("فعالیت زنده", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(Modifier.clip(CircleShape).background(Color(0xFF315B3D).copy(alpha = .72f)).border(1.dp, Color.White.copy(alpha = .65f), CircleShape).padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text("مسافت پیموده‌شده", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text("%.2f KM".format(java.util.Locale.US, distance / 1000f), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End, color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Black)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ActivityStat("مدت", "%02d:%02d".format(seconds / 60, seconds % 60))
                ActivityStat("کالری", "—")
                ActivityStat("سرعت", if (seconds > 0 && distance > 0) "%.1f".format(java.util.Locale.US, distance / 1000f / (seconds / 3600f)) else "0.0")
                ActivityStat("ضربان", "—")
            }
            Text("کنترل فعالیت", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ActivityControl(Icons.Default.PlayArrow, "شروع", onStart, !tracking && !paused, Modifier.weight(1f))
                ActivityControl(Icons.Default.Pause, "مکث", onPause, tracking, Modifier.weight(1f))
                ActivityControl(Icons.Default.PlayCircleOutline, "ادامه", onResume, paused, Modifier.weight(1f))
                ActivityControl(Icons.Default.Stop, "توقف", onStop, tracking || paused, Modifier.weight(1f))
                ActivityControl(Icons.Default.Share, "اشتراک", {
                    val text = "گزارش فعالیت MFit\nنوع فعالیت: ${types[selectedActivity].second}\nمدت: %02d:%02d\nمسافت: %.2f کیلومتر".format(seconds / 60, seconds % 60, distance / 1000f)
                    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
                    context.startActivity(Intent.createChooser(send, "اشتراک‌گذاری فعالیت"))
                }, true, Modifier.weight(1f))
            }
            Text(when { tracking -> "فعالیت در حال اجرا"; paused -> "فعالیت موقتاً متوقف شده"; else -> "آماده‌ی شروع فعالیت" }, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        }
        SectionTitle("نوع فعالیت", "CHOOSE YOUR SESSION")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            types.chunked(3).forEachIndexed { rowIndex, rowItems ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowItems.forEachIndexed { columnIndex, item ->
                        val index = rowIndex * 3 + columnIndex
                        val selected = selectedActivity == index
                        Column(Modifier.weight(1f).height(78.dp).clip(RoundedCornerShape(17.dp))
                            .background(if (selected) Color(0xFFD6EBD1) else Color.White)
                            .border(1.dp, if (selected) Color(0xFF78A879) else Color.White, RoundedCornerShape(17.dp))
                            .clickable { selectedActivity = index }.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(item.first, null, tint = Color(0xFF315D3E), modifier = Modifier.size(21.dp))
                            Spacer(Modifier.height(4.dp))
                            Text(item.second, color = Color(0xFF36543D), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("مسیر فعالیت", color = Color(0xFF254631), fontSize = 17.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            Text("%.2f KM".format(java.util.Locale.US, distance / 1000f), color = Color(0xFF254631), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().clip(CircleShape).background(Color(0xFFEEF2EC)).padding(3.dp)) {
                listOf("خیابانی", "ماهواره‌ای").forEachIndexed { index, label ->
                    Box(Modifier.weight(1f).clip(CircleShape).background(if (satellite == (index == 1)) Color.White else Color.Transparent).clickable { satellite = index == 1 }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(label, color = Color(0xFF34523C), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxWidth().height(155.dp).clip(RoundedCornerShape(16.dp)), update = { map ->
                map.setTileSource(if (satellite) TileSourceFactory.USGS_SAT else TileSourceFactory.MAPNIK)
                if (routePoints != renderedRoute || currentLocation != renderedLocation) {
                    if (routePoints.isNotEmpty()) {
                        val geo = routePoints.map { GeoPoint(it.first, it.second) }
                        routeLine.setPoints(geo)
                        if (routeLine !in map.overlays) map.overlays.add(routeLine)
                        marker.position = geo.last()
                        marker.icon = ContextCompat.getDrawable(context, android.R.drawable.ic_menu_mylocation)
                        marker.title = if (tracking) "موقعیت فعلی" else "پایان مسیر"
                        if (marker !in map.overlays) map.overlays.add(marker)
                        if (!hasCentered) { map.controller.setZoom(16.0); map.controller.setCenter(geo.last()); hasCentered = true }
                        else if (tracking) map.controller.animateTo(geo.last())
                    } else if (currentLocation != null) {
                        map.overlays.remove(routeLine)
                        marker.position = GeoPoint(currentLocation.first, currentLocation.second)
                        marker.icon = ContextCompat.getDrawable(context, android.R.drawable.ic_menu_mylocation)
                        if (marker !in map.overlays) map.overlays.add(marker)
                        if (!hasCentered) { map.controller.setZoom(16.0); map.controller.animateTo(marker.position); hasCentered = true }
                    }
                    renderedRoute = routePoints
                    renderedLocation = currentLocation
                    map.invalidate()
                }
            })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (routePoints.isEmpty()) "با شروع فعالیت مسیر واقعی ثبت می‌شود" else "${routePoints.size} نقطه ثبت‌شده", color = Color(0xFF78847B), fontSize = 9.sp)
                TextButton(onClick = onRequestCurrentLocation) { Icon(Icons.Default.MyLocation, null, modifier = Modifier.size(15.dp)); Text("مکان من", fontSize = 10.sp) }
            }
        }
        SectionTitle("خلاصه امروز", "عملکرد روزانه")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActivitySummary(Icons.Default.DirectionsWalk, "—", "قدم", Modifier.weight(1f))
            ActivitySummary(Icons.Default.Route, "%.2f".format(java.util.Locale.US, distance / 1000f), "کیلومتر", Modifier.weight(1f))
            ActivitySummary(Icons.Default.LocalFireDepartment, "—", "کالری", Modifier.weight(1f))
        }
        if (activityHistory.isNotEmpty()) {
            SectionTitle("سوابق", "آخرین فعالیت‌های ثبت‌شده")
            activityHistory.take(5).forEach { item ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).padding(13.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(item.substringBefore("|"), color = Color(0xFF254D37), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(item.substringAfter("|", ""), color = Color(0xFF78847B), fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable private fun ActivityStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White.copy(alpha = .82f), fontSize = 9.sp)
        Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
    }
}
@Composable private fun ActivityControl(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, action: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(11.dp)
    Column(modifier.clip(shape).background(Color.White.copy(alpha = if (enabled) .25f else .12f))
        .border(1.dp, Color.White.copy(alpha = .62f), shape).clickable(enabled = enabled, onClick = action)
        .padding(vertical = 8.dp, horizontal = 1.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(17.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
@Composable private fun ActivitySummary(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).padding(vertical = 12.dp, horizontal = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Color(0xFF5B9360), modifier = Modifier.size(18.dp))
        Text(value, color = Color(0xFF284A33), fontSize = 16.sp, fontWeight = FontWeight.Black)
        Text(label, color = Color(0xFF78847B), fontSize = 9.sp)
    }
}

@Composable
private fun RoutinePanel(
    sleep: Float, onSleepChange: (Float) -> Unit, weightKg: Double,
    workoutLog: List<String>, onAddWorkout: (String) -> Unit,
    waterGlasses: Int, onWaterChange: (Int) -> Unit,
    habitsDone: List<Boolean>, onHabitChange: (Int, Boolean) -> Unit
) {
    val workouts = listOf(
        Triple("کشش کل بدن", 2.5, "حرکات آرام برای گردن، شانه، پشت و پاها"),
        Triple("انعطاف و کشش عمیق", 2.8, "کشش کنترل‌شده عضلات اصلی بدن"),
        Triple("گرم‌کردن و تحرک مفاصل", 3.0, "حرکت نرم مفاصل و آماده‌سازی بدن"),
        Triple("یوگای سبک در خانه", 2.5, "تعادل، تنفس و انعطاف"),
        Triple("فول‌بادی سبک بدون وسیله", 3.5, "اسکوات، پل باسن، شنا روی دیوار و پلانک"),
        Triple("فول‌بادی متوسط بدون وسیله", 5.0, "اسکوات، لانج، شنا و تمرین میان‌تنه"),
        Triple("تمرین شکم و میان‌تنه", 3.8, "پلانک، دِدباگ و کرانچ کنترل‌شده")
    )
    val context = androidx.compose.ui.platform.LocalContext.current
    val workoutPrefs = remember { context.getSharedPreferences("mfit_native", Context.MODE_PRIVATE) }
    var selected by remember { mutableStateOf(workoutPrefs.getString("active_workout_name", workouts[0].first) ?: workouts[0].first) }
    var minutes by remember { mutableStateOf(workoutPrefs.getInt("active_workout_duration", 15).toString()) }
    val selectedWorkout = workouts.firstOrNull { it.first == selected } ?: workouts[0]
    val duration = minutes.toIntOrNull()?.coerceIn(1, 180) ?: 0
    val estimated = selectedWorkout.second * 3.5 * weightKg / 200.0 * duration
    val totalBurned = workoutLog.sumOf { it.substringAfterLast("|", "0").toDoubleOrNull() ?: 0.0 }
    var workoutRemainingSeconds by remember { mutableIntStateOf(workoutPrefs.getInt("workout_remaining_seconds", 0)) }
    var workoutRunning by remember { mutableStateOf(workoutPrefs.getBoolean("workout_running", false)) }
    var workoutSessionStarted by remember { mutableStateOf(workoutPrefs.getBoolean("workout_session_started", false)) }
    LaunchedEffect(workoutRunning) {
        while (workoutRunning) {
            val endAt = workoutPrefs.getLong("workout_end_at", 0L)
            workoutRemainingSeconds = ((endAt - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
            workoutPrefs.edit().putInt("workout_remaining_seconds", workoutRemainingSeconds).apply()
            if (workoutRemainingSeconds == 0) {
                workoutRunning = false
                workoutPrefs.edit().putBoolean("workout_running", false).apply()
                break
            }
            delay(1000L)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle("روتین روزانه", "تمرین، خواب و مراقبت‌های روزمره")
        Card(modifier = Modifier.glassSurface(RoundedCornerShape(24.dp), Color(0xFFEAF5F0)), colors = CardDefaults.cardColors(containerColor = Color.Transparent), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("کالری سوزانده‌شده با تمرین خانگی", color = Muted, fontSize = 12.sp)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(totalBurned.toInt().toString(), color = Lime, fontSize = 38.sp, fontWeight = FontWeight.Black)
                    Text("kcal امروز", color = Ink, fontSize = 12.sp, modifier = Modifier.padding(bottom = 7.dp))
                }
                Text("برآورد بر اساس نوع تمرین، مدت و وزن پروفایل", color = Muted, fontSize = 10.sp)
            }
        }
        SectionTitle("آب‌رسانی روزانه", "هدف پیشنهادی: ۸ لیوان، هر لیوان حدود ۲۵۰ میلی‌لیتر")
        Card(modifier = Modifier.glassSurface(RoundedCornerShape(22.dp)), colors = CardDefaults.cardColors(containerColor = Color.Transparent), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column { Text("${waterGlasses} / 8 لیوان", fontSize = 24.sp, color = Ink, fontWeight = FontWeight.Black); Text("${waterGlasses * 250} میلی‌لیتر ثبت‌شده", color = Muted, fontSize = 11.sp) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onWaterChange(waterGlasses - 1) }, enabled = waterGlasses > 0) { Text("−") }
                        Button(onClick = { onWaterChange(waterGlasses + 1) }, colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink)) { Text("+ لیوان") }
                    }
                }
                Box(Modifier.fillMaxWidth().height(9.dp).clip(CircleShape).background(Color(0xFF536A6A).copy(alpha = .35f))) {
                    Box(Modifier.fillMaxWidth((waterGlasses / 8f).coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(Lime))
                }
            }
        }
        SectionTitle("عادت‌های امروز", "کارهای کوچک و قابل تکرار")
        val dailyHabits = listOf("حداقل ۱۵ دقیقه پیاده‌روی یا تحرک", "ثبت وعده‌های غذایی", "نوشیدن آب در طول روز", "خاموش‌کردن صفحه‌نمایش پیش از خواب")
        Card(modifier = Modifier.glassSurface(RoundedCornerShape(22.dp)), colors = CardDefaults.cardColors(containerColor = Color.Transparent), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                dailyHabits.forEachIndexed { index, label ->
                    Row(Modifier.fillMaxWidth().clickable { onHabitChange(index, !habitsDone.getOrElse(index) { false }) }.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        androidx.compose.material3.Checkbox(checked = habitsDone.getOrElse(index) { false }, onCheckedChange = { onHabitChange(index, it) })
                        Text(label, color = if (habitsDone.getOrElse(index) { false }) Muted else Ink, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    if (index != dailyHabits.lastIndex) HorizontalDivider(color = MFitPalette.Stroke, modifier = Modifier.padding(horizontal = 14.dp))
                }
                val completed = habitsDone.count { it }
                Text("${completed} از ۴ عادت تکمیل شده", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = Muted, fontSize = 11.sp)
            }
        }
        SectionTitle("تمرین‌های قابل ثبت", "بدون باشگاه و با وزن بدن")
        workouts.forEach { item ->
            val active = selected == item.first
            Row(Modifier.fillMaxWidth().glassSurface(RoundedCornerShape(17.dp), if (active) Color(0xFF324B4B) else Color(0xFF1C3031))
                .clickable { selected = item.first }.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(if (active) Lime else Color(0xFF536A6A)), contentAlignment = Alignment.Center) {
                    Icon(if (item.first.contains("کشش") || item.first.contains("انعطاف") || item.first.contains("یوگا")) Icons.Default.SelfImprovement else Icons.Default.FitnessCenter, null, tint = Ink, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(item.first, color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(item.third, color = Muted, fontSize = 10.sp)
                }
                if (active) Icon(Icons.Default.CheckCircle, null, tint = Ink)
            }
        }
        OutlinedTextField(value = minutes, onValueChange = { minutes = it.filter(Char::isDigit).take(3) },
            label = { Text("مدت تمرین (دقیقه)") }, suffix = { Text("min") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Card(modifier = Modifier.glassSurface(RoundedCornerShape(20.dp)), colors = CardDefaults.cardColors(containerColor = Color.Transparent), shape = RoundedCornerShape(20.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("کالری تخمینی این تمرین", color = Muted, fontSize = 11.sp)
                    Text("${estimated.toInt().coerceAtLeast(0)} kcal", color = Ink, fontSize = 27.sp, fontWeight = FontWeight.Black)
                }
                Text("${selectedWorkout.second} MET", color = Muted, fontSize = 11.sp)
            }
        }
        if (workoutSessionStarted) {
            Card(modifier = Modifier.fillMaxWidth().glassSurface(RoundedCornerShape(22.dp), Color(0xFFEAF2D4)),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("جلسه فعال • ${selectedWorkout.first}", color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("%02d:%02d".format(workoutRemainingSeconds / 60, workoutRemainingSeconds % 60),
                        color = Ink, fontSize = 38.sp, fontWeight = FontWeight.Black)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            if (workoutRunning) {
                                val left = ((workoutPrefs.getLong("workout_end_at", 0L) - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
                                workoutRemainingSeconds = left
                                workoutRunning = false
                                workoutPrefs.edit().putInt("workout_remaining_seconds", left).putBoolean("workout_running", false).apply()
                            } else {
                                workoutRunning = true
                                workoutPrefs.edit().putBoolean("workout_running", true)
                                    .putLong("workout_end_at", System.currentTimeMillis() + workoutRemainingSeconds * 1000L).apply()
                            }
                        }, modifier = Modifier.weight(1f)) {
                            Icon(if (workoutRunning) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(5.dp)); Text(if (workoutRunning) "مکث" else "ادامه")
                        }
                        Button(onClick = {
                            val completedMinutes = ((duration * 60 - workoutRemainingSeconds) / 60).coerceAtLeast(1)
                            val calories = (selectedWorkout.second * 3.5 * weightKg / 200.0 * completedMinutes).toInt()
                            onAddWorkout("${selectedWorkout.first}|$completedMinutes|$calories")
                            workoutRunning = false
                            workoutSessionStarted = false
                            workoutRemainingSeconds = 0
                            workoutPrefs.edit().putBoolean("workout_running", false).putBoolean("workout_session_started", false)
                                .putInt("workout_remaining_seconds", 0).putLong("workout_end_at", 0L).apply()
                        }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Ink)) {
                            Icon(Icons.Default.Check, null); Spacer(Modifier.width(5.dp)); Text("پایان و ثبت")
                        }
                    }
                    Text(if (workoutRemainingSeconds == 0) "زمان تمرین تمام شد؛ جلسه را ثبت کن." else "زمان‌سنج مستقل از تغییر صفحات تا زمانی که این صفحه فعال باشد کار می‌کند.",
                        color = Muted, fontSize = 10.sp)
                }
            }
        } else {
            Button(onClick = {
                if (duration > 0) {
                    workoutRemainingSeconds = duration * 60
                    workoutSessionStarted = true
                    workoutRunning = true
                    workoutPrefs.edit().putString("active_workout_name", selectedWorkout.first)
                        .putInt("active_workout_duration", duration).putInt("workout_remaining_seconds", workoutRemainingSeconds)
                        .putBoolean("workout_session_started", true).putBoolean("workout_running", true)
                        .putLong("workout_end_at", System.currentTimeMillis() + duration * 60_000L).apply()
                }
            }, enabled = duration > 0, modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink), shape = RoundedCornerShape(17.dp)) {
                Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(7.dp)); Text("شروع تمرین زمان‌دار")
            }
            Text("پس از پایان جلسه، مدت واقعی و کالری تخمینی آن ثبت می‌شود.", color = Muted, fontSize = 10.sp)
        }
        if (workoutLog.isNotEmpty()) {
            SectionTitle("تمرین‌های ثبت‌شده امروز", "${workoutLog.size} جلسه")
            workoutLog.asReversed().forEach { entry ->
                val parts = entry.split("|")
                Row(Modifier.fillMaxWidth().glassSurface(RoundedCornerShape(13.dp)).padding(13.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(parts.getOrNull(0) ?: "تمرین", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text("${parts.getOrNull(1) ?: "0"} دقیقه", color = Muted, fontSize = 10.sp)
                    }
                    Text("${parts.getOrNull(2) ?: "0"} kcal", color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
        HorizontalDivider(color = MFitPalette.Stroke)
        SectionTitle("خواب شبانه", "مدت استراحتت را ثبت کن")
        Card(modifier = Modifier.glassSurface(RoundedCornerShape(22.dp)), colors = CardDefaults.cardColors(containerColor = Color.Transparent), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${sleep.toInt()} ساعت", color = Ink, fontSize = 32.sp, fontWeight = FontWeight.Black)
                Slider(value = sleep, onValueChange = onSleepChange, valueRange = 0f..12f, steps = 23,
                    colors = SliderDefaults.colors(thumbColor = Lime, activeTrackColor = Ink))
            }
        }
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
                    .glassSurface(RoundedCornerShape(13.dp), if (selected == food) Color(0xFFEAF2D4) else Color(0xFF1C3031))
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
        Card(modifier = Modifier.glassSurface(RoundedCornerShape(24.dp), Ink), colors = CardDefaults.cardColors(containerColor = Color.Transparent), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(selected?.name ?: "ابتدا یک غذا انتخاب کن", color = Muted, fontSize = 13.sp)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(if(selected == null) "—" else kcal.toInt().toString(), color = Ink, fontSize = 38.sp, fontWeight = FontWeight.Black)
                    Text("کیلوکالری", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 7.dp))
                    Spacer(Modifier.weight(1f))
                    Text(if(selected == null) "بر اساس 100 گرم" else "${grams.toInt()} گرم", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
                }
                HorizontalDivider(color = MFitPalette.Stroke)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    NutrientValue("پروتئین", protein, Ink)
                    NutrientValue("کربوهیدرات", carbs, Ink)
                    NutrientValue("چربی", fat, Ink)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    NutrientValue("قند", sugar, Ink)
                    NutrientValue("فیبر", fiber, Ink)
                }
            }
        }
        Button(onClick = {
            selected?.let { food ->
                onAdd("${food.name} • ${grams.toInt()} g • ${kcal.toInt()} kcal • ${"%.1f".format(java.util.Locale.US, protein)}g P • ${"%.1f".format(java.util.Locale.US, carbs)}g C • ${"%.1f".format(java.util.Locale.US, fat)}g F • ${"%.1f".format(java.util.Locale.US, sugar)}g S • ${"%.1f".format(java.util.Locale.US, fiber)}g Fib")
            }
        }, enabled = selected != null && grams > 0.0, modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ink), shape = RoundedCornerShape(17.dp)) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(7.dp)); Text("ثبت در وعده امروز", color = MFitPalette.Canvas)
        }
        SectionTitle("ترکیب مواد مغذی", "مقایسه مقدار پروتئین، کربوهیدرات و چربی ثبت‌شده")
        Card(modifier = Modifier.glassSurface(RoundedCornerShape(20.dp)), colors = CardDefaults.cardColors(containerColor = Color.Transparent), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val macroMax = maxOf(todayProtein, todayCarbs, todayFat, 1.0)
                MacroBar("پروتئین", todayProtein, macroMax, Color(0xFF7E9D62))
                MacroBar("کربوهیدرات", todayCarbs, macroMax, Color(0xFFDBA34A))
                MacroBar("چربی", todayFat, macroMax, Color(0xFF7896B5))
            }
        }
        SectionTitle("جمع ثبت‌شده امروز", "مجموع غذاهایی که در این روز وارد کرده‌ای")
        Card(modifier = Modifier.glassSurface(RoundedCornerShape(20.dp)), colors = CardDefaults.cardColors(containerColor = Color.Transparent), shape = RoundedCornerShape(20.dp)) {
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
            Text(entry, modifier = Modifier.fillMaxWidth().glassSurface(RoundedCornerShape(12.dp)).padding(12.dp), fontSize = 12.sp, color = Ink)
        }
        Text("مقادیر این فهرست، مرجع عمومی و تقریبی بر پایه ۱۰۰ گرم بخش خوراکی هستند. نوع پخت و برند می‌تواند مقدار واقعی را تغییر دهد.", color = Muted, fontSize = 10.sp)
    }
}

@Composable private fun MacroBar(label: String, value: Double, maxValue: Double, color: Color) {
    val progress by animateFloatAsState((value / maxValue).toFloat().coerceIn(0f, 1f), animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy), label = "macro-bar-$label")
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 12.sp, color = Ink, fontWeight = FontWeight.SemiBold)
            Text("%.1f g".format(java.util.Locale.US, value), fontSize = 11.sp, color = Muted)
        }
        Box(Modifier.fillMaxWidth().height(9.dp).clip(CircleShape).background(Color(0xFFEDEFEB))) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().clip(CircleShape).background(color))
        }
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
    Column(modifier.glassSurface(RoundedCornerShape(18.dp)).padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, fontSize = 12.sp, color = Muted)
        Text(value, fontSize = 25.sp, fontWeight = FontWeight.Black, color = Ink)
        Text(unit, fontSize = 11.sp, color = Muted)
    }
}