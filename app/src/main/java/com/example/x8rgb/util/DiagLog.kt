package com.x8rgb.util

import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Locale

/** Logcat tag for every line this app emits. Filter with: adb logcat -s X8RGB */
const val DIAG_TAG = "X8RGB"

private const val MAX_LINES = 400

/**
 * Logs to logcat AND to a file in the app's private dir, so results can be read
 * without logcat: adb shell run-as com.x8rgb cat files/x8rgb_diag.log
 * Never log notification text or contact names; package names and rule types only.
 */
object DiagLog {
    private val buf = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private var file: File? = null

    /** Call once from the service with a file under filesDir. */
    @Synchronized
    fun attach(f: File) { file = f }

    @Synchronized
    fun log(area: String, msg: String) {
        val line = "${fmt.format(System.currentTimeMillis())} [$area] $msg"
        Log.i(DIAG_TAG, line)
        if (buf.size >= MAX_LINES) buf.pollFirst()
        buf.addLast(line)
        runCatching { file?.appendText(line + "\n") }
    }

    /** Empties the in-memory ring and the file. */
    @Synchronized
    fun clear() {
        buf.clear()
        runCatching { file?.writeText("") }
    }
}