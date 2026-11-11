package bulksms.test.mark.data.sim

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

data class SimInfo(
    val subscriptionId: Int,
    val slotIndex: Int,
    val carrierName: String,
    val displayName: String,
    val number: String?,
    val last4: String?
) {
    /** "Ethio Telecom · Slot 1 · ••••1234" */
    val label: String
        get() = buildString {
            append(if (carrierName.isNotBlank()) carrierName else "SIM")
            append(" · Slot ").append(slotIndex + 1)
            if (!last4.isNullOrBlank()) append(" · ••••").append(last4)
        }
}

class SimRepository(private val context: Context) {

    @SuppressLint("MissingPermission")
    fun listActive(): List<SimInfo> {
        if (!hasPhoneStatePermission()) return emptyList()
        val sm = ContextCompat.getSystemService(context, SubscriptionManager::class.java) ?: return emptyList()
        val subs = sm.activeSubscriptionInfoList ?: return emptyList()
        return subs.map { s ->
            val number = try { s.number } catch (_: Throwable) { null }
            SimInfo(
                subscriptionId = s.subscriptionId,
                slotIndex = s.simSlotIndex,
                carrierName = s.carrierName?.toString().orEmpty(),
                displayName = s.displayName?.toString().orEmpty(),
                number = number,
                last4 = number?.takeLast(4)?.takeIf { it.length == 4 }
            )
        }
    }

    fun isSubscriptionStillActive(subscriptionId: Int): Boolean =
        listActive().any { it.subscriptionId == subscriptionId }

    private fun hasPhoneStatePermission(): Boolean {
        val readPhone = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        // READ_PHONE_NUMBERS only matters from API 30+ for the number; READ_PHONE_STATE is enough for SIM list.
        return readPhone || Build.VERSION.SDK_INT < Build.VERSION_CODES.M
    }
}
