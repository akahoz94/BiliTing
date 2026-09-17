package com.tingbili.app.player

import android.content.Intent
import android.util.Log
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * 后台播放服务（系统通知栏 / 锁屏控件 / Android Auto / Wear OS 的关键）。
 *
 * 为什么必须存在：
 *   ExoPlayer 单跑只会出声，不会生成任何系统可见的播放控件。
 *   把 ExoPlayer 绑定到一个 MediaSessionService 上，Android 系统才会：
 *     - 拉起前台服务（带媒体 playback 类型的通知）
 *     - 在锁屏 / 通知栏 / 系统媒体控件 / Wear OS / Android Auto 露出 ⏯ ⏭ ⏮
 *     - 自动响应耳机按键、蓝牙按键、Google Assistant
 *
 * 注意：MediaSession 拿的是当前 App 里"唯一活跃"的 ExoPlayer 实例 —— 这里直接
 *  复用 PlayerHolder 里那个懒加载的 player，让播放状态（队列、进度、错误）天然共享。
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        runCatching {
            val app = applicationContext as com.tingbili.app.BiliTingApplication
            val player = app.playerHolder.player
            // MediaSession 绑定到 PlayerHolder 的同一个 ExoPlayer；
            // 系统锁屏/通知栏看到的 controls 都通过这个 session 桥接到 player，
            // 自动生成 ⏯ / ⏭ / ⏮，并把"下一首"映射成 seekToNext。
            mediaSession = MediaSession.Builder(this, player).build()
            Log.i(TAG, "PlaybackService onCreate, MediaSession ready")
        }.onFailure {
            Log.e(TAG, "PlaybackService.onCreate 失败", it)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 用户从最近任务里滑掉 App 时，若非播放中则停服，避免残留通知
        runCatching {
            val player = mediaSession?.player
            if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
                stopSelf()
            }
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        runCatching {
            mediaSession?.run {
                player.release()
                release()
            }
            mediaSession = null
        }.onFailure {
            Log.w(TAG, "onDestroy 清理 MediaSession 异常", it)
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PlaybackService"
    }
}