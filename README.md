# Quest Settings & BT Toolbox

Eine maßgeschneiderte Android-App (APK) für die Meta Quest (Quest 2, 3, 3S, Pro) zur **Bluetooth-Tiefenanalyse**, **Kopplungs-Diagnose** und zum **Direktaufruf aller versteckten Android- und Meta-Systemmenüs**.

---

## 📦 Fertige APK
Die aktuelle Version liegt direkt im Projektordner:
* **[QuestSettingsToolbox.apk](file:///d:/Developement/Workspace-Anti/AG-Android_SystemSettings-APK/QuestSettingsToolbox.apk)**

---

## 🚀 Neuheiten in Version 1.4.0

### ⭐ Direkter Schnellstart der nativen "Android settings" Quest-App
* **Primärer 1. Button auf der Hauptseite**:
  * Direkter 1-Klick-Start für die auf Meta Quest Headsets vorhandene App **„Android settings“**.
  * Ermöglicht vollen Zugriff auf das native AOSP-Gesamtsystem (Apps, Speicher, Konten, Systemdialoge, Zugriffsrechte & erweiterte Einstellungen).
* **Multi-Tier Starter & automatische Erkennung**:
  * Erkennt dynamisch per PackageManager installierte System- und Sideload-Settings-Apps.
  * Automatische Fallbacks über Component-Intents sowie ADB-Shell-Befehle falls Intents beschränkt sind.
  * Vollständige bilinguale Lokalisierung (Deutsch 🇩🇪 / Englisch 🇬🇧).

---

## 🚀 Neuheiten in Version 1.3.0

### 🌐 Zweisprachige Benutzeroberfläche & Sprachschalter (🇬🇧 EN / 🇩🇪 DE)
* **Standard-Sprache Englisch**: Für internationale Meta Quest Nutzer startet die App standardmäßig auf Englisch.
* **1-Klick Landesflaggen-Schalter**: Direkt in der oberen App-Bar kann jederzeit nahtlos per Flaggen-Button (`🇬🇧 EN` / `🇩🇪 DE`) zwischen Englisch und Deutsch umgeschaltet werden.
* **Persistent**: Die gewählte Spracheinstellung wird automatisch dauerhaft gespeichert (`SharedPreferences`).
* **Vollständiges Offline-Kompendium übersetzt**: Alle Bluetooth-Profile (A2DP, HID, AVRCP, HFP/HSP, Battery Service, Generic Access/Attribute, Device Info, Custom Services), Gerätetypen, GATT-Attribute und technischen Erläuterungen sind vollständig auf Englisch und Deutsch verfügbar!

---

## 🚀 Neuheiten in Version 1.2.0

### 1. Aufdeckung der verwaisten AOSP Phone Settings & Meta-Panels
* **📱 Original Android Phone Settings (AOSP Spa-Architektur)**:
  * Das geheime Menü, das Tools wie QGO öffnen, wenn keine ADB-Verbindung besteht!
  * Ermöglicht vollen Zugriff auf das moderne Android 14 Settings-Framework ohne Meta-Overlay.
* **🥽 Meta VR Native Bluetooth Panel**:
  * Direkter Intent-Call auf `com.oculus.action.BLUETOOTH_SETTINGS`.
* **⚡ Meta VR Quick Settings Overlay**:
  * Direktsprung in das schwebende Quick-Controls Panel (`com.oculus.action.QUICK_SETTINGS`).
* **🧭 Meta VR Navigator Controls**:
  * Internes Navigator-Steuerungs-Panel von HorizonOS.
* **⚙️ AOSP Root-Einstellungen & Entwickleroptionen**:
  * Alle Systemparameter, Töne, Speicher und Debugging-Schalter.

### 2. Auflösung des MAC-Adress-Rätsels (`02:00:00:00:00:00`)
* Android maskiert die Bluetooth-Hardware-Adresse (`02:00:00:00:00:00`) ab Android 6 als Datenschutzmaßnahme für Dritt-Apps.
* Unsere App liest die echte physische Hardware-MAC-Adresse über System-Settings und Dumpsys aus (z.B. **`C0:DD:8A:97:FE:B3`**) und kennzeichnet die Maskierung transparent.

### 3. Bluetooth Tiefenanalyse & Koppel-Popup
* **Klick auf jedes gefundene oder gekoppelte Gerät** öffnet ein detailliertes Diagnose-Fenster:
  * **Geräte-Typ**: Classic BR/EDR, BLE Low Energy oder Dual Mode.
  * **Geräte-Klassen**: Audio/Headset, Tastatur, Maus, Smartphone, etc.
  * **Signalstärke (RSSI)** in dBm.
  * **Erkannte Profile & Services**: Aufschlüsselung von A2DP, HID, AVRCP, HFP, GATT Battery Service etc. mit verständlichen Erklärungen und **direkten Links zu den offiziellen Bluetooth SIG Spezifikationen**.
  * **Live GATT Scan**: Liest alle primären und sekundären GATT-Attribute des BLE-Geräts aus.
  * **🔗 Gerät Koppeln (Bond)** & **❌ Entkoppeln (Unpair)** per Knopfdruck.
  * **Echtzeit Koppel-Traces**: Direkt im Dialog eingebettetes Logcat-Terminal, das nur Traces dieses spezifischen Geräts anzeigt.
  * **Fehlerübersetzer & Lösungstipps**: Übersetzt kryptische Unbond-Fehlercodes (Auth Failed, Timeout, Vom Gerät abgelehnt, Repeated Attempts) in verständliches Deutsch mit Handlungsempfehlungen.
  * **ADB-Tiefen-Indikator**: Zeigt an, ob tiefe HCI-Kernel-Pakete geloggt werden oder Standard-Framework-Logs.
