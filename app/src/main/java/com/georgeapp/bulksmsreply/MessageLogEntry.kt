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
    val readLocally: Boolean = false,
    /** Mr. George's manual Political/Commercial/No-label choice for this
     *  sender's number, if he's set one (Round 6) - one of
     *  AttributionExtractor.OVERRIDE_POLITICAL/OVERRIDE_COMMERCIAL/
     *  OVERRIDE_NONE, or null for "automatic." Looked up by normalized
     *  address when this entry is loaded from the database. */
    val labelOverride: String? = null
) {
    /** What to show as "who this was from": the "RE: ..." label (Round 5) -
     *  a last name or business name found in the message's own disclosure,
     *  a Political/Commercial guess when it doesn't say, or Mr. George's
     *  own manual override (Round 6) when he's set one. See
     *  AttributionExtractor.reLabel for the priority order. */
    val senderLabel: String
        get() = AttributionExtractor.reLabel(attribution, body, address, labelOverride)

    /** The key used to group this entry with others from the same
     *  real-world sender for the reports screen. Grouping by the RE: label
     *  itself (rather than the raw disclosure text) is intentional: it's
     *  what lets two differently-worded disclosures for the same person
     *  ("John Smith for Congress" / "Re-Elect Smith") collapse into one
     *  "RE: Smith" bucket. */
    val senderGroupKey: String
        get() = senderLabel
}

/** A raw row read straight from the phone's SMS inbox, before any of our
 *  own bookkeeping (attribution, action taken, etc.) is attached. */
data class RawSmsMessage(
    val address: String,
    val body: String,
    val dateMillis: Long
)
