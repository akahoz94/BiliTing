package com.tingbili.app.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val theme by viewModel.themeMode.collectAsState()
    val keywords by viewModel.keywords.collectAsState()
    val speed by viewModel.speed.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("设置") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            item {
                Text("主题", style = MaterialTheme.typography.titleSmall)
                Row {
                    listOf(0 to "跟随系统", 1 to "浅色", 2 to "深色").forEach { (mode, label) ->
                        FilterChip(selected = theme == mode, onClick = { viewModel.setTheme(mode) },
                            label = { Text(label) }, modifier = Modifier.padding(end = 6.dp))
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)); Text("听书关键词", style = MaterialTheme.typography.titleSmall) }
            items(keywords.toList()) { kw -> Text(kw, modifier = Modifier.padding(vertical = 2.dp)) }
            item {
                var newKw by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = newKw, onValueChange = { newKw = it },
                        label = { Text("新增关键词") }, modifier = Modifier.weight(1f))
                    TextButton(onClick = { if (newKw.isNotBlank()) { viewModel.addKeyword(newKw.trim()); newKw = "" } }) { Text("添加") }
                }
            }
            item { Spacer(Modifier.height(16.dp)); Text("默认倍速", style = MaterialTheme.typography.titleSmall) }
            item {
                Slider(value = speed, onValueChange = viewModel::setSpeed, valueRange = 0.5f..2.0f, steps = 5)
                Text("${speed}x", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
