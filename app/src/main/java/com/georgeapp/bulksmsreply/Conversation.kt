package com.georgeapp.bulksmsreply

/**
 * One text-message thread, collapsed down to "the sender's number" plus
 * just enough about the most recent message to show a useful list row.
 */
data class Conversation(
    val address: String,
    val lastMessageBody: String,
    val lastMessageDateMillis: Long,
    val messageCount: Int
)
