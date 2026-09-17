package com.tingbili.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.tingbili.app.data.api.dto.SearchItem
import com.tingbili.app.data.repo.SearchRepository
import com.tingbili.app.util.CoverUtil
import com.tingbili.app.util.FormatUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onOpenPlayer: (SearchItem) -> Unit = {},
    onOpenAuthor: (Long, String, String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory)
) {
    val s by viewModel.state.collectAsState()
    Scaffold(modifier = modifier, topBar = { TopAppBar(title = { Text("搜索") }) }) { padding ->
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
                    SearchResultRow(
                        item = item,
                        onClick = { onOpenPlayer(item) },
                        onOpenAuthor = { onOpenAuthor(item.uid, item.author, item.upic) }
                    )
                }
                if (s.hasMore) {
                    item { TextButton(onClick = viewModel::loadMore, modifier = Modifier.fillMaxWidth()) { Text("加载更多") } }
                }
            }
        }
    }
}

@Composable
fun SearchResultRow(item: SearchItem, onClick: () -> Unit, onOpenAuthor: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        // 点标题 → 播放
        Text(
            SearchRepository.stripHtml(item.title),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            modifier = Modifier.clickable(onClick = onClick)
        )
        Spacer(Modifier.height(6.dp))
        // 点作者头像/名字 → 进入作者主页
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(onClick = onOpenAuthor)
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                val upic = CoverUtil.normalize(item.upic)
                if (upic.isNotBlank()) {
                    AsyncImage(
                        model = upic,
                        contentDescription = item.author,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Text(
                        item.author.take(1).ifBlank { "·" },
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Text(
                item.author,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "${item.durationStr.ifBlank { FormatUtil.duration("") }} · ${if (item.bvid.isNotBlank()) "视频" else "音频"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}
