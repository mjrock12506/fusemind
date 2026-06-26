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
 * PHASE 1 (P1-001): the GATT server + advertising are now implemented.
 *   The phone (GATT central) scans for FuseMindGatt.SERVICE, connects, and
 *   reads Capabilities once. Notification UI (Phase 2) and Health Services
 *   (Phase 5) wiring remain TODO and are marked below.
 *
 * NOT hardware-tested yet — see NOTES.md for the on-device checklist.
 */

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.os.PowerManager
import android.util.Log
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

    /** Standard Client Characteristic Configuration Descriptor (CCCD).
     *  The phone writes this to subscribe/unsubscribe to a Notify characteristic. */
    val CCC_DESCRIPTOR: UUID     = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
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
) {
    /** Serialise to the exact JSON the phone expects (docs/ble-protocol.md). */
    fun toJson(): String =
        """{"hasMic":$hasMic,"hasSpeaker":$hasSpeaker,"hasGPS":$hasGPS,""" +
        """"hasHRM":$hasHRM,"maxNotifLength":$maxNotifLength}"""
}

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
 * Wear OS implementation.
 *
 * Phase 1 (this ticket) fills in the BLE GATT server + advertising;
 * Phase 2 wires the notification UI; Phase 5 wires Health Services.
 *
 * @param context an application Context — needed for the BluetoothManager,
 *   the LE advertiser, and the WakeLock. Pass `applicationContext`.
 */
@SuppressLint("MissingPermission") // BLUETOOTH_CONNECT / _ADVERTISE are requested at runtime by the host Activity.
class FuseMindWatchService(private val context: Context) : WatchSideAdapter {

    private val tag = "FuseMindWatch"

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var wakeLock: PowerManager.WakeLock? = null

    /** Centrals (phones) that have subscribed to the Notify characteristics. */
    private val subscribers = mutableSetOf<BluetoothDevice>()

    // ---- Lifecycle ---------------------------------------------------------

    override fun startGattServer(): Boolean {
        val adapter = bluetoothManager.adapter
        if (adapter == null || !adapter.isEnabled) {
            Log.w(tag, "Bluetooth is off or unavailable; cannot start GATT server")
            return false
        }
        if (!adapter.isMultipleAdvertisementSupported) {
            Log.w(tag, "This device does not support BLE advertising")
            return false
        }

        val server = bluetoothManager.openGattServer(context, gattCallback)
        if (server == null) {
            Log.e(tag, "openGattServer returned null")
            return false
        }
        server.addService(buildService())
        gattServer = server

        acquireWakeLock()
        startAdvertising(adapter.bluetoothLeAdvertiser)

        Log.i(tag, "GATT server up; advertising ${FuseMindGatt.SERVICE}")
        return true
    }

    override fun stopGattServer() {
        advertiser?.stopAdvertising(advertiseCallback)
        advertiser = null
        gattServer?.close()
        gattServer = null
        subscribers.clear()
        releaseWakeLock()
        Log.i(tag, "GATT server stopped")
    }

    // ---- Service + characteristic definitions ------------------------------

    /**
     * Build the FuseMind service with all six characteristics from
     * docs/ble-protocol.md, with the exact properties the contract lists.
     * Notify characteristics also carry a CCCD so the phone can subscribe.
     */
    private fun buildService(): BluetoothGattService {
        val service = BluetoothGattService(
            FuseMindGatt.SERVICE,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        // Notification Push — phone -> watch — Write, Notify
        service.addCharacteristic(notifiableWritable(FuseMindGatt.CHAR_NOTIFICATION_PUSH))

        // Notification Dismiss — watch -> phone — Write
        //   NOTE: the contract lists this watch->phone but property "Write".
        //   In Phase 2 the watch must NOTIFY dismissals to the phone; revisit
        //   whether to add NOTIFY here and update docs/ble-protocol.md (see NOTES.md).
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                FuseMindGatt.CHAR_NOTIFICATION_DISMISS,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )
        )

        // Call Control — bidirectional — Write, Notify
        service.addCharacteristic(notifiableWritable(FuseMindGatt.CHAR_CALL_CONTROL))

        // Media Command — watch -> phone — Write  (same NOTE as Dismiss above)
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                FuseMindGatt.CHAR_MEDIA_COMMAND,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )
        )

        // Health Data — watch -> phone — Read, Notify
        val health = BluetoothGattCharacteristic(
            FuseMindGatt.CHAR_HEALTH_DATA,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        health.addDescriptor(cccDescriptor())
        service.addCharacteristic(health)

        // Capabilities — watch -> phone — Read (the phone reads this once on connect)
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                FuseMindGatt.CHAR_CAPABILITIES,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
        )

        return service
    }

    /** A Write+Notify characteristic with the subscribe CCCD attached. */
    private fun notifiableWritable(uuid: UUID): BluetoothGattCharacteristic {
        val c = BluetoothGattCharacteristic(
            uuid,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        c.addDescriptor(cccDescriptor())
        return c
    }

    private fun cccDescriptor() = BluetoothGattDescriptor(
        FuseMindGatt.CCC_DESCRIPTOR,
        BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
    )

    // ---- Advertising -------------------------------------------------------

    private fun startAdvertising(leAdvertiser: BluetoothLeAdvertiser?) {
        if (leAdvertiser == null) {
            Log.e(tag, "No LE advertiser available")
            return
        }
        advertiser = leAdvertiser

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .setTimeout(0) // advertise indefinitely (survives screen-off; backed by the WakeLock)
            .build()

        // Service UUID only — keep the 31-byte advertising payload small.
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(FuseMindGatt.SERVICE))
            .build()

        leAdvertiser.startAdvertising(settings, data, advertiseCallback)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.i(tag, "Advertising started")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(tag, "Advertising failed: $errorCode")
        }
    }

    // ---- GATT server callbacks ---------------------------------------------

    private val gattCallback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED ->
                    Log.i(tag, "Central connected: ${device.address}")
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(tag, "Central disconnected: ${device.address}")
                    subscribers.remove(device)
                    // P1-003: keep advertising so the phone can auto-reconnect.
                }
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            val value: ByteArray = when (characteristic.uuid) {
                FuseMindGatt.CHAR_CAPABILITIES ->
                    capabilities().toJson().toByteArray(Charsets.UTF_8)
                FuseMindGatt.CHAR_HEALTH_DATA ->
                    healthToJson(readHealth()).toByteArray(Charsets.UTF_8)
                else -> ByteArray(0)
            }
            gattServer?.sendResponse(
                device, requestId, android.bluetooth.BluetoothGatt.GATT_SUCCESS, offset,
                value.copyOfRange(offset.coerceAtMost(value.size), value.size)
            )
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            val payload = value.toString(Charsets.UTF_8)
            when (characteristic.uuid) {
                FuseMindGatt.CHAR_NOTIFICATION_PUSH ->
                    Log.d(tag, "Notification push received (${payload.length} bytes) — Phase 2 will render it")
                FuseMindGatt.CHAR_CALL_CONTROL ->
                    Log.d(tag, "Call control received — Phase 3 will handle it")
                else ->
                    Log.d(tag, "Write to ${characteristic.uuid}")
            }
            if (responseNeeded) {
                gattServer?.sendResponse(
                    device, requestId, android.bluetooth.BluetoothGatt.GATT_SUCCESS, offset, null
                )
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            // The phone subscribing/unsubscribing to a Notify characteristic.
            if (descriptor.uuid == FuseMindGatt.CCC_DESCRIPTOR) {
                val subscribe = value.isNotEmpty() &&
                    value[0] == BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE[0]
                if (subscribe) subscribers.add(device) else subscribers.remove(device)
                Log.d(tag, "CCCD ${if (subscribe) "subscribe" else "unsubscribe"} from ${device.address}")
            }
            if (responseNeeded) {
                gattServer?.sendResponse(
                    device, requestId, android.bluetooth.BluetoothGatt.GATT_SUCCESS, offset, null
                )
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            // The phone requests a larger MTU on connect (Phase 2 notification bodies
            // exceed the 23-byte default). Nothing to negotiate server-side; just log.
            Log.i(tag, "MTU changed to $mtu for ${device.address}")
        }
    }

    // ---- Outbound notify helper --------------------------------------------

    /** Push a value to every subscribed central on a Notify characteristic. */
    private fun notifyCharacteristic(uuid: UUID, value: ByteArray) {
        val server = gattServer ?: return
        val characteristic = server.getService(FuseMindGatt.SERVICE)?.getCharacteristic(uuid) ?: return
        characteristic.value = value
        for (device in subscribers) {
            server.notifyCharacteristicChanged(device, characteristic, false)
        }
    }

    // ---- WakeLock (keeps advertising alive through screen-off) --------------

    private fun acquireWakeLock() {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FuseMind:GattAdvertising").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
    }

    // ---- WatchSideAdapter: feature hooks (later phases) ---------------------

    override fun onNotificationReceived(n: WatchNotification) {
        // TODO[Phase 2]: render in a Jetpack Compose notification list,
        //   trigger haptic, respect aiPriority for Do-Not-Disturb behaviour.
    }

    override fun dismissNotification(id: String) {
        // TODO[Phase 2]: NOTIFY `id` on CHAR_NOTIFICATION_DISMISS so the phone
        //   clears the matching notification. (See the property NOTE above.)
        notifyCharacteristic(FuseMindGatt.CHAR_NOTIFICATION_DISMISS, id.toByteArray(Charsets.UTF_8))
    }

    override fun readHealth(): HealthSnapshot {
        // TODO[Phase 5]: query Wear OS Health Services for HR, HRV, steps.
        return HealthSnapshot(heartRate = null, hrv = null, steps = null)
    }

    override fun capabilities(): WatchCapabilities = WatchCapabilities()

    /** Serialise a HealthSnapshot to the protocol JSON (nulls allowed). */
    private fun healthToJson(h: HealthSnapshot): String =
        """{"heartRate":${h.heartRate},"hrv":${h.hrv},"steps":${h.steps},""" +
        """"sourceAdapter":"${h.sourceAdapter}","recordedAt":${h.recordedAt}}"""
}
