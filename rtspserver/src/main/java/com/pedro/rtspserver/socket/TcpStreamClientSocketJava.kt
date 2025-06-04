package com.pedro.rtspserver.socket

import com.pedro.common.socket.java.TcpStreamSocketJavaBase
import java.net.Socket

class TcpStreamClientSocketJava(
    private val socket: Socket
): TcpStreamSocketJavaBase() {

    override fun onConnectSocket(timeout: Long): Socket {
        return socket
    }
}