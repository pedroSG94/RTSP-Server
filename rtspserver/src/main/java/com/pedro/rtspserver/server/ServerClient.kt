package com.pedro.rtspserver.server

import android.media.MediaCodec
import android.util.Log
import com.pedro.common.ConnectChecker
import com.pedro.common.clone
import com.pedro.common.frame.MediaFrame
import com.pedro.common.onMainThreadHandler
import com.pedro.common.toMediaFrameInfo
import com.pedro.rtsp.rtsp.RtspSender
import com.pedro.rtsp.rtsp.commands.Method
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import java.io.IOException
import java.nio.ByteBuffer

class ServerClient(
  private val socket: ClientSocket, serverIp: String, serverPort: Int,
  private val connectChecker: ConnectChecker,
  private val serverCommandManager: ServerCommandManager,
  private val listener: ServerListener
) {

  private val TAG = "Client"
  private val rtspSender = RtspSender(connectChecker, serverCommandManager)
  private val scope = CoroutineScope(Dispatchers.IO)
  private var job: Job? = null
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

  fun startClient() {
    job = scope.launch {
      socket.connect()
      while (job?.isActive == true) {
        try {
          val request = serverCommandManager.getRequest(socket)
          val cSeq = request.cSeq //update cSeq
          if (cSeq == -1) { //If cSeq parsed fail send error to client
            socket.write(serverCommandManager.createError(500, cSeq))
            socket.flush()
            continue
          }
          val response = serverCommandManager.createResponse(request.method, request.text, cSeq, socket.getHost())
          Log.i(TAG, response)
          socket.write(response)
          socket.flush()

          if (request.method == Method.PLAY) {
            Log.i(TAG, "Protocol ${serverCommandManager.protocol}")

            if (!serverCommandManager.videoDisabled) {
              rtspSender.setVideoInfo(serverCommandManager.sps!!, serverCommandManager.pps, serverCommandManager.vps)
            }
            if (!serverCommandManager.audioDisabled) {
              rtspSender.setAudioInfo(serverCommandManager.sampleRate, serverCommandManager.isStereo)
            }

            val videoPorts = if (!serverCommandManager.videoDisabled) {
              serverCommandManager.videoPorts.toTypedArray()
            } else arrayOf<Int?>(null, null)
            val videoServerPorts = if (!serverCommandManager.videoDisabled) {
              serverCommandManager.videoServerPorts
            } else arrayOf<Int?>(null, null)
            val audioPorts = if (!serverCommandManager.audioDisabled) {
              serverCommandManager.audioPorts.toTypedArray()
            } else arrayOf<Int?>(null, null)
            val audioServerPorts = if (!serverCommandManager.audioDisabled) {
              serverCommandManager.audioServerPorts
            } else arrayOf<Int?>(null, null)

            rtspSender.setSocketsInfo(
              serverCommandManager.protocol,
              socket.getHost(),
              videoServerPorts,
              audioServerPorts,
              videoPorts, audioPorts
            )
            rtspSender.setSocket(socket)
            rtspSender.start()
            onMainThreadHandler {
              connectChecker.onConnectionSuccess()
            }
            canSend = true
          } else if (request.method == Method.TEARDOWN) {
            Log.i(TAG, "Client disconnected")
            listener.onClientDisconnected(this@ServerClient)
            onMainThreadHandler {
              connectChecker.onDisconnect()
            }
          }
        } catch (e: IOException) { // Client has left
          Log.e(TAG, "Client disconnected", e)
          listener.onClientDisconnected(this@ServerClient)
          break
        } catch (e: Exception) {
          Log.e(TAG, "Unexpected error", e)
        }
      }
    }
  }

  fun stopClient() {
    CoroutineScope(Dispatchers.IO).launch {
      canSend = false
      rtspSender.stop()
      job?.cancelAndJoin()
      job = null
      socket.close()
    }
  }

  fun getAddress() = socket.getHost()

  fun isAlive(): Boolean = job?.isActive == true

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

  fun sendVideoFrame(videoBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    rtspSender.sendMediaFrame(MediaFrame(videoBuffer.clone(), info.toMediaFrameInfo(), MediaFrame.Type.VIDEO))
  }

  fun sendAudioFrame(audioBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    rtspSender.sendMediaFrame(MediaFrame(audioBuffer.clone(), info.toMediaFrameInfo(), MediaFrame.Type.AUDIO))
  }

  fun setBitrateExponentialFactor(factor: Float) {
    rtspSender.setBitrateExponentialFactor(factor)
  }

  fun getBitrateExponentialFactor() = rtspSender.getBitrateExponentialFactor()
}