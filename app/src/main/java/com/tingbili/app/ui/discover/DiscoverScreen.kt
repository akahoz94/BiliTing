package com.tingbili.app.ui.discover

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tingbili.app.data.api.dto.SearchItem
import com.tingbili.app.ui.components.EmptyState
import com.tingbili.app.ui.search.SearchResultRow
import com.tingbili.app.ui.theme.AppTokens
import kotlinx.coroutines.launch

/**
 * 发现页 —— 每个分类是一页，顶部 chip 与页面双向联动（点 chip 翻页、左右滑同步 chip）。
 * 内容走现有搜索接口，滚到底自动续页。与听单/搜索/历史同一套设计语言。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    onOpenPlayer: (SearchItem) -> Unit = {},
    onOpenAuthor: (Long, String, String, String) -> Unit = { _, _, _, _ -> },
    onFavorite: (SearchItem) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = viewModel(factory = DiscoverViewModel.Factory)
) {
    val categories = viewModel.categories
    val feeds by viewModel.feeds.collectAsState()
    val pagerState = rememberPagerState(pageCount = { categories.size })
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "发现",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = AppTokens.Spacing4, vertical = AppTokens.Spacing2),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val selectedLabel = categories[pagerState.currentPage].label
                categories.forEachIndexed { index, cat ->
                    FilterChip(
                        selected = cat.label == selectedLabel,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        label = { Text(cat.label) }
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { index ->
                val category = categories[index]
                CategoryPage(
                    category = category,
                    feed = feeds[category.label] ?: CategoryFeed(),
                    viewModel = viewModel,
                    onOpenPlayer = onOpenPlayer,
                    onOpenAuthor = onOpenAuthor,
                    onFavorite = onFavorite
                )
            }
        }
    }
}


@Composable
private fun CategoryPage(
    category: DiscoverCategory,
    feed: CategoryFeed,
    viewModel: DiscoverViewModel,
    onOpenPlayer: (SearchItem) -> Unit,
    onOpenAuthor: (Long, String, String, String) -> Unit,
    onFavorite: (SearchItem) -> Unit
) {
    LaunchedEffect(category.label) { viewModel.ensureLoaded(category) }

    Box(Modifier.fillMaxSize()) {
        when {
            feed.items.isEmpty() && feed.loading -> {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }

            feed.items.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        title = "「${category.label}」暂时没拉到内容",
                        subtitle = "左右滑动换个分类，或者稍后再试"
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = AppTokens.Spacing4,
                        vertical = AppTokens.Spacing2
                    )
                ) {
                    items(feed.items, key = { it.stableKey() }) { item ->
                        Spacer(Modifier.height(AppTokens.Spacing3))
                        SearchResultRow(
                            item = item,
                            onClick = { onOpenPlayer(item) },
                            onOpenAuthor = {
                                onOpenAuthor(item.uid, item.author, item.upic, item.bvid)
                            },
                            onFavorite = { onFavorite(item) }
                        )
                    }
                    // 尾巴一露头就续页，省掉滚动监听
                    item {
                        LaunchedEffect(feed.items.size) { viewModel.loadMore(category) }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            if (feed.loading) {
                                CircularProgressIndicator(Modifier.padding(top = AppTokens.Spacing4))
                            } else if (feed.endReached) {
                                Text(
                                    "已经到底了",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = AppTokens.Spacing4)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
