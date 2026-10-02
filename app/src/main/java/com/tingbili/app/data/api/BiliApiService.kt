package com.tingbili.app.data.api

import com.tingbili.app.data.api.dto.AppArchiveData
import com.tingbili.app.data.api.dto.AudioUrlData
import com.tingbili.app.data.api.dto.AuthorVideosData
import com.tingbili.app.data.api.dto.BiliResponse
import com.tingbili.app.data.api.dto.GaiaRegisterData
import com.tingbili.app.data.api.dto.GaiaRegisterReq
import com.tingbili.app.data.api.dto.PageItem
import com.tingbili.app.data.api.dto.PlayUrlData
import com.tingbili.app.data.api.dto.QrGenerateData
import com.tingbili.app.data.api.dto.QrPollData
import com.tingbili.app.data.api.dto.SearchResult
import com.tingbili.app.data.api.dto.VideoViewData
import com.tingbili.app.data.api.dto.WbiNav
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
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

    // 2026-09 实测：v2 端点已不收 POST（405 Method Not Allowed），改 GET 正常
    @GET
    suspend fun searchFallback(
        @Url url: String = "https://api.bilibili.com/x/web-interface/search/all/v2",
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

    @GET("x/player/pagelist")
    suspend fun pagelist(@Query("bvid") bvid: String): BiliResponse<List<PageItem>>

    @GET("x/web-interface/view")
    suspend fun view(@Query("bvid") bvid: String): BiliResponse<VideoViewData>

    @GET("x/space/wbi/arc/search")
    suspend fun authorVideos(
        @Header("Referer") referer: String,
        @Query("mid") mid: Long,
        @Query("ps") ps: Int,
        @Query("pn") pn: Int,
        @Query("dm_img_list") dmImgList: String,
        @Query("dm_img_str") dmImgStr: String,
        @Query("dm_cover_img_str") dmCoverImgStr: String,
        @Query("dm_img_inter") dmImgInter: String,
        @Query("w_rid") wRid: String,
        @Query("wts") wts: String,
        @Header("x-gaia-vtoken") gaiaToken: String? = null
    ): BiliResponse<AuthorVideosData>

    /** -352 风控时用 v_voucher 换 x-gaia-vtoken（challenge 为空即无验证码，直接给 token） */
    @POST("x/gaia-vgate/v1/register")
    suspend fun gaiaRegister(@Body body: GaiaRegisterReq): BiliResponse<GaiaRegisterData>

    /**
     * 扫码登录：生成二维码（passport 域，与 api 基址不同故用 @Url 全址）。
     * 路径必须是 `x/passport-login/...`，旧的 `x/passport/login/...` 已下线（返回 404 质询页，实测 2026-10-01）。
     */
    @GET
    suspend fun qrGenerate(
        @Url url: String = "https://passport.bilibili.com/x/passport-login/web/qrcode/generate"
    ): BiliResponse<QrGenerateData>

    /**
     * 扫码登录：轮询扫码状态。返回 Retrofit Response 是为了读 `set-cookie` 头 ——
     * 新版 passport-login 端点把登录 cookie 放在响应头下发，`data.url` 可能是空的，
     * 只解析 url 会"扫码成功却拿不到 cookie"（实测 2026-10-01 用户端：B站 App 已确认登录，App 里毫无反应）。
     */
    @GET
    suspend fun qrPoll(
        @Url url: String = "https://passport.bilibili.com/x/passport-login/web/qrcode/poll",
        @Query("qrcode_key") qrcodeKey: String
    ): retrofit2.Response<BiliResponse<QrPollData>>

    @GET("x/space/arc/search")
    suspend fun authorVideosLegacy(
        @Header("Referer") referer: String,
        @Query("mid") mid: Long,
        @Query("ps") ps: Int,
        @Query("pn") pn: Int,
        @Query("keyword") keyword: String = "",
        @Query("order") order: String = "pubdate",
        @Query("platform") platform: String = "web",
        @Query("web_location") webLocation: String = "1550101"
    ): BiliResponse<AuthorVideosData>

    @GET("x/space/arc/search")
    suspend fun authorVideosLegacyWbi(
        @Header("Referer") referer: String,
        @Query("mid") mid: Long,
        @Query("ps") ps: Int,
        @Query("pn") pn: Int,
        @Query("keyword") keyword: String = "",
        @Query("order") order: String = "pubdate",
        @Query("platform") platform: String = "web",
        @Query("web_location") webLocation: String = "1550101",
        @Query("w_rid") wRid: String,
        @Query("wts") wts: String
    ): BiliResponse<AuthorVideosData>

    /** 手机端独立风控栈的兜底（v0.26.0 曾被删，PC 上实测 -400 但那次 IP 已在升级风控中，证据不足所以保留） */
    @GET
    suspend fun authorAppArchive(
        @Url url: String = "https://app.bilibili.com/x/v2/space/archive",
        @Query("mid") mid: Long,
        @Query("order") order: String = "pubdate",
        @Query("pn") pn: Int,
        @Query("ps") ps: Int,
        @Query("tid") tid: Int = 0,
        @Query("keyword") keyword: String = ""
    ): BiliResponse<AppArchiveData>
}

@kotlinx.serialization.Serializable
data class AudioInfo(
    val title: String = "",
    val author: String = "",
    @kotlinx.serialization.SerialName("duration") val durationSec: Long = 0
)

fun buildHttpClient(
    cookieProvider: () -> String,
    loginCookieProvider: () -> String = { "" }
): OkHttpClient =
    OkHttpClient.Builder()
        .addInterceptor { chain ->
            val original = chain.request()
            // 实测（2026-09-28，对照 curl）：B站 space 系接口（/x/space/*）
            //   1) 校验 Referer 必须 https://space.bilibili.com/{mid}/，全局 www Referer 会吃 412；
            //   2) 随机生成的匿名指纹 cookie（buvid3/buvid4/b_nut）会被 WAF 412。
            // 浏览器里 space 请求带真实登录态是正常形态，所以 space 只带登录 cookie
            // （若有），不带匿名指纹；其余接口带完整 cookie。
            val isSpaceApi = original.url.host == "api.bilibili.com" &&
                original.url.encodedPath.contains("/x/space/")
            val cookie = if (isSpaceApi) loginCookieProvider() else cookieProvider()
            val req = original.newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
                // 请求自带 Referer（如 space 系接口要 https://space.bilibili.com/{mid}/）
                // 时以请求为准，别用 header() 一刀切覆盖掉
                .apply {
                    if (original.header("Referer") == null) header("Referer", "https://www.bilibili.com/")
                }
                .apply { if (cookie.isNotBlank()) header("Cookie", cookie) }
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