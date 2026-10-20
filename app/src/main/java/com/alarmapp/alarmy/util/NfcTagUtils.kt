package com.alarmapp.alarmy.util

import android.nfc.Tag
import java.util.Locale

/** Utilities shared by NFC setup and alarm dismissal. */
object NfcTagUtils {
    /**
     * Returns the tag's low-level identifier in a stable, human-readable form.
     * Android exposes the identifier as bytes; normalizing to uppercase hex avoids
     * differences caused by locale or byte-array formatting.
     */
    fun canonicalId(tag: Tag): String = tag.id.toHexString()

    fun ByteArray.toHexString(): String = joinToString(separator = "") { byte ->
        "%02X".format(Locale.ROOT, byte.toInt() and 0xFF)
    }
}
