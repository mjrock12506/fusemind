package app.fusemind.wearos

/*
 * FuseMindWatchService.kt
 *
 * The WATCH-SIDE half of FuseMind on Wear OS.
 *
 * Architecture (see docs/ble-protocol.md and docs/adr/ADR-001):
 *   - The iPhone app holds a WatchAdapter (Swift) and is the "brain".
 *   - Every watch platform ships a companion app that speaks FuseMind's
 *     custom GATT protocol. THIS service is that companion for Wear OS.
 *   - It runs a BLE GATT server, exposes the FuseMind characteristics,
 *     receives notifications/commands from the phone, and reports health.
 *
 * One Wear OS codebase here covers Samsung, Pixel Watch, Fossil, TicWatch,
 * Mobvoi — every Wear OS device.
 *
 * Apache-2.0 © FuseMind contributors
 *
 * NOTE: This is a Phase 0 skeleton. Real BLE wiring lands in Phase 1.
 *       TODOs mark exactly where Phase 1 work begins.
 */

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import java.util.UUID

/** FuseMind custom GATT service + characteristic UUIDs.
 *  These MUST match the iOS side exactly. See docs/ble-protocol.md. */
object FuseMindGatt {
    val SERVICE: UUID            = UUID.fromString("F0000000-0000-1000-8000-00805F9B34FB")
    val CHAR_NOTIFICATION_PUSH   = UUID.fromString("F0000001-0000-1000-8000-00805F9B34FB") // phone -> watch
    val CHAR_NOTIFICATION_DISMISS= UUID.fromString("F0000002-0000-1000-8000-00805F9B34FB") // watch -> phone
    val CHAR_CALL_CONTROL        = UUID.fromString("F0000003-0000-1000-8000-00805F9B34FB") // bidirectional
    val CHAR_MEDIA_COMMAND       = UUID.fromString("F0000004-0000-1000-8000-00805F9B34FB") // watch -> phone
    val CHAR_HEALTH_DATA         = UUID.fromString("F0000005-0000-1000-8000-00805F9B34FB") // watch -> phone
    val CHAR_CAPABILITIES        = UUID.fromString("F0000006-0000-1000-8000-00805F9B34FB") // watch -> phone
}

/** Notification as received from the phone, mirrors WatchNotification (Swift). */
data class WatchNotification(
    val id: String,
    val appName: String,
    val title: String,
    val body: String,
    val aiPriority: String,   // "H" | "M" | "L"
    val timestamp: Long
)

/** Health snapshot read from Wear OS Health Services, mirrors HealthSnapshot. */
data class HealthSnapshot(
    val heartRate: Int?,
    val hrv: Double?,
    val steps: Int?,
    val sourceAdapter: String = "wear_os",
    val recordedAt: Long = System.currentTimeMillis()
)

/** What this watch can do. Reported to the phone so it can degrade gracefully. */
data class WatchCapabilities(
    val hasMic: Boolean = true,
    val hasSpeaker: Boolean = true,
    val hasGPS: Boolean = true,
    val hasHRM: Boolean = true,
    val maxNotifLength: Int = 120
)

/**
 * Watch-side contract. The Wear OS app implements this; other platforms
 * (Garmin/Monkey C, Amazfit/JS) implement the equivalent in their language.
 */
interface WatchSideAdapter {
    fun startGattServer(): Boolean
    fun stopGattServer()
    fun onNotificationReceived(n: WatchNotification)
    fun dismissNotification(id: String)
    fun readHealth(): HealthSnapshot
    fun capabilities(): WatchCapabilities
}

/**
 * Wear OS implementation skeleton.
 * Phase 1 fills in the BLE GATT server; Phase 2 wires notification UI;
 * Phase 5 wires Health Services.
 */
class FuseMindWatchService : WatchSideAdapter {

    private var gattServer: BluetoothGattServer? = null

    override fun startGattServer(): Boolean {
        // TODO[Phase 1]: open BluetoothGattServer, register FuseMindGatt.SERVICE
        //   with all six characteristics, begin advertising the service UUID,
        //   hold a WakeLock so advertising survives screen timeout.
        return false
    }

    override fun stopGattServer() {
        // TODO[Phase 1]: close server, stop advertising, release WakeLock.
        gattServer?.close()
        gattServer = null
    }

    override fun onNotificationReceived(n: WatchNotification) {
        // TODO[Phase 2]: render in a Jetpack Compose notification list,
        //   trigger haptic, respect aiPriority for Do-Not-Disturb behaviour.
    }

    override fun dismissNotification(id: String) {
        // TODO[Phase 2]: write `id` to CHAR_NOTIFICATION_DISMISS so the
        //   phone clears the matching notification.
    }

    override fun readHealth(): HealthSnapshot {
        // TODO[Phase 5]: query Wear OS Health Services for HR, HRV, steps.
        return HealthSnapshot(heartRate = null, hrv = null, steps = null)
    }

    override fun capabilities(): WatchCapabilities = WatchCapabilities()

    private fun notifyCharacteristic(char: BluetoothGattCharacteristic, value: ByteArray) {
        // TODO[Phase 1]: gattServer.notifyCharacteristicChanged(...)
    }
}
