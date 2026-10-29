package com.quest.systemsettings.adb

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

sealed class AdbConnectionState {
    data object Disconnected : AdbConnectionState()
    data class Connecting(val host: String, val port: Int) : AdbConnectionState()
    data object WaitingForUserAuth : AdbConnectionState()
    data object Connected : AdbConnectionState()
    data class Error(val message: String) : AdbConnectionState()
}

class AdbClient(private val context: Context) {

    private val tag = "AdbClient"
    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private val localIdCounter = AtomicInteger(1)
    private val crypto by lazy { AdbCrypto(context) }

    private val _connectionState = MutableStateFlow<AdbConnectionState>(AdbConnectionState.Disconnected)
    val connectionState: StateFlow<AdbConnectionState> = _connectionState

    suspend fun connect(host: String = "127.0.0.1", port: Int = 5555): Boolean = withContext(Dispatchers.IO) {
        close()
        _connectionState.value = AdbConnectionState.Connecting(host, port)
        try {
            val s = Socket()
            s.connect(InetSocketAddress(host, port), 4000)
            // 60-second read timeout so it gives user enough time to confirm in VR if prompt appears
            s.soTimeout = 60000
            socket = s
            inputStream = s.getInputStream()
            outputStream = s.getOutputStream()

            // Send initial CNXN
            val banner = "host::quest-toolbox\u0000".toByteArray(Charsets.UTF_8)
            val cnxnMsg = AdbProtocol.createMessage(
                command = AdbProtocol.CMD_CNXN,
                arg0 = AdbProtocol.CONNECT_VERSION,
                arg1 = AdbProtocol.CONNECT_MAXDATA,
                payload = banner
            )
            writeMessage(cnxnMsg)

            var sentSignature = false
            var sentPublicKey = false

            // Read response
            while (s.isConnected && !s.isClosed) {
                val header = readHeader() ?: break
                val payload = if (header.dataLength > 0) readPayload(header.dataLength) else ByteArray(0)

                when (header.command) {
                    AdbProtocol.CMD_CNXN -> {
                        Log.i(tag, "ADB Connected successfully!")
                        _connectionState.value = AdbConnectionState.Connected
                        return@withContext true
                    }
                    AdbProtocol.CMD_AUTH -> {
                        if (header.arg0 == AdbProtocol.AUTH_TYPE_TOKEN) {
                            if (!sentSignature) {
                                // First time receiving token: try signing it with existing key
                                Log.i(tag, "Received AUTH TOKEN, attempting RSA signature authentication...")
                                try {
                                    val signed = crypto.sign(payload)
                                    val signMsg = AdbProtocol.createMessage(
                                        command = AdbProtocol.CMD_AUTH,
                                        arg0 = AdbProtocol.AUTH_TYPE_SIGNATURE,
                                        arg1 = 0,
                                        payload = signed
                                    )
                                    writeMessage(signMsg)
                                    sentSignature = true
                                } catch (e: Exception) {
                                    Log.e(tag, "Sign failed: ${e.message}")
                                }
                            } else if (!sentPublicKey) {
                                // Signature was sent but rejected by adbd (key not yet authorized).
                                // Send public key ONCE to prompt the user dialog.
                                Log.i(tag, "Signature not recognized. Sending public key for user authorization...")
                                val pubKeyPayload = crypto.getAdbPublicKeyPayload()
                                val pubKeyMsg = AdbProtocol.createMessage(
                                    command = AdbProtocol.CMD_AUTH,
                                    arg0 = AdbProtocol.AUTH_TYPE_RSAPUBLICKEY,
                                    arg1 = 0,
                                    payload = pubKeyPayload
                                )
                                writeMessage(pubKeyMsg)
                                sentPublicKey = true
                                _connectionState.value = AdbConnectionState.WaitingForUserAuth
                            } else {
                                // adbd may challenge us with a new token after user authorizes in dialog.
                                // Re-sign with the approved key. Do NOT re-send public key!
                                Log.i(tag, "Received AUTH TOKEN after public key sent; answering with signature...")
                                try {
                                    val signed = crypto.sign(payload)
                                    val signMsg = AdbProtocol.createMessage(
                                        command = AdbProtocol.CMD_AUTH,
                                        arg0 = AdbProtocol.AUTH_TYPE_SIGNATURE,
                                        arg1 = 0,
                                        payload = signed
                                    )
                                    writeMessage(signMsg)
                                } catch (e: Exception) {
                                    Log.e(tag, "Re-sign failed: ${e.message}")
                                }
                            }
                        }
                    }
                    else -> {
                        Log.d(tag, "Received unexpected message command: ${header.command}")
                    }
                }
            }
            _connectionState.value = AdbConnectionState.Error("Verbindung unerwartet geschlossen")
            false
        } catch (e: Exception) {
            Log.e(tag, "Error connecting to ADB: ${e.message}")
            _connectionState.value = AdbConnectionState.Error(e.message ?: "Verbindungsfehler")
            close()
            false
        }
    }

    suspend fun executeCommand(command: String): String = withContext(Dispatchers.IO) {
        if (_connectionState.value != AdbConnectionState.Connected) {
            // Fallback to local Runtime.exec if ADB TCP is not connected
            return@withContext executeLocalCommand(command)
        }

        try {
            socket?.soTimeout = 10000 // 10s timeout for single command execution
            val localId = localIdCounter.getAndIncrement()
            val dest = "shell:$command\u0000".toByteArray(Charsets.UTF_8)
            val openMsg = AdbProtocol.createMessage(
                command = AdbProtocol.CMD_OPEN,
                arg0 = localId,
                arg1 = 0,
                payload = dest
            )
            writeMessage(openMsg)

            var remoteId = 0
            val sb = StringBuilder()

            while (true) {
                val header = readHeader() ?: break
                val payload = if (header.dataLength > 0) readPayload(header.dataLength) else ByteArray(0)

                when (header.command) {
                    AdbProtocol.CMD_OKAY -> {
                        remoteId = header.arg0
                    }
                    AdbProtocol.CMD_WRTE -> {
                        sb.append(String(payload, Charsets.UTF_8))
                        // Acknowledge WRTE
                        val okayMsg = AdbProtocol.createMessage(
                            command = AdbProtocol.CMD_OKAY,
                            arg0 = localId,
                            arg1 = remoteId
                        )
                        writeMessage(okayMsg)
                    }
                    AdbProtocol.CMD_CLSE -> {
                        val clseMsg = AdbProtocol.createMessage(
                            command = AdbProtocol.CMD_CLSE,
                            arg0 = localId,
                            arg1 = remoteId
                        )
                        writeMessage(clseMsg)
                        break
                    }
                }
            }
            sb.toString()
        } catch (e: Exception) {
            Log.e(tag, "Error executing ADB command: ${e.message}")
            executeLocalCommand(command)
        }
    }

    private fun executeLocalCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val error = process.errorStream.bufferedReader().use { it.readText() }
            if (error.isNotBlank()) "$output\n[Error]: $error" else output
        } catch (e: Exception) {
            "Fehler beim lokalen Ausführen: ${e.message}"
        }
    }

    private fun writeMessage(message: AdbProtocol.Message) {
        val out = outputStream ?: throw IllegalStateException("OutputStream is null")
        out.write(message.toByteArray())
        out.flush()
    }

    private fun readHeader(): AdbProtocol.MessageHeader? {
        val input = inputStream ?: return null
        val headerBytes = ByteArray(24)
        var readTotal = 0
        while (readTotal < 24) {
            val count = input.read(headerBytes, readTotal, 24 - readTotal)
            if (count == -1) return null
            readTotal += count
        }
        val header = AdbProtocol.parseHeader(headerBytes)
        if (!header.isValid) {
            Log.e(tag, "Invalid header magic received")
            return null
        }
        return header
    }

    private fun readPayload(length: Int): ByteArray {
        val input = inputStream ?: return ByteArray(0)
        val data = ByteArray(length)
        var readTotal = 0
        while (readTotal < length) {
            val count = input.read(data, readTotal, length - readTotal)
            if (count == -1) break
            readTotal += count
        }
        return data
    }

    fun close() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
        inputStream = null
        outputStream = null
        _connectionState.value = AdbConnectionState.Disconnected
    }
}
