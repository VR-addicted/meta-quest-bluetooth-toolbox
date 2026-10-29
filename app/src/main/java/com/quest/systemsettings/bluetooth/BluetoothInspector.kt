package com.quest.systemsettings.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.quest.systemsettings.localization.LanguageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets
import java.util.UUID

data class DetailedBtDevice(
    val name: String,
    val address: String,
    val bondState: Int,
    val rawDeviceType: Int,
    val majorClassCode: Int?,
    val deviceClassCode: Int?,
    val uuids: List<ServiceProfileInfo>,
    val gattServices: List<GattServiceInfo> = emptyList(),
    val rssi: Int? = null
) {
    val bondStateString: String
        get() = when (bondState) {
            BluetoothDevice.BOND_BONDED -> if (LanguageManager.isGerman) "Gekoppelt (BONDED)" else "Paired (BONDED)"
            BluetoothDevice.BOND_BONDING -> if (LanguageManager.isGerman) "Wird gekoppelt (BONDING...)" else "Pairing (BONDING...)"
            else -> if (LanguageManager.isGerman) "Nicht gekoppelt (BOND_NONE)" else "Not paired (BOND_NONE)"
        }

    val deviceType: String
        get() = when (rawDeviceType) {
            BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic Bluetooth (BR/EDR)"
            BluetoothDevice.DEVICE_TYPE_LE -> "Bluetooth Low Energy (BLE)"
            BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual Mode (BR/EDR + BLE)"
            else -> if (LanguageManager.isGerman) "Unbekannt" else "Unknown"
        }

    val majorClass: String
        get() = BluetoothInspector.parseMajorClass(majorClassCode)

    val deviceClassString: String
        get() = BluetoothInspector.parseDeviceClass(deviceClassCode)
}

data class ServiceProfileInfo(
    val uuid: String,
    val nameEn: String,
    val nameDe: String,
    val shortName: String,
    val descriptionEn: String,
    val descriptionDe: String,
    val technicalDetailsEn: String,
    val technicalDetailsDe: String
) {
    val name: String
        get() = if (LanguageManager.isGerman) nameDe else nameEn

    val description: String
        get() = if (LanguageManager.isGerman) descriptionDe else descriptionEn

    val technicalDetails: String
        get() = if (LanguageManager.isGerman) technicalDetailsDe else technicalDetailsEn
}

data class GattServiceInfo(
    val uuid: String,
    val name: String,
    val isPrimary: Boolean,
    val characteristics: List<GattCharacteristicInfo>
) {
    val type: String
        get() = if (isPrimary) {
            if (LanguageManager.isGerman) "Primär" else "Primary"
        } else {
            if (LanguageManager.isGerman) "Sekundär" else "Secondary"
        }
}

data class GattCharacteristicInfo(
    val uuid: String,
    val name: String,
    val propertiesString: String,
    val decodedValue: String? = null,
    val hexValue: String? = null
)

class BluetoothInspector(private val context: Context) {

    private val tag = "BtInspector"
    private var activeGatt: BluetoothGatt? = null

    private val _selectedDeviceDetails = MutableStateFlow<DetailedBtDevice?>(null)
    val selectedDeviceDetails: StateFlow<DetailedBtDevice?> = _selectedDeviceDetails.asStateFlow()

    private val _selectedCompendiumProfile = MutableStateFlow<ServiceProfileInfo?>(null)
    val selectedCompendiumProfile: StateFlow<ServiceProfileInfo?> = _selectedCompendiumProfile.asStateFlow()

    private val _pairingStatusMessage = MutableStateFlow<String?>(null)
    val pairingStatusMessage: StateFlow<String?> = _pairingStatusMessage.asStateFlow()

    private val _isGattScanning = MutableStateFlow(false)
    val isGattScanning: StateFlow<Boolean> = _isGattScanning.asStateFlow()

    @SuppressLint("MissingPermission")
    fun inspectDevice(device: BluetoothDevice, rssi: Int? = null) {
        val unknown = if (LanguageManager.isGerman) "Unbekanntes Gerät" else "Unknown Device"
        val name = try { device.name ?: unknown } catch (_: Exception) { unknown }

        val btClass = device.bluetoothClass

        // Parse known UUIDs
        val rawUuids = try { device.uuids } catch (_: Exception) { null }
        val profileList = parseUuids(rawUuids)

        _selectedDeviceDetails.value = DetailedBtDevice(
            name = name,
            address = device.address,
            bondState = device.bondState,
            rawDeviceType = device.type,
            majorClassCode = btClass?.majorDeviceClass,
            deviceClassCode = btClass?.deviceClass,
            uuids = profileList,
            rssi = rssi
        )
    }

    @SuppressLint("MissingPermission")
    fun pairDevice(device: BluetoothDevice): Boolean {
        return try {
            _pairingStatusMessage.value = if (LanguageManager.isGerman) {
                "Starte Kopplungsvorgang mit ${device.address}..."
            } else {
                "Initiating pairing process with ${device.address}..."
            }
            val result = device.createBond()
            if (result) {
                _pairingStatusMessage.value = if (LanguageManager.isGerman) {
                    "Kopplungs-Handshake initiiert. Bitte Bestätigung am Gerät oder Headset beachten."
                } else {
                    "Pairing handshake initiated. Please confirm prompt on device or headset."
                }
            } else {
                _pairingStatusMessage.value = if (LanguageManager.isGerman) {
                    "Kopplungs-Aufruf fehlgeschlagen (evtl. bereits gekoppelt oder blockiert)."
                } else {
                    "Pairing request failed (may already be paired or blocked)."
                }
            }
            result
        } catch (e: Exception) {
            _pairingStatusMessage.value = if (LanguageManager.isGerman) "Fehler: ${e.message}" else "Error: ${e.message}"
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun discoverGattServices(device: BluetoothDevice) {
        _isGattScanning.value = true
        activeGatt?.close()

        activeGatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    _isGattScanning.value = false
                    gatt.close()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                _isGattScanning.value = false
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val gattList = gatt.services.map { svc ->
                        val serviceName = getKnownGattServiceName(svc.uuid)
                        val charList = svc.characteristics.map { ch ->
                            val rawBytes = ch.value
                            val hex = rawBytes?.let { formatHex(it) }
                            val decoded = rawBytes?.let { decodeCharacteristicBytes(ch.uuid, it) }

                            GattCharacteristicInfo(
                                uuid = ch.uuid.toString(),
                                name = getKnownCharacteristicName(ch.uuid),
                                propertiesString = parseCharacteristicProperties(ch.properties),
                                decodedValue = decoded,
                                hexValue = hex
                            )
                        }

                        GattServiceInfo(
                            uuid = svc.uuid.toString(),
                            name = serviceName,
                            isPrimary = svc.type == BluetoothGattService.SERVICE_TYPE_PRIMARY,
                            characteristics = charList
                        )
                    }
                    _selectedDeviceDetails.value = _selectedDeviceDetails.value?.copy(gattServices = gattList)
                }
            }
        })
    }

    fun openCompendiumEntry(profile: ServiceProfileInfo) {
        _selectedCompendiumProfile.value = profile
    }

    fun closeCompendiumEntry() {
        _selectedCompendiumProfile.value = null
    }

    fun closeGatt() {
        try {
            activeGatt?.close()
        } catch (_: Exception) {}
        activeGatt = null
        _isGattScanning.value = false
    }

    fun clearSelection() {
        closeGatt()
        _selectedDeviceDetails.value = null
        _selectedCompendiumProfile.value = null
        _pairingStatusMessage.value = null
    }

    private fun parseUuids(parcelUuids: Array<ParcelUuid>?): List<ServiceProfileInfo> {
        if (parcelUuids == null) return emptyList()
        return parcelUuids.map { pUuid ->
            val uStr = pUuid.uuid.toString().lowercase()
            when {
                uStr.startsWith("0000110b") -> ServiceProfileInfo(
                    uuid = uStr,
                    nameEn = "A2DP Sink (Audio Receiver)",
                    nameDe = "A2DP Sink (Audio-Empfänger)",
                    shortName = "A2DP",
                    descriptionEn = "Advanced Audio Distribution Profile: Streams uncompressed or high-bitrate stereo audio in real-time (e.g. music/gaming sound).",
                    descriptionDe = "Advanced Audio Distribution Profile: Überträgt unkomprimiertes bzw. hochkomprimiertes Stereo-Audio in Echtzeit (z.B. Musik/Gaming-Sound).",
                    technicalDetailsEn = "Architecture: Built on L2CAP and AVDTP. Typical codecs are SBC (standard), AAC, aptX, or LDAC. Standard A2DP can introduce 100-200ms latency on VR headsets; Low Latency codecs (aptX LL / LE Audio) are preferred for gaming.",
                    technicalDetailsDe = "Architektur: Basiert auf L2CAP und AVDTP. Typische Codecs sind SBC (Standard), AAC, aptX oder LDAC. In VR-Brillen kann A2DP eine Latenz von 100-200ms haben, weshalb für Gaming LL-Codecs (aptX LL) bevorzugt werden."
                )
                uStr.startsWith("0000110a") -> ServiceProfileInfo(
                    uuid = uStr,
                    nameEn = "A2DP Source (Audio Source)",
                    nameDe = "A2DP Source (Audio-Quelle)",
                    shortName = "A2DP Source",
                    descriptionEn = "Transmits stereo audio streams from this headset to external receivers (e.g. headphones, speakers).",
                    descriptionDe = "Sendet Audio-Streams von diesem Gerät an externe Empfänger.",
                    technicalDetailsEn = "Source role for real-time audio transport sessions via AVDTP.",
                    technicalDetailsDe = "Quelle für Audio-Streaming über AVDTP Transport-Sessions."
                )
                uStr.startsWith("00001124") -> ServiceProfileInfo(
                    uuid = uStr,
                    nameEn = "HID (Human Interface Device)",
                    nameDe = "HID (Human Interface Device)",
                    shortName = "HID",
                    descriptionEn = "Standard protocol for gamepads, keyboards, mice, and VR motion controllers. Delivers ultra-low latency input without buffering.",
                    descriptionDe = "Standard-Protokoll für Gamepads, Tastaturen, Mäuse und VR-Controller. Bietet minimale Latenz.",
                    technicalDetailsEn = "Utilizes two L2CAP channels: Control (PSM 0x0011) and Interrupt (PSM 0x0013). Report Maps define hardware buttons, joysticks, triggers, and axes.",
                    technicalDetailsDe = "Nutzt zwei L2CAP-Kanäle: Control (PSM 0x0011) und Interrupt (PSM 0x0013). Report-Maps definieren Tastencodes, Achsen und Buttons. Ideal für Controller, da keine Pufferung erfolgt."
                )
                uStr.startsWith("0000110e") -> ServiceProfileInfo(
                    uuid = uStr,
                    nameEn = "AVRCP (Audio/Video Remote Control)",
                    nameDe = "AVRCP (Fernsteuerung)",
                    shortName = "AVRCP",
                    descriptionEn = "Audio/Video Remote Control: Controls Play, Pause, Track skipping, and absolute volume synchronization.",
                    descriptionDe = "Audio/Video Remote Control: Steuert Play, Pause, Titelsprung und synchrone Lautstärkeregelung.",
                    technicalDetailsEn = "Built on AVCTP. Supports metadata transmission (track title, artist) and bidirectional hardware volume lock between headset and audio sink.",
                    technicalDetailsDe = "Basiert auf AVCTP. Unterstützt Metadaten-Transfer (Titelname, Künstler) und absolute Lautstärkesynchronisation zwischen Headset und Brille."
                )
                uStr.startsWith("0000111e") || uStr.startsWith("00001108") -> ServiceProfileInfo(
                    uuid = uStr,
                    nameEn = "HFP / HSP (Hands-Free & Headset)",
                    nameDe = "HFP / HSP (Headset & Freisprechen)",
                    shortName = "HFP",
                    descriptionEn = "Hands-Free & Headset Profile: Handles bidirectional voice communication and microphone input.",
                    descriptionDe = "Hands-Free & Headset Profile: Unterstützt bidirektionale Sprachkommunikation (Mikrofon).",
                    technicalDetailsEn = "Operates over SCO/eSCO (Synchronous Connection Oriented) channels using mSBC (16 kHz Wideband Audio) or CVSD (8 kHz). Note: Activating microphone input often forces Android from high-res stereo A2DP to low-bitrate mono HFP voice audio.",
                    technicalDetailsDe = "Arbeitet über SCO/eSCO (Synchronous Connection Oriented) Kanäle mit mSBC (16 kHz Wideband Audio) oder CVSD (8 kHz). Hinweis: Wenn das Mikrofon aktiv ist, schaltet Android oft von High-Res A2DP auf Mono-HFP um."
                )
                uStr.startsWith("0000180f") -> ServiceProfileInfo(
                    uuid = uStr,
                    nameEn = "GATT Battery Service (BAS)",
                    nameDe = "GATT Battery Service",
                    shortName = "BAS",
                    descriptionEn = "Reads current battery percentage and receives live battery level notifications.",
                    descriptionDe = "Liest den aktuellen Ladezustand des Akkus in Prozent aus.",
                    technicalDetailsEn = "Characteristic 0x2A19 returns an unsigned 8-bit integer (0 to 100%) and supports GATT Notify subscriptions.",
                    technicalDetailsDe = "Charakteristik 0x2A19 gibt einen vorzeichenlosen 8-Bit-Wert (0 bis 100%) zurück und unterstützt Benachrichtigungen (Notify) bei Akkuänderungen."
                )
                uStr.startsWith("00001800") -> ServiceProfileInfo(
                    uuid = uStr,
                    nameEn = "GATT Generic Access (GAP)",
                    nameDe = "GATT Generic Access (GAP)",
                    shortName = "GAP",
                    descriptionEn = "Core BLE service defining device name, appearance icon, and connection intervals.",
                    descriptionDe = "Basis-Dienst für Gerätenamen, Erscheinungsbild (Appearance) und Verbindungsparameter.",
                    technicalDetailsEn = "Defines BLE Central/Peripheral roles and fundamental advertising properties.",
                    technicalDetailsDe = "Definiert die Rollen (Peripheral/Central) und fundamentale Werbe-Eigenschaften im BLE-Stack."
                )
                uStr.startsWith("00001801") -> ServiceProfileInfo(
                    uuid = uStr,
                    nameEn = "GATT Generic Attribute",
                    nameDe = "GATT Generic Attribute",
                    shortName = "GATT",
                    descriptionEn = "Service discovery and client notification mechanism when GATT services change.",
                    descriptionDe = "Dienst-Discovery und Benachrichtigungen über Service-Änderungen.",
                    technicalDetailsEn = "Manages the Service Changed Indication mechanism used for BLE GATT attribute cache invalidation.",
                    technicalDetailsDe = "Verwaltet den Service Changed Indication Mechanismus zur Cache-Invalidierung."
                )
                uStr.startsWith("0000180a") -> ServiceProfileInfo(
                    uuid = uStr,
                    nameEn = "GATT Device Information (DIS)",
                    nameDe = "GATT Device Information",
                    shortName = "DIS",
                    descriptionEn = "Hardware, manufacturer, model, serial, and firmware revision data.",
                    descriptionDe = "Hardware- und Firmware-Informationen des Herstellers.",
                    technicalDetailsEn = "Contains discrete attributes for Manufacturer Name (0x2A29), Model Number (0x2A24), Serial Number (0x2A25), Firmware Revision (0x2A26), and Hardware Revision (0x2A27).",
                    technicalDetailsDe = "Enthält getrennte Attribute für Manufacturer Name (0x2A29), Model Number (0x2A24), Serial Number (0x2A25), Firmware Revision (0x2A26) und Hardware Revision (0x2A27)."
                )
                else -> ServiceProfileInfo(
                    uuid = uStr,
                    nameEn = "Vendor-Specific Custom Service",
                    nameDe = "Herstellerspezifischer Dienst",
                    shortName = "Custom Service",
                    descriptionEn = "Proprietary BLE interface for vendor-specific protocols, RGB lighting, sensors, or firmware updates.",
                    descriptionDe = "Proprietäre Schnittstelle für herstellerspezifische Protokolle, Sensoren oder Firmware-Updates.",
                    technicalDetailsEn = "128-bit UUIDs outside the Bluetooth SIG standard range are used for proprietary accessories (e.g. Corsair iCUE Wireless, Razer HyperSpeed, Oculus Touch controllers).",
                    technicalDetailsDe = "UUIDs außerhalb des Bluetooth SIG 16-Bit-Bereichs werden für proprietäre Protokolle (z.B. Corsair iCUE Wireless, Razer HyperSpeed, Oculus Touch) verwendet."
                )
            }
        }
    }

    private fun getKnownGattServiceName(uuid: UUID): String {
        val s = uuid.toString().lowercase()
        return when {
            s.startsWith("00001800") -> "Generic Access (GAP)"
            s.startsWith("00001801") -> "Generic Attribute (GATT)"
            s.startsWith("0000180a") -> if (LanguageManager.isGerman) "Geräteinformationen (DIS)" else "Device Information (DIS)"
            s.startsWith("0000180f") -> if (LanguageManager.isGerman) "Batteriedienst (BAS)" else "Battery Service (BAS)"
            s.startsWith("00001812") -> "Human Interface Device (HID over GATT)"
            s.startsWith("0000180d") -> if (LanguageManager.isGerman) "Herzfrequenz-Dienst" else "Heart Rate Service"
            else -> "GATT Service (${uuid.toString().substring(0, 8)})"
        }
    }

    private fun getKnownCharacteristicName(uuid: UUID): String {
        val s = uuid.toString().lowercase()
        return when {
            s.startsWith("00002a00") -> if (LanguageManager.isGerman) "Gerätename" else "Device Name"
            s.startsWith("00002a01") -> if (LanguageManager.isGerman) "Erscheinungsbild (Appearance)" else "Appearance"
            s.startsWith("00002a19") -> if (LanguageManager.isGerman) "Batteriestand" else "Battery Level"
            s.startsWith("00002a24") -> if (LanguageManager.isGerman) "Modellnummer" else "Model Number String"
            s.startsWith("00002a25") -> if (LanguageManager.isGerman) "Seriennummer" else "Serial Number String"
            s.startsWith("00002a26") -> if (LanguageManager.isGerman) "Firmware-Version" else "Firmware Revision String"
            s.startsWith("00002a27") -> if (LanguageManager.isGerman) "Hardware-Version" else "Hardware Revision String"
            s.startsWith("00002a28") -> if (LanguageManager.isGerman) "Software-Version" else "Software Revision String"
            s.startsWith("00002a29") -> if (LanguageManager.isGerman) "Herstellername" else "Manufacturer Name String"
            s.startsWith("00002a4a") -> "HID Information"
            s.startsWith("00002a4b") -> "Report Map"
            s.startsWith("00002a4d") -> "Report (Input/Output)"
            s.startsWith("00002a37") -> if (LanguageManager.isGerman) "Herzfrequenz-Messung" else "Heart Rate Measurement"
            else -> "Char (${uuid.toString().substring(0, 8)})"
        }
    }

    private fun parseCharacteristicProperties(props: Int): String {
        val list = mutableListOf<String>()
        if (props and BluetoothGattCharacteristic.PROPERTY_READ != 0) list.add("Read")
        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) list.add("Write")
        if (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) list.add("Notify")
        if (props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) list.add("Indicate")
        return list.joinToString(", ")
    }

    fun decodeCharacteristicBytes(uuid: UUID, bytes: ByteArray): String {
        val s = uuid.toString().lowercase()
        return try {
            when {
                // Battery level: 1 byte unsigned (0-100%)
                s.startsWith("00002a19") && bytes.isNotEmpty() -> {
                    val percent = bytes[0].toInt() and 0xFF
                    if (LanguageManager.isGerman) "$percent% Akkustand" else "$percent% Battery"
                }
                // Text strings (Name, Manufacturer, Model, Firmware)
                s.startsWith("00002a00") || s.startsWith("00002a24") ||
                s.startsWith("00002a26") || s.startsWith("00002a27") ||
                s.startsWith("00002a28") || s.startsWith("00002a29") -> {
                    "\"${String(bytes, StandardCharsets.UTF_8).trim()}\""
                }
                // Appearance: 2 bytes uint16
                s.startsWith("00002a01") && bytes.size >= 2 -> {
                    val value = ((bytes[1].toInt() and 0xFF) shl 8) or (bytes[0].toInt() and 0xFF)
                    val isDe = LanguageManager.isGerman
                    when (value) {
                        0x03C1 -> if (isDe) "Tastatur (Keyboard)" else "Keyboard"
                        0x03C2 -> if (isDe) "Maus (Mouse)" else "Mouse"
                        0x03C3 -> "Joystick"
                        0x03C4 -> "Gamepad"
                        0x0040 -> if (isDe) "Telefon" else "Phone"
                        0x0080 -> "Computer"
                        0x0380 -> "Wearable"
                        else -> if (isDe) "Geräte-Typ: 0x${value.toString(16).uppercase()}" else "Device Type: 0x${value.toString(16).uppercase()}"
                    }
                }
                // Heart rate
                s.startsWith("00002a37") && bytes.isNotEmpty() -> {
                    val bpm = bytes[1].toInt() and 0xFF
                    "$bpm BPM"
                }
                // Default: readable string if ASCII, else hex with byte count
                else -> {
                    val asString = String(bytes, StandardCharsets.UTF_8)
                    if (asString.all { it.code in 32..126 }) {
                        "\"$asString\""
                    } else {
                        val hex = formatHex(bytes)
                        val bytesWord = if (LanguageManager.isGerman) "Bytes" else "bytes"
                        "${bytes.size} $bytesWord: $hex"
                    }
                }
            }
        } catch (_: Exception) {
            formatHex(bytes)
        }
    }

    private fun formatHex(bytes: ByteArray): String {
        return bytes.joinToString(" ") { "%02X".format(it) }
    }

    companion object {
        fun parseMajorClass(major: Int?): String {
            val isDe = LanguageManager.isGerman
            return when (major) {
                BluetoothClass.Device.Major.AUDIO_VIDEO -> if (isDe) "Audio / Video (Kopfhörer, Lautsprecher)" else "Audio / Video (Headphones, Speakers)"
                BluetoothClass.Device.Major.COMPUTER -> "Computer / Tablet / Laptop"
                BluetoothClass.Device.Major.PHONE -> if (isDe) "Smartphone / Telefon" else "Smartphone / Phone"
                BluetoothClass.Device.Major.PERIPHERAL -> if (isDe) "Eingabegerät (Tastatur, Maus, Gamepad)" else "Peripheral (Keyboard, Mouse, Gamepad)"
                BluetoothClass.Device.Major.WEARABLE -> "Wearable (Smartwatch, Tracker)"
                BluetoothClass.Device.Major.HEALTH -> if (isDe) "Gesundheits- / Fitness-Sensor" else "Health / Fitness Sensor"
                BluetoothClass.Device.Major.NETWORKING -> if (isDe) "Netzwerk / Access Point" else "Network / Access Point"
                BluetoothClass.Device.Major.TOY -> if (isDe) "Spielzeug / Controller" else "Toy / Controller"
                else -> if (isDe) "Allgemeines Bluetooth-Gerät" else "General Bluetooth Device"
            }
        }

        fun parseDeviceClass(cls: Int?): String {
            val isDe = LanguageManager.isGerman
            return when (cls) {
                BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET -> if (isDe) "Headset mit Mikrofon" else "Headset with Microphone"
                BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES -> if (isDe) "Stereo-Kopfhörer" else "Stereo Headphones"
                BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER -> if (isDe) "Lautsprecher" else "Loudspeaker"
                BluetoothClass.Device.PERIPHERAL_KEYBOARD -> if (isDe) "Tastatur" else "Keyboard"
                BluetoothClass.Device.PERIPHERAL_POINTING -> if (isDe) "Maus / Zeigegerät" else "Mouse / Pointing Device"
                BluetoothClass.Device.PERIPHERAL_KEYBOARD_POINTING -> if (isDe) "Tastatur & Touchpad Kombi" else "Keyboard & Touchpad Combo"
                BluetoothClass.Device.WEARABLE_WRIST_WATCH -> "Smartwatch"
                else -> {
                    val hex = "0x${cls?.toString(16)?.uppercase() ?: "0"}"
                    if (isDe) "Standard Geräte-Klasse ($hex)" else "Standard Device Class ($hex)"
                }
            }
        }
    }
}
