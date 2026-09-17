package com.tingbili.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Log
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 封面取色：从本地封面缓存文件里抽取 dominant 主色 + 1~2 个 accent。
 *
 * 用 AndroidX Palette 库做主色提取。落盘缓存 (md5(coverUrl).palette) 避免重复解码。
 *
 * 调用时机：
 *   - PlayerLauncher.play() 后异步触发
 *   - 播放页背景（CoverColorBackground）订阅 StateFlow
 *
 * 颜色含义：
 *   - dominant：封面里出现频率最高的"有彩色"
 *   - muted / vibrant：饱和度适中的两个色
 *   - onDominant：根据 dominant 亮度自动决定的文字色（白/黑）
 */
object PaletteExtractor {
    private const val TAG = "PaletteExtractor"
    private const val CACHE_VERSION = "v1"
    private const val PALETTE_EXT = ".palette"

    data class CoverPalette(
        val dominant: Int,
        val onDominant: Int,
        val muted: Int,
        val vibrant: Int,
        val source: String // local path or remote url
    )

    /** 从本地 File 解码 + 取色；缓存命中直接读缓存 */
    suspend fun extract(context: Context, localFile: File, sourceUrl: String): CoverPalette? =
        withContext(Dispatchers.IO) {
            if (!localFile.exists() || localFile.length() == 0L) return@withContext null
            try {
                val cacheKey = md5(sourceUrl.ifBlank { localFile.absolutePath })
                val cacheFile = File(localFile.parentFile, "$cacheKey-$CACHE_VERSION$PALETTE_EXT")
                if (cacheFile.exists() && cacheFile.length() > 0L) {
                    val cached = readPaletteCache(cacheFile)
                    if (cached != null) return@withContext cached
                }
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(localFile.absolutePath, opts)
                val longEdge = maxOf(opts.outWidth, opts.outHeight).coerceAtLeast(1)
                var sample = 1
                while (longEdge / sample > 256) sample *= 2
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = BitmapFactory.decodeFile(localFile.absolutePath, decodeOpts) ?: return@withContext null
                val palette = Palette.from(bmp).maximumColorCount(24).generate()
                val dominant = palette.getDominantColor(Color.parseColor("#6750A4"))
                val muted = palette.getMutedColor(dominant)
                val vibrant = palette.getVibrantColor(dominant)
                val onDominant = pickOnColor(dominant)
                val out = CoverPalette(dominant, onDominant, muted, vibrant, sourceUrl.ifBlank { localFile.absolutePath })
                bmp.recycle()
                writePaletteCache(cacheFile, out)
                out
            } catch (t: Throwable) {
                Log.w(TAG, "取色失败: ${t.message}")
                null
            }
        }

    /**
     * 根据背景亮度挑反色文字：背景过暗用白字、极端亮用黑字。
     * 阈值 0.55 是经验值（luminance > 0.55 时文字应改黑）。
     */
    private fun pickOnColor(bg: Int): Int {
        val r = Color.red(bg) / 255.0
        val g = Color.green(bg) / 255.0
        val b = Color.blue(bg) / 255.0
        val luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
        return if (luminance > 0.55) Color.parseColor("#1A1A1A") else Color.WHITE
    }

    private fun writePaletteCache(file: File, p: CoverPalette) {
        try {
            val payload = "${p.dominant}|${p.onDominant}|${p.muted}|${p.vibrant}|${p.source}"
            file.writeText(payload)
        } catch (_: Throwable) { /* cache 写不动就下次重算 */ }
    }

    private fun readPaletteCache(file: File): CoverPalette? = try {
        val parts = file.readText().split("|")
        if (parts.size < 4) null else CoverPalette(
            dominant = parts[0].toInt(),
            onDominant = parts[1].toInt(),
            muted = parts[2].toInt(),
            vibrant = parts[3].toInt(),
            source = parts.getOrNull(4).orEmpty()
        )
    } catch (_: Throwable) { null }

    private fun md5(s: String): String {
        val bytes = java.security.MessageDigest.getInstance("MD5").digest(s.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}