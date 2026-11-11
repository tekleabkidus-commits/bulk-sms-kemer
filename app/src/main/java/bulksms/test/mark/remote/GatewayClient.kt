package bulksms.test.mark.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Small HTTPS JSON client; no cookies and no token logging. */
class GatewayClient(private val prefs: GatewayPrefs) {
    suspend fun post(path: String, token: String, data: JSONObject): JSONObject =
        request(path, "POST", token, data)
    suspend fun get(path: String, token: String): JSONObject = request(path,"GET",token,null)

    private suspend fun request(path: String, method: String, token: String, data: JSONObject?): JSONObject =
        withContext(Dispatchers.IO) {
            val base=prefs.serverUrl
            require(base.startsWith("https://",ignoreCase=true)) { "Enter an HTTPS backoffice URL" }
            val url=URL(base+path)
            require(url.protocol=="https") { "HTTPS is required" }
            val c=(url.openConnection() as HttpURLConnection).apply {
                requestMethod=method
                connectTimeout=12000
                readTimeout=15000
                instanceFollowRedirects=false
                setRequestProperty("Accept","application/json")
                setRequestProperty("Authorization","Bearer $token")
                if(data!=null) {
                    doOutput=true
                    setRequestProperty("Content-Type","application/json")
                }
            }
            try {
                if(data!=null) c.outputStream.use { it.write(data.toString().toByteArray(Charsets.UTF_8)) }
                val status=c.responseCode
                val stream=if(status in 200..299) c.inputStream else c.errorStream
                val body=stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if(status !in 200..299) {
                    val message=runCatching { JSONObject(body).optString("message") }.getOrNull()
                    throw IllegalStateException(message?.takeIf { it.isNotBlank() } ?: "Server error HTTP $status")
                }
                if(body.isBlank()) JSONObject() else JSONObject(body)
            } finally { c.disconnect() }
        }
}

