package com.harroyuz.ddrchartviewer.domain

enum class PlayMode(val lanes: Int) { SP(4), DP(8) }

enum class Difficulty(val shortName: String) {
    BEGINNER("BEGINNER"), BASIC("BASIC"), DIFFICULT("DIFFICULT"), EXPERT("EXPERT"), CHALLENGE("CHALLENGE");
    companion object {
        fun fromSlot(slot: Int) = entries[if (slot <= 4) slot else slot - 4]
    }
}

data class ChartRef(val songId: String, val slot: Int, val level: Int, val constant: Double?) {
    val key get() = "$songId/$slot"
    val mode get() = if (slot <= 4) PlayMode.SP else PlayMode.DP
    val difficulty get() = Difficulty.fromSlot(slot)
}

data class Song(
    val id: String, val title: String, val aliases: String, val version: Int,
    val deleted: Boolean, val charts: List<ChartRef>,
)

data class Catalog(val songs: List<Song>, val updatedAt: Long, val sourceUpdatedAt: Long = 0)
data class BjmUser(val id: String, val name: String, val email: String = "")
data class BjmMusic(val id: Int, val title: String, val artist: String, val series: Int, val levels: List<Int>) {
    // The music database has a DP-BEGINNER placeholder; score/chart slots do not.
    fun level(slot: Int): Int = levels.getOrElse(if (slot >= 5) slot + 1 else slot) { 0 }
    val era: Era get() = when { series <= 13 -> Era.CLASSIC; series <= 17 -> Era.WHITE; else -> Era.GOLD }
}
enum class Era { CLASSIC, WHITE, GOLD }
data class BjmScore(
    val musicId: Int, val slot: Int, val rank: Int, val clearKind: Int,
    val score: Int, val time: Long, val flare: Int,
) {
    val key get() = "$musicId/$slot"
    val mode get() = if (slot < 5) PlayMode.SP else PlayMode.DP
}
data class ScoreSnapshot(val user: BjmUser, val music: List<BjmMusic>, val scores: List<BjmScore>, val updatedAt: Long)
data class SkillEntry(val score: BjmScore, val music: BjmMusic?, val skill: Int?, val inTarget: Boolean = false)
data class SkillSummary(val mode: PlayMode, val era: Era, val total: Int, val count: Int, val border: Int)

data class TimingEvent(val beat: Double, val value: Double)
enum class NoteKind { TAP, HOLD, ROLL, SHOCK, LIFT, FAKE }
data class Note(val beat: Double, val lane: Int, val kind: NoteKind, val endBeat: Double = beat)
data class ChartData(
    val title: String, val artist: String, val mode: PlayMode, val notes: List<Note>,
    val bpms: List<TimingEvent>, val stops: List<TimingEvent>, val delays: List<TimingEvent>,
    val offset: Double, val endBeat: Double,
) {
    val timeline by lazy { Timeline(bpms, stops, delays, offset, endBeat) }
}

object ScoreLabels {
    private val ranks = listOf("AAA", "AA+", "AA", "AA−", "A+", "A", "A−", "B+", "B", "B−", "C+", "C", "C−", "D+", "D", "E")
    private val clears = listOf("NO PLAY", "FAILED", "ASSIST", "CLEAR", "—", "—", "LIFE 4", "Good FC", "Great FC", "PFC", "MFC")
    fun rank(value: Int) = ranks.getOrElse(value) { "未知 $value" }
    fun clear(value: Int) = clears.getOrElse(value) { "未知 $value" }
    fun flare(value: Int) = listOf("NORMAL", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "EX").getOrElse(value) { "未知 $value" }
}
