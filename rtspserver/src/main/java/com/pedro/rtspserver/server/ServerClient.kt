package com.pedro.rtspserver.server

import android.media.MediaCodec
import android.util.Log
import com.pedro.common.ConnectChecker
import com.pedro.common.clone
import com.pedro.common.frame.MediaFrame
import com.pedro.common.socket.base.SocketType
import com.pedro.rtsp.rtsp.RtspSender
import com.pedro.rtsp.rtsp.commands.Method
import com.pedro.rtspserver.util.toMediaFrameInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import java.io.IOException
import java.nio.ByteBuffer

class ServerClient(
  private val socket: ClientSocket, serverIp: String, serverPort: Int,
  serverCommandManager: ServerCommandManager,
  private val listener: ClientListener
) {

  private val TAG = "Client"
  private val connectChecker = object: ConnectChecker {
    override fun onAuthError() {}
    override fun onAuthSuccess() {}
    override fun onConnectionStarted(url: String) {}
    override fun onConnectionSuccess() {}
    override fun onDisconnect() {}

    override fun onNewBitrate(bitrate: Long) {
      listener.onClientNewBitrate(bitrate, this@ServerClient)
    }
    override fun onConnectionFailed(reason: String) {
      listener.onClientDisconnected(this@ServerClient)
    }
  }
  private val commandManager by lazy {
    ServerCommandManager().apply {
      setVideoInfo(serverCommandManager.sps!!, serverCommandManager.pps, serverCommandManager.vps)
      setAudioInfo(serverCommandManager.sampleRate, serverCommandManager.isStereo)
      setAuth(serverCommandManager.user, serverCommandManager.password)
      videoCodec = serverCommandManager.videoCodec
      audioCodec = serverCommandManager.audioCodec
      audioDisabled = serverCommandManager.audioDisabled
      videoDisabled = serverCommandManager.videoDisabled
    }
  }
  private val rtspSender = RtspSender(connectChecker, commandManager)
  private val scope = CoroutineScope(Dispatchers.IO)
  private var job: Job? = null
  var canSend = false
    private set
  //few players need start with timestamp 0 to work
  private var startTs = 0L

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
      startTs = 0L
      socket.connect()
      while (job?.isActive == true) {
        try {
          val request = commandManager.getRequest(socket)
          val cSeq = request.cSeq //update cSeq
          if (cSeq == -1) { //If cSeq parsed fail send error to client
            socket.write(commandManager.createError(500, cSeq))
            socket.flush()
            continue
          }
          val response = commandManager.createResponse(request.method, request.text, cSeq, socket.getHost())
          Log.i(TAG, response)
          socket.write(response)
          socket.flush()

          if (request.method == Method.PLAY) {
            Log.i(TAG, "Protocol ${commandManager.protocol}")

            if (!commandManager.videoDisabled) {
              rtspSender.setVideoInfo(commandManager.sps!!, commandManager.pps, commandManager.vps)
            }
            if (!commandManager.audioDisabled) {
              rtspSender.setAudioInfo(commandManager.sampleRate, commandManager.isStereo)
            }

            val videoPorts = if (!commandManager.videoDisabled) {
              commandManager.videoPorts
            } else arrayOf<Int?>(null, null)
            val videoServerPorts = if (!commandManager.videoDisabled) {
              commandManager.videoServerPorts
            } else arrayOf<Int?>(null, null)
            val audioPorts = if (!commandManager.audioDisabled) {
              commandManager.audioPorts
            } else arrayOf<Int?>(null, null)
            val audioServerPorts = if (!commandManager.audioDisabled) {
              commandManager.audioServerPorts
            } else arrayOf<Int?>(null, null)

            rtspSender.setSocketsInfo(
              SocketType.JAVA,
              commandManager.protocol,
              socket.getHost(),
              videoServerPorts,
              audioServerPorts,
              videoPorts, audioPorts
            )
            rtspSender.setSocket(socket)
            rtspSender.start()
            listener.onClientConnected(this@ServerClient)
            canSend = true
          } else if (request.method == Method.TEARDOWN) {
            Log.i(TAG, "Client disconnected")
            listener.onClientDisconnected(this@ServerClient)
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
      startTs = 0L
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
    if (canSend) {
      if (startTs == 0L) startTs = info.presentationTimeUs
      rtspSender.sendMediaFrame(MediaFrame(videoBuffer.clone(), info.toMediaFrameInfo(startTs), MediaFrame.Type.VIDEO))
    }
  }

  fun sendAudioFrame(audioBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    if (canSend) {
      if (startTs == 0L) startTs = info.presentationTimeUs
      rtspSender.sendMediaFrame(MediaFrame(audioBuffer.clone(), info.toMediaFrameInfo(startTs), MediaFrame.Type.AUDIO))
    }
  }

  fun setBitrateExponentialFactor(factor: Float) {
    rtspSender.setBitrateExponentialFactor(factor)
  }

  fun getBitrateExponentialFactor() = rtspSender.getBitrateExponentialFactor()
}