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
