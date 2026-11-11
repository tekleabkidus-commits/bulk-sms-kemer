package bulksms.test.mark.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import bulksms.test.mark.MainActivity
import bulksms.test.mark.R
import bulksms.test.mark.data.entity.PauseReason

object DispatchNotifications {

    const val CHANNEL_PROGRESS = "campaign_progress"
    const val CHANNEL_ALERTS = "campaign_alerts"

    const val NOTIF_ID_PROGRESS = 1001
    private var alertsCounter = 2000

    fun progress(
        context: Context,
        campaignId: Long,
        campaignName: String,
        sent: Int,
        total: Int,
        ratePerMin: Int,
        paused: Boolean,
        pauseReasonText: String?
    ): Notification {
        val text = if (paused && pauseReasonText != null)
            "Paused — $pauseReasonText · $sent of $total"
        else
            "$sent of $total · $ratePerMin / min"

        val openIntent = openCampaignPendingIntent(context, campaignId)
        val pauseIntent = PendingIntent.getBroadcast(
            context, 100,
            Intent(context, ServiceCommandReceiver::class.java).apply {
                action = ServiceCommandReceiver.ACTION_PAUSE
                putExtra(ServiceCommandReceiver.EXTRA_CAMPAIGN_ID, campaignId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val resumeIntent = PendingIntent.getBroadcast(
            context, 101,
            Intent(context, ServiceCommandReceiver::class.java).apply {
                action = ServiceCommandReceiver.ACTION_RESUME
                putExtra(ServiceCommandReceiver.EXTRA_CAMPAIGN_ID, campaignId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(campaignName)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setProgress(total.coerceAtLeast(1), sent, false)
            .setContentIntent(openIntent)

        if (paused) {
            builder.addAction(0, context.getString(R.string.action_resume), resumeIntent)
        } else {
            builder.addAction(0, context.getString(R.string.action_pause), pauseIntent)
        }
        builder.addAction(0, context.getString(R.string.action_open), openIntent)
        return builder.build()
    }

    fun postAlert(context: Context, title: String, text: String, campaignId: Long?) {
        val nm = NotificationManagerCompat.from(context)
        val open = if (campaignId != null) openCampaignPendingIntent(context, campaignId) else openMainPendingIntent(context)
        val n = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            nm.notify(nextAlertId(), n)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted on Android 13+; ignore.
        }
    }

    fun pauseReasonText(reason: PauseReason, detail: String?): String = when (reason) {
        PauseReason.USER -> "Paused by you"
        PauseReason.NO_SERVICE -> "No cellular service"
        PauseReason.RATE_LIMIT -> "Carrier rate limit"
        PauseReason.DAILY_CAP -> "Daily cap reached"
        PauseReason.INSTABILITY -> "3 consecutive failures"
        PauseReason.BALANCE_SUSPECTED -> "Check SIM balance"
        PauseReason.PHONE_CALL -> "Phone call in progress"
        PauseReason.BATTERY_CRITICAL -> "Battery critical — plug in"
        PauseReason.THERMAL -> "Phone is overheating"
        PauseReason.SIM_REMOVED -> "SIM removed"
        PauseReason.PERMISSION_REVOKED -> "SMS permission revoked"
        PauseReason.STORAGE_FULL -> "Storage full"
        PauseReason.ROAMING -> "SIM is roaming"
    }.let { base -> if (detail.isNullOrBlank()) base else "$base — $detail" }

    private fun openCampaignPendingIntent(context: Context, campaignId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_CAMPAIGN
            putExtra(MainActivity.EXTRA_CAMPAIGN_ID, campaignId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, campaignId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openMainPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    @Synchronized
    private fun nextAlertId(): Int {
        if (alertsCounter > 9_000_000) alertsCounter = 2000
        return ++alertsCounter
    }
}
