package com.harroyuz.ddrchartviewer.data.local

import android.content.Context
import android.util.AtomicFile
import com.harroyuz.ddrchartviewer.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class LocalStore(context: Context) {
    private val root = File(context.filesDir, "ddr-data").apply { mkdirs() }
    val chartDirectory = File(context.cacheDir, "charts").apply { mkdirs() }
    private val preferences = context.getSharedPreferences("ddr-settings", Context.MODE_PRIVATE)
    var mode: PlayMode
        get() = runCatching { PlayMode.valueOf(preferences.getString("mode", "SP")!!) }.getOrDefault(PlayMode.SP)
        set(value) { preferences.edit().putString("mode", value.name).apply() }
    fun playerSettings(mode: PlayMode): PlayerSettings = PlayerSettingsCodec.decode(
        preferences.getString("player-$mode", null),
        if (preferences.contains("speed-$mode")) preferences.getFloat("speed-$mode", 2f) else null,
    )
    fun savePlayerSettings(mode: PlayMode, settings: PlayerSettings) {
        preferences.edit().putString("player-$mode", PlayerSettingsCodec.encode(settings)).apply()
    }
    fun favorites(): Set<String> = preferences.getStringSet("favorites", emptySet())!!.toSet()
    fun saveFavorites(ids: Set<String>) { preferences.edit().putStringSet("favorites", ids).apply() }

    fun readCatalog(): Catalog? = read("catalog.json")?.let { o ->
        val songs = o.getJSONArray("songs").objects().map { song ->
            val id = song.getString("id")
            Song(id, song.getString("title"), song.optString("aliases"), song.getInt("version"), song.getBoolean("deleted"), song.getJSONArray("charts").objects().map {
                ChartRef(id, it.getInt("slot"), it.getInt("level"), if (it.isNull("constant")) null else it.getDouble("constant"))
            })
        }
        Catalog(songs, o.getLong("updatedAt"), o.optLong("sourceUpdatedAt"))
    }
    fun writeCatalog(catalog: Catalog) = write("catalog.json", JSONObject().apply {
        put("updatedAt", catalog.updatedAt); put("sourceUpdatedAt", catalog.sourceUpdatedAt)
        put("songs", JSONArray(catalog.songs.map { song -> JSONObject().apply {
            put("id", song.id); put("title", song.title); put("aliases", song.aliases); put("version", song.version); put("deleted", song.deleted)
            put("charts", JSONArray(song.charts.map { chart -> JSONObject().apply { put("slot", chart.slot); put("level", chart.level); put("constant", chart.constant ?: JSONObject.NULL) } }))
        } }))
    })
    fun readScores(): ScoreSnapshot? = read("scores.json")?.let { o ->
        val user = o.getJSONObject("user")
        ScoreSnapshot(BjmUser(user.getString("id"), user.getString("name")), o.getJSONArray("music").objects().map {
            val levels = it.getJSONArray("levels")
            BjmMusic(it.getInt("id"), it.getString("title"), it.getString("artist"), it.getInt("series"), (0 until levels.length()).map(levels::getInt))
        }, o.getJSONArray("scores").objects().map {
            BjmScore(it.getInt("id"), it.getInt("slot"), it.getInt("rank"), it.getInt("clear"), it.getInt("score"), it.getLong("time"), it.getInt("flare"))
        }, o.getLong("updatedAt"))
    }
    fun writeScores(snapshot: ScoreSnapshot) = write("scores.json", JSONObject().apply {
        put("updatedAt", snapshot.updatedAt)
        put("user", JSONObject().put("id", snapshot.user.id).put("name", snapshot.user.name))
        put("music", JSONArray(snapshot.music.map { JSONObject().put("id", it.id).put("title", it.title).put("artist", it.artist).put("series", it.series).put("levels", JSONArray(it.levels)) }))
        put("scores", JSONArray(snapshot.scores.map { JSONObject().put("id", it.musicId).put("slot", it.slot).put("rank", it.rank).put("clear", it.clearKind).put("score", it.score).put("time", it.time).put("flare", it.flare) }))
    })
    fun clearScores() { AtomicFile(File(root, "scores.json")).delete() }
    private fun read(name: String): JSONObject? {
        val file = AtomicFile(File(root, name))
        if (!file.baseFile.exists()) return null
        return JSONObject(file.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
    }
    private fun write(name: String, json: JSONObject) {
        val file = AtomicFile(File(root, name)); val stream = file.startWrite()
        try { stream.write(json.toString().toByteArray()); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
}
private fun JSONArray.objects() = (0 until length()).map(::getJSONObject)
