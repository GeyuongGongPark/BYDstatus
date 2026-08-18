package com.ggpark.bydstats.crypto

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

actual object CryptoUtils {

    actual fun md5Hex(value: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02X".format(it) }
    }

    actual fun sha1Mixed(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(value.toByteArray(Charsets.UTF_8))
        // 짝수 인덱스 대문자, 홀수 인덱스 소문자
        val mixed = StringBuilder()
        bytes.forEachIndexed { i, b ->
            val hex = "%02x".format(b)
            mixed.append(if (i % 2 == 0) hex.uppercase() else hex)
        }
        // 짝수 위치의 '0' 제거
        return buildString {
            mixed.forEachIndexed { j, ch ->
                if (!(ch == '0' && j % 2 == 0)) append(ch)
            }
        }
    }

    actual fun aesEncryptHex(plaintext: String, keyHex: String): String {
        val keyBytes = hexToBytes(keyHex)
        val iv = ByteArray(16)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02X".format(it) }
    }

    actual fun aesDecryptUTF8(cipherHex: String, keyHex: String): String {
        val keyBytes = hexToBytes(keyHex)
        val cipherBytes = hexToBytes(cipherHex)
        val iv = ByteArray(16)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(cipherBytes).toString(Charsets.UTF_8)
    }

    actual fun computeCheckcode(jsonStr: String): String {
        val md5 = md5Hex(jsonStr).lowercase()
        return md5.substring(24, 32) + md5.substring(8, 16) + md5.substring(16, 24) + md5.substring(0, 8)
    }

    actual fun buildSignString(fields: Map<String, String>, password: String): String {
        val pairs = fields.entries.sortedBy { it.key }.joinToString("&") { "${it.key}=${it.value}" }
        return "$pairs&password=$password"
    }

    actual fun pwdLoginKey(password: String): String = md5Hex(md5Hex(password))

    private fun hexToBytes(hex: String): ByteArray {
        val s = hex.lowercase()
        return ByteArray(s.length / 2) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }
}
