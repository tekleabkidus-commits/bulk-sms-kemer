package bulksms.test.mark.ui.campaigns

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import bulksms.test.mark.BulkSmsApp

class CampaignsViewModel(app: Application) : AndroidViewModel(app) {
    private val a = app as BulkSmsApp
    val rows = a.campaignRepo.observeAllRows()
}
