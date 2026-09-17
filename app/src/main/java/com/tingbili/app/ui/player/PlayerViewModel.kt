package com.tingbili.app.ui.player

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
import com.tingbili.app.player.SleepTimer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    private var sleepRemain = -1
    private var sleepTotalMs = 0L
    private var autoNextFired = false

    private val sleepTimer = SleepTimer(
        onFire = {
            holder.pause()
            sleepRemain = -1
            _state.value = _state.value.copy(sleepRemainSec = -1, sleepEndOfTrack = false)
        },
        isPlayingProvider = { holder.player.isPlaying }
    )

    fun bind() {
        viewModelScope.launch {
            while (true) {
                val p = holder.player
                val ended = !p.isPlaying && p.playbackState == Player.STATE_ENDED
                if (ended && !autoNextFired) {
                    autoNextFired = true
                    if (sleepTimer.isEndOfTrack()) {
                        sleepTimer.stop()
                    }
                    launcher.nextPart()
                } else if (!ended) {
                    autoNextFired = false
                }
                _state.value = UiState(
                    record = holder.record.value,
                    isPlaying = p.isPlaying,
                    positionMs = p.currentPosition,
                    durationMs = p.duration.coerceAtLeast(0L),
                    speed = p.playbackParameters.speed,
                    sleepRemainSec = sleepRemain,
                    sleepEndOfTrack = sleepTimer.isEndOfTrack(),
                    queue = holder.currentQueue(),
                    queueIndex = holder.currentQueueIndex()
                )
                delay(500)
            }
        }
    }

    fun toggle() { holder.togglePlay() }
    fun setSpeed(v: Float) {
        holder.setSpeed(v)
        viewModelScope.launch { settings.setPlaybackSpeed(v) }
    }
    fun seekTo(ms: Long) { holder.seekTo(ms) }

    fun nextPart() = viewModelScope.launch { launcher.nextPart() }
    fun prevPart() = viewModelScope.launch { launcher.prevPart() }
    fun jumpToPart(index: Int) = viewModelScope.launch { launcher.jumpToPart(index) }

    fun startSleep(minutes: Int) {
        sleepTotalMs = minutes * 60_000L
        sleepRemain = minutes * 60
        sleepTimer.start(minutes)
    }

    fun startSleepEndOfTrack() {
        sleepTimer.startEndOfTrack()
        sleepRemain = -1
        _state.value = _state.value.copy(sleepEndOfTrack = true)
    }

    fun stopSleep() {
        sleepTimer.stop()
        sleepRemain = -1
        _state.value = _state.value.copy(sleepEndOfTrack = false)
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
        sleepTimer.stop()
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
                        app.container.biliService
                    ),
                    app.settingsStore
                )
            }
        }
    }
}