package com.georgeapp.bulksmsreply

import android.content.Context

/**
 * A simple, app-local list of numbers Mr. George has chosen to block.
 *
 * IMPORTANT LIMITATION: because this app is not (yet) the phone's default
 * SMS handler, Android will not let it add entries to the *system*
 * BlockedNumberContract - only the default SMS or Phone app can do that.
 * So "blocking" here means: this app will stop showing that number in its
 * own list. It does NOT stop the carrier from delivering the text, and it
 * will still show up in the phone's regular Messages app. See README.md
 * for what a true default-SMS-app version of this would change.
 */
class BlocklistStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("blocklist_prefs", Context.MODE_PRIVATE)

    fun getBlockedNumbers(): Set<String> =
        prefs.getStringSet(KEY_BLOCKED, emptySet())?.toSet() ?: emptySet()

    fun isBlocked(rawNumber: String): Boolean {
        val normalized = PhoneNumbers.normalize(rawNumber)
        if (normalized.isEmpty()) return false
        return getBlockedNumbers().contains(normalized)
    }

    fun blockAll(rawNumbers: Collection<String>) {
        val updated = getBlockedNumbers().toMutableSet()
        rawNumbers.forEach { number ->
            val normalized = PhoneNumbers.normalize(number)
            if (normalized.isNotEmpty()) updated.add(normalized)
        }
        prefs.edit().putStringSet(KEY_BLOCKED, updated).apply()
    }

    fun unblock(rawNumber: String) {
        val normalized = PhoneNumbers.normalize(rawNumber)
        val updated = getBlockedNumbers().toMutableSet()
        updated.remove(normalized)
        prefs.edit().putStringSet(KEY_BLOCKED, updated).apply()
    }

    private companion object {
        const val KEY_BLOCKED = "blocked_numbers"
    }
}
