package bulksms.test.mark.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import bulksms.test.mark.data.entity.Campaign
import bulksms.test.mark.data.entity.Contact
import bulksms.test.mark.data.entity.ContactGroup
import bulksms.test.mark.data.entity.MessageTemplate
import bulksms.test.mark.data.entity.Recipient

@Database(
    entities = [
        Campaign::class,
        Recipient::class,
        ContactGroup::class,
        Contact::class,
        MessageTemplate::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun campaignDao(): CampaignDao
    abstract fun recipientDao(): RecipientDao
    abstract fun contactGroupDao(): ContactGroupDao
    abstract fun contactDao(): ContactDao
    abstract fun templateDao(): TemplateDao

    companion object {
        const val DB_NAME = "bulk_sms_kemer.db"

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
                .fallbackToDestructiveMigration()
                .build()
    }
}
