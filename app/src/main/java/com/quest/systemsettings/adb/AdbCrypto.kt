package com.quest.systemsettings.adb

import android.content.Context
import android.util.Base64
import java.io.File
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

class AdbCrypto(private val context: Context) {

    private var keyPair: KeyPair? = null

    init {
        loadOrGenerateKeys()
    }

    private fun loadOrGenerateKeys() {
        val privFile = File(context.filesDir, "adbkey")
        val pubFile = File(context.filesDir, "adbkey.pub")

        if (privFile.exists() && pubFile.exists()) {
            try {
                val privBytes = privFile.readBytes()
                val pubBytes = pubFile.readBytes()
                val kf = KeyFactory.getInstance("RSA")
                val privKey = kf.generatePrivate(PKCS8EncodedKeySpec(privBytes))
                val pubKey = kf.generatePublic(X509EncodedKeySpec(pubBytes))
                keyPair = KeyPair(pubKey, privKey)
                return
            } catch (_: Exception) {
            }
        }

        // Generate 2048-bit RSA key pair
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()
        keyPair = kp

        try {
            privFile.writeBytes(kp.private.encoded)
            pubFile.writeBytes(kp.public.encoded)
        } catch (_: Exception) {
        }
    }

    fun sign(token: ByteArray): ByteArray {
        val privKey = keyPair?.private ?: throw IllegalStateException("Keypair not loaded")
        val sig = Signature.getInstance("NONEwithRSA")
        sig.initSign(privKey)

        // Android adbd uses RSA_verify(NID_sha1, ...) which expects the 15-byte ASN.1 SHA-1 DigestInfo prefix
        // prepended to the 20-byte token before PKCS#1 v1.5 padding.
        val asn1Sha1Prefix = byteArrayOf(
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14
        )
        val dataToSign = ByteArray(asn1Sha1Prefix.size + token.size)
        System.arraycopy(asn1Sha1Prefix, 0, dataToSign, 0, asn1Sha1Prefix.size)
        System.arraycopy(token, 0, dataToSign, asn1Sha1Prefix.size, token.size)

        sig.update(dataToSign)
        return sig.sign()
    }

    /**
     * Converts an RSAPublicKey into standard ADB format:
     * struct RSAPublicKey {
     *   int len;
     *   uint32_t n0inv;
     *   uint32_t n[RSANUMWORDS];
     *   uint32_t rr[RSANUMWORDS];
     *   int exponent;
     * };
     */
    fun getAdbPublicKeyPayload(): ByteArray {
        val pub = keyPair?.public as? RSAPublicKey ?: throw IllegalStateException("Keypair not loaded")
        val n = pub.modulus
        val e = pub.publicExponent

        val r32 = BigInteger.valueOf(2).pow(32)
        val r = BigInteger.valueOf(2).pow(2048)
        val rr = r.multiply(r).mod(n)
        val n0inv = n.modInverse(r32).negate().mod(r32).toLong()

        val numWords = 2048 / 32 // 64
        val buffer = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(numWords)
        buffer.putInt(n0inv.toInt())

        // Modulus (n) little endian
        val nBytes = getPaddedArray(n.toByteArray(), 256)
        for (i in 0 until numWords) {
            val idx = 256 - 4 * (i + 1)
            val word = ((nBytes[idx + 3].toInt() and 0xFF) shl 24) or
                    ((nBytes[idx + 2].toInt() and 0xFF) shl 16) or
                    ((nBytes[idx + 1].toInt() and 0xFF) shl 8) or
                    (nBytes[idx].toInt() and 0xFF)
            buffer.putInt(word)
        }

        // RR little endian
        val rrBytes = getPaddedArray(rr.toByteArray(), 256)
        for (i in 0 until numWords) {
            val idx = 256 - 4 * (i + 1)
            val word = ((rrBytes[idx + 3].toInt() and 0xFF) shl 24) or
                    ((rrBytes[idx + 2].toInt() and 0xFF) shl 16) or
                    ((rrBytes[idx + 1].toInt() and 0xFF) shl 8) or
                    (rrBytes[idx].toInt() and 0xFF)
            buffer.putInt(word)
        }

        buffer.putInt(e.toInt())

        val b64 = Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
        val fullString = "$b64 quest@toolbox\u0000"
        return fullString.toByteArray(Charsets.UTF_8)
    }

    private fun getPaddedArray(src: ByteArray, size: Int): ByteArray {
        val res = ByteArray(size)
        val srcPos = if (src.size > size) src.size - size else 0
        val destPos = if (size > src.size) size - src.size else 0
        val length = minOf(src.size, size)
        System.arraycopy(src, srcPos, res, destPos, length)
        return res
    }
}
