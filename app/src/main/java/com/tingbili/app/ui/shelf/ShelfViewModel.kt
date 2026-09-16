package com.tingbili.app.ui.shelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tingbili.app.BiliTingApplication
import com.tingbili.app.data.local.BookRecord
import com.tingbili.app.data.local.BookRecordDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ShelfViewModel(dao: BookRecordDao) : ViewModel() {
    val favorites: StateFlow<List<BookRecord>> = dao.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BiliTingApplication
                ShelfViewModel(app.container.dao)
            }
        }
    }
}
