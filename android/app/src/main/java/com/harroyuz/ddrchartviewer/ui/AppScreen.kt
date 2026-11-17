package com.harroyuz.ddrchartviewer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harroyuz.ddrchartviewer.BuildConfig
import com.harroyuz.ddrchartviewer.app.*
import com.harroyuz.ddrchartviewer.data.JacketRepository
import com.harroyuz.ddrchartviewer.domain.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable fun AppScreen(state: AppState, model: AppViewModel, login: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var songId by rememberSaveable { mutableStateOf<String?>(null) }
    val song = state.catalog?.songs?.find { it.id == songId }
    val matcher = remember(state.catalog, state.scores?.music) { CatalogMatcher(state.catalog?.songs.orEmpty(), state.scores?.music.orEmpty()) }
    val entriesByKey = remember(state.entries) { state.entries.associateBy { it.score.key } }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); model.dismissMessage() } }
    BackHandler(enabled = state.chartRef != null || songId != null) { if (state.chartRef != null) model.closeChart() else songId = null }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { if (state.chartRef == null && songId == null) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            listOf("曲库" to "▤", "成绩" to "◈", "Skill" to "✧", "设置" to "☷").forEachIndexed { index, (label, glyph) ->
                NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Text(glyph, fontSize = 23.sp) }, label = { Text(label) })
            }
        } },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.chartRef != null) {
                ChartScreen(state, model)
            } else {
                if (song != null) SimpleTopBar("曲目详情") { songId = null }
                else Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("DDR", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = Mint)
                        Text("CHART VIEWER", style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp, color = Muted)
                    }
                    ModeSwitch(state.mode, model::mode)
                }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (song != null) SongDetail(song, state, matcher, entriesByKey, model)
                else when (tab) {
                    0 -> CatalogScreen(state, model, matcher, entriesByKey, { songId = it })
                    1, 2 -> ScoreScreen(state, tab == 2, matcher, model.repository.jackets, login, model::syncScores) { ref -> songId = ref.songId; model.openChart(ref) }
                    else -> SettingsScreen(state, model, login)
                }
            }
        }
    }
}

@Composable private fun ModeSwitch(mode: PlayMode, change: (PlayMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PlayMode.entries.forEach { m -> FilterChip(selected = mode == m, onClick = { change(m) }, label = { Text(m.name, fontWeight = FontWeight.Bold) }) }
    }
}

@Composable private fun CatalogScreen(state: AppState, model: AppViewModel, matcher: CatalogMatcher, entries: Map<String, SkillEntry>, select: (String) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var level by rememberSaveable { mutableIntStateOf(0) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var showDeleted by rememberSaveable { mutableStateOf(false) }
    val songs = remember(state.catalog, search, level, state.mode, favoritesOnly, state.favorites, showDeleted) {
        state.catalog?.songs.orEmpty().filter { song ->
            (showDeleted || !song.deleted) && (!favoritesOnly || song.id in state.favorites) &&
                (search.isBlank() || "${song.title} ${song.aliases}".contains(search.trim(), true)) &&
                song.charts.any { it.mode == state.mode && (level == 0 || it.level == level) }
        }.sortedBy { CatalogMatcher.normalize(it.title) }
    }
    Column {
        OutlinedTextField(search, { search = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true,
            placeholder = { Text("搜索曲名 / 别名") }, shape = RoundedCornerShape(16.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(level == 0, { level = 0 }, { Text("全部等级") })
            (1..19).forEach { n -> FilterChip(level == n, { level = if (level == n) 0 else n }, { Text("$n") }) }
        }
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${songs.size} 首 · ${state.mode}", color = Muted, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            TextButton({ favoritesOnly = !favoritesOnly }) { Text(if (favoritesOnly) "★ 已收藏" else "☆ 收藏") }
            TextButton({ showDeleted = !showDeleted }) { Text(if (showDeleted) "含删除曲" else "稼动曲") }
        }
        if (state.loading) EmptyState("正在读取本地曲库…")
        else if (state.catalog == null) EmptyState(if (state.busy) "正在下载曲目与 38 个难度列表…" else "尚无曲库缓存", "联网完成首次同步后，可离线浏览") { Button(onClick = { model.refresh() }, enabled = !state.busy) { Text("获取曲库") } }
        else if (songs.isEmpty()) EmptyState("没有匹配的曲目", "试试其他等级、模式或关键词")
        else LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(songs, key = { it.id }) { song ->
                Card(onClick = { select(song.id) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Jacket(song.id, song.title, model.repository.jackets, Modifier.size(64.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(versionName(song.version) + if (song.deleted) " · 删除曲" else "", color = Muted, style = MaterialTheme.typography.labelSmall)
                                Text(song.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            TextButton({ model.favorite(song.id) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(40.dp)) { Text(if (song.id in state.favorites) "★" else "☆", fontSize = 23.sp) }
                        }
                        Spacer(Modifier.height(12.dp))
                        ChartButtons(song, state.mode, level, matcher, entries, model::openChart)
                    }
                }
            }
        }
    }
}

@Composable private fun ChartButtons(song: Song, mode: PlayMode, filter: Int, matcher: CatalogMatcher, entries: Map<String, SkillEntry>, open: (ChartRef) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        song.charts.filter { it.mode == mode }.forEach { chart ->
            val color = difficultyColor(chart.difficulty)
            Surface(onClick = { open(chart) }, shape = RoundedCornerShape(10.dp), color = color.copy(alpha = if (filter == 0 || chart.level == filter) .12f else .04f), border = BorderStroke(1.dp, color.copy(alpha = .35f)), modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(chart.difficulty.shortName.take(3), color = color, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                    Text("${chart.level}", fontWeight = FontWeight.Bold, fontSize = 23.sp)
                    Text(chart.constant?.let { "%.2f".format(Locale.ROOT, it) } ?: "—", color = Muted, fontSize = 10.sp)
                    Text(if (matcher.score(chart, entries) != null) "●" else " ", color = Mint, fontSize = 8.sp)
                }
            }
        }
    }
}

@Composable private fun SongDetail(song: Song, state: AppState, matcher: CatalogMatcher, entries: Map<String, SkillEntry>, model: AppViewModel) {
    val uri = LocalUriHandler.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Jacket(song.id, song.title, model.repository.jackets, Modifier.size(160.dp).align(Alignment.CenterHorizontally))
        Text(versionName(song.version), color = Mint, style = MaterialTheme.typography.labelLarge)
        Text(song.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        matcher.music(song)?.artist?.takeIf { it.isNotBlank() }?.let { Text(it, color = Muted) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ModeSwitch(state.mode, model::mode)
            Spacer(Modifier.weight(1f))
            TextButton({ model.favorite(song.id) }) { Text(if (song.id in state.favorites) "★ 已收藏" else "☆ 收藏曲目") }
        }
        Text("参考定数来自社区难度表，仅供选曲参考。", color = Muted, style = MaterialTheme.typography.bodySmall)
        song.charts.filter { it.mode == state.mode }.forEach { chart ->
            Card(onClick = { model.openChart(chart) }) {
                Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row { Text("${chart.mode} ${chart.difficulty.shortName}", color = difficultyColor(chart.difficulty), modifier = Modifier.weight(1f)); Text("Lv.${chart.level}", fontWeight = FontWeight.Bold) }
                    Text("参考定数 ${chart.constant?.let { "%.2f".format(Locale.ROOT, it) } ?: "暂无"}", color = Muted)
                    matcher.score(chart, entries)?.let { ScoreDetail(it) } ?: Text("暂无已匹配成绩", color = Muted)
                    Text("播放谱面  →", color = Mint)
                }
            }
        }
        TextButton({ uri.openUri("https://3icecream.com/ddr/song_details/${song.id}") }) { Text("在 3icecream 查看曲目 / 视频") }
    }
}

@Composable private fun ScoreScreen(state: AppState, skillPage: Boolean, matcher: CatalogMatcher, jackets: JacketRepository, login: () -> Unit, sync: () -> Unit, open: (ChartRef) -> Unit) {
    var targetFilter by rememberSaveable(skillPage) { mutableIntStateOf(if (skillPage) 1 else 0) }
    var search by rememberSaveable(skillPage) { mutableStateOf("") }
    var eraFilter by rememberSaveable(skillPage) { mutableStateOf<String?>(null) }
    val visible = remember(state.entries, state.mode, targetFilter, search, eraFilter, skillPage) {
        state.entries.filter {
            it.score.mode == state.mode && (targetFilter == 0 || it.inTarget == (targetFilter == 1)) &&
                (eraFilter == null || it.music?.era?.name == eraFilter) &&
                (search.isBlank() || (it.music?.title ?: "#${it.score.musicId}").contains(search, true))
        }.let { if (skillPage) it else it.sortedByDescending { row -> row.score.time } }
    }
    if (state.scores == null) {
        EmptyState("连接 BJMania", "登录后同步 DDR 成绩、Flare 等级与 Skill 对象曲。") { Button(login, enabled = !state.busy) { Text("登录 BJMania") } }
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(state.scores.user.name, fontWeight = FontWeight.Bold); Text("同步于 ${date(state.scores.updatedAt)}", color = Muted, style = MaterialTheme.typography.labelSmall) }
                OutlinedButton(sync, enabled = !state.busy) { Text("同步") }
            }
        }
        if (skillPage) {
            item {
                val summaries = state.summaries.filter { it.mode == state.mode }
                Card(colors = CardDefaults.cardColors(containerColor = Mint.copy(alpha = .1f))) {
                    Column(Modifier.padding(20.dp).fillMaxWidth()) {
                        Text("${state.mode} FLARE SKILL", color = Mint, letterSpacing = 2.sp, style = MaterialTheme.typography.labelMedium)
                        Text("${summaries.sumOf { it.total }}", fontSize = 44.sp, fontWeight = FontWeight.Black)
                        Text(SkillRank.name(summaries.sumOf { it.total }), color = Mint, fontWeight = FontWeight.Bold)
                        Text("${summaries.sumOf { it.count }} / 90 张对象谱面", color = Muted)
                        Spacer(Modifier.height(16.dp))
                        summaries.forEach { summary ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                Text(summary.era.name, Modifier.weight(1f), color = Muted, style = MaterialTheme.typography.labelMedium)
                                Text("${summary.total}  ·  ${summary.count}/30  ·  下限 ${summary.border}", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                Text("按 BJMania 规则，每个年代取 30 张合格谱面；与社区参考定数无关。", Modifier.padding(top = 10.dp), color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        item { OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), placeholder = { Text("搜索成绩曲名") }, singleLine = true, shape = RoundedCornerShape(14.dp)) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("全部", "IN TARGET", "NOT IN TARGET").forEachIndexed { index, label -> FilterChip(targetFilter == index, { targetFilter = index }, { Text(label) }) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(eraFilter == null, { eraFilter = null }, { Text("全部年代") })
                Era.entries.forEach { era -> FilterChip(eraFilter == era.name, { eraFilter = era.name }, { Text(era.name) }) }
            }
            Text("${visible.size} 条成绩", color = Muted, style = MaterialTheme.typography.labelMedium)
        }
        if (visible.isEmpty()) item { EmptyState("暂无符合筛选的成绩", "可切换 SP/DP 或重新同步") }
        items(visible, key = { it.score.key }) { entry ->
            val chart = matcher.chart(entry)
            Card(modifier = if (chart != null) Modifier.clickable { open(chart) } else Modifier) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (chart != null) {
                            Jacket(chart.songId, entry.music?.title.orEmpty(), jackets, Modifier.size(48.dp))
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(entry.music?.title ?: "未匹配曲目 #${entry.score.musicId}", fontWeight = FontWeight.Bold, maxLines = 2)
                    }
                    Text("${entry.score.mode} ${Difficulty.fromSlot(entry.score.slot).shortName} · Lv.${entry.music?.level(entry.score.slot) ?: "?"} · ${entry.music?.era?.name ?: "未知年代"}", color = Muted, style = MaterialTheme.typography.labelSmall)
                    ScoreDetail(entry)
                    if (chart == null) Text("尚未关联 3icecream 谱面", color = Muted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable private fun ScoreDetail(entry: SkillEntry) {
    val score = entry.score
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("%,d".format(Locale.ROOT, score.score), fontWeight = FontWeight.Bold, fontSize = 24.sp, modifier = Modifier.weight(1f))
        Text(ScoreLabels.rank(score.rank), color = Mint, fontWeight = FontWeight.Bold)
    }
    Text("${ScoreLabels.clear(score.clearKind)} · FLARE ${ScoreLabels.flare(score.flare)} · Skill ${entry.skill ?: "—"}", style = MaterialTheme.typography.bodySmall)
    if (entry.inTarget) Text("IN TARGET", color = Mint, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
    Text(date(score.time * 1000), color = Muted, style = MaterialTheme.typography.labelSmall)
}

@Composable private fun SettingsScreen(state: AppState, model: AppViewModel, login: () -> Unit) {
    val uri = LocalUriHandler.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("数据与账户", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Card {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("3icecream 曲库", fontWeight = FontWeight.Bold)
                Text("本机更新：${date(state.catalog?.updatedAt ?: 0)}\n源数据更新：${date(state.catalog?.sourceUpdatedAt ?: 0)}", color = Muted, style = MaterialTheme.typography.bodySmall)
                Text("每天自动检查曲目、等级与参考定数。Android 可能延后后台任务；打开应用会补查。", style = MaterialTheme.typography.bodySmall)
                Text("曲绘按曲目 ID 永久缓存，已下载的 ID 不会重复请求；更新曲库不会刷新曲绘。", style = MaterialTheme.typography.bodySmall)
                Button({ model.refresh() }, enabled = !state.busy) { Text("立即更新曲库") }
            }
        }
        Card {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("BJMania", fontWeight = FontWeight.Bold)
                Text(state.scores?.let { "${it.user.name} · ${it.scores.size} 条成绩\n${date(it.updatedAt)}" } ?: "尚未同步账户", color = Muted)
                Button(login, enabled = !state.busy) { Text("登录 / 恢复登录") }
                OutlinedButton(model::syncScores, enabled = !state.busy) { Text("同步成绩、Flare、Skill") }
                TextButton(model::logout, enabled = !state.busy) { Text("退出登录并清除本机成绩") }
            }
        }
        Text("关于", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("DDR Chart Viewer ${BuildConfig.VERSION_NAME}\n原生 Android 谱面浏览与无音乐播放。", color = Muted)
        Text("参考定数只是社区难度参考。Skill 使用 BJMania 的等级 / Flare 对照表与对象曲规则。成绩仅在本机缓存，退出登录会清除。", style = MaterialTheme.typography.bodySmall)
        TextButton({ uri.openUri("https://3icecream.com/difficulty_list/15") }) { Text("数据来源：3icecream") }
        TextButton({ uri.openUri("https://u.bjmania.com/panel/ddr/score") }) { Text("成绩来源：BJMania") }
        TextButton({ uri.openUri("https://github.com/HarroYuz/ddr-chart-viewer") }) { Text("GitHub · 源代码与反馈") }
        TextButton({ uri.openUri("https://github.com/HarroYuz/ddr-chart-viewer/blob/main/LICENSE") }) { Text("GNU GPL v3 · 沿用 IIDX Chart Viewer 许可") }
    }
}

@Composable fun EmptyState(title: String, subtitle: String = "", action: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (subtitle.isNotBlank()) Text(subtitle, color = Muted, style = MaterialTheme.typography.bodyMedium)
        action()
    }
}
private fun date(millis: Long): String = if (millis <= 0) "暂无" else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
private fun versionName(version: Int) = listOf("未知版本", "DDR", "2ndMIX", "3rdMIX", "4thMIX", "5thMIX", "DDRMAX", "DDRMAX2", "EXTREME", "SuperNOVA", "SuperNOVA2", "X", "X2", "X3", "DDR 2013", "DDR 2014", "A", "A20", "A20 PLUS", "A3", "WORLD").getOrElse(version) { "版本 $version" }
