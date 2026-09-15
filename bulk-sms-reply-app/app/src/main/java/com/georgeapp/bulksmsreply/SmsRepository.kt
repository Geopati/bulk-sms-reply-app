package com.georgeapp.bulksmsreply

import android.content.Context
import android.provider.Telephony

/**
 * Reads the phone's existing SMS inbox (via the standard content provider)
 * and collapses it into one row per sender/address, newest message first.
 *
 * Requires the READ_SMS runtime permission to already be granted - callers
 * should check that before invoking this.
 */
object SmsRepository {

    private data class Accumulator(
        var displayAddress: String,
        var lastBody: String,
        var lastDateMillis: Long,
        var messageCount: Int
    )

    fun loadConversations(context: Context): List<Conversation> {
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )

        // Query is newest-first, so the first row we see for a given
        // normalized number is that conversation's most recent message.
        val byNormalizedAddress = LinkedHashMap<String, Accumulator>()

        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} DESC"
        )?.use { cursor ->
            val addressIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY)
            val dateIndex = cursor.getColumnIndex(Telephony.Sms.DATE)
            if (addressIndex < 0 || bodyIndex < 0 || dateIndex < 0) return@use

            while (cursor.moveToNext()) {
                val rawAddress = cursor.getString(addressIndex) ?: continue
                val body = cursor.getString(bodyIndex) ?: ""
                val date = cursor.getLong(dateIndex)
                val normalized = PhoneNumbers.normalize(rawAddress).ifEmpty { rawAddress }

                val existing = byNormalizedAddress[normalized]
                if (existing == null) {
                    byNormalizedAddress[normalized] = Accumulator(
                        displayAddress = rawAddress,
                        lastBody = body,
                        lastDateMillis = date,
                        messageCount = 1
                    )
                } else {
                    existing.messageCount += 1
                }
            }
        }

        return byNormalizedAddress.values
            .sortedByDescending { it.lastDateMillis }
            .map { acc ->
                Conversation(
                    address = acc.displayAddress,
                    lastMessageBody = acc.lastBody,
                    lastMessageDateMillis = acc.lastDateMillis,
                    messageCount = acc.messageCount
                )
            }
    }
}
