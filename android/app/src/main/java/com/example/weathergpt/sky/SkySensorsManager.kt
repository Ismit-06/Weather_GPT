package com.example.weathergpt.sky

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2
import kotlin.math.sqrt

data class SensorOrientationData(
    val pitchDeg: Float = 0f,          // ~90° straight up, 0° level horizon, -90° straight down
    val rollDeg: Float = 0f,
    val angularSpeedRadS: Float = 0f,  // current camera rotational velocity
    val isStable: Boolean = true,
    val stabilityScore: Float = 1.0f   // 0.0 (erratic motion) to 1.0 (perfectly still)
)

class SkySensorsManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val rotationVector: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val _sensorData = MutableStateFlow(SensorOrientationData())
    val sensorData: StateFlow<SensorOrientationData> = _sensorData.asStateFlow()

    private var gravityValues = FloatArray(3)
    private var lastGyroSpeed = 0f

    fun start() {
        val sm = sensorManager ?: return
        rotationVector?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        } ?: run {
            accelerometer?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        }
        gyroscope?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                val rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                val orientation = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientation)
                // orientation[1] is pitch in radians: -pi/2 (up) to +pi/2 (down) depending on coordinate system
                // We normalize pitch so pointing straight up to sky/ceiling is +90°, level is 0°, down is -90°
                val pitch = -Math.toDegrees(orientation[1].toDouble()).toFloat()
                val roll = Math.toDegrees(orientation[2].toDouble()).toFloat()
                updateOrientation(pitch, roll)
            }

            Sensor.TYPE_ACCELEROMETER -> {
                // Fallback if rotation vector is unavailable
                gravityValues = event.values.clone()
                val ax = gravityValues[0]
                val ay = gravityValues[1]
                val az = gravityValues[2]
                // Pitch relative to phone screen normal
                val pitch = Math.toDegrees(atan2(-ay.toDouble(), sqrt((ax * ax + az * az).toDouble()))).toFloat()
                val roll = Math.toDegrees(atan2(ax.toDouble(), az.toDouble())).toFloat()
                updateOrientation(pitch, roll)
            }

            Sensor.TYPE_GYROSCOPE -> {
                val gx = event.values[0]
                val gy = event.values[1]
                val gz = event.values[2]
                val speed = sqrt(gx * gx + gy * gy + gz * gz)
                lastGyroSpeed = speed

                val isStable = speed < 1.1f
                val stabilityScore = (1.0f - (speed / 2.5f)).coerceIn(0.1f, 1.0f)

                _sensorData.value = _sensorData.value.copy(
                    angularSpeedRadS = speed,
                    isStable = isStable,
                    stabilityScore = stabilityScore
                )
            }
        }
    }

    private fun updateOrientation(pitch: Float, roll: Float) {
        _sensorData.value = _sensorData.value.copy(
            pitchDeg = pitch,
            rollDeg = roll
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
