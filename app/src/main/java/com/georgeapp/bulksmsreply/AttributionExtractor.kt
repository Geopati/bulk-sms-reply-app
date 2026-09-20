package com.georgeapp.bulksmsreply

/**
 * Political texts are legally required to say who paid for/authorized
 * them, but they say it in all kinds of ways ("Paid for by...", "Pd for
 * by...", "on behalf of...", "Authorized by..."). This does a best-effort
 * scan of the message text for one of those phrases and pulls out the
 * name that follows it.
 *
 * On top of that raw extraction, [reLabel] turns whatever we found into
 * the short "RE: ..." label Mr. George asked for (Round 5), in priority
 * order:
 *   1. A last name (or two) found in the disclosure -> "RE: Smith"
 *   2. A business/committee name if no personal name found -> "RE: Acme Corp"
 *   3. Neither found -> a content-based guess: "RE: Political" or "RE: Commercial"
 *
 * This is intentionally simple pattern matching, not a legal or factual
 * verification of who actually sent something, and not machine learning -
 * it's best-effort, and will occasionally miss a name/business or guess
 * the wrong fallback category on an unusually worded or ambiguous text.
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

    // Two-capitalized-word sequences look like a person's name (e.g.
    // "John Smith"), but plenty of disclosure boilerplate also matches
    // that shape ("For Congress", "Political Action"...). Words in this
    // list never count as part of a person's name.
    private val NAME_STOPWORDS = setOf(
        "for", "congress", "senate", "committee", "campaign", "america",
        "united", "states", "the", "political", "action", "pac", "fund",
        "party", "house", "representatives", "governor", "mayor", "city",
        "county", "state", "national", "federal", "election", "vote",
        "re-elect", "reelect", "friends", "of", "and", "to", "on", "in",
        "by", "a", "an", "inc", "llc", "corp", "corporation", "coalition",
        "foundation", "association", "alliance", "group", "victory",
        "leadership", "future", "americans", "citizens", "keep"
    )

    private val BUSINESS_SUFFIXES = listOf(
        "Inc.", "Inc", "LLC", "Corp.", "Corp", "Corporation", "Committee",
        "PAC", "Fund", "Campaign", "Coalition", "Foundation", "Association",
        "Alliance"
    )
    private val BUSINESS_SUFFIX_PATTERN = Regex(
        "(?i)((?:[A-Z][\\w&'-]*\\s+){0,3}(?:" +
            BUSINESS_SUFFIXES.joinToString("|") { Regex.escape(it) } +
            "))"
    )

    private val PERSONAL_NAME_PATTERN =
        Regex("\\b([A-Z][a-zA-Z'-]{1,20})\\s+([A-Z][a-zA-Z'-]{1,20})\\b")

    private val POLITICAL_KEYWORDS = listOf(
        "vote", "election", "campaign", "candidate", "senator", "congress",
        "president", "poll", "ballot", "donate", "contribute", "pac",
        "committee", "governor", "mayor", "primary", "republican",
        "democrat", "party", "elect"
    )
    private val COMMERCIAL_KEYWORDS = listOf(
        "order", "sale", "discount", "% off", "coupon", "shop", "store",
        "offer", "deal", "subscription", "cart", "shipping", "delivery",
        "promo", "free trial", "unsubscribe", "code", "save"
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
     * The short "RE: ..." label used throughout the app (Messages, Log,
     * Reports). [rawAttribution] should be the result of [extract] on the
     * same message when already available (e.g. from a stored log row) -
     * pass null to have this look it up from [messageBody] itself.
     */
    fun reLabel(rawAttribution: String?, messageBody: String): String {
        val disclosure = rawAttribution ?: extract(messageBody)
        if (!disclosure.isNullOrBlank()) {
            lastNamesFrom(disclosure)?.let { return "RE: $it" }
            businessNameFrom(disclosure)?.let { return "RE: $it" }
        }
        return "RE: ${classifyPoliticalOrCommercial(messageBody)}"
    }

    /** Convenience overload when only the message body is on hand. */
    fun reLabel(messageBody: String): String = reLabel(extract(messageBody), messageBody)

    /** Tier 1: last name(s) found in the disclosure text, e.g. "Smith" or
     *  "Smith/Jones" for two. Null if nothing name-shaped was found. */
    private fun lastNamesFrom(disclosure: String): String? {
        val lastNames = LinkedHashSet<String>()
        for (match in PERSONAL_NAME_PATTERN.findAll(disclosure)) {
            val first = match.groupValues[1]
            val last = match.groupValues[2]
            if (first.lowercase() in NAME_STOPWORDS || last.lowercase() in NAME_STOPWORDS) continue
            lastNames.add(last)
            if (lastNames.size >= 2) break
        }
        return if (lastNames.isEmpty()) null else lastNames.joinToString("/")
    }

    /** Tier 2: a business/committee-shaped name in the disclosure text,
     *  e.g. "Acme Corp" or "Smith for Congress Committee". Null if no
     *  recognizable business suffix was found. */
    private fun businessNameFrom(disclosure: String): String? {
        val match = BUSINESS_SUFFIX_PATTERN.find(disclosure) ?: return null
        val name = match.groupValues[1].trim()
        return name.take(60).ifBlank { null }
    }

    /** Tier 3: no disclosure at all (or nothing name/business-shaped in
     *  it) - guess Political vs Commercial from the message's own
     *  wording. Defaults to Commercial when the guess is a toss-up, since
     *  compliant political texts almost always carry a disclosure that
     *  would have already been caught above. */
    private fun classifyPoliticalOrCommercial(messageBody: String): String {
        val lower = messageBody.lowercase()
        val politicalScore = POLITICAL_KEYWORDS.count { lower.contains(it) }
        val commercialScore = COMMERCIAL_KEYWORDS.count { lower.contains(it) }
        return if (politicalScore > commercialScore) "Political" else "Commercial"
    }
}
