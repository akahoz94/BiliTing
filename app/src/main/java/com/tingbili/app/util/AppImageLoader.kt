package com.tingbili.app.util

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import okhttp3.OkHttpClient

/**
 * Coil 全局 ImageLoader —— 给所有 AsyncImage 默认带 UA + Referer，
 * 避免 B 站 CDN/i0~i9.hdslb.com 对裸请求 403。
 *
 * Application.onCreate 里通过 Coil.setImageLoader(...) 注入一次即可。
 */
object AppImageLoader {
    @Volatile private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        instance?.let { return it }
        synchronized(this) {
            instance?.let { return it }
            val loader = ImageLoader.Builder(context.applicationContext)
                .okHttpClient(
                    OkHttpClient.Builder()
                        .addInterceptor { chain ->
                            val req = chain.request().newBuilder()
                                .header(
                                    "User-Agent",
                                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
                                )
                                .header("Referer", "https://www.bilibili.com/")
                                .build()
                            chain.proceed(req)
                        }
                        .build()
                )
                .memoryCache {
                    MemoryCache.Builder(context.applicationContext)
                        .maxSizePercent(0.20)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(context.applicationContext.cacheDir.resolve("img_cache"))
                        .maxSizePercent(0.02)
                        .build()
                }
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .respectCacheHeaders(false)
                .build()
            instance = loader
            return loader
        }
    }
}