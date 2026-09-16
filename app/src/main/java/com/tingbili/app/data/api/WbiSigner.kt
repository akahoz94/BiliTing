package com.tingbili.app.data.api

import java.security.MessageDigest
import java.util.concurrent.ThreadLocalRandom

object WbiSigner {
    private const val MIXIN_KEY_ENC_TAB = 46

    fun mixinKey(imgKey: String, subKey: String): String {
        val raw = imgKey + subKey
        val chars = CharArray(32)
        for (i in 0 until 32) chars[i] = raw[WBI_TABLE[i]]
        return String(chars)
    }

    fun sign(
        params: Map<String, String>,
        imgKey: String,
        subKey: String
    ): Map<String, String> {
        val mixin = mixinKey(imgKey, subKey)
        val wts = System.currentTimeMillis() / 1000
        val result = params.toMutableMap()
        result["wts"] = wts.toString()
        val sorted = result.toSortedMap()
        val query = sorted.entries.joinToString("&") { "${it.key}=${encode(it.value)}" }
        val wRid = md5(query + mixin)
        result["w_rid"] = wRid
        return result
    }

    fun randomBuvid3(): String {
        val sb = StringBuilder()
        repeat(28) { sb.append("0123456789ABCDEF".random()) }
        sb.append("infoc")
        return sb.toString()
    }

    private fun encode(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8")
            .replace("+", "%20").replace("*", "%2A")

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private val WBI_TABLE = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
        27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
        37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
        22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52
    )
}
