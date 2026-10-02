package com.tingbili.app.player

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import com.tingbili.app.BiliTingApplication
import com.tingbili.app.R
import com.tingbili.app.util.CoverDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 桌面播放控件。两个尺寸（4x2 宽版 / 2x2 紧凑版）共用一套渲染逻辑，只是布局不同。
 *
 * 按钮 PendingIntent 全部指向 PlaybackService（service action），由服务 onStartCommand 统一分发：
 *   PLAY_PAUSE / PREV / NEXT。封面用 CoverDownloader 本地缓存解码（~256px），内存缓存避免每 2 秒重复解码。
 *
 * 刷新：PlayerHolder 状态变化时调 [WidgetUpdater.updateAll]；系统 onUpdate 时也渲染一次。
 */
abstract class PlaybackWidgetBaseProvider(private val layoutRes: Int) : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> renderWidget(context, mgr, id, layoutRes) }
    }

    companion object {
        internal fun buildPendingIntent(context: Context, action: String): PendingIntent {
            val intent = Intent(context, PlaybackService::class.java).setAction(action)
            return PendingIntent.getService(
                context,
                action.hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        fun renderWidget(context: Context, mgr: AppWidgetManager, id: Int, layoutRes: Int) {
            val rv = RemoteViews(context.packageName, layoutRes)
            // 按钮
            rv.setOnClickPendingIntent(R.id.widget_play, buildPendingIntent(context, PlaybackService.ACTION_PLAY_PAUSE))
            rv.setOnClickPendingIntent(R.id.widget_prev, buildPendingIntent(context, PlaybackService.ACTION_PREV))
            rv.setOnClickPendingIntent(R.id.widget_next, buildPendingIntent(context, PlaybackService.ACTION_NEXT))
            // 内容
            WidgetUpdater.applyState(context, rv)
            mgr.updateAppWidget(id, rv)
        }
    }
}

class PlaybackWidgetWideProvider : PlaybackWidgetBaseProvider(R.layout.widget_wide)

class PlaybackWidgetCompactProvider : PlaybackWidgetBaseProvider(R.layout.widget_compact)

/** 集中读 PlayerHolder 状态并渲染 RemoteViews（必须主线程调用，PlayerHolder getter 校验线程）。 */
object WidgetUpdater {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** 封面内存缓存：key = cover URL；两个尺寸共用同一张图。 */
    @Volatile private var coverKey: String? = null
    @Volatile private var coverBmp: Bitmap? = null
    @Volatile private var loadingKey: String? = null
    /** 封面下载失败冷却：60s 内不重试同一 URL（离线避免每 2 秒打 CDN）。 */
    @Volatile private var failedCoverUrl: String? = null
    @Volatile private var failedCoverAt: Long = 0L

    fun updateAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context) ?: return
        val providers = listOf(
            ComponentName(context, PlaybackWidgetWideProvider::class.java),
            ComponentName(context, PlaybackWidgetCompactProvider::class.java)
        )
        providers.forEach { cn ->
            mgr.getAppWidgetIds(cn).forEach { id ->
                // 两个 provider 的布局不同，按 ComponentName 选 layout
                val layout = if (cn.className.endsWith("CompactProvider"))
                    R.layout.widget_compact else R.layout.widget_wide
                PlaybackWidgetBaseProvider.renderWidget(context, mgr, id, layout)
            }
        }
    }

    internal fun applyState(context: Context, rv: RemoteViews) {
        val app = context.applicationContext as? BiliTingApplication ?: return
        runCatching {
            val holder = app.playerHolder
            val rec = holder.record.value
            val playing = holder.isPlaying()
            rv.setImageViewResource(R.id.widget_play, if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            if (rec == null) {
                rv.setTextViewText(R.id.widget_title, "未在播放")
                rv.setTextViewText(R.id.widget_subtitle, "")
                rv.setProgressBar(R.id.widget_progress, 1000, 0, false)
                return
            }
            rv.setTextViewText(R.id.widget_title, rec.title.ifBlank { "听书" })
            val partTxt = if (rec.totalParts > 0) "第 ${rec.currentPart}/${rec.totalParts} 集" else ""
            val remain = holder.sleepRemainSec.value
            val sleepTxt = if (remain > 0) "🌙 定时 ${remain / 60 + 1}:${(remain % 60).toString().padStart(2, '0')}" else ""
            rv.setTextViewText(R.id.widget_subtitle, listOf(partTxt, sleepTxt).filter { it.isNotBlank() }.joinToString(" · "))
            // 进度（按 player 实时位置，而不是 DB 快照）
            runCatching {
                val pos = holder.player.currentPosition
                val dur = holder.player.duration.coerceAtLeast(1L)
                rv.setProgressBar(R.id.widget_progress, 1000, (pos * 1000 / dur).toInt(), false)
            }
            // 封面：命中缓存直接设；未命中异步加载（成功后回主线程重渲染所有 widget）
            applyCover(context, rv, rec.cover.takeIf { it.isNotBlank() })
        }
    }

    private fun applyCover(context: Context, rv: RemoteViews, url: String?) {
        if (url == null) return // 无封面：保持 accent 底色，不动缓存 key（下次可重试）
        if (url == failedCoverUrl && System.currentTimeMillis() - failedCoverAt < 60_000L) return // 失败冷却期
        if (url == coverKey && coverBmp != null) {
            rv.setImageViewBitmap(R.id.widget_cover, coverBmp)
            return
        }
        loadCover(context, url)
    }

    private fun loadCover(context: Context, url: String) {
        if (loadingKey == url) return
        loadingKey = url
        scope.launch {
            val bmp = runCatching {
                val f = CoverDownloader.ensureLocalFile(context, url) ?: return@launch
                decodeSampled(f.absolutePath, 256)
            }.getOrNull()
            if (bmp != null) {
                coverBmp = bmp
                coverKey = url
                failedCoverUrl = null
                failedCoverAt = 0L
            } else {
                failedCoverUrl = url
                failedCoverAt = System.currentTimeMillis()
            }
            loadingKey = null
            // 回主线程重渲染（PlayerHolder getter 校验线程）
            Handler(Looper.getMainLooper()).post { runCatching { updateAll(context) } }
        }
    }

    /** 按长边 target 采样解码，防 OOM/损坏图崩控件。 */
    private fun decodeSampled(path: String, target: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val max = maxOf(bounds.outWidth, bounds.outHeight)
        if (max <= 0) return null
        var sample = 1
        while (max / sample > target * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return runCatching { BitmapFactory.decodeFile(path, opts) }.getOrNull()
    }
}
