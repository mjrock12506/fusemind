package app.fusemind.spike.ancs

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.UUID

/**
 * The feasibility probe: connect to a bonded iPhone as a BLE central, discover
 * ANCS, subscribe to the Notification Source (+ Data Source), and log every step.
 *
 * GATT operations are SERIALIZED through a small queue (Android allows only one
 * in flight at a time). Without it, the Control Point write for "Get Notification
 * Attributes" collided with other ops and silently failed — so app/title/message
 * never arrived. Each completion callback releases the queue for the next op.
 *
 * Everything is best-effort and verbose on purpose — this is a throwaway spike.
 */
@SuppressLint("MissingPermission") // BLUETOOTH_CONNECT is requested at runtime by MainActivity.
class AncsGattClient(
    private val context: Context,
    /** Sink for human-readable progress, mirrored to the on-screen log. */
    private val log: (String) -> Unit,
    /** Called once when the session ends (disconnect or close) so the UI can
     *  re-enable the device buttons. Invoked at most once per session. */
    private val onEnded: () -> Unit = {}
) {
    private val tag = "ANCSpike"
    private var gatt: BluetoothGatt? = null

    /** Guards against re-entrant connect()/teardown — the root of the old loop.
     *  @Volatile: set on the UI thread, read on the BLE binder thread. */
    @Volatile private var active = false

    private val handler = Handler(Looper.getMainLooper())

    // ---- Serial GATT operation queue ---------------------------------------

    /** One queued GATT op. `run` performs the actual gatt.* call and returns the
     *  boolean it reports (true = started; its callback will complete it). */
    private class Op(val label: String, val run: (BluetoothGatt) -> Boolean)

    private val opLock = Any()
    private val opQueue = ArrayDeque<Op>()
    private var opInProgress = false

    private fun enqueue(label: String, run: (BluetoothGatt) -> Boolean) {
        synchronized(opLock) { opQueue.addLast(Op(label, run)) }
        drain()
    }

    /** Start the next op if the bus is free. Repeats past ops that fail to start. */
    private fun drain() {
        synchronized(opLock) {
            if (opInProgress) return
            val g = gatt ?: return
            val op = opQueue.removeFirstOrNull() ?: return
            opInProgress = true
            val started = op.run(g)
            if (!started) {
                emit("   (op '${op.label}' did not start; skipping)")
                opInProgress = false
                // Drain the rest outside this frame to avoid deep recursion.
                handler.post { drain() }
            }
        }
    }

    /** Called from each completion callback to free the bus for the next op. */
    private fun completeOp() {
        synchronized(opLock) { opInProgress = false }
        drain()
    }

    private fun clearQueue() {
        synchronized(opLock) {
            opQueue.clear()
            opInProgress = false
        }
    }

    // ---- Lifecycle ----------------------------------------------------------

    fun connect(device: BluetoothDevice) {
        if (active) {
            emit("Connect ignored — a session is already active (no re-entry).")
            return
        }
        active = true
        emit("Bond state: ${bondName(device.bondState)} (${device.address})")
        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            emit("⚠ Device is NOT bonded. ANCS needs an encrypted/bonded link.")
            emit("   Pair the iPhone in system Bluetooth settings first, then retry.")
        }
        emit("Connecting GATT as central…")
        gatt = device.connectGatt(context, /* autoConnect = */ false, callback)
    }

    /** Explicit teardown (Refresh button / onDestroy). Idempotent. Does NOT
     *  fire onEnded — the caller initiated it. */
    fun close() {
        val g = gatt ?: run { active = false; return }
        active = false
        gatt = null
        clearQueue()
        g.disconnect()
        g.close()
        emit("GATT closed")
    }

    fun isActive(): Boolean = active

    // ---- GATT callbacks -----------------------------------------------------

    private val callback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            emit("onConnectionStateChange status=$status newState=${stateName(newState)}")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    emit("Connected. Queuing MTU + service discovery…")
                    // Queue both; the queue serializes them (MTU completes on
                    // onMtuChanged, discovery on onServicesDiscovered).
                    enqueue("requestMtu") { it.requestMtu(185) }
                    enqueue("discoverServices") { it.discoverServices() }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    emit("Disconnected (status=$status). If this happened right after a " +
                        "subscribe, iOS may have rejected an unbonded/unauthorised read.")
                    clearQueue()
                    g.close()
                    val wasActive = active
                    active = false
                    gatt = null
                    if (wasActive) {
                        emit("Session ended. To retry: tap 'Refresh bonded devices', then the iPhone.")
                        onEnded()
                    }
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            emit("MTU=$mtu (status=$status)")
            completeOp()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            emit("onServicesDiscovered status=$status; ${g.services.size} services:")
            for (s in g.services) emit("   • ${s.uuid}")
            completeOp()

            val ancs = g.getService(Ancs.SERVICE)
            if (ancs == null) {
                emit("✗ ANCS service NOT present on this device.")
                emit("   Likely: iOS didn't expose ANCS to this consumer (no notification")
                emit("   sharing granted), or this isn't the iPhone. See README §Outcomes.")
                return
            }
            emit("✓ ANCS service FOUND. Subscribing to Notification Source + Data Source…")
            enqueue("subscribe NotificationSource") { subscribeOp(it, Ancs.NOTIFICATION_SOURCE, "NotificationSource") }
            enqueue("subscribe DataSource") { subscribeOp(it, Ancs.DATA_SOURCE, "DataSource") }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            val charUuid = descriptor.characteristic.uuid
            emit("CCCD written for ${shortName(charUuid)} status=$status")
            if (charUuid == Ancs.DATA_SOURCE) {
                emit("✓ Subscribed. Waiting for notifications — lock the iPhone and send")
                emit("   yourself a text / WhatsApp / Mail. Each should appear below.")
            }
            completeOp()
        }

        // API < 33 delivers notifications here…
        @Deprecated("Deprecated in API 33")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            handleChanged(c.uuid, c.value ?: ByteArray(0))
        }

        // …API 33+ delivers them here (with an explicit value).
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            handleChanged(c.uuid, value)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (c.uuid == Ancs.CONTROL_POINT) emit("Control Point write status=$status")
            completeOp()
        }
    }

    // ---- Handlers (not part of the op queue) -------------------------------

    /** Notifications are asynchronous, NOT responses to queued ops. */
    private fun handleChanged(uuid: UUID, value: ByteArray) {
        when (uuid) {
            Ancs.NOTIFICATION_SOURCE -> {
                val n = Ancs.parseSource(value)
                if (n == null) { emit("NOTIFICATION: <unparseable ${value.size} bytes>"); return }
                emit("NOTIFICATION: $n")
                // Fetch attributes for freshly Added notifications, via the queue.
                if (n.eventId == 0) enqueueGetAttributes(n.rawUid, attempt = 1)
            }
            Ancs.DATA_SOURCE -> {
                val attrs = Ancs.parseAttributes(value)
                if (attrs.isEmpty()) emit("   (data source: ${value.size} bytes, no attrs parsed)")
                else attrs.forEach { emit("   $it") }
            }
        }
    }

    /** Queue a "Get Notification Attributes" Control Point write, with bounded retry. */
    @Suppress("DEPRECATION")
    private fun enqueueGetAttributes(rawUid: ByteArray, attempt: Int) {
        val maxAttempts = 3
        // Build the command now, but apply it to the characteristic at EXECUTION
        // time so concurrently-queued writes for different UIDs can't clobber it.
        val command = Ancs.buildGetAttributes(rawUid)
        enqueue("getAttrs attempt=$attempt") { g ->
            val cp = g.getService(Ancs.SERVICE)?.getCharacteristic(Ancs.CONTROL_POINT)
            if (cp == null) { emit("   (Control Point characteristic missing)"); return@enqueue false }
            cp.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            cp.value = command
            val ok = g.writeCharacteristic(cp)
            if (!ok && attempt < maxAttempts) {
                emit("   (Control Point write busy; retrying ${attempt + 1}/$maxAttempts)")
                handler.postDelayed({ enqueueGetAttributes(rawUid, attempt + 1) }, 150)
            } else if (!ok) {
                emit("   (Control Point write failed after $maxAttempts attempts)")
            }
            ok
        }
    }

    @Suppress("DEPRECATION")
    private fun subscribeOp(g: BluetoothGatt, charUuid: UUID, label: String): Boolean {
        val c = g.getService(Ancs.SERVICE)?.getCharacteristic(charUuid)
            ?: run { emit("✗ $label characteristic missing"); return false }
        g.setCharacteristicNotification(c, true)
        val cccd = c.getDescriptor(Ancs.CCCD)
            ?: run { emit("✗ $label has no CCCD; cannot subscribe"); return false }
        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        return g.writeDescriptor(cccd)
    }

    // ---- Naming helpers -----------------------------------------------------

    private fun shortName(u: UUID) = when (u) {
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
