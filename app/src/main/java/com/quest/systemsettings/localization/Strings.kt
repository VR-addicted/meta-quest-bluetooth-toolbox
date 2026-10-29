package com.quest.systemsettings.localization

object Strings {
    private val isDe: Boolean get() = LanguageManager.isGerman

    // Top Bar
    val appTitle: String = "Quest Settings & BT Toolbox"
    val appSubtitle: String
        get() = if (isDe) "Meta Quest Systemdialoge & Bluetooth Tiefenanalyse"
                else "Meta Quest System Dialogs & Bluetooth Deep Analysis"

    val adbBadgeActive: String
        get() = if (isDe) "ADB: Aktiv (Port 5555)" else "ADB: Active (Port 5555)"
    val adbBadgeConnecting: String
        get() = if (isDe) "ADB: Verbinde..." else "ADB: Connecting..."
    val adbBadgeAuth: String
        get() = if (isDe) "ADB: Autorisierung?" else "ADB: Auth Required?"
    val adbBadgeConnectAction: String
        get() = if (isDe) "⚡ ADB Verbinden (5555)" else "⚡ Connect ADB (5555)"

    // Tabs
    val tabSystemMenus: String
        get() = if (isDe) "⚙️ System-Menüs" else "⚙️ System Menus"
    val tabBtScanner: String
        get() = if (isDe) "📶 BT Scanner & Stack" else "📶 BT Scanner & Stack"
    val tabLogcat: String
        get() = if (isDe) "📜 Logcat Terminal" else "📜 Logcat Terminal"
    val tabAdbAssistant: String
        get() = if (isDe) "🔌 ADB Assistent" else "🔌 ADB Assistant"

    // Notifications / Toasts
    val adbConnectedSuccess: String
        get() = if (isDe) "Erfolgreich mit ADB verbunden!" else "Successfully connected to ADB!"
    val adbConnectFailedPort: String
        get() = if (isDe) "Konnte Port 5555 nicht erreichen. Prüfe Kabel oder führe 'adb tcpip 5555' aus."
                else "Could not reach port 5555. Check cable or run 'adb tcpip 5555'."
    val adbConnectedToHost: String
        get() = if (isDe) "Erfolgreich mit ADB auf 127.0.0.1:5555 verbunden!"
                else "Successfully connected to ADB at 127.0.0.1:5555!"
    val adbConnectFailedNoServer: String
        get() = if (isDe) "Verbindung fehlgeschlagen: Kein ADB Server auf 5555 aktiv."
                else "Connection failed: No ADB daemon running on port 5555."
    val adbDisconnectedMsg: String
        get() = if (isDe) "ADB Verbindung getrennt. (Direktmodus aktiv)"
                else "ADB connection disconnected. (Direct mode active)"
    fun deviceUnpaired(name: String): String
        = if (isDe) "Gerät entkoppelt: $name" else "Device unpaired: $name"
    val unpairSuccess: String
        get() = if (isDe) "Gerät entkoppelt" else "Device unpaired"
    val unpairFailed: String
        get() = if (isDe) "Entkoppeln fehlgeschlagen" else "Unpairing failed"
    val deviceNotFound: String
        get() = if (isDe) "Gerät nicht gefunden" else "Device not found"
    fun browserOpenError(msg: String): String
        = if (isDe) "Konnte Browser nicht öffnen: $msg" else "Could not open browser: $msg"
    val close: String
        get() = if (isDe) "Schließen" else "Close"

    // Tab 0: System Menus
    val sysMenusBannerTitle: String
        get() = if (isDe) "Direkte System-Dialoge & Verwaiste Menüs"
                else "Direct System Dialogs & Orphaned Menus"
    val sysMenusBannerSubtitle: String
        get() = if (isDe) "Ruft versteckte Android 14 Menüs und interne Meta-VR-Panels auf. Funktioniert sofort ohne ADB!"
                else "Launches hidden Android 14 menus and internal Meta VR panels. Works immediately without ADB!"

    // Android Settings App
    val cardAndroidSettingsAppTitle: String
        get() = if (isDe) "📱 Android Settings (Quest App)"
                else "📱 Android Settings (Quest App)"
    val cardAndroidSettingsAppSubtitle: String
        get() = if (isDe) "Startet direkt die native 'Android settings' App der Brille! Bietet Zugriff auf alle AOSP-Systemeinstellungen, Apps, Speicher, Konten, Verbindungen und Systemdialoge."
                else "Directly launches the native 'Android settings' app on the headset! Provides access to all AOSP system settings, apps, storage, accounts, connections, and system dialogs."
    val cardAndroidSettingsAppButton: String
        get() = if (isDe) "Android Settings App öffnen" else "Open Android Settings App"
    val cardAndroidSettingsAppBadge: String
        get() = if (isDe) "⭐ Haupt-App" else "⭐ Main App"
    val androidSettingsLaunchSuccess: String
        get() = if (isDe) "'Android settings' erfolgreich gestartet!" else "'Android settings' launched successfully!"

    val cardAospSpaTitle: String
        get() = "📱 Original Android Phone Settings (AOSP Spa)"
    val cardAospSpaSubtitle: String
        get() = if (isDe) "Genau das geheime Menü, das QGO bei fehlender ADB-Verbindung öffnet! Moderne AOSP-Oberfläche mit vollständigen Telefon- und App-Berechtigungen."
                else "The exact secret menu QGO opens when ADB is missing! Modern AOSP interface with full phone & app permissions."
    val cardAospSpaButton: String
        get() = if (isDe) "AOSP Phone Settings öffnen" else "Open AOSP Phone Settings"
    val cardAospSpaBadge: String
        get() = if (isDe) "QGO Geheim-Menü" else "QGO Secret Menu"

    val cardNetworkTitle: String
        get() = if (isDe) "🌐 AOSP Netzwerk & Internet Dashboard (Methode 1)"
                else "🌐 AOSP Network & Internet Dashboard (Method 1)"
    val cardNetworkSubtitle: String
        get() = if (isDe) "Echte Android Telefonie- & Netzwerk-Einstellungen: Flugmodus, VPN, DNS, Tethering und Verbindungs-Parameter."
                else "Real Android phone & network settings: Airplane mode, VPN, DNS, tethering and connection parameters."
    val cardNetworkButton: String
        get() = if (isDe) "Netzwerk-Dashboard öffnen" else "Open Network Dashboard"

    val cardDisplayTitle: String
        get() = if (isDe) "🖥️ AOSP Display- & Bildschirmeinstellungen (Methode 2)"
                else "🖥️ AOSP Display & Screen Settings (Method 2)"
    val cardDisplaySubtitle: String
        get() = if (isDe) "Versteckte Android Display-Optionen: Schriftgröße, Anzeigeskalierung, Ruhezustand und Farbprofile abseits der Quest-VR-Begrenzungen."
                else "Hidden Android display options: Font size, display scaling, sleep timeout, and color profiles beyond Quest VR limits."
    val cardDisplayButton: String
        get() = if (isDe) "Display-Einstellungen öffnen" else "Open Display Settings"

    val cardSoundTitle: String
        get() = if (isDe) "🔊 AOSP Sound- & Audio-Manager (Methode 3)"
                else "🔊 AOSP Sound & Audio Manager (Method 3)"
    val cardSoundSubtitle: String
        get() = if (isDe) "Erweiterte Android Lautstärkeregler für Medien, Benachrichtigungen, Wecker und Systemtöne."
                else "Advanced Android volume controls for media, notifications, alarm, and system sounds."
    val cardSoundButton: String
        get() = if (isDe) "Sound-Einstellungen öffnen" else "Open Sound Settings"

    val cardRootSettingsTitle: String
        get() = if (isDe) "⚙️ Vollständige Android-Einstellungen (Root Table)"
                else "⚙️ Full Android Settings (Root Table)"
    val cardRootSettingsSubtitle: String
        get() = if (isDe) "Öffnet die DeepLink-Homepage der nativen Android-Einstellungen. Zugriff auf Systemparameter, Töne, Anzeige und Speicher."
                else "Opens the native Android Settings deep link homepage. Access to system parameters, sounds, display, and storage."
    val cardRootSettingsButton: String
        get() = if (isDe) "Root-Einstellungen öffnen" else "Open Root Settings"
    val cardRootSettingsBadge: String
        get() = if (isDe) "Vollständig" else "Full Root"

    val cardAospBtTitle: String
        get() = if (isDe) "📶 Android Bluetooth & Verbundene Geräte"
                else "📶 Android Bluetooth & Connected Devices"
    val cardAospBtSubtitle: String
        get() = if (isDe) "Natives Android-Kopplungs- und Gerätemenü. Zeigt alle registrierten Controller, Tastaturen, Headsets und Peripheriegeräte."
                else "Native Android pairing & device menu. Shows all registered controllers, keyboards, headsets and peripherals."
    val cardAospBtButton: String
        get() = if (isDe) "Android Bluetooth-Menü aufrufen" else "Open Android Bluetooth Menu"
    val cardAospBtBadge: String
        get() = if (isDe) "AOSP Kopplung" else "AOSP Pairing"

    val cardMetaBtTitle: String
        get() = if (isDe) "🥽 Meta VR Bluetooth-Panel (Native Quest VR)"
                else "🥽 Meta VR Bluetooth Panel (Native Quest VR)"
    val cardMetaBtSubtitle: String
        get() = if (isDe) "Öffnet direkt das in das Quest-VR-Overlay eingebettete Bluetooth-Modal (com.oculus.action.BLUETOOTH_SETTINGS)."
                else "Directly opens the Bluetooth modal embedded into the Quest VR overlay (com.oculus.action.BLUETOOTH_SETTINGS)."
    val cardMetaBtButton: String
        get() = if (isDe) "Meta VR Bluetooth öffnen" else "Open Meta VR Bluetooth"

    val cardMetaQuickTitle: String
        get() = "⚡ Meta VR Quick Settings Overlay"
    val cardMetaQuickSubtitle: String
        get() = if (isDe) "Aktiviert die schwebenden Meta-Schnelleinstellungen (WLAN, Bluetooth, Helligkeit, Lautstärke)."
                else "Activates the floating Meta quick settings (Wi-Fi, Bluetooth, brightness, volume)."
    val cardMetaQuickButton: String
        get() = if (isDe) "Quick Settings öffnen" else "Open Quick Settings"
    val cardMetaQuickBadge: String
        get() = if (isDe) "VR Schnellmenü" else "VR Quick Menu"

    val cardMetaNavTitle: String
        get() = "🧭 Meta VR Navigator Quick Controls"
    val cardMetaNavSubtitle: String
        get() = if (isDe) "Internes Navigator-Steuerungs-Panel von HorizonOS."
                else "HorizonOS internal navigator control panel."
    val cardMetaNavButton: String
        get() = if (isDe) "Navigator Controls öffnen" else "Open Navigator Controls"

    val cardDevOptionsTitle: String
        get() = if (isDe) "🛠️ Entwickler-Optionen" else "🛠️ Developer Options"
    val cardDevOptionsSubtitle: String
        get() = if (isDe) "Direktsprung in die Entwickleroptionen: USB-Debugging, Kabelloses Debugging, Display-Wiederholraten & Systemprotokolle."
                else "Jump directly into Developer Options: USB debugging, Wireless debugging, refresh rates & system logs."
    val cardDevOptionsButton: String
        get() = if (isDe) "Entwickleroptionen öffnen" else "Open Developer Options"
    val cardDevOptionsBadge: String
        get() = "Dev Options"

    val cardAppsTitle: String
        get() = if (isDe) "📦 Apps & Berechtigungen verwalten"
                else "📦 Manage Apps & Permissions"
    val cardAppsSubtitle: String
        get() = if (isDe) "AOSP-Verwaltung aller installierten Anwendungen: Caches löschen, Speicher inspizieren und Berechtigungen anpassen."
                else "AOSP management of all installed apps: Clear caches, inspect storage, and configure permissions."
    val cardAppsButton: String
        get() = if (isDe) "App-Verwaltung öffnen" else "Open App Management"
    val cardAppsBadge: String
        get() = "AOSP Apps"

    // Tab 1: Bluetooth Scanner & Stack
    val btStatusCardTitle: String
        get() = "Bluetooth Hardware Status"
    val btStateOn: String
        get() = if (isDe) "Aktiv (ON)" else "Active (ON)"
    val btStateOff: String
        get() = if (isDe) "Deaktiviert (OFF)" else "Disabled (OFF)"
    val btDeviceNameLabel: String
        get() = if (isDe) "Geräte-Name:" else "Device Name:"
    val btMacLabel: String
        get() = if (isDe) "MAC-Adresse:" else "MAC Address:"
    val btPrivacyWarning: String
        get() = if (isDe) "02:00:00:00:00:00 ist eine Google/Android Datenschutz-Maskierung für Standard-Apps. Verbinde ADB in Tab 4, um die echte physische Hardware-MAC und tiefere Systemdaten auszulesen."
                else "02:00:00:00:00:00 is a Google/Android privacy mask for standard apps. Connect ADB in Tab 4 to read the real physical hardware MAC and deeper system data."

    val btStackResetButton: String
        get() = "Stack Reset"
    val btCacheClearButton: String
        get() = "Cache (pm)"
    val btAdbStartButton: String
        get() = "⚡ ADB Start"

    fun btBondedDevicesTitle(count: Int): String
        = if (isDe) "Gekoppelte Geräte ($count)" else "Paired Devices ($count)"
    val btBondedDevicesSubtitle: String
        get() = if (isDe) "Tippe auf ein Gerät für Tiefenanalyse & Protokolle"
                else "Tap a device for deep analysis & live traces"
    val btNoBondedDevices: String
        get() = if (isDe) "Keine gekoppelten Bluetooth-Geräte gefunden."
                else "No paired Bluetooth devices found."
    val btRefreshDesc: String
        get() = if (isDe) "Aktualisieren" else "Refresh"
    val btDetailsButton: String
        get() = "Details"
    val btUnpairButton: String
        get() = if (isDe) "Entkoppeln" else "Unpair"

    val btNearbyTitle: String
        get() = if (isDe) "Geräte-Suche in der Nähe" else "Nearby Device Discovery"
    val btNearbySubtitle: String
        get() = if (isDe) "Tippe auf ein gefundenes Gerät zum Koppel-Dialog"
                else "Tap a discovered device to open pairing dialog"
    val btStartScan: String
        get() = if (isDe) "Scan starten" else "Start Scan"
    val btStopScan: String
        get() = if (isDe) "Stoppen" else "Stop"
    val btScanInProgress: String
        get() = if (isDe) "Suche läuft nach sichtbaren Bluetooth-Geräten..."
                else "Scanning for visible Bluetooth devices..."
    val btNoDiscoveredDevices: String
        get() = if (isDe) "Keine Scan-Ergebnisse vorhanden." else "No scan results found."
    val btPairDetailsButton: String
        get() = if (isDe) "Koppeln / Details" else "Pair / Details"

    // Device Detail Dialog
    val devDetailBonded: String
        get() = if (isDe) "Gekoppelt" else "Paired"
    val devDetailBonding: String
        get() = if (isDe) "Kopplung läuft..." else "Pairing..."
    val devDetailNotBonded: String
        get() = if (isDe) "Nicht gekoppelt" else "Not paired"

    val devDetailTypeLabel: String
        get() = if (isDe) "Geräte-Typ:" else "Device Type:"
    val devDetailMajorClassLabel: String
        get() = if (isDe) "Haupt-Kategorie:" else "Major Category:"
    val devDetailClassDetailsLabel: String
        get() = if (isDe) "Klassen-Details:" else "Class Details:"
    val devDetailSignalLabel: String
        get() = if (isDe) "Signalstärke (RSSI):" else "Signal Strength (RSSI):"

    val devDetailPairButton: String
        get() = if (isDe) "Gerät Koppeln" else "Pair Device"
    val devDetailScanGattButton: String
        get() = if (isDe) "GATT Scannen" else "Scan GATT"
    val devDetailRecognizedProfiles: String
        get() = if (isDe) "Erkannte Bluetooth-Profile" else "Recognized Bluetooth Profiles"
    val devDetailCompendiumBadge: String
        get() = if (isDe) "📚 Offline-Kompendium" else "📚 Offline Compendium"
    val devDetailNoUuids: String
        get() = if (isDe) "Keine Standard-UUIDs im Werbe-Paket enthalten."
                else "No standard UUIDs in advertising packet."
    val devDetailCompendiumButton: String
        get() = if (isDe) "📚 Details ℹ️" else "📚 Details ℹ️"
    val devDetailGattAttributes: String
        get() = if (isDe) "GATT Attribute & Dekodierte Werte" else "GATT Attributes & Decoded Values"
    val devDetailLiveTracesTitle: String
        get() = if (isDe) "Echtzeit Koppel-Traces" else "Real-time Pairing Traces"
    val devDetailStartAdbButton: String
        get() = if (isDe) "⚡ ADB Starten" else "⚡ Start ADB"
    val devDetailDeepTracesBadge: String
        get() = if (isDe) "Tiefe Traces (ADB)" else "Deep Traces (ADB)"
    val devDetailFrameworkLogsBadge: String
        get() = if (isDe) "Framework Logs" else "Framework Logs"
    val devDetailWaitingLogs: String
        get() = if (isDe) "Warte auf Kopplungs-Aktivität für dieses Gerät..."
                else "Waiting for pairing activity for this device..."

    // Compendium Dialog
    val compendiumFunctionTitle: String
        get() = if (isDe) "Funktion & Verwendungszweck" else "Function & Purpose"
    val compendiumTechTitle: String
        get() = if (isDe) "Technische Architektur & VR-Relevanz"
                else "Technical Architecture & VR Relevance"
    val compendiumBackButton: String
        get() = if (isDe) "Zurück zum Gerät" else "Back to Device"

    // Tab 2: Logcat Terminal
    val logcatPresetAll: String
        get() = if (isDe) "Alle Logs" else "All Logs"
    val logcatPresetPairing: String
        get() = if (isDe) "BT Kopplung" else "BT Pairing"
    val logcatPresetGatt: String
        get() = "BLE / GATT"
    val logcatPresetBtAll: String
        get() = if (isDe) "Bluetooth Alles" else "All Bluetooth"

    val logcatFilterPlaceholder: String
        get() = if (isDe) "Logzeilen filtern..." else "Filter log lines..."
    val logcatClearDesc: String
        get() = if (isDe) "Leeren" else "Clear"
    val logcatCopyDesc: String
        get() = if (isDe) "Kopieren" else "Copy"
    fun logcatBufferedCount(count: Int): String
        = if (isDe) "$count Logzeilen gepuffert" else "$count log lines buffered"
    val logcatAutoScroll: String
        get() = "Auto-Scroll"
    val logcatWaitingStream: String
        get() = if (isDe) "Warte auf Logcat-Einträge...\n(Tipp: Führe einen Kopplungs-Versuch durch oder wähle 'Alle Logs')"
                else "Waiting for logcat entries...\n(Tip: Initiate a pairing attempt or select 'All Logs')"
    val logcatPaused: String
        get() = if (isDe) "Logcat pausiert." else "Logcat paused."

    // Tab 3: ADB Assistant
    val adbStatusCardTitle: String
        get() = if (isDe) "Aktueller ADB Verbindungsstatus" else "Current ADB Connection Status"
    val adbConnectedDesc: String
        get() = if (isDe) "Aktiv verbunden mit ADB Daemon auf 127.0.0.1:5555"
                else "Actively connected to ADB Daemon at 127.0.0.1:5555"
    fun adbConnectingDesc(host: String, port: Int): String
        = if (isDe) "Verbindungsaufbau zu $host:$port..." else "Connecting to $host:$port..."
    val adbWaitingAuthDesc: String
        get() = if (isDe) "Warte auf Bestätigung! Bitte schau in die Brille: Erlaube den USB/ADB-Zugriff im Pop-Up-Dialog."
                else "Waiting for confirmation! Please look inside headset: Allow USB/ADB access in pop-up dialog."
    val adbDisconnectedDesc: String
        get() = if (isDe) "Getrennt. (Direktmodus aktiv – Systemmenüs funktionieren immer!)"
                else "Disconnected. (Direct mode active – system menus always work!)"

    val adbMethodsTitle: String
        get() = if (isDe) "Verbindungs-Methoden & Steuerung" else "Connection Methods & Controls"
    val adbAutoConnectBtn: String
        get() = "Auto-Connect"
    val adbDisconnectBtn: String
        get() = if (isDe) "Trennen" else "Disconnect"
    val adbWirelessPairingBtn: String
        get() = if (isDe) "Wireless Debugging Pairing (6-stelliger PIN)"
                else "Wireless Debugging Pairing (6-digit PIN)"
    val adbWebAdbBtn: String
        get() = if (isDe) "WebADB im Quest Browser öffnen" else "Open WebADB in Quest Browser"

    val adbCableModeTitle: String
        get() = if (isDe) "Kabel-Modus (Wenn Quest am PC angeschlossen ist)"
                else "Cable Mode (When Quest is connected to PC)"
    val adbCableModeDesc: String
        get() = if (isDe) "Wenn die Brille am PC hängt, kannst du Port 5555 mit einem einzigen Terminal-Befehl aktivieren. Danach bleibt der Port bis zum Neustart der Quest aktiv:"
                else "When the headset is plugged into a PC, activate port 5555 with a single terminal command. The port remains active until headset reboot:"
    fun adbCmdCopied(cmd: String): String
        = if (isDe) "Befehl kopiert: $cmd" else "Command copied: $cmd"

    // Wireless Pairing Dialog
    val wirelessPairingTitle: String
        get() = "Wireless Debugging Pairing"
    val wirelessPairingInstructions: String
        get() = if (isDe) "1. Öffne auf der Quest: Einstellungen -> Entwickleroptionen -> Kabelloses Debugging.\n2. Tippe auf 'Gerät mit Kopplungscode koppeln'.\n3. Trage den angezeigten Port und den 6-stelligen Code hier ein:"
                else "1. On Quest open: Settings -> Developer Options -> Wireless debugging.\n2. Tap 'Pair device with pairing code'.\n3. Enter the displayed port and 6-digit code here:"
    val wirelessHostLabel: String
        get() = if (isDe) "IP-Adresse (meist 127.0.0.1)" else "IP Address (usually 127.0.0.1)"
    val wirelessPortLabel: String
        get() = if (isDe) "Kopplungs-Port (z.B. 38491)" else "Pairing Port (e.g. 38491)"
    val wirelessCodeLabel: String
        get() = if (isDe) "6-stelliger Code (z.B. 123456)" else "6-digit Code (e.g. 123456)"
    val wirelessPairNowBtn: String
        get() = if (isDe) "Jetzt Koppeln" else "Pair Now"
    val wirelessCancelBtn: String
        get() = if (isDe) "Abbrechen" else "Cancel"
}
