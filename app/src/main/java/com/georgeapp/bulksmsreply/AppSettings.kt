package com.georgeapp.bulksmsreply

import android.content.Context

/**
 * Small app-wide on/off settings - separate from the per-number manual
 * label overrides (the `label_override` table in MessageLogDatabase) and
 * the app-local blocklist (BlocklistStore). Round 8 adds the first one.
 *
 * [automaticGuessingEnabled] controls only tier 3 of
 * AttributionExtractor.reLabel - the content-based Political/Commercial
 * guess used when a message has no recognizable disclosure name or
 * business. Turning it off does NOT:
 *   - remove or hide any label a number already has, automatic or manual
 *   - stop tier 1/2 (name/business found in a message's own disclosure
 *     line) from labeling a message
 *   - touch the tag icon or override menu, which stay available either way
 * It only stops NEW/unlabeled numbers from getting a guessed Political/
 * Commercial tag going forward - they show the raw phone number instead
 * (same as manually choosing "No label") until a real disclosure name
 * turns up or Mr. George sets a manual override himself.
 *
 * This was built as a fully reversible toggle by design (2026-09-21
 * design dialogue, confirmed before building): flipping it back on
 * immediately resumes guessing, exactly as easily as turning it off.
 */
class AppSettings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    var automaticGuessingEnabled: Boolean
        get() = prefs.getBoolean(KEY_GUESSING_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_GUESSING_ENABLED, value).apply()

    private companion object {
        const val KEY_GUESSING_ENABLED = "automatic_guessing_enabled"
    }
}
