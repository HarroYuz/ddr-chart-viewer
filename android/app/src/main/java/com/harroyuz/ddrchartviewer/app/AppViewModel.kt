package com.harroyuz.ddrchartviewer.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.harroyuz.ddrchartviewer.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AppState(
    val catalog: Catalog? = null, val scores: ScoreSnapshot? = null,
    val entries: List<SkillEntry> = emptyList(), val summaries: List<SkillSummary> = emptyList(),
    val mode: PlayMode = PlayMode.SP, val favorites: Set<String> = emptySet(),
    val loading: Boolean = true, val busy: Boolean = false, val message: String? = null,
    val chartRef: ChartRef? = null, val chart: ChartData? = null, val chartLoading: Boolean = false, val chartError: String? = null,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    val repository = (application as DdrApplication).repository
    private val mutable = MutableStateFlow(AppState(mode = repository.store.mode, favorites = repository.store.favorites()))
    val state = mutable.asStateFlow()
    private var chartJob: Job? = null
    init {
        viewModelScope.launch {
            var failure: String? = null
            val catalog = withContext(Dispatchers.IO) { runCatching { repository.store.readCatalog() }.onFailure { failure = "曲库缓存损坏，正在重新获取" }.getOrNull() }
            val scores = withContext(Dispatchers.IO) { runCatching { repository.store.readScores() }.onFailure { failure = "成绩缓存无法读取，请重新同步" }.getOrNull() }
            mutable.update { it.copy(catalog = catalog, loading = false, message = failure) }
            setScores(scores)
            launch { repository.catalogUpdates.collect { catalog -> mutable.update { it.copy(catalog = catalog) } } }
            refresh(false)
        }
    }
    fun mode(mode: PlayMode) { repository.store.mode = mode; mutable.update { it.copy(mode = mode) } }
    fun favorite(id: String) {
        val ids = mutable.value.favorites.let { if (id in it) it - id else it + id }
        repository.store.saveFavorites(ids); mutable.update { it.copy(favorites = ids) }
    }
    fun dismissMessage() { mutable.update { it.copy(message = null) } }
    fun refresh(force: Boolean = true) = task {
        val catalog = repository.refreshCatalog(force) { progress -> mutable.update { it.copy(message = progress) } }
        mutable.update { it.copy(catalog = catalog, message = if (force) "曲库已更新：${catalog.songs.size} 首" else null) }
    }
    fun syncScores() = task { setScores(repository.syncScores()); mutable.update { it.copy(message = "BJMania 成绩、Flare 与 Skill 已同步") } }
    fun logout() = task {
        repository.bjm.clearSession()
        withContext(Dispatchers.IO) { repository.store.clearScores() }
        setScores(null)
        mutable.update { it.copy(message = "已退出 BJMania，并清除本机成绩") }
    }
    private fun setScores(snapshot: ScoreSnapshot?) {
        val entries = snapshot?.let(repository.skill::entries).orEmpty()
        mutable.update { it.copy(scores = snapshot, entries = entries, summaries = repository.skill.summaries(entries)) }
    }
    fun openChart(ref: ChartRef) {
        chartJob?.cancel()
        mutable.update { it.copy(chartRef = ref, chart = null, chartLoading = true, chartError = null) }
        chartJob = viewModelScope.launch {
            try {
                val result = repository.chart(ref)
                mutable.update { it.copy(chart = result.chart, chartLoading = false, message = if (result.stale) "网络更新失败，正在播放上次缓存的谱面" else null) }
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { mutable.update { it.copy(chartLoading = false, chartError = e.message ?: "谱面读取失败") } }
        }
    }
    fun closeChart() { chartJob?.cancel(); mutable.update { it.copy(chartRef = null, chart = null, chartLoading = false, chartError = null) } }
    private fun task(action: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true) }
        viewModelScope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(message = "${e.message ?: "同步失败"}；已保留上次成功的数据") } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
}
