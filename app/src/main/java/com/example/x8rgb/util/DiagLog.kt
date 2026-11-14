package com.x8rgb.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Locale

/** Logcat tag for every line this app emits. Filter with: adb logcat -s X8RGB */
const val DIAG_TAG = "X8RGB"

private const val MAX_LINES = 400

/**
 * Logs each line to logcat and keeps the last MAX_LINES in memory for the dump receiver.
 * Never log notification text or contact names; package names and rule types only.
 */
object DiagLog {
    private val buf = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun log(area: String, msg: String) {
        val line = "${fmt.format(System.currentTimeMillis())} [$area] $msg"
        Log.i(DIAG_TAG, line)
        if (buf.size >= MAX_LINES) buf.pollFirst()
        buf.addLast(line)
    }

    @Synchronized
    fun clear() = buf.clear()
}
