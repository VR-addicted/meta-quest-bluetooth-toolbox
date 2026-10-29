package com.quest.systemsettings.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import com.quest.systemsettings.adb.AdbClient
import com.quest.systemsettings.localization.LanguageManager

object SettingsLauncher {

    private fun showToast(context: Context, textEn: String, textDe: String) {
        val msg = if (LanguageManager.isGerman) textDe else textEn
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    /**
     * Launches the "Android settings" app on the Quest glasses/headset.
     * Implements multi-tier detection:
     * 1. Dynamic scan of installed packages for an app with label "Android settings" / "Android Settings"
     * 2. Direct package launch intent for "com.android.settings"
     * 3. Known SideQuest / HorizonOS Settings shortcut and wrapper APKs
     * 4. Classic AOSP Settings components (Settings, MainSettings, DeepLinkHomepageActivity)
     * 5. ADB shell intent execution fallback if direct execution is blocked
     */
    suspend fun openAndroidSettingsApp(context: Context, adbClient: AdbClient? = null): Boolean {
        val pm = context.packageManager

        // Tier 1: Look for any installed app named "Android settings" or "Android Settings"
        try {
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            // Priority 1A: Exact case-insensitive match
            for (app in installedApps) {
                if (app.packageName == context.packageName) continue
                val label = pm.getApplicationLabel(app).toString().trim()
                if (label.equals("Android settings", ignoreCase = true) ||
                    label.equals("Android Settings", ignoreCase = true) ||
                    label.equals("Android-Einstellungen", ignoreCase = true)
                ) {
                    val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            context.startActivity(launchIntent)
                            showToast(context, "Opened: $label", "Geöffnet: $label")
                            return true
                        } catch (_: Exception) {}
                    }
                }
            }

            // Priority 1B: App label containing both "Android" and "Settings"
            for (app in installedApps) {
                if (app.packageName == context.packageName) continue
                val label = pm.getApplicationLabel(app).toString().trim()
                if (label.contains("Android", ignoreCase = true) && label.contains("Settings", ignoreCase = true)) {
                    val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            context.startActivity(launchIntent)
                            showToast(context, "Opened: $label", "Geöffnet: $label")
                            return true
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (_: Exception) {}

        // Tier 2: Try launch intent for "com.android.settings" directly
        try {
            val launchIntent = pm.getLaunchIntentForPackage("com.android.settings")
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                showToast(context, "Android Settings opened", "Android Settings geöffnet")
                return true
            }
        } catch (_: Exception) {}

        // Tier 3: Known Quest shortcut & wrapper APKs
        val wrapperPackages = listOf(
            "arpruss.gotosettings",
            "com.threethan.settings",
            "com.threethan.QuestHiddenSettings",
            "com.anagan79.xrnativeandroidsettings",
            "com.pawankv.settings",
            "com.quickshortcutmaker"
        )
        for (pkg in wrapperPackages) {
            try {
                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    showToast(context, "Android Settings opened", "Android Settings geöffnet")
                    return true
                }
            } catch (_: Exception) {}
        }

        // Tier 4: Direct component & intent fallbacks
        val directIntents = listOf(
            Intent().apply {
                component = ComponentName("com.android.settings", "com.android.settings.Settings")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            Intent().apply {
                component = ComponentName("com.android.settings", "com.android.settings.MainSettings")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            Intent().apply {
                component = ComponentName("com.android.settings", "com.android.settings.homepage.DeepLinkHomepageActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage("com.android.settings")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
        for (intent in directIntents) {
            try {
                context.startActivity(intent)
                showToast(context, "Android Settings opened", "Android Settings geöffnet")
                return true
            } catch (_: Exception) {}
        }

        // Tier 5: ADB shell execution if direct startActivity is blocked
        if (adbClient != null) {
            try {
                val res = adbClient.executeCommand("am start -n com.android.settings/.Settings")
                if (!res.contains("Error", ignoreCase = true) && !res.contains("does not exist", ignoreCase = true)) {
                    showToast(context, "Android Settings opened via ADB", "Android Settings über ADB geöffnet")
                    return true
                }
                val res2 = adbClient.executeCommand("am start -a android.settings.SETTINGS")
                if (!res2.contains("Error", ignoreCase = true)) {
                    showToast(context, "Android Settings opened via ADB", "Android Settings über ADB geöffnet")
                    return true
                }
            } catch (_: Exception) {}
        }

        showToast(
            context,
            "Could not launch 'Android settings' app. Please verify it is installed.",
            "Konnte 'Android settings' App nicht starten. Bitte prüfen, ob sie installiert ist."
        )
        return false
    }

    /**
     * AOSP Phone Settings (Spa / Special Access).
     * Opens com.android.settings/.spa.SpaActivity (the Android 14 Settings Platform Architecture).
     */
    fun openAospPhoneSettings(context: Context): Boolean {
        val intents = listOf(
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            Intent("android.settings.NOTIFICATION_LISTENER_SETTINGS").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )

        for (intent in intents) {
            try {
                context.startActivity(intent)
                return true
            } catch (_: Exception) {}
        }
        showToast(context, "Could not open phone settings", "Konnte Phone-Settings nicht öffnen")
        return false
    }

    /**
     * AOSP Network & Connectivity Dashboard (Settings$NetworkDashboardActivity)
     */
    fun openNetworkDashboard(context: Context): Boolean {
        val intent = Intent().apply {
            component = ComponentName("com.android.settings", "com.android.settings.Settings\$NetworkDashboardActivity")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            showToast(context, "Network dashboard not reachable", "Netzwerk-Dashboard nicht erreichbar")
            false
        }
    }

    /**
     * AOSP Display Settings (Settings$DisplaySettingsActivity)
     */
    fun openDisplaySettings(context: Context): Boolean {
        val intent = Intent().apply {
            component = ComponentName("com.android.settings", "com.android.settings.Settings\$DisplaySettingsActivity")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            showToast(context, "Display settings not reachable", "Display-Einstellungen nicht erreichbar")
            false
        }
    }

    /**
     * AOSP Sound Settings (Settings$SoundSettingsActivity)
     */
    fun openSoundSettings(context: Context): Boolean {
        val intent = Intent().apply {
            component = ComponentName("com.android.settings", "com.android.settings.Settings\$SoundSettingsActivity")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            showToast(context, "Sound settings not reachable", "Sound-Einstellungen nicht erreichbar")
            false
        }
    }

    /**
     * Opens Root Android Settings Table (DeepLinkHomepageActivity)
     */
    fun openFullSettings(context: Context): Boolean {
        val intents = listOf(
            Intent().apply {
                component = ComponentName("com.android.settings", "com.android.settings.homepage.DeepLinkHomepageActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            Intent("android.settings.SETTINGS_EMBED_DEEP_LINK_ACTIVITY").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )

        for (intent in intents) {
            try {
                context.startActivity(intent)
                return true
            } catch (_: Exception) {}
        }
        showToast(context, "Could not open root settings", "Konnte Root-Einstellungen nicht öffnen")
        return false
    }

    /**
     * Opens AOSP Connected Devices & Bluetooth pairing table
     */
    fun openBluetoothSettings(context: Context): Boolean {
        val intents = listOf(
            Intent().apply {
                component = ComponentName("com.android.settings", "com.android.settings.Settings\$ConnectedDeviceDashboardActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            Intent().apply {
                component = ComponentName("com.android.settings", "com.android.settings.Settings\$BluetoothSettingsActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )

        for (intent in intents) {
            try {
                context.startActivity(intent)
                return true
            } catch (_: Exception) {}
        }
        showToast(context, "Could not open Bluetooth menu", "Konnte Bluetooth-Menü nicht öffnen")
        return false
    }

    /**
     * Opens Meta VR Native Bluetooth Panel directly
     */
    fun openMetaVrBluetoothPanel(context: Context): Boolean {
        val intent = Intent("com.oculus.action.BLUETOOTH_SETTINGS").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            showToast(context, "Meta VR Bluetooth Panel not reachable", "Meta VR Bluetooth Panel nicht erreichbar")
            false
        }
    }

    /**
     * Opens Meta VR Quick Settings Overlay
     */
    fun openMetaQuickSettings(context: Context): Boolean {
        val intent = Intent("com.oculus.action.QUICK_SETTINGS").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            showToast(context, "Meta Quick Settings not reachable", "Meta Quick Settings nicht erreichbar")
            false
        }
    }

    /**
     * Opens Meta VR Navigator Quick Controls
     */
    fun openMetaNavigatorControls(context: Context): Boolean {
        val intent = Intent().apply {
            component = ComponentName("com.oculus.panelapp.settings", "com.oculus.navigator.quickcontrols.app.NavigatorQuickControlsActivity")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            showToast(context, "Meta Navigator Controls not reachable", "Meta Navigator Controls nicht erreichbar")
            false
        }
    }

    /**
     * Developer Settings
     */
    fun openDeveloperSettings(context: Context): Boolean {
        val intents = listOf(
            Intent().apply {
                component = ComponentName("com.android.settings", "com.android.settings.Settings\$DevelopmentSettingsDashboardActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )

        for (intent in intents) {
            try {
                context.startActivity(intent)
                return true
            } catch (_: Exception) {}
        }
        showToast(context, "Could not open developer options", "Konnte Entwickleroptionen nicht öffnen")
        return false
    }

    /**
     * Application Management
     */
    fun openApplicationSettings(context: Context): Boolean {
        val intents = listOf(
            Intent().apply {
                component = ComponentName("com.android.settings", "com.android.settings.Settings\$ManageApplicationsActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            Intent(Settings.ACTION_APPLICATION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )

        for (intent in intents) {
            try {
                context.startActivity(intent)
                return true
            } catch (_: Exception) {}
        }
        showToast(context, "Could not open app management", "Konnte App-Verwaltung nicht öffnen")
        return false
    }
}
