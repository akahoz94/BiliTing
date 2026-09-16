package com.tingbili.app.data.api.dto

import kotlinx.serialization.Serializable

/** /x/web-interface/view 视频详情：用于取分P cid 列表 */
@Serializable
data class ViewData(
    val title: String = "",
    val owner: Owner = Owner(),
    val pages: List<Page> = emptyList()
)

@Serializable
data class Owner(val name: String = "")

@Serializable
data class Page(val cid: Long = 0, val page: Int = 1)
