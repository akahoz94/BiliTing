package com.tingbili.app.data.api

import com.tingbili.app.data.api.dto.AudioUrlData
import com.tingbili.app.data.api.dto.AuthorVideosData
import com.tingbili.app.data.api.dto.BiliResponse
import com.tingbili.app.data.api.dto.PageItem
import com.tingbili.app.data.api.dto.PlayUrlData
import com.tingbili.app.data.api.dto.SearchResult
import com.tingbili.app.data.api.dto.WbiNav
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

private val json = Json { ignoreUnknownKeys = true }

interface BiliApiService {
    @GET("x/web-interface/nav")
    suspend fun nav(@Query("wbi") wbi: String = ""): BiliResponse<WbiNav>

    @GET("x/web-interface/wbi/search/type")
    suspend fun search(
        @Query("keyword") keyword: String,
        @Query("search_type") searchType: String,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
        @Query("w_rid") wRid: String,
        @Query("wts") wts: String
    ): BiliResponse<SearchResult>

    /**
     * 搜索兜底端点（POST，匿名可用）：
     *   URL: https://api.bilibili.com/x/web-interface/search/all/v2
     *   Body: form-encoded `keyword=&page=1` 等参数。
     *   优点：无需 wbi 签名、风控宽松；缺点：返回字段结构略不同，需要客户端解析 result.video 内嵌。
     *   当 wbi 端点风控被拦截（412/352）时调用此端点。
     */
    @POST
    suspend fun searchFallback(
        @Url url: String = "https://api.bilibili.com/x/web-interface/search/all/v2",
        @Body body: okhttp3.RequestBody
    ): okhttp3.ResponseBody

    /**
     * 第三个兜底（websearch，无 wbi 风控）：
     *   URL: https://api.bilibili.com/search?search_type=video&keyword=...&page=1
     *   这是 web 站搜索调用的老接口，匿名风控非常宽松；返回字段同样需要手动解析。
     */
    @GET
    suspend fun searchWeb(
        @Url url: String = "https://api.bilibili.com/search",
        @Query("search_type") searchType: String = "video",
        @Query("keyword") keyword: String,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 30
    ): okhttp3.ResponseBody

    @GET("x/player/wbi/playurl")
    suspend fun playUrl(
        @Query("bvid") bvid: String,
        @Query("cid") cid: Long,
        @Query("qn") qn: Int = 64,
        @Query("fnval") fnval: Int = 16,
        @Query("fourk") fourk: Int = 0,
        @Query("w_rid") wRid: String,
        @Query("wts") wts: String
    ): BiliResponse<PlayUrlData>

    @GET("audio/music-service-c/web/song/info")
    suspend fun audioInfo(@Query("sid") sid: Long): BiliResponse<AudioInfo>

    @GET("audio/music-service-c/web/url")
    suspend fun audioUrl(@Query("sid") sid: Long): BiliResponse<AudioUrlData>

    /** 分P cid 列表：比 view 风控宽松，无需 wbi 签名（view 匿名请求易被 412 拦截） */
    @GET("x/player/pagelist")
    suspend fun pagelist(@Query("bvid") bvid: String): BiliResponse<List<PageItem>>

    /** UP主全部投稿，按时间倒序分页 */
    @GET("x/space/wbi/arc/search")
    suspend fun authorVideos(
        @Query("mid") mid: Long,
        @Query("ps") ps: Int,
        @Query("pn") pn: Int,
        @Query("w_rid") wRid: String,
        @Query("wts") wts: String
    ): BiliResponse<AuthorVideosData>

    /**
     * 旧版 UP 主投稿接口（/x/space/arc/search）：
     *   优点：不需要 wbi 签名、风控宽松；缺点：官方文档不公开，仍可用一段时间。
     *   实际请求时 B 站要求固定参数：keyword="" order=pubdate platform=web web_location=1550101。
     */
    @GET("x/space/arc/search")
    suspend fun authorVideosLegacy(
        @Query("mid") mid: Long,
        @Query("ps") ps: Int,
        @Query("pn") pn: Int,
        @Query("keyword") keyword: String = "",
        @Query("order") order: String = "pubdate",
        @Query("platform") platform: String = "web",
        @Query("web_location") webLocation: String = "1550101"
    ): BiliResponse<AuthorVideosData>
}

@kotlinx.serialization.Serializable
data class AudioInfo(
    val title: String = "",
    val author: String = "",
    @kotlinx.serialization.SerialName("duration") val durationSec: Long = 0
)

fun buildHttpClient(cookieHeader: String): OkHttpClient =
    OkHttpClient.Builder()
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
                .header("Referer", "https://www.bilibili.com/")
                .apply { if (cookieHeader.isNotBlank()) header("Cookie", cookieHeader) }
                .build()
            chain.proceed(req)
        }
        .addInterceptor(
            HttpLoggingInterceptor { msg -> android.util.Log.d("BiliApi", msg) }
                .setLevel(HttpLoggingInterceptor.Level.BODY)
        )
        .build()

fun buildRetrofit(client: OkHttpClient): Retrofit =
    Retrofit.Builder()
        .baseUrl("https://api.bilibili.com/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
