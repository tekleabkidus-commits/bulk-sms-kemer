package bulksms.test.mark

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.PeopleAlt
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import bulksms.test.mark.ui.navigation.AppNavHost
import bulksms.test.mark.ui.navigation.Screen
import bulksms.test.mark.ui.theme.BulkSmsTheme
import bulksms.test.mark.util.PermissionUtil

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BulkSmsTheme { App() } }
    }

    @Composable
    private fun App() {
        val nav = rememberNavController()
        val ctx = LocalContext.current
        var tabsVisible by remember { mutableStateOf(true) }

        // Decide initial destination from permission state.
        val start = remember {
            when {
                !PermissionUtil.hasSendSms(ctx) ||
                    !PermissionUtil.hasReadPhoneState(ctx) ||
                    !PermissionUtil.hasNotifications(ctx) -> Screen.Permission.route
                !PermissionUtil.isIgnoringBatteryOptimization(ctx) -> Screen.Permission.route
                else -> Screen.Campaigns.route
            }
        }

        Scaffold(
            bottomBar = {
                if (tabsVisible) BottomNav(nav)
            }
        ) { padding ->
            androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
                AppNavHost(
                    navController = nav,
                    startDestination = start,
                    onTabsVisibleChange = { tabsVisible = it }
                )
            }
        }

        // Handle deep-link from notification
        LaunchedEffect(Unit) { handleIntent(intent, nav) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
    }

    private fun handleIntent(intent: Intent?, nav: androidx.navigation.NavHostController) {
        if (intent == null) return
        if (intent.action == ACTION_OPEN_CAMPAIGN) {
            val id = intent.getLongExtra(EXTRA_CAMPAIGN_ID, -1L)
            if (id > 0) nav.navigate(Screen.CampaignDetail.route(id))
        }
    }

    @Composable
    private fun BottomNav(nav: androidx.navigation.NavHostController) {
        val backStack by nav.currentBackStackEntryAsState()
        val currentDest = backStack?.destination
        val items = listOf(
            BottomItem(Screen.Campaigns.route, "Campaigns", Icons.Outlined.Campaign),
            BottomItem(Screen.Contacts.route,  "Contacts",  Icons.Outlined.PeopleAlt),
            BottomItem(Screen.Templates.route, "Templates", Icons.Outlined.Notes),
            BottomItem(Screen.Sent.route,      "Sent",      Icons.Outlined.History),
            BottomItem(Screen.Account.route,   "Account",   Icons.Outlined.AccountCircle)
        )
        NavigationBar {
            items.forEach { item ->
                val selected = currentDest?.hierarchy?.any { it.route == item.route } == true
                NavigationBarItem(
                    selected = selected,
                    onClick = {
                        nav.navigate(item.route) {
                            popUpTo(nav.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(item.icon, contentDescription = item.label) },
                    label = { Text(item.label) }
                )
            }
        }
    }

    private data class BottomItem(
        val route: String,
        val label: String,
        val icon: androidx.compose.ui.graphics.vector.ImageVector
    )

    companion object {
        const val ACTION_OPEN_CAMPAIGN = "bulksms.test.mark.OPEN_CAMPAIGN"
        const val EXTRA_CAMPAIGN_ID = "campaign_id"
    }
}
