package com.x8rgb.system

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Monitors device movement via accelerometer and calculates a 0..1 intensity value.
 * Used for "Motion Reactive" ring effect.
 */
@Singleton
class MotionManager @Inject constructor(
    @ApplicationContext private val context: Context
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _motionIntensity = MutableStateFlow(0f)
    val motionIntensity: StateFlow<Float> = _motionIntensity

    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var currentZ = 0f
    private var active = false

    fun start() {
        if (active) return
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            active = true
        }
    }

    fun stop() {
        if (!active) return
        sensorManager.unregisterListener(this)
        _motionIntensity.value = 0f
        active = false
    }

    /** Returns true if the device is screen-down (Z axis is negative). */
    fun isFaceDown(): Boolean {
        return currentZ < -7.0f
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            currentZ = z

            // Calculate change in acceleration (jerk/motion)
            val delta = sqrt((x - lastX) * (x - lastX) + (y - lastY) * (y - lastY) + (z - lastZ) * (z - lastZ))
            
            // Low-pass filter to smooth the intensity signal
            val alpha = 0.2f
            val target = (delta * 0.15f).coerceIn(0f, 1f)
            _motionIntensity.value = _motionIntensity.value * (1 - alpha) + target * alpha

            lastX = x
            lastY = y
            lastZ = z
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
