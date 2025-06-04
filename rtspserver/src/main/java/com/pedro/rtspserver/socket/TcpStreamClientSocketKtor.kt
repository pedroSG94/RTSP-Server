package com.pedro.rtspserver.socket

import com.pedro.common.socket.ktor.TcpStreamSocketKtorBase
import io.ktor.network.sockets.ReadWriteSocket
import io.ktor.network.sockets.Socket

class TcpStreamClientSocketKtor(
    private val socket: Socket,
    host: String, port: Int
): TcpStreamSocketKtorBase(host, port) {

    override suspend fun onConnectSocket(timeout: Long): ReadWriteSocket {
        return socket
    }
}