package com.x8rgb.util

import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Locale

object DiagLog {
    private val buf = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private var file: File? = null

    @Synchronized
    fun attach(f: File) { file = f }

    @Synchronized
    fun log(area: String, msg: String) {
        val line = "${fmt.format(System.currentTimeMillis())} [$area] $msg"
        Log.i("X8RGB", line)
        if (buf.size >= 400) buf.pollFirst()
        buf.addLast(line)
        runCatching { file?.appendText(line + "\n") }
    }

    @Synchronized
    fun clear() {
        buf.clear()
        runCatching { file?.writeText("") }
    }
}
