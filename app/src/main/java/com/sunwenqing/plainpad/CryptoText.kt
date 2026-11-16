package com.sunwenqing.plainpad

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 纯文本文件的密钥加密格式。
 *
 * 文件布局（全部为大端）：
 * ```
 * magic      8 bytes  "PPADENC1"
 * version    1 byte   1
 * iterations 4 bytes  PBKDF2 迭代次数
 * salt       16 bytes
 * iv         12 bytes
 * ciphertext ...      AES-256-GCM，tag 128 bit
 * ```
 * 加密前的正文带有编码名，解密后仍按原编码还原。
 *
 * 安全说明：
 * - 使用 PBKDF2WithHmacSHA1（API 24 可用），210000 次迭代，16 字节随机 salt
 * - AES-256-GCM 提供机密性和完整性校验；篡改文件或密码错误都会认证失败
 * - 密码只保存在当前进程内存中，不写入 SharedPreferences、草稿或标签页
 */
object CryptoText {

    private val MAGIC = "PPADENC1".toByteArray(Charsets.US_ASCII)
    private const val VERSION = 1
    private const val ITERATIONS = 210_000
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val TAG_BITS = 128
    private const val HEADER_LEN = 8 + 1 + 4 + SALT_LEN + IV_LEN

    data class Payload(val text: String, val encoding: String, val hasBom: Boolean)

    class WrongPasswordException : Exception()

    fun isEncrypted(bytes: ByteArray): Boolean {
        if (bytes.size < MAGIC.size) return false
        return MAGIC.indices.all { bytes[it] == MAGIC[it] }
    }

    fun encrypt(
        text: String,
        encoding: String,
        withBom: Boolean,
        password: CharArray
    ): ByteArray {
        require(password.isNotEmpty()) { "empty password" }
        val name = encoding.toByteArray(Charsets.UTF_8)
        require(name.size in 1..255) { "unsupported encoding name" }
        val textBytes = TextCodec.encode(text, encoding, withBom)

        val plain = ByteBuffer.allocate(1 + name.size + textBytes.size).apply {
            put(name.size.toByte())
            put(name)
            put(textBytes)
        }.array()

        val random = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }
        val header = ByteBuffer.allocate(8 + 1 + 4 + SALT_LEN).apply {
            put(MAGIC)
            put(VERSION.toByte())
            putInt(ITERATIONS)
            put(salt)
        }.array()

        val key = deriveKey(password, salt, ITERATIONS)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(header)
        val ciphertext = cipher.doFinal(plain)

        return ByteBuffer.allocate(header.size + IV_LEN + ciphertext.size).apply {
            put(header)
            put(iv)
            put(ciphertext)
        }.array()
    }

    fun decrypt(bytes: ByteArray, password: CharArray): Payload {
        if (!isEncrypted(bytes) || bytes.size < HEADER_LEN) {
            throw WrongPasswordException()
        }

        val buf = ByteBuffer.wrap(bytes)
        val magic = ByteArray(MAGIC.size).also { buf.get(it) }
        if (!magic.contentEquals(MAGIC)) throw WrongPasswordException()

        val version = buf.get().toInt() and 0xFF
        if (version != VERSION) throw WrongPasswordException()
        val iterations = buf.int
        if (iterations !in 10_000..1_000_000) throw WrongPasswordException()

        val salt = ByteArray(SALT_LEN).also { buf.get(it) }
        val header = bytes.copyOfRange(0, buf.position())
        val iv = ByteArray(IV_LEN).also { buf.get(it) }
        val ciphertext = ByteArray(buf.remaining()).also { buf.get(it) }

        val key = deriveKey(password, salt, iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(header)

        val plain = try {
            cipher.doFinal(ciphertext)
        } catch (_: AEADBadTagException) {
            throw WrongPasswordException()
        } catch (_: Exception) {
            throw WrongPasswordException()
        }
        if (plain.isEmpty()) throw WrongPasswordException()

        val nameLen = plain[0].toInt() and 0xFF
        if (nameLen <= 0 || 1 + nameLen > plain.size) throw WrongPasswordException()
        val encoding = String(plain, 1, nameLen, Charsets.UTF_8)
        val textBytes = plain.copyOfRange(1 + nameLen, plain.size)
        val decoded = TextCodec.decode(textBytes, encoding)
        return Payload(decoded.text, decoded.encoding, decoded.hasBom)
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
            val encoded = factory.generateSecret(spec).encoded
            return SecretKeySpec(encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
