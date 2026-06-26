package app.fusemind.wearos

/*
 * MainActivity.kt
 *
 * The app's launcher screen (P1-001). Its only jobs this phase:
 *   1. Request the runtime BLE permissions the GATT server needs.
 *   2. Once granted, start FuseMindForegroundService (which advertises).
 *   3. Show a one-line status. (The real Connected/Degraded UI is P1-004.)
 *
 * Graceful denial: if the user denies a permission we show why and stop —
 * we never call into BLE without it, so the app can't crash on a SecurityException
 * (P1-001 acceptance: "confirm denial is handled, not crashed").
 *
 * Apache-2.0 © FuseMind contributors
 */

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

class MainActivity : ComponentActivity() {

    private lateinit var status: TextView

    /** Runtime permissions this app must hold before it can advertise. */
    private val requiredPermissions: Array<String>
        get() = buildList {
            // Android 12+ (API 31+) split Bluetooth into runtime permissions.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            // Android 13+ (API 33+) requires opting in to post the ongoing
            // foreground-service notification.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            // POST_NOTIFICATIONS being denied only mutes the status notification;
            // the BLE permissions are the ones that actually gate advertising.
            val bleGranted = grants
                .filterKeys { it != Manifest.permission.POST_NOTIFICATIONS }
                .all { it.value }
            if (bleGranted) startServing() else showDenied()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 16f
            setPadding(24, 24, 24, 24)
        }
        setContentView(status)
    }

    override fun onStart() {
        super.onStart()
        if (hasAllRequired()) startServing() else permissionLauncher.launch(requiredPermissions)
    }

    private fun hasAllRequired(): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startServing() {
        FuseMindForegroundService.start(this)
        status.text = "FuseMind active\nAdvertising to your phone"
    }

    private fun showDenied() {
        status.text = "Bluetooth permission needed\nGrant it in Settings to connect your phone"
    }
}
