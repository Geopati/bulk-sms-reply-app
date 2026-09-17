@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.georgeapp.bulksmsreply.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.georgeapp.bulksmsreply.PeriodBucket
import com.georgeapp.bulksmsreply.ReportPeriod

private const val MAX_SENDERS_SHOWN_PER_BUCKET = 8

@Composable
fun ReportsScreen(
    selectedPeriod: ReportPeriod,
    onPeriodSelected: (ReportPeriod) -> Unit,
    buckets: List<PeriodBucket>
) {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text(
            "How many texts came in, and from whom",
            style = MaterialTheme.typography.titleMedium
        )
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

        Spacer(Modifier.height(12.dp))

        if (buckets.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("No logged messages yet for this breakdown.")
            }
        } else {
            LazyColumn {
                items(buckets, key = { it.startMillis }) { bucket ->
                    BucketCard(bucket)
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun BucketCard(bucket: PeriodBucket) {
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
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
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
