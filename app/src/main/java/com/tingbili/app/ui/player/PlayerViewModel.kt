package com.tingbili.app.ui.player

import android.util.Log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.Player
import com.tingbili.app.BiliTingApplication
import com.tingbili.app.data.local.BookRecord
import com.tingbili.app.data.local.SettingsStore
import com.tingbili.app.data.repo.LibraryRepository
import com.tingbili.app.player.PartItem
import com.tingbili.app.player.PlayerHolder
import com.tingbili.app.player.PlayerLauncher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val holder: PlayerHolder,
    private val library: LibraryRepository,
    private val launcher: PlayerLauncher,
    private val settings: SettingsStore
) : ViewModel() {
    data class UiState(
        val record: BookRecord? = null,
        val isPlaying: Boolean = false,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val speed: Float = 1.0f,
        val sleepRemainSec: Int = -1,
        val sleepEndOfTrack: Boolean = false,
        val queue: List<PartItem> = emptyList(),
        val queueIndex: Int = 0
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private var autoNextFired = false

    // 睡眠定时器本体在 PlayerHolder（App 级）：这里只做转发 + UI 状态镜像。
    // 早前放在本 ViewModel 里有两个致命问题：
    //  1) 它在后台线程轮询 player.isPlaying —— ExoPlayer 的 getter 有线程校验，必崩；
    //  2) 播放页一退出 onCleared 就把定时器停掉，"听完本集/倒计时"当场失效。

    fun bind() {
        viewModelScope.launch {
            var ticks = 0
            while (true) {
                val p = holder.player
                val ended = !p.isPlaying && p.playbackState == Player.STATE_ENDED
                if (ended && !autoNextFired) {
                    autoNextFired = true
                    Log.i(
                        "PlayerViewModel",
                        "ended -> autoAdvance=${holder.shouldAutoAdvance()} " +
                            "itemCount=${p.mediaItemCount} idx=${p.currentMediaItemIndex}"
                    )
                    if (holder.shouldAutoAdvance()) {
                        launcher.nextPart()
                    } else {
                        // 「播完本集自动停止」或关掉了「自动下一集」：就到这儿
                        holder.stopSleep()
                    }
                } else if (!ended) {
                    autoNextFired = false
                }
                _state.value = UiState(
                    record = holder.record.value,
                    isPlaying = p.isPlaying,
                    positionMs = p.currentPosition,
                    durationMs = p.duration.coerceAtLeast(0L),
                    speed = p.playbackParameters.speed,
                    sleepRemainSec = holder.sleepRemainSec.value,
                    sleepEndOfTrack = holder.sleepEndOfTrack.value,
                    queue = holder.currentQueue(),
                    queueIndex = holder.currentQueueIndex()
                )
                // 每 10 次循环（约 5 秒）落盘一次进度，防止系统回收/强杀丢进度
                ticks++
                if (ticks % 10 == 0 && p.isPlaying) saveProgress()
                delay(500)
            }
        }
    }

    fun toggle() { holder.togglePlay() }
    fun setSpeed(v: Float) {
        holder.setSpeed(v)
        viewModelScope.launch {
            settings.setPlaybackSpeed(v)
            // 按 UP 主记忆倍速
            val mid = holder.record.value?.ownerMid ?: 0L
            val remember = settings.rememberSpeedPerAuthor.firstOrNull() ?: false
            if (remember && mid > 0L) {
                settings.setAuthorSpeed(mid.toString(), v)
            }
        }
    }
    fun seekTo(ms: Long) { holder.seekTo(ms) }

    fun nextPart() = viewModelScope.launch { launcher.nextPart() }
    fun prevPart() = viewModelScope.launch { launcher.prevPart() }
    fun jumpToPart(index: Int) = viewModelScope.launch { launcher.jumpToPart(index) }

    fun startSleep(minutes: Int) {
        holder.startSleep(minutes)
        _state.value = _state.value.copy(sleepRemainSec = minutes * 60, sleepEndOfTrack = false)
    }

    fun startSleepEndOfTrack() {
        holder.startSleepEndOfTrack()
        _state.value = _state.value.copy(sleepRemainSec = -1, sleepEndOfTrack = true)
    }

    fun stopSleep() {
        holder.stopSleep()
        _state.value = _state.value.copy(sleepRemainSec = -1, sleepEndOfTrack = false)
    }

    fun saveProgress() {
        val r = holder.record.value ?: return
        viewModelScope.launch {
            library.recordPlayed(
                r.copy(
                    progressMs = holder.player.currentPosition,
                    durationMs = holder.player.duration.coerceAtLeast(0L),
                    speed = holder.player.playbackParameters.speed
                )
            )
        }
    }

    fun toggleFavorite() {
        val r = holder.record.value ?: return
        val newFav = !r.isFavorite
        holder.updateRecord(r.copy(isFavorite = newFav))
        _state.value = _state.value.copy(record = r.copy(isFavorite = newFav))
        viewModelScope.launch { library.toggleFavorite(r.id, newFav) }
    }

    override fun onCleared() {
        // 注意：**不要**在这里停睡眠定时器 —— 它在 PlayerHolder（App 级）上，
        // 退出播放页、切 tab 都得继续生效（否则用户锁屏听书时定时会静默失效）。
        super.onCleared()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BiliTingApplication
                PlayerViewModel(
                    app.container.playerHolder,
                    app.container.libraryRepo,
                    PlayerLauncher(
                        app.container.playerHolder,
                        app.container.playRepo,
                        app.container.libraryRepo,
                        app.container.biliService,
                        app.settingsStore
                    ),
                    app.settingsStore
                )
            }
        }
    }
}
