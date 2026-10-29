package com.quest.systemsettings.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.quest.systemsettings.localization.LanguageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BtDeviceInfo(
    val device: BluetoothDevice,
    val name: String,
    val address: String,
    val bondState: String,
    val isBonded: Boolean,
    val deviceType: String,
    val rssi: Int? = null
)

data class BtAdapterStatus(
    val isEnabled: Boolean,
    val name: String,
    val address: String,
    val isAnonymized: Boolean,
    val stateString: String
)

class BluetoothManagerHelper(
    private val context: Context,
    private val scope: CoroutineScope,
    private val adbClient: com.quest.systemsettings.adb.AdbClient? = null
) {
    private val tag = "BtManagerHelper"
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val adapter: BluetoothAdapter? get() = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()

    private val _adapterStatus = MutableStateFlow(queryStatus())
    val adapterStatus: StateFlow<BtAdapterStatus> = _adapterStatus.asStateFlow()

    private val _bondedDevices = MutableStateFlow<List<BtDeviceInfo>>(emptyList())
    val bondedDevices: StateFlow<List<BtDeviceInfo>> = _bondedDevices.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BtDeviceInfo>>(emptyList())
    val discoveredDevices: StateFlow<List<BtDeviceInfo>> = _discoveredDevices.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private var realHardwareMac: String? = null

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(c: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    refresh()
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    refresh()
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    _isDiscovering.value = true
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _isDiscovering.value = false
                }
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                    device?.let { addDiscoveredDevice(it, if (rssi != Short.MIN_VALUE.toInt()) rssi else null) }
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_FOUND)
        }
        try {
            context.registerReceiver(receiver, filter)
        } catch (_: Exception) {}

        adbClient?.let { client ->
            scope.launch {
                client.connectionState.collect { state ->
                    if (state is com.quest.systemsettings.adb.AdbConnectionState.Connected) {
                        fetchRealMacAddress()
                    }
                }
            }
        }

        fetchRealMacAddress()
        refresh()
    }

    private fun fetchRealMacAddress() {
        scope.launch(Dispatchers.IO) {
            // Method 1: Settings.Secure
            var mac: String? = null
            try {
                mac = Settings.Secure.getString(context.contentResolver, "bluetooth_address")
            } catch (_: Exception) {}

            // Method 2: ADB Shell (if connected)
            if ((mac.isNullOrBlank() || mac == "02:00:00:00:00:00") && adbClient != null) {
                try {
                    val adbOut = adbClient.executeCommand("settings get secure bluetooth_address 2>/dev/null || dumpsys bluetooth_manager | grep -m 1 -i 'address:'")
                    val regex = Regex("([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})")
                    val match = regex.find(adbOut)
                    if (match != null) {
                        mac = match.value.uppercase()
                    }
                } catch (_: Exception) {}
            }

            // Method 3: Local Shell dumpsys / getprop fallback
            if (mac.isNullOrBlank() || mac == "02:00:00:00:00:00") {
                try {
                    val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "settings get secure bluetooth_address 2>/dev/null || dumpsys bluetooth_manager | grep -m 1 -i 'address:'"))
                    val out = process.inputStream.bufferedReader().use { it.readText() }
                    val regex = Regex("([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})")
                    val match = regex.find(out)
                    if (match != null) {
                        mac = match.value.uppercase()
                    }
                } catch (_: Exception) {}
            }

            if (!mac.isNullOrBlank() && mac != "02:00:00:00:00:00") {
                realHardwareMac = mac
                refresh()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun refresh() {
        _adapterStatus.value = queryStatus()
        _bondedDevices.value = queryBondedDevices()
    }

    @SuppressLint("MissingPermission")
    private fun queryStatus(): BtAdapterStatus {
        val noAdapter = if (LanguageManager.isGerman) "Kein Adapter" else "No Adapter"
        val notAvailable = if (LanguageManager.isGerman) "Nicht verfügbar" else "Not Available"
        val a = adapter ?: return BtAdapterStatus(false, noAdapter, "--", false, notAvailable)
        val isEn = a.isEnabled
        val name = try { a.name ?: "Quest Bluetooth" } catch (_: Exception) { "Quest Bluetooth" }
        val rawAddr = try { a.address ?: "--" } catch (_: Exception) { "--" }

        val isAnonymized = rawAddr == "02:00:00:00:00:00"
        val displayAddr = realHardwareMac ?: rawAddr

        val stateStr = if (isEn) (if (LanguageManager.isGerman) "Aktiv (ON)" else "Active (ON)")
                       else (if (LanguageManager.isGerman) "Deaktiviert (OFF)" else "Disabled (OFF)")
        return BtAdapterStatus(isEn, name, displayAddr, isAnonymized, stateStr)
    }

    @SuppressLint("MissingPermission")
    private fun queryBondedDevices(): List<BtDeviceInfo> {
        val a = adapter ?: return emptyList()
        return try {
            a.bondedDevices?.map { dev ->
                mapDevice(dev, isBonded = true, rssi = null)
            } ?: emptyList()
        } catch (e: Exception) {
            Log.w(tag, "Failed to get bonded devices: ${e.message}")
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    private fun addDiscoveredDevice(device: BluetoothDevice, rssi: Int?) {
        val info = mapDevice(device, isBonded = false, rssi = rssi)
        val current = _discoveredDevices.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.address == info.address }
        if (existingIndex >= 0) {
            current[existingIndex] = info
        } else {
            current.add(info)
        }
        _discoveredDevices.value = current
    }

    @SuppressLint("MissingPermission")
    private fun mapDevice(dev: BluetoothDevice, isBonded: Boolean, rssi: Int?): BtDeviceInfo {
        val unknown = if (LanguageManager.isGerman) "Unbekanntes Gerät" else "Unknown Device"
        val devName = try { dev.name ?: unknown } catch (_: Exception) { unknown }
        val bondStr = when (dev.bondState) {
            BluetoothDevice.BOND_BONDED -> if (LanguageManager.isGerman) "Gekoppelt" else "Paired"
            BluetoothDevice.BOND_BONDING -> if (LanguageManager.isGerman) "Kopplung läuft..." else "Pairing..."
            else -> if (LanguageManager.isGerman) "Nicht gekoppelt" else "Not paired"
        }
        val typeStr = when (dev.type) {
            BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic BT"
            BluetoothDevice.DEVICE_TYPE_LE -> "BLE"
            BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual Mode"
            else -> "Standard"
        }
        return BtDeviceInfo(dev, devName, dev.address, bondStr, isBonded, typeStr, rssi)
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery(): Boolean {
        val a = adapter ?: return false
        _discoveredDevices.value = emptyList()
        return try {
            if (a.isDiscovering) a.cancelDiscovery()
            a.startDiscovery()
        } catch (e: Exception) {
            Log.e(tag, "Start discovery failed: ${e.message}")
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        try {
            adapter?.cancelDiscovery()
        } catch (_: Exception) {}
        _isDiscovering.value = false
    }

    @SuppressLint("MissingPermission")
    fun unpairDevice(address: String): Boolean {
        val a = adapter ?: return false
        try {
            val dev = a.getRemoteDevice(address)
            val method = dev.javaClass.getMethod("removeBond")
            val result = method.invoke(dev) as? Boolean ?: false
            refresh()
            return result
        } catch (e: Exception) {
            Log.e(tag, "removeBond failed: ${e.message}")
            return false
        }
    }

    fun destroy() {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) {}
    }
}
