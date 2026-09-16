package com.tingbili.app.data.api

import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * 真实接口冒烟测试：走 App 实际代码路径（OkHttp 客户端 + WBI 签名）直连 B站。
 * 依赖外网与 B站风控，非 CI 常规用例，手动执行：gradlew :app:testDebugUnitTest --tests "*ApiSmokeTest*"
 */
class ApiSmokeTest {
    private val buvid3 = WbiSigner.randomBuvid3()
    private val cookie =
        "buvid3=$buvid3; buvid4=${WbiSigner.randomBuvid4()}; b_nut=${System.currentTimeMillis() / 1000}"
    private val service: BiliApiService =
        buildRetrofit(buildHttpClient(cookie)).create(BiliApiService::class.java)
    private val keys = WbiKeyStore(service)

    @Test
    fun nav_获取wbi签名key() = runBlocking {
        val (img, sub) = keys.keys()
        check(img.isNotBlank() && sub.isNotBlank()) { "nav 未返回 wbi img/sub key（可能被风控拦截）" }
        println("PASS nav: img=$img sub=$sub")
    }

    @Test
    fun 搜索视频并解析播放地址() = runBlocking {
        val items = SearchApi(service, keys).search("凡人修仙传 有声书", "video", 1)
        check(items.isNotEmpty()) { "视频搜索无结果" }
        val item = items.first { it.bvid.isNotBlank() }
        println("PASS search: ${item.title} bvid=${item.bvid}")

        val resp = service.pagelist(item.bvid)
        val pages = resp.data.orEmpty().filter { it.cid > 0 }
        check(pages.isNotEmpty()) { "pagelist 接口失败 code=${resp.code} msg=${resp.message}" }
        val cid = pages.first().cid
        println("PASS pagelist: 分P数=${pages.size} cid=$cid")

        val url = PlayUrlApi(service, keys).audioUrl(item.bvid, cid)
        check(!url.isNullOrBlank()) { "playurl 未返回音频流" }
        check(url.startsWith("http")) { "playurl 返回异常: $url" }
        println("PASS playurl: $url")
    }

    @Test
    fun 音频区搜索并解析播放地址() = runBlocking {
        val items = SearchApi(service, keys).search("凡人修仙传 有声书", "audio", 1)
        if (items.isEmpty()) {
            println("INFO audio 搜索无结果（不影响视频主链路）")
            return@runBlocking
        }
        val item = items.first { it.aid > 0 }
        val info = AudioApi(service).info(item.aid)
        val url = AudioApi(service).url(item.aid)
        println("PASS audio: ${info?.title} url=${url}")
        check(!url.isNullOrBlank()) { "audio url 为空" }
        check(url.startsWith("http")) { "audio url 异常: $url" }
    }
}
