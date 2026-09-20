@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.georgeapp.bulksmsreply.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.georgeapp.bulksmsreply.APP_VERSION
import com.georgeapp.bulksmsreply.APP_VERSION_HISTORY
import com.georgeapp.bulksmsreply.AttributionExtractor
import com.georgeapp.bulksmsreply.Conversation
import com.georgeapp.bulksmsreply.PhoneNumbers
import java.text.DateFormat
import java.util.Date

@Composable
fun ConversationListScreen(
    conversations: List<Conversation>,
    selectedAddresses: Set<String>,
    onToggleSelected: (Conversation) -> Unit,
    onSelectAll: () -> Unit,
    /** Selects only the conversations that still have something this app
     *  hasn't marked read yet - a shortcut for "select everything I still
     *  need to process," instead of checking each box by hand. */
    onSelectAllUnread: () -> Unit,
    onClearSelection: () -> Unit,
    /** Normalized addresses this app hasn't marked "read" yet (see
     *  MessageLogDatabase.markThreadRead) - drives the "Unread" tag below.
     *  This reflects only this app's own bookkeeping, not the phone's
     *  actual SMS read flag. */
    unreadAddresses: Set<String>,
    onApplyBulkAction: (sendReply: String?, block: Boolean, reportSpam: Boolean) -> Unit,
    /** Mr. George's manual Political/Commercial/No-label choices, keyed by
     *  normalized address (Round 6) - null/absent means "automatic." */
    labelOverrides: Map<String, String>,
    onSetLabelOverride: (address: String, override: String?) -> Unit,
    /** Marks every one of these addresses read in one tap (Round 6),
     *  without sending anything or moving them to S.R.B. Callers pass
     *  whichever addresses are currently visible, so this naturally
     *  respects the "Unread only" filter below. */
    onMarkAllRead: (List<String>) -> Unit
) {
    var showActionSheet by rememberSaveable { mutableStateOf(false) }
    var showUnreadOnly by rememberSaveable { mutableStateOf(false) }
    var showAboutDialog by rememberSaveable { mutableStateOf(false) }

    val visibleConversations = if (showUnreadOnly) {
        conversations.filter { unreadAddresses.contains(PhoneNumbers.normalize(it.address)) }
    } else {
        conversations
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bulk SMS Reply Log") },
                actions = {
                    TextButton(onClick = onSelectAll) { Text("Select all") }
                    TextButton(onClick = onSelectAllUnread) { Text("Select unread") }
                    TextButton(onClick = onClearSelection) { Text("Clear") }
                    IconButton(onClick = { showAboutDialog = true }) {
                        Icon(Icons.Filled.Info, contentDescription = "About")
                    }
                }
            )
        },
        bottomBar = {
            if (selectedAddresses.isNotEmpty()) {
                BottomAppBar {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${selectedAddresses.size} selected")
                        Button(onClick = { showActionSheet = true }) {
                            Icon(Icons.Filled.Send, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Bulk action")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = showUnreadOnly,
                    onClick = { showUnreadOnly = !showUnreadOnly },
                    label = { Text("Unread only") }
                )
                Spacer(Modifier.weight(1f))
                val anyUnreadVisible = visibleConversations.any {
                    unreadAddresses.contains(PhoneNumbers.normalize(it.address))
                }
                TextButton(
                    onClick = { onMarkAllRead(visibleConversations.map { it.address }) },
                    enabled = anyUnreadVisible
                ) {
                    Icon(Icons.Filled.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Mark all read")
                }
            }

            if (conversations.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Nothing here - either permission isn't granted yet, or " +
                            "everything's already been processed (check the S.R.B. tab)."
                    )
                }
            } else if (visibleConversations.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nothing unread - you're all caught up.")
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(visibleConversations, key = { it.address }) { conversation ->
                        ConversationRow(
                            conversation = conversation,
                            selected = selectedAddresses.contains(conversation.address),
                            unread = unreadAddresses.contains(PhoneNumbers.normalize(conversation.address)),
                            labelOverride = labelOverrides[PhoneNumbers.normalize(conversation.address)],
                            onToggle = { onToggleSelected(conversation) },
                            onSetLabelOverride = { override -> onSetLabelOverride(conversation.address, override) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showActionSheet) {
        BulkActionDialog(
            selectedCount = selectedAddresses.size,
            onDismiss = { showActionSheet = false },
            onConfirm = { sendReply, block, reportSpam ->
                showActionSheet = false
                onApplyBulkAction(sendReply, block, reportSpam)
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("Bulk SMS Reply Log - $APP_VERSION") },
            text = {
                // Round 6: show the full history, not just the current
                // version, so Mr. George can see what each past round added.
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    APP_VERSION_HISTORY.forEachIndexed { index, entry ->
                        Text(entry.version, fontWeight = FontWeight.Bold)
                        Text(entry.notes, style = MaterialTheme.typography.bodySmall)
                        if (index != APP_VERSION_HISTORY.lastIndex) {
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("OK") }
            }
        )
    }
}

@Composable
private fun ConversationRow(
    conversation: Conversation,
    selected: Boolean,
    unread: Boolean,
    /** Mr. George's manual label choice for this number, if he's set one
     *  (Round 6) - null means "automatic." */
    labelOverride: String?,
    onToggle: () -> Unit,
    onSetLabelOverride: (String?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = selected, onCheckedChange = { onToggle() })
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            // Round 5: "RE: [label]" - a last name or business name found
            // in the message's own disclosure, or a Political/Commercial
            // guess when it doesn't say one. Round 6: Mr. George's manual
            // override, if he's set one for this number, always wins over
            // all of that. Same logic used in the Log and Reports tabs
            // (AttributionExtractor.reLabel).
            val label = remember(conversation.lastMessageBody, conversation.address, labelOverride) {
                AttributionExtractor.reLabel(conversation.lastMessageBody, conversation.address, labelOverride)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                if (unread) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "UNREAD",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(4.dp))
                // Round 7: the tag icon + menu now lives in one shared
                // composable (LabelTag.kt) used by every tab that shows a
                // sender label, not just this one - see that file for why.
                LabelTagButton(
                    labelOverride = labelOverride,
                    onSetLabelOverride = onSetLabelOverride
                )
            }
            Text(
                conversation.address,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                conversation.lastMessageBody,
                maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                DateFormat.getDateInstance(DateFormat.SHORT)
                    .format(Date(conversation.lastMessageDateMillis)),
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                "${conversation.messageCount} msg${if (conversation.messageCount == 1) "" else "s"}",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun BulkActionDialog(
    selectedCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (sendReply: String?, block: Boolean, reportSpam: Boolean) -> Unit
) {
    var sendReplyEnabled by rememberSaveable { mutableStateOf(true) }
    var replyText by rememberSaveable { mutableStateOf("STOP") }
    var blockEnabled by rememberSaveable { mutableStateOf(true) }
    var reportSpamEnabled by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Apply to $selectedCount conversation${if (selectedCount == 1) "" else "s"}") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = sendReplyEnabled, onCheckedChange = { sendReplyEnabled = it })
                    Text("Send this reply to each:")
                }
                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    enabled = sendReplyEnabled,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(start = 40.dp, bottom = 8.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = blockEnabled, onCheckedChange = { blockEnabled = it })
                    Icon(Icons.Filled.Block, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text("Hide these numbers in this app going forward")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = reportSpamEnabled, onCheckedChange = { reportSpamEnabled = it })
                    Icon(Icons.Filled.Report, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text("Forward each last message to 7726 (spam report)")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "This will send real text messages from your phone number and may use your plan's messaging allotment.",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    if (sendReplyEnabled && replyText.isNotBlank()) replyText else null,
                    blockEnabled,
                    reportSpamEnabled
                )
            }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
