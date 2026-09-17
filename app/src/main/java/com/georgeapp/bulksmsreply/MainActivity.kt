package com.georgeapp.bulksmsreply

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.georgeapp.bulksmsreply.ui.BulkTextReplyTheme
import com.georgeapp.bulksmsreply.ui.ConversationListScreen
import com.georgeapp.bulksmsreply.ui.LogScreen
import com.georgeapp.bulksmsreply.ui.ReportsScreen
import com.georgeapp.bulksmsreply.ui.rangeDaysToSinceMillis

private enum class AppTab(val label: String) {
    MESSAGES("Messages"),
    LOG("Log"),
    REPORTS("Reports")
}

class MainActivity : ComponentActivity() {

    private lateinit var blocklistStore: BlocklistStore
    private lateinit var messageLogDatabase: MessageLogDatabase

    private val requiredPermissions = arrayOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        blocklistStore = BlocklistStore(this)
        messageLogDatabase = MessageLogDatabase(this)

        setContent {
            BulkTextReplyTheme {
                AppRoot(
                    blocklistStore = blocklistStore,
                    messageLogDatabase = messageLogDatabase,
                    requiredPermissions = requiredPermissions
                )
            }
        }
    }
}

@Composable
private fun AppRoot(
    blocklistStore: BlocklistStore,
    messageLogDatabase: MessageLogDatabase,
    requiredPermissions: Array<String>
) {
    val context = LocalContext.current

    var permissionsGranted by remember {
        mutableStateOf(hasPermissions(context, requiredPermissions))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
    }

    var selectedTab by remember { mutableStateOf(AppTab.MESSAGES) }

    var allConversations by remember { mutableStateOf<List<Conversation>>(emptyList()) }
    var selectedAddresses by remember { mutableStateOf<Set<String>>(emptySet()) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }
    var unreadNormalizedAddresses by remember { mutableStateOf<Set<String>>(emptySet()) }

    var logSearchText by remember { mutableStateOf("") }
    var logRangeDays by remember { mutableStateOf<Int?>(30) }
    var logEntries by remember { mutableStateOf<List<MessageLogEntry>>(emptyList()) }

    var reportPeriod by remember { mutableStateOf(ReportPeriod.DAY) }
    var reportBuckets by remember { mutableStateOf<List<PeriodBucket>>(emptyList()) }

    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    // Reload the conversation list AND add any newly-seen messages to the
    // running log whenever permission is granted or a bulk action changes
    // things (refreshTrigger).
    LaunchedEffect(permissionsGranted, refreshTrigger) {
        if (permissionsGranted) {
            val rawMessages = SmsRepository.loadAllRawMessages(context)
            messageLogDatabase.recordIncoming(rawMessages)

            val loaded = SmsRepository.loadConversations(context)
            val blocked = blocklistStore.getBlockedNumbers()
            allConversations = loaded.filterNot { conversation ->
                blocked.contains(PhoneNumbers.normalize(conversation.address))
            }
            selectedAddresses = emptySet()
            unreadNormalizedAddresses = messageLogDatabase.unreadNormalizedAddresses()
        }
    }

    // Re-query the log whenever its own search/range filters change, a
    // bulk action just happened, or the user switches to that tab.
    LaunchedEffect(logSearchText, logRangeDays, refreshTrigger, selectedTab, permissionsGranted) {
        if (permissionsGranted && selectedTab == AppTab.LOG) {
            logEntries = messageLogDatabase.queryLog(
                searchText = logSearchText,
                sinceMillis = rangeDaysToSinceMillis(logRangeDays)
            )
        }
    }

    // Rebuild the day/week/month/etc. report whenever the chosen period,
    // the underlying data, or the active tab changes.
    LaunchedEffect(reportPeriod, refreshTrigger, selectedTab, permissionsGranted) {
        if (permissionsGranted && selectedTab == AppTab.REPORTS) {
            val allEntries = messageLogDatabase.allForReports(sinceMillis = null)
            reportBuckets = ReportGenerator.build(allEntries, reportPeriod)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            snackbarMessage = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (permissionsGranted) {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == AppTab.MESSAGES,
                        onClick = { selectedTab = AppTab.MESSAGES },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = { Text(AppTab.MESSAGES.label) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == AppTab.LOG,
                        onClick = { selectedTab = AppTab.LOG },
                        icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        label = { Text(AppTab.LOG.label) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == AppTab.REPORTS,
                        onClick = { selectedTab = AppTab.REPORTS },
                        icon = { Icon(Icons.Filled.BarChart, contentDescription = null) },
                        label = { Text(AppTab.REPORTS.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (!permissionsGranted) {
                PermissionRationale(onRequestPermissions = {
                    permissionLauncher.launch(requiredPermissions)
                })
            } else {
                when (selectedTab) {
                    AppTab.MESSAGES -> ConversationListScreen(
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
                        onSelectAllUnread = {
                            selectedAddresses = allConversations
                                .filter { unreadNormalizedAddresses.contains(PhoneNumbers.normalize(it.address)) }
                                .map { it.address }
                                .toSet()
                        },
                        onClearSelection = { selectedAddresses = emptySet() },
                        unreadAddresses = unreadNormalizedAddresses,
                        onApplyBulkAction = { sendReply, block, reportSpam ->
                            val targeted = allConversations.filter { selectedAddresses.contains(it.address) }
                            val now = System.currentTimeMillis()
                            val anyActionApplied = sendReply != null || reportSpam || block

                            if (sendReply != null) {
                                SendHelper.sendToAll(targeted.map { it.address }, sendReply)
                                targeted.forEach { messageLogDatabase.recordAction(it.address, "REPLIED", now) }
                            }
                            if (reportSpam) {
                                targeted.forEach { conversation ->
                                    SendHelper.reportAsSpam(conversation.lastMessageBody)
                                    messageLogDatabase.recordAction(conversation.address, "REPORTED_SPAM", now)
                                }
                            }
                            if (block) {
                                blocklistStore.blockAll(targeted.map { it.address })
                                targeted.forEach { messageLogDatabase.recordAction(it.address, "BLOCKED", now) }
                            }
                            // Mr. George asked that processing a text mark it "read."
                            // We can only do that inside this app's own log - Android
                            // reserves writing to the phone's shared SMS inbox (the
                            // one your native Messages app reads) for whichever app is
                            // set as the phone's default texting app, which this one
                            // deliberately isn't (see README/project notes).
                            if (anyActionApplied) {
                                targeted.forEach { messageLogDatabase.markThreadRead(it.address, now) }
                            }

                            snackbarMessage = buildSummary(targeted.size, sendReply != null, block, reportSpam)
                            refreshTrigger += 1
                        }
                    )

                    AppTab.LOG -> LogScreen(
                        entries = logEntries,
                        searchText = logSearchText,
                        onSearchTextChange = { logSearchText = it },
                        selectedRangeDays = logRangeDays,
                        onRangeSelected = { logRangeDays = it }
                    )

                    AppTab.REPORTS -> ReportsScreen(
                        selectedPeriod = reportPeriod,
                        onPeriodSelected = { reportPeriod = it },
                        buckets = reportBuckets
                    )
                }
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
