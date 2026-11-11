package bulksms.test.mark.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import bulksms.test.mark.BulkSmsApp
import bulksms.test.mark.R
import bulksms.test.mark.data.sim.SimRepository
import bulksms.test.mark.remote.GatewayClient
import bulksms.test.mark.remote.GatewayEventStore
import bulksms.test.mark.remote.GatewayPrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * A user-initiated gateway. Each leased SMS is stored BEFORE transmitting. Results are
 * persisted synchronously by GatewaySmsReceiver, then retried over HTTPS until acknowledged.
 * For unknown/missing sent callbacks the server does not retry automatically.
 *
 * Android limits background dataSync FGS execution; this service does not bypass that policy.
 */
class RemoteGatewayService : Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private lateinit var prefs: GatewayPrefs
    private lateinit var store: GatewayEventStore
    private var running=false
    private var lastHeartbeat=0L

    override fun onCreate() {
        super.onCreate()
        prefs=GatewayPrefs(this)
        store=GatewayEventStore(this)
        val manager=getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"Backoffice SMS gateway",
            NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        if(intent?.action==STOP) {
            prefs.enabled=false
            running=false
            stopSelf()
            return START_NOT_STICKY
        }
        if(!prefs.enabled || prefs.deviceToken.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        val notification=NotificationCompat.Builder(this,CHANNEL)
            .setContentTitle("Bulk SMS Kemer gateway")
            .setContentText("Connected to backoffice · Sending approved campaigns")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q) {
            startForeground(2249,notification,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else startForeground(2249,notification)
        if(!running) {
            running=true
            scope.launch { synchronize() }
        }
        return START_STICKY
    }
    private suspend fun synchronize() {
        val client=GatewayClient(prefs)
        while(scope.isActive && prefs.enabled && prefs.deviceToken.isNotBlank()) {
            try {
                val token=prefs.deviceToken
                // Always flush locally durable receipts before taking another job.
                for(row in store.pendingReports()) {
                    val state=row.optString("status")
                    val payload=JSONObject().put("id",row.getLong("id"))
                        .put("lease_uuid",row.getString("lease_uuid"))
                        .put("status",state)
                    if(row.has("code")) payload.put("sms_result_code",row.getInt("code"))
                    if(row.has("error")) payload.put("error",row.getString("error"))
                    val response=client.post("/api/v1/device/receipt",token,payload)
                    if(response.optBoolean("ok")) store.acknowledged(row.getLong("id"),row.getString("lease_uuid"),state)
                }
                store.clearOld()
                val battery=(getSystemService(BATTERY_SERVICE) as BatteryManager)
                    .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                val sim=SimRepository(this).listActive().firstOrNull { it.subscriptionId==prefs.simId }
                if(System.currentTimeMillis()-lastHeartbeat>30000L) {
                    client.post("/api/v1/device/heartbeat",token,JSONObject()
                        .put("battery",if(battery in 0..100) battery else JSONObject.NULL)
                        .put("sim_label",sim?.label ?: "SIM unavailable")
                        .put("app_version","1.1.0"))
                    lastHeartbeat=System.currentTimeMillis()
                }
                if(ContextCompat.checkSelfPermission(this,Manifest.permission.SEND_SMS)!=PackageManager.PERMISSION_GRANTED) {
                    lastStatus="SMS permission required"; delay(5000); continue
                }
                if(sim==null) { lastStatus="Selected SIM unavailable"; delay(5000); continue }
                if((applicationContext as BulkSmsApp).campaignRepo.activeCampaignId()!=null) {
                    lastStatus="Local campaign active; remote gateway paused"; delay(5000); continue
                }
                if(store.inFlight()) {
                    lastStatus="Awaiting carrier acknowledgment"; delay(900); continue
                }
                val response=client.post("/api/v1/device/claim",token,JSONObject())
                val job=response.optJSONObject("job")
                if(job==null) { lastStatus="Online · waiting for campaigns"; delay(4000); continue }
                lastStatus="Sending remote campaign #${job.optLong("campaign_id")}"
                transmit(job)
                delay(1000)
            } catch(c: CancellationException) { throw c }
              catch(e: Exception) {
                  lastStatus=(e.message ?: "Connection error").take(100)
                  delay(5000)
              }
        }
        running=false
    }
    private fun transmit(job: JSONObject) {
        val id=job.getLong("id")
        val lease=job.getString("lease_uuid")
        val body=job.getString("body")
        val number=job.getString("phone")
        val sms=SmsManager.getSmsManagerForSubscriptionId(prefs.simId)
        val parts=sms.divideMessage(body)
        if(parts.isEmpty()) return
        // Persist the dispatch before calling the modem.
        store.newJob(id,lease,parts.size)
        val sent=ArrayList<PendingIntent>()
        val delivered=ArrayList<PendingIntent>()
        for(i in parts.indices) {
            sent.add(pending(id,lease,"sent",i,parts.size))
            delivered.add(pending(id,lease,"delivered",i,parts.size))
        }
        try {
            sms.sendMultipartTextMessage(number,null,parts,sent,delivered)
        } catch(e:Exception) {
            store.record(id,lease,"sent",0,parts.size,1,false)
        }
    }
    private fun pending(id: Long,lease: String,type: String,index: Int,total: Int): PendingIntent {
        val intent=Intent(this,GatewaySmsReceiver::class.java).apply {
            action="bulksms.test.mark.REMOTE_SMS.${id}.${lease}.${type}.${index}"
            putExtra("job_id",id)
            putExtra("lease_uuid",lease)
            putExtra("event_type",type)
            putExtra("part_index",index)
            putExtra("parts_total",total)
        }
        return PendingIntent.getBroadcast(this,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    // Android 15 dataSync foreground-service time budget: stop promptly on platform timeout.
    override fun onTimeout(startId: Int, fgsType: Int) {
        prefs.enabled=false
        lastStatus="Android service time limit reached"
        stopSelf()
    }
    override fun onDestroy() {
        running=false
        scope.cancel()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder?=null
    companion object {
        const val STOP="bulksms.test.mark.REMOTE_STOP"
        const val CHANNEL="remote_gateway"
        @Volatile var lastStatus="Offline"
    }
}

