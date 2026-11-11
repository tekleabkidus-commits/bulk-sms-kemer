package bulksms.test.mark.service

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import bulksms.test.mark.remote.GatewayEventStore

/** Manifest registered to receive telephony callbacks even when the service is recreated. */
class GatewaySmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id=intent.getLongExtra("job_id",-1L)
        val lease=intent.getStringExtra("lease_uuid") ?: return
        if(id<=0) return
        val type=intent.getStringExtra("event_type") ?: return
        val index=intent.getIntExtra("part_index",0)
        val total=intent.getIntExtra("parts_total",1)
        GatewayEventStore(context).record(id,lease,type,index,total,resultCode,resultCode==Activity.RESULT_OK)
    }
}

