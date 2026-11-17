package com.harroyuz.ddrchartviewer.data.remote.bjm

import com.harroyuz.ddrchartviewer.domain.BjmMusic
import com.harroyuz.ddrchartviewer.domain.BjmScore
import org.jsoup.parser.Parser

/** Bounds-checked protobuf reader. Unknown fields are skipped for forward compatibility. */
internal class ProtoReader(private val bytes: ByteArray) {
    private var offset = 0
    val ended get() = offset == bytes.size
    fun varint(): Long {
        var result = 0L
        for (shift in 0..63 step 7) {
            require(offset < bytes.size) { "protobuf 数据截断" }
            val value = bytes[offset++].toInt() and 255
            require(shift != 63 || value <= 1) { "protobuf varint 溢出" }
            result = result or ((value and 127).toLong() shl shift)
            if (value and 128 == 0) return result
        }
        error("protobuf varint 过长")
    }
    private fun advance(size: Long): Int {
        require(size >= 0 && size <= bytes.size - offset) { "protobuf 字段越界" }
        val start = offset
        offset += size.toInt()
        return start
    }
    fun bytes(): ByteArray { val size = varint(); val start = advance(size); return bytes.copyOfRange(start, offset) }
    fun string() = Parser.unescapeEntities(bytes().toString(Charsets.UTF_8), false)
    fun skip(wire: Int) { when (wire) { 0 -> varint(); 1 -> advance(8); 2 -> advance(varint()); 5 -> advance(4); else -> error("不支持 protobuf wire=$wire") } }
    fun fields(block: (Int, Int) -> Boolean) {
        while (!ended) {
            val tag = varint(); val field = (tag ushr 3).toInt(); val wire = (tag and 7).toInt()
            require(field > 0) { "protobuf 字段号为零" }
            if (!block(field, wire)) skip(wire)
        }
    }
}

internal object DdrScoreProto {
    data class Decoded(val scores: List<BjmScore>, val status: Int)
    fun decodeGrpcWeb(body: ByteArray): Decoded {
        var offset = 0
        val messages = mutableListOf<ByteArray>()
        while (offset < body.size) {
            require(body.size - offset >= 5) { "gRPC 帧头截断" }
            val flag = body[offset].toInt() and 255
            var length = 0L
            for (i in 1..4) length = (length shl 8) or (body[offset + i].toLong() and 255)
            offset += 5
            require(length <= body.size - offset) { "gRPC 帧体截断" }
            val payload = body.copyOfRange(offset, offset + length.toInt())
            offset += length.toInt()
            when (flag) {
                0 -> messages += payload
                128 -> {
                    val trailers = payload.toString(Charsets.UTF_8).lineSequence().mapNotNull {
                        val parts = it.split(':', limit = 2)
                        if (parts.size == 2) parts[0].trim().lowercase() to parts[1].trim() else null
                    }.toMap()
                    val status = trailers["grpc-status"]?.toIntOrNull() ?: error("gRPC trailer 缺少状态")
                    require(status == 0) { "BJM gRPC 请求失败（$status）" }
                }
                else -> error("不支持的 gRPC 压缩或标记：$flag")
            }
        }
        require(messages.size == 1) { "BJM 应返回一条成绩消息" }
        val reader = ProtoReader(messages.single())
        val scores = mutableListOf<BjmScore>(); var status = 0
        reader.fields { field, wire ->
            when {
                field == 1 && wire == 2 -> {
                    val values = LongArray(8); val score = ProtoReader(reader.bytes())
                    score.fields { f, w -> if (f in 1..7 && w == 0) { values[f] = score.varint(); true } else false }
                    require(values[1] > 0 && values[2] in 0..8 && values[5] in 0..1_000_000) { "BJM 成绩字段不合法" }
                    scores += BjmScore(values[1].toInt(), values[2].toInt(), values[3].toInt(), values[4].toInt(), values[5].toInt(), values[6], values[7].toInt())
                    true
                }
                field == 2 && wire == 0 -> { status = reader.varint().toInt(); true }
                else -> false
            }
        }
        return Decoded(scores, status)
    }
}

internal object DdrMusicProto {
    fun decode(bytes: ByteArray): List<BjmMusic> {
        val reader = ProtoReader(bytes); val result = mutableListOf<BjmMusic>()
        reader.fields { field, wire ->
            if (field != 1 || wire != 2) false else {
                val record = ProtoReader(reader.bytes()); var id = 0; var title = ""; var artist = ""; var series = 0; var levels = ""
                record.fields { f, w ->
                    when {
                        f == 1 && w == 0 -> { id = record.varint().toInt(); true }
                        f == 3 && w == 2 -> { title = record.string(); true }
                        f == 5 && w == 2 -> { artist = record.string(); true }
                        f == 6 && w == 0 -> { series = record.varint().toInt(); true }
                        f == 7 && w == 2 -> { levels = record.string(); true }
                        else -> false
                    }
                }
                require(id > 0 && title.isNotBlank()) { "BJM 曲库记录无效" }
                result += BjmMusic(id, title, artist, series, levels.trim().split(Regex("\\s+")).map { it.toIntOrNull() ?: 0 })
                true
            }
        }
        return result
    }
}
