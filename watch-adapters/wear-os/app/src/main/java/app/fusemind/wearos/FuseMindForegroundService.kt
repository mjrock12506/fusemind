package app.fusemind.wearos

/*
 * FuseMindForegroundService.kt
 *
 * Owns the BLE GATT server for its whole lifetime (P1-001).
 *
 * Why a foreground service and not just an Activity:
 *   On Wear OS the OS aggressively kills background processes when the screen
 *   sleeps. The PARTIAL_WAKE_LOCK in FuseMindWatchService keeps the CPU awake,
 *   but it does NOT stop Android from reclaiming the process. A foreground
 *   service with an ongoing notification is the supported way to keep BLE
 *   advertising alive through screen-off — which is exactly P1-001 acceptance
 *   criterion "advertising continues after the watch screen turns off".
 *
 * Apache-2.0 © FuseMind contributors
 */

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log

class FuseMindForegroundService : Service() {

    private val tag = "FuseMindFgService"

    /** The watch-side GATT server. Created once, lives as long as the service. */
    private val watch: FuseMindWatchService by lazy {
        FuseMindWatchService(applicationContext)
    }

    /** True once the GATT server is up. Repeated onStartCommand calls (the
     *  Activity re-resuming on every screen wake re-issues startForegroundService)
     *  must NOT re-stand-up the server or re-advertise. */
    private var serving = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Promote to foreground BEFORE touching BLE so the OS won't kill us mid-start.
        startInForeground()

        // Only stand up the GATT server on the first command. A re-resume of
        // MainActivity re-issues startForegroundService → another onStartCommand;
        // re-advertising from here is what caused "Advertising failed: 3".
        if (!serving) {
            val started = watch.startGattServer()
            if (!started) {
                // Bluetooth off, advertising unsupported, or openGattServer failed.
                // Don't crash; the Activity surfaces the failure to the user (P1-004).
                Log.w(tag, "GATT server failed to start; stopping foreground service")
                stopSelf()
                return START_NOT_STICKY
            }
            serving = true
        } else {
            Log.d(tag, "onStartCommand re-entry; GATT server already serving — no-op")
        }

        // START_STICKY: if the OS kills us under memory pressure, restart and
        // re-advertise once resources free up (groundwork for P1-003 persistence).
        return START_STICKY
    }

    override fun onDestroy() {
        watch.stopGattServer()
        serving = false
        super.onDestroy()
    }

    // This service is started, not bound.
    override fun onBind(intent: Intent?): IBinder? = null

    // ---- Foreground notification plumbing ----------------------------------

    private fun startInForeground() {
        val notification: Notification =
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("FuseMind")
                .setContentText("Connecting your watch…")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setOngoing(true)
                .build()

        // API 34+ wants the running foreground-service type declared explicitly.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FuseMind link",
                NotificationManager.IMPORTANCE_LOW // quiet; no sound for an always-on status
            ).apply { description = "Keeps the watch↔phone Bluetooth link alive." }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "fusemind_link"
        private const val NOTIF_ID = 1

        /** Start the service from an Activity once permissions are granted. */
        fun start(context: Context) {
            val intent = Intent(context, FuseMindForegroundService::class.java)
            context.startForegroundService(intent)
        }
    }
}
