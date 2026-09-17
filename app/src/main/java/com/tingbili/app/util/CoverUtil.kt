package com.tingbili.app.util

/** 封面 URL 规范化：B站返回形如 "//i0.hdslb.com/bfs/.."（相对协议），Coil 无法加载，需补 https: */
object CoverUtil {
    fun normalize(url: String?): String {
        if (url.isNullOrBlank()) return ""
        var u = url.trim()
        if (u.startsWith("//")) u = "https:$u"
        else if (u.startsWith("http://")) u = "https:${u.substring(5)}"
        return u
    }
}