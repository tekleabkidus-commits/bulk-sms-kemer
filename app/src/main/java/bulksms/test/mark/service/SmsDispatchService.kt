package bulksms.test.mark.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
            // Reset failure trackers at the start of each run
            failureStreak = 0
            genericFailureStreak = 0

            while (scope.isActive) {
                val campaign = app.campaignRepo.getCampaign(campaignId) ?: break
                if (campaign.status != CampaignStatus.RUNNING) break

                val guardResult = guardCheck(campaign)
                if (guardResult != null) {
                    // Any system-level block (SIM removed, phone call, no service, low
                    // battery, no signal, permission revoked, etc.) goes through the
                    // auto-retry path. We wait up to 60s for the condition to clear
                    // before pausing the campaign for real.
                    if (handlePauseOrAutoRetry(campaign, guardResult.first, guardResult.second)) {
                        return
                    }
                    // Cleared — fall through to normal dispatch.
                }

                // Reconcile any in-flight messages whose result broadcast didn't come back
                // within the window. The SMS has been handed to the radio at that point;
                // we treat lost broadcasts as "sent" (with a Stale-recovered note) rather
                // than blocking the dispatcher waiting on them.
                val now0 = System.currentTimeMillis()
                app.database.recipientDao().recoverStaleSending(now0, now0 - STALE_SENDING_CUTOFF_MS)

                // Instability / balance pause checks (counters maintained by SmsResultReceiver)
                if (genericFailureStreak >= 5) {
                    if (handlePauseOrAutoRetry(campaign, PauseReason.BALANCE_SUSPECTED, "5 consecutive failures")) {
                        return
                    }
                    // Cleared via wait — counters were reset; continue.
                }
                if (failureStreak >= 3) {
                    if (handlePauseOrAutoRetry(campaign, PauseReason.INSTABILITY, "3 consecutive failures")) {
                        return
                    }
                    // Cleared via wait — counters were reset; continue.
                }

                val recipient = app.campaignRepo.nextEligible(campaignId)
                if (recipient == null) {
                    val nextRetryAt = app.campaignRepo.nextRetryTime(campaignId)
                    val pending = app.campaignRepo.pendingCount(campaignId)
                    if (nextRetryAt == null && pending == 0) {
                        // Nothing pending and no retries — but check that nothing is still
                        // in SENDING state (a broadcast might still be on the wire).
                        val stillSending = app.database.recipientDao().sendingCount(campaignId)
                        if (stillSending == 0) {
                            completeAndStop(campaign)
                            return
                        }
                        delay(1_000L)
                        continue
                    }
                    val sleep = if (nextRetryAt != null) {
                        (nextRetryAt - System.currentTimeMillis()).coerceAtLeast(500L).coerceAtMost(60_000L)
                    } else 1_000L
                    delay(sleep)
                    continue
                }

                updateProgressNotification(campaign, sentCountFresh(), currentRatePerMin())

                // Fire-and-forget: don't block on the result broadcast. The receiver
                // updates the recipient row + the failure counters asynchronously.
                fireSend(campaign, recipient)

                // Throttle delay paces the loop; broadcast latency does NOT.
                delay(computeThrottle(campaign))
            }
        } catch (t: Throwable) {
            try {
                app.campaignRepo.pause(campaignId, PauseReason.USER, "Service error: ${t.javaClass.simpleName}")
            } catch (_: Throwable) {}
        } finally {
            stopSelfSafe()
        }
    }

    /**
     * Unified pause-or-auto-retry handler. Called whenever the dispatcher decides the
     * campaign should pause for any reason. For USER pauses, this calls pauseAndStop
     * immediately. For any other reason, it enters a wait loop for up to 2 minutes,
     * re-checking every 5 seconds whether the condition has cleared.
     *
     * Returns true if the campaign was paused (caller should `return` from runLoop).
     * Returns false if the condition cleared during the wait (caller should continue).
     */
    private suspend fun handlePauseOrAutoRetry(
        campaign: Campaign,
        reason: PauseReason,
        detail: String?
    ): Boolean {
        // User-initiated pauses are honored immediately.
        if (reason == PauseReason.USER) {
            pauseAndStop(campaign, reason, detail)
            return true
        }

        val maxWaitMs = AUTO_RETRY_TOTAL_MS
        val startMs = System.currentTimeMillis()

        while (scope.isActive) {
            val elapsed = System.currentTimeMillis() - startMs
            val remaining = maxWaitMs - elapsed
            if (remaining <= 0) {
                val baseDetail = detail?.takeIf { it.isNotBlank() }?.let { "; $it" } ?: ""
                pauseAndStop(campaign, reason, "Auto-retried for 2 min$baseDetail")
                return true
            }

            val sleepMs = minOf(AUTO_RETRY_INTERVAL_MS, remaining)

            val label = pauseReasonLabel(reason)
            val secondsLeft = (remaining / 1000).coerceAtLeast(1).toInt()
            updateWaitingNotification(campaign, "$label — auto-resuming ($secondsLeft s left)")

            delay(sleepMs)
            if (!scope.isActive) return true

            // Reload — user may have manually paused/cancelled during the wait.
            val current = app.campaignRepo.getCampaign(campaignId) ?: return true
            if (current.status != CampaignStatus.RUNNING) return true

            // Re-evaluate. If guardCheck still returns a problem, keep waiting.
            val newGuard = guardCheck(current)
            if (newGuard != null) continue

            // Cleared. Reset failure streaks so the dispatcher gets a clean attempt.
            failureStreak = 0
            genericFailureStreak = 0
            return false
        }
        return true
    }

    private fun pauseReasonLabel(r: PauseReason): String = when (r) {
        PauseReason.SIM_REMOVED -> "SIM unavailable"
        PauseReason.PHONE_CALL -> "Phone call active"
        PauseReason.NO_SERVICE -> "No signal"
        PauseReason.RATE_LIMIT -> "Rate limited"
        PauseReason.DAILY_CAP -> "Daily cap reached"
        PauseReason.INSTABILITY -> "Network unstable"
        PauseReason.BALANCE_SUSPECTED -> "Balance suspected"
        PauseReason.BATTERY_CRITICAL -> "Battery critical"
        PauseReason.THERMAL -> "Phone overheating"
        PauseReason.PERMISSION_REVOKED -> "Permission revoked"
        PauseReason.STORAGE_FULL -> "Storage full"
        PauseReason.ROAMING -> "Roaming"
        PauseReason.USER -> "Paused"
    }

    private fun updateWaitingNotification(campaign: Campaign, message: String) {
        val nm = ContextCompat.getSystemService(this, android.app.NotificationManager::class.java) ?: return
        val n = DispatchNotifications.progress(
            this, campaign.id, campaign.name,
            sentCountFresh(), campaign.totalCount, currentRatePerMin(),
            paused = true, pauseReasonText = message
        )
        try {
            nm.notify(DispatchNotifications.NOTIF_ID_PROGRESS, n)
        } catch (_: SecurityException) { /* notifications off */ }
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

    /**
     * Fire-and-forget send. The dispatcher does NOT wait for the SENT broadcast — that
     * comes back asynchronously and SmsResultReceiver writes the result to the DB and
     * updates the failure-streak counter. This is the key to honoring the throttle setting
     * accurately: with a 1s throttle the loop runs once per second regardless of how
     * long the carrier takes to send a result broadcast back (which on Ethio's network
     * can be 3-10 seconds per message).
     */
    private suspend fun fireSend(campaign: Campaign, recipient: Recipient) {
        val text = campaign.message
        val smsManager = getSmsManager(campaign.simSubscriptionId)
        val parts = try {
            smsManager.divideMessage(text)
        } catch (_: Throwable) {
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

        try {
            smsManager.sendMultipartTextMessage(
                recipient.normalizedPhone,
                null,
                parts,
                sentIntents,
                deliveredIntents
            )
        } catch (t: Throwable) {
            // Synchronous failure — never reaches the radio. Count it locally.
            val msg = "send error: ${t.javaClass.simpleName}: ${t.message ?: ""}".take(160)
            if (current >= CampaignRepository.MAX_ATTEMPTS) {
                app.campaignRepo.markFailed(recipient.id, msg, null)
            } else {
                app.campaignRepo.markRetryLater(recipient.id, current, msg, null)
            }
            recordFailure(isGeneric = false)
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

    private fun currentRatePerMin(): Int {
        // Count rows whose sentAt is within the last minute. Cheap indexed query.
        return try {
            val cutoff = System.currentTimeMillis() - 60_000L
            val cur = app.database.openHelper.readableDatabase.query(
                "SELECT COUNT(*) FROM recipients WHERE campaignId = ? AND sentAt IS NOT NULL AND sentAt >= ?",
                arrayOf(campaignId.toString(), cutoff.toString())
            )
            cur.use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
        } catch (_: Throwable) { 0 }
    }

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
        const val STALE_SENDING_CUTOFF_MS: Long = 60_000  // 60s — broadcast presumed lost
        const val MAX_IN_FLIGHT: Int = 5  // cap concurrent unconfirmed sends to protect modem

        /** Total time to spend auto-retrying any system pause before giving up. */
        const val AUTO_RETRY_TOTAL_MS: Long = 2L * 60 * 1000  // 2 minutes

        /** Interval between auto-retry attempts. */
        const val AUTO_RETRY_INTERVAL_MS: Long = 5_000L  // 5 seconds

        @Volatile var isRunning: Boolean = false
            private set

        // --- Failure-streak counters (updated by SmsResultReceiver, read by the loop) ---
        @Volatile var failureStreak: Int = 0
        @Volatile var genericFailureStreak: Int = 0

        /** Called by SmsResultReceiver on a successful (RESULT_OK on last part) send. */
        fun recordSuccess() {
            failureStreak = 0
            genericFailureStreak = 0
        }

        /** Called by SmsResultReceiver on a failed send. */
        fun recordFailure(isGeneric: Boolean) {
            failureStreak++
            if (isGeneric) genericFailureStreak++
        }

        fun requestStop(context: Context) {
            try {
                context.stopService(Intent(context, SmsDispatchService::class.java))
            } catch (_: Throwable) { /* ignore */ }
        }
    }
}
