package com.harroyuz.ddrchartviewer

import com.harroyuz.ddrchartviewer.data.remote.icecream.IceCreamParser
import com.harroyuz.ddrchartviewer.domain.*
import org.junit.Assert.*
import org.junit.Test

class ParserTest {
    private val source = """var ALL_SONG_DATA=[{"song_id":"abc","song_name":"A &amp; B ]","version_num":20,"ratings":[1,2,3,4,5,6,7,8,9],"tiers":[0,0.95,1,0.5,0,0.2,0.3,0.4,1]}];const SONG_DATA_LAST_UPDATED_unixms=123;"""
    @Test fun catalogParsesAllNineSlotsAndUnknownTiers() {
        val catalog = IceCreamParser.catalog(source, 456)
        val song = catalog.songs.single()
        assertEquals("A & B ]", song.title)
        assertEquals(9, song.charts.size)
        assertEquals(1.0, song.charts[0].constant!!, 0.0)
        assertNull(song.charts[2].constant)
        assertEquals(PlayMode.DP, song.charts[5].mode)
        assertEquals(Difficulty.BASIC, song.charts[5].difficulty)
        assertEquals(123L, catalog.sourceUpdatedAt)
    }
    @Test fun assignmentHandlesEscapesAndNestedBrackets() {
        assertEquals("""[{"text":"quote\" } ]", "nested":[1]}]""", IceCreamParser.assignment("""let data = [{"text":"quote\" } ]", "nested":[1]}]; throw 'not executed';""", "data"))
    }
    @Test(expected = IllegalArgumentException::class) fun invalidSlotCountFails() { IceCreamParser.catalog(source.replace("1,2,3,4,5,6,7,8,9", "1,2"), 0) }
    @Test fun referenceUsesTierNotStatisticalRating() {
        val html = """const viewSpdp=1; const viewRating=15; let difficultyList={"abc/7":{"tier":0.25,"rating":-0.76}};"""
        assertEquals(15.25, IceCreamParser.difficultyPage(html, PlayMode.DP, 15)["abc/7"]!!, 0.0)
    }
    @Test(expected = IllegalArgumentException::class) fun redirectedWrongModeIsRejected() {
        IceCreamParser.difficultyPage("const viewSpdp=0; const viewRating=15; let difficultyList={};", PlayMode.DP, 15)
    }
    @Test fun chartPairsHoldsAndUsesPerChartTiming() {
        val chart = IceCreamParser.chart("""{"TITLE":"Test","notes":[[["2000","0100","3000","000M"]]],"BPMS":[[0,120]],"chartTiming":[{"BPMS":[[0,240]],"STOPS":[]}]}""", 0)
        assertEquals(3, chart.notes.size)
        assertEquals(2.0, chart.notes.first().endBeat, 0.0)
        assertEquals(NoteKind.SHOCK, chart.notes.last().kind)
        assertEquals(240.0, chart.bpms.single().value, 0.0)
        assertEquals(.5, chart.timeline.timeAt(2.0), 0.0)
    }
    @Test fun legacyChartUsesGlobalTiming() {
        val chart = IceCreamParser.chart("""{"notes":[[["1000","0000","0000","0000"]]],"BPMS":[["0","120"]],"STOPS":[["2","1.5"]]}""", 0)
        assertEquals(3.5, chart.timeline.timeAt(4.0), .00001)
    }
    @Test(expected = IllegalArgumentException::class) fun dpLaneCountMustMatch() {
        IceCreamParser.chart("""{"notes":[null,null,null,null,null,[["1000"]]],"BPMS":[[0,120]]}""", 5)
    }
    @Test(expected = IllegalStateException::class) fun missingChartHasExplicitError() { IceCreamParser.chart("{}", 0) }
    @Test(expected = IllegalArgumentException::class) fun unterminatedHoldIsRejected() {
        IceCreamParser.chart("""{"notes":[[["2000"]]],"BPMS":[[0,120]]}""", 0)
    }
}
