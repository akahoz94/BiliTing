package com.tingbili.app.data.api

class PlayUrlApi(
    private val service: BiliApiService,
    private val keys: WbiKeyStore
) {
    /** 返回视频 DASH 音频流 URL；按音频质量 id 降序选择（192K -> 132K -> 64K），baseUrl 优先于 base_url */
    suspend fun audioUrl(bvid: String, cid: Long): String? {
        val (imgKey, subKey) = keys.keys()
        val params = mapOf(
            "bvid" to bvid,
            "cid" to cid.toString(),
            "qn" to "64",
            "fnval" to "16",
            "fourk" to "0"
        )
        val signed = WbiSigner.sign(params, imgKey, subKey)
        val resp = service.playUrl(
            bvid = bvid, cid = cid, qn = 64, fnval = 16, fourk = 0,
            wRid = signed.getValue("w_rid"), wts = signed.getValue("wts")
        )
        val audios = resp.data?.dash?.audio.orEmpty().sortedByDescending { it.id }
        return audios.firstNotNullOfOrNull { it.baseUrl.takeIf(String::isNotBlank) }
            ?: audios.firstNotNullOfOrNull { it.baseUrl2.takeIf(String::isNotBlank) }
    }
}
