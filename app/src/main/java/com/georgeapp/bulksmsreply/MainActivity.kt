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
import androidx.compose.material.icons.filled.Block
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
import com.georgeapp.bulksmsreply.ui.SrbScreen
import com.georgeapp.bulksmsreply.ui.rangeDaysToSinceMillis

private enum class AppTab(val label: String) {
    MESSAGES("Messages"),
    SRB("S.R.B."),
    LOG("Log"),
    REPORTS("Reports")
}

class MainActivity : ComponentActivity() {

    private lateinit var blocklistStore: BlocklistStore
    private lateinit var messageLogDatabase: MessageLogDatabase
    private lateinit var appSettings: AppSettings

    private val requiredPermissions = arrayOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        blocklistStore = BlocklistStore(this)
        messageLogDatabase = MessageLogDatabase(this)
        appSettings = AppSettings(this)

        setContent {
            BulkTextReplyTheme {
                AppRoot(
                    blocklistStore = blocklistStore,
                    messageLogDatabase = messageLogDatabase,
                    appSettings = appSettings,
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
    appSettings: AppSettings,
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
    // Round 5: conversations whose latest message already had a bulk
    // action applied - shown on the S.R.B. tab instead of Messages, so
    // they can't be accidentally selected and processed a second time.
    var processedConversations by remember {
        mutableStateOf<List<MessageLogDatabase.ProcessedConversation>>(emptyList())
    }
    // Round 6: Mr. George's manual label choices (Personal/Other added
    // Round 8), normalized address -> override value. Loaded alongside
    // everything else below and re-loaded on every refreshTrigger bump.
    var labelOverrides by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    // Round 8: Mr. George's app-wide opt-out toggle for the automatic
    // Political/Commercial guess only - see AppSettings and
    // AttributionExtractor.reLabel's guessingEnabled parameter. Read once
    // at startup from persisted storage; kept in sync with it below.
    var guessingEnabled by remember { mutableStateOf(appSettings.automaticGuessingEnabled) }

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
    LaunchedEffect(permissionsGranted, refreshTrigger, guessingEnabled) {
        if (permissionsGranted) {
            val rawMessages = SmsRepository.loadAllRawMessages(context)
            messageLogDatabase.recordIncoming(rawMessages)

            // Round 5: figure out which conversations are already
            // processed (S.R.B. tab) BEFORE building the Messages list,
            // so processed ones can be filtered out of Messages entirely.
            val processed = messageLogDatabase.processedConversations(guessingEnabled)
            processedConversations = processed
            val processedAddresses = processed.map { it.normalizedAddress }.toSet()

            val loaded = SmsRepository.loadConversations(context)
            val blocked = blocklistStore.getBlockedNumbers()
            allConversations = loaded.filterNot { conversation ->
                val normalized = PhoneNumbers.normalize(conversation.address)
                blocked.contains(normalized) || processedAddresses.contains(normalized)
            }
            selectedAddresses = emptySet()
            unreadNormalizedAddresses = messageLogDatabase.unreadNormalizedAddresses()
            labelOverrides = messageLogDatabase.labelOverrides()
        }
    }

    // Re-query the log whenever its own search/range filters change, a
    // bulk action just happened, or the user switches to that tab.
    LaunchedEffect(logSearchText, logRangeDays, refreshTrigger, selectedTab, permissionsGranted, guessingEnabled) {
        if (permissionsGranted && selectedTab == AppTab.LOG) {
            logEntries = messageLogDatabase.queryLog(
                searchText = logSearchText,
                sinceMillis = rangeDaysToSinceMillis(logRangeDays),
                guessingEnabled = guessingEnabled
            )
        }
    }

    // Rebuild the day/week/month/etc. report whenever the chosen period,
    // the underlying data, or the active tab changes.
    LaunchedEffect(reportPeriod, refreshTrigger, selectedTab, permissionsGranted, guessingEnabled) {
        if (permissionsGranted && selectedTab == AppTab.REPORTS) {
            val allEntries = messageLogDatabase.allForReports(sinceMillis = null, guessingEnabled = guessingEnabled)
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

    // Round 7: one shared handler for the label-override tag icon, now
    // used by every tab (Messages/S.R.B./Log/Reports) instead of only
    // Messages - see ui/LabelTag.kt for why that mattered. Defined once
    // here so all four call sites below stay wired to the exact same
    // logic and can't drift apart from each other.
    val setLabelOverride: (String, String?) -> Unit = { address, override ->
        messageLogDatabase.setLabelOverride(address, override)
        refreshTrigger += 1
    }

    // Round 8: flips the persisted automatic-guessing setting and updates
    // the in-memory copy together, so every screen re-reads it on the same
    // refresh (see the LaunchedEffect blocks above, all keyed on
    // guessingEnabled).
    val setGuessingEnabled: (Boolean) -> Unit = { enabled ->
        appSettings.automaticGuessingEnabled = enabled
        guessingEnabled = enabled
        refreshTrigger += 1
    }

    // Round 8: bulk relabel for the Messages tab - applies one override to
    // every currently-selected conversation in one pass, reusing the same
    // per-number setLabelOverride under the hood so it stays retroactive
    // and consistent with the single-row tag icon everywhere else.
    val applyBulkLabelOverride: (String?) -> Unit = { override ->
        val targetedAddresses = allConversations
            .filter { selectedAddresses.contains(it.address) }
            .map { it.address }
        targetedAddresses.forEach { address -> messageLogDatabase.setLabelOverride(address, override) }
        val optionLabel = AttributionExtractor.OVERRIDE_OPTIONS
            .firstOrNull { it.second == override }?.first ?: "Automatic"
        snackbarMessage = "Set \"$optionLabel\" on ${targetedAddresses.size} conversation" +
            "${if (targetedAddresses.size == 1) "" else "s"}."
        refreshTrigger += 1
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
                        selected = selectedTab == AppTab.SRB,
                        onClick = { selectedTab = AppTab.SRB },
                        icon = { Icon(Icons.Filled.Block, contentDescription = null) },
                        label = { Text(AppTab.SRB.label) }
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
                        },
                        labelOverrides = labelOverrides,
                        onSetLabelOverride = setLabelOverride,
                        onMarkAllRead = { addresses ->
                            messageLogDatabase.markAllRead(addresses, System.currentTimeMillis())
                            snackbarMessage = "Marked ${addresses.size} conversation${if (addresses.size == 1) "" else "s"} as read."
                            refreshTrigger += 1
                        },
                        onApplyBulkLabelOverride = applyBulkLabelOverride,
                        guessingEnabled = guessingEnabled,
                        onSetGuessingEnabled = setGuessingEnabled
                    )

                    AppTab.SRB -> SrbScreen(
                        processed = processedConversations,
                        onSetLabelOverride = setLabelOverride
                    )

                    AppTab.LOG -> LogScreen(
                        entries = logEntries,
                        searchText = logSearchText,
                        onSearchTextChange = { logSearchText = it },
                        selectedRangeDays = logRangeDays,
                        onRangeSelected = { logRangeDays = it },
                        onSetLabelOverride = setLabelOverride
                    )

                    AppTab.REPORTS -> ReportsScreen(
                        selectedPeriod = reportPeriod,
                        onPeriodSelected = { reportPeriod = it },
                        buckets = reportBuckets,
                        onSetLabelOverride = setLabelOverride
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
