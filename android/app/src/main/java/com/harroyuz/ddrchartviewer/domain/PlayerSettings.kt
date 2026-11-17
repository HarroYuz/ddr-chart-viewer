package com.harroyuz.ddrchartviewer.domain

import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

enum class SpeedMode { FHS, HI_SPEED }

data class PlayerSettings(
    val speed: Float = 2f,
    val speedMode: SpeedMode = SpeedMode.FHS,
    val greenNumber: Int = 500,
    val fieldHeight: Int = 1000,
    val constant: Boolean = false,
    val constantMs: Int = 1000,
    val mirror: Boolean = false,
    val reverse: Boolean = false,
    val flip: Boolean = false,
    val rate: Float = 1f,
) {
    fun normalized() = copy(
        speed = if (speed.isFinite()) (speed.coerceIn(.25f, 100f) * 100).roundToInt() / 100f else 2f,
        greenNumber = greenNumber.coerceIn(10, 9999),
        fieldHeight = fieldHeight.coerceIn(200, 1000),
        constantMs = ((constantMs.coerceIn(100, 10000) + 5) / 10) * 10,
        rate = if (rate.isFinite()) rate.coerceIn(.25f, 2f) else 1f,
    )
}

/** FHS matches IIDX: green / 600 seconds across the field at the initial BPM. */
internal fun playerPixelsPerBeat(settings: PlayerSettings, initialBpm: Double, judgeDistance: Float, density: Float): Float {
    val safe = settings.normalized()
    return if (safe.speedMode == SpeedMode.FHS) {
        judgeDistance.coerceAtLeast(1f) * 36000f / (safe.greenNumber * initialBpm.coerceAtLeast(1.0).toFloat())
    } else 32f * density * safe.speed
}

internal fun playerFieldHeight(available: Float, fraction: Int): Float =
    available.coerceAtLeast(0f) * fraction.coerceIn(200, 1000) / 1000f

/** Visibility uses judgment time, including STOP/DELAY, rather than beat distance. */
internal fun constantAlpha(secondsUntilJudgment: Double, windowMs: Int): Float {
    val window = windowMs.coerceIn(100, 10000) / 1000.0
    val fade = minOf(.1, window)
    return ((window - secondsUntilJudgment) / fade).toFloat().coerceIn(0f, 1f)
}

/** Same clockwise gesture and dead zone as the IIDX rotary control. */
internal class RotaryAdjustment(initial: Float, private val minimum: Float, private val maximum: Float) {
    var value = initial.coerceIn(minimum, maximum)
        private set
    private var lastAngle: Float? = null
    fun move(x: Float, y: Float, deadZone: Float, unitsPerTurn: Float): Float {
        if (hypot(x, y) < deadZone) { lastAngle = null; return value }
        val angle = Math.toDegrees(atan2(y, x).toDouble()).toFloat()
        lastAngle?.let { previous ->
            val delta = (angle - previous + 540f) % 360f - 180f
            value = (value + delta * unitsPerTurn / 360f).coerceIn(minimum, maximum)
        }
        lastAngle = angle
        return value
    }
}
