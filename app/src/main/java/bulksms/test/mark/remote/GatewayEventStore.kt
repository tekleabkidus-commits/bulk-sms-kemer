package bulksms.test.mark.remote

import android.content.Context
import org.json.JSONObject
import org.json.JSONArray

/**
 * Stores receipt state before network reporting, so a callback survives service/process death.
 * A lost callback is kept as unknown on the backend; it is NEVER blindly retried.
 */
class GatewayEventStore(context: Context) {
    private val prefs=context.applicationContext.getSharedPreferences("gateway_receipts_v1",Context.MODE_PRIVATE)
    private val lock=Any()
    private fun key(id: Long) = "job_$id"

    fun newJob(id: Long, lease: String, parts: Int) = synchronized(lock) {
        val value=JSONObject().put("id",id).put("lease_uuid",lease)
            .put("total",parts).put("sent",JSONArray()).put("delivered",JSONArray())
            .put("status","inflight").put("dirty",false).put("since",System.currentTimeMillis())
        prefs.edit().putString(key(id),value.toString()).commit()
    }
    fun record(id: Long,lease: String,type: String,index: Int,total: Int,code: Int,successful: Boolean) = synchronized(lock) {
        val raw=prefs.getString(key(id),null) ?: return@synchronized
        val v=JSONObject(raw)
        if(v.optString("lease_uuid")!=lease) return@synchronized
        if(type=="sent" && !successful) {
            // Some multipart parts might have sent before a later part failed.
            val partial=v.optJSONArray("sent")?.length() ?: 0
            v.put("status",if(partial>0)"unknown" else "failed")
            v.put("error",if(partial>0)"Partial multipart failure" else "Modem send error")
            v.put("code",code).put("dirty",true)
        } else if(successful) {
            val field=if(type=="delivered") "delivered" else "sent"
            val arr=v.optJSONArray(field) ?: JSONArray()
            if((0 until arr.length()).none { arr.optInt(it)==index }) arr.put(index)
            v.put(field,arr)
            if(type=="delivered" && arr.length()>=total) {
                v.put("status","delivered").put("dirty",true)
            } else if(type=="sent" && v.optString("status")=="inflight" && arr.length()>=total) {
                v.put("status","submitted").put("dirty",true)
            }
        }
        prefs.edit().putString(key(id),v.toString()).commit()
    }
    fun inFlight(): Boolean = synchronized(lock) {
        prefs.all.values.any { value ->
            val v=runCatching { JSONObject(value as String) }.getOrNull()
            v?.optString("status")=="inflight" && System.currentTimeMillis()-v.optLong("since")<120000L
        }
    }
    fun pendingReports(): List<JSONObject> = synchronized(lock) {
        prefs.all.values.mapNotNull { value ->
            val v=runCatching { JSONObject(value as String) }.getOrNull()
            v?.takeIf { it.optBoolean("dirty") && it.optString("status")!="inflight" }
        }
    }
    fun acknowledged(id: Long,lease: String,status: String) = synchronized(lock) {
        val raw=prefs.getString(key(id),null) ?: return@synchronized
        val v=JSONObject(raw)
        if(v.optString("lease_uuid")!=lease || v.optString("status")!=status) return@synchronized
        v.put("dirty",false)
        prefs.edit().putString(key(id),v.toString()).commit()
    }
    fun clearOld() = synchronized(lock) {
        val cutoff=System.currentTimeMillis()-7L*24*60*60*1000
        val editor=prefs.edit()
        for((k,value) in prefs.all) {
            val v=runCatching { JSONObject(value as String) }.getOrNull()
            if(v!=null && v.optLong("since")<cutoff && !v.optBoolean("dirty")) editor.remove(k)
        }
        editor.apply()
    }
}

