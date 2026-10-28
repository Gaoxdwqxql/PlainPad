package com.sunwenqing.plainpad

import android.content.Context
import android.net.Uri
import java.security.MessageDigest

sealed class Sha256Result {
    data class DiskFile(val hash: String) : Sha256Result()
    data class CurrentContent(val hash: String) : Sha256Result()
    object Unavailable : Sha256Result()
}

object FileHasher {

    fun sha256OfText(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(text.toByteArray(Charsets.UTF_8))
        return bytes.toHex()
    }

    fun sha256OfUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(8192)
                var n: Int
                while (stream.read(buffer).also { n = it } != -1) {
                    digest.update(buffer, 0, n)
                }
                digest.digest().toHex()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }
}