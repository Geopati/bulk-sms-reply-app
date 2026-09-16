@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.georgeapp.bulksmsreply.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.georgeapp.bulksmsreply.Conversation
import java.text.DateFormat
import java.util.Date

@Composable
fun ConversationListScreen(
    conversations: List<Conversation>,
    selectedAddresses: Set<String>,
    onToggleSelected: (Conversation) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onApplyBulkAction: (sendReply: String?, block: Boolean, reportSpam: Boolean) -> Unit
) {
    var showActionSheet by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bulk Text Reply") },
                actions = {
                    TextButton(onClick = onSelectAll) { Text("Select all") }
                    TextButton(onClick = onClearSelection) { Text("Clear") }
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
        if (conversations.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No text conversations found (or permission not yet granted).")
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(conversations, key = { it.address }) { conversation ->
                    ConversationRow(
                        conversation = conversation,
                        selected = selectedAddresses.contains(conversation.address),
                        onToggle = { onToggleSelected(conversation) }
                    )
                    HorizontalDivider()
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
}

@Composable
private fun ConversationRow(
    conversation: Conversation,
    selected: Boolean,
    onToggle: () -> Unit
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
            Text(conversation.address, fontWeight = FontWeight.Bold)
            Text(
                conversation.lastMessageBody,
                maxLines = 1,
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
