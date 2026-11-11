package bulksms.test.mark.ui.navigation

sealed class Screen(val route: String) {

    // Bottom-nav tabs
    data object Campaigns : Screen("campaigns")
    data object Contacts  : Screen("contacts")
    data object Templates : Screen("templates")
    data object Sent      : Screen("sent")
    data object Account   : Screen("account")

    // Pre-app gates
    data object Permission   : Screen("permission")
    data object Reliability  : Screen("reliability")

    // Sub-screens (with arguments)
    data object NewCampaign : Screen("campaigns/new") {
        fun route() = "campaigns/new"
    }

    data object RunCampaign : Screen("campaigns/{id}/run") {
        fun route(id: Long) = "campaigns/$id/run"
    }

    data object CampaignDetail : Screen("campaigns/{id}") {
        fun route(id: Long) = "campaigns/$id"
    }

    data object ContactGroupDetail : Screen("contacts/{id}") {
        fun route(id: Long) = "contacts/$id"
    }

    data object ImportContacts : Screen("contacts/{id}/import") {
        fun route(id: Long) = "contacts/$id/import"
    }

    data object TemplateEditor : Screen("templates/edit?id={id}") {
        fun route(id: Long? = null) = if (id == null) "templates/edit" else "templates/edit?id=$id"
    }
}
