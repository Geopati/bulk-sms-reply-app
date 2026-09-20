package com.georgeapp.bulksmsreply

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Round 5: lets Mr. George get the Log or Reports tab out as a CSV file
 * (opens fine in Excel, no extra library needed). Rather than writing
 * straight to shared storage - which would need broader storage
 * permissions this app doesn't otherwise need - this writes to the app's
 * own cache and hands it off through Android's share sheet, so he can
 * save it, email it, or drop it in Drive himself.
 */
object CsvExporter {

    private val TIMESTAMP_FORMAT = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US)
    private val ROW_TIMESTAMP_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    fun exportLog(context: Context, entries: List<MessageLogEntry>) {
        val csv = buildString {
            appendRow(listOf("Date/Time", "Sender", "Phone Number", "Message", "Action Taken", "Status (in this app)"))
            for (entry in entries) {
                appendRow(
                    listOf(
                        ROW_TIMESTAMP_FORMAT.format(Date(entry.smsDateMillis)),
                        entry.senderLabel,
                        entry.address,
                        entry.body,
                        entry.action ?: "",
                        if (entry.readLocally) "Read" else "Unread"
                    )
                )
            }
        }
        shareCsv(context, "bulk-sms-log", csv)
    }

    fun exportReports(context: Context, buckets: List<PeriodBucket>) {
        val csv = buildString {
            appendRow(listOf("Period", "Sender", "Date/Time", "Phone Number", "Message", "Action Taken"))
            for (bucket in buckets) {
                for (sender in bucket.bySender) {
                    for (message in sender.messages) {
                        appendRow(
                            listOf(
                                bucket.label,
                                sender.senderLabel,
                                ROW_TIMESTAMP_FORMAT.format(Date(message.smsDateMillis)),
                                message.address,
                                message.body,
                                message.action ?: ""
                            )
                        )
                    }
                }
            }
        }
        shareCsv(context, "bulk-sms-reports", csv)
    }

    private fun StringBuilder.appendRow(fields: List<String>) {
        appendLine(fields.joinToString(",") { csvField(it) })
    }

    private fun csvField(value: String): String {
        val needsQuoting = value.contains(',') || value.contains('"') || value.contains('\n')
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuoting) "\"$escaped\"" else escaped
    }

    private fun shareCsv(context: Context, baseName: String, csvContent: String) {
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val fileName = "${baseName}_${TIMESTAMP_FORMAT.format(Date())}.csv"
        val file = File(exportsDir, fileName)
        file.writeText(csvContent)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Save or send $fileName"))
    }
}
