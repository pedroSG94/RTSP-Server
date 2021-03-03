package com.pedro.rtspserver

import android.util.Log
import com.pedro.rtsp.rtsp.Protocol
import com.pedro.rtsp.rtsp.RtspSender
import com.pedro.rtsp.utils.ConnectCheckerRtsp
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.net.SocketException
import java.nio.ByteBuffer

class ServerClient(private val socket: Socket, serverIp: String, serverPort: Int,
    connectCheckerRtsp: ConnectCheckerRtsp, sps: ByteBuffer,
    pps: ByteBuffer, vps: ByteBuffer?, private val sampleRate: Int,
    isStereo: Boolean, private val listener: ClientListener) : Thread() {

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
        listener.onDisconnected(this)
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