package com.georgeapp.bulksmsreply

/**
 * Shown in the Messages tab's "About" dialog. Per Mr. George's versioning
 * convention, decided 2026-09-19: round N of features shipped = version
 * 1.N. Bump [APP_VERSION] and add a new entry at the top of
 * [APP_VERSION_HISTORY] each time a new round ships (Round 6 made this
 * history retroactive, per his request).
 *
 * Honest caveat: rounds 1-4 didn't actually carry version numbers at the
 * time they shipped (the installed app was "0.1.0" the whole way through
 * those rounds). The v1.1-v1.4 entries below are a retroactive relabeling
 * for a clean, readable history now that the convention exists - not a
 * claim that those exact version numbers were literally on the phone
 * back then.
 */
const val APP_VERSION = "v1.6"

/** One entry in the app's version history, newest first in
 *  [APP_VERSION_HISTORY]. */
data class VersionHistoryEntry(val version: String, val notes: String)

val APP_VERSION_HISTORY: List<VersionHistoryEntry> = listOf(
    VersionHistoryEntry(
        "v1.6",
        "Manual Political/Commercial/No-label override for any number (remembered), " +
            "a \"Mark all read\" button, a wider Political/Commercial keyword list, " +
            "and this full version history."
    ),
    VersionHistoryEntry(
        "v1.5",
        "Stop/Report/Block (S.R.B.) tab, RE: sender labels, expandable report rows, " +
            "CSV export for the Log and Reports tabs."
    ),
    VersionHistoryEntry(
        "v1.4",
        "\"Unread only\" filter, \"Select unread\" shortcut, and an on-behalf-of " +
            "attribution label shown right on the Messages tab."
    ),
    VersionHistoryEntry(
        "v1.3",
        "App-local \"mark as read\" after a bulk action - clears the UNREAD tag " +
            "without touching your native Messages app (which Android reserves for " +
            "your phone's default texting app)."
    ),
    VersionHistoryEntry(
        "v1.2",
        "A running, searchable log; day/week/month/quarter/half-year/year reports; " +
            "and a longer (3-line) message preview on the Messages tab."
    ),
    VersionHistoryEntry(
        "v1.1",
        "Initial MVP: multi-select several conversations at once, then apply a bulk " +
            "reply (e.g. \"STOP\"), block locally, and/or report as spam to 7726."
    )
)
