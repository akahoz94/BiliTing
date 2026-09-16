package com.tingbili.app.player

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.tingbili.app.BiliTingApplication

/**
 * 后台播放 + 锁屏控制服务。持有全局 PlayerHolder 中的 ExoPlayer 构建 MediaSession。
 */
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val playerHolder = (application as? BiliTingApplication)?.playerHolder ?: return
        mediaSession = MediaSession.Builder(this, playerHolder.player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        // 只释放 MediaSession，不 release 播放器：ExoPlayer 由 Application 持有的 PlayerHolder 管理
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
