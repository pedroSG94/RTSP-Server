package com.pedro.rtspserver

import android.media.MediaCodec
import android.util.Log
import com.pedro.rtsp.utils.ConnectCheckerRtsp
import com.pedro.rtsp.utils.RtpConstants
import java.io.*
import java.net.*
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 *
 * Created by pedro on 13/02/19.
 *
 *
 * TODO Use different session per client.
 */

open class RtspServer(private val connectCheckerRtsp: ConnectCheckerRtsp,
  val port: Int): ClientListener {

  private val TAG = "RtspServer"
  private var server: ServerSocket? = null
  val serverIp: String get() = getIPAddress()
  var sps: ByteBuffer? = null
  var pps: ByteBuffer? = null
  var vps: ByteBuffer? = null
  var sampleRate = 32000
  var isStereo = true
  private val clients = mutableListOf<ServerClient>()
  private var videoDisabled = false
  private var audioDisabled = false
  private var thread: Thread? = null
  private var user: String? = null
  private var password: String? = null
  private var logs = true
  private var running = false
  private val semaphore = Semaphore(0)

  fun setAuth(user: String?, password: String?) {
    this.user = user
    this.password = password
  }

  fun startServer() {
    stopServer()
    thread = Thread {
      try {
        if (!videoDisabled) {
          if (sps == null || pps == null) {
            semaphore.drainPermits()
            Log.i(TAG, "waiting for sps and pps")
            semaphore.tryAcquire(5000, TimeUnit.MILLISECONDS)
          }
          if (sps == null || pps == null) {
            connectCheckerRtsp.onConnectionFailedRtsp("sps or pps is null")
            return@Thread
          }
        }
        server = ServerSocket(port)
      } catch (e: IOException) {
        connectCheckerRtsp.onConnectionFailedRtsp("Server creation failed")
        Log.e(TAG, "Error", e)
        return@Thread
      }
      Log.i(TAG, "Server started $serverIp:$port")
      while (!Thread.interrupted()) {
        try {
          val clientSocket = server?.accept() ?: continue
          val clientAddress = clientSocket.inetAddress.hostAddress
          if (clientAddress == null) {
            Log.e(TAG, "Unknown client ip, closing clientSocket...")
            if (!clientSocket.isClosed) clientSocket.close()
            continue
          }
          val client = ServerClient(clientSocket, serverIp, port, connectCheckerRtsp, clientAddress, sps, pps, vps,
              sampleRate, isStereo, videoDisabled, audioDisabled, user, password, this)
          client.rtspSender.setLogs(logs)
          client.start()
          synchronized(clients) {
            clients.add(client)
          }
        } catch (e: SocketException) {
          // server.close called
          break
        } catch (e: IOException) {
          Log.e(TAG, "Error", e)
          continue
        }
      }
      Log.i(TAG, "Server finished")
    }
    running = true
    thread?.start()
  }

  fun getNumClients(): Int = clients.size

  fun stopServer() {
    synchronized(clients) {
      clients.forEach { it.stopClient() }
      clients.clear()
    }
    if (server?.isClosed == false) server?.close()
    thread?.interrupt()
    try {
      thread?.join(100)
    } catch (e: InterruptedException) {
      thread?.interrupt()
    }
    semaphore.release()
    running = false
    thread = null
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
    audioDisabled = false
    videoDisabled = onlyAudio
  }

  fun setOnlyVideo(onlyVideo: Boolean) {
    RtpConstants.trackVideo = 0
    RtpConstants.trackAudio = 1
    videoDisabled = false
    audioDisabled = onlyVideo
  }

  fun setLogs(enable: Boolean) {
    logs = enable
    synchronized(clients) {
      clients.forEach { it.rtspSender.setLogs(enable) }
    }
  }

  fun sendVideo(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    synchronized(clients) {
      clients.forEach {
        if (it.isAlive && it.canSend && !it.commandsManager.videoDisabled) {
          it.rtspSender.sendVideoFrame(h264Buffer.duplicate(), info)
        }
      }
    }
  }

  fun sendAudio(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    synchronized(clients) {
      clients.forEach {
        if (it.isAlive && it.canSend && !it.commandsManager.audioDisabled) {
          it.rtspSender.sendAudioFrame(aacBuffer.duplicate(), info)
        }
      }
    }
  }

  fun setVideoInfo(sps: ByteBuffer, pps: ByteBuffer, vps: ByteBuffer?) {
    this.sps = sps
    this.pps = pps
    this.vps = vps  //H264 has no vps so if not null assume H265
    semaphore.release()
  }

  fun hasCongestion(): Boolean {
    synchronized(clients) {
      var congestion = false
      clients.forEach { if (it.hasCongestion()) congestion = true }
      return congestion
    }
  }

  override fun onDisconnected(client: ServerClient) {
    synchronized(clients) {
      client.stopClient()
      clients.remove(client)
    }
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
    .filter { address -> address?.contains(":") == false }
    .toList()


  companion object {
    private const val VPN_INTERFACE = "tun"
    private const val DEFAULT_IP = "0.0.0.0"
  }
}