@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.georgeapp.bulksmsreply.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.georgeapp.bulksmsreply.MessageLogEntry
import java.text.DateFormat
import java.util.Date

/** One day, in milliseconds - used to build the quick range filter chips. */
private const val ONE_DAY_MILLIS = 24L * 60 * 60 * 1000

/** The preset time ranges shown as chips; null means "All time." */
val LOG_RANGE_OPTIONS: List<Pair<String, Int?>> = listOf(
    "7 days" to 7,
    "30 days" to 30,
    "90 days" to 90,
    "1 year" to 365,
    "All time" to null
)

fun rangeDaysToSinceMillis(days: Int?): Long? =
    days?.let { System.currentTimeMillis() - it * ONE_DAY_MILLIS }

@Composable
fun LogScreen(
    entries: List<MessageLogEntry>,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    selectedRangeDays: Int?,
    onRangeSelected: (Int?) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        OutlinedTextField(
            value = searchText,
            onValueChange = onSearchTextChange,
            label = { Text("Search sender, \"on behalf of\", or message text") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            LOG_RANGE_OPTIONS.forEach { (label, days) ->
                FilterChip(
                    selected = selectedRangeDays == days,
                    onClick = { onRangeSelected(days) },
                    label = { Text(label) },
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            "${entries.size} message${if (entries.size == 1) "" else "s"} logged",
            style = MaterialTheme.typography.labelMedium
        )

        Spacer(Modifier.height(4.dp))

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("No logged messages match this search/range yet.")
            }
        } else {
            LazyColumn {
                items(entries, key = { it.id }) { entry ->
                    LogRow(entry)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: MessageLogEntry) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(entry.senderLabel, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(Date(entry.smsDateMillis)),
                style = MaterialTheme.typography.labelSmall
            )
        }
        Text(entry.body, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        entry.action?.let { action ->
            Text(
                "Action taken: $action",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            if (entry.readLocally) "Read (in this app)" else "Unread (in this app)",
            style = MaterialTheme.typography.labelSmall,
            color = if (entry.readLocally) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            }
        )
    }
}
