package com.quest.systemsettings.adb

import java.nio.ByteBuffer
import java.nio.ByteOrder

object AdbProtocol {
    const val CMD_CNXN = 0x4e584e43 // "CNXN"
    const val CMD_AUTH = 0x48545541 // "AUTH"
    const val CMD_OPEN = 0x4e45504f // "OPEN"
    const val CMD_OKAY = 0x59414b4f // "OKAY"
    const val CMD_CLSE = 0x45534c43 // "CLSE"
    const val CMD_WRTE = 0x45545257 // "WRTE"

    const val AUTH_TYPE_TOKEN = 1
    const val AUTH_TYPE_SIGNATURE = 2
    const val AUTH_TYPE_RSAPUBLICKEY = 3

    const val CONNECT_VERSION = 0x01000000
    const val CONNECT_MAXDATA = 4096 * 4

    data class Message(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val dataLength: Int,
        val dataCrc32: Int,
        val magic: Int,
        val payload: ByteArray
    ) {
        fun toByteArray(): ByteArray {
            val buf = ByteBuffer.allocate(24 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
            buf.putInt(command)
            buf.putInt(arg0)
            buf.putInt(arg1)
            buf.putInt(dataLength)
            buf.putInt(dataCrc32)
            buf.putInt(magic)
            buf.put(payload)
            return buf.array()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Message) return false
            return command == other.command && arg0 == other.arg0 && arg1 == other.arg1 &&
                    dataLength == other.dataLength && dataCrc32 == other.dataCrc32 &&
                    magic == other.magic && payload.contentEquals(other.payload)
        }

        override fun hashCode(): Int {
            var result = command
            result = 31 * result + arg0
            result = 31 * result + arg1
            result = 31 * result + dataLength
            result = 31 * result + dataCrc32
            result = 31 * result + magic
            result = 31 * result + payload.contentHashCode()
            return result
        }
    }

    fun createMessage(command: Int, arg0: Int, arg1: Int, payload: ByteArray = ByteArray(0)): Message {
        var checksum = 0
        for (b in payload) {
            checksum += (b.toInt() and 0xFF)
        }
        return Message(
            command = command,
            arg0 = arg0,
            arg1 = arg1,
            dataLength = payload.size,
            dataCrc32 = checksum,
            magic = command xor -0x1,
            payload = payload
        )
    }

    fun parseHeader(headerBytes: ByteArray): MessageHeader {
        val buf = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
        return MessageHeader(
            command = buf.int,
            arg0 = buf.int,
            arg1 = buf.int,
            dataLength = buf.int,
            dataCrc32 = buf.int,
            magic = buf.int
        )
    }

    data class MessageHeader(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val dataLength: Int,
        val dataCrc32: Int,
        val magic: Int
    ) {
        val isValid: Boolean get() = (command xor -0x1) == magic
    }
}
