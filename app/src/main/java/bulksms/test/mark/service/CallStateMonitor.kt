package bulksms.test.mark.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class CallStateMonitor(private val context: Context) {

    private val inCall = AtomicBoolean(false)
    fun isInCall(): Boolean = inCall.get()

    private var legacyListener: PhoneStateListener? = null
    private var modernCallback: Any? = null
    private val executor = Executors.newSingleThreadExecutor()

    @SuppressLint("MissingPermission")
    fun start() {
        if (!hasPermission()) return
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    inCall.set(state != TelephonyManager.CALL_STATE_IDLE)
                }
            }
            modernCallback = cb
            try { tm.registerTelephonyCallback(executor, cb) } catch (_: Throwable) { /* ignore */ }
        } else {
            @Suppress("DEPRECATION")
            val l = object : PhoneStateListener() {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    inCall.set(state != TelephonyManager.CALL_STATE_IDLE)
                }
            }
            legacyListener = l
            try {
                @Suppress("DEPRECATION")
                tm.listen(l, PhoneStateListener.LISTEN_CALL_STATE)
            } catch (_: Throwable) { /* ignore */ }
        }
    }

    fun stop() {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (modernCallback as? TelephonyCallback)?.let {
                try { tm.unregisterTelephonyCallback(it) } catch (_: Throwable) { /* ignore */ }
            }
            modernCallback = null
        } else {
            legacyListener?.let {
                try {
                    @Suppress("DEPRECATION")
                    tm.listen(it, PhoneStateListener.LISTEN_NONE)
                } catch (_: Throwable) { /* ignore */ }
            }
            legacyListener = null
        }
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
}
