package com.tingbili.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tingbili.app.BiliTingApplication
import com.tingbili.app.data.local.SettingsStore
import com.tingbili.app.data.repo.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class StatsViewModel(
    private val library: LibraryRepository,
    private val settings: SettingsStore
) : ViewModel() {
    data class UiState(
        val totalMs: Long = 0L,
        val monthlyCount: Int = 0,
        val totalCount: Int = 0,
        val year: Int = LocalDate.now().year,
        val month: Int = LocalDate.now().monthValue,
        /** day-of-month -> ms（当月有收听的天） */
        val dailyMs: Map<Int, Long> = emptyMap(),
        val monthTotalMs: Long = 0L,
        val daysInMonth: Int = LocalDate.now().lengthOfMonth(),
        /** 当月 1 号周几：0=周一 .. 6=周日 */
        val firstDayOfWeek: Int = LocalDate.now().withDayOfMonth(1).dayOfWeek.value - 1,
        val maxDayMs: Long = 0L
    )
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private val _monthOffset = MutableStateFlow(0)
    private var report: Map<Int, Long> = emptyMap()

    init {
        viewModelScope.launch {
            settings.listeningMs.collect { r ->
                report = r
                refresh()
            }
        }
    }

    fun load() = viewModelScope.launch {
        val all = library.all()
        _state.value = _state.value.copy(
            totalMs = all.sumOf { it.progressMs.coerceAtLeast(0L) },
            monthlyCount = library.monthlyPlayedCount(),
            totalCount = all.size
        )
        refresh()
    }

    fun prevMonth() { _monthOffset.value -= 1; refresh() }
    fun nextMonth() { _monthOffset.value = (_monthOffset.value + 1).coerceAtMost(0); refresh() }

    private fun refresh() {
        val ym = LocalDate.now().plusMonths(_monthOffset.value.toLong())
        val firstDow = ym.withDayOfMonth(1).dayOfWeek.value - 1
        val days = ym.lengthOfMonth()
        val daily = mutableMapOf<Int, Long>()
        for (d in 1..days) {
            val ms = report[ym.withDayOfMonth(d).toEpochDay().toInt()] ?: 0L
            if (ms > 0) daily[d] = ms
        }
        val max = daily.values.maxOrNull() ?: 0L
        _state.value = _state.value.copy(
            year = ym.year,
            month = ym.monthValue,
            dailyMs = daily,
            monthTotalMs = daily.values.sum(),
            daysInMonth = days,
            firstDayOfWeek = firstDow,
            maxDayMs = max
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BiliTingApplication
                StatsViewModel(app.container.libraryRepo, app.container.settingsStore)
            }
        }
    }
}