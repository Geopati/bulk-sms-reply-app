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
        createOverrideTable(db)
    }

    private fun createOverrideTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_OVERRIDE (
                $COL_OVERRIDE_ADDRESS TEXT PRIMARY KEY,
                $COL_OVERRIDE_VALUE TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 -> 2 added the "marked read in this app" column, and
        // 2 -> 3 added the manual label-override table (Round 6). Applied
        // as additive steps so Mr. George's existing log data is kept
        // instead of wiped, whichever old version he's upgrading from.
        var version = oldVersion
        if (version < 2) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_READ_LOCALLY INTEGER NOT NULL DEFAULT 0")
            version = 2
        }
        if (version < 3) {
            createOverrideTable(db)
            version = 3
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

    /**
     * Marks every one of [addresses] read as of [atMillis] in one
     * transaction (Round 6's "Mark all read" button) - same effect as
     * calling [markThreadRead] on each address, just faster for a batch.
     */
    fun markAllRead(addresses: List<String>, atMillis: Long) {
        if (addresses.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (address in addresses) {
                val normalized = PhoneNumbers.normalize(address).ifEmpty { address }
                val values = ContentValues().apply { put(COL_READ_LOCALLY, 1) }
                db.update(
                    TABLE,
                    values,
                    "$COL_NORMALIZED_ADDRESS = ? AND $COL_READ_LOCALLY = 0",
                    arrayOf(normalized)
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Sets Mr. George's manual Political/Commercial/No-label choice for
     * this number (Round 6) - one of AttributionExtractor.
     * OVERRIDE_POLITICAL/OVERRIDE_COMMERCIAL/OVERRIDE_NONE. Pass null to
     * clear it and go back to automatic detection.
     */
    fun setLabelOverride(address: String, override: String?) {
        val normalized = PhoneNumbers.normalize(address).ifEmpty { address }
        val db = writableDatabase
        if (override == null) {
            db.delete(TABLE_OVERRIDE, "$COL_OVERRIDE_ADDRESS = ?", arrayOf(normalized))
        } else {
            val values = ContentValues().apply {
                put(COL_OVERRIDE_ADDRESS, normalized)
                put(COL_OVERRIDE_VALUE, override)
            }
            db.insertWithOnConflict(TABLE_OVERRIDE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    /** All of Mr. George's manual label overrides, normalized address ->
     *  override value, for applying to every screen that shows a sender
     *  label. Loaded once per refresh rather than queried per-row. */
    fun labelOverrides(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        readableDatabase.query(
            TABLE_OVERRIDE,
            arrayOf(COL_OVERRIDE_ADDRESS, COL_OVERRIDE_VALUE),
            null, null, null, null, null
        ).use { cursor ->
            val addressIdx = cursor.getColumnIndexOrThrow(COL_OVERRIDE_ADDRESS)
            val valueIdx = cursor.getColumnIndexOrThrow(COL_OVERRIDE_VALUE)
            while (cursor.moveToNext()) {
                result[cursor.getString(addressIdx)] = cursor.getString(valueIdx)
            }
        }
        return result
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
     * One conversation that has had a bulk action (reply/block/report)
     * applied to its most recent message - i.e. it belongs in the S.R.B.
     * (Stop/Report/Block) tab instead of the main Messages tab. If a new,
     * not-yet-actioned message later arrives from the same sender, that
     * sender naturally drops out of this list and reappears in Messages -
     * only the conversation's CURRENT latest message matters, not its
     * history.
     */
    data class ProcessedConversation(
        val normalizedAddress: String,
        val address: String,
        val attribution: String?,
        val body: String,
        val action: String,
        val actionAtMillis: Long,
        /** Mr. George's manual label override for this number, if any
         *  (Round 6/8) - see MessageLogEntry.labelOverride. */
        val labelOverride: String? = null,
        /** See MessageLogEntry.guessingEnabled (Round 8) - baked in at
         *  query time the same way. */
        val guessingEnabled: Boolean = true
    )

    /**
     * Conversations whose most recent message already has a bulk action
     * recorded against it - drives the S.R.B. tab and the Messages-tab
     * filter that hides them there (see MainActivity). Uses only the
     * existing action/action_at columns - no schema change needed.
     */
    fun processedConversations(guessingEnabled: Boolean = true): List<ProcessedConversation> {
        val overrides = labelOverrides()
        val sql = """
            SELECT $COL_NORMALIZED_ADDRESS, $COL_ADDRESS, $COL_ATTRIBUTION, $COL_BODY, $COL_ACTION, $COL_ACTION_AT
            FROM $TABLE m1
            WHERE $COL_SMS_DATE = (
                SELECT MAX($COL_SMS_DATE) FROM $TABLE m2
                WHERE m2.$COL_NORMALIZED_ADDRESS = m1.$COL_NORMALIZED_ADDRESS
            )
            AND $COL_ACTION IS NOT NULL AND $COL_ACTION != ''
            ORDER BY $COL_ACTION_AT DESC
        """.trimIndent()

        val result = mutableListOf<ProcessedConversation>()
        readableDatabase.rawQuery(sql, null).use { cursor ->
            val normalizedIdx = cursor.getColumnIndexOrThrow(COL_NORMALIZED_ADDRESS)
            val addressIdx = cursor.getColumnIndexOrThrow(COL_ADDRESS)
            val attributionIdx = cursor.getColumnIndexOrThrow(COL_ATTRIBUTION)
            val bodyIdx = cursor.getColumnIndexOrThrow(COL_BODY)
            val actionIdx = cursor.getColumnIndexOrThrow(COL_ACTION)
            val actionAtIdx = cursor.getColumnIndexOrThrow(COL_ACTION_AT)
            while (cursor.moveToNext()) {
                val normalizedAddress = cursor.getString(normalizedIdx)
                result.add(
                    ProcessedConversation(
                        normalizedAddress = normalizedAddress,
                        address = cursor.getString(addressIdx),
                        attribution = if (cursor.isNull(attributionIdx)) null else cursor.getString(attributionIdx),
                        body = cursor.getString(bodyIdx),
                        action = cursor.getString(actionIdx),
                        actionAtMillis = cursor.getLong(actionAtIdx),
                        labelOverride = overrides[normalizedAddress],
                        guessingEnabled = guessingEnabled
                    )
                )
            }
        }
        return result
    }

    /**
     * Searchable view of the log for the Log screen. [searchText] matches
     * against the sender number, the extracted attribution, and the
     * message body (case-insensitive substring). [sinceMillis] limits how
     * far back to look; pass null for "all time". [guessingEnabled] is
     * Mr. George's app-wide automatic-guessing toggle (Round 8) - see
     * AppSettings.automaticGuessingEnabled.
     */
    fun queryLog(
        searchText: String?,
        sinceMillis: Long?,
        limit: Int = 500,
        guessingEnabled: Boolean = true
    ): List<MessageLogEntry> {
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

        val overrides = labelOverrides()
        return readableDatabase.query(
            TABLE,
            null,
            whereParts.joinToString(" AND ").ifEmpty { null },
            whereArgs.toTypedArray(),
            null, null,
            "$COL_SMS_DATE DESC",
            limit.toString()
        ).use { cursor -> cursor.toEntries(overrides, guessingEnabled) }
    }

    /** All logged messages since [sinceMillis] (or all time if null), for
     *  building the day/week/month/etc. reports. No row-count limit.
     *  [guessingEnabled] is Mr. George's app-wide automatic-guessing
     *  toggle (Round 8) - see AppSettings.automaticGuessingEnabled. */
    fun allForReports(sinceMillis: Long?, guessingEnabled: Boolean = true): List<MessageLogEntry> {
        val where = sinceMillis?.let { "$COL_SMS_DATE >= ?" }
        val args = sinceMillis?.let { arrayOf(it.toString()) }
        val overrides = labelOverrides()
        return readableDatabase.query(
            TABLE, null, where, args, null, null, "$COL_SMS_DATE DESC"
        ).use { cursor -> cursor.toEntries(overrides, guessingEnabled) }
    }

    private fun android.database.Cursor.toEntries(
        overrides: Map<String, String> = emptyMap(),
        guessingEnabled: Boolean = true
    ): List<MessageLogEntry> {
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
            val normalizedAddress = getString(normalizedIdx)
            entries.add(
                MessageLogEntry(
                    id = getLong(idIdx),
                    smsDateMillis = getLong(dateIdx),
                    address = getString(addressIdx),
                    normalizedAddress = normalizedAddress,
                    attribution = if (isNull(attributionIdx)) null else getString(attributionIdx),
                    body = getString(bodyIdx),
                    action = if (isNull(actionIdx)) null else getString(actionIdx),
                    actionAtMillis = if (isNull(actionAtIdx)) null else getLong(actionAtIdx),
                    readLocally = getInt(readLocallyIdx) != 0,
                    labelOverride = overrides[normalizedAddress],
                    guessingEnabled = guessingEnabled
                )
            )
        }
        return entries
    }

    companion object {
        private const val DB_NAME = "message_log.db"
        private const val DB_VERSION = 3
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
        private const val TABLE_OVERRIDE = "label_override"
        private const val COL_OVERRIDE_ADDRESS = "normalized_address"
        private const val COL_OVERRIDE_VALUE = "override"
    }
}
