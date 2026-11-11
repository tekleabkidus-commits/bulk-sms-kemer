package bulksms.test.mark.ui.account

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import bulksms.test.mark.data.sim.SimRepository
import bulksms.test.mark.remote.GatewayClient
import bulksms.test.mark.remote.GatewayPrefs
import bulksms.test.mark.service.RemoteGatewayService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen() {
    val ctx=LocalContext.current
    val prefs=remember { GatewayPrefs(ctx) }
    val client=remember { GatewayClient(prefs) }
    val scope=rememberCoroutineScope()
    var url by remember { mutableStateOf(prefs.serverUrl) }
    var email by remember { mutableStateOf(prefs.email) }
    var password by remember { mutableStateOf("") }
    var workspaces by remember { mutableStateOf(emptyList<JSONObject>()) }
    var selected by remember { mutableStateOf(0L) }
    val sims=remember { SimRepository(ctx).listActive() }
    var selectedSim by remember { mutableIntStateOf(if(prefs.simId>=0) prefs.simId else sims.firstOrNull()?.subscriptionId ?: -1) }
    var connected by remember { mutableStateOf(prefs.userToken.isNotBlank()) }
    var linked by remember { mutableStateOf(prefs.deviceToken.isNotBlank()) }
    var enabled by remember { mutableStateOf(prefs.enabled) }
    var speed by remember { mutableIntStateOf(prefs.maxInFlight) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(RemoteGatewayService.lastStatus) }
    LaunchedEffect(Unit) {
        while(true) { status=RemoteGatewayService.lastStatus; delay(1500) }
    }
    LaunchedEffect(connected) {
        if(connected) {
            runCatching { client.get("/api/v1/app/me",prefs.userToken) }
                .onSuccess { r ->
                    val arr=r.optJSONArray("workspaces")
                    workspaces=(0 until (arr?.length() ?: 0)).mapNotNull { arr?.optJSONObject(it) }
                    if(selected==0L) selected=workspaces.firstOrNull()?.optLong("id") ?: 0L
                }.onFailure { error=it.message ?: "Unable to load workspaces" }
        }
    }
    Scaffold(topBar={ TopAppBar(title={Text("Backoffice account")}) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(17.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Connect your Android SIM to your Laravel backoffice account.",style=MaterialTheme.typography.bodyMedium)
            if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
            if(!connected) {
                OutlinedTextField(url,{url=it},label={Text("Backoffice HTTPS URL")},placeholder={Text("https://sms.example.com")},modifier=Modifier.fillMaxWidth())
                OutlinedTextField(email,{email=it},label={Text("Email")},modifier=Modifier.fillMaxWidth())
                OutlinedTextField(password,{password=it},label={Text("Password")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
                Button(enabled=!loading,onClick={
                    scope.launch {
                        loading=true; error=""
                        try {
                            prefs.serverUrl=url
                            val r=client.post("/api/v1/app/login","",JSONObject().put("email",email).put("password",password))
                            prefs.userToken=r.getString("token"); prefs.email=email; password=""
                            connected=true
                        } catch(e:Exception) { error=e.message ?: "Sign-in failed" }
                        finally { loading=false }
                    }
                }) { Text("Sign in") }
                Text("Create your personal account or company in the web backoffice first.")
            } else {
                Text("Signed in: ${prefs.email}",style=MaterialTheme.typography.titleMedium)
                if(!linked) {
                    Text("Choose the account/workspace that owns this phone.")
                    workspaces.forEach { w ->
                        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                            RadioButton(selected=selected==w.optLong("id"),onClick={selected=w.optLong("id")})
                            Text("${w.optString("name")} · ${w.optString("role")}")
                        }
                    }
                    if(sims.isEmpty()) Text("No SIM detected. Grant phone state permission and insert a SIM.")
                    sims.forEach { sim ->
                        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                            RadioButton(selected=selectedSim==sim.subscriptionId,onClick={selectedSim=sim.subscriptionId})
                            Text(sim.label)
                        }
                    }
                    Button(enabled=!loading && selected>0 && selectedSim>=0,onClick={
                        scope.launch {
                            loading=true; error=""
                            try {
                                val sim=sims.first { it.subscriptionId==selectedSim }
                                val r=client.post("/api/v1/app/devices/link",prefs.userToken,JSONObject()
                                    .put("workspace_id",selected).put("name",Build.MANUFACTURER+" "+Build.MODEL)
                                    .put("sim_label",sim.label))
                                prefs.deviceToken=r.getString("device_token")
                                prefs.deviceId=r.getLong("device_id")
                                prefs.simId=selectedSim
                                linked=true
                            } catch(e:Exception) { error=e.message ?: "Link failed" }
                            finally { loading=false }
                        }
                    }) { Text("Link this Android phone") }
                } else {
                    Text("Device #${prefs.deviceId} linked securely",style=MaterialTheme.typography.titleMedium)
                    Text("Selected SIM: ${sims.firstOrNull { it.subscriptionId==prefs.simId }?.label ?: "Unavailable"}")
                    Text("Gateway: $status",style=MaterialTheme.typography.bodyMedium)
                    Text("Remote SMS sending is user-initiated. Android/carrier background limits still apply.")
                    Text("Sending capacity: $speed in flight (lower is safer)")
                    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        listOf(1,2,3).forEach { level ->
                            OutlinedButton(enabled=!enabled,onClick={speed=level;prefs.maxInFlight=level}) {
                                Text(when(level){1->"Safe";2->"Balanced";else->"Faster"})
                            }
                        }
                    }
                    if(speed>1) Text("Higher speed increases carrier rejection risk. Test on your SIM first.",
                        color=MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Button(enabled=!enabled,onClick={
                            error=""
                            try {
                                prefs.enabled=true
                                ContextCompat.startForegroundService(ctx,Intent(ctx,RemoteGatewayService::class.java))
                                enabled=true
                            } catch(e:Exception) { prefs.enabled=false;error=e.message ?: "Cannot start gateway" }
                        }) { Text("Start gateway") }
                        OutlinedButton(enabled=enabled,onClick={
                            prefs.enabled=false; enabled=false
                            ctx.startService(Intent(ctx,RemoteGatewayService::class.java).setAction(RemoteGatewayService.STOP))
                        }) { Text("Stop") }
                    }
                    OutlinedButton(enabled=!loading,onClick={
                        scope.launch {
                            loading=true; error=""
                            try {
                                client.post("/api/v1/app/devices/unlink",prefs.userToken,JSONObject().put("device_id",prefs.deviceId))
                                prefs.enabled=false
                                ctx.startService(Intent(ctx,RemoteGatewayService::class.java).setAction(RemoteGatewayService.STOP))
                                prefs.deviceToken=""; prefs.deviceId=0;linked=false;enabled=false
                            } catch(e:Exception) { error=e.message ?: "Could not unlink. Revoke from backoffice." }
                            finally { loading=false }
                        }
                    }) { Text("Unlink device") }
                }
                TextButton(enabled=!loading,onClick={
                    scope.launch {
                        loading=true;error=""
                        try {
                            // Stop locally first. Revoke linked device and login session on the server.
                            prefs.enabled=false
                            ctx.startService(Intent(ctx,RemoteGatewayService::class.java).setAction(RemoteGatewayService.STOP))
                            if(prefs.deviceId>0) client.post("/api/v1/app/devices/unlink",prefs.userToken,JSONObject().put("device_id",prefs.deviceId))
                            client.post("/api/v1/app/logout",prefs.userToken,JSONObject())
                            prefs.clearCredentials();connected=false;linked=false;enabled=false;workspaces=emptyList()
                        } catch(e:Exception) { error="Logout not complete: ${e.message}. Revoke device from backoffice if offline." }
                        finally { loading=false }
                    }
                }) { Text("Log out and revoke device") }
            }
        }
    }
}

