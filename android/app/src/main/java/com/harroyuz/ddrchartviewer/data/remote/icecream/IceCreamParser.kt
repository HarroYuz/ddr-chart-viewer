package com.harroyuz.ddrchartviewer.data.remote.icecream

import com.harroyuz.ddrchartviewer.domain.*
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.parser.Parser

object IceCreamParser {
    /** Extract a JSON assignment with bracket/string awareness; never execute remote JavaScript. */
    fun assignment(source: String, name: String): String {
        val match = Regex("(?:var|let|const)\\s+${Regex.escape(name)}\\s*=\\s*").find(source)
            ?: error("3icecream 格式变更：缺少 $name")
        val start = match.range.last + 1
        require(source.getOrNull(start) in listOf('[', '{')) { "不是 JSON 容器" }
        var depth = 0; var quoted = false; var escaped = false
        for (i in start until source.length) {
            val c = source[i]
            if (quoted) { if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false }
            else when (c) { '"' -> quoted = true; '[', '{' -> depth++; ']', '}' -> { depth--; if (depth == 0) return source.substring(start, i + 1) } }
        }
        error("3icecream JSON 不完整")
    }

    fun catalog(js: String, updatedAt: Long): Catalog {
        val rows = JSONArray(assignment(js, "ALL_SONG_DATA"))
        val songs = (0 until rows.length()).map { index ->
            val o = rows.getJSONObject(index); val id = o.getString("song_id")
            require(id.matches(Regex("[A-Za-z0-9]+"))) { "曲目 ID 无效" }
            val ratings = o.getJSONArray("ratings"); val tiers = o.optJSONArray("tiers")
            require(ratings.length() == 9) { "3icecream 谱面槽位格式变更" }
            val charts = (0..8).mapNotNull { slot ->
                val level = ratings.getInt(slot)
                if (level == 0) null else {
                    require(level in 1..20)
                    val tier = tiers?.optDouble(slot, Double.NaN) ?: Double.NaN
                    // 1 is the source's insufficient-data sentinel, not level + 1.
                    ChartRef(id, slot, level, tier.takeIf { it.isFinite() && it >= 0 && it < 1 }?.plus(level))
                }
            }
            Song(id, text(o.getString("song_name")), listOf("romanized_name", "searchable_name", "alternate_name").joinToString(" ") { text(o.optString(it)) }, o.optInt("version_num"), o.optInt("deleted") == 1, charts)
        }
        require(songs.isNotEmpty() && songs.map { it.id }.distinct().size == songs.size) { "3icecream 曲库为空或 ID 重复" }
        val sourceTime = Regex("SONG_DATA_LAST_UPDATED_unixms\\s*=\\s*(\\d+)").find(js)?.groupValues?.get(1)?.toLongOrNull() ?: 0
        return Catalog(songs, updatedAt, sourceTime)
    }

    fun difficultyPage(html: String, mode: PlayMode, level: Int): Map<String, Double?> {
        val spdp = Regex("viewSpdp\\s*=\\s*(\\d+)").find(html)?.groupValues?.get(1)?.toInt()
        val rating = Regex("viewRating\\s*=\\s*(\\d+)").find(html)?.groupValues?.get(1)?.toInt()
        require(spdp == mode.ordinal && rating == level) { "3icecream 难度页模式或等级不符" }
        val data = JSONObject(assignment(html, "difficultyList"))
        return data.keys().asSequence().associateWith { key ->
            val slot = key.substringAfterLast('/').toInt()
            require(slot in 0..8 && (slot <= 4) == (mode == PlayMode.SP))
            val tier = data.getJSONObject(key).optDouble("tier", Double.NaN)
            tier.takeIf { it.isFinite() && it >= 0 && it < 1 }?.plus(level)
        }
    }

    fun chart(json: String, slot: Int): ChartData {
        require(slot in 0..8)
        val root = JSONObject(json)
        val measures = root.optJSONArray("notes")?.optJSONArray(slot) ?: error("原站尚未提供这张谱面的音符数据")
        require(measures.length() > 0) { "谱面没有小节" }
        val timing = root.optJSONArray("chartTiming")?.optJSONObject(slot)
        fun events(key: String): List<TimingEvent> {
            val array = if (timing?.has(key) == true) timing.optJSONArray(key) else root.optJSONArray(key)
            return (0 until (array?.length() ?: 0)).map { i ->
                val pair = array!!.getJSONArray(i)
                TimingEvent(pair.get(0).toString().toDouble(), pair.get(1).toString().toDouble()).also {
                    require(it.beat.isFinite() && it.beat >= 0 && it.value.isFinite() && it.value >= 0) { "$key 含不支持的负数或无效时序" }
                }
            }.sortedBy { it.beat }
        }
        require(events("WARPS").isEmpty()) { "这张谱面包含尚不支持的 WARP 时序" }
        val mode = if (slot < 5) PlayMode.SP else PlayMode.DP
        val holds = arrayOfNulls<Pair<Double, NoteKind>>(mode.lanes)
        val notes = mutableListOf<Note>()
        for (measure in 0 until measures.length()) {
            val rows = measures.getJSONArray(measure)
            require(rows.length() > 0) { "空小节" }
            for (row in 0 until rows.length()) {
                val cells = rows.getString(row).trim(); val beat = measure * 4.0 + row * 4.0 / rows.length()
                require(cells.length == mode.lanes) { "谱面轨道数与 SP/DP 不符" }
                cells.forEachIndexed { lane, c -> when (c) {
                    '0' -> Unit
                    '1' -> notes.add(Note(beat, lane, NoteKind.TAP))
                    '2', '4' -> { require(holds[lane] == null) { "长按重叠" }; holds[lane] = beat to if (c == '2') NoteKind.HOLD else NoteKind.ROLL }
                    '3' -> { val hold = holds[lane] ?: error("长按尾缺少起点"); notes.add(Note(hold.first, lane, hold.second, beat)); holds[lane] = null }
                    'M' -> notes.add(Note(beat, lane, NoteKind.SHOCK))
                    'L' -> notes.add(Note(beat, lane, NoteKind.LIFT))
                    'F' -> notes.add(Note(beat, lane, NoteKind.FAKE))
                    else -> error("暂不支持的音符类型：$c")
                } }
            }
        }
        require(holds.all { it == null }) { "长按没有结束" }
        val offset = (timing?.optString("OFFSET")?.toDoubleOrNull() ?: root.optString("OFFSET").toDoubleOrNull()) ?: 0.0
        require(offset.isFinite())
        return ChartData(text(root.optString("TITLE")), text(root.optString("ARTIST")), mode, notes.sortedBy { it.beat }, events("BPMS"), events("STOPS"), events("DELAYS"), offset, measures.length() * 4.0).also { it.timeline }
    }

    private fun text(value: String) = Parser.unescapeEntities(value, false)
}
