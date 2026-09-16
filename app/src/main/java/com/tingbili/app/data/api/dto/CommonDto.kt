package com.tingbili.app.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BiliResponse<T>(
    val code: Int = 0,
    val message: String = "",
    val data: T? = null
)

@Serializable
data class WbiNav(
    @SerialName("wbi_img") val wbiImg: WbiImg? = null
)

@Serializable
data class WbiImg(
    @SerialName("img_url") val imgUrl: String = "",
    @SerialName("sub_url") val subUrl: String = ""
)
