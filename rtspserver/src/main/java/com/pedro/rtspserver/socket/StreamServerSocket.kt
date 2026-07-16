package com.pedro.rtspserver.socket

import com.pedro.common.socket.base.SocketType
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.toJavaAddress
import io.ktor.util.network.address
import io.ktor.util.network.port
import kotlinx.coroutines.Dispatchers
import java.net.InetSocketAddress
import java.net.ServerSocket

class StreamServerSocket(
    private val type: SocketType
) {
    private var ktorServer: io.ktor.network.sockets.ServerSocket? = null
    private var javaServer: ServerSocket? = null
    private var selectorManager: SelectorManager? = null

    suspend fun create(port: Int) {
        when (type) {
            SocketType.KTOR -> {
                selectorManager?.close()
                val manager = SelectorManager(Dispatchers.IO)
                selectorManager = manager
                ktorServer = aSocket(manager).tcp()
                    .bind("0.0.0.0", port)
            }
            SocketType.JAVA -> {
                javaServer = ServerSocket(port)
            }
        }
    }

    suspend fun accept(): ClientSocket {
        return when (type) {
            SocketType.KTOR -> {
                val socket = ktorServer?.accept() ?: throw IllegalStateException("Server not available")
                val address = socket.remoteAddress.toJavaAddress() as InetSocketAddress
                val hostAddress: String = address.address?.hostAddress ?: address.hostString!!
                ClientSocket(
                    host = hostAddress,
                    port = address.port,
                    socket = TcpStreamClientSocketKtor(socket, hostAddress, address.port)
                )
            }
            SocketType.JAVA -> {
                val socket = javaServer?.accept() ?: throw IllegalStateException("Server not available")
                val hostAddress = socket.inetAddress.hostAddress ?: "0.0.0.0"
                ClientSocket(
                    host = hostAddress,
                    port = socket.port,
                    socket = TcpStreamClientSocketJava(socket)
                )
            }
        }
    }

    fun close() {
        try {
            when (type) {
                SocketType.KTOR -> {
                    ktorServer?.close()
                    ktorServer = null
                    selectorManager?.close()
                    selectorManager = null
                }
                SocketType.JAVA -> {
                    javaServer?.close()
                    javaServer = null
                }
            }
        } catch (ignored: Exception) {}
    }
}
