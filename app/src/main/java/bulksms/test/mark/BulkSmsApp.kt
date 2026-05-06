package bulksms.test.mark

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import bulksms.test.mark.data.db.AppDatabase
import bulksms.test.mark.data.repo.CampaignRepository
import bulksms.test.mark.data.repo.ContactRepository
import bulksms.test.mark.data.repo.TemplateRepository
import bulksms.test.mark.service.DispatchNotifications

class BulkSmsApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.create(this) }
    val campaignRepo: CampaignRepository by lazy {
        CampaignRepository(database.campaignDao(), database.recipientDao(), database.contactDao())
            .apply { attachDb(database) }
    }
    val contactRepo: ContactRepository by lazy {
        ContactRepository(database.contactGroupDao(), database.contactDao())
    }
    val templateRepo: TemplateRepository by lazy {
        TemplateRepository(database.templateDao())
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val progress = NotificationChannel(
            DispatchNotifications.CHANNEL_PROGRESS,
            getString(R.string.channel_progress_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.channel_progress_desc)
            setShowBadge(false)
            enableVibration(false)
        }

        val alerts = NotificationChannel(
            DispatchNotifications.CHANNEL_ALERTS,
            getString(R.string.channel_alerts_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.channel_alerts_desc)
            setShowBadge(true)
            enableVibration(true)
        }

        nm.createNotificationChannels(listOf(progress, alerts))
    }

    companion object {
        @Volatile
        private var instance: BulkSmsApp? = null

        fun get(): BulkSmsApp =
            instance ?: error("BulkSmsApp not initialized — must be set as android:name in manifest")
    }
}
