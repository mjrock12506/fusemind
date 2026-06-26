package app.fusemind.spike.ancs

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log

/**
 * The feasibility probe: connect to a bonded iPhone as a BLE central, discover
 * ANCS, subscribe to the Notification Source (+ Data Source), and log every step.
 *
 * Success = we see "ANCS service FOUND" and then "NOTIFICATION:" lines when the
 * iPhone receives notifications. Failure modes are logged explicitly (no bond,
 * ANCS absent, no notifications) so the outcome is unambiguous.
 *
 * Everything is best-effort and verbose on purpose — this is a throwaway spike.
 */
@SuppressLint("MissingPermission") // BLUETOOTH_CONNECT is requested at runtime by MainActivity.
class AncsGattClient(
    private val context: Context,
    /** Sink for human-readable progress, mirrored to the on-screen log. */
    private val log: (String) -> Unit
) {
    private val tag = "ANCSpike"
    private var gatt: BluetoothGatt? = null

    fun connect(device: BluetoothDevice) {
        emit("Bond state: ${bondName(device.bondState)} (${device.address})")
        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            emit("⚠ Device is NOT bonded. ANCS needs an encrypted/bonded link.")
            emit("   Pair the iPhone in system Bluetooth settings first, then retry.")
        }
        emit("Connecting GATT as central…")
        gatt = device.connectGatt(context, /* autoConnect = */ false, callback)
    }

    fun close() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        emit("GATT closed")
    }

    private val callback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            emit("onConnectionStateChange status=$status newState=${stateName(newState)}")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    emit("Connected. Requesting MTU 185…")
                    if (!g.requestMtu(185)) {
                        emit("requestMtu returned false; discovering services directly")
                        g.discoverServices()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    emit("Disconnected (status=$status). If this happened right after a " +
                        "subscribe, iOS may have rejected an unbonded/unauthorised read.")
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            emit("MTU=$mtu (status=$status). Discovering services…")
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            emit("onServicesDiscovered status=$status; ${g.services.size} services:")
            for (s in g.services) emit("   • ${s.uuid}")

            val ancs = g.getService(Ancs.SERVICE)
            if (ancs == null) {
                emit("✗ ANCS service NOT present on this device.")
                emit("   Likely: iOS didn't expose ANCS to this consumer (no notification")
                emit("   sharing granted), or this isn't the iPhone. See README §Outcomes.")
                return
            }
            emit("✓ ANCS service FOUND. Subscribing to Notification Source…")
            subscribe(g, ancs.getCharacteristic(Ancs.NOTIFICATION_SOURCE), "NotificationSource")
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            val charUuid = descriptor.characteristic.uuid
            emit("CCCD written for ${shortName(charUuid)} status=$status")
            // Chain: after Notification Source is subscribed, subscribe Data Source.
            if (charUuid == Ancs.NOTIFICATION_SOURCE) {
                val ds = g.getService(Ancs.SERVICE)?.getCharacteristic(Ancs.DATA_SOURCE)
                subscribe(g, ds, "DataSource")
            } else if (charUuid == Ancs.DATA_SOURCE) {
                emit("✓ Subscribed. Waiting for notifications — lock the iPhone and send")
                emit("   yourself a text / WhatsApp / Mail. Each should appear below.")
            }
        }

        // API < 33 delivers notifications here…
        @Deprecated("Deprecated in API 33")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            handleChanged(g, c.uuid, c.value ?: ByteArray(0))
        }

        // …API 33+ delivers them here (with an explicit value).
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            handleChanged(g, c.uuid, value)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (c.uuid == Ancs.CONTROL_POINT) emit("Control Point write status=$status")
        }
    }

    private fun handleChanged(g: BluetoothGatt, uuid: java.util.UUID, value: ByteArray) {
        when (uuid) {
            Ancs.NOTIFICATION_SOURCE -> {
                val n = Ancs.parseSource(value)
                if (n == null) { emit("NOTIFICATION: <unparseable ${value.size} bytes>"); return }
                emit("NOTIFICATION: $n")
                // Only fetch attributes for freshly Added notifications, to keep noise down.
                if (n.eventId == 0) requestAttributes(g, n.rawUid)
            }
            Ancs.DATA_SOURCE -> {
                val attrs = Ancs.parseAttributes(value)
                if (attrs.isEmpty()) emit("   (data source: ${value.size} bytes, no attrs parsed)")
                else attrs.forEach { emit("   $it") }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun requestAttributes(g: BluetoothGatt, rawUid: ByteArray) {
        val cp = g.getService(Ancs.SERVICE)?.getCharacteristic(Ancs.CONTROL_POINT) ?: return
        cp.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        cp.value = Ancs.buildGetAttributes(rawUid)
        val ok = g.writeCharacteristic(cp)
        if (!ok) emit("   (could not write Control Point — another GATT op may be in flight)")
    }

    @Suppress("DEPRECATION")
    private fun subscribe(g: BluetoothGatt, c: BluetoothGattCharacteristic?, label: String) {
        if (c == null) { emit("✗ $label characteristic missing"); return }
        g.setCharacteristicNotification(c, true)
        val cccd = c.getDescriptor(Ancs.CCCD)
        if (cccd == null) { emit("✗ $label has no CCCD; cannot subscribe"); return }
        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        val ok = g.writeDescriptor(cccd)
        emit("Subscribe $label → writeDescriptor=$ok")
    }

    private fun shortName(u: java.util.UUID) = when (u) {
        Ancs.NOTIFICATION_SOURCE -> "NotificationSource"
        Ancs.DATA_SOURCE -> "DataSource"
        else -> u.toString()
    }

    private fun bondName(s: Int) = when (s) {
        BluetoothDevice.BOND_BONDED -> "BONDED"
        BluetoothDevice.BOND_BONDING -> "BONDING"
        else -> "NOT BONDED"
    }

    private fun stateName(s: Int) = when (s) {
        BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
        BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
        BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
        else -> "DISCONNECTED"
    }

    private fun emit(msg: String) {
        Log.i(tag, msg)
        log(msg)
    }
}
