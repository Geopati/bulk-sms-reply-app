package com.georgeapp.bulksmsreply

/** One row of the running log: one incoming text message we've observed. */
data class MessageLogEntry(
    val id: Long,
    val smsDateMillis: Long,
    val address: String,
    val normalizedAddress: String,
    val attribution: String?,
    val body: String,
    val action: String?,
    val actionAtMillis: Long?,
    /** True once this app has marked the message "read" after a bulk
     *  action was applied to its conversation. This is local to the app
     *  only - it is not the phone's real SMS read flag, and your native
     *  Messages app does not see it (see MessageLogDatabase.markThreadRead
     *  for why). */
    val readLocally: Boolean = false
) {
    /** What to show as "who this was from" - the extracted attribution
     *  when we have one, otherwise a plain note plus the raw number. */
    val senderLabel: String
        get() = attribution?.let { "On behalf of $it" } ?: "Not stated ($address)"

    /** The key used to group this entry with others from the same
     *  real-world sender for the reports screen: prefer the attribution
     *  text (so the same campaign texting from many numbers still groups
     *  together), otherwise fall back to the phone number. */
    val senderGroupKey: String
        get() = attribution ?: normalizedAddress
}

/** A raw row read straight from the phone's SMS inbox, before any of our
 *  own bookkeeping (attribution, action taken, etc.) is attached. */
data class RawSmsMessage(
    val address: String,
    val body: String,
    val dateMillis: Long
)
