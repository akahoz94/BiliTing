package com.tingbili.app.data.api

import com.tingbili.app.data.api.dto.AudioUrlData
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
import retrofit2.http.GET
import retrofit2.http.Query

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
