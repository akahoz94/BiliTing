package com.tingbili.app.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlayUrlData(
    @SerialName("dash") val dash: Dash? = null,
    val durl: List<Durl> = emptyList()
)

@Serializable
data class Dash(
    val audio: List<DashAudio> = emptyList()
)

@Serializable
data class DashAudio(
    val id: Int = 0,
    @SerialName("baseUrl") val baseUrl: String = "",
    @SerialName("base_url") val baseUrl2: String = ""
)

@Serializable
data class Durl(val url: String = "")

@Serializable
data class AudioUrlData(val url: String = "", @SerialName("cdns") val cdns: List<String> = emptyList())
