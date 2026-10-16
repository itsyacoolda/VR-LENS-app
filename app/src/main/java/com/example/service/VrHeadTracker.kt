package com.example.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import com.example.data.VrPreferencesRepository
import com.example.model.VrSettings
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-performance 3DoF (Degrees of Freedom) Head Tracker for VR Overlay.
 *
 * Utilizes hardware sensors (Game Rotation Vector / Rotation Vector / Gyroscope + Accelerometer fusion)
 * to detect head orientation (Yaw, Pitch, Roll) with:
 * - Sub-millisecond latency via dedicated background HandlerThread.
 * - Adaptive Exponential Moving Average (EMA) smoothing for rock-solid stability and zero motion sickness.
 * - Dynamic deadband filtering to eliminate thermal sensor micro-tremor when looking stationary.
 * - Automatic landscape/portrait display coordinate remapping.
 * - Instant one-tap Recenter calibration.
 */
class VrHeadTracker(
    private val context: Context,
    private val onHeadPoseChanged: (yaw: Float, pitch: Float, roll: Float) -> Unit
) : SensorEventListener {

    private val tag = "VrHeadTracker"
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefsRepo = VrPreferencesRepository.getInstance(context)

    private var handlerThread: HandlerThread? = null
    private var sensorHandler: Handler? = null

    // Sensors
    private var activeSensor: Sensor? = null
    private var sensorName: String = "Detecting Sensors..."
    private var isUsingDirectFusion = false
    private var gyroSensor: Sensor? = null
    private var accelSensor: Sensor? = null

    @Volatile
    private var isTracking = false

    // Raw and reference orientation in radians
    private var rawYaw = 0f
    private var rawPitch = 0f
    private var rawRoll = 0f

    private var refYaw = 0f
    private var refPitch = 0f
    private var refRoll = 0f

    // Filtered / smoothed output values
    @Volatile
    private var smoothedYaw = 0f
    @Volatile
    private var smoothedPitch = 0f
    @Volatile
    private var smoothedRoll = 0f

    // Direct complementary fusion state (for devices lacking hardware rotation vector)
    private var lastGyroTimestamp = 0L
    private var fusionPitch = 0f
    private var fusionRoll = 0f
    private var fusionYaw = 0f

    // Matrix buffers (re-used to eliminate GC allocations during 120Hz-200Hz sensor cycles)
    private val rotMatrix = FloatArray(16)
    private val remappedMatrix = FloatArray(16)
    private val orientationAngles = FloatArray(3)

    // UI telemetry rate limiter (~20 Hz)
    private var lastTelemetryDispatchTime = 0L

    fun start() {
        if (isTracking) return

        // Launch background HandlerThread for zero-jank sensor updates
        handlerThread = HandlerThread("VrHeadTrackerThread", android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY).apply {
            start()
            sensorHandler = Handler(looper)
        }

        val handler = sensorHandler

        // 1. Try Game Rotation Vector (uncalibrated gyro + accel; no magnetic drift; lowest latency)
        var sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        var name = "Game Rotation Vector (Gyro + Accel Fusion)"

        // 2. Fallback to standard Rotation Vector
        if (sensor == null) {
            sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            name = "Rotation Vector (6-Axis IMU)"
        }

        if (sensor != null) {
            activeSensor = sensor
            sensorName = name
            isUsingDirectFusion = false
            sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_FASTEST,
                handler
            )
            Log.i(tag, "Head tracking started with sensor: $name")
        } else {
            // 3. Fallback to direct Gyroscope + Accelerometer complementary fusion
            gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
            accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (gyroSensor != null && accelSensor != null) {
                isUsingDirectFusion = true
                sensorName = "Dual IMU Fusion (Gyroscope + Accelerometer)"
                sensorManager.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_FASTEST, handler)
                sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_FASTEST, handler)
                Log.i(tag, "Head tracking started with direct Gyro + Accel fusion")
            } else {
                sensorName = "No Gyroscope Hardware Found"
                Log.w(tag, "No suitable gyroscope/rotation sensors found for head tracking")
            }
        }

        isTracking = true
        recenter()
    }

    fun stop() {
        if (!isTracking) return
        isTracking = false
        try {
            sensorManager.unregisterListener(this)
        } catch (e: Exception) {
            Log.w(tag, "Error unregistering sensors: ${e.message}")
        }
        handlerThread?.quitSafely()
        handlerThread = null
        sensorHandler = null
        Log.i(tag, "Head tracking stopped")
    }

    /**
     * Recenters the forward-facing reference direction to wherever the user is currently looking.
     */
    fun recenter() {
        refYaw = rawYaw
        refPitch = rawPitch
        refRoll = rawRoll
        smoothedYaw = 0f
        smoothedPitch = 0f
        smoothedRoll = 0f
        onHeadPoseChanged(0f, 0f, 0f)
        prefsRepo.updateHeadOrientation(0f, 0f, 0f, sensorName)
        Log.i(tag, "Head tracking reference recentered (yaw=$refYaw, pitch=$refPitch, roll=$refRoll)")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isTracking) return

        when (event.sensor.type) {
            Sensor.TYPE_GAME_ROTATION_VECTOR, Sensor.TYPE_ROTATION_VECTOR -> {
                processRotationVectorEvent(event)
            }
            Sensor.TYPE_GYROSCOPE -> {
                if (isUsingDirectFusion) {
                    processGyroscopeEvent(event)
                }
            }
            Sensor.TYPE_ACCELEROMETER -> {
                if (isUsingDirectFusion) {
                    processAccelerometerEvent(event)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun processRotationVectorEvent(event: SensorEvent) {
        try {
            SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)

            // Remap coordinate system based on display orientation (Landscape vs Portrait)
            val displayRotation = try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    context.display?.rotation ?: Surface.ROTATION_90
                } else {
                    @Suppress("DEPRECATION")
                    windowManager.defaultDisplay.rotation
                }
            } catch (_: Exception) {
                Surface.ROTATION_90
            }

            when (displayRotation) {
                Surface.ROTATION_90 -> {
                    // Headset landscape orientation (standard)
                    SensorManager.remapCoordinateSystem(
                        rotMatrix,
                        SensorManager.AXIS_Y,
                        SensorManager.AXIS_MINUS_X,
                        remappedMatrix
                    )
                }
                Surface.ROTATION_270 -> {
                    // Reverse landscape
                    SensorManager.remapCoordinateSystem(
                        rotMatrix,
                        SensorManager.AXIS_MINUS_Y,
                        SensorManager.AXIS_X,
                        remappedMatrix
                    )
                }
                Surface.ROTATION_180 -> {
                    SensorManager.remapCoordinateSystem(
                        rotMatrix,
                        SensorManager.AXIS_MINUS_X,
                        SensorManager.AXIS_MINUS_Y,
                        remappedMatrix
                    )
                }
                else -> {
                    // Portrait (ROTATION_0)
                    SensorManager.remapCoordinateSystem(
                        rotMatrix,
                        SensorManager.AXIS_X,
                        SensorManager.AXIS_Y,
                        remappedMatrix
                    )
                }
            }

            SensorManager.getOrientation(remappedMatrix, orientationAngles)
            rawYaw = orientationAngles[0]
            rawPitch = orientationAngles[1]
            rawRoll = orientationAngles[2]

            filterAndDispatchHeadPose()
        } catch (e: Exception) {
            Log.w(tag, "Rotation vector process error: ${e.message}")
        }
    }

    private fun processGyroscopeEvent(event: SensorEvent) {
        val now = event.timestamp
        if (lastGyroTimestamp != 0L) {
            val dt = (now - lastGyroTimestamp) * 1.0e-9f
            if (dt in 0.0001f..0.2f) {
                val gx = event.values[0]
                val gy = event.values[1]
                val gz = event.values[2]

                // Integrate gyroscope angular velocities (landscape mapping)
                fusionPitch += gy * dt
                fusionRoll += gx * dt
                fusionYaw += gz * dt

                rawYaw = fusionYaw
                rawPitch = fusionPitch
                rawRoll = fusionRoll
                filterAndDispatchHeadPose()
            }
        }
        lastGyroTimestamp = now
    }

    private fun processAccelerometerEvent(event: SensorEvent) {
        val ax = event.values[0]
        val ay = event.values[1]
        val az = event.values[2]

        val norm = sqrt(ax * ax + ay * ay + az * az)
        if (norm > 0.1f) {
            val nax = ax / norm
            val nay = ay / norm
            val naz = az / norm

            // Estimate pitch and roll from gravity vector
            val accelPitch = atan2(nay, sqrt(nax * nax + naz * naz))
            val accelRoll = atan2(-nax, naz)

            // Complementary filter fusion (96% gyro, 4% accelerometer gravity correction)
            fusionPitch = 0.96f * fusionPitch + 0.04f * accelPitch
            fusionRoll = 0.96f * fusionRoll + 0.04f * accelRoll
        }
    }

    private fun filterAndDispatchHeadPose() {
        val settings = prefsRepo.getSettings()
        if (!settings.headTrackingEnabled) return

        // 1. Calculate relative orientation deltas from calibrated forward reference
        var deltaYaw = rawYaw - refYaw
        while (deltaYaw > PI.toFloat()) deltaYaw -= (2f * PI.toFloat())
        while (deltaYaw < -PI.toFloat()) deltaYaw += (2f * PI.toFloat())

        var deltaPitch = rawPitch - refPitch
        while (deltaPitch > PI.toFloat()) deltaPitch -= (2f * PI.toFloat())
        while (deltaPitch < -PI.toFloat()) deltaPitch += (2f * PI.toFloat())

        var deltaRoll = rawRoll - refRoll
        while (deltaRoll > PI.toFloat()) deltaRoll -= (2f * PI.toFloat())
        while (deltaRoll < -PI.toFloat()) deltaRoll += (2f * PI.toFloat())

        // 2. Invert axes if requested by user
        if (settings.headTrackingInvertYaw) deltaYaw = -deltaYaw
        if (settings.headTrackingInvertPitch) deltaPitch = -deltaPitch

        // 3. Deadband filter: suppress micro-jitter (<0.0012 rad ≈ 0.07°) when head is resting
        val deadband = 0.0012f
        val diffYaw = deltaYaw - smoothedYaw
        val diffPitch = deltaPitch - smoothedPitch
        val diffRoll = deltaRoll - smoothedRoll

        val moveDist = sqrt(diffYaw * diffYaw + diffPitch * diffPitch + diffRoll * diffRoll)
        if (moveDist < deadband) {
            // Keep steady, zero micro-shake
            return
        }

        // 4. Adaptive Exponential Moving Average (EMA)
        // High movement speed -> alpha increases for instantaneous responsiveness
        // Slow movement -> alpha decreases for cinematic smoothness and zero nausea
        val baseAlpha = settings.headTrackingSmoothing.coerceIn(0.05f, 0.65f)
        val speedFactor = (moveDist * 8f).coerceIn(0f, 0.45f)
        val alpha = (baseAlpha + speedFactor).coerceIn(0.12f, 0.85f)

        smoothedYaw += alpha * diffYaw
        smoothedPitch += alpha * diffPitch
        smoothedRoll += alpha * diffRoll

        // 5. Apply user sensitivity multiplier
        val sens = settings.headTrackingSensitivity.coerceIn(0.2f, 3.0f)
        val finalYaw = smoothedYaw * sens
        val finalPitch = smoothedPitch * sens
        val finalRoll = smoothedRoll * sens

        // Dispatch directly to OpenGL ES renderer for low-latency VR projection
        onHeadPoseChanged(finalYaw, finalPitch, finalRoll)

        // Throttle UI / StateFlow telemetry updates (~20 Hz)
        val now = SystemClock.uptimeMillis()
        if (now - lastTelemetryDispatchTime >= 45L) {
            lastTelemetryDispatchTime = now
            prefsRepo.updateHeadOrientation(finalYaw, finalPitch, finalRoll, sensorName)
        }
    }
}
