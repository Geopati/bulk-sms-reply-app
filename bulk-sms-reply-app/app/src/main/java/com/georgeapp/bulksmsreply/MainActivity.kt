package com.georgeapp.bulksmsreply

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.georgeapp.bulksmsreply.ui.BulkTextReplyTheme
import com.georgeapp.bulksmsreply.ui.ConversationListScreen

class MainActivity : ComponentActivity() {

    private lateinit var blocklistStore: BlocklistStore

    private val requiredPermissions = arrayOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        blocklistStore = BlocklistStore(this)

        setContent {
            BulkTextReplyTheme {
                AppRoot(blocklistStore = blocklistStore, requiredPermissions = requiredPermissions)
            }
        }
    }
}

@Composable
private fun AppRoot(blocklistStore: BlocklistStore, requiredPermissions: Array<String>) {
    val context = LocalContext.current

    var permissionsGranted by remember {
        mutableStateOf(hasPermissions(context, requiredPermissions))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
    }

    var allConversations by remember { mutableStateOf<List<Conversation>>(emptyList()) }
    var selectedAddresses by remember { mutableStateOf<Set<String>>(emptySet()) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    LaunchedEffect(permissionsGranted, refreshTrigger) {
        if (permissionsGranted) {
            val loaded = SmsRepository.loadConversations(context)
            val blocked = blocklistStore.getBlockedNumbers()
            allConversations = loaded.filterNot { conversation ->
                blocked.contains(PhoneNumbers.normalize(conversation.address))
            }
            selectedAddresses = emptySet()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            snackbarMessage = null
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (!permissionsGranted) {
                PermissionRationale(onRequestPermissions = {
                    permissionLauncher.launch(requiredPermissions)
                })
            } else {
                ConversationListScreen(
                    conversations = allConversations,
                    selectedAddresses = selectedAddresses,
                    onToggleSelected = { conversation ->
                        selectedAddresses = if (selectedAddresses.contains(conversation.address)) {
                            selectedAddresses - conversation.address
                        } else {
                            selectedAddresses + conversation.address
                        }
                    },
                    onSelectAll = {
                        selectedAddresses = allConversations.map { it.address }.toSet()
                    },
                    onClearSelection = { selectedAddresses = emptySet() },
                    onApplyBulkAction = { sendReply, block, reportSpam ->
                        val targeted = allConversations.filter { selectedAddresses.contains(it.address) }

                        if (sendReply != null) {
                            SendHelper.sendToAll(targeted.map { it.address }, sendReply)
                        }
                        if (reportSpam) {
                            targeted.forEach { conversation ->
                                SendHelper.reportAsSpam(conversation.lastMessageBody)
                            }
                        }
                        if (block) {
                            blocklistStore.blockAll(targeted.map { it.address })
                        }

                        snackbarMessage = buildSummary(targeted.size, sendReply != null, block, reportSpam)
                        refreshTrigger += 1
                    }
                )
            }
        }
    }
}

@Composable
private fun PermissionRationale(onRequestPermissions: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "This app needs permission to read your text messages and send " +
                "replies on your behalf. Nothing is sent anywhere except the " +
                "SMS replies you choose to trigger.",
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRequestPermissions) { Text("Grant permissions") }
    }
}

private fun buildSummary(count: Int, replied: Boolean, blocked: Boolean, reported: Boolean): String {
    val actions = listOfNotNull(
        "replied to".takeIf { replied },
        "blocked".takeIf { blocked },
        "reported".takeIf { reported }
    )
    return if (actions.isEmpty()) {
        "No action selected."
    } else {
        "Done: ${actions.joinToString(", ")} $count conversation${if (count == 1) "" else "s"}."
    }
}

private fun hasPermissions(context: android.content.Context, permissions: Array<String>): Boolean =
    permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
