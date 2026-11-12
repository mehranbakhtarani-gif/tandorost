package com.mfit.app
import android.app.*
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TrackingService : Service(), LocationListener {
    private lateinit var manager: LocationManager
    private var distance = 0f
    private var last: Location? = null
    private var startedAt = 0L
    private var lastAcceptedAt = 0L
    private var wakeLock: PowerManager.WakeLock? = null
    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("mfit_native", MODE_PRIVATE)
        val now = System.currentTimeMillis()
        startedAt = prefs.getLong("session_started_at", now).takeIf { it > 0L } ?: now
        lastAcceptedAt = now
        distance = prefs.getFloat("live_distance", 0f)
        last = null
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
        try { manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000L, 2f, this) } catch (_: SecurityException) {}
    }
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
        prefs.edit().putFloat("live_distance", distance).putLong("live_seconds", elapsed)
            .putString("route_points", route.toString()).putBoolean("tracking_active", true).apply()
        sendBroadcast(Intent("com.mfit.app.TRACK_UPDATE").setPackage(packageName))
    }
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    @Deprecated("Deprecated by platform") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }; return START_STICKY }
    override fun onDestroy() {
        try { manager.removeUpdates(this) } catch (_: Exception) {}
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
        wakeLock = null
        val p = getSharedPreferences("mfit_native", MODE_PRIVATE)
        val meters = p.getFloat("live_distance", 0f); val seconds = p.getLong("live_seconds", 0L)
        if (meters > 0f) {
            val date = SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale.getDefault()).format(Date())
            val history = try { JSONArray(p.getString("activity_history", "[]")) } catch (_: Exception) { JSONArray() }
            val entry = "$date|%.2f km • %02d:%02d".format(Locale.US, meters / 1000f, seconds / 60, seconds % 60)
            history.put(0, entry)
            while (history.length() > 30) history.remove(history.length() - 1)
            val today = java.time.LocalDate.now().toString()
            val weight = p.getString("weight", "70")?.replace(",", ".")?.toDoubleOrNull()?.coerceIn(30.0, 250.0) ?: 70.0
            val gpsCalories = (weight * (meters / 1000.0) * 0.7).toInt().coerceAtLeast(0)
            val logDate = p.getString("workout_log_date", "")
            val workoutLog = if (logDate == today) {
                try { p.getString("workout_log_today", "")?.split("\n")?.filter { it.isNotBlank() }?.toMutableList() ?: mutableListOf() }
                catch (_: Exception) { mutableListOf() }
            } else mutableListOf()
            if (gpsCalories > 0) workoutLog.add("پیاده‌روی/دویدن GPS|${(seconds / 60).coerceAtLeast(1)}|$gpsCalories")
            p.edit().putString("activity_history", history.toString())
                .putString("last_activity", "%.2f".format(Locale.US, meters / 1000f))
                .putLong("last_activity_seconds", seconds)
                .putString("workout_log_date", today).putString("workout_log_today", workoutLog.joinToString("\n"))
                .putFloat("live_distance", 0f).putLong("live_seconds", 0L)
                .putBoolean("tracking_active", false).apply()
        } else p.edit().putBoolean("tracking_active", false).apply()
        sendBroadcast(Intent("com.mfit.app.TRACK_UPDATE").setPackage(packageName))
        super.onDestroy()
    }
}