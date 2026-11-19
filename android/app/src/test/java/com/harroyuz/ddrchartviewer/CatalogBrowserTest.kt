package com.harroyuz.ddrchartviewer

import com.harroyuz.ddrchartviewer.data.remote.icecream.IceCreamParser
import com.harroyuz.ddrchartviewer.domain.*
import org.junit.Assert.*
import org.junit.Test

class CatalogBrowserTest {
    private val first = Song("a", "Alpha", "别名", 13, false, listOf(
        ChartRef("a", 1, 11, 11.05), ChartRef("a", 3, 15, 15.0), ChartRef("a", 5, 11, 11.25)), "DJ TEST")
    private val second = Song("b", "Beta", "", 16, false, listOf(ChartRef("b", 1, 11, null)), "")
    private val third = Song("c", "Gamma", "", 17, false, listOf(ChartRef("c", 1, 11, 11.25)))
    private val songs = listOf(first, second, third)
    private val music = listOf(
        BjmMusic(1, "Alpha", "DJ Test", 1, listOf(0, 11, 0, 15)),
        BjmMusic(2, "Beta", "Guest", 2, listOf(0, 11)),
    )
    private val matcher = CatalogMatcher(songs, music)
    private fun record(id: Int, slot: Int, score: Int, skill: Int) =
        SkillEntry(BjmScore(id, slot, 5, 3, score, 0, 1), music[id - 1], skill)
    private val entries = listOf(record(1, 1, 700000, 500), record(1, 3, 990000, 510), record(2, 1, 800000, 650))
        .associateBy { it.score.key }
    private fun browse(filter: CatalogFilter = CatalogFilter(), query: String = "", order: CatalogOrder = CatalogOrder.TITLE,
                       desc: Boolean = false, mode: PlayMode = PlayMode.SP) =
        browseCatalog(songs, mode, query, filter, emptySet(), order, desc, matcher, entries)

    @Test fun integerAndTierMustMatchTheSameChartAndMode() {
        val filter = CatalogFilter(levels = setOf(11), tiers = setOf(0))
        assertTrue(browse(filter).isEmpty())
        assertEquals(listOf("b"), browse(filter.copy(tiers = setOf(NO_TIER))).map { it.song.id })
        assertEquals(listOf(1), browse(filter.copy(tiers = setOf(5))).single().charts.map { it.slot })
        assertEquals(listOf(5), browse(filter.copy(tiers = setOf(25)), mode = PlayMode.DP).single().charts.map { it.slot })
    }

    @Test fun eraToggleCompletesPartialSelectionAndRemovesOnlyItsOwnChildren() {
        val selection = CatalogFilter(versions = setOf(13, 14)).toggleEra(Era.WHITE)
        assertEquals(setOf(13, 14, 15, 16), selection.versions)
        assertEquals(setOf(13), selection.toggleEra(Era.WHITE).versions)
        assertEquals(listOf("a", "b"), browse(selection).map { it.song.id })
        assertEquals(listOf("c"), browse(CatalogFilter().toggleEra(Era.GOLD)).map { it.song.id })
    }

    @Test fun titleIncludesAliasAndArtistFieldWorksWithoutScoreData() {
        assertEquals(listOf("a"), browse(CatalogFilter(searchArtists = false), "别名").map { it.song.id })
        assertTrue(browse(CatalogFilter(searchArtists = false), "DJ").isEmpty())
        assertTrue(browse(CatalogFilter(searchTitles = false), "Alpha").isEmpty())
        val offline = browseCatalog(songs, PlayMode.SP, "ＤＪtest", CatalogFilter(searchTitles = false), emptySet(),
            CatalogOrder.TITLE, false, CatalogMatcher(songs, emptyList()), emptyMap())
        assertEquals("DJ TEST", offline.single().artist)
        assertEquals("Guest", browse(query = "guest").single().artist)
    }

    @Test fun scoreAndFlareSortUseOnlyMatchingChartsAndKeepMissingValuesLast() {
        val filter = CatalogFilter(levels = setOf(11))
        assertEquals(listOf("a", "b", "c"), browse(filter, order = CatalogOrder.SCORE).map { it.song.id })
        assertEquals(listOf("b", "a", "c"), browse(filter, order = CatalogOrder.SCORE, desc = true).map { it.song.id })
        assertEquals(listOf("a", "b", "c"), browse(order = CatalogOrder.SCORE, desc = true).map { it.song.id })
        assertEquals(listOf(650, 510, null), browse(order = CatalogOrder.FLARE, desc = true).map { it.value })
        assertEquals(listOf(510, 650, null), browse(order = CatalogOrder.FLARE).map { it.value })
    }

    @Test fun difficultySortUsesTierAndTitleSortCanReverse() {
        val filter = CatalogFilter(levels = setOf(11))
        assertEquals(listOf("b", "a", "c"), browse(filter, order = CatalogOrder.DIFFICULTY).map { it.song.id })
        assertEquals(listOf("c", "a", "b"), browse(filter, order = CatalogOrder.DIFFICULTY, desc = true).map { it.song.id })
        assertEquals(listOf("c", "b", "a"), browse(desc = true).map { it.song.id })
    }

    @Test fun detailArtistUsesSongCreditAndDecodesEntities() {
        assertEquals("A & B", IceCreamParser.songArtist("<span class='sp-artist'>A &amp; B</span>"))
        assertEquals("", IceCreamParser.songArtist("<title>song</title>"))
    }
}
