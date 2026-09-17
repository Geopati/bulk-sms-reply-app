package com.georgeapp.bulksmsreply

/**
 * Political texts are legally required to say who paid for/authorized
 * them, but they say it in all kinds of ways ("Paid for by...", "Pd for
 * by...", "on behalf of...", "Authorized by..."). This does a best-effort
 * scan of the message text for one of those phrases and pulls out the
 * name that follows it, so the log/reports can show "sent on behalf of
 * X" instead of just a bare phone number when possible.
 *
 * This is intentionally simple pattern matching, not a legal or factual
 * verification of who actually sent something - it just surfaces what
 * the message itself claims.
 */
object AttributionExtractor {

    // Ordered by how strongly they indicate a real disclosure - first
    // match wins. Each pattern captures the name/organization that follows
    // the trigger phrase, stopping at a period, newline, or the end of
    // the text.
    private val PATTERNS = listOf(
        Regex("(?i)paid\\s+for\\s+by\\s+([^.\\n]{1,100})"),
        Regex("(?i)pd\\.?\\s+for\\s+by\\s+([^.\\n]{1,100})"),
        Regex("(?i)authorized\\s+by\\s+([^.\\n]{1,100})"),
        Regex("(?i)sponsored\\s+by\\s+([^.\\n]{1,100})"),
        Regex("(?i)on\\s+behalf\\s+of\\s+([^.\\n]{1,100})"),
    )

    /**
     * Returns the extracted attribution text (trimmed, length-capped), or
     * null if the message doesn't contain any recognizable disclosure.
     */
    fun extract(messageBody: String): String? {
        for (pattern in PATTERNS) {
            val match = pattern.find(messageBody) ?: continue
            val raw = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (raw.isNotEmpty()) {
                return raw.take(80).trim().trimEnd(',', ';', ':', '-')
            }
        }
        return null
    }

    /**
     * A short label for display: the attribution if we found one,
     * otherwise a plain note that the message itself didn't say.
     */
    fun displayLabel(messageBody: String, address: String): String {
        val attribution = extract(messageBody)
        return if (attribution != null) {
            "On behalf of $attribution"
        } else {
            "Sender not stated in message ($address)"
        }
    }
}
