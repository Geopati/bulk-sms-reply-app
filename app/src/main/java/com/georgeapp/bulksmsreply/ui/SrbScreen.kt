package com.georgeapp.bulksmsreply.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.georgeapp.bulksmsreply.AttributionExtractor
import com.georgeapp.bulksmsreply.MessageLogDatabase
import java.text.DateFormat
import java.util.Date

/**
 * Round 5: the "S.R.B." (Stop / Report / Block) tab. Once a bulk action is
 * applied to a conversation on the Messages tab, it moves out of that list
 * and shows up here instead - so a conversation Mr. George already
 * processed can't be accidentally selected and re-processed a second time
 * on the Messages tab. Confirmations are shown as an in-place status on
 * each row here rather than a separate tab, per his decision, to keep this
 * to one place to check.
 *
 * Note on "confirmed": this reflects that the app successfully carried out
 * the action locally (sent the reply, added the block, forwarded the spam
 * report) - it is not a delivery receipt from the carrier or the number
 * being texted, which Android has no way to give this app.
 *
 * Round 7 (2026-09-21): each row now also has the tag icon so Mr. George
 * can fix a wrong label here too - previously the override control only
 * existed on the Messages tab, so a conversation lost that option the
 * moment it was processed and moved here, which is what he ran into.
 */
@Composable
fun SrbScreen(
    processed: List<MessageLogDatabase.ProcessedConversation>,
    onSetLabelOverride: (address: String, override: String?) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text(
            "Stop / Report / Block",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            "Conversations you've already processed - moved out of Messages " +
                "so you don't accidentally do them again.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        if (processed.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nothing processed yet - bulk actions you apply on Messages will show up here.")
            }
        } else {
            LazyColumn {
                items(processed, key = { it.normalizedAddress }) { conversation ->
                    SrbRow(
                        conversation = conversation,
                        onSetLabelOverride = { override -> onSetLabelOverride(conversation.address, override) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SrbRow(
    conversation: MessageLogDatabase.ProcessedConversation,
    onSetLabelOverride: (String?) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    AttributionExtractor.reLabel(
                        conversation.attribution,
                        conversation.body,
                        conversation.address,
                        conversation.labelOverride
                    ),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(4.dp))
                LabelTagButton(
                    labelOverride = conversation.labelOverride,
                    onSetLabelOverride = onSetLabelOverride
                )
            }
            Text(
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(Date(conversation.actionAtMillis)),
                style = MaterialTheme.typography.labelSmall
            )
        }
        Text(
            conversation.address,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Row {
            actionBadges(conversation.action).forEach { badge ->
                Text(
                    "$badge ✓  ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            conversation.body,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Turns the stored "REPLIED, BLOCKED" style action string into friendly
 *  labels for display, e.g. ["Replied", "Blocked"]. */
private fun actionBadges(rawAction: String): List<String> =
    rawAction.split(",").map { it.trim() }.mapNotNull {
        when (it) {
            "REPLIED" -> "Replied"
            "BLOCKED" -> "Blocked"
            "REPORTED_SPAM" -> "Reported"
            "" -> null
            else -> it
        }
    }
