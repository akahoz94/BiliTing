package com.tingbili.app.data.api.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** /x/player/pagelist 分P 列表：提供各 P 的 cid（view 接口匿名调用易被 412，改用 pagelist） */
@Serializable
data class PageItem(
    val cid: Long = 0,
    val page: Int = 1,
    val part: String = "",
    val duration: Long = 0
)

/** /x/space/wbi/arc/search UP主全部投稿 */
@Serializable
data class AuthorVideosData(
    val list: AuthorVideoList = AuthorVideoList(),
    val page: AuthorVideoPage = AuthorVideoPage(),
    /** code=-352 风控时下发，拿它走 gaia vgate 换 token 后重试 */
    val v_voucher: String? = null
)

/** /x/gaia-vgate/v1/register 请求体 */
@Serializable
data class GaiaRegisterReq(val v_voucher: String)

/** /x/gaia-vgate/v1/register 响应：challenge 非空说明要过验证码，放弃 */
@Serializable
data class GaiaRegisterData(
    val token: String? = null,
    val challenge: List<JsonElement> = emptyList()
)

/** 扫码登录：生成二维码 */
@Serializable
data class QrGenerateData(val url: String = "", val qrcode_key: String = "")

/** 扫码登录：轮询。data.code：86101 待扫码 / 86090 已扫码待确认 / 86038 已过期 / 0 登录成功 */
@Serializable
data class QrPollData(
    val url: String = "",
    val refresh_token: String = "",
    val timestamp: Long = 0,
    val code: Int = 86101
)

@Serializable
data class AuthorVideoList(val vlist: List<AuthorVideo> = emptyList())

@Serializable
data class AuthorVideo(
    val aid: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val author: String = "",
    val pic: String = "",
    val duration: String = "",
    val length: String = ""
)

@Serializable
data class AuthorVideoPage(
    val count: Int = 0,
    val pn: Int = 1,
    val ps: Int = 30
)

/** app.bilibili.com/x/v2/space/archive 返回结构：data.vlist（与 web 端 data.list.vlist 不同） */
@Serializable
data class AppArchiveData(
    val vlist: List<AuthorVideo> = emptyList(),
    val page: AuthorVideoPage = AuthorVideoPage()
)
