package app.fusemind.spike.ancs

import java.util.UUID

/**
 * Apple Notification Center Service constants + tiny parsers.
 * Spec: Apple "ANCS Specification". UUIDs are fixed by Apple.
 *
 * Roles: the iPhone is the GATT *server* (Notification Provider); THIS app is
 * the GATT *client* / consumer. The characteristics require an encrypted
 * (bonded) link, which is exactly what we're testing the feasibility of.
 */
object Ancs {
    val SERVICE: UUID             = UUID.fromString("7905F431-B5CE-4E99-A40F-4B1E122D00D0")
    val NOTIFICATION_SOURCE: UUID = UUID.fromString("9FBF120D-6301-42D9-8C58-25E699A21DBD") // Notify
    val CONTROL_POINT: UUID       = UUID.fromString("69D1D8F3-45E1-49A8-9821-9BBDFDAAD9D9") // Write
    val DATA_SOURCE: UUID         = UUID.fromString("22EAC6E9-24D6-4BB5-BE44-B36ACE7C7BFB") // Notify

    /** Standard Client Characteristic Configuration Descriptor (subscribe). */
    val CCCD: UUID                = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

    // Notification Source EventID (byte 0)
    fun eventName(id: Int) = when (id) {
        0 -> "Added"; 1 -> "Modified"; 2 -> "Removed"; else -> "Event$id"
    }

    // Notification Source CategoryID (byte 2)
    fun categoryName(id: Int) = when (id) {
        0 -> "Other"; 1 -> "IncomingCall"; 2 -> "MissedCall"; 3 -> "Voicemail"
        4 -> "Social"; 5 -> "Schedule"; 6 -> "Email"; 7 -> "News"
        8 -> "HealthAndFitness"; 9 -> "BusinessAndFinance"; 10 -> "Location"
        11 -> "Entertainment"; else -> "Category$id"
    }

    // Control Point CommandID
    const val CMD_GET_NOTIFICATION_ATTRIBUTES = 0x00

    // Attribute IDs we ask for
    const val ATTR_APP_IDENTIFIER = 0x00 // no length param
    const val ATTR_TITLE          = 0x01 // needs 2-byte max length
    const val ATTR_MESSAGE        = 0x03 // needs 2-byte max length

    /** An 8-byte Notification Source tuple. */
    data class SourceNotification(
        val eventId: Int,
        val eventFlags: Int,
        val categoryId: Int,
        val categoryCount: Int,
        val uid: Int,        // 32-bit, little-endian
        val rawUid: ByteArray // the 4 UID bytes, to echo back to Control Point
    ) {
        override fun toString() =
            "${eventName(eventId)} ${categoryName(categoryId)} " +
            "uid=$uid flags=0x%02X count=$categoryCount".format(eventFlags)
    }

    /** Parse the 8-byte Notification Source payload. */
    fun parseSource(b: ByteArray): SourceNotification? {
        if (b.size < 8) return null
        val uid = (b[4].toInt() and 0xFF) or
                  ((b[5].toInt() and 0xFF) shl 8) or
                  ((b[6].toInt() and 0xFF) shl 16) or
                  ((b[7].toInt() and 0xFF) shl 24)
        return SourceNotification(
            eventId = b[0].toInt() and 0xFF,
            eventFlags = b[1].toInt() and 0xFF,
            categoryId = b[2].toInt() and 0xFF,
            categoryCount = b[3].toInt() and 0xFF,
            uid = uid,
            rawUid = b.copyOfRange(4, 8)
        )
    }

    /**
     * Build a "Get Notification Attributes" command for the Control Point:
     * asks for AppIdentifier, Title (<=maxLen), Message (<=maxLen).
     * Layout: 0x00, uid[4 LE], 0x00, 0x01, len[2 LE], 0x03, len[2 LE].
     */
    fun buildGetAttributes(rawUid: ByteArray, maxLen: Int = 256): ByteArray {
        val lenLo = (maxLen and 0xFF).toByte()
        val lenHi = ((maxLen shr 8) and 0xFF).toByte()
        return byteArrayOf(
            CMD_GET_NOTIFICATION_ATTRIBUTES.toByte(),
            rawUid[0], rawUid[1], rawUid[2], rawUid[3],
            ATTR_APP_IDENTIFIER.toByte(),
            ATTR_TITLE.toByte(), lenLo, lenHi,
            ATTR_MESSAGE.toByte(), lenLo, lenHi
        )
    }

    /**
     * Parse a Data Source attribute response into readable "id: value" lines.
     * Layout: CommandID(1), uid[4], then repeating {AttrID(1), len[2 LE], value[len]}.
     * Returns human-readable strings; best-effort (Data Source may fragment across
     * multiple notifications for long messages — the spike logs what it gets).
     */
    fun parseAttributes(b: ByteArray): List<String> {
        val out = mutableListOf<String>()
        if (b.size < 5) return out
        var i = 5 // skip CommandID + 4-byte UID
        while (i + 3 <= b.size) {
            val attrId = b[i].toInt() and 0xFF
            val len = (b[i + 1].toInt() and 0xFF) or ((b[i + 2].toInt() and 0xFF) shl 8)
            i += 3
            if (i + len > b.size) break
            val value = String(b.copyOfRange(i, i + len), Charsets.UTF_8)
            i += len
            val name = when (attrId) {
                ATTR_APP_IDENTIFIER -> "app"
                ATTR_TITLE -> "title"
                ATTR_MESSAGE -> "message"
                else -> "attr$attrId"
            }
            out.add("$name: $value")
        }
        return out
    }
}
