package com.tingbili.app.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tingbili.app.BiliTingApplication
import com.tingbili.app.data.local.CrashLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashLogsViewModel(app: BiliTingApplication) : ViewModel() {
    private val dao = app.appDatabase.crashLogDao()
    private val _logs = MutableStateFlow<List<CrashLog>>(emptyList())
    val logs: StateFlow<List<CrashLog>> = _logs

    init {
        viewModelScope.launch(Dispatchers.IO) { _logs.value = dao.recent(50) }
    }

    fun clear() = viewModelScope.launch(Dispatchers.IO) {
        dao.clearAll()
        _logs.value = emptyList()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BiliTingApplication
                CrashLogsViewModel(app)
            }
        }
    }
}

@Composable
fun CrashLogsScreen(
    onBack: () -> Unit,
    viewModel: CrashLogsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = CrashLogsViewModel.Factory)
) {
    val logs by viewModel.logs.collectAsState()
    val clipboard = LocalClipboardManager.current
    var expandedId by remember { mutableStateOf<Long?>(null) }
    val timeFmt = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }

    fun copyAll() {
        val text = logs.joinToString("\n\n") {
            buildString {
                appendLine("[${timeFmt.format(Date(it.timestamp))}] ${it.exceptionType}: ${it.message ?: ""}")
                appendLine("device=${it.deviceModel} android=${it.androidVersion} thread=${it.threadName}")
                append(it.stackTrace)
            }
        }.ifBlank { "暂无错误日志" }
        clipboard.setText(AnnotatedString(text))
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    "错误日志",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = ::copyAll) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "复制全部", Modifier.padding(end = 4.dp))
                    Text("复制全部")
                }
                if (logs.isNotEmpty()) {
                    TextButton(onClick = { viewModel.clear() }) { Text("清空") }
                }
            }

            if (logs.isEmpty()) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("暂无错误日志", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    items(logs, key = { it.id }) { log ->
                        val expanded = expandedId == log.id
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { expandedId = if (expanded) null else log.id }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(
                                timeFmt.format(Date(log.timestamp)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${log.exceptionType}: ${log.message ?: ""}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            if (expanded) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "device=${log.deviceModel} android=${log.androidVersion} thread=${log.threadName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    log.stackTrace,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        androidx.compose.material3.HorizontalDivider()
                    }
                }
            }
        }
    }
}
