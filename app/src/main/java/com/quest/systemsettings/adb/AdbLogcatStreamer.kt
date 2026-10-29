package com.quest.systemsettings.adb

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Dedicated ADB stream client for realtime logcat streaming without blocking or interfering
 * with one-off ADB shell commands on the main AdbClient connection.
 */
class AdbLogcatStreamer(private val context: Context) {

    private val tag = "AdbLogcatStreamer"
    private val crypto by lazy { AdbCrypto(context) }
    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    suspend fun stream(
        host: String = "127.0.0.1",
        port: Int = 5555,
        command: String = "logcat -v time -T 250",
        onLine: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        close()
        try {
            val s = Socket()
            s.connect(InetSocketAddress(host, port), 4000)
            socket = s
            val inStream = s.getInputStream().also { inputStream = it }
            val outStream = s.getOutputStream().also { outputStream = it }

            // 1. Initial CNXN
            val banner = "host::quest-logcat\u0000".toByteArray(Charsets.UTF_8)
            val cnxnMsg = AdbProtocol.createMessage(
                command = AdbProtocol.CMD_CNXN,
                arg0 = AdbProtocol.CONNECT_VERSION,
                arg1 = AdbProtocol.CONNECT_MAXDATA,
                payload = banner
            )
            outStream.write(cnxnMsg.toByteArray())
            outStream.flush()

            // 2. Authentication
            var authenticated = false
            var sentSignature = false
            var sentPublicKey = false

            while (s.isConnected && !s.isClosed && !authenticated) {
                val header = readHeader(inStream) ?: break
                val payload = if (header.dataLength > 0) readPayload(inStream, header.dataLength) else ByteArray(0)

                when (header.command) {
                    AdbProtocol.CMD_CNXN -> {
                        authenticated = true
                    }
                    AdbProtocol.CMD_AUTH -> {
                        if (header.arg0 == AdbProtocol.AUTH_TYPE_TOKEN) {
                            if (!sentSignature) {
                                try {
                                    val signed = crypto.sign(payload)
                                    val signMsg = AdbProtocol.createMessage(
                                        command = AdbProtocol.CMD_AUTH,
                                        arg0 = AdbProtocol.AUTH_TYPE_SIGNATURE,
                                        arg1 = 0,
                                        payload = signed
                                    )
                                    outStream.write(signMsg.toByteArray())
                                    outStream.flush()
                                    sentSignature = true
                                } catch (e: Exception) {
                                    Log.e(tag, "Failed to sign token for logcat stream: ${e.message}")
                                    break
                                }
                            } else if (!sentPublicKey) {
                                val pubKeyPayload = crypto.getAdbPublicKeyPayload()
                                val pubKeyMsg = AdbProtocol.createMessage(
                                    command = AdbProtocol.CMD_AUTH,
                                    arg0 = AdbProtocol.AUTH_TYPE_RSAPUBLICKEY,
                                    arg1 = 0,
                                    payload = pubKeyPayload
                                )
                                outStream.write(pubKeyMsg.toByteArray())
                                outStream.flush()
                                sentPublicKey = true
                            }
                        }
                    }
                }
            }

            if (!authenticated) {
                Log.w(tag, "Logcat stream failed to authenticate with adbd")
                return@withContext
            }

            // 3. Open shell:logcat stream
            val localId = 200
            val dest = "shell:$command\u0000".toByteArray(Charsets.UTF_8)
            val openMsg = AdbProtocol.createMessage(
                command = AdbProtocol.CMD_OPEN,
                arg0 = localId,
                arg1 = 0,
                payload = dest
            )
            outStream.write(openMsg.toByteArray())
            outStream.flush()

            var remoteId = 0
            val lineBuffer = StringBuilder()

            // 4. Stream read loop
            while (s.isConnected && !s.isClosed && currentCoroutineContext().isActive) {
                val header = readHeader(inStream) ?: break
                val payload = if (header.dataLength > 0) readPayload(inStream, header.dataLength) else ByteArray(0)

                when (header.command) {
                    AdbProtocol.CMD_OKAY -> {
                        remoteId = header.arg0
                    }
                    AdbProtocol.CMD_WRTE -> {
                        remoteId = header.arg0
                        val text = String(payload, Charsets.UTF_8)
                        lineBuffer.append(text)

                        var newlineIdx: Int
                        while (lineBuffer.indexOf('\n').also { newlineIdx = it } != -1) {
                            val line = lineBuffer.substring(0, newlineIdx).trimEnd('\r')
                            lineBuffer.delete(0, newlineIdx + 1)
                            if (line.isNotEmpty()) {
                                onLine(line)
                            }
                        }

                        // Acknowledge WRTE
                        val okayMsg = AdbProtocol.createMessage(
                            command = AdbProtocol.CMD_OKAY,
                            arg0 = localId,
                            arg1 = remoteId
                        )
                        outStream.write(okayMsg.toByteArray())
                        outStream.flush()
                    }
                    AdbProtocol.CMD_CLSE -> {
                        val clseMsg = AdbProtocol.createMessage(
                            command = AdbProtocol.CMD_CLSE,
                            arg0 = localId,
                            arg1 = remoteId
                        )
                        outStream.write(clseMsg.toByteArray())
                        outStream.flush()
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(tag, "Logcat stream terminated: ${e.message}")
        } finally {
            close()
        }
    }

    private fun readHeader(input: InputStream): AdbProtocol.MessageHeader? {
        val headerBytes = ByteArray(24)
        var readTotal = 0
        while (readTotal < 24) {
            val count = input.read(headerBytes, readTotal, 24 - readTotal)
            if (count == -1) return null
            readTotal += count
        }
        val header = AdbProtocol.parseHeader(headerBytes)
        return if (header.isValid) header else null
    }

    private fun readPayload(input: InputStream, length: Int): ByteArray {
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
    }
}
