package com.harroyuz.ddrchartviewer

import com.harroyuz.ddrchartviewer.domain.*
import org.junit.Assert.*
import org.junit.Test

class SkillTest {
    private val calculator = SkillCalculator(javaClass.getResource("/flare-skill.json")!!.readText())
    private fun music(id: Int, series: Int = 20) = BjmMusic(id, "Song $id", "Artist", series, List(10) { 15 })
    private fun score(id: Int, slot: Int = 3, clear: Int = 3, flare: Int = 10, points: Int = 900_000, time: Long = 1) = BjmScore(id, slot, 3, clear, points, time, flare)
    private fun snapshot(music: List<BjmMusic>, scores: List<BjmScore>) = ScoreSnapshot(BjmUser("test", "Fixture"), music, scores, 0)
    @Test fun rankBoundariesMatchBjm() {
        assertEquals("NONE", SkillRank.name(0)); assertEquals("NONE +++", SkillRank.name(1999))
        assertEquals("SATURN +++", SkillRank.name(44999)); assertEquals("URANUS", SkillRank.name(45000))
        assertEquals("SUN +++", SkillRank.name(89999)); assertEquals("WORLD", SkillRank.name(90000))
    }
    @Test fun exactWebsiteValuesIncludeNormalAndUnknownIsNotZero() {
        assertEquals(600, calculator.value(15, 0))
        assertEquals(960, calculator.value(15, 10))
        assertEquals(1064, calculator.value(19, 10))
        assertNull(calculator.value(20, 10))
        assertNull(calculator.value(15, 11))
    }
    @Test fun eachModeAndEraHasIndependentTopThirty() {
        val music = (1..198).map { music(it, when { it <= 66 -> 13; it <= 132 -> 17; else -> 20 }) }
        val scores = music.flatMap { listOf(score(it.id), score(it.id, slot = 7)) }
        val entries = calculator.entries(snapshot(music, scores))
        val sums = calculator.summaries(entries)
        assertEquals(180, entries.count { it.inTarget })
        assertTrue(sums.all { it.count == 30 && it.total == 28800 && it.border == 960 })
    }
    @Test fun failedAssistAndUnknownMusicAreRetainedButNotTargets() {
        val entries = calculator.entries(snapshot(listOf(music(1), music(2)), listOf(score(1, clear = 1), score(2, clear = 2), score(999))))
        assertEquals(3, entries.size)
        assertFalse(entries.any { it.inTarget })
    }
    @Test fun tiesUseScoreThenTimeAndDuplicatesDoNotConsumeSlots() {
        val music = (1..31).map { music(it) }
        val scores = (1..31).map { score(it, points = if (it == 1) 999999 else 900000, time = it.toLong()) } + score(1)
        val entries = calculator.entries(snapshot(music, scores))
        assertEquals(31, entries.size)
        assertTrue(entries.first { it.score.musicId == 1 }.inTarget)
        assertFalse(entries.first { it.score.musicId == 2 }.inTarget)
    }
    @Test fun dpDatabasePlaceholderIsSkipped() {
        val song = music(1).copy(levels = listOf(1,2,3,4,5,0,6,7,8,9))
        assertEquals(6, song.level(5)); assertEquals(9, song.level(8))
        assertEquals(Era.CLASSIC, music(1, 13).era); assertEquals(Era.WHITE, music(1, 16).era); assertEquals(Era.GOLD, music(1, 18).era)
    }
    @Test fun matchingRequiresUniqueTitleAndSameLevel() {
        val chart = ChartRef("abc", 3, 15, 15.8)
        val song = Song("abc", "Ｓｏｎｇ 1", "", 20, false, listOf(chart))
        val entry = SkillEntry(score(1), music(1), 960)
        assertNotNull(CatalogMatcher(listOf(song), listOf(music(1))).score(chart, mapOf(entry.score.key to entry)))
        assertNull(CatalogMatcher(listOf(song), listOf(music(1), music(2).copy(title = "Song 1"))).score(chart, mapOf(entry.score.key to entry)))
        assertNull(CatalogMatcher(listOf(song), listOf(music(1))).score(chart.copy(level = 16), mapOf(entry.score.key to entry)))
    }
}
