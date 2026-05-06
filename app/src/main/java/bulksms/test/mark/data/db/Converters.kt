package bulksms.test.mark.data.db

import androidx.room.TypeConverter
import bulksms.test.mark.data.entity.CampaignStatus
import bulksms.test.mark.data.entity.RecipientStatus

class Converters {
    @TypeConverter
    fun fromCampaignStatus(s: CampaignStatus): String = s.name

    @TypeConverter
    fun toCampaignStatus(s: String): CampaignStatus = CampaignStatus.valueOf(s)

    @TypeConverter
    fun fromRecipientStatus(s: RecipientStatus): String = s.name

    @TypeConverter
    fun toRecipientStatus(s: String): RecipientStatus = RecipientStatus.valueOf(s)
}
