package com.harroyuz.ddrchartviewer.ui

import com.harroyuz.ddrchartviewer.domain.PlayMode
import kotlin.math.min

internal data class PlayerLaneLayout(val laneWidth: Float, val left: Float, val lanes: Int) {
    val width get() = lanes * laneWidth
    fun center(lane: Int) = left + (lane + .5f) * laneWidth
}

/** SP occupies the middle four lanes of the same eight-lane grid used by DP. */
internal fun playerLaneLayout(viewportWidth: Float, density: Float, mode: PlayMode): PlayerLaneLayout {
    val laneWidth = min((viewportWidth - 32f * density).coerceAtLeast(0f) / 8, 68f * density)
    return PlayerLaneLayout(laneWidth, (viewportWidth - mode.lanes * laneWidth) / 2, mode.lanes)
}

internal enum class NotePhase { APPROACHING, HOLDING, PASSED }
internal fun notePhase(position: Double, headTime: Double, tailTime: Double): NotePhase = when {
    position < headTime -> NotePhase.APPROACHING
    tailTime > headTime && position < tailTime -> NotePhase.HOLDING
    else -> NotePhase.PASSED
}
