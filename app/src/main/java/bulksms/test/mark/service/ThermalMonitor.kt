package bulksms.test.mark.service

import android.content.Context
import android.os.Build
import android.os.PowerManager
import java.util.concurrent.atomic.AtomicInteger

class ThermalMonitor(private val context: Context) {

    private val status = AtomicInteger(0)
    fun status(): Int = status.get()

    /** True when status is SEVERE or worse (>= 4). */
    fun isCritical(): Boolean = status.get() >= 4

    private var listener: PowerManager.OnThermalStatusChangedListener? = null

    fun start() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        val l = PowerManager.OnThermalStatusChangedListener { s -> status.set(s) }
        listener = l
        status.set(pm.currentThermalStatus)
        try {
            pm.addThermalStatusListener(l)
        } catch (_: Throwable) { /* ignore */ }
    }

    fun stop() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        listener?.let {
            try { pm.removeThermalStatusListener(it) } catch (_: Throwable) { /* ignore */ }
        }
        listener = null
    }
}
