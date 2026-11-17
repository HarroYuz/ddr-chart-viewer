package com.harroyuz.ddrchartviewer

import com.harroyuz.ddrchartviewer.domain.*
import com.harroyuz.ddrchartviewer.ui.*
import org.junit.Assert.*
import org.junit.Test

class PlayerPresentationTest {
    @Test fun dpHasNoExtraGapAndSpUsesTheSameCenteredLanes() {
        for ((width, density) in listOf(360f to 1f, 1080f to 2.625f, 2560f to 2f)) {
            val dp = playerLaneLayout(width, density, PlayMode.DP)
            val sp = playerLaneLayout(width, density, PlayMode.SP)
            for (lane in 1..7) assertEquals(dp.laneWidth, dp.center(lane) - dp.center(lane - 1), .001f)
            assertEquals(dp.laneWidth, sp.laneWidth, .001f)
            for (lane in 0..3) assertEquals(dp.center(lane + 2), sp.center(lane), .001f)
            assertEquals(width / 2, (sp.center(0) + sp.center(3)) / 2, .001f)
        }
    }

    @Test fun tapsDisappearAtJudgmentAndSeekingBackRestoresThem() {
        assertEquals(NotePhase.APPROACHING, notePhase(.999, 1.0, 1.0))
        assertEquals(NotePhase.PASSED, notePhase(1.0, 1.0, 1.0))
        assertEquals(NotePhase.PASSED, notePhase(1.01, 1.0, 1.0))
        assertEquals(NotePhase.APPROACHING, notePhase(.5, 1.0, 1.0))
    }

    @Test fun holdsRemainOnTheLineOnlyUntilTheirTail() {
        assertEquals(NotePhase.APPROACHING, notePhase(.9, 1.0, 2.0))
        assertEquals(NotePhase.HOLDING, notePhase(1.0, 1.0, 2.0))
        assertEquals(NotePhase.HOLDING, notePhase(1.999, 1.0, 2.0))
        assertEquals(NotePhase.PASSED, notePhase(2.0, 1.0, 2.0))
    }

    @Test fun stopAndDelayUseJudgmentTimeInsteadOfFrozenBeat() {
        val stop = Timeline(listOf(TimingEvent(0.0, 120.0)), listOf(TimingEvent(4.0, 1.0)), emptyList(), 0.0, 8.0)
        val delay = Timeline(listOf(TimingEvent(0.0, 120.0)), emptyList(), listOf(TimingEvent(4.0, 1.0)), 0.0, 8.0)
        assertEquals(stop.beatAt(2.5), delay.beatAt(2.5), .001)
        assertEquals(NotePhase.PASSED, notePhase(2.5, stop.timeAt(4.0), stop.timeAt(4.0)))
        assertEquals(NotePhase.APPROACHING, notePhase(2.5, delay.timeAt(4.0), delay.timeAt(4.0)))
    }

    @Test fun referenceDifficultyKeepsLeadingZeroAndDoesNotInventMissingDecimals() {
        assertEquals(".05", difficultySuffix(11, 11.05))
        assertEquals(".00", difficultySuffix(11, 11.0))
        assertEquals(".95", difficultySuffix(19, 19.95))
        assertNull(difficultySuffix(11, null))
        assertNull(difficultySuffix(11, 12.05))
    }
}
