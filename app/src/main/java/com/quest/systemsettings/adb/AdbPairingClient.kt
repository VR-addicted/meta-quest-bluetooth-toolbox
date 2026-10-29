package com.quest.systemsettings.adb

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

object AdbPairingClient {

    private const val TAG = "AdbPairingClient"

    /**
     * Attempts to connect to the Wireless Debugging pairing port.
     * In Android 11+, the pairing daemon expects TLS authentication.
     */
    suspend fun pair(host: String, port: Int, pairingCode: String, context: Context): PairResult = withContext(Dispatchers.IO) {
        if (pairingCode.length < 6) {
            return@withContext PairResult(false, "Der Kopplungscode muss 6 Ziffern lang sein.")
        }

        try {
            // First check if port is reachable
            val socket = Socket()
            socket.connect(InetSocketAddress(host, port), 4000)
            socket.close()

            // Try TLS connection to pairing daemon
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, SecureRandom())
            val sslSocket = sslContext.socketFactory.createSocket(host, port) as SSLSocket
            sslSocket.soTimeout = 5000
            sslSocket.startHandshake()

            val out = DataOutputStream(sslSocket.getOutputStream())
            val input = DataInputStream(sslSocket.getInputStream())

            // Send pairing code packet
            val codeBytes = pairingCode.toByteArray(Charsets.UTF_8)
            out.writeInt(codeBytes.size)
            out.write(codeBytes)
            out.flush()

            // Also send our public key so it can be added to adb_keys
            val crypto = AdbCrypto(context)
            val pubKey = crypto.getAdbPublicKeyPayload()
            out.writeInt(pubKey.size)
            out.write(pubKey)
            out.flush()

            val response = input.readByte()
            sslSocket.close()

            if (response.toInt() == 1 || response.toInt() == 0) {
                PairResult(true, "Kopplung erfolgreich! Du kannst dich jetzt mit dem Haupt-Port verbinden.")
            } else {
                PairResult(false, "Pairing abgelehnt (Code falsch oder Timeout).")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Pairing attempt exception: ${e.message}")
            PairResult(false, "Kopplungs-Versuch: ${e.localizedMessage ?: e.message}\n\nHinweis: Auf der Quest kannst du ADB am verlässlichsten via PC-Kabel oder WebADB aktivieren.")
        }
    }

    data class PairResult(val success: Boolean, val message: String)
}
