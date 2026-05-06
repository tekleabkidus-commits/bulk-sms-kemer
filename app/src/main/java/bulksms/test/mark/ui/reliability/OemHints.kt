package bulksms.test.mark.ui.reliability

object OemHints {

    data class Tips(val label: String, val steps: List<String>)

    fun tipsFor(manufacturer: String): Tips {
        val m = manufacturer.lowercase()
        return when {
            m.contains("samsung") -> Tips(
                "Samsung (One UI)",
                listOf(
                    "Settings → Apps → Bulk SMS Kemer → Battery → Unrestricted",
                    "Settings → Battery and device care → Background usage limits → Never sleeping apps → add Bulk SMS Kemer",
                    "Settings → Apps → Bulk SMS Kemer → Battery → Allow background activity",
                    "Settings → Battery and device care → More battery settings → Adaptive battery → Off",
                    "Developer options → Don't keep activities → Off"
                )
            )
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> Tips(
                "Xiaomi / Redmi (MIUI / HyperOS)",
                listOf(
                    "Settings → Apps → Manage apps → Bulk SMS Kemer → Autostart → On",
                    "Settings → Apps → Manage apps → Bulk SMS Kemer → Battery saver → No restrictions",
                    "Settings → Battery → App battery saver → Bulk SMS Kemer → No restrictions",
                    "Recents (multitask) view → long-press the app card → lock icon"
                )
            )
            m.contains("oppo") || m.contains("realme") -> Tips(
                "Oppo / Realme (ColorOS)",
                listOf(
                    "Settings → Battery → App battery management → Bulk SMS Kemer → Allow background activity → On",
                    "Settings → App management → Auto-launch → Bulk SMS Kemer → On",
                    "Recents view → lock the app card"
                )
            )
            m.contains("vivo") || m.contains("iqoo") -> Tips(
                "Vivo / iQOO (Funtouch / OriginOS)",
                listOf(
                    "Settings → Battery → Background power consumption management → Bulk SMS Kemer → Allow",
                    "Settings → More settings → Permission management → Autostart → enable",
                    "i Manager → App manager → Autostart manager → enable"
                )
            )
            m.contains("oneplus") -> Tips(
                "OnePlus (OxygenOS)",
                listOf(
                    "Settings → Battery → Battery optimization → Bulk SMS Kemer → Don't optimize",
                    "Settings → Apps → Bulk SMS Kemer → Battery → Background battery usage → Allow",
                    "Recents view → lock the app card"
                )
            )
            m.contains("huawei") || m.contains("honor") -> Tips(
                "Huawei / Honor (EMUI / MagicOS)",
                listOf(
                    "Settings → Apps → Bulk SMS Kemer → Battery → App launch → Manage manually → enable Auto-launch, Secondary launch, Run in background",
                    "Phone Manager → Protected apps → Bulk SMS Kemer → On"
                )
            )
            else -> Tips(
                "your phone",
                listOf(
                    "Settings → Apps → Bulk SMS Kemer → Battery → Unrestricted (or 'Don't optimize')",
                    "Settings → Battery → Allow this app to run in the background",
                    "Recents view → lock the app card if your phone supports it",
                    "Disable any 'adaptive battery' or 'auto-sleeping apps' feature for this app"
                )
            )
        }
    }
}
