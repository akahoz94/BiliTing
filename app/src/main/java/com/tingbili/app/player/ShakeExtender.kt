package com.tingbili.app.player

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * 摇一摇延长睡眠定时。只在睡眠定时激活期间由 PlayerHolder start()/stop()。
 *
 * 标准 shake 检测：加速度向量模去掉重力后的瞬时冲击超过阈值，且距上次触发超过冷却时间。
 * 没有传感器（部分模拟器/电视）时 start/stop 都是空操作，不崩。
 */
class ShakeExtender(
    context: Context,
    private val onShake: () -> Unit
) : SensorEventListener {

    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    @Volatile private var lastTriggerMs = 0L

    fun start() {
        runCatching {
            sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        runCatching { sensorManager?.unregisterListener(this) }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        // 去掉重力分量后的瞬时加速度冲击
        val magnitude = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH
        val now = System.currentTimeMillis()
        if (magnitude > THRESHOLD && now - lastTriggerMs > COOLDOWN_MS) {
            lastTriggerMs = now
            onShake()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        /** 冲击阈值，m/s²（普通持握晃动不会触发，用力摇一下才会） */
        private const val THRESHOLD = 15f
        /** 冷却：一次摇动手势只触发一次 */
        private const val COOLDOWN_MS = 2_000L
    }
}
