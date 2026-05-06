package bulksms.test.mark.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import bulksms.test.mark.ui.campaigns.CampaignDetailScreen
import bulksms.test.mark.ui.campaigns.CampaignsScreen
import bulksms.test.mark.ui.campaigns.NewCampaignScreen
import bulksms.test.mark.ui.campaigns.RunCampaignScreen
import bulksms.test.mark.ui.contacts.ContactGroupDetailScreen
import bulksms.test.mark.ui.contacts.ContactGroupsScreen
import bulksms.test.mark.ui.contacts.ImportContactsScreen
import bulksms.test.mark.ui.permissions.PermissionScreen
import bulksms.test.mark.ui.reliability.ReliabilityScreen
import bulksms.test.mark.ui.sent.SentMessagesScreen
import bulksms.test.mark.ui.templates.TemplateEditorScreen
import bulksms.test.mark.ui.templates.TemplatesScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String,
    onTabsVisibleChange: (Boolean) -> Unit
) {
    NavHost(navController = navController, startDestination = startDestination) {

        composable(Screen.Permission.route) {
            onTabsVisibleChange(false)
            PermissionScreen(
                onContinue = {
                    navController.navigate(Screen.Reliability.route) {
                        popUpTo(Screen.Permission.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Reliability.route) {
            onTabsVisibleChange(false)
            ReliabilityScreen(
                onDone = {
                    navController.navigate(Screen.Campaigns.route) {
                        popUpTo(Screen.Reliability.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Campaigns.route) {
            onTabsVisibleChange(true)
            CampaignsScreen(
                onNewCampaign = { navController.navigate(Screen.NewCampaign.route()) },
                onOpenCampaign = { id -> navController.navigate(Screen.CampaignDetail.route(id)) },
                onResumeCampaign = { id -> navController.navigate(Screen.RunCampaign.route(id)) }
            )
        }

        composable(Screen.NewCampaign.route) {
            onTabsVisibleChange(false)
            NewCampaignScreen(
                onCancel = { navController.popBackStack() },
                onStarted = { id ->
                    navController.navigate(Screen.RunCampaign.route(id)) {
                        popUpTo(Screen.Campaigns.route) { inclusive = false }
                    }
                }
            )
        }

        composable(
            Screen.RunCampaign.route,
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            onTabsVisibleChange(false)
            val id = entry.arguments?.getLong("id") ?: -1L
            RunCampaignScreen(campaignId = id, onBack = { navController.popBackStack() })
        }

        composable(
            Screen.CampaignDetail.route,
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            onTabsVisibleChange(false)
            val id = entry.arguments?.getLong("id") ?: -1L
            CampaignDetailScreen(
                campaignId = id,
                onBack = { navController.popBackStack() },
                onResume = { navController.navigate(Screen.RunCampaign.route(id)) }
            )
        }

        composable(Screen.Contacts.route) {
            onTabsVisibleChange(true)
            ContactGroupsScreen(
                onOpenGroup = { id -> navController.navigate(Screen.ContactGroupDetail.route(id)) }
            )
        }

        composable(
            Screen.ContactGroupDetail.route,
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            onTabsVisibleChange(false)
            val id = entry.arguments?.getLong("id") ?: -1L
            ContactGroupDetailScreen(
                groupId = id,
                onBack = { navController.popBackStack() },
                onImport = { navController.navigate(Screen.ImportContacts.route(id)) }
            )
        }

        composable(
            Screen.ImportContacts.route,
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            onTabsVisibleChange(false)
            val id = entry.arguments?.getLong("id") ?: -1L
            ImportContactsScreen(groupId = id, onDone = { navController.popBackStack() })
        }

        composable(Screen.Templates.route) {
            onTabsVisibleChange(true)
            TemplatesScreen(
                onNew = { navController.navigate(Screen.TemplateEditor.route(null)) },
                onEdit = { id -> navController.navigate(Screen.TemplateEditor.route(id)) }
            )
        }

        composable(
            Screen.TemplateEditor.route,
            arguments = listOf(navArgument("id") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { entry ->
            onTabsVisibleChange(false)
            val id = entry.arguments?.getString("id")?.toLongOrNull()
            TemplateEditorScreen(
                templateId = id,
                onDone = { navController.popBackStack() }
            )
        }

        composable(Screen.Sent.route) {
            onTabsVisibleChange(true)
            SentMessagesScreen()
        }
    }
}
