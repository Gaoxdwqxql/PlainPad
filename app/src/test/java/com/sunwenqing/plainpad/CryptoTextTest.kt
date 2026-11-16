package com.sunwenqing.plainpad

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CryptoTextTest {

    private val password = "correct horse".toCharArray()

    @Test
    fun roundTripUtf8() {
        val original = "PlainPad 加密测试\n第二行"
        val encrypted = CryptoText.encrypt(original, "UTF-8", false, password)
        assertTrue(CryptoText.isEncrypted(encrypted))
        assertFalse(encrypted.contentEquals(original.toByteArray(Charsets.UTF_8)))

        val decoded = CryptoText.decrypt(encrypted, password)
        assertEquals(original, decoded.text)
        assertEquals("UTF-8", decoded.encoding)
        assertFalse(decoded.hasBom)
    }

    @Test
    fun roundTripGbk() {
        val original = "中文编码测试"
        val encrypted = CryptoText.encrypt(original, "GBK", false, password)
        val decoded = CryptoText.decrypt(encrypted, password)
        assertEquals(original, decoded.text)
        assertEquals("GBK", decoded.encoding)
    }

    @Test
    fun utf8BomIsPreserved() {
        val encrypted = CryptoText.encrypt("BOM", "UTF-8", true, password)
        val decoded = CryptoText.decrypt(encrypted, password)
        assertEquals("BOM", decoded.text)
        assertTrue(decoded.hasBom)
    }

    @Test
    fun wrongPasswordFails() {
        val encrypted = CryptoText.encrypt("secret", "UTF-8", false, password)
        try {
            CryptoText.decrypt(encrypted, "wrong".toCharArray())
            fail("expected WrongPasswordException")
        } catch (_: CryptoText.WrongPasswordException) {
            // expected
        }
    }

    @Test
    fun tamperedFileFailsAuthentication() {
        val encrypted = CryptoText.encrypt("secret", "UTF-8", false, password)
        encrypted[encrypted.size - 1] = (encrypted.last().toInt() xor 1).toByte()
        try {
            CryptoText.decrypt(encrypted, password)
            fail("expected WrongPasswordException")
        } catch (_: CryptoText.WrongPasswordException) {
            // expected
        }
    }

    @Test
    fun emptyTextRoundTrip() {
        val encrypted = CryptoText.encrypt("", "UTF-8", false, password)
        val decoded = CryptoText.decrypt(encrypted, password)
        assertEquals("", decoded.text)
    }

    @Test
    fun headerIsStablePrefix() {
        val encrypted = CryptoText.encrypt("x", "UTF-8", false, password)
        assertArrayEquals("PPADENC1".toByteArray(Charsets.US_ASCII), encrypted.copyOfRange(0, 8))
    }
}
