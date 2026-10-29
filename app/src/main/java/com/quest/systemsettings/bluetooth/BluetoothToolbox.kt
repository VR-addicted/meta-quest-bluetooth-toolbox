package com.quest.systemsettings.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import com.quest.systemsettings.adb.AdbClient
import com.quest.systemsettings.localization.LanguageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BluetoothToolbox(
    private val context: Context,
    private val adbClient: AdbClient
) {
    suspend fun restartBluetoothStack(): String = withContext(Dispatchers.IO) {
        // Try shell command via ADB or local process
        val cmd = "svc bluetooth disable; sleep 1; svc bluetooth enable"
        val result = adbClient.executeCommand(cmd)

        // Also try standard adapter toggle as bonus fallback
        try {
            toggleBluetoothAdapter()
        } catch (_: Exception) {
        }

        if (result.isBlank()) {
            if (LanguageManager.isGerman) {
                "Bluetooth-Dienst neu gestartet (svc bluetooth)"
            } else {
                "Bluetooth service restarted (svc bluetooth)"
            }
        } else {
            result
        }
    }

    suspend fun clearBluetoothCache(): String = withContext(Dispatchers.IO) {
        val cmd = "pm clear com.android.bluetooth"
        val result = adbClient.executeCommand(cmd)
        if (result.contains("Success", ignoreCase = true)) {
            if (LanguageManager.isGerman) {
                "Bluetooth-Cache & Kopplungs-Daten erfolgreich zurückgesetzt (pm clear)!"
            } else {
                "Bluetooth cache & pairing data successfully reset (pm clear)!"
            }
        } else {
            if (LanguageManager.isGerman) {
                "Befehl ausgeführt: $result\n(Hinweis: Benötigt ADB-Rechte falls nicht bereits erteilt)"
            } else {
                "Command executed: $result\n(Note: Requires ADB permissions if not granted yet)"
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun toggleBluetoothAdapter() {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        if (adapter.isEnabled) {
            @Suppress("DEPRECATION")
            adapter.disable()
            Thread.sleep(1000)
            @Suppress("DEPRECATION")
            adapter.enable()
        } else {
            @Suppress("DEPRECATION")
            adapter.enable()
        }
    }
}
