@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.georgeapp.bulksmsreply.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.georgeapp.bulksmsreply.CsvExporter
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
    onRangeSelected: (Int?) -> Unit,
    onSetLabelOverride: (address: String, override: String?) -> Unit
) {
    val context = LocalContext.current

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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "${entries.size} message${if (entries.size == 1) "" else "s"} logged",
                style = MaterialTheme.typography.labelMedium
            )
            TextButton(
                onClick = { CsvExporter.exportLog(context, entries) },
                enabled = entries.isNotEmpty()
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Export CSV")
            }
        }

        Spacer(Modifier.height(4.dp))

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("No logged messages match this search/range yet.")
            }
        } else {
            LazyColumn {
                items(entries, key = { it.id }) { entry ->
                    LogRow(
                        entry = entry,
                        onSetLabelOverride = { override -> onSetLabelOverride(entry.address, override) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: MessageLogEntry, onSetLabelOverride: (String?) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    entry.senderLabel,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(4.dp))
                // Round 7: lets Mr. George correct a wrong label right from
                // the Log tab, not only the fleeting moment before a
                // conversation is processed - see LabelTag.kt for why.
                LabelTagButton(
                    labelOverride = entry.labelOverride,
                    onSetLabelOverride = onSetLabelOverride
                )
            }
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
        // Round 5: only flag unread messages - a processed/read one shows
        // nothing here rather than a "Read (in this app)" line.
        if (!entry.readLocally) {
            Text(
                "Unread",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
