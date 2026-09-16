package com.tingbili.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tingbili.app.data.api.dto.SearchItem
import com.tingbili.app.data.repo.SearchRepository
import com.tingbili.app.util.FormatUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onOpenPlayer: (SearchItem) -> Unit = {},
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory)
) {
    val s by viewModel.state.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text("搜索") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = s.keyword,
                onValueChange = viewModel::onKeywordChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                placeholder = { Text("搜索：有声书 / 小说 / 广播剧 / 播客...") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search(1) })
            )
            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf("video" to "视频", "audio" to "音频", "all" to "全部").forEach { (type, label) ->
                    FilterChip(
                        selected = s.type == type,
                        onClick = { viewModel.onTypeChange(type) },
                        label = { Text(label) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Text("听书优先", style = MaterialTheme.typography.bodySmall)
                Switch(checked = s.listeningOnly, onCheckedChange = viewModel::onListeningOnlyChange)
            }
            if (s.error != null) Text(s.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
                items(s.results, key = { it.bvid.ifBlank { it.aid.toString() } }) { item ->
                    SearchResultRow(item, onClick = { onOpenPlayer(item) })
                }
                if (s.hasMore) {
                    item { TextButton(onClick = viewModel::loadMore, modifier = Modifier.fillMaxWidth()) { Text("加载更多") } }
                }
            }
        }
    }
}

@Composable
fun SearchResultRow(item: SearchItem, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(SearchRepository.stripHtml(item.title), style = MaterialTheme.typography.bodyLarge, maxLines = 2)
        Spacer(Modifier.height(2.dp))
        Text(
            "${item.author} · ${item.durationStr.ifBlank { FormatUtil.duration("") }} · ${if (item.bvid.isNotBlank()) "视频" else "音频"}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}
