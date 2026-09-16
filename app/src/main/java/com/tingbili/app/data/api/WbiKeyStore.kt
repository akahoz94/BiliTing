package com.tingbili.app.data.api

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * wbi 签名所需的 img_key / sub_key 内存缓存。
 * 首次调用 keys() 时懒加载（请求 nav 接口），之后复用；Mutex 防止并发重复请求。
 */
class WbiKeyStore(private val service: BiliApiService) {
    private val mutex = Mutex()
    private var imgKey = ""
    private var subKey = ""

    /** 返回 (imgKey, subKey)，缓存 + Mutex 保护 */
    suspend fun keys(): Pair<String, String> {
        if (imgKey.isNotBlank()) return imgKey to subKey
        return mutex.withLock {
            if (imgKey.isBlank()) {
                val img = service.nav().data?.wbiImg ?: return@withLock "" to ""
                imgKey = img.imgUrl.substringAfterLast("/").substringBefore(".")
                subKey = img.subUrl.substringAfterLast("/").substringBefore(".")
            }
            imgKey to subKey
        }
    }
}
