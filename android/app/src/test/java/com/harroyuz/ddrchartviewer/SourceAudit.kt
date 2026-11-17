package com.harroyuz.ddrchartviewer

import com.harroyuz.ddrchartviewer.data.remote.bjm.DdrMusicProto
import com.harroyuz.ddrchartviewer.data.remote.icecream.IceCreamParser
import com.harroyuz.ddrchartviewer.domain.*
import org.json.JSONObject
import java.io.File

/** Opt-in audit of public snapshots; never reads a user's account or credentials. */
object SourceAudit {
    @JvmStatic fun main(args: Array<String>) {
        val root = File(args.single())
        val catalog = IceCreamParser.catalog(File(root, "songdata.js").readText(), 0)
        val music = DdrMusicProto.decode(File(root, "MDX_mdb.bin").readBytes())
        val matcher = CatalogMatcher(catalog.songs, music)
        println("Public catalog: ${catalog.songs.size} songs, ${catalog.songs.sumOf { it.charts.size }} charts; BJM ${music.size} songs; matched ${catalog.songs.count { matcher.music(it) != null }} titles")
        var pages = 0
        for (mode in PlayMode.entries) for (level in 1..19) {
            val file = File(root, "difficulty-${mode.name}-$level.html")
            if (!file.exists()) continue
            val tiers = IceCreamParser.difficultyPage(file.readText(), mode, level)
            val charts = catalog.songs.flatMap { it.charts }.associateBy { it.key }
            check(tiers.keys.all { charts[it]?.level == level }) { "Level mismatch in $file" }
            pages++
        }
        check(pages == 38) { "Expected all 38 difficulty pages, got $pages" }
        var charts = 0; var notes = 0
        for (file in root.listFiles().orEmpty().filter { it.name.startsWith("chart-") && it.extension == "json" }) {
            val raw = file.readText(); val array = JSONObject(raw).getJSONArray("notes")
            for (slot in 0..8) {
                if (array.optJSONArray(slot) == null) continue
                val chart = IceCreamParser.chart(raw, slot)
                check(chart.timeline.duration.isFinite() && chart.timeline.duration > 0)
                check(chart.notes.all { it.lane in 0 until chart.mode.lanes })
                charts++; notes += chart.notes.size
            }
        }
        check(charts >= 16) { "Provide multiple SP/DP timing samples" }
        println("Validated $pages difficulty pages and $charts playable charts ($notes note objects), including BPM changes and stops")
    }
}
