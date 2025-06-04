package com.pedro.rtspserver.socket

import com.pedro.common.socket.base.TcpStreamSocket

data class ClientSocket(
    val host: String,
    val port: Int,
    val socket: TcpStreamSocket
)