package com.quest.systemsettings.logcat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.quest.systemsettings.adb.AdbClient
import com.quest.systemsettings.adb.AdbConnectionState
import com.quest.systemsettings.localization.LanguageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

enum class LogLevel {
    VERBOSE, DEBUG, INFO, WARN, ERROR, UNKNOWN
}

data class LogEntry(
    val id: Long,
    val rawText: String,
    val level: LogLevel = LogLevel.UNKNOWN
)

enum class BluetoothLogPreset(val tagRegex: Regex?) {
    ALL(null),
    BT_PAIRING(Regex("(?i)BluetoothBondStateMachine|bt_btif|bt_bta|BluetoothDevice|bt_hci|bte|btm|smp|l2cap|hidp|Bonding")),
    BT_GATT(Regex("(?i)GattService|bt_gatt|BluetoothGatt|GattClient")),
    BT_ALL(Regex("(?i)bt_|bluetooth|bluez|GattService|l2cap|smp|hidp|btm|bte"));

    val title: String
        get() = when (this) {
            ALL -> if (LanguageManager.isGerman) "Alle Logs" else "All Logs"
            BT_PAIRING -> if (LanguageManager.isGerman) "BT Kopplung" else "BT Pairing"
            BT_GATT -> "BLE / GATT"
            BT_ALL -> if (LanguageManager.isGerman) "Bluetooth Alles" else "All Bluetooth"
        }
}

class LogcatManager(
    private val context: Context,
    private val adbClient: AdbClient,
    private val scope: CoroutineScope
) {
    private var streamJob: Job? = null
    private var observerJob: Job? = null
    private var nextId = 0L
    private val adbLogcatStreamer by lazy { com.quest.systemsettings.adb.AdbLogcatStreamer(context) }

    private val allLogs = mutableListOf<LogEntry>()
    private val _displayedLogs = MutableStateFlow<List<LogEntry>>(emptyList())
    val displayedLogs: StateFlow<List<LogEntry>> = _displayedLogs.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _selectedPreset = MutableStateFlow(BluetoothLogPreset.BT_PAIRING)
    val selectedPreset: StateFlow<BluetoothLogPreset> = _selectedPreset.asStateFlow()

    private val _searchFilter = MutableStateFlow("")
    val searchFilter: StateFlow<String> = _searchFilter.asStateFlow()

    private val _autoScroll = MutableStateFlow(true)
    val autoScroll: StateFlow<Boolean> = _autoScroll.asStateFlow()

    private val maxBufferLines = 1500

    init {
        // Automatically switch between local fallback and deep ADB system logcat when ADB state changes
        observerJob = scope.launch {
            adbClient.connectionState.collect { state ->
                when (state) {
                    is AdbConnectionState.Connected -> {
                        val initMsg = if (LanguageManager.isGerman) {
                            "[System] ADB Verbindung aktiv (127.0.0.1:5555). Starte tiefen Bluetooth-Systemtrace..."
                        } else {
                            "[System] ADB connection active (127.0.0.1:5555). Starting deep Bluetooth system trace..."
                        }
                        appendLog(LogEntry(nextId++, initMsg, LogLevel.INFO))
                        restartStreaming()
                    }
                    is AdbConnectionState.Disconnected, is AdbConnectionState.Error -> {
                        if (_isStreaming.value) {
                            restartStreaming()
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun restartStreaming() {
        stopStreaming()
        synchronized(allLogs) {
            allLogs.clear()
        }
        _displayedLogs.value = emptyList()
        startStreaming()
    }

    fun startStreaming() {
        val oldJob = streamJob
        if (oldJob != null && oldJob.isActive) {
            oldJob.cancel()
        }
        _isStreaming.value = true

        streamJob = scope.launch(Dispatchers.IO) {
            val isAdb = adbClient.connectionState.value is AdbConnectionState.Connected

            if (isAdb) {
                appendLog(LogEntry(nextId++, "[System] ADB aktiv: Privilegierter Logcat-Stream gestartet (-T 250 Buffer + Live Traces)...", LogLevel.INFO))
                try {
                    adbLogcatStreamer.stream(
                        host = "127.0.0.1",
                        port = 5555,
                        command = "logcat -v time -T 250"
                    ) { line ->
                        val entry = parseLogLine(line)
                        appendLog(entry)
                    }
                } catch (e: Exception) {
                    appendLog(LogEntry(nextId++, "[System] ADB Stream unterbrochen: ${e.message}", LogLevel.WARN))
                } finally {
                    _isStreaming.value = false
                }
            } else {
                var process: Process? = null
                var reader: BufferedReader? = null

                try {
                    appendLog(LogEntry(nextId++, "[System] Lokaler App-Logcat aktiv. Für vollständigen Bluetooth-Systemtrace oben 'ADB Verbinden' wählen.", LogLevel.INFO))
                    process = ProcessBuilder("logcat", "-v", "time").start()
                    reader = BufferedReader(InputStreamReader(process.inputStream))

                    while (isActive) {
                        val line = reader.readLine() ?: break
                        val entry = parseLogLine(line)
                        appendLog(entry)
                    }
                } catch (e: Exception) {
                    appendLog(LogEntry(nextId++, "[System] Logcat Stream beendet oder eingeschränkt: ${e.message}", LogLevel.WARN))
                    appendLog(LogEntry(nextId++, "[Tipp] Falls Logs blockiert sind, tippe oben auf 'ADB Verbinden (5555)'.", LogLevel.INFO))
                } finally {
                    reader?.close()
                    process?.destroy()
                    _isStreaming.value = false
                }
            }
        }
    }

    fun stopStreaming() {
        _isStreaming.value = false
        streamJob?.cancel()
        streamJob = null
        adbLogcatStreamer.close()
    }

    fun toggleStreaming() {
        if (_isStreaming.value) {
            stopStreaming()
        } else {
            startStreaming()
        }
    }

    fun clearLogs() {
        synchronized(allLogs) {
            allLogs.clear()
        }
        _displayedLogs.value = emptyList()
        // Also clear system logcat buffer
        scope.launch(Dispatchers.IO) {
            try {
                Runtime.getRuntime().exec(arrayOf("logcat", "-c"))
            } catch (_: Exception) {}
        }
    }

    fun setPreset(preset: BluetoothLogPreset) {
        _selectedPreset.value = preset
        recomputeDisplayedLogs()
    }

    fun setSearchFilter(query: String) {
        _searchFilter.value = query
        recomputeDisplayedLogs()
    }

    fun toggleAutoScroll() {
        _autoScroll.value = !_autoScroll.value
    }

    fun copyToClipboard() {
        val text = synchronized(allLogs) {
            _displayedLogs.value.joinToString("\n") { it.rawText }
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Bluetooth Logs", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "${_displayedLogs.value.size} Zeilen in Zwischenablage kopiert", Toast.LENGTH_SHORT).show()
    }

    private fun appendLog(entry: LogEntry) {
        synchronized(allLogs) {
            allLogs.add(entry)
            if (allLogs.size > maxBufferLines) {
                allLogs.removeAt(0)
            }
        }

        if (matchesCurrentFilters(entry)) {
            val current = _displayedLogs.value.toMutableList()
            current.add(entry)
            if (current.size > maxBufferLines) {
                current.removeAt(0)
            }
            _displayedLogs.value = current
        }
    }

    private fun recomputeDisplayedLogs() {
        synchronized(allLogs) {
            _displayedLogs.value = allLogs.filter { matchesCurrentFilters(it) }
        }
    }

    private fun matchesCurrentFilters(entry: LogEntry): Boolean {
        // System and status announcements are always displayed so the user sees live connection feedback
        if (entry.rawText.startsWith("[System]") || entry.rawText.startsWith("[Tipp]")) {
            return true
        }

        val preset = _selectedPreset.value
        val regex = preset.tagRegex
        if (regex != null && !regex.containsMatchIn(entry.rawText)) {
            return false
        }
        val search = _searchFilter.value
        if (search.isNotBlank() && !entry.rawText.contains(search, ignoreCase = true)) {
            return false
        }
        return true
    }

    private fun parseLogLine(line: String): LogEntry {
        val level = when {
            line.contains(" E ") || line.contains(" E/") -> LogLevel.ERROR
            line.contains(" W ") || line.contains(" W/") -> LogLevel.WARN
            line.contains(" I ") || line.contains(" I/") -> LogLevel.INFO
            line.contains(" D ") || line.contains(" D/") -> LogLevel.DEBUG
            line.contains(" V ") || line.contains(" V/") -> LogLevel.VERBOSE
            else -> LogLevel.UNKNOWN
        }
        return LogEntry(
            id = nextId++,
            rawText = line,
            level = level
        )
    }
}
