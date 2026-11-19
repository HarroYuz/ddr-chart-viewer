package com.harroyuz.ddrchartviewer.domain

import org.json.JSONArray
import java.text.Normalizer
import java.util.Locale

/** BJMania's table and ranking, independent of 3icecream's reference constants. */
class SkillCalculator(tableJson: String) {
    private val table = JSONArray(tableJson).let { rows ->
        (0 until rows.length()).associateWith { level -> rows.optJSONArray(level)?.let { row ->
            (0 until row.length()).map { row.getInt(it) }
        }.orEmpty() }
    }
    fun value(level: Int, flare: Int): Int? = table[level]?.getOrNull(flare)

    fun entries(snapshot: ScoreSnapshot): List<SkillEntry> {
        val music = snapshot.music.associateBy { it.id }
        val order = compareByDescending<SkillEntry> { it.skill ?: -1 }
            .thenByDescending { it.score.score }.thenByDescending { it.score.time }.thenBy { it.score.musicId }.thenBy { it.score.slot }
        val entries = snapshot.scores.map { score ->
            val song = music[score.musicId]
            SkillEntry(score, song, song?.let { value(it.level(score.slot), score.flare) })
        }.groupBy { it.score.key }.map { (_, rows) -> rows.sortedWith(order).first() }
        val targets = entries.filter { it.score.clearKind >= 3 && it.skill != null && it.music != null }
            .groupBy { it.score.mode to it.music!!.era }
            .flatMap { (_, rows) -> rows.sortedWith(order).take(30) }.map { it.score.key }.toSet()
        return entries.map { it.copy(inTarget = it.score.key in targets) }.sortedWith(order)
    }

    fun summaries(entries: List<SkillEntry>): List<SkillSummary> = PlayMode.entries.flatMap { mode ->
        Era.entries.map { era ->
            val targets = entries.filter { it.inTarget && it.score.mode == mode && it.music?.era == era }
            SkillSummary(mode, era, targets.sumOf { it.skill ?: 0 }, targets.size, targets.minOfOrNull { it.skill ?: 0 } ?: 0)
        }
    }
}

object SkillRank {
    private val ranks = listOf(
        Triple(90000, 0, "WORLD"), Triple(75000, 3750, "SUN"), Triple(60000, 3750, "NEPTUNE"),
        Triple(45000, 3750, "URANUS"), Triple(34000, 2750, "SATURN"), Triple(24000, 2500, "JUPITER"),
        Triple(16000, 2000, "MARS"), Triple(10000, 1500, "EARTH"), Triple(6000, 1000, "VENUS"),
        Triple(2000, 1000, "MERCURY"), Triple(0, 500, "NONE"),
    )
    fun name(total: Int): String {
        val (base, step, name) = ranks.first { total.coerceAtLeast(0) >= it.first }
        val stars = if (step == 0) 0 else ((total - base) / step).coerceIn(0, 3)
        return name + if (stars == 0) "" else " " + "+".repeat(stars)
    }
}

/** Never use fuzzy title matching: ambiguous names and changed levels remain unlinked. */
class CatalogMatcher(songs: List<Song>, music: List<BjmMusic>) {
    private val musicByName = music.groupBy { normalize(it.title) }
    private val links: Map<String, Int> = songs.mapNotNull { song ->
        val candidates = musicByName[normalize(song.title)].orEmpty()
        candidates.singleOrNull()?.id?.let { song.id to it }
    }.toMap()
    private val musicById = music.associateBy { it.id }
    private val songByMusic = songs.filterNot { it.deleted }.mapNotNull { song -> links[song.id]?.let { it to song } }
        .groupBy({ it.first }, { it.second })

    fun music(song: Song) = links[song.id]?.let(musicById::get)
    fun score(chart: ChartRef, entries: Map<String, SkillEntry>): SkillEntry? {
        val id = links[chart.songId] ?: return null
        if (musicById[id]?.level(chart.slot) != chart.level) return null
        return entries["$id/${chart.slot}"]
    }
    fun chart(entry: SkillEntry): ChartRef? = songByMusic[entry.score.musicId]?.singleOrNull()?.charts
        ?.singleOrNull { it.slot == entry.score.slot && it.level == entry.music?.level(entry.score.slot) }

    companion object {
        private val whitespace = Regex("\\s+")
        fun normalize(title: String): String = Normalizer.normalize(title, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT).replace(whitespace, "")
    }
}
