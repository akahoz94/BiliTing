package com.tingbili.app.util

object FormatUtil {
    fun duration(durationStr: String): String = durationStr  // B站返回 "12:34"
    fun progress(ms: Long): String {
        val totalSec = ms / 1000
        return "%d:%02d".format(totalSec / 60, totalSec % 60)
    }
    fun playCount(raw: String): String = raw  // 展示原样（含"万"）
}
