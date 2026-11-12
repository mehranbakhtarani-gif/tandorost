package com.mfit.app
import android.app.*
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import org.json.JSONArray

class TrackingService : Service(), LocationListener {
    private lateinit var manager: LocationManager
    private var distance = 0f
    private var last: Location? = null
    private var startedAt = 0L
    private var lastAcceptedAt = 0L
    override fun onCreate() {
        super.onCreate()
        startedAt = System.currentTimeMillis()
        lastAcceptedAt = startedAt
        distance = 0f
        last = null
        val channel = "mfit_activity"
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channel, "ثبت فعالیت", NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(this, channel).setContentTitle("MFit • فعالیت در حال ثبت").setContentText("GPS روشن است").setSmallIcon(android.R.drawable.ic_menu_mylocation).build()
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
        if (accepted && (route.length() == 0 || route.length() % 3 == 0 || route.length() < 2)) {
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
        val p = getSharedPreferences("mfit_native", MODE_PRIVATE)
        val meters = p.getFloat("live_distance", 0f); val seconds = p.getLong("live_seconds", 0L)
        if (meters > 0f) p.edit().putString("last_activity", "%.2f".format(meters / 1000f)).putLong("last_activity_seconds", seconds).putFloat("live_distance", 0f).putLong("live_seconds", 0L).putBoolean("tracking_active", false).apply() else p.edit().putBoolean("tracking_active", false).apply()
        sendBroadcast(Intent("com.mfit.app.TRACK_UPDATE").setPackage(packageName))
        super.onDestroy()
    }
}