package com.harroyuz.ddrchartviewer.data

import android.content.Context
import android.util.AtomicFile
import com.harroyuz.ddrchartviewer.BuildConfig
import com.harroyuz.ddrchartviewer.data.local.LocalStore
import com.harroyuz.ddrchartviewer.data.remote.bjm.BjmClient
import com.harroyuz.ddrchartviewer.data.remote.icecream.IceCreamParser
import com.harroyuz.ddrchartviewer.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class Repository(context: Context) {
    private val catalogChanges = MutableSharedFlow<Catalog>(replay = 1)
    val catalogUpdates = catalogChanges.asSharedFlow()
    val jackets = JacketRepository(context)
    val store = LocalStore(context)
    val bjm = BjmClient(context)
    val skill = SkillCalculator(context.assets.open("flare-skill.json").bufferedReader().use { it.readText() })
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).callTimeout(50, TimeUnit.SECONDS).build()
    private val catalogMutex = Mutex()
    private val chartMutex = Mutex()
    companion object {
        const val DAY = 86_400_000L
        fun due(updatedAt: Long, now: Long = System.currentTimeMillis()) = updatedAt <= 0 || now < updatedAt || now - updatedAt >= DAY
    }

    suspend fun refreshCatalog(force: Boolean = false, progress: (String) -> Unit = {}): Catalog = withContext(Dispatchers.IO) {
        catalogMutex.withLock {
            val old = runCatching { store.readCatalog() }.getOrNull()
            if (!force && old != null && !due(old.updatedAt)) return@withLock old
            progress("读取 3icecream 全曲库…")
            val parsed = IceCreamParser.catalog(get("https://3icecream.com/js/songdata.js"), System.currentTimeMillis())
            val chartsByKey = parsed.songs.flatMap { it.charts }.associateBy { it.key }
            val semaphore = Semaphore(3)
            val pages = coroutineScope {
                PlayMode.entries.flatMap { mode -> (1..19).map { level -> async {
                    semaphore.withPermit {
                        ensureActive()
                        val html = get("https://3icecream.com/difficulty_list/$level?spdp=${mode.ordinal}")
                        IceCreamParser.difficultyPage(html, mode, level).also { page ->
                            require(page.keys.all { chartsByKey[it]?.level == level }) { "难度页与曲库等级不一致，请稍后重试" }
                        }
                    }
                } } }.awaitAll().flatMap { it.entries }.associate { it.key to it.value }
            }
            val next = parsed.copy(songs = parsed.songs.map { song -> song.copy(charts = song.charts.map { chart ->
                chart.copy(constant = pages[chart.key])
            }) }, updatedAt = System.currentTimeMillis())
            // Commit only after all 38 pages are valid. Failures leave the old snapshot intact.
            store.writeCatalog(next)
            catalogChanges.emit(next)
            next
        }
    }

    suspend fun syncScores(): ScoreSnapshot = withContext(Dispatchers.IO) {
        val music = bjm.fetchMusicDatabase()
        val result = bjm.fetchScores()
        ScoreSnapshot(result.user, music, result.scores, System.currentTimeMillis()).also(store::writeScores)
    }

    data class LoadedChart(val chart: ChartData, val stale: Boolean)
    suspend fun chart(ref: ChartRef): LoadedChart = withContext(Dispatchers.IO) {
        chartMutex.withLock {
            require(ref.songId.matches(Regex("[A-Za-z0-9]+")))
            val file = File(store.chartDirectory, "${ref.songId}.json")
            val cached = if (file.exists()) runCatching { file.readText() }.getOrNull() else null
            if (cached != null && !due(file.lastModified())) return@withLock LoadedChart(IceCreamParser.chart(cached, ref.slot), false)
            try {
                val data = get("https://3icecream.com/api/chart_info/${ref.songId}")
                val chart = IceCreamParser.chart(data, ref.slot)
                val atomic = AtomicFile(file); val output = atomic.startWrite()
                try { output.write(data.toByteArray()); atomic.finishWrite(output) } catch (e: Exception) { atomic.failWrite(output); throw e }
                trimCharts(file)
                LoadedChart(chart, false)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (cached != null) LoadedChart(IceCreamParser.chart(cached, ref.slot), true) else throw e
            }
        }
    }

    private fun trimCharts(current: File) {
        val files = store.chartDirectory.listFiles().orEmpty().filter { it.extension == "json" }.sortedBy { it.lastModified() }
        var bytes = files.sumOf { it.length() }
        for (file in files) { if (bytes <= 100L * 1024 * 1024) break; if (file != current) { val length = file.length(); if (file.delete()) bytes -= length } }
    }
    private fun get(url: String): String = client.newCall(Request.Builder().url(url).header("User-Agent", "DDRChartViewer/${BuildConfig.VERSION_NAME} (Android)").build()).execute().use {
        check(it.isSuccessful) { "数据源请求失败（HTTP ${it.code}）" }
        it.body?.string() ?: error("数据源返回空内容")
    }
}
