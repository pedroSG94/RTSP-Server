package com.pedro.rtspserver.server

import android.media.MediaCodec
import android.util.Log
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.common.onMainThread
import com.pedro.common.onMainThreadHandler
import com.pedro.common.socket.base.SocketType
import com.pedro.rtsp.utils.RtpConstants
import com.pedro.rtspserver.socket.StreamServerSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.NetworkInterface
import java.nio.ByteBuffer
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 *
 * Created by pedro on 13/02/19.
 *
 *
 * TODO Use different session per client.
 */

class RtspServer(
  private val connectChecker: ConnectChecker,
  val port: Int
): ClientListener {

  private val TAG = "RtspServer"
  private var socketType = SocketType.JAVA
  private var server = StreamServerSocket(socketType)
  val serverIp: String get() = getIPAddress()
  private val clients = mutableListOf<ServerClient>()
  private val scope = CoroutineScope(Dispatchers.IO)
  private var job: Job? = null
  private var running = false
  private var isEnableLogs = true
  private val semaphore = Semaphore(0)
  private val serverCommandManager = ServerCommandManager()
  private var clientListener: ClientListener? = null
  private var ipType = IpType.All
  private var delay: Long? = null

  val droppedAudioFrames: Long
    get() = synchronized(clients) {
      var items = 0L
      clients.forEach { items += it.droppedAudioFrames }
      return items
    }

  val droppedVideoFrames: Long
    get() = synchronized(clients) {
      var items = 0L
      clients.forEach { items += it.droppedVideoFrames }
      return items
    }

  val cacheSize: Int
    get() = synchronized(clients) {
      var items = 0
      clients.forEach { items += it.cacheSize }
      return items / getNumClients()
    }
  val sentAudioFrames: Long
    get() = synchronized(clients) {
      var items = 0L
      clients.forEach { items += it.sentAudioFrames }
      return items
    }
  val sentVideoFrames: Long
    get() = synchronized(clients) {
      var items = 0L
      clients.forEach { items += it.sentVideoFrames }
      return items
    }

  fun setClientListener(clientListener: ClientListener?) {
    this.clientListener = clientListener
  }

  fun setDelay(delay: Long) {
    this.delay = delay
  }

  fun setSocketType(socketType: SocketType) {
    if (!isRunning()) {
      this.socketType = socketType
      server = StreamServerSocket(socketType)
    }
  }

  fun setAuth(user: String?, password: String?) {
    serverCommandManager.setAuth(user, password)
  }

  fun startServer() {
    running = true
    job = scope.launch {
      try {
        if (!serverCommandManager.videoDisabled) {
          if (!serverCommandManager.videoInfoReady()) {
            semaphore.drainPermits()
            Log.i(TAG, "waiting for video info")
            semaphore.tryAcquire(5000, TimeUnit.MILLISECONDS)
          }
          if (!serverCommandManager.videoInfoReady()) {
            onMainThread {
              connectChecker.onConnectionFailed("Video info is null")
            }
            return@launch
          }
        }
        server.create(port)
        Log.i(TAG, "Server started: $serverIp:$port")
      } catch (e: Exception) {
        onMainThread {
          connectChecker.onConnectionFailed("Server creation failed")
        }
        Log.e(TAG, "Error", e)
        return@launch
      }
      while (running) {
        try {
          Log.i(TAG, "Waiting client...")
          val clientSocket = server.accept()
          Log.i(TAG, "Client connected: ${clientSocket.host}")
          val client = ServerClient(delay, socketType, clientSocket.host, clientSocket.socket, serverIp, port,
            serverCommandManager, this@RtspServer)
          client.setLogs(isEnableLogs)
          client.startClient()
          synchronized(clients) {
            clients.add(client)
          }
        } catch (e: IOException) {
          // server.close called
          break
        } catch (e: Exception) {
          Log.e(TAG, "Error", e)
          continue
        }
      }
      Log.i(TAG, "Server finished")
    }
  }

  fun getNumClients(): Int = clients.size

  fun stopServer() {
    synchronized(clients) {
      clients.forEach { it.stopClient() }
      clients.clear()
    }
    server.close()
    CoroutineScope(Dispatchers.IO).launch {
      job?.cancelAndJoin()
      job = null
      semaphore.release()
    }
    running = false
  }

  fun isRunning(): Boolean = running

  fun setOnlyAudio(onlyAudio: Boolean) {
    if (onlyAudio) {
      RtpConstants.trackAudio = 0
      RtpConstants.trackVideo = 1
    } else {
      RtpConstants.trackVideo = 0
      RtpConstants.trackAudio = 1
    }
    serverCommandManager.audioDisabled = false
    serverCommandManager.videoDisabled = onlyAudio
  }

  fun setOnlyVideo(onlyVideo: Boolean) {
    RtpConstants.trackVideo = 0
    RtpConstants.trackAudio = 1
    serverCommandManager.videoDisabled = false
    serverCommandManager.audioDisabled = onlyVideo
  }

  fun setLogs(enable: Boolean) {
    isEnableLogs = enable
    synchronized(clients) {
      clients.forEach { it.setLogs(enable) }
    }
  }

  fun sendVideo(videoBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    synchronized(clients) {
      clients.forEach {
        if (it.isAlive() && it.canSend && !serverCommandManager.videoDisabled) {
          it.sendVideoFrame(videoBuffer.duplicate(), info)
        }
      }
    }
  }

  fun sendAudio(audioBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    synchronized(clients) {
      clients.forEach {
        if (it.isAlive() && it.canSend && !serverCommandManager.audioDisabled) {
          it.sendAudioFrame(audioBuffer.duplicate(), info)
        }
      }
    }
  }

  fun setVideoInfo(sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) {
    serverCommandManager.setVideoInfo(sps, pps, vps)
    semaphore.release()
  }

  fun setAudioInfo(sampleRate: Int, isStereo: Boolean) {
    serverCommandManager.setAudioInfo(sampleRate, isStereo)
  }

  fun setVideoCodec(videoCodec: VideoCodec) {
    if (!isRunning()) {
      serverCommandManager.videoCodec = videoCodec
    } else {
      throw RuntimeException("Please set VideoCodec before startServer.")
    }
  }

  fun setAudioCodec(audioCodec: AudioCodec) {
    if (!isRunning()) {
      serverCommandManager.audioCodec = audioCodec
    } else {
      throw RuntimeException("Please set AudioCodec before startServer.")
    }
  }

  fun hasCongestion(percentUsed: Float): Boolean {
    synchronized(clients) {
      var congestion = false
      clients.forEach { if (it.hasCongestion(percentUsed)) congestion = true }
      return congestion
    }
  }

  fun resetSentAudioFrames() {
    synchronized(clients) {
      clients.forEach { it.resetSentAudioFrames() }
    }
  }

  fun resetSentVideoFrames() {
    synchronized(clients) {
      clients.forEach { it.resetSentVideoFrames() }
    }
  }

  fun resetDroppedAudioFrames() {
    synchronized(clients) {
      clients.forEach { it.resetDroppedAudioFrames() }
    }
  }

  fun resetDroppedVideoFrames() {
    synchronized(clients) {
      clients.forEach { it.resetDroppedVideoFrames() }
    }
  }

  @Throws(RuntimeException::class)
  fun resizeCache(newSize: Int) {
    synchronized(clients) {
      clients.forEach { it.resizeCache(newSize) }
    }
  }

  fun clearCache() {
    synchronized(clients) {
      clients.forEach { it.clearCache() }
    }
  }

  fun setBitrateExponentialFactor(factor: Float) {
    synchronized(clients) {
      clients.forEach { it.setBitrateExponentialFactor(factor) }
    }
  }

  fun getBitrateExponentialFactor(): Float {
    synchronized(clients) {
      var factor = 0f
      clients.forEach { factor += it.getBitrateExponentialFactor() }
      return factor / clients.size
    }
  }

  fun getItemsInCache(): Int {
    synchronized(clients) {
      var items = 0
      clients.forEach { items += it.getItemsInCache() }
      return items
    }
  }

  fun forceIpType(ipType: IpType) {
    if (!isRunning()) {
      this.ipType = ipType
    } else {
      throw RuntimeException("Please set IpType before startServer.")
    }
  }

  override fun onClientConnected(client: ServerClient) {
    onMainThreadHandler { clientListener?.onClientConnected(client) }
  }

  override fun onClientDisconnected(client: ServerClient) {
    synchronized(clients) {
      client.stopClient()
      clients.remove(client)
    }
    onMainThreadHandler { clientListener?.onClientDisconnected(client) }
  }

  override fun onClientNewBitrate(bitrate: Long, client: ServerClient) {
    onMainThreadHandler { clientListener?.onClientNewBitrate(bitrate, client) }
  }

  private fun getIPAddress(): String {
    val interfaces: List<NetworkInterface> = NetworkInterface.getNetworkInterfaces().toList()
    val vpnInterfaces = interfaces.filter { it.displayName.contains(VPN_INTERFACE) }
    val address: String by lazy { interfaces.findAddress().firstOrNull() ?: DEFAULT_IP }
    return if (vpnInterfaces.isNotEmpty()) {
      val vpnAddresses = vpnInterfaces.findAddress()
      vpnAddresses.firstOrNull() ?: address
    } else {
      address
    }
  }

  private fun List<NetworkInterface>.findAddress(): List<String?> = this.asSequence()
    .map { addresses -> addresses.inetAddresses.asSequence() }
    .flatten()
    .filter { address -> !address.isLoopbackAddress }
    .map { it.hostAddress }
    .filter { address ->
      //exclude invalid IPv6 addresses
      address?.startsWith("fe80") != true && // Exclude link-local addresses
      address?.startsWith("fc00") != true && // Exclude unique local addresses
      address?.startsWith("fd00") != true // Exclude unique local addresses
    }
    .filter { address ->
      when (ipType) {
        IpType.IPv4 -> address?.contains(":") == false
        IpType.IPv6 -> address?.contains(":") == true
        IpType.All -> true
      }
    }
    .toList()

  companion object {
    private const val VPN_INTERFACE = "tun"
    private const val DEFAULT_IP = "0.0.0.0"
  }
}