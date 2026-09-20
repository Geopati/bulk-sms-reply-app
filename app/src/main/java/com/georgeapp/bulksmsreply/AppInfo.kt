package com.georgeapp.bulksmsreply

/**
 * Shown in the Messages tab's "About" dialog.
 *
 * Versioning convention, decided 2026-09-19, RESCALED 2026-09-21: round N
 * of features shipped = version 1.N originally. Mr. George asked (Round 7)
 * that every number be multiplied by .1 (decimal point one place left) and
 * the same per-round increment continued from there, so the app reads as
 * pre-1.0/still-in-development for as long as he's still actively
 * correcting it - the old scheme had already implied a "1.x" (past-1.0,
 * "done") release from Round 1 onward, which wasn't the intent. Under the
 * new scheme, round N = version 0.1 + 0.01*N, so v1.6 (Round 6) becomes
 * v0.16, and it would take roughly 90 rounds at this pace to reach v1.00 -
 * effectively as much room as needed. He decides when to deliberately jump
 * to v1.0, not the numbering. Bump [APP_VERSION] and add a new entry at
 * the top of [APP_VERSION_HISTORY] each time a new round ships.
 *
 * Honest caveats:
 * - Rounds 1-4 didn't actually carry version numbers at the time they
 *   shipped (the installed app was "0.1.0" the whole way through those
 *   rounds). The v0.11-v0.14 entries below are a retroactive relabeling
 *   for a clean, readable history now that the convention exists - not a
 *   claim that those exact version numbers were literally on the phone
 *   back then.
 * - Rounds 1-6 were shown to Mr. George as v1.1 through v1.6 before this
 *   rescale. The v0.11-v0.16 numbers below are the SAME rounds, renamed -
 *   nothing about what those rounds did has changed, only the number.
 */
const val APP_VERSION = "v0.17"

/** One entry in the app's version history, newest first in
 *  [APP_VERSION_HISTORY]. */
data class VersionHistoryEntry(val version: String, val notes: String)

val APP_VERSION_HISTORY: List<VersionHistoryEntry> = listOf(
    VersionHistoryEntry(
        "v0.17",
        "The label tag icon now appears on EVERY tab that shows a sender label - " +
            "S.R.B., Log, and Reports, not just Messages - so a wrong automatic guess " +
            "can be fixed wherever you actually see it, including after a conversation " +
            "has already been processed. Also: version numbers rescaled (multiplied by " +
            ".1) so this reads as pre-1.0/still-in-development, per Mr. George's request - " +
            "see the note in AppInfo.kt for the full explanation."
    ),
    VersionHistoryEntry(
        "v0.16",
        "Manual Political/Commercial/No-label override for any number (remembered), " +
            "a \"Mark all read\" button, a wider Political/Commercial keyword list, " +
            "and this full version history. (Shown as v1.6 at the time; renumbered in v0.17.)"
    ),
    VersionHistoryEntry(
        "v0.15",
        "Stop/Report/Block (S.R.B.) tab, RE: sender labels, expandable report rows, " +
            "CSV export for the Log and Reports tabs. (Shown as v1.5 at the time.)"
    ),
    VersionHistoryEntry(
        "v0.14",
        "\"Unread only\" filter, \"Select unread\" shortcut, and an on-behalf-of " +
            "attribution label shown right on the Messages tab. (Shown as v1.4 at the time.)"
    ),
    VersionHistoryEntry(
        "v0.13",
        "App-local \"mark as read\" after a bulk action - clears the UNREAD tag " +
            "without touching your native Messages app (which Android reserves for " +
            "your phone's default texting app). (Shown as v1.3 at the time.)"
    ),
    VersionHistoryEntry(
        "v0.12",
        "A running, searchable log; day/week/month/quarter/half-year/year reports; " +
            "and a longer (3-line) message preview on the Messages tab. (Shown as v1.2 " +
            "at the time.)"
    ),
    VersionHistoryEntry(
        "v0.11",
        "Initial MVP: multi-select several conversations at once, then apply a bulk " +
            "reply (e.g. \"STOP\"), block locally, and/or report as spam to 7726. " +
            "(Shown as v1.1 at the time.)"
    )
)
