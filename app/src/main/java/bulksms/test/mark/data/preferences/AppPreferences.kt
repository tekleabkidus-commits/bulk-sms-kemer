package bulksms.test.mark.data.preferences

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("bulksms_prefs", Context.MODE_PRIVATE)

    var reliabilityComplete: Boolean
        get() = prefs.getBoolean(KEY_RELIABILITY_COMPLETE, false)
        set(value) = prefs.edit().putBoolean(KEY_RELIABILITY_COMPLETE, value).apply()

    var lastSelectedSubId: Int
        get() = prefs.getInt(KEY_LAST_SUB_ID, -1)
        set(value) = prefs.edit().putInt(KEY_LAST_SUB_ID, value).apply()

    var lastThrottleMs: Long
        get() = prefs.getLong(KEY_LAST_THROTTLE_MS, 1_000L)
        set(value) = prefs.edit().putLong(KEY_LAST_THROTTLE_MS, value).apply()

    var lastJitter: Boolean
        get() = prefs.getBoolean(KEY_LAST_JITTER, false)
        set(value) = prefs.edit().putBoolean(KEY_LAST_JITTER, value).apply()

    companion object {
        private const val KEY_RELIABILITY_COMPLETE = "reliability_complete"
        private const val KEY_LAST_SUB_ID = "last_sub_id"
        private const val KEY_LAST_THROTTLE_MS = "last_throttle_ms"
        private const val KEY_LAST_JITTER = "last_jitter"
    }
}
