package app.fusemind.spike.ancs

/*
 * ANCS feasibility SPIKE — entry screen.
 *
 * Flow: grant Bluetooth permissions → pick the (already-bonded) iPhone from the
 * list → the app connects as a BLE central and tries to read ANCS, logging every
 * step on screen and to logcat (tag "ANCSpike").
 *
 * This is a throwaway probe (ADR-002). It does not touch the production app.
 */

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var deviceContainer: LinearLayout
    private lateinit var logView: TextView
    private lateinit var logScroll: ScrollView
    private var client: AncsGattClient? = null

    /** True while a BLE session is live. Prevents the lifecycle/UI from kicking
     *  off a second connect (the cause of the old connect/disconnect loop). */
    private var sessionActive = false

    private val btManager by lazy { getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }

    private val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants.values.all { it }) refreshDevices()
            else log("✗ Bluetooth permission denied — grant it in Settings to run the spike.")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        log("ANCS feasibility spike. Goal: prove a BLE central can read iPhone ANCS.")
        log("Step 1: pair this device with the iPhone in system Bluetooth settings.")
        log("Step 2: pick the iPhone below.  (Watch logcat with tag 'ANCSpike'.)")
        // Populate the list ONCE here — NOT in onStart(), which re-fires on every
        // foreground/dialog-dismiss and used to churn the BLE session.
        if (hasAllPermissions()) refreshDevices() else permissionLauncher.launch(requiredPermissions)
    }

    override fun onDestroy() {
        client?.close()
        super.onDestroy()
    }

    private fun hasAllPermissions() = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Rebuilds the bonded-device list. Tears down any live session first, so
     *  this doubles as the "reset" action. Only called from onCreate, the
     *  permission grant, and the Refresh button — never from a lifecycle hook. */
    private fun refreshDevices() {
        client?.close()
        sessionActive = false
        deviceContainer.removeAllViews()
        val bonded = try { btManager.adapter?.bondedDevices ?: emptySet() } catch (e: SecurityException) { emptySet() }
        if (bonded.isEmpty()) {
            log("No bonded devices. Pair the iPhone in Bluetooth settings, then tap Refresh.")
            return
        }
        log("Bonded devices (${bonded.size}). Tap the iPhone:")
        for (d in bonded) {
            val name = try { d.name } catch (e: SecurityException) { null } ?: "(unknown)"
            deviceContainer.addView(Button(this).apply {
                text = "$name\n${d.address}"
                setOnClickListener { startSession(d, name) }
            })
        }
    }

    private fun startSession(device: android.bluetooth.BluetoothDevice, name: String) {
        if (sessionActive) {
            log("A session is already active. Tap 'Refresh bonded devices' to reset.")
            return
        }
        sessionActive = true
        setDeviceButtonsEnabled(false) // no second tap can re-enter while we're live
        log("──────── connecting to $name ────────")
        client?.close()
        client = AncsGattClient(
            context = this,
            log = { msg -> runOnUiThread { log(msg) } },
            onEnded = { runOnUiThread { onSessionEnded() } }
        )
        client?.connect(device)
    }

    private fun onSessionEnded() {
        sessionActive = false
        setDeviceButtonsEnabled(true)
    }

    private fun setDeviceButtonsEnabled(enabled: Boolean) {
        for (i in 0 until deviceContainer.childCount) deviceContainer.getChildAt(i).isEnabled = enabled
    }

    private fun log(msg: String) {
        logView.append(msg + "\n")
        logScroll.post { logScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    // ---- Programmatic UI (no XML; this is a spike) -------------------------

    private fun buildUi(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        root.addView(TextView(this).apply {
            text = "ANCS Spike"
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        })
        root.addView(Button(this).apply {
            text = "Refresh bonded devices"
            setOnClickListener { if (hasAllPermissions()) refreshDevices() else permissionLauncher.launch(requiredPermissions) }
        })
        deviceContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(deviceContainer)

        logScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
        }
        logView = TextView(this).apply {
            setTextIsSelectable(true)
            typeface = Typeface.MONOSPACE
            setTextColor(Color.DKGRAY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }
        logScroll.addView(logView)
        root.addView(logScroll)
        return root
    }
}
