package com.tingbili.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tingbili.app.data.local.BookRecord
import com.tingbili.app.util.FormatUtil

@Composable
fun ListItemRow(record: BookRecord, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(record.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
        Spacer(Modifier.height(2.dp))
        Text(
            "${record.owner} · ${if (record.totalParts > 0) "第${record.currentPart}集/共${record.totalParts}集" else FormatUtil.progress(record.progressMs)} · ${record.type}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}
