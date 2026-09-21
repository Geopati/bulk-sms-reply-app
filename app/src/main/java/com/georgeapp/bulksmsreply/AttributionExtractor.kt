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
 *      (Round 8: skipped when [guessingEnabled] is false - see that
 *      parameter's note)
 *   4. A manual override (Round 6: Political/Commercial/No label; Round 8
 *      added Personal/Other), if Mr. George has set one for this number -
 *      always wins over all three tiers above, since at that point he's
 *      deliberately taking control of the label himself.
 *
 * This is intentionally simple pattern matching, not a legal or factual
 * verification of who actually sent something, and not machine learning -
 * it's best-effort, and will occasionally miss a name/business or guess
 * the wrong fallback category on an unusually worded or ambiguous text.
 * The manual override (Round 6) exists specifically so Mr. George can
 * correct a wrong Political/Commercial guess himself, permanently, for a
 * given number.
 */
object AttributionExtractor {

    /** The values a manual label override can hold, stored as-is in the
     *  database. Absence of a stored value (null) means "automatic" - use
     *  the tiered detection below, same as before Round 6. Personal and
     *  Other were added in Round 8 at Mr. George's request, for contacts
     *  the automatic Political/Commercial guess was never meant to catch. */
    const val OVERRIDE_POLITICAL = "POLITICAL"
    const val OVERRIDE_COMMERCIAL = "COMMERCIAL"
    const val OVERRIDE_PERSONAL = "PERSONAL"
    const val OVERRIDE_OTHER = "OTHER"
    const val OVERRIDE_NONE = "NONE"

    /** Options shown in the manual-override menu, in display order,
     *  paired with the value [reLabel] expects (null = "Automatic"). */
    val OVERRIDE_OPTIONS: List<Pair<String, String?>> = listOf(
        "Automatic" to null,
        "Political" to OVERRIDE_POLITICAL,
        "Commercial" to OVERRIDE_COMMERCIAL,
        "Personal" to OVERRIDE_PERSONAL,
        "Other" to OVERRIDE_OTHER,
        "No label" to OVERRIDE_NONE
    )

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

    // Expanded in Round 6 (Mr. George found the original lists too easily
    // misclassified messages) - still a plain keyword count, just a wider
    // net on both sides. The manual override below is the real fix for
    // any individual number this still gets wrong.
    private val POLITICAL_KEYWORDS = listOf(
        "vote", "election", "campaign", "candidate", "senator", "congress",
        "president", "poll", "ballot", "donate", "contribute", "pac",
        "committee", "governor", "mayor", "primary", "republican",
        "democrat", "party", "elect", "voters", "grassroots", "endorse",
        "endorsed", "constituents", "legislature", "legislation",
        "district", "assembly", "gop", "chip in", "matched 3x",
        "fellow american", "flip the"
    )
    // Round 8: removed "reply stop"/"text stop" - that's shared opt-out
    // boilerplate required on almost any bulk text (political ones
    // included), not evidence of a commercial message. Leaving them in
    // was quietly dragging political texts toward a false Commercial
    // guess whenever the disclosure line itself wasn't recognized.
    private val COMMERCIAL_KEYWORDS = listOf(
        "order", "sale", "discount", "% off", "coupon", "shop", "store",
        "offer", "deal", "subscription", "cart", "shipping", "delivery",
        "promo", "free trial", "unsubscribe", "code", "save",
        "limited time", "exclusive", "rewards", "clearance",
        "flash sale", "checkout", "your order", "tracking", "confirm your",
        "gift card", "new arrivals", "member price"
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
     *
     * [address] is shown as-is when [override] is [OVERRIDE_NONE] ("no
     * label" - Round 6). [override] is one of [OVERRIDE_POLITICAL],
     * [OVERRIDE_COMMERCIAL], [OVERRIDE_PERSONAL], [OVERRIDE_OTHER],
     * [OVERRIDE_NONE] (Round 8 added the middle two), or null for
     * "automatic" (the tiered detection below, same as before Round 6) -
     * when set, it wins over everything else, including a real
     * name/business we did find.
     *
     * [guessingEnabled] (Round 8, default true) is Mr. George's app-wide
     * opt-out toggle for tier 3 (the content-based Political/Commercial
     * guess) only - see AppSettings.automaticGuessingEnabled. When false
     * and neither a manual override nor a real name/business was found,
     * this falls back to showing [address] (same as "No label") instead
     * of guessing a category, since he specifically asked to be able to
     * turn guessing off without losing any label a number already has.
     */
    fun reLabel(
        rawAttribution: String?,
        messageBody: String,
        address: String,
        override: String? = null,
        guessingEnabled: Boolean = true
    ): String {
        when (override) {
            OVERRIDE_POLITICAL -> return "RE: Political"
            OVERRIDE_COMMERCIAL -> return "RE: Commercial"
            OVERRIDE_PERSONAL -> return "RE: Personal"
            OVERRIDE_OTHER -> return "RE: Other"
            OVERRIDE_NONE -> return address
        }
        val disclosure = rawAttribution ?: extract(messageBody)
        if (!disclosure.isNullOrBlank()) {
            lastNamesFrom(disclosure)?.let { return "RE: $it" }
            businessNameFrom(disclosure)?.let { return "RE: $it" }
        }
        if (!guessingEnabled) return address
        return "RE: ${classifyPoliticalOrCommercial(messageBody)}"
    }

    /** Convenience overload when only the message body is on hand (no
     *  already-extracted disclosure to reuse). */
    fun reLabel(
        messageBody: String,
        address: String,
        override: String? = null,
        guessingEnabled: Boolean = true
    ): String =
        reLabel(extract(messageBody), messageBody, address, override, guessingEnabled)

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
     *  wording. Round 8: defaults to Political on a toss-up (flipped from
     *  Commercial, per Mr. George's 2026-09-21 request) - most of the
     *  volume he was seeing mislabeled was political spam without a
     *  recognizable disclosure line, not commercial spam. */
    private fun classifyPoliticalOrCommercial(messageBody: String): String {
        val lower = messageBody.lowercase()
        val politicalScore = POLITICAL_KEYWORDS.count { lower.contains(it) }
        val commercialScore = COMMERCIAL_KEYWORDS.count { lower.contains(it) }
        return if (commercialScore > politicalScore) "Commercial" else "Political"
    }
}
