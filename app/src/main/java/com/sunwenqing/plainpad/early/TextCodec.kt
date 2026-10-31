package com.sunwenqing.plainpad.early

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

object TextCodec {

    private val GBK: Charset = Charset.forName("GBK")

    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)

        if (bytes.size >= 2) {
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte())
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }

        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: Exception) {
            String(bytes, GBK)
        }
    }

    fun encode(text: String): ByteArray = text.toByteArray(Charsets.UTF_8)
}