package com.harroyuz.ddrchartviewer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.launch

@Composable fun AppScreen(state: AppState, model: AppViewModel, login: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var songId by rememberSaveable { mutableStateOf<String?>(null) }
    val song = state.catalog?.songs?.find { it.id == songId }
    val matcher = remember(state.catalog, state.scores?.music) { CatalogMatcher(state.catalog?.songs.orEmpty(), state.scores?.music.orEmpty()) }
    val entriesByKey = remember(state.entries) { state.entries.associateBy { it.score.key } }
    val catalogScreenState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); model.dismissMessage() } }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    val pages = listOf("曲库", "成绩", "Skill", "设置")
    BackHandler(enabled = drawer.isOpen || state.chartRef != null || songId != null || tab != 0) {
        when {
            drawer.isOpen -> scope.launch { drawer.close() }
            state.chartRef != null -> model.closeChart()
            songId != null -> songId = null
            else -> tab = 0
        }
    }
    ModalNavigationDrawer(
        drawerState = drawer, gesturesEnabled = state.chartRef == null && songId == null,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(200.dp), drawerShape = RoundedCornerShape(0.dp)) {
                Column(Modifier.fillMaxSize()) {
                    Text("菜单", Modifier.padding(start = 24.dp, top = 8.dp, bottom = 16.dp), color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    pages.forEachIndexed { index, label ->
                        NavigationDrawerItem(label = { Text(label) }, selected = tab == index,
                            onClick = { tab = index; scope.launch { drawer.close() } },
                            shape = RoundedCornerShape(0.dp), modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    HorizontalDivider()
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                        Text("版本 ${BuildConfig.VERSION_NAME}", color = Muted, fontSize = 11.sp)
                        TextButton({ uri.openUri("https://github.com/HarroYuz/ddr-chart-viewer") }, contentPadding = PaddingValues(0.dp)) { Text("项目主页  ↗") }
                    }
                }
            }
        },
    ) {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (state.chartRef != null) ChartScreen(state, model)
                else {
                    if (song != null) SimpleTopBar("曲目详情") { songId = null }
                    else AppTopBar(pages[tab], { scope.launch { drawer.open() } }, menu = true) {
                        OutlinedButton({ model.mode(if (state.mode == PlayMode.SP) PlayMode.DP else PlayMode.SP) },
                            Modifier.width(64.dp).height(36.dp).semantics { contentDescription = "切换 SP/DP" }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text(state.mode.name) }
                        Spacer(Modifier.width(12.dp))
                        Surface(onClick = { tab = 1 }, shape = RoundedCornerShape(20.dp), color = Purple.copy(alpha = .12f),
                            modifier = Modifier.size(40.dp).semantics { contentDescription = "BJMania 账户与成绩" }) {
                            Box(contentAlignment = Alignment.Center) { Text(state.scores?.user?.name?.take(1)?.uppercase() ?: "♙", color = Purple, fontWeight = FontWeight.Bold) }
                        }
                    }
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (song != null) SongDetail(song, state, matcher, entriesByKey, model)
                    else when (tab) {
                        0 -> catalogScreenState.SaveableStateProvider("catalog") { CatalogScreen(state, model, matcher, entriesByKey) { songId = it } }
                        1, 2 -> ScoreScreen(state, tab == 2, matcher, model.repository.jackets, login, model::syncScores) { ref -> songId = ref.songId; model.openChart(ref) }
                        else -> SettingsScreen(state, model, login)
                    }
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
    var filter by rememberSaveable { mutableStateOf(CatalogFilter()) }
    var order by rememberSaveable { mutableStateOf(CatalogOrder.TITLE) }
    var descending by rememberSaveable { mutableStateOf(false) }
    val songs = remember(state.catalog, state.mode, state.favorites, matcher, entries, search, filter, order, descending) {
        browseCatalog(state.catalog?.songs.orEmpty(), state.mode, search, filter, state.favorites, order, descending, matcher, entries)
    }
    val tiers = remember(state.catalog, state.mode) {
        state.catalog?.songs.orEmpty().flatMap { it.charts }.filter { it.mode == state.mode }
            .map { it.tier() }.distinct().sortedBy { if (it == NO_TIER) 100 else it }
    }
    val versions = remember(state.catalog) {
        (catalogVersions.values.flatten() + state.catalog?.songs.orEmpty().map { it.version }).distinct().sorted()
    }
    val resultList = key(state.mode, search, filter, order, descending) { rememberLazyListState() }
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(search, { search = it }, modifier = Modifier.weight(1f), singleLine = true,
                placeholder = { Text(when { filter.searchTitles && filter.searchArtists -> "搜索曲名 / 曲师"; filter.searchArtists -> "搜索曲师"; filter.searchTitles -> "搜索曲名 / 别名"; else -> "请选择搜索范围" }) }, shape = MaterialTheme.shapes.medium,
                trailingIcon = { if (search.isNotEmpty()) IconButton({ search = "" }) { Text("×", fontSize = 20.sp) } })
            IconButton({ filtersExpanded = !filtersExpanded }, Modifier.semantics { contentDescription = "展开或收起筛选" }) {
                val color = if (filtersExpanded || filter != CatalogFilter()) Purple else Muted
                Canvas(Modifier.size(22.dp)) {
                    val path = Path().apply {
                        moveTo(size.width * .1f, size.height * .2f); lineTo(size.width * .9f, size.height * .2f)
                        lineTo(size.width * .6f, size.height * .55f); lineTo(size.width * .6f, size.height * .8f)
                        lineTo(size.width * .4f, size.height * .9f); lineTo(size.width * .4f, size.height * .55f); close()
                    }
                    drawPath(path, color, style = Stroke(2.dp.toPx()))
                }
            }
        }
        AnimatedVisibility(filtersExpanded) {
            CatalogFilterPanel(filter, { filter = it }, tiers, versions)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${songs.size} 首曲目 · ${state.mode}", color = Muted, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            CatalogSortControls(order, descending, { order = it }, { descending = !descending })
        }
        if (state.loading) EmptyState("正在读取本地曲库…")
        else if (state.catalog == null) EmptyState(if (state.busy) "正在下载曲目与 38 个难度列表…" else "尚无曲库缓存", "联网完成首次同步后，可离线浏览") { Button(onClick = { model.refresh() }, enabled = !state.busy) { Text("获取曲库") } }
        else if (songs.isEmpty()) EmptyState("没有匹配的曲目", "试试其他等级、模式或关键词")
        else LazyColumn(Modifier.weight(1f), state = resultList, contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(songs, key = { it.song.id }) { row ->
                val song = row.song
                Card(onClick = { select(song.id) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, Outline)) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Jacket(song.id, song.title, model.repository.jackets, Modifier.size(52.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(versionName(song.version) + if (song.deleted) " · 删除曲" else "", color = Muted, fontSize = 10.sp)
                                Text(song.title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(row.artist.ifBlank { "曲师未收录" }, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (order == CatalogOrder.SCORE || order == CatalogOrder.FLARE) {
                                Spacer(Modifier.width(6.dp))
                                Text("${order.label} ${row.value ?: "—"}", color = Purple, fontSize = 11.sp)
                            }
                        }
                        Spacer(Modifier.height(9.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            ChartButtons(row.charts, matcher, entries, model::openChart, Modifier.weight(1f))
                            Spacer(Modifier.width(6.dp))
                            IconButton({ model.favorite(song.id) }, Modifier.size(36.dp).semantics { contentDescription = "收藏 ${song.title}" }) {
                                Text(if (song.id in state.favorites) "★" else "☆", color = if (song.id in state.favorites) Purple else Muted, fontSize = 24.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun ChartButtons(charts: List<ChartRef>, matcher: CatalogMatcher, entries: Map<String, SkillEntry>, open: (ChartRef) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        charts.forEach { chart ->
            val color = difficultyColor(chart.difficulty)
            val score = matcher.score(chart, entries)?.score
            Surface(onClick = { open(chart) }, shape = RoundedCornerShape(14.dp),
                color = color.copy(alpha = .13f),
                border = BorderStroke(1.dp, color.copy(alpha = .55f)),
                modifier = Modifier.size(width = 54.dp, height = 50.dp).semantics {
                    contentDescription = "${chart.mode} ${chart.difficulty.shortName} ${chart.level}${difficultySuffix(chart.level, chart.constant).orEmpty()}"
                }) {
                Box(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 3.dp)) {
                    DifficultyNumber(chart.level, chart.constant, Modifier.align(Alignment.TopCenter), color, mainSize = 21.sp, fractionSize = 11.sp)
                    if (score != null) {
                        if (score.flare in 1..10) Text(ScoreLabels.flare(score.flare), color = color, fontSize = 10.sp, lineHeight = 11.sp,
                            fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.align(Alignment.BottomStart))
                        if (score.rank in 0..15) Text(ScoreLabels.rank(score.rank), color = Ink, fontSize = 10.sp, lineHeight = 11.sp,
                            fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.align(Alignment.BottomEnd))
                    }
                }
            }
        }
    }
}

@Composable private fun SongDetail(song: Song, state: AppState, matcher: CatalogMatcher, entries: Map<String, SkillEntry>, model: AppViewModel) {
    val uri = LocalUriHandler.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Jacket(song.id, song.title, model.repository.jackets, Modifier.size(60.dp))
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(versionName(song.version), color = Muted, style = MaterialTheme.typography.labelSmall)
                Text(song.title, color = Ink, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold)
                Text(song.artist.ifBlank { matcher.music(song)?.artist.orEmpty() }.ifBlank { "曲师未收录" },
                    color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            val favorite = song.id in state.favorites
            IconToggleButton(checked = favorite, onCheckedChange = { model.favorite(song.id) },
                modifier = Modifier.semantics { contentDescription = "收藏曲目" }) {
                Text(if (favorite) "★" else "☆", color = if (favorite) Purple else Muted, fontSize = 23.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            ModeSwitch(state.mode, model::mode)
            TextButton({ uri.openUri("https://3icecream.com/ddr/song_details/${song.id}") }) { Text("3icecream ↗") }
        }
        song.charts.filter { it.mode == state.mode }.forEach { chart ->
            DetailChartRow(chart, matcher.score(chart, entries)) { model.openChart(chart) }
        }
    }
}

@Composable private fun DetailChartRow(chart: ChartRef, entry: SkillEntry?, open: () -> Unit) {
    val accent = difficultyColor(chart.difficulty)
    Surface(onClick = open, shape = MaterialTheme.shapes.small, color = accent.copy(alpha = .06f)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(chart.difficulty.shortName, color = accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                DifficultyNumber(chart.level, chart.constant, color = accent, mainSize = 21.sp, fractionSize = 11.sp)
            }
            Column(Modifier.weight(2f), horizontalAlignment = Alignment.End) {
                if (entry == null) {
                    Text("暂无成绩", color = Muted, style = MaterialTheme.typography.bodySmall)
                } else {
                    val score = entry.score
                    Text("${"%,d".format(Locale.ROOT, score.score)}  ${ScoreLabels.rank(score.rank)}", color = Ink,
                        fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
                    Text("${ScoreLabels.clear(score.clearKind)} · FLARE ${ScoreLabels.flare(score.flare)} · Skill ${entry.skill ?: "—"}",
                        color = Muted, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
                    Text(listOfNotNull(if (entry.inTarget) "IN TARGET" else null, date(score.time * 1000)).joinToString(" · "),
                        color = if (entry.inTarget) Purple else Muted, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
                }
            }
        }
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
                Card(colors = CardDefaults.cardColors(containerColor = Purple.copy(alpha = .1f))) {
                    Column(Modifier.padding(20.dp).fillMaxWidth()) {
                        Text("${state.mode} FLARE SKILL", color = Purple, letterSpacing = 2.sp, style = MaterialTheme.typography.labelMedium)
                        Text("${summaries.sumOf { it.total }}", fontSize = 44.sp, fontWeight = FontWeight.Black)
                        Text(SkillRank.name(summaries.sumOf { it.total }), color = Purple, fontWeight = FontWeight.Bold)
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${entry.score.mode} ${Difficulty.fromSlot(entry.score.slot).shortName}", color = Muted, style = MaterialTheme.typography.labelSmall)
                        DifficultyNumber(entry.music?.level(entry.score.slot), chart?.constant, mainSize = 20.sp, fractionSize = 11.sp)
                        Text(entry.music?.era?.name ?: "未知年代", color = Muted, style = MaterialTheme.typography.labelSmall)
                    }
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
        Text(ScoreLabels.rank(score.rank), color = Purple, fontWeight = FontWeight.Bold)
    }
    Text("${ScoreLabels.clear(score.clearKind)} · FLARE ${ScoreLabels.flare(score.flare)} · Skill ${entry.skill ?: "—"}", style = MaterialTheme.typography.bodySmall)
    if (entry.inTarget) Text("IN TARGET", color = Purple, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
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
