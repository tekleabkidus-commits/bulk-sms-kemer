package bulksms.test.mark.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object Time {

    private val isoLocal: SimpleDateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
    }

    private val pretty: SimpleDateFormat by lazy {
        SimpleDateFormat("MMM d, HH:mm:ss", Locale.US)
    }

    private val prettyDate: SimpleDateFormat by lazy {
        SimpleDateFormat("MMM d, yyyy", Locale.US)
    }

    fun iso(epochMs: Long): String = isoLocal.format(Date(epochMs))
    fun pretty(epochMs: Long): String = pretty.format(Date(epochMs))
    fun prettyDate(epochMs: Long): String = prettyDate.format(Date(epochMs))

    fun durationShort(ms: Long): String {
        if (ms < 0) return "-"
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m ${s}s"
            else  -> "${s}s"
        }
    }

    fun startOfTodayLocal(): Long {
        val cal = java.util.Calendar.getInstance(TimeZone.getDefault())
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
