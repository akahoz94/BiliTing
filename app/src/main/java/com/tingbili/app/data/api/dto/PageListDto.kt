package com.tingbili.app.data.api.dto

import kotlinx.serialization.Serializable

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
    val page: AuthorVideoPage = AuthorVideoPage()
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