package com.quest.systemsettings

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.quest.systemsettings.adb.AdbClient
import com.quest.systemsettings.adb.AdbConnectionState
import com.quest.systemsettings.adb.AdbPairingClient
import com.quest.systemsettings.bluetooth.*
import com.quest.systemsettings.localization.AppLanguage
import com.quest.systemsettings.localization.LanguageManager
import com.quest.systemsettings.localization.Strings
import com.quest.systemsettings.logcat.BluetoothLogPreset
import com.quest.systemsettings.logcat.LogLevel
import com.quest.systemsettings.logcat.LogcatManager
import com.quest.systemsettings.settings.SettingsLauncher
import com.quest.systemsettings.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val adbClient by lazy { AdbClient(this) }
    private val bluetoothToolbox by lazy { BluetoothToolbox(this, adbClient) }
    private val logcatManager by lazy { LogcatManager(this, adbClient, lifecycleScope) }
    private val bluetoothHelper by lazy { BluetoothManagerHelper(this, lifecycleScope, adbClient) }
    private val bluetoothInspector by lazy { BluetoothInspector(this) }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        bluetoothHelper.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize language preference (defaults to English)
        LanguageManager.init(this)

        // Request Bluetooth and Location permissions on Android 12+ / 14
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (permissions.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissions.toTypedArray())
        }

        // Start logcat streaming
        logcatManager.startStreaming()

        setContent {
            QuestSystemSettingsTheme {
                MainAppScreen(
                    adbClient = adbClient,
                    bluetoothToolbox = bluetoothToolbox,
                    logcatManager = logcatManager,
                    bluetoothHelper = bluetoothHelper,
                    bluetoothInspector = bluetoothInspector
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        logcatManager.stopStreaming()
        adbClient.close()
        bluetoothHelper.destroy()
        bluetoothInspector.closeGatt()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    adbClient: AdbClient,
    bluetoothToolbox: BluetoothToolbox,
    logcatManager: LogcatManager,
    bluetoothHelper: BluetoothManagerHelper,
    bluetoothInspector: BluetoothInspector
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val adbState by adbClient.connectionState.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var showPairDialog by remember { mutableStateOf(false) }

    val selectedDeviceDetails by bluetoothInspector.selectedDeviceDetails.collectAsState()
    val selectedCompendiumProfile by bluetoothInspector.selectedCompendiumProfile.collectAsState()
    val pairingStatusMsg by bluetoothInspector.pairingStatusMessage.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = VrDarkBackground
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Top App Bar
            TopAppBar(
                title = {
                    Column {
                        Text(
                            Strings.appTitle,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextWhite
                        )
                        Text(
                            Strings.appSubtitle,
                            fontSize = 12.sp,
                            color = TextDim
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VrCardBg
                ),
                actions = {
                    // Dynamic Language Switcher: 🇬🇧 EN | 🇩🇪 DE
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(VrCardBgElevated)
                            .border(1.dp, BorderDefault, RoundedCornerShape(20.dp))
                            .padding(2.dp)
                    ) {
                        // English Flag Option
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (LanguageManager.currentLanguage == AppLanguage.EN) AccentCyan.copy(alpha = 0.25f) else Color.Transparent)
                                .border(
                                    if (LanguageManager.currentLanguage == AppLanguage.EN) 1.dp else 0.dp,
                                    if (LanguageManager.currentLanguage == AppLanguage.EN) AccentCyan else Color.Transparent,
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    LanguageManager.setLanguage(context, AppLanguage.EN)
                                    bluetoothHelper.refresh()
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🇬🇧", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "EN",
                                    fontSize = 11.sp,
                                    fontWeight = if (LanguageManager.currentLanguage == AppLanguage.EN) FontWeight.Bold else FontWeight.Medium,
                                    color = if (LanguageManager.currentLanguage == AppLanguage.EN) TextWhite else TextDim
                                )
                            }
                        }

                        // German Flag Option
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (LanguageManager.currentLanguage == AppLanguage.DE) AccentCyan.copy(alpha = 0.25f) else Color.Transparent)
                                .border(
                                    if (LanguageManager.currentLanguage == AppLanguage.DE) 1.dp else 0.dp,
                                    if (LanguageManager.currentLanguage == AppLanguage.DE) AccentCyan else Color.Transparent,
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    LanguageManager.setLanguage(context, AppLanguage.DE)
                                    bluetoothHelper.refresh()
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🇩🇪", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "DE",
                                    fontSize = 11.sp,
                                    fontWeight = if (LanguageManager.currentLanguage == AppLanguage.DE) FontWeight.Bold else FontWeight.Medium,
                                    color = if (LanguageManager.currentLanguage == AppLanguage.DE) TextWhite else TextDim
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Interactive ADB Status / Connect Button in Top Bar
                    val (badgeBg, badgeBorder, badgeText) = when (adbState) {
                        is AdbConnectionState.Connected -> Triple(AccentGreen.copy(alpha = 0.2f), AccentGreen, Strings.adbBadgeActive)
                        is AdbConnectionState.Connecting -> Triple(AccentOrange.copy(alpha = 0.2f), AccentOrange, Strings.adbBadgeConnecting)
                        is AdbConnectionState.WaitingForUserAuth -> Triple(AccentOrange.copy(alpha = 0.2f), AccentOrange, Strings.adbBadgeAuth)
                        is AdbConnectionState.Error -> Triple(AccentCyan.copy(alpha = 0.2f), AccentCyan, Strings.adbBadgeConnectAction)
                        is AdbConnectionState.Disconnected -> Triple(AccentCyan.copy(alpha = 0.2f), AccentCyan, Strings.adbBadgeConnectAction)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(badgeBg)
                            .border(1.dp, badgeBorder, RoundedCornerShape(16.dp))
                            .clickable {
                                if (adbState !is AdbConnectionState.Connected) {
                                    coroutineScope.launch {
                                        val ok = adbClient.connect("127.0.0.1", 5555)
                                        actionMessage = if (ok) Strings.adbConnectedSuccess else Strings.adbConnectFailedPort
                                        bluetoothHelper.refresh()
                                    }
                                } else {
                                    selectedTab = 3
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            badgeText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextWhite
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }
            )

            // Primary Navigation Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = VrCardBgElevated,
                contentColor = AccentCyan,
                divider = { HorizontalDivider(color = BorderDefault) }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(Strings.tabSystemMenus, fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        bluetoothHelper.refresh()
                    },
                    text = { Text(Strings.tabBtScanner, fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                    icon = { Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text(Strings.tabLogcat, fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                    icon = { Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text(Strings.tabAdbAssistant, fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                    icon = { Icon(Icons.Default.Usb, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            // Optional Notification Banner
            actionMessage?.let { msg ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = VrCardBgElevated),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AccentPurple))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = AccentPurple)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(msg, color = TextWhite, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { actionMessage = null }) {
                            Icon(Icons.Default.Close, contentDescription = Strings.close, tint = TextDim)
                        }
                    }
                }
            }

            // Tab Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                when (selectedTab) {
                    0 -> SystemMenusTab(
                        isAdbConnected = adbState is AdbConnectionState.Connected,
                        onAndroidSettingsApp = {
                            coroutineScope.launch {
                                val ok = SettingsLauncher.openAndroidSettingsApp(context, adbClient)
                                if (ok) {
                                    actionMessage = Strings.androidSettingsLaunchSuccess
                                }
                            }
                        },
                        onFullSettings = { SettingsLauncher.openFullSettings(context) },
                        onAospPhoneSettings = { SettingsLauncher.openAospPhoneSettings(context) },
                        onNetworkDashboard = { SettingsLauncher.openNetworkDashboard(context) },
                        onDisplaySettings = { SettingsLauncher.openDisplaySettings(context) },
                        onSoundSettings = { SettingsLauncher.openSoundSettings(context) },
                        onBluetoothSettings = { SettingsLauncher.openBluetoothSettings(context) },
                        onMetaVrBluetooth = { SettingsLauncher.openMetaVrBluetoothPanel(context) },
                        onMetaQuickSettings = { SettingsLauncher.openMetaQuickSettings(context) },
                        onMetaNavigator = { SettingsLauncher.openMetaNavigatorControls(context) },
                        onDeveloperSettings = { SettingsLauncher.openDeveloperSettings(context) },
                        onApplicationSettings = { SettingsLauncher.openApplicationSettings(context) }
                    )
                    1 -> BluetoothStackTab(
                        isAdbConnected = adbState is AdbConnectionState.Connected,
                        bluetoothHelper = bluetoothHelper,
                        onInspectDevice = { dev, rssi ->
                            bluetoothInspector.inspectDevice(dev, rssi)
                        },
                        onRestartStack = {
                            coroutineScope.launch {
                                val res = bluetoothToolbox.restartBluetoothStack()
                                actionMessage = res
                                bluetoothHelper.refresh()
                            }
                        },
                        onClearCache = {
                            coroutineScope.launch {
                                val res = bluetoothToolbox.clearBluetoothCache()
                                actionMessage = res
                                bluetoothHelper.refresh()
                            }
                        },
                        onConnectAdb = {
                            coroutineScope.launch {
                                val ok = adbClient.connect("127.0.0.1", 5555)
                                actionMessage = if (ok) Strings.adbConnectedSuccess else Strings.adbConnectFailedPort
                                bluetoothHelper.refresh()
                            }
                        },
                        onUnpair = { addr ->
                            val ok = bluetoothHelper.unpairDevice(addr)
                            actionMessage = if (ok) Strings.unpairSuccess else Strings.unpairFailed
                        }
                    )
                    2 -> LogcatTerminalTab(logcatManager = logcatManager)
                    3 -> AdbAssistantTab(
                        adbState = adbState,
                        onAutoConnect = {
                            coroutineScope.launch {
                                val ok = adbClient.connect("127.0.0.1", 5555)
                                actionMessage = if (ok) Strings.adbConnectedToHost else Strings.adbConnectFailedNoServer
                            }
                        },
                        onDisconnect = {
                            adbClient.close()
                            actionMessage = Strings.adbDisconnectedMsg
                        },
                        onOpenPairDialog = { showPairDialog = true },
                        onOpenWebAdb = {
                            try {
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://app.webadb.com")).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(browserIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, Strings.browserOpenError(e.message ?: ""), Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }

        // Device Inspection & Deep Pairing Dialog
        selectedDeviceDetails?.let { details ->
            DeviceDetailDialog(
                device = details,
                pairingStatus = pairingStatusMsg,
                logcatManager = logcatManager,
                isAdbActive = adbState is AdbConnectionState.Connected,
                onDismiss = { bluetoothInspector.clearSelection() },
                onOpenCompendium = { prof -> bluetoothInspector.openCompendiumEntry(prof) },
                onConnectAdb = {
                    coroutineScope.launch {
                        val ok = adbClient.connect("127.0.0.1", 5555)
                        actionMessage = if (ok) Strings.adbConnectedSuccess else Strings.adbConnectFailedPort
                        bluetoothHelper.refresh()
                    }
                },
                onPair = {
                    val rawDev = bluetoothHelper.adapter?.getRemoteDevice(details.address)
                    if (rawDev != null) {
                        bluetoothInspector.pairDevice(rawDev)
                    } else {
                        Toast.makeText(context, Strings.deviceNotFound, Toast.LENGTH_SHORT).show()
                    }
                },
                onUnpair = {
                    bluetoothHelper.unpairDevice(details.address)
                    bluetoothInspector.clearSelection()
                    actionMessage = Strings.deviceUnpaired(details.name)
                },
                onScanGatt = {
                    val rawDev = bluetoothHelper.adapter?.getRemoteDevice(details.address)
                    if (rawDev != null) {
                        bluetoothInspector.discoverGattServices(rawDev)
                    }
                }
            )
        }

        // In-App Offline Bluetooth Compendium Dialog (Zero App Exit!)
        selectedCompendiumProfile?.let { prof ->
            CompendiumDialog(
                profile = prof,
                onDismiss = { bluetoothInspector.closeCompendiumEntry() }
            )
        }

        // 6-digit Wireless Pairing Dialog
        if (showPairDialog) {
            WirelessPairingDialog(
                onDismiss = { showPairDialog = false },
                onPair = { host, port, code ->
                    showPairDialog = false
                    coroutineScope.launch {
                        val result = AdbPairingClient.pair(host, port, code, context)
                        actionMessage = result.message
                        if (result.success) {
                            adbClient.connect("127.0.0.1", 5555)
                        }
                    }
                }
            )
        }
    }
}

// -------------------------------------------------------------
// TAB 0: SYSTEM-MENÜS & VERWAISTE DIALOGE
// -------------------------------------------------------------
@Composable
fun SystemMenusTab(
    isAdbConnected: Boolean,
    onAndroidSettingsApp: () -> Unit,
    onFullSettings: () -> Unit,
    onAospPhoneSettings: () -> Unit,
    onNetworkDashboard: () -> Unit,
    onDisplaySettings: () -> Unit,
    onSoundSettings: () -> Unit,
    onBluetoothSettings: () -> Unit,
    onMetaVrBluetooth: () -> Unit,
    onMetaQuickSettings: () -> Unit,
    onMetaNavigator: () -> Unit,
    onDeveloperSettings: () -> Unit,
    onApplicationSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = VrCardBg),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AccentCyan))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        Strings.sysMenusBannerTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextWhite
                    )
                    Text(
                        Strings.sysMenusBannerSubtitle,
                        fontSize = 12.sp,
                        color = TextLight
                    )
                }
            }
        }

        // Button 1: Android Settings App (Quest App)
        BigActionCard(
            title = Strings.cardAndroidSettingsAppTitle,
            subtitle = Strings.cardAndroidSettingsAppSubtitle,
            buttonLabel = Strings.cardAndroidSettingsAppButton,
            badge = Strings.cardAndroidSettingsAppBadge,
            badgeColor = AccentGreen,
            cardBorder = BorderGreen,
            onClick = onAndroidSettingsApp
        )

        // Button 2: Original AOSP Phone Settings (Spa)
        BigActionCard(
            title = Strings.cardAospSpaTitle,
            subtitle = Strings.cardAospSpaSubtitle,
            buttonLabel = Strings.cardAospSpaButton,
            badge = Strings.cardAospSpaBadge,
            badgeColor = AccentOrange,
            cardBorder = AccentOrange.copy(alpha = 0.6f),
            onClick = onAospPhoneSettings
        )

        // Button 2: AOSP Network Dashboard (Methode 1)
        BigActionCard(
            title = Strings.cardNetworkTitle,
            subtitle = Strings.cardNetworkSubtitle,
            buttonLabel = Strings.cardNetworkButton,
            badge = "AOSP Phone",
            badgeColor = AccentCyan,
            cardBorder = BorderCyan,
            onClick = onNetworkDashboard
        )

        // Button 3: AOSP Display Settings (Methode 2)
        BigActionCard(
            title = Strings.cardDisplayTitle,
            subtitle = Strings.cardDisplaySubtitle,
            buttonLabel = Strings.cardDisplayButton,
            badge = "AOSP Display",
            badgeColor = AccentPurple,
            cardBorder = BorderPurple,
            onClick = onDisplaySettings
        )

        // Button 4: AOSP Sound Settings (Methode 3)
        BigActionCard(
            title = Strings.cardSoundTitle,
            subtitle = Strings.cardSoundSubtitle,
            buttonLabel = Strings.cardSoundButton,
            badge = "AOSP Audio",
            badgeColor = AccentGreen,
            cardBorder = BorderGreen,
            onClick = onSoundSettings
        )

        // Button 5: Root Settings
        BigActionCard(
            title = Strings.cardRootSettingsTitle,
            subtitle = Strings.cardRootSettingsSubtitle,
            buttonLabel = Strings.cardRootSettingsButton,
            badge = Strings.cardRootSettingsBadge,
            badgeColor = AccentCyan,
            cardBorder = BorderCyan,
            onClick = onFullSettings
        )

        // Button 6: Bluetooth Menu (AOSP)
        BigActionCard(
            title = Strings.cardAospBtTitle,
            subtitle = Strings.cardAospBtSubtitle,
            buttonLabel = Strings.cardAospBtButton,
            badge = Strings.cardAospBtBadge,
            badgeColor = AccentGreen,
            cardBorder = BorderGreen,
            onClick = onBluetoothSettings
        )

        // Button 7: Meta VR Native Bluetooth Panel
        BigActionCard(
            title = Strings.cardMetaBtTitle,
            subtitle = Strings.cardMetaBtSubtitle,
            buttonLabel = Strings.cardMetaBtButton,
            badge = "Meta VR Panel",
            badgeColor = AccentPurple,
            cardBorder = BorderPurple,
            onClick = onMetaVrBluetooth
        )

        // Button 8: Meta Quick Settings Overlay
        BigActionCard(
            title = Strings.cardMetaQuickTitle,
            subtitle = Strings.cardMetaQuickSubtitle,
            buttonLabel = Strings.cardMetaQuickButton,
            badge = Strings.cardMetaQuickBadge,
            badgeColor = AccentCyan,
            cardBorder = BorderDefault,
            onClick = onMetaQuickSettings
        )

        // Button 9: Meta Navigator Controls
        BigActionCard(
            title = Strings.cardMetaNavTitle,
            subtitle = Strings.cardMetaNavSubtitle,
            buttonLabel = Strings.cardMetaNavButton,
            badge = "HorizonOS",
            badgeColor = AccentPurple,
            cardBorder = BorderDefault,
            onClick = onMetaNavigator
        )

        // Button 10: Developer Options
        BigActionCard(
            title = Strings.cardDevOptionsTitle,
            subtitle = Strings.cardDevOptionsSubtitle,
            buttonLabel = Strings.cardDevOptionsButton,
            badge = Strings.cardDevOptionsBadge,
            badgeColor = AccentOrange,
            cardBorder = BorderDefault,
            onClick = onDeveloperSettings
        )

        // Button 11: Manage Apps
        BigActionCard(
            title = Strings.cardAppsTitle,
            subtitle = Strings.cardAppsSubtitle,
            buttonLabel = Strings.cardAppsButton,
            badge = Strings.cardAppsBadge,
            badgeColor = AccentBlue,
            cardBorder = BorderDefault,
            onClick = onApplicationSettings
        )
    }
}

@Composable
fun BigActionCard(
    title: String,
    subtitle: String,
    buttonLabel: String,
    badge: String,
    badgeColor: Color,
    cardBorder: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VrCardBg),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(cardBorder)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextWhite,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeColor.copy(alpha = 0.2f))
                        .border(1.dp, badgeColor, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(badge, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = badgeColor)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                subtitle,
                fontSize = 12.sp,
                color = TextLight,
                lineHeight = 17.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = badgeColor, contentColor = TextDark),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(buttonLabel, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 1: BLUETOOTH SCANNER & GERÄTE
// -------------------------------------------------------------
@Composable
fun BluetoothStackTab(
    isAdbConnected: Boolean,
    bluetoothHelper: BluetoothManagerHelper,
    onInspectDevice: (BluetoothDevice, Int?) -> Unit,
    onRestartStack: () -> Unit,
    onClearCache: () -> Unit,
    onConnectAdb: () -> Unit,
    onUnpair: (String) -> Unit
) {
    val status by bluetoothHelper.adapterStatus.collectAsState()
    val bondedDevices by bluetoothHelper.bondedDevices.collectAsState()
    val discoveredDevices by bluetoothHelper.discoveredDevices.collectAsState()
    val isDiscovering by bluetoothHelper.isDiscovering.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Bluetooth Hardware Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = VrCardBg),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderGreen))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bluetooth, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(Strings.btStatusCardTitle, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextWhite)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (status.isEnabled) AccentGreen.copy(alpha = 0.2f) else AccentRed.copy(alpha = 0.2f))
                            .border(1.dp, if (status.isEnabled) AccentGreen else AccentRed, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(status.stateString, fontSize = 12.sp, color = TextWhite, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("${Strings.btDeviceNameLabel} ${status.name}", fontSize = 13.sp, color = TextLight)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${Strings.btMacLabel} ${status.address}", fontSize = 13.sp, color = AccentCyan, fontFamily = FontFamily.Monospace)
                }

                // Privacy Warning if MAC is 02:00:00:00:00:00 and ADB is not connected
                if (status.isAnonymized && !isAdbConnected) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = AccentOrange.copy(alpha = 0.12f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentOrange.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                Strings.btPrivacyWarning,
                                fontSize = 11.sp,
                                color = TextLight,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons: Stack Restart & Clear Cache & ADB Connect
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onRestartStack,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = VrCardBgElevated),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(Strings.btStackResetButton, fontSize = 11.sp, color = TextWhite, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onClearCache,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed.copy(alpha = 0.2f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentRed),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = AccentRed, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(Strings.btCacheClearButton, fontSize = 11.sp, color = AccentRed, fontWeight = FontWeight.Bold)
                    }

                    if (!isAdbConnected) {
                        Button(
                            onClick = onConnectAdb,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = TextDark),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Usb, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(Strings.btAdbStartButton, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Bonded Devices Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = VrCardBg),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderDefault))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(Strings.btBondedDevicesTitle(bondedDevices.size), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextWhite)
                        Text(Strings.btBondedDevicesSubtitle, fontSize = 12.sp, color = TextDim)
                    }
                    IconButton(onClick = { bluetoothHelper.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = Strings.btRefreshDesc, tint = AccentCyan)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (bondedDevices.isEmpty()) {
                    Text(
                        Strings.btNoBondedDevices,
                        fontSize = 13.sp,
                        color = TextDim,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    bondedDevices.forEach { dev ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onInspectDevice(dev.device, dev.rssi) },
                            colors = CardDefaults.cardColors(containerColor = VrCardBgElevated),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderDefault)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.BluetoothConnected, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(dev.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextWhite)
                                        Text("${dev.address} • ${dev.deviceType}", fontSize = 12.sp, color = TextDim)
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        onClick = { onInspectDevice(dev.device, dev.rssi) },
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan.copy(alpha = 0.2f)),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(Strings.btDetailsButton, fontSize = 11.sp, color = AccentCyan)
                                    }

                                    Button(
                                        onClick = { onUnpair(dev.address) },
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed.copy(alpha = 0.2f)),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentRed.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(Strings.btUnpairButton, fontSize = 11.sp, color = AccentRed)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Live Scanner Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = VrCardBg),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderDefault))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(Strings.btNearbyTitle, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextWhite)
                        Text(Strings.btNearbySubtitle, fontSize = 12.sp, color = TextDim)
                    }
                    Button(
                        onClick = {
                            if (isDiscovering) bluetoothHelper.stopDiscovery() else bluetoothHelper.startDiscovery()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isDiscovering) AccentOrange else AccentCyan, contentColor = TextDark),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (isDiscovering) Strings.btStopScan else Strings.btStartScan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                if (isDiscovering) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = AccentCyan)
                }

                Spacer(modifier = Modifier.height(10.dp))
                if (discoveredDevices.isEmpty()) {
                    Text(
                        if (isDiscovering) Strings.btScanInProgress else Strings.btNoDiscoveredDevices,
                        fontSize = 12.sp,
                        color = TextDim,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    discoveredDevices.forEach { dev ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onInspectDevice(dev.device, dev.rssi) },
                            colors = CardDefaults.cardColors(containerColor = VrCardBgElevated),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderCyan.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.AutoMirrored.Filled.BluetoothSearching, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(dev.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextWhite)
                                        Text("${dev.address} • ${dev.deviceType}${if (dev.rssi != null) " • ${dev.rssi} dBm" else ""}", fontSize = 12.sp, color = TextDim)
                                    }
                                }
                                Button(
                                    onClick = { onInspectDevice(dev.device, dev.rssi) },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = TextDark),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(Strings.btPairDetailsButton, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// GERÄTE-INSPEKTIONS- & KOPPLUNGS-POPUP
// -------------------------------------------------------------
@Composable
fun DeviceDetailDialog(
    device: DetailedBtDevice,
    pairingStatus: String?,
    logcatManager: LogcatManager,
    isAdbActive: Boolean,
    onDismiss: () -> Unit,
    onOpenCompendium: (ServiceProfileInfo) -> Unit,
    onConnectAdb: () -> Unit,
    onPair: () -> Unit,
    onUnpair: () -> Unit,
    onScanGatt: () -> Unit
) {
    val displayedLogs by logcatManager.displayedLogs.collectAsState()
    val terminalListState = rememberLazyListState()

    // Filter logs for this device address or bonding state machine
    val deviceLogs = remember(displayedLogs, device.address) {
        displayedLogs.filter { line ->
            line.rawText.contains(device.address, ignoreCase = true) ||
            line.rawText.contains("BluetoothBondStateMachine", ignoreCase = true) ||
            line.rawText.contains("bt_btif", ignoreCase = true)
        }.takeLast(30)
    }

    LaunchedEffect(deviceLogs.size) {
        if (deviceLogs.isNotEmpty()) {
            terminalListState.scrollToItem(deviceLogs.size - 1)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.95f),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(device.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextWhite)
                    Text(device.address, fontSize = 13.sp, color = AccentCyan, fontFamily = FontFamily.Monospace)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (device.bondState == BluetoothDevice.BOND_BONDED) AccentGreen.copy(alpha = 0.2f) else AccentOrange.copy(alpha = 0.2f))
                        .border(1.dp, if (device.bondState == BluetoothDevice.BOND_BONDED) AccentGreen else AccentOrange, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(device.bondStateString, fontSize = 11.sp, color = TextWhite, fontWeight = FontWeight.Bold)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Device Hardware Info Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = VrCardBgElevated),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${Strings.devDetailTypeLabel} ${device.deviceType}", fontSize = 12.sp, color = TextLight)
                        Text("${Strings.devDetailMajorClassLabel} ${device.majorClass}", fontSize = 12.sp, color = TextLight)
                        Text("${Strings.devDetailClassDetailsLabel} ${device.deviceClassString}", fontSize = 12.sp, color = TextDim)
                        if (device.rssi != null) {
                            Text("${Strings.devDetailSignalLabel} ${device.rssi} dBm", fontSize = 12.sp, color = AccentGreen)
                        }
                    }
                }

                // Action Buttons: Pair, Unpair, GATT Scan
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (device.bondState != BluetoothDevice.BOND_BONDED) {
                        Button(
                            onClick = onPair,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = TextDark),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(Strings.devDetailPairButton, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = onUnpair,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentRed.copy(alpha = 0.2f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentRed),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.LinkOff, contentDescription = null, tint = AccentRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(Strings.btUnpairButton, color = AccentRed, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = onScanGatt,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = VrCardBg, contentColor = TextWhite),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(Strings.devDetailScanGattButton, fontSize = 12.sp)
                    }
                }

                // Pairing Status Notification
                pairingStatus?.let { status ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = AccentPurple.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = status,
                            fontSize = 12.sp,
                            color = TextLight,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // Profiles / Services Card with In-App Offline Compendium
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = VrCardBgElevated),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(Strings.devDetailRecognizedProfiles, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextWhite)
                            Text(Strings.devDetailCompendiumBadge, fontSize = 11.sp, color = AccentCyan)
                        }

                        if (device.uuids.isEmpty()) {
                            Text(Strings.devDetailNoUuids, fontSize = 12.sp, color = TextDim)
                        } else {
                            device.uuids.forEach { prof ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(VrCardBg)
                                        .clickable { onOpenCompendium(prof) }
                                        .padding(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(prof.name, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = AccentCyan)
                                        Text(Strings.devDetailCompendiumButton, fontSize = 11.sp, color = AccentGreen)
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(prof.description, fontSize = 11.sp, color = TextLight, lineHeight = 15.sp)
                                }
                            }
                        }

                        // Discovered GATT Services with decoded Characteristic values
                        if (device.gattServices.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(Strings.devDetailGattAttributes, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = AccentPurple)
                            device.gattServices.forEach { gattSvc ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(VrCardBg)
                                        .padding(6.dp)
                                ) {
                                    Text("${gattSvc.name} (${gattSvc.type})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AccentCyan)
                                    gattSvc.characteristics.forEach { ch ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, top = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(ch.name, fontSize = 11.sp, color = TextLight, modifier = Modifier.weight(1f))
                                            ch.decodedValue?.let { dec ->
                                                Text(dec, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
                                            } ?: ch.hexValue?.let { hex ->
                                                Text(hex, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TextDim)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Live Embedded Pairing Terminal
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = VrCardBgElevated),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(Strings.devDetailLiveTracesTitle, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextWhite)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (!isAdbActive) {
                                    Button(
                                        onClick = onConnectAdb,
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = TextDark),
                                        shape = RoundedCornerShape(4.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(Strings.devDetailStartAdbButton, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isAdbActive) AccentGreen.copy(alpha = 0.2f) else AccentOrange.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        if (isAdbActive) Strings.devDetailDeepTracesBadge else Strings.devDetailFrameworkLogsBadge,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isAdbActive) AccentGreen else AccentOrange
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(VrTerminalBg)
                                .border(1.dp, BorderDefault, RoundedCornerShape(6.dp))
                                .padding(6.dp)
                        ) {
                            if (deviceLogs.isEmpty()) {
                                Text(
                                    Strings.devDetailWaitingLogs,
                                    color = TextDim,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            } else {
                                LazyColumn(
                                    state = terminalListState,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(deviceLogs, key = { it.id }) { log ->
                                        Text(
                                            text = log.rawText,
                                            color = when (log.level) {
                                                LogLevel.ERROR -> LogLevelError
                                                LogLevel.WARN -> LogLevelWarn
                                                LogLevel.INFO -> LogLevelInfo
                                                LogLevel.DEBUG -> LogLevelDebug
                                                LogLevel.VERBOSE -> LogLevelVerbose
                                                LogLevel.UNKNOWN -> TextWhite
                                            },
                                            fontSize = 10.sp,
                                            lineHeight = 13.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = TextDark)
            ) {
                Text(Strings.close, fontWeight = FontWeight.Bold)
            }
        }
    )
}

// -------------------------------------------------------------
// IN-APP OFFLINE BLUETOOTH KOMPENDIUM DIALOG
// -------------------------------------------------------------
@Composable
fun CompendiumDialog(
    profile: ServiceProfileInfo,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(profile.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextWhite)
                Text("UUID: ${profile.uuid}", fontSize = 11.sp, color = AccentCyan, fontFamily = FontFamily.Monospace)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = VrCardBgElevated),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(Strings.compendiumFunctionTitle, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = AccentGreen)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(profile.description, fontSize = 12.sp, color = TextLight, lineHeight = 16.sp)
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = VrCardBgElevated),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(Strings.compendiumTechTitle, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = AccentPurple)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(profile.technicalDetails, fontSize = 12.sp, color = TextLight, lineHeight = 16.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = TextDark)
            ) {
                Text(Strings.compendiumBackButton, fontWeight = FontWeight.Bold)
            }
        }
    )
}

// -------------------------------------------------------------
// TAB 2: LOGCAT TERMINAL
// -------------------------------------------------------------
@Composable
fun LogcatTerminalTab(logcatManager: LogcatManager) {
    val displayedLogs by logcatManager.displayedLogs.collectAsState()
    val isStreaming by logcatManager.isStreaming.collectAsState()
    val selectedPreset by logcatManager.selectedPreset.collectAsState()
    val searchFilter by logcatManager.searchFilter.collectAsState()
    val autoScroll by logcatManager.autoScroll.collectAsState()

    val listState = rememberLazyListState()

    LaunchedEffect(displayedLogs.size, autoScroll) {
        if (autoScroll && displayedLogs.isNotEmpty()) {
            listState.scrollToItem(displayedLogs.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Preset Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BluetoothLogPreset.entries.forEach { preset ->
                FilterChip(
                    selected = (selectedPreset == preset),
                    onClick = { logcatManager.setPreset(preset) },
                    label = { Text(preset.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentCyan,
                        selectedLabelColor = TextDark,
                        containerColor = VrCardBg,
                        labelColor = TextLight
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search & Controls Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedTextField(
                value = searchFilter,
                onValueChange = { logcatManager.setSearchFilter(it) },
                placeholder = { Text(Strings.logcatFilterPlaceholder, fontSize = 12.sp, color = TextDim) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, color = TextWhite),
                trailingIcon = {
                    if (searchFilter.isNotEmpty()) {
                        IconButton(onClick = { logcatManager.setSearchFilter("") }) {
                            Icon(Icons.Default.Clear, contentDescription = null, tint = TextLight, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Action Buttons
            IconButton(
                onClick = { logcatManager.toggleStreaming() },
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isStreaming) AccentGreen.copy(alpha = 0.2f) else AccentOrange.copy(alpha = 0.2f))
            ) {
                Icon(
                    if (isStreaming) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = if (isStreaming) AccentGreen else AccentOrange
                )
            }

            IconButton(
                onClick = { logcatManager.clearLogs() },
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(VrCardBg)
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = Strings.logcatClearDesc, tint = TextLight)
            }

            IconButton(
                onClick = { logcatManager.copyToClipboard() },
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(VrCardBg)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = Strings.logcatCopyDesc, tint = TextLight)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Info bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(Strings.logcatBufferedCount(displayedLogs.size), fontSize = 12.sp, color = TextDim)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Strings.logcatAutoScroll, fontSize = 12.sp, color = TextLight)
                Spacer(modifier = Modifier.width(6.dp))
                Switch(
                    checked = autoScroll,
                    onCheckedChange = { logcatManager.toggleAutoScroll() }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Terminal Console Window
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(VrTerminalBg)
                .border(1.dp, BorderDefault, RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            if (displayedLogs.isEmpty()) {
                Text(
                    text = if (isStreaming) Strings.logcatWaitingStream else Strings.logcatPaused,
                    color = TextDim,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(displayedLogs, key = { it.id }) { entry ->
                        val color = when (entry.level) {
                            LogLevel.ERROR -> LogLevelError
                            LogLevel.WARN -> LogLevelWarn
                            LogLevel.INFO -> LogLevelInfo
                            LogLevel.DEBUG -> LogLevelDebug
                            LogLevel.VERBOSE -> LogLevelVerbose
                            LogLevel.UNKNOWN -> TextWhite
                        }
                        Text(
                            text = entry.rawText,
                            color = color,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 3: ADB VERBINDUNG & HILFE
// -------------------------------------------------------------
@Composable
fun AdbAssistantTab(
    adbState: AdbConnectionState,
    onAutoConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenPairDialog: () -> Unit,
    onOpenWebAdb: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = VrCardBg),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AccentOrange))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(Strings.adbStatusCardTitle, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextWhite)
                Spacer(modifier = Modifier.height(6.dp))
                val stateText = when (adbState) {
                    is AdbConnectionState.Connected -> Strings.adbConnectedDesc
                    is AdbConnectionState.Connecting -> Strings.adbConnectingDesc(adbState.host, adbState.port)
                    is AdbConnectionState.WaitingForUserAuth -> Strings.adbWaitingAuthDesc
                    is AdbConnectionState.Error -> "Offline: ${adbState.message}"
                    is AdbConnectionState.Disconnected -> Strings.adbDisconnectedDesc
                }
                Text(stateText, fontSize = 13.sp, color = TextLight)
            }
        }

        // Solution & Control Buttons
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = VrCardBg),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderDefault))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Strings.adbMethodsTitle, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextWhite)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Button 1: Auto Connect
                    Button(
                        onClick = onAutoConnect,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = TextDark),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(Strings.adbAutoConnectBtn, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    // Button 2: Disconnect
                    if (adbState is AdbConnectionState.Connected || adbState is AdbConnectionState.Connecting) {
                        Button(
                            onClick = onDisconnect,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentRed.copy(alpha = 0.2f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentRed),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = AccentRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(Strings.adbDisconnectBtn, color = AccentRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                // Button 3: Wireless Pairing PIN
                Button(
                    onClick = onOpenPairDialog,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPurple, contentColor = TextWhite),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.VpnKey, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(Strings.adbWirelessPairingBtn, fontWeight = FontWeight.Bold)
                }

                // Button 4: WebADB in Browser
                Button(
                    onClick = onOpenWebAdb,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = VrCardBgElevated, contentColor = TextWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentGreen),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = AccentGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(Strings.adbWebAdbBtn, color = AccentGreen, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // PC Cable Mode Explanation & Copy Command
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = VrCardBg),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderDefault))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(Strings.adbCableModeTitle, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextWhite)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    Strings.adbCableModeDesc,
                    fontSize = 12.sp,
                    color = TextLight
                )
                Spacer(modifier = Modifier.height(10.dp))

                val cmd = "adb tcpip 5555"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(VrTerminalBg)
                        .border(1.dp, BorderDefault, RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(cmd, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = AccentGreen)
                    IconButton(
                        onClick = {
                            val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clip.setPrimaryClip(ClipData.newPlainText("ADB Command", cmd))
                            Toast.makeText(context, Strings.adbCmdCopied(cmd), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = Strings.logcatCopyDesc, tint = TextLight, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 6-DIGIT WIRELESS PAIRING DIALOG
// -------------------------------------------------------------
@Composable
fun WirelessPairingDialog(
    onDismiss: () -> Unit,
    onPair: (host: String, port: Int, code: String) -> Unit
) {
    var host by remember { mutableStateOf("127.0.0.1") }
    var portText by remember { mutableStateOf("") }
    var codeText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(Strings.wirelessPairingTitle, fontWeight = FontWeight.Bold, color = TextWhite)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    Strings.wirelessPairingInstructions,
                    fontSize = 12.sp,
                    color = TextLight,
                    lineHeight = 16.sp
                )

                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text(Strings.wirelessHostLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it },
                    label = { Text(Strings.wirelessPortLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = codeText,
                    onValueChange = { codeText = it },
                    label = { Text(Strings.wirelessCodeLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val port = portText.toIntOrNull() ?: 5555
                    onPair(host, port, codeText)
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple, contentColor = TextWhite)
            ) {
                Text(Strings.wirelessPairNowBtn)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Strings.wirelessCancelBtn, color = TextLight)
            }
        }
    )
}
