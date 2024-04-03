package com.pedro.rtspserver.server

import android.media.MediaCodec
import android.util.Log
import com.pedro.common.ConnectChecker
import com.pedro.common.onMainThreadHandler
import com.pedro.rtsp.rtsp.Protocol
import com.pedro.rtsp.rtsp.RtspSender
import com.pedro.rtsp.rtsp.commands.Method
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

class ServerClient(
  private val socket: Socket, serverIp: String, serverPort: Int,
  private val connectChecker: ConnectChecker,
  val clientAddress: String,
  private val serverCommandManager: ServerCommandManager,
  private val listener: ServerListener
): Thread() {

  private val TAG = "Client"
  private val output = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
  private val input = BufferedReader(InputStreamReader(socket.getInputStream()))
  private val rtspSender = RtspSender(connectChecker, serverCommandManager)
  var canSend = false
    private set

  val droppedAudioFrames: Long
    get() = rtspSender.droppedAudioFrames
  val droppedVideoFrames: Long
    get() = rtspSender.droppedVideoFrames

  val cacheSize: Int
    get() = rtspSender.getCacheSize()
  val sentAudioFrames: Long
    get() = rtspSender.getSentAudioFrames()
  val sentVideoFrames: Long
    get() = rtspSender.getSentVideoFrames()

  init {
    serverCommandManager.setServerInfo(serverIp, serverPort)
  }

  override fun run() {
    super.run()
    Log.i(TAG, "New client $clientAddress")
    while (!interrupted()) {
      try {
        val request = serverCommandManager.getRequest(input)
        val cSeq = request.cSeq //update cSeq
        if (cSeq == -1) { //If cSeq parsed fail send error to client
          output.write(serverCommandManager.createError(500, cSeq))
          output.flush()
          continue
        }
        val response = serverCommandManager.createResponse(request.method, request.text, cSeq, clientAddress)
        Log.i(TAG, response)
        output.write(response)
        output.flush()

        if (request.method == Method.PLAY) {
          Log.i(TAG, "Protocol ${serverCommandManager.protocol}")
          rtspSender.setSocketsInfo(serverCommandManager.protocol, serverCommandManager.videoServerPorts,
              serverCommandManager.audioServerPorts)
          if (!serverCommandManager.videoDisabled) {
            rtspSender.setVideoInfo(serverCommandManager.sps!!, serverCommandManager.pps, serverCommandManager.vps)
          }
          if (!serverCommandManager.audioDisabled) {
            rtspSender.setAudioInfo(serverCommandManager.sampleRate)
          }
          rtspSender.setDataStream(socket.getOutputStream(), clientAddress)
          if (serverCommandManager.protocol == Protocol.UDP) {
            if (!serverCommandManager.videoDisabled) {
              rtspSender.setVideoPorts(serverCommandManager.videoPorts[0], serverCommandManager.videoPorts[1])
            }
            if (!serverCommandManager.audioDisabled) {
              rtspSender.setAudioPorts(serverCommandManager.audioPorts[0], serverCommandManager.audioPorts[1])
            }
          }
          rtspSender.start()
          onMainThreadHandler {
            connectChecker.onConnectionSuccess()
          }
          canSend = true
        } else if (request.method == Method.TEARDOWN) {
          Log.i(TAG, "Client disconnected")
          listener.onClientDisconnected(this)
          onMainThreadHandler {
            connectChecker.onDisconnect()
          }
        }
      } catch (e: SocketException) { // Client has left
        Log.e(TAG, "Client disconnected", e)
        listener.onClientDisconnected(this)
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

  fun hasCongestion(percentUsed: Float): Boolean = rtspSender.hasCongestion(percentUsed)

  fun resetSentAudioFrames() {
    rtspSender.resetSentAudioFrames()
  }

  fun resetSentVideoFrames() {
    rtspSender.resetSentVideoFrames()
  }

  fun resetDroppedAudioFrames() {
    rtspSender.resetDroppedAudioFrames()
  }

  fun resetDroppedVideoFrames() {
    rtspSender.resetDroppedVideoFrames()
  }

  @Throws(RuntimeException::class)
  fun resizeCache(newSize: Int) {
    rtspSender.resizeCache(newSize)
  }

  fun setLogs(enable: Boolean) {
    rtspSender.setLogs(enable)
  }

  fun clearCache() {
    rtspSender.clearCache()
  }

  fun getItemsInCache(): Int = rtspSender.getItemsInCache()

  fun sendVideoFrame(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    rtspSender.sendVideoFrame(h264Buffer, info)
  }

  fun sendAudioFrame(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    rtspSender.sendAudioFrame(aacBuffer, info)
  }
}