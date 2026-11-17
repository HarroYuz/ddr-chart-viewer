package com.harroyuz.ddrchartviewer.domain

/** Stops occur after a note at that beat; delays occur before it. Seconds are chart time. */
class Timeline(
    val bpms: List<TimingEvent>, stops: List<TimingEvent>, delays: List<TimingEvent>,
    offset: Double, endBeat: Double,
) {
    private data class Segment(val beat: Double, val endBeat: Double, val start: Double, val end: Double, val bpm: Double)
    private val segments: List<Segment>
    private val arrivals = mutableMapOf<Double, Double>()
    val duration: Double
    val start: Double = minOf(0.0, -offset)

    init {
        require(bpms.isNotEmpty() && bpms.first().beat == 0.0 && bpms.all { it.value > 0 && it.value.isFinite() }) { "谱面 BPM 无效" }
        val changes = bpms.associate { it.beat to it.value }
        val stopMap = stops.groupBy { it.beat }.mapValues { (_, v) -> v.sumOf { it.value } }
        val delayMap = delays.groupBy { it.beat }.mapValues { (_, v) -> v.sumOf { it.value } }
        val boundaries = (changes.keys + stopMap.keys + delayMap.keys + setOf(0.0, endBeat)).filter { it in 0.0..endBeat }.sorted()
        val built = mutableListOf<Segment>(); var time = -offset; var beat = 0.0; var bpm = bpms.first().value
        for (next in boundaries) {
            if (next > beat) {
                val end = time + (next - beat) * 60 / bpm
                built += Segment(beat, next, time, end, bpm); time = end
            }
            bpm = changes[next] ?: bpm
            val delay = delayMap[next] ?: 0.0
            if (delay > 0) { built += Segment(next, next, time, time + delay, bpm); time += delay }
            arrivals[next] = time
            val stop = stopMap[next] ?: 0.0
            if (stop > 0) { built += Segment(next, next, time, time + stop, bpm); time += stop }
            beat = next
        }
        segments = built
        duration = maxOf(time + 2, 1.0)
    }

    fun timeAt(beat: Double): Double {
        arrivals[beat]?.let { return it }
        val segment = segments.firstOrNull { beat >= it.beat && beat < it.endBeat }
            ?: return if (beat <= 0) arrivals.getValue(0.0) + beat * 60 / bpms.first().value else segments.last().end
        return segment.start + (beat - segment.beat) * 60 / segment.bpm
    }
    fun beatAt(time: Double): Double {
        val segment = segmentAt(time) ?: return if (time < segments.first().start)
            (time - segments.first().start) * bpms.first().value / 60 else segments.last().endBeat
        return if (segment.beat == segment.endBeat) segment.beat else segment.beat + (time - segment.start) * segment.bpm / 60
    }
    fun bpmAt(time: Double) = segmentAt(time)?.bpm ?: if (time < 0) bpms.first().value else bpms.last().value
    private fun segmentAt(time: Double): Segment? {
        var lo = 0; var hi = segments.lastIndex
        while (lo <= hi) { val mid = (lo + hi) ushr 1; val s = segments[mid]
            if (time < s.start) hi = mid - 1 else if (time >= s.end) lo = mid + 1 else return s
        }
        return null
    }
}
