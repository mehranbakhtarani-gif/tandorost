package com.mfit.app
import android.Manifest
import android.app.*
import android.content.pm.PackageManager
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TrackingService : Service(), LocationListener, SensorEventListener {
    private lateinit var manager: LocationManager
    private var distance = 0f
    private var last: Location? = null
    private var startedAt = 0L
    private var lastAcceptedAt = 0L
    private var pauseRequested = false
    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private var stepCounterBaseline: Float? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerTick = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            val elapsed = ((now - startedAt) / 1000L).coerceAtLeast(0L)
            getSharedPreferences("mfit_native", MODE_PRIVATE).edit()
                .putLong("live_seconds", elapsed)
                .putBoolean("tracking_active", true)
                .apply()
            sendBroadcast(Intent("com.mfit.app.TRACK_UPDATE").setPackage(packageName))
            timerHandler.postDelayed(this, 1000L)
        }
    }
    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("mfit_native", MODE_PRIVATE)
        val now = System.currentTimeMillis()
        startedAt = prefs.getLong("session_started_at", now).takeIf { it > 0L } ?: now
        lastAcceptedAt = now
        distance = prefs.getFloat("live_distance", 0f)
        last = null
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        val canReadActivity = Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepSensor != null && canReadActivity) {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:activity-tracking").apply {
            setReferenceCounted(false)
            acquire()
        }
        val channel = "mfit_activity"
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channel, "ثبت فعالیت", NotificationManager.IMPORTANCE_LOW))
        val stopIntent = PendingIntent.getService(
            this, 7, Intent(this, TrackingService::class.java).setAction("STOP"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, channel)
            .setContentTitle("MFit • فعالیت در حال ثبت")
            .setContentText("ثبت GPS در پس‌زمینه فعال است")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "پایان فعالیت", stopIntent)
            .build()
        startForeground(7, notification)
        manager = getSystemService(LOCATION_SERVICE) as LocationManager
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val preferredProvider = if (hasFine) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
        val fallbackProvider = if (hasFine) LocationManager.NETWORK_PROVIDER else null
        val provider = listOfNotNull(preferredProvider, fallbackProvider)
            .firstOrNull { candidate -> try { manager.isProviderEnabled(candidate) } catch (_: Exception) { false } }
        try {
            if (provider != null) manager.requestLocationUpdates(provider, 2000L, 1f, this)
        } catch (_: SecurityException) {
            // Timer remains active even if location permission/provider is unavailable.
        }
        timerHandler.post(timerTick)
    }
    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        val prefs = getSharedPreferences("mfit_native", MODE_PRIVATE)
        val currentSteps = prefs.getInt("live_steps", 0)
        val nextSteps = when (event.sensor.type) {
            Sensor.TYPE_STEP_DETECTOR -> currentSteps + 1
            Sensor.TYPE_STEP_COUNTER -> {
                val counter = event.values.firstOrNull() ?: return
                val baseline = stepCounterBaseline ?: (counter - currentSteps).also { stepCounterBaseline = it }
                maxOf(currentSteps, (counter - baseline).toInt().coerceAtLeast(0))
            }
            else -> return
        }
        if (nextSteps != currentSteps) {
            prefs.edit().putInt("live_steps", nextSteps).apply()
            sendBroadcast(Intent("com.mfit.app.TRACK_UPDATE").setPackage(packageName))
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onLocationChanged(location: Location) {
        if (location.accuracy > 35f) return
        val now = System.currentTimeMillis()
        val previous = last
        var accepted = previous == null
        if (previous != null) {
            val meters = previous.distanceTo(location)
            val seconds = ((now - lastAcceptedAt).coerceAtLeast(1000L)) / 1000f
            val plausible = meters <= maxOf(45f, seconds * 12f)
            if (plausible && meters >= 1.5f) {
                distance += meters
                accepted = true
            }
        }
        if (accepted) {
            last = location
            lastAcceptedAt = now
        }
        val prefs = getSharedPreferences("mfit_native", MODE_PRIVATE)
        val route = try { JSONArray(prefs.getString("route_points", "[]")) } catch (_: Exception) { JSONArray() }
        if (accepted) {
            route.put(JSONArray().put(location.latitude).put(location.longitude))
        }
        val elapsed = (now - startedAt) / 1000
        val safeSpeed = if (location.hasSpeed() && location.speed.isFinite()) (location.speed * 3.6f).coerceIn(0f, 25f) else 0f
        prefs.edit().putFloat("live_distance", distance).putLong("live_seconds", elapsed)
            .putFloat("live_speed_kmh", safeSpeed)
            .putString("route_points", route.toString()).putBoolean("tracking_active", true).apply()
        sendBroadcast(Intent("com.mfit.app.TRACK_UPDATE").setPackage(packageName))
    }
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    @Deprecated("Deprecated by platform") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "PAUSE" -> {
                pauseRequested = true
                val prefs = getSharedPreferences("mfit_native", MODE_PRIVATE)
                val elapsed = if (startedAt > 0L) ((System.currentTimeMillis() - startedAt) / 1000L).coerceAtLeast(prefs.getLong("live_seconds", 0L)) else prefs.getLong("live_seconds", 0L)
                prefs.edit().putLong("live_seconds", elapsed).putLong("session_started_at", 0L)
                    .putBoolean("tracking_active", false).putBoolean("session_paused", true).apply()
                stopSelf()
                return START_NOT_STICKY
            }
            "FINISH", "STOP" -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }
    override fun onDestroy() {
        try { manager.removeUpdates(this) } catch (_: Exception) {}
        try { sensorManager.unregisterListener(this) } catch (_: Exception) {}
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
        wakeLock = null
        timerHandler.removeCallbacks(timerTick)
        val p = getSharedPreferences("mfit_native", MODE_PRIVATE)
        if (pauseRequested) {
            val elapsed = if (startedAt > 0L) ((System.currentTimeMillis() - startedAt) / 1000L).coerceAtLeast(p.getLong("live_seconds", 0L)) else p.getLong("live_seconds", 0L)
            p.edit().putLong("live_seconds", elapsed).putLong("session_started_at", 0L)
                .putBoolean("tracking_active", false).putBoolean("session_paused", true).apply()
            sendBroadcast(Intent("com.mfit.app.TRACK_UPDATE").setPackage(packageName))
            super.onDestroy()
            return
        }
        val meters = p.getFloat("live_distance", 0f)
        val storedStart = p.getLong("session_started_at", 0L)
        val elapsedFromStart = if (storedStart > 0L) (System.currentTimeMillis() - storedStart) / 1000L else 0L
        val seconds = elapsedFromStart.coerceAtLeast(p.getLong("live_seconds", 0L))
        // Save timer-only sessions too when GPS is unavailable or the workout is stationary.
        if (meters > 0f || seconds > 0L) {
            val date = SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale.getDefault()).format(Date())
            val history = try { JSONArray(p.getString("activity_history", "[]")) } catch (_: Exception) { JSONArray() }
            val entry = "$date|%.2f km • %02d:%02d".format(Locale.US, meters / 1000f, seconds / 60, seconds % 60)
            history.put(0, entry)
            while (history.length() > 30) history.remove(history.length() - 1)
            val today = java.time.LocalDate.now().toString()
            val weight = p.getString("weight", "96")?.replace(",", ".")?.toDoubleOrNull()?.coerceIn(30.0, 250.0) ?: 96.0
            val gpsCalories = (weight * (meters / 1000.0) * 0.7).toInt().coerceAtLeast(0)
            val savedSteps = p.getInt("live_steps", 0)
            val avgSpeed = if (seconds > 0L) (meters / 1000.0) / (seconds / 3600.0) else 0.0
            val routeSnapshot = try { JSONArray(p.getString("route_points", "[]")) } catch (_: Exception) { JSONArray() }
            val sessions = try { JSONArray(p.getString("activity_sessions", "[]")) } catch (_: Exception) { JSONArray() }
            val session = org.json.JSONObject()
                .put("id", System.currentTimeMillis().toString())
                .put("date", date)
                .put("activity", p.getString("active_activity_name", "پیاده‌روی") ?: "پیاده‌روی")
                .put("distance_m", meters.toDouble())
                .put("duration_s", seconds)
                .put("steps", savedSteps)
                .put("calories", gpsCalories)
                .put("avg_speed_kmh", avgSpeed)
                .put("route", routeSnapshot)
            val updatedSessions = JSONArray().put(session)
            for (i in 0 until minOf(sessions.length(), 99)) {
                sessions.optJSONObject(i)?.let { updatedSessions.put(it) }
            }
            val logDate = p.getString("workout_log_date", "")
            val workoutLog = if (logDate == today) {
                try { p.getString("workout_log_today", "")?.split("\n")?.filter { it.isNotBlank() }?.toMutableList() ?: mutableListOf() }
                catch (_: Exception) { mutableListOf() }
            } else mutableListOf()
            val activityName = p.getString("active_activity_name", "پیاده‌روی") ?: "پیاده‌روی"
            if (gpsCalories > 0) workoutLog.add("$activityName GPS|${(seconds / 60).coerceAtLeast(1)}|$gpsCalories")
            p.edit().putString("activity_history", history.toString())
                .putString("activity_sessions", updatedSessions.toString())
                .putString("last_activity_route_points", routeSnapshot.toString())
                .putInt("last_activity_steps", savedSteps)
                .putInt("last_activity_calories", gpsCalories)
                .putFloat("last_activity_speed_kmh", avgSpeed.toFloat())
                .putString("last_activity", "%.2f".format(Locale.US, meters / 1000f))
                .putLong("last_activity_seconds", seconds)
                .putString("workout_log_date", today).putString("workout_log_today", workoutLog.joinToString("\n"))
                .putFloat("live_distance", 0f).putLong("live_seconds", 0L).putInt("live_steps", 0).putFloat("live_speed_kmh", 0f).putLong("session_started_at", 0L)
                .putBoolean("tracking_active", false).putBoolean("session_paused", false).apply()
        } else p.edit().putFloat("live_distance", 0f).putLong("live_seconds", 0L).putLong("session_started_at", 0L).putBoolean("tracking_active", false).apply()
        sendBroadcast(Intent("com.mfit.app.TRACK_UPDATE").setPackage(packageName))
        super.onDestroy()
    }
}