package bulksms.test.mark.util

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

object BatteryUtil {

    data class Snapshot(val percent: Int, val charging: Boolean)

    fun snapshot(c: Context): Snapshot {
        val bm = c.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val pct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val intent = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val statusFromIntent = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = statusFromIntent == BatteryManager.BATTERY_STATUS_CHARGING ||
            statusFromIntent == BatteryManager.BATTERY_STATUS_FULL ||
            (bm?.isCharging ?: false)
        val percentFinal = if (pct in 0..100) pct else {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) (level * 100 / scale) else -1
        }
        return Snapshot(percent = percentFinal, charging = charging)
    }
}
