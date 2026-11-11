package bulksms.test.mark.service

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persist each segment callback: a successful last segment alone must never mean that
 * every segment succeeded. Missing/ambiguous callbacks remain UNKNOWN, not SENT.
 */
object SmsSegmentAckStore {
    enum class Outcome { WAIT, COMPLETE, FAILURE, AMBIGUOUS }
    private val lock=Any()
    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("local_sms_segments_v1",Context.MODE_PRIVATE)
    private fun key(id: Long,attempt: Int,type: String) = "$type.$id.$attempt"

    fun sent(context: Context,id: Long,attempt: Int,index: Int,total: Int,ok: Boolean): Outcome =
        synchronized(lock) {
            if(!ok) return@synchronized if(total>1) Outcome.AMBIGUOUS else Outcome.FAILURE
            val p=prefs(context)
            val k=key(id,attempt,"sent")
            val arr=runCatching { JSONArray(p.getString(k,"[]")) }.getOrDefault(JSONArray())
            if((0 until arr.length()).none { arr.optInt(it)==index }) arr.put(index)
            p.edit().putString(k,arr.toString()).commit()
            if(arr.length()>=total) Outcome.COMPLETE else Outcome.WAIT
        }
    fun delivered(context: Context,id: Long,index: Int,total: Int,ok: Boolean): Boolean =
        synchronized(lock) {
            if(!ok) return@synchronized false
            val p=prefs(context);val k=key(id,0,"delivered")
            val arr=runCatching { JSONArray(p.getString(k,"[]")) }.getOrDefault(JSONArray())
            if((0 until arr.length()).none { arr.optInt(it)==index }) arr.put(index)
            p.edit().putString(k,arr.toString()).commit()
            arr.length()>=total
        }
    fun deliveryComplete(context: Context,id: Long,total: Int): Boolean =
        synchronized(lock) {
            val a=runCatching { JSONArray(prefs(context).getString(key(id,0,"delivered"),"[]")) }
                .getOrDefault(JSONArray())
            a.length()>=total
        }
}

