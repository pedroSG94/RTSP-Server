package com.pedro.rtspserver.socket

import com.pedro.common.socket.base.SocketType
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.isClosed
import io.ktor.network.sockets.toJavaAddress
import io.ktor.util.network.address
import io.ktor.util.network.port
import kotlinx.coroutines.Dispatchers
import java.net.ServerSocket

class StreamServerSocket(
    private val type: SocketType
) {
    private var ktorServer: io.ktor.network.sockets.ServerSocket? = null
    private var javaServer: ServerSocket? = null

    suspend fun create(port: Int) {
        when (type) {
            SocketType.KTOR -> {
                val selectorManager = SelectorManager(Dispatchers.IO)
                ktorServer = aSocket(selectorManager).tcp()
                    .bind("0.0.0.0", port)
            }
            SocketType.JAVA -> {
                javaServer = ServerSocket(port)
            }
        }
    }

    fun isClosed(): Boolean {
        return when (type) {
            SocketType.KTOR -> ktorServer?.isClosed ?: true
            SocketType.JAVA -> javaServer?.isClosed ?: true
        }
    }

    suspend fun accept(): ClientSocket {
        return when (type) {
            SocketType.KTOR -> {
                val socket = ktorServer?.accept() ?: throw IllegalStateException("Server no available")
                val address = socket.remoteAddress.toJavaAddress()
                ClientSocket(
                    host = address.address,
                    port = address.port,
                    socket = TcpStreamClientSocketKtor(socket, address.address, address.port)
                )
            }
            SocketType.JAVA -> {
                val socket = javaServer?.accept() ?: throw IllegalStateException("Server no available")
                ClientSocket(
                    host = socket.inetAddress.hostAddress,
                    port = socket.port,
                    socket = TcpStreamClientSocketJava(socket)
                )
            }
        }
    }

    fun close() {
        when (type) {
            SocketType.KTOR -> ktorServer?.close()
            SocketType.JAVA -> javaServer?.close()
        }
    }
}
