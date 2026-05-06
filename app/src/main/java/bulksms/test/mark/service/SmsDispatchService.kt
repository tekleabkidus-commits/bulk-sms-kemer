package bulksms.test.mark.service

import android.Manifest
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import bulksms.test.mark.BulkSmsApp
import bulksms.test.mark.data.entity.Campaign
import bulksms.test.mark.data.entity.CampaignStatus
import bulksms.test.mark.data.entity.PauseReason
import bulksms.test.mark.data.entity.Recipient
import bulksms.test.mark.data.repo.CampaignRepository
import bulksms.test.mark.data.sim.SimRepository
import bulksms.test.mark.util.BatteryUtil
import bulksms.test.mark.util.PermissionUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

/**
 * The single foreground service that drives a campaign end to end. There is at most one
 * active campaign at a time. The dispatch loop:
 *
 *   1. Acquire WAKE_LOCK and start as foreground (notification required on Android 8+).
 *   2. Recover any stale SENDING rows from a prior crash.
 *   3. Loop:
 *      - Check guard rails (permission, SIM, call, battery, thermal). Pause if any tripped.
 *      - Pick next eligible recipient (PENDING with nextEligibleAt <= now).
 *      - If none, but retries are scheduled later, sleep until then.
 *      - If none and no retries pending, mark COMPLETED, stop.
 *      - Send via SmsManager.sendMultipartTextMessage.
 *      - Wait for the SENT broadcast to land in DB (or 30s timeout).
 *      - Sleep `throttleMillis` (with optional ±20% jitter) before next.
 *   4. Track consecutive failure streak — pause on instability / suspected zero balance.
 *   5. Renew wake lock periodically (long campaigns).
 */
class SmsDispatchService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var app: BulkSmsApp
    private var campaignId: Long = -1L

    private lateinit var sentReceiver: SmsResultReceiver
    private lateinit var deliveredReceiver: SmsDeliveryReceiver

    private lateinit var callMonitor: CallStateMonitor
    private lateinit var simMonitor: SimChangeMonitor
    private lateinit var thermalMonitor: ThermalMonitor

    private var consecutiveFailures = 0
    private var consecutiveGenericFailures = 0
    private val sentRateWindow = ArrayDeque<Long>()  // timestamps of recent SENTs for rate calc

    override fun onCreate() {
        super.onCreate()
        app = applicationContext as BulkSmsApp
        isRunning = true
        registerLocalReceivers()
        callMonitor = CallStateMonitor(this).also { it.start() }
        simMonitor = SimChangeMonitor(this) { /* checked inline by guards */ }.also { it.start() }
        thermalMonitor = ThermalMonitor(this).also { it.start() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getLongExtra(EXTRA_CAMPAIGN_ID, -1L) ?: -1L
        if (id <= 0) {
            stopSelfSafe()
            return START_NOT_STICKY
        }
        // If we get a start command for a different campaign while already running, swap.
        if (campaignId > 0 && campaignId != id) {
            scope.launch {
                stopLoop()
                campaignId = id
                attachAndRun()
            }
        } else if (campaignId <= 0) {
            campaignId = id
            scope.launch { attachAndRun() }
        }
        return START_STICKY
    }

    private suspend fun attachAndRun() {
        val campaign = app.campaignRepo.getCampaign(campaignId)
        if (campaign == null) {
            stopSelfSafe()
            return
        }
        startForegroundForCampaign(campaign, sent = 0, ratePerMin = 0)
        acquireWakeLock(campaign.name)

        // Fresh start: ensure our state isn't half-written from a prior session.
        app.campaignRepo.resetSendingOnRestart(campaignId)

        if (campaign.status == CampaignStatus.DRAFT) {
            app.campaignRepo.startRunning(campaignId)
        }

        HeartbeatScheduler.schedule(this)
        DispatchNotifications.postAlert(
            this,
            "Campaign started",
            "\"${campaign.name}\" — sending to ${campaign.totalCount} recipients via ${campaign.simDisplayLabel}.",
            campaignId
        )
        loopJob = scope.launch { runLoop() }
    }

    // --------------------------- Main loop ---------------------------

    private suspend fun runLoop() {
        try {
            while (scope.isActive) {
                val campaign = app.campaignRepo.getCampaign(campaignId) ?: break
                if (campaign.status != CampaignStatus.RUNNING) break

                guardCheck(campaign)?.let { reason ->
                    pauseAndStop(campaign, reason.first, reason.second)
                    return
                }

                val recipient = app.campaignRepo.nextEligible(campaignId)
                if (recipient == null) {
                    val nextRetryAt = app.campaignRepo.nextRetryTime(campaignId)
                    if (nextRetryAt == null) {
                        // Nothing pending and no retries — done.
                        completeAndStop(campaign)
                        return
                    }
                    val waitMs = (nextRetryAt - System.currentTimeMillis()).coerceAtLeast(1_000L).coerceAtMost(60_000L)
                    delay(waitMs)
                    continue
                }

                val rateNow = currentRatePerMin()
                updateProgressNotification(campaign, sentCountFresh(), rateNow)

                val sendOk = sendOneAndWait(campaign, recipient)
                if (sendOk) {
                    consecutiveFailures = 0
                    consecutiveGenericFailures = 0
                    val now = System.currentTimeMillis()
                    sentRateWindow.addLast(now)
                    while (sentRateWindow.isNotEmpty() && now - sentRateWindow.first() > 60_000L) {
                        sentRateWindow.removeFirst()
                    }
                } else {
                    consecutiveFailures++
                    // BALANCE_SUSPECTED tracking happens in sendOneAndWait when the receiver
                    // wrote a GENERIC_FAILURE result code.
                }

                checkInstabilityPauseTriggers(campaign)?.let { reason ->
                    pauseAndStop(campaign, reason.first, reason.second)
                    return
                }

                // Throttle delay
                val waitMs = computeThrottle(campaign)
                delay(waitMs)
            }
        } catch (t: Throwable) {
            // Unexpected — pause for safety.
            try {
                app.campaignRepo.pause(campaignId, PauseReason.USER, "Service error: ${t.javaClass.simpleName}")
            } catch (_: Throwable) {}
        } finally {
            stopSelfSafe()
        }
    }

    private suspend fun guardCheck(campaign: Campaign): Pair<PauseReason, String?>? {
        // 1. SEND_SMS revoked?
        if (!PermissionUtil.hasSendSms(this)) {
            return PauseReason.PERMISSION_REVOKED to null
        }
        // 2. SIM still present?
        val simRepo = SimRepository(this)
        if (!simRepo.isSubscriptionStillActive(campaign.simSubscriptionId)) {
            return PauseReason.SIM_REMOVED to campaign.simDisplayLabel
        }
        // 3. Phone call?
        if (callMonitor.isInCall()) {
            return PauseReason.PHONE_CALL to null
        }
        // 4. Thermal critical?
        if (thermalMonitor.isCritical()) {
            return PauseReason.THERMAL to null
        }
        // 5. Battery critical?
        val battery = BatteryUtil.snapshot(this)
        if (battery.percent in 0..14 && !battery.charging) {
            return PauseReason.BATTERY_CRITICAL to "${battery.percent}%"
        }
        // 6. Storage breathing room (we need at least ~5MB free for Room WAL).
        val free = filesDir.usableSpace
        if (free in 0 until 5L * 1024 * 1024) {
            return PauseReason.STORAGE_FULL to null
        }
        return null
    }

    private fun checkInstabilityPauseTriggers(@Suppress("UNUSED_PARAMETER") campaign: Campaign): Pair<PauseReason, String?>? {
        if (consecutiveGenericFailures >= 5) return PauseReason.BALANCE_SUSPECTED to "5 consecutive failures"
        if (consecutiveFailures >= 3) return PauseReason.INSTABILITY to "3 consecutive failures"
        return null
    }

    /**
     * Sends one recipient and waits for the SENT broadcast (or 30s timeout) to update DB.
     * Returns true if the SENT broadcast came back as RESULT_OK.
     */
    private suspend fun sendOneAndWait(campaign: Campaign, recipient: Recipient): Boolean {
        // Rendered message is constant across all recipients in this app (no merge fields).
        val text = campaign.message
        val smsManager = getSmsManager(campaign.simSubscriptionId)
        val parts = try {
            smsManager.divideMessage(text)
        } catch (_: Throwable) {
            // Fallback if divideMessage fails for any reason
            arrayListOf(text)
        }
        val totalParts = parts.size

        // Mark SENDING (also increments attempts; we read the new attempts back).
        app.campaignRepo.markSending(recipient.id, segments = totalParts)
        val current = app.database.recipientDao().getById(recipient.id)?.attempts
            ?: (recipient.attempts + 1)

        val sentIntents = ArrayList<PendingIntent>(totalParts)
        val deliveredIntents = ArrayList<PendingIntent>(totalParts)
        for (i in 0 until totalParts) {
            sentIntents += sentPi(recipient.id, current, i, totalParts)
            deliveredIntents += deliveredPi(recipient.id, i, totalParts)
        }

        // Reset the deferred so the next SmsResultReceiver.signalResult() will wake us.
        signalDeferred = CompletableDeferred()

        try {
            smsManager.sendMultipartTextMessage(
                recipient.normalizedPhone,
                null,
                parts,
                sentIntents,
                deliveredIntents
            )
        } catch (t: Throwable) {
            val msg = "send error: ${t.javaClass.simpleName}: ${t.message ?: ""}".take(160)
            if (current >= CampaignRepository.MAX_ATTEMPTS) {
                app.campaignRepo.markFailed(recipient.id, msg, null)
            } else {
                app.campaignRepo.markRetryLater(recipient.id, current, msg, null)
            }
            consecutiveGenericFailures++
            return false
        }

        // Wait up to 30s for the SENT broadcast to be processed (we know it arrived because
        // SmsResultReceiver.signalResult() completes the deferred). If timeout, treat as no-result:
        // we mark stale-sent later via reconciliation.
        withTimeoutOrNull(30_000L) { signalDeferred.await() }

        // Re-fetch to see what status the receiver wrote.
        val updated = app.database.recipientDao().getById(recipient.id) ?: return false

        return when (updated.status.name) {
            "SENT", "DELIVERED" -> true
            "FAILED", "PENDING" -> {
                // PENDING here means scheduled for retry. Either way, this attempt did not succeed.
                if (updated.smsResultCode == SmsManager.RESULT_ERROR_GENERIC_FAILURE) {
                    consecutiveGenericFailures++
                }
                false
            }
            else -> false  // SENDING after timeout: treat as not-confirmed
        }
    }

    private fun computeThrottle(campaign: Campaign): Long {
        val base = campaign.throttleMillis
        val v = if (campaign.jitterEnabled) {
            val span = (base * 0.2).toLong().coerceAtLeast(1L)
            base + Random.nextLong(-span, span + 1)
        } else base
        return v.coerceAtLeast(MIN_THROTTLE_MS)
    }

    private fun sentCountFresh(): Int = countSentFromDb()

    private fun countSentFromDb(): Int {
        // Simple synchronous-style count using a blocking call inside coroutine context (IO dispatcher).
        return try {
            val db = app.database
            val cur = db.openHelper.readableDatabase.query(
                "SELECT COUNT(*) FROM recipients WHERE campaignId = ? AND status IN ('SENT','DELIVERED')",
                arrayOf(campaignId.toString())
            )
            cur.use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
        } catch (_: Throwable) { 0 }
    }

    private fun currentRatePerMin(): Int = sentRateWindow.size

    // --------------------------- PendingIntents ---------------------------

    private fun sentPi(recipientId: Long, attempt: Int, partIndex: Int, totalParts: Int): PendingIntent {
        val intent = Intent(SmsResultReceiver.ACTION_SMS_SENT).apply {
            setPackage(packageName)
            putExtra(SmsResultReceiver.EXTRA_RECIPIENT_ID, recipientId)
            putExtra(SmsResultReceiver.EXTRA_ATTEMPT, attempt)
            putExtra(SmsResultReceiver.EXTRA_PART_INDEX, partIndex)
            putExtra(SmsResultReceiver.EXTRA_TOTAL_PARTS, totalParts)
        }
        val req = ((recipientId xor (attempt.toLong() shl 48) xor (partIndex.toLong() shl 32))
            and 0x7FFFFFFFL).toInt()
        return PendingIntent.getBroadcast(
            this, req, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun deliveredPi(recipientId: Long, partIndex: Int, totalParts: Int): PendingIntent {
        val intent = Intent(SmsDeliveryReceiver.ACTION_SMS_DELIVERED).apply {
            setPackage(packageName)
            putExtra(SmsResultReceiver.EXTRA_RECIPIENT_ID, recipientId)
            putExtra(SmsResultReceiver.EXTRA_PART_INDEX, partIndex)
            putExtra(SmsResultReceiver.EXTRA_TOTAL_PARTS, totalParts)
        }
        val req = ((recipientId xor (partIndex.toLong() shl 32) xor 0x100000000L)
            and 0x7FFFFFFFL).toInt()
        return PendingIntent.getBroadcast(
            this, req, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getSmsManager(subId: Int): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(SmsManager::class.java).createForSubscriptionId(subId)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getSmsManagerForSubscriptionId(subId)
        }
    }

    // --------------------------- Receivers ---------------------------

    private fun registerLocalReceivers() {
        sentReceiver = SmsResultReceiver()
        deliveredReceiver = SmsDeliveryReceiver()

        val sentFilter = IntentFilter(SmsResultReceiver.ACTION_SMS_SENT)
        val deliveredFilter = IntentFilter(SmsDeliveryReceiver.ACTION_SMS_DELIVERED)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(sentReceiver, sentFilter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(deliveredReceiver, deliveredFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(sentReceiver, sentFilter)
            registerReceiver(deliveredReceiver, deliveredFilter)
        }
    }

    private fun unregisterLocalReceivers() {
        runCatching { unregisterReceiver(sentReceiver) }
        runCatching { unregisterReceiver(deliveredReceiver) }
    }

    // --------------------------- Notification + Wakelock ---------------------------

    private fun startForegroundForCampaign(campaign: Campaign, sent: Int, ratePerMin: Int) {
        val n = DispatchNotifications.progress(
            this, campaign.id, campaign.name, sent, campaign.totalCount, ratePerMin,
            paused = false, pauseReasonText = null
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(DispatchNotifications.NOTIF_ID_PROGRESS, n,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(DispatchNotifications.NOTIF_ID_PROGRESS, n)
        }
    }

    private fun updateProgressNotification(campaign: Campaign, sent: Int, ratePerMin: Int) {
        val nm = ContextCompat.getSystemService(this, android.app.NotificationManager::class.java) ?: return
        val n = DispatchNotifications.progress(
            this, campaign.id, campaign.name, sent, campaign.totalCount, ratePerMin,
            paused = false, pauseReasonText = null
        )
        try {
            nm.notify(DispatchNotifications.NOTIF_ID_PROGRESS, n)
        } catch (_: SecurityException) { /* notifications off */ }
    }

    private fun acquireWakeLock(name: String) {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val tag = "BulkSmsKemer::dispatch"
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag).apply {
            setReferenceCounted(false)
        }
        wl.acquire(WAKELOCK_TIMEOUT_MS)
        wakeLock = wl
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Throwable) { /* ignore */ }
        wakeLock = null
    }

    // --------------------------- Lifecycle ---------------------------

    private suspend fun completeAndStop(campaign: Campaign) {
        app.campaignRepo.complete(campaign.id)
        DispatchNotifications.postAlert(
            this,
            "Campaign completed",
            "\"${campaign.name}\" finished sending to ${campaign.totalCount} recipients.",
            campaign.id
        )
        stopSelfSafe()
    }

    private suspend fun pauseAndStop(campaign: Campaign, reason: PauseReason, detail: String?) {
        app.campaignRepo.pause(campaign.id, reason, detail)
        DispatchNotifications.postAlert(
            this,
            "Campaign paused",
            DispatchNotifications.pauseReasonText(reason, detail),
            campaign.id
        )
        stopSelfSafe()
    }

    private suspend fun stopLoop() {
        loopJob?.cancel()
        loopJob = null
    }

    private fun stopSelfSafe() {
        releaseWakeLock()
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Throwable) {
            @Suppress("DEPRECATION")
            try { stopForeground(true) } catch (_: Throwable) { /* ignore */ }
        }
        stopSelf()
    }

    override fun onDestroy() {
        try {
            isRunning = false
            unregisterLocalReceivers()
            callMonitor.stop()
            simMonitor.stop()
            thermalMonitor.stop()
            releaseWakeLock()
            scope.cancel()
        } catch (_: Throwable) { /* ignore */ }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_CAMPAIGN_ID = "campaign_id"
        const val EXTRA_BOOT_RECOVERY = "boot_recovery"

        const val WAKELOCK_TIMEOUT_MS: Long = 12L * 3600 * 1000  // 12 hours
        const val MIN_THROTTLE_MS: Long = 500

        @Volatile var isRunning: Boolean = false
            private set

        @Volatile private var signalDeferred: CompletableDeferred<Unit> = CompletableDeferred<Unit>().apply { complete(Unit) }

        /** Called by SmsResultReceiver when a result lands so the loop can stop waiting. */
        fun signalResult() {
            try { signalDeferred.complete(Unit) } catch (_: Throwable) { /* already completed */ }
        }

        fun requestStop(context: Context) {
            try {
                context.stopService(Intent(context, SmsDispatchService::class.java))
            } catch (_: Throwable) { /* ignore */ }
        }
    }
}
