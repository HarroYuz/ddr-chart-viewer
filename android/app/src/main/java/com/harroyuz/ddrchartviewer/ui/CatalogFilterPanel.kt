package com.harroyuz.ddrchartviewer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harroyuz.ddrchartviewer.domain.*

@Composable
internal fun CatalogFilterPanel(filter: CatalogFilter, change: (CatalogFilter) -> Unit, tiers: List<Int>, versions: List<Int>) {
    var section by rememberSaveable { mutableStateOf("") }
    val height = (LocalConfiguration.current.screenHeightDp * .38f).dp
    Column(Modifier.fillMaxWidth().heightIn(max = height).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("搜索范围", fontSize = 12.sp, color = Muted)
            Choice("曲名", filter.searchTitles, Modifier.weight(1f)) { change(filter.copy(searchTitles = !filter.searchTitles)) }
            Choice("曲师", filter.searchArtists, Modifier.weight(1f)) { change(filter.copy(searchArtists = !filter.searchArtists)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Choice("仅收藏", filter.favoritesOnly, Modifier.weight(1f)) { change(filter.copy(favoritesOnly = !filter.favoritesOnly)) }
            Choice("含删除曲", filter.includeDeleted, Modifier.weight(1f)) { change(filter.copy(includeDeleted = !filter.includeDeleted)) }
            Choice("重置筛选", false) { change(CatalogFilter()); section = "" }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((label, count) in listOf("整数" to filter.levels.size, "小数" to filter.tiers.size, "版本" to filter.versions.size)) {
                val expanded = label == section
                val summary = if (count == 0) "不限" else "${count}项"
                Choice("$label · $summary ${if (expanded) "▴" else "▾"}", expanded, Modifier.weight(1f)) {
                    section = if (expanded) "" else label
                }
            }
        }
        if (section.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("可多选，未选时不限", Modifier.weight(1f), color = Muted, fontSize = 11.sp)
                Choice("不限", false) {
                    change(when (section) {
                        "整数" -> filter.copy(levels = emptySet())
                        "小数" -> filter.copy(tiers = emptySet())
                        else -> filter.copy(versions = emptySet())
                    })
                }
            }
            when (section) {
                "整数" -> Grid((1..19).toList(), 5, filter.levels, { it.toString() }) { change(filter.copy(levels = filter.levels.toggle(it))) }
                "小数" -> Grid(tiers, 5, filter.tiers, { if (it == NO_TIER) "无定数" else "." + it.toString().padStart(2, '0') }) {
                    change(filter.copy(tiers = filter.tiers.toggle(it)))
                }
                "版本" -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for ((era, children) in catalogVersions) {
                            val count = children.count { it in filter.versions }
                            val total = children.count()
                            val label = if (count in 1 until total) "${era.name} $count/$total" else era.name
                            Choice(label, count == total, Modifier.weight(1f)) { change(filter.toggleEra(era)) }
                        }
                    }
                    Grid(versions, 3, filter.versions, ::versionName) { change(filter.copy(versions = filter.versions.toggle(it))) }
                }
            }
        }
    }
}

@Composable private fun Grid(items: List<Int>, columns: Int, selected: Set<Int>, label: (Int) -> String, toggle: (Int) -> Unit) {
    items.chunked(columns).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { value -> Choice(label(value), value in selected, Modifier.weight(1f)) { toggle(value) } }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable private fun Choice(label: String, selected: Boolean, modifier: Modifier = Modifier, click: () -> Unit) {
    Surface(selected = selected, onClick = click, modifier = modifier.heightIn(min = 32.dp),
        shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, if (selected) Purple else Outline),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
        Box(Modifier.padding(horizontal = 5.dp, vertical = 3.dp), contentAlignment = Alignment.Center) {
            Text(label, color = if (selected) Purple else Ink, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable internal fun CatalogSortControls(order: CatalogOrder, descending: Boolean, choose: (CatalogOrder) -> Unit, reverse: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        TextButton({ menu = true }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("排序 · ${order.label} ▾", fontSize = 12.sp) }
        DropdownMenu(menu, { menu = false }) {
            CatalogOrder.entries.forEach { candidate ->
                DropdownMenuItem({ Text(candidate.label, color = if (candidate == order) Purple else Ink) }, { choose(candidate); menu = false })
            }
        }
    }
    TextButton(reverse, contentPadding = PaddingValues(horizontal = 4.dp)) { Text(if (descending) "↓ 降序" else "↑ 升序", fontSize = 12.sp) }
}
