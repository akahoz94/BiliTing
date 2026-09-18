package com.tingbili.app.player

import android.app.PendingIntent
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
            // 点通知栏回到 App（不设 sessionActivity 的话点通知是没反应的）
            val sessionActivity = PendingIntent.getActivity(
                this,
                0,
                Intent(this, com.tingbili.app.MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            // MediaSession 绑定到 PlayerHolder 的同一个 ExoPlayer；
            // 系统锁屏/通知栏看到的 controls 都通过这个 session 桥接到 player，
            // 自动生成 ⏯ / ⏭ / ⏮，并把"下一首"映射成 seekToNext。
            mediaSession = MediaSession.Builder(this, player)
                .setSessionActivity(sessionActivity)
                .build()
            Log.i(TAG, "PlaybackService onCreate, MediaSession ready")
        }.onFailure {
            Log.e(TAG, "PlaybackService.onCreate 失败", it)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /**
     * 不覆写 onTaskRemoved：media3 自带实现已经是"正在播就继续跑，没在播才 pause 并停服"，
     * 自己再写一遍只会引入差异（曾经写成"只要不是 playWhenReady 就 stopSelf"，
     * 与官方 isPlaybackOngoing() 判定不一致，划掉任务时容易把正在播的会话掐掉）。
     */

    override fun onDestroy() {
        // 只释放 MediaSession，**绝不能**在服务里 release 共享的 ExoPlayer：
        // PlayerHolder 持有的是 Application 级单例，服务一旦被系统回收就把全局播放器
        // 一起 release 掉，之后点播放全都没反应（"退后台一会儿就停了"的元凶之一）。
        runCatching {
            mediaSession?.release()
        }.onFailure {
            Log.w(TAG, "释放 MediaSession 异常", it)
        }
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PlaybackService"
    }
}
