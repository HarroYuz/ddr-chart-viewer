package com.harroyuz.ddrchartviewer.domain

import java.io.Serializable
import kotlin.math.roundToInt

internal enum class CatalogOrder(val label: String) {
    TITLE("标题"), DIFFICULTY("难度"), SCORE("成绩"), FLARE("Flare 分")
}

internal val catalogVersions = mapOf(
    Era.CLASSIC to (1..13), Era.WHITE to (14..16), Era.GOLD to (17..20),
)
private val versionNames = listOf("未知版本", "DDR", "2ndMIX", "3rdMIX", "4thMIX", "5thMIX", "DDRMAX", "DDRMAX2",
    "EXTREME", "SuperNOVA", "SuperNOVA2", "X", "X2", "X3", "DDR 2013", "DDR 2014", "A", "A20", "A20 PLUS", "A3", "WORLD")

internal fun versionName(version: Int) = versionNames.getOrElse(version) { "版本 $version" }
internal const val NO_TIER = -1

internal fun ChartRef.tier(): Int = constant?.let { ((it - level) * 100).roundToInt().coerceIn(0, 99) } ?: NO_TIER
internal fun Set<Int>.toggle(value: Int): Set<Int> = if (value in this) this - value else this + value

internal data class CatalogFilter(
    val levels: Set<Int> = emptySet(),
    val tiers: Set<Int> = emptySet(),
    val versions: Set<Int> = emptySet(),
    val searchTitles: Boolean = true,
    val searchArtists: Boolean = true,
    val favoritesOnly: Boolean = false,
    val includeDeleted: Boolean = false,
) : Serializable {
    fun toggleEra(era: Era): CatalogFilter {
        val children = catalogVersions.getValue(era).toSet()
        return copy(versions = if (versions.containsAll(children)) versions - children else versions + children)
    }
}

internal data class CatalogRow(val song: Song, val artist: String, val charts: List<ChartRef>, val value: Int?) {
    val normalizedTitle = CatalogMatcher.normalize(song.title)
}

internal fun browseCatalog(
    songs: List<Song>, mode: PlayMode, query: String, filter: CatalogFilter, favorites: Set<String>,
    order: CatalogOrder, descending: Boolean, matcher: CatalogMatcher, entries: Map<String, SkillEntry>,
): List<CatalogRow> {
    val keyword = CatalogMatcher.normalize(query.trim())
    val rows = songs.mapNotNull { song ->
        if (song.deleted && !filter.includeDeleted || filter.favoritesOnly && song.id !in favorites ||
            filter.versions.isNotEmpty() && song.version !in filter.versions) return@mapNotNull null
        val artist = song.artist.ifBlank { matcher.music(song)?.artist.orEmpty() }
        if (keyword.isNotEmpty()) {
            val titleMatches = filter.searchTitles && (CatalogMatcher.normalize(song.title).contains(keyword) ||
                CatalogMatcher.normalize(song.aliases).contains(keyword))
            val artistMatches = filter.searchArtists && CatalogMatcher.normalize(artist).contains(keyword)
            if (!titleMatches && !artistMatches) return@mapNotNull null
        }
        val charts = song.charts.filter { chart ->
            chart.mode == mode && (filter.levels.isEmpty() || chart.level in filter.levels) &&
                (filter.tiers.isEmpty() || chart.tier() in filter.tiers)
        }
        if (charts.isEmpty()) return@mapNotNull null
        val value = when (order) {
            CatalogOrder.TITLE -> null
            CatalogOrder.DIFFICULTY -> charts.maxOf { it.level * 100 + it.tier().coerceAtLeast(0) }
            CatalogOrder.SCORE -> charts.mapNotNull { matcher.score(it, entries)?.score?.score }.maxOrNull()
            CatalogOrder.FLARE -> charts.mapNotNull { matcher.score(it, entries)?.skill }.maxOrNull()
        }
        CatalogRow(song, artist, charts, value)
    }
    val byTitle = compareBy<CatalogRow> { it.normalizedTitle }.thenBy { it.song.id }
    if (order == CatalogOrder.TITLE) return rows.sortedWith(if (descending) byTitle.reversed() else byTitle)
    val byValue = if (descending) compareByDescending<CatalogRow> { it.value } else compareBy { it.value }
    return rows.sortedWith(compareBy<CatalogRow> { it.value == null }.then(byValue).then(byTitle))
}
