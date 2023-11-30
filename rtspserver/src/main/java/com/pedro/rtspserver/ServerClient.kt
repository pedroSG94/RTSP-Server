package com.pedro.rtspserver

import android.util.Log
import com.pedro.common.AudioCodec
import com.pedro.rtsp.rtsp.Protocol
import com.pedro.rtsp.rtsp.RtspSender
import com.pedro.rtsp.rtsp.commands.Method
import com.pedro.common.ConnectChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.net.SocketException
import java.nio.ByteBuffer

open class ServerClient(
  private val socket: Socket, serverIp: String, serverPort: Int,
  private val connectChecker: ConnectChecker, clientAddress: String, sps: ByteBuffer?,
  pps: ByteBuffer?, vps: ByteBuffer?, sampleRate: Int, isStereo: Boolean, audioCodec: AudioCodec,
  videoDisabled: Boolean, audioDisabled: Boolean, user: String?, password: String?,
  private val listener: ClientListener
) : Thread() {

  private val TAG = "Client"
  private val output = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
  private val input = BufferedReader(InputStreamReader(socket.getInputStream()))
  val rtspSender = RtspSender(connectChecker)
  val commandsManager = ServerCommandManager(serverIp, serverPort, clientAddress)
  var canSend = false

  init {
    commandsManager.videoDisabled = videoDisabled
    commandsManager.audioDisabled = audioDisabled
    commandsManager.isStereo = isStereo
    commandsManager.audioCodec = audioCodec
    commandsManager.sampleRate = sampleRate
    if (!commandsManager.videoDisabled) {
      commandsManager.setVideoInfo(sps!!, pps!!, vps)
    }
    commandsManager.setAuth(user, password)
  }

  override fun run() {
    super.run()
    Log.i(TAG, "New client ${commandsManager.clientIp}")
    while (!interrupted()) {
      try {
        val request = commandsManager.getRequest(input)
        val cSeq = request.cSeq //update cSeq
        if (cSeq == -1) { //If cSeq parsed fail send error to client
          output.write(commandsManager.createError(500, cSeq))
          output.flush()
          continue
        }
        val response = commandsManager.createResponse(request.method, request.text, cSeq)
        Log.i(TAG, response)
        output.write(response)
        output.flush()

        if (request.method == Method.PLAY) {
          Log.i(TAG, "Protocol ${commandsManager.protocol}")
          rtspSender.setSocketsInfo(commandsManager.protocol, commandsManager.videoServerPorts,
              commandsManager.audioServerPorts)
          if (!commandsManager.videoDisabled) {
            rtspSender.setVideoInfo(commandsManager.sps!!, commandsManager.pps!!, commandsManager.vps)
          }
          if (!commandsManager.audioDisabled) {
            rtspSender.setAudioInfo(commandsManager.sampleRate)
          }
          rtspSender.setDataStream(socket.getOutputStream(), commandsManager.clientIp)
          if (commandsManager.protocol == Protocol.UDP) {
            if (!commandsManager.videoDisabled) {
              rtspSender.setVideoPorts(commandsManager.videoPorts[0], commandsManager.videoPorts[1])
            }
            if (!commandsManager.audioDisabled) {
              rtspSender.setAudioPorts(commandsManager.audioPorts[0], commandsManager.audioPorts[1])
            }
          }
          rtspSender.start()
          connectChecker.onConnectionSuccess()
          canSend = true
        } else if (request.method == Method.TEARDOWN) {
          Log.i(TAG, "Client disconnected")
          listener.onDisconnected(this)
          connectChecker.onDisconnect()
        }
      } catch (e: SocketException) { // Client has left
        Log.e(TAG, "Client disconnected", e)
        listener.onDisconnected(this)
        connectChecker.onConnectionFailed(e.message.toString())
        break
      } catch (e: Exception) {
        Log.e(TAG, "Unexpected error", e)
      }
    }
  }

  fun stopClient() {
    CoroutineScope(Dispatchers.IO).launch {
      canSend = false
      rtspSender.stop()
      interrupt()
      withContext(Dispatchers.IO) {
        try {
          join(100)
        } catch (e: InterruptedException) {
          interrupt()
        } finally {
          socket.close()
        }
      }
    }
  }

  fun hasCongestion(): Boolean = rtspSender.hasCongestion()
}