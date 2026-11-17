package com.harroyuz.ddrchartviewer

import com.harroyuz.ddrchartviewer.data.remote.bjm.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class ProtoTest {
    private fun varint(n: Long): ByteArray { var v = n; val out = ByteArrayOutputStream(); do { var b = (v and 127).toInt(); v = v ushr 7; if (v != 0L) b = b or 128; out.write(b) } while (v != 0L); return out.toByteArray() }
    private fun field(f: Int, n: Long) = varint((f * 8).toLong()) + varint(n)
    private fun message(f: Int, bytes: ByteArray) = varint((f * 8 + 2).toLong()) + varint(bytes.size.toLong()) + bytes
    private fun frame(bytes: ByteArray, flag: Int = 0) = byteArrayOf(flag.toByte(), (bytes.size ushr 24).toByte(), (bytes.size ushr 16).toByte(), (bytes.size ushr 8).toByte(), bytes.size.toByte()) + bytes
    private fun response() = message(1, field(1, 1234) + field(2, 7) + field(3, 1) + field(4, 9) + field(5, 999123) + field(6, 1_700_000_000) + field(7, 10))
    @Test fun scoreWireFieldsAndTrailerAreDecoded() {
        val decoded = DdrScoreProto.decodeGrpcWeb(frame(response()) + frame("grpc-status: 0\r\n".toByteArray(), 128))
        val s = decoded.scores.single()
        assertEquals(1234, s.musicId); assertEquals(7, s.slot); assertEquals(999123, s.score); assertEquals(10, s.flare); assertEquals(1_700_000_000, s.time)
    }
    @Test(expected = IllegalArgumentException::class) fun trailerErrorIsNotAnEmptySuccess() { DdrScoreProto.decodeGrpcWeb(frame(byteArrayOf()) + frame("grpc-status: 16\r\n".toByteArray(), 128)) }
    @Test(expected = IllegalArgumentException::class) fun truncatedFrameIsRejected() { DdrScoreProto.decodeGrpcWeb(frame(response()).dropLast(1).toByteArray()) }
    @Test(expected = IllegalArgumentException::class) fun oversizedUnknownFieldCannotOverflowReader() { DdrScoreProto.decodeGrpcWeb(frame(response() + varint(802) + varint(Long.MAX_VALUE))) }
    @Test fun unknownFieldsAreSkippedAndBusinessStatusPreserved() {
        val decoded = DdrScoreProto.decodeGrpcWeb(frame(response() + message(30, byteArrayOf(1,2,3)) + field(2, 4)))
        assertEquals(1, decoded.scores.size); assertEquals(4, decoded.status)
    }
    @Test fun musicFieldsAreDifferentFromIidx() {
        val bytes = message(1, field(1, 42) + message(3, "A &amp; B".toByteArray()) + message(5, "Artist".toByteArray()) + field(6, 19) + message(7, "1 2 3 4 5 0 6 7 8 9".toByteArray()))
        val song = DdrMusicProto.decode(bytes).single()
        assertEquals("A & B", song.title); assertEquals(19, song.series); assertEquals(6, song.level(5))
    }
}
