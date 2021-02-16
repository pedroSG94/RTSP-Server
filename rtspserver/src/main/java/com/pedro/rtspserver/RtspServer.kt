package com.pedro.rtspserver

import android.content.Context
import android.media.MediaCodec
import android.net.wifi.WifiManager
import android.util.Log
import com.pedro.rtsp.rtsp.Protocol
import com.pedro.rtsp.rtsp.RtspSender
import com.pedro.rtsp.utils.ConnectCheckerRtsp
import java.io.*
import java.net.*
import java.nio.ByteBuffer
import java.util.*

/**
 *
 * Created by pedro on 13/02/19.
 *
 *
 * TODO Use different session per client.
 */

class RtspServer(context: Context, private val connectCheckerRtsp: ConnectCheckerRtsp,
  val port: Int) {

  private val TAG = "RtspServer"
  private var server: ServerSocket? = null
  val serverIp = getIPAddress(true)
  var sps: ByteBuffer? = null
  var pps: ByteBuffer? = null
  var vps: ByteBuffer? = null
  var sampleRate = 32000
  var isStereo = true
  private val clients = mutableListOf<Client>()
  private var thread: Thread? = null

  fun startServer() {
    stopServer()
    thread = Thread {
      server = ServerSocket(port)
      while (!Thread.interrupted()) {
        Log.i(TAG, "Server started $serverIp:$port")
        try {
          val client =
            Client(server!!.accept(), serverIp, port, connectCheckerRtsp, sps!!, pps!!, vps, sampleRate,
              isStereo)
          client.start()
          clients.add(client)
        } catch (e: SocketException) {
          Log.e(TAG, "Error", e)
          break
        } catch (e: IOException) {
          Log.e(TAG, e.message ?: "")
          continue
        }
      }
      Log.i(TAG, "Server finished")
    }
    thread?.start()
  }

  fun stopServer() {
    clients.forEach { it.stopClient() }
    clients.clear()
    thread?.interrupt()
    try {
      thread?.join(100)
    } catch (e: InterruptedException) {
      thread?.interrupt()
    }
    thread = null
    if (server != null) {
      if (!server!!.isClosed) server!!.close()
    }
  }

  fun setLogs(enable: Boolean) {
    clients.forEach { it.rtspSender.setLogs(enable) }
  }

  fun sendVideo(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    clients.forEach {
      if (it.isAlive && it.canSend) {
        it.rtspSender.sendVideoFrame(h264Buffer.duplicate(), info)
      }
    }
  }

  fun sendAudio(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    clients.forEach {
      if (it.isAlive && it.canSend) {
        it.rtspSender.sendAudioFrame(aacBuffer.duplicate(), info)
      }
    }
  }

  fun setVideoInfo(sps: ByteBuffer, pps: ByteBuffer, vps: ByteBuffer?) {
    this.sps = sps
    this.pps = pps
    this.vps = vps  //H264 has no vps so if not null assume H265
  }

  private fun getIPAddress(useIPv4: Boolean): String {
    try {
      val interfaces: List<NetworkInterface> =
        Collections.list(NetworkInterface.getNetworkInterfaces())
      for (intf in interfaces) {
        val addrs: List<InetAddress> =
          Collections.list(intf.inetAddresses)
        for (addr in addrs) {
          if (!addr.isLoopbackAddress) {
            val sAddr = addr.hostAddress
            val isIPv4 = sAddr.indexOf(':') < 0
            if (useIPv4) {
              if (isIPv4) return sAddr
            } else {
              if (!isIPv4) {
                val delim = sAddr.indexOf('%') // drop ip6 zone suffix
                return if (delim < 0) sAddr.toUpperCase() else sAddr.substring(0, delim)
                  .toUpperCase()
              }
            }
          }
        }
      }
    } catch (ignored: java.lang.Exception) {
    } // for now eat exceptions
    return "0.0.0.0"
  }

  fun hasCongestion(): Boolean {
    var congestion = false
    clients.forEach { if (it.hasCongestion()) congestion = true }
    return congestion
  }


  internal class Client(private val socket: Socket, serverIp: String, serverPort: Int,
    connectCheckerRtsp: ConnectCheckerRtsp, sps: ByteBuffer,
    pps: ByteBuffer, vps: ByteBuffer?, private val sampleRate: Int,
    isStereo: Boolean) : Thread() {

    private val TAG = "Client"
    private var cSeq = 0
    private val output = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
    private val input = BufferedReader(InputStreamReader(socket.getInputStream()))
    val rtspSender = RtspSender(connectCheckerRtsp)
    private val commandsManager =
      ServerCommandManager(
        serverIp,
        serverPort,
        socket.inetAddress.hostAddress,
        connectCheckerRtsp
      )
    var canSend = false

    init {
      commandsManager.isStereo = isStereo
      commandsManager.sampleRate = sampleRate
      commandsManager.setVideoInfo(sps, pps, vps)
    }

    override fun run() {
      super.run()
      Log.i(TAG, "New client ${commandsManager.clientIp}")
      while (!interrupted()) {
        try {
          val request = commandsManager.getRequest(input)
          cSeq = commandsManager.getCSeq(request) //update cSeq
          if (cSeq == -1) { //If cSeq parsed fail send error to client
            output.write(commandsManager.createError(500, cSeq))
            output.flush()
            continue
          }
          val action = request.split("\n")[0]
          Log.i(TAG, request)
          val response = commandsManager.createResponse(action, request, cSeq)
          Log.i(TAG, response)
          output.write(response)
          output.flush()

          if (action.contains("play", true)) {
            Log.i(TAG, "Protocol ${commandsManager.protocol}")
            rtspSender.setSocketsInfo(commandsManager.protocol, commandsManager.videoClientPorts,
              commandsManager.audioClientPorts)
            rtspSender.setVideoInfo(commandsManager.sps!!, commandsManager.pps!!, commandsManager.vps)
            rtspSender.setAudioInfo(sampleRate)
            rtspSender.setDataStream(socket.getOutputStream(), commandsManager.clientIp!!)
            if (commandsManager.protocol == Protocol.UDP) {
              rtspSender.setVideoPorts(commandsManager.videoPorts[0], commandsManager.videoPorts[1])
              rtspSender.setAudioPorts(commandsManager.audioPorts[0], commandsManager.audioPorts[1])
            }
            rtspSender.start()
            canSend = true
          }
        } catch (e: SocketException) { // Client has left
          Log.e(TAG, "Client disconnected", e)
          break
        } catch (e: Exception) {
          Log.e(TAG, "Unexpected error", e)
        }
      }
    }

    fun stopClient() {
      canSend = false
      rtspSender.stop()
      interrupt()
      try {
        join(100)
      } catch (e: InterruptedException) {
        interrupt()
      } finally {
        socket.close()
      }
    }

    fun hasCongestion(): Boolean = rtspSender.hasCongestion()
  }
}