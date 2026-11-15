package com.x8rgb

import android.app.Application
import com.x8rgb.system.MiuiLightsBinder
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class X8RGBApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize the light binder with our own package name to avoid being blocked.
        MiuiLightsBinder.callerPackage = packageName
    }
}
