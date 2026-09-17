package com.georgeapp.bulksmsreply

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

/** How to bucket the log for the Reports screen. */
enum class ReportPeriod(val label: String, val bucketsToShow: Int) {
    DAY("Day", 30),
    WEEK("Week", 12),
    MONTH("Month", 12),
    QUARTER("Quarter (3 months)", 8),
    HALF_YEAR("Half year (6 months)", 6),
    YEAR("Year", 5)
}

/** How many texts came in from one sender within a period. */
data class SenderCount(val senderLabel: String, val count: Int)

/** One row of a report: a time period plus its total and per-sender breakdown. */
data class PeriodBucket(
    val label: String,
    val startMillis: Long,
    val total: Int,
    val bySender: List<SenderCount>
)

/**
 * Turns the raw message log into the day/week/month/quarter/half-year/
 * year breakdowns Mr. George asked for. Pure Kotlin, no database access,
 * so it's easy to test/reason about on its own.
 */
object ReportGenerator {

    private val zone: ZoneId = ZoneId.systemDefault()

    private data class BucketInfo(val label: String, val startMillis: Long)

    fun build(entries: List<MessageLogEntry>, period: ReportPeriod): List<PeriodBucket> {
        if (entries.isEmpty()) return emptyList()

        val grouped = LinkedHashMap<String, MutableList<MessageLogEntry>>()
        val bucketInfo = HashMap<String, BucketInfo>()

        for (entry in entries) {
            val zdt = Instant.ofEpochMilli(entry.smsDateMillis).atZone(zone)
            val (key, label, startMillis) = bucketKeyFor(zdt, period)
            grouped.getOrPut(key) { mutableListOf() }.add(entry)
            bucketInfo.putIfAbsent(key, BucketInfo(label, startMillis))
        }

        val buckets = grouped.entries.map { (key, items) ->
            val bySender = items
                .groupBy { it.senderGroupKey }
                .map { (_, groupItems) ->
                    SenderCount(
                        senderLabel = groupItems.first().senderLabel,
                        count = groupItems.size
                    )
                }
                .sortedByDescending { it.count }

            val info = bucketInfo.getValue(key)
            PeriodBucket(
                label = info.label,
                startMillis = info.startMillis,
                total = items.size,
                bySender = bySender
            )
        }

        return buckets
            .sortedByDescending { it.startMillis }
            .take(period.bucketsToShow)
    }

    private fun bucketKeyFor(zdt: ZonedDateTime, period: ReportPeriod): Triple<String, String, Long> {
        return when (period) {
            ReportPeriod.DAY -> {
                val day = zdt.toLocalDate()
                val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
                Triple(day.toString(), day.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")), start)
            }

            ReportPeriod.WEEK -> {
                val weekFields = WeekFields.of(Locale.getDefault())
                val week = zdt.get(weekFields.weekOfWeekBasedYear())
                val weekYear = zdt.get(weekFields.weekBasedYear())
                val monday = zdt.toLocalDate().with(weekFields.dayOfWeek(), 1)
                val start = monday.atStartOfDay(zone).toInstant().toEpochMilli()
                val key = "$weekYear-W$week"
                val label = "Week of ${monday.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}"
                Triple(key, label, start)
            }

            ReportPeriod.MONTH -> {
                val ym = zdt.toLocalDate().withDayOfMonth(1)
                val start = ym.atStartOfDay(zone).toInstant().toEpochMilli()
                Triple(ym.toString(), ym.format(DateTimeFormatter.ofPattern("MMMM yyyy")), start)
            }

            ReportPeriod.QUARTER -> {
                val year = zdt.year
                val quarter = (zdt.monthValue - 1) / 3 + 1
                val startMonth = (quarter - 1) * 3 + 1
                val start = zdt.toLocalDate()
                    .withMonth(startMonth).withDayOfMonth(1)
                    .atStartOfDay(zone).toInstant().toEpochMilli()
                Triple("$year-Q$quarter", "Q$quarter $year", start)
            }

            ReportPeriod.HALF_YEAR -> {
                val year = zdt.year
                val half = if (zdt.monthValue <= 6) 1 else 2
                val startMonth = if (half == 1) 1 else 7
                val start = zdt.toLocalDate()
                    .withMonth(startMonth).withDayOfMonth(1)
                    .atStartOfDay(zone).toInstant().toEpochMilli()
                val label = if (half == 1) "Jan-Jun $year" else "Jul-Dec $year"
                Triple("$year-H$half", label, start)
            }

            ReportPeriod.YEAR -> {
                val year = zdt.year
                val start = zdt.toLocalDate()
                    .withDayOfYear(1)
                    .atStartOfDay(zone).toInstant().toEpochMilli()
                Triple(year.toString(), year.toString(), start)
            }
        }
    }
}
