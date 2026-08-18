package com.ggpark.bydstats.crypto

expect object CryptoUtils {
    fun md5Hex(value: String): String
    fun sha1Mixed(value: String): String
    fun aesEncryptHex(plaintext: String, keyHex: String): String
    fun aesDecryptUTF8(cipherHex: String, keyHex: String): String

    fun computeCheckcode(jsonStr: String): String
    fun buildSignString(fields: Map<String, String>, password: String): String
    fun pwdLoginKey(password: String): String
}
