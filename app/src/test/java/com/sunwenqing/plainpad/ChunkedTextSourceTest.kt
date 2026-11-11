package com.sunwenqing.plainpad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ChunkedTextSourceTest {

    @Test
    fun singleLongLineIsSplitIntoVisualLines() {
        val file = Files.createTempFile("plainpad-long-line", ".txt").toFile()
        try {
            file.writeBytes(ByteArray(5000) { 'A'.code.toByte() })
            val source = ChunkedTextSource.open(file, "UTF-8")!!
            try {
                assertEquals(5, source.lineCount)

                val lines = source.readLines(0, 4)
                assertEquals(5, lines.size)
                assertEquals(1024, lines[0].length)
                assertEquals(1024, lines[3].length)
                assertEquals(904, lines[4].length)
                assertTrue(lines.all { line -> line.all { it == 'A' } })

                assertEquals(1024, source.readLine(1).length)
                assertEquals(3072L, source.lineOffset(3))
            } finally {
                source.close()
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun utf8LongLineIsSplitOnCharBoundaries() {
        val file = Files.createTempFile("plainpad-utf8-line", ".txt").toFile()
        try {
            val text = "中".repeat(1000)
            file.writeBytes(text.toByteArray(Charsets.UTF_8))
            val source = ChunkedTextSource.open(file, "UTF-8")!!
            try {
                val lines = source.readLines(0, source.lineCount - 1)
                assertEquals(text, lines.joinToString(""))
                assertTrue(lines.none { it.contains('\uFFFD') })
            } finally {
                source.close()
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun crlfLinesAreSplitAndTrimmed() {
        val file = Files.createTempFile("plainpad-crlf", ".txt").toFile()
        try {
            file.writeBytes("hello\r\nworld\n".toByteArray(Charsets.UTF_8))
            val source = ChunkedTextSource.open(file, "UTF-8")!!
            try {
                val lines = source.readLines(0, 1)
                assertEquals(listOf("hello", "world"), lines)
                assertEquals("world", source.readLine(1))
            } finally {
                source.close()
            }
        } finally {
            file.delete()
        }
    }
}
