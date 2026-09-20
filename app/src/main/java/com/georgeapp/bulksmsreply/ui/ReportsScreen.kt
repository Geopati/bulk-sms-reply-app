@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.georgeapp.bulksmsreply.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.georgeapp.bulksmsreply.CsvExporter
import com.georgeapp.bulksmsreply.MessageLogEntry
import com.georgeapp.bulksmsreply.PeriodBucket
import com.georgeapp.bulksmsreply.ReportPeriod
import com.georgeapp.bulksmsreply.SenderCount
import java.text.DateFormat
import java.util.Date

private const val MAX_SENDERS_SHOWN_PER_BUCKET = 8

/** Finds http(s) links inside a message body so a report row can show
 *  them as tappable links when expanded (Round 5). */
private val URL_PATTERN = Regex("(https?://[\\w\\-.~:/?#\\[\\]@!$&'()*+,;=%]+)")

@Composable
fun ReportsScreen(
    selectedPeriod: ReportPeriod,
    onPeriodSelected: (ReportPeriod) -> Unit,
    buckets: List<PeriodBucket>,
    onSetLabelOverride: (address: String, override: String?) -> Unit
) {
    val context = LocalContext.current
    // Which sender rows are expanded, keyed by bucket + sender label so
    // state survives a bucket re-sort but resets when you switch period.
    var expandedKeys by remember(selectedPeriod) { mutableStateOf(setOf<String>()) }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "How many texts came in, and from whom",
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(
                onClick = { CsvExporter.exportReports(context, buckets) },
                enabled = buckets.isNotEmpty()
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Export CSV")
            }
        }
        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            ReportPeriod.entries.forEach { period ->
                FilterChip(
                    selected = selectedPeriod == period,
                    onClick = { onPeriodSelected(period) },
                    label = { Text(period.label) },
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            "Tap a sender to see the full messages and any links.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        if (buckets.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("No logged messages yet for this breakdown.")
            }
        } else {
            LazyColumn {
                items(buckets, key = { it.startMillis }) { bucket ->
                    BucketCard(
                        bucket = bucket,
                        expandedKeys = expandedKeys,
                        onToggleSender = { key ->
                            expandedKeys = if (expandedKeys.contains(key)) {
                                expandedKeys - key
                            } else {
                                expandedKeys + key
                            }
                        },
                        onSetLabelOverride = onSetLabelOverride
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun BucketCard(
    bucket: PeriodBucket,
    expandedKeys: Set<String>,
    onToggleSender: (String) -> Unit,
    onSetLabelOverride: (address: String, override: String?) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(bucket.label, fontWeight = FontWeight.Bold)
                Text("${bucket.total} message${if (bucket.total == 1) "" else "s"}")
            }
            Spacer(Modifier.height(6.dp))
            val shown = bucket.bySender.take(MAX_SENDERS_SHOWN_PER_BUCKET)
            shown.forEach { sender ->
                val key = "${bucket.startMillis}_${sender.senderLabel}"
                SenderRow(
                    sender = sender,
                    expanded = expandedKeys.contains(key),
                    onToggle = { onToggleSender(key) },
                    onSetLabelOverride = onSetLabelOverride
                )
            }
            val remaining = bucket.bySender.size - shown.size
            if (remaining > 0) {
                Text(
                    "+ $remaining more sender${if (remaining == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun SenderRow(
    sender: SenderCount,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSetLabelOverride: (address: String, override: String?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                sender.senderLabel,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text("${sender.count}")
        }
        if (expanded) {
            Column(modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)) {
                sender.messages.forEach { message ->
                    ExpandedMessageRow(
                        message = message,
                        onSetLabelOverride = { override -> onSetLabelOverride(message.address, override) }
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun ExpandedMessageRow(message: MessageLogEntry, onSetLabelOverride: (String?) -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column {
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(message.smsDateMillis)),
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    message.address,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Round 7: lets Mr. George fix this specific number's label
            // right from the expanded report row - see LabelTag.kt for why
            // this needed to exist on every screen, not only Messages.
            LabelTagButton(
                labelOverride = message.labelOverride,
                onSetLabelOverride = onSetLabelOverride
            )
        }
        Text(message.body, style = MaterialTheme.typography.bodySmall)

        val urls = remember(message.body) { URL_PATTERN.findAll(message.body).map { it.value }.toList() }
        urls.forEach { url ->
            Text(
                url,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            )
        }

        message.action?.let { action ->
            Text(
                "Action taken: $action",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
