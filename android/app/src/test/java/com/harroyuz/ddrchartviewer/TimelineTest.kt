package com.harroyuz.ddrchartviewer

import com.harroyuz.ddrchartviewer.domain.*
import org.junit.Assert.*
import org.junit.Test

class TimelineTest {
    private fun timeline() = Timeline(listOf(TimingEvent(0.0, 120.0), TimingEvent(8.0, 240.0)), listOf(TimingEvent(4.0, 1.0)), listOf(TimingEvent(6.0, .5)), .25, 16.0)
    @Test fun stopIsAfterNoteAndDelayBeforeNote() {
        val t = timeline()
        assertEquals(-.25, t.timeAt(0.0), .00001)
        assertEquals(1.75, t.timeAt(4.0), .00001)
        assertEquals(3.25, t.timeAt(5.0), .00001)
        assertEquals(4.25, t.timeAt(6.0), .00001)
        assertEquals(5.25, t.timeAt(8.0), .00001)
        assertEquals(6.25, t.timeAt(12.0), .00001)
    }
    @Test fun playbackFreezesBeatDuringStopAndDelay() {
        val t = timeline()
        assertEquals(4.0, t.beatAt(2.2), .00001)
        assertEquals(6.0, t.beatAt(4.0), .00001)
        assertEquals(240.0, t.bpmAt(5.5), .00001)
    }
    @Test fun beatTimeRoundTripAcrossChanges() {
        val t = timeline()
        for (i in 0..128) { val beat = i / 8.0; assertEquals(beat, t.beatAt(t.timeAt(beat)), .00001) }
    }
    @Test fun duplicateStopsAccumulate() {
        val t = Timeline(listOf(TimingEvent(0.0, 120.0)), listOf(TimingEvent(4.0, 1.0), TimingEvent(4.0, .5)), emptyList(), 0.0, 8.0)
        assertEquals(5.5, t.timeAt(8.0), .00001)
    }
    @Test(expected = IllegalArgumentException::class) fun zeroBpmIsRejected() { Timeline(listOf(TimingEvent(0.0, 0.0)), emptyList(), emptyList(), 0.0, 4.0) }
}
