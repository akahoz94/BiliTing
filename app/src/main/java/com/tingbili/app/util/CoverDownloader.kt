package com.tingbili.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * 锁屏封面下载器：给定 URL → 落盘到 coverCache/ 目录 → 返回 File
 *
 * 设计要点：
 *   - 用 OkHttp + Referer/User-Agent 下载（B 站 CDN 反盗链）
 *   - 缩放到 512×512 主流锁屏控件尺寸，避免内存爆炸
 *   - 同 URL 复用：URL 做 MD5 命名，重复请求直接命中本地
 *   - 异步执行（IO 调度），调用方在协程中等待
 *
 * 调用时机：PlayerLauncher.playSearchItem / playRecord → 在 play() 之前预下载
 */
object CoverDownloader {
    private const val TAG = "CoverDownloader"

    /**
     * 同步获取封面文件：本地有直接返回；否则下载后落盘返回。
     * @return 缓存文件，失败时返回 null
     */
    suspend fun ensureLocalFile(context: Context, url: String): File? {
        if (url.isBlank()) return null
        val normalized = CoverUtil.normalize(url)
        if (normalized.isBlank()) return null
        val dir = CoverCacheStore.dir(context)
        val name = md5(normalized) + ".jpg"
        val out = File(dir, name)
        if (out.exists() && out.length() > 0) return out

        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder().build()
                val req = Request.Builder()
                    .url(normalized)
                    .header("Referer", "https://www.bilibili.com/")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "下载失败: code=${resp.code} url=$normalized")
                        return@withContext null
                    }
                    val bytes = resp.body?.bytes() ?: return@withContext null
                    FileOutputStream(out).use { it.write(bytes) }
                    out
                }
            } catch (t: Throwable) {
                Log.w(TAG, "下载异常: ${t.message} url=$normalized")
                out.delete()
                null
            }
        }
    }

    /**
     * 把锁屏用的 Bitmap 解码出来（最大边缩放到 512px，节省 MediaStyle 通知内存）
     * 本地文件不存在时返回 null
     */
    fun decodeForLockScreen(file: File): Bitmap? {
        if (!file.exists() || file.length() == 0L) return null
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            val maxEdge = 512
            var sample = 1
            val longEdge = maxOf(opts.outWidth, opts.outHeight)
            while (longEdge / sample > maxEdge) sample *= 2
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
        } catch (t: Throwable) {
            Log.w(TAG, "decode 失败: ${t.message}")
            null
        }
    }

    private fun md5(s: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(s.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

/** 封面缓存目录（与 WebDAV 备份目录区分） */
class CoverCacheStore {
    companion object {
        fun dir(context: Context): File {
            val d = File(context.cacheDir, "coverCache")
            if (!d.exists()) d.mkdirs()
            return d
        }
        fun clear(context: Context) {
            dir(context).listFiles()?.forEach { it.delete() }
        }
    }
}