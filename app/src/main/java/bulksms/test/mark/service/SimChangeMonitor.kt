package bulksms.test.mark.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

class SimChangeMonitor(
    private val context: Context,
    private val onChange: () -> Unit
) {
    private var listener: SubscriptionManager.OnSubscriptionsChangedListener? = null

    @SuppressLint("MissingPermission")
    fun start() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) return
        val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            ?: return
        val l = object : SubscriptionManager.OnSubscriptionsChangedListener() {
            override fun onSubscriptionsChanged() = onChange()
        }
        listener = l
        try { sm.addOnSubscriptionsChangedListener(l) } catch (_: Throwable) { /* ignore */ }
    }

    fun stop() {
        val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            ?: return
        listener?.let {
            try { sm.removeOnSubscriptionsChangedListener(it) } catch (_: Throwable) { /* ignore */ }
        }
        listener = null
    }
}
