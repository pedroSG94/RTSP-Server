package com.pedro.rtspserver.server

import com.pedro.common.socket.TcpStreamSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.isClosed
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.sockets.toJavaAddress
import io.ktor.util.network.hostname
import io.ktor.util.network.port
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Created by pedro on 26/10/24.
 */
class ClientSocket(
  private val socket: Socket
): TcpStreamSocket() {

  private val javaAddress = socket.remoteAddress.toJavaAddress()
  private val address = java.net.InetSocketAddress(javaAddress.hostname, javaAddress.port).address

  fun getHost() = javaAddress.hostname

  override suspend fun connect() {
    input = socket.openReadChannel()
    output = socket.openWriteChannel(autoFlush = false)
  }

  override suspend fun close() = withContext(Dispatchers.IO) {
      try {
        socket.close()
      } catch (ignored: Exception) {}
  }

  override fun isConnected(): Boolean = !socket.isClosed

  override fun isReachable(): Boolean = address?.isReachable(5000) ?: false
}