package com.pedro.rtspserver.server

import com.pedro.common.socket.base.TcpStreamSocket
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.isClosed
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.sockets.toJavaAddress
import io.ktor.util.network.hostname
import io.ktor.util.network.port
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readFully
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers

/**
 * Created by pedro on 26/10/24.
 */
class ClientSocket(
    private val socket: Socket
) : TcpStreamSocket() {

    private val javaAddress = socket.remoteAddress.toJavaAddress()
    private var address = java.net.InetSocketAddress(javaAddress.hostname, javaAddress.port).address

    private var input: ByteReadChannel? = null
    private var output: ByteWriteChannel? = null
    private var selectorManager = SelectorManager(Dispatchers.IO)

    override suspend fun connect() {
        input = socket.openReadChannel()
        output = socket.openWriteChannel(autoFlush = false)
    }

    override suspend fun close() {
        runCatching { output?.flushAndClose() }
        runCatching {
            address = null
            input = null
            output = null
            socket.close()
            selectorManager.close()
        }
    }

    override suspend fun write(bytes: ByteArray) {
        output?.writeFully(bytes)
    }

    override suspend fun write(bytes: ByteArray, offset: Int, size: Int) {
        output?.writeFully(bytes, offset, size)
    }

    override suspend fun write(b: Int) {
        output?.writeByte(b.toByte())
    }

    override suspend fun write(string: String) {
        write(string.toByteArray())
    }

    override suspend fun flush() {
        output?.flush()
    }

    override suspend fun read(bytes: ByteArray) {
        input?.readFully(bytes)
    }

    override suspend fun read(size: Int): ByteArray {
        val data = ByteArray(size)
        read(data)
        return data
    }

    override suspend fun readLine(): String? = input?.readUTF8Line()

    override fun isConnected(): Boolean = socket.isClosed != true

    override fun isReachable(): Boolean = address?.isReachable(timeout.toInt()) == true

    fun getHost(): String {
        return address.hostAddress ?: address.hostName
    }
}