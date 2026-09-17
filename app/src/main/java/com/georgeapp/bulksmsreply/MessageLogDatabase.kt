package com.georgeapp.bulksmsreply

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * The running log Mr. George asked for: every incoming text message we've
 * seen, with when it arrived, who it was from (raw number), who it claims
 * to be on behalf of (if the message says so), and what action (if any)
 * he took on it. Plain SQLite (no extra libraries) to keep the build
 * simple and reliable.
 */
class MessageLogDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_SMS_DATE INTEGER NOT NULL,
                $COL_ADDRESS TEXT NOT NULL,
                $COL_NORMALIZED_ADDRESS TEXT NOT NULL,
                $COL_ATTRIBUTION TEXT,
                $COL_BODY TEXT NOT NULL,
                $COL_ACTION TEXT,
                $COL_ACTION_AT INTEGER,
                $COL_READ_LOCALLY INTEGER NOT NULL DEFAULT 0,
                UNIQUE($COL_SMS_DATE, $COL_NORMALIZED_ADDRESS)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_log_date ON $TABLE ($COL_SMS_DATE)")
        db.execSQL("CREATE INDEX idx_log_normalized_address ON $TABLE ($COL_NORMALIZED_ADDRESS)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 -> 2 added the "marked read in this app" column. Keep
        // Mr. George's existing log data instead of wiping it when we can.
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_READ_LOCALLY INTEGER NOT NULL DEFAULT 0")
        } else {
            db.execSQL("DROP TABLE IF EXISTS $TABLE")
            onCreate(db)
        }
    }

    /**
     * Adds any messages we haven't logged before. Safe to call every time
     * the app reads the inbox - already-logged messages are silently
     * skipped (same sms date + same sender = same message).
     */
    fun recordIncoming(messages: List<RawSmsMessage>) {
        if (messages.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (message in messages) {
                val normalized = PhoneNumbers.normalize(message.address).ifEmpty { message.address }
                val values = ContentValues().apply {
                    put(COL_SMS_DATE, message.dateMillis)
                    put(COL_ADDRESS, message.address)
                    put(COL_NORMALIZED_ADDRESS, normalized)
                    put(COL_ATTRIBUTION, AttributionExtractor.extract(message.body))
                    put(COL_BODY, message.body)
                }
                db.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Marks the most recent logged message from this sender as having had
     * [action] applied at [atMillis] - e.g. "REPLIED", "BLOCKED",
     * "REPORTED_SPAM". If an action was already recorded, the new one is
     * appended so a full history of what was done isn't lost.
     */
    fun recordAction(address: String, action: String, atMillis: Long) {
        val normalized = PhoneNumbers.normalize(address).ifEmpty { address }
        val db = writableDatabase
        db.query(
            TABLE,
            arrayOf(COL_ID, COL_ACTION),
            "$COL_NORMALIZED_ADDRESS = ?",
            arrayOf(normalized),
            null, null,
            "$COL_SMS_DATE DESC",
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID))
                val existingAction = cursor.getString(cursor.getColumnIndexOrThrow(COL_ACTION))
                val combinedAction = when {
                    existingAction.isNullOrBlank() -> action
                    existingAction.contains(action) -> existingAction
                    else -> "$existingAction, $action"
                }
                val values = ContentValues().apply {
                    put(COL_ACTION, combinedAction)
                    put(COL_ACTION_AT, atMillis)
                }
                db.update(TABLE, values, "$COL_ID = ?", arrayOf(id.toString()))
            }
        }
    }

    /**
     * Marks every logged message from this sender as "read in this app" as
     * of [atMillis]. This is a local, in-app-only flag - it does NOT touch
     * your phone's shared SMS inbox, so your native Messages app will keep
     * showing these as unread (Android only lets your phone's default
     * texting app write to that shared inbox; per Mr. George's decision on
     * 2026-09-17, this app stays a lightweight add-on rather than taking
     * over as the default app). Called automatically whenever a bulk action
     * (reply/block/report) is applied to a conversation, so "processed"
     * conversations stop showing as unread inside this app's own Log/Reports.
     */
    fun markThreadRead(address: String, atMillis: Long) {
        val normalized = PhoneNumbers.normalize(address).ifEmpty { address }
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_READ_LOCALLY, 1)
            put(COL_ACTION_AT, atMillis)
        }
        db.update(
            TABLE,
            values,
            "$COL_NORMALIZED_ADDRESS = ? AND $COL_READ_LOCALLY = 0",
            arrayOf(normalized)
        )
    }

    /** Normalized addresses that still have at least one message this app
     *  hasn't marked read yet - used to show an "unread" indicator on the
     *  Messages tab. */
    fun unreadNormalizedAddresses(): Set<String> {
        val result = mutableSetOf<String>()
        readableDatabase.query(
            true,
            TABLE,
            arrayOf(COL_NORMALIZED_ADDRESS),
            "$COL_READ_LOCALLY = 0",
            null, null, null, null, null
        ).use { cursor ->
            val idx = cursor.getColumnIndexOrThrow(COL_NORMALIZED_ADDRESS)
            while (cursor.moveToNext()) {
                result.add(cursor.getString(idx))
            }
        }
        return result
    }

    /**
     * Searchable view of the log for the Log screen. [searchText] matches
     * against the sender number, the extracted attribution, and the
     * message body (case-insensitive substring). [sinceMillis] limits how
     * far back to look; pass null for "all time".
     */
    fun queryLog(searchText: String?, sinceMillis: Long?, limit: Int = 500): List<MessageLogEntry> {
        val whereParts = mutableListOf<String>()
        val whereArgs = mutableListOf<String>()

        if (sinceMillis != null) {
            whereParts.add("$COL_SMS_DATE >= ?")
            whereArgs.add(sinceMillis.toString())
        }
        if (!searchText.isNullOrBlank()) {
            val like = "%${searchText.trim()}%"
            whereParts.add("($COL_ADDRESS LIKE ? OR $COL_ATTRIBUTION LIKE ? OR $COL_BODY LIKE ?)")
            whereArgs.add(like)
            whereArgs.add(like)
            whereArgs.add(like)
        }

        return readableDatabase.query(
            TABLE,
            null,
            whereParts.joinToString(" AND ").ifEmpty { null },
            whereArgs.toTypedArray(),
            null, null,
            "$COL_SMS_DATE DESC",
            limit.toString()
        ).use { cursor -> cursor.toEntries() }
    }

    /** All logged messages since [sinceMillis] (or all time if null), for
     *  building the day/week/month/etc. reports. No row-count limit. */
    fun allForReports(sinceMillis: Long?): List<MessageLogEntry> {
        val where = sinceMillis?.let { "$COL_SMS_DATE >= ?" }
        val args = sinceMillis?.let { arrayOf(it.toString()) }
        return readableDatabase.query(
            TABLE, null, where, args, null, null, "$COL_SMS_DATE DESC"
        ).use { cursor -> cursor.toEntries() }
    }

    private fun android.database.Cursor.toEntries(): List<MessageLogEntry> {
        val entries = mutableListOf<MessageLogEntry>()
        val idIdx = getColumnIndexOrThrow(COL_ID)
        val dateIdx = getColumnIndexOrThrow(COL_SMS_DATE)
        val addressIdx = getColumnIndexOrThrow(COL_ADDRESS)
        val normalizedIdx = getColumnIndexOrThrow(COL_NORMALIZED_ADDRESS)
        val attributionIdx = getColumnIndexOrThrow(COL_ATTRIBUTION)
        val bodyIdx = getColumnIndexOrThrow(COL_BODY)
        val actionIdx = getColumnIndexOrThrow(COL_ACTION)
        val actionAtIdx = getColumnIndexOrThrow(COL_ACTION_AT)
        val readLocallyIdx = getColumnIndexOrThrow(COL_READ_LOCALLY)

        while (moveToNext()) {
            entries.add(
                MessageLogEntry(
                    id = getLong(idIdx),
                    smsDateMillis = getLong(dateIdx),
                    address = getString(addressIdx),
                    normalizedAddress = getString(normalizedIdx),
                    attribution = if (isNull(attributionIdx)) null else getString(attributionIdx),
                    body = getString(bodyIdx),
                    action = if (isNull(actionIdx)) null else getString(actionIdx),
                    actionAtMillis = if (isNull(actionAtIdx)) null else getLong(actionAtIdx),
                    readLocally = getInt(readLocallyIdx) != 0
                )
            )
        }
        return entries
    }

    companion object {
        private const val DB_NAME = "message_log.db"
        private const val DB_VERSION = 2
        private const val TABLE = "message_log"
        private const val COL_ID = "id"
        private const val COL_SMS_DATE = "sms_date"
        private const val COL_ADDRESS = "address"
        private const val COL_NORMALIZED_ADDRESS = "normalized_address"
        private const val COL_ATTRIBUTION = "attribution"
        private const val COL_BODY = "body"
        private const val COL_ACTION = "action"
        private const val COL_ACTION_AT = "action_at"
        private const val COL_READ_LOCALLY = "read_locally"
    }
}
