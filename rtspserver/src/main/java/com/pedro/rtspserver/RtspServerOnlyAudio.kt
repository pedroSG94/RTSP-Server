package com.pedro.rtspserver

import android.media.MediaCodec
import com.pedro.rtplibrary.base.OnlyAudioBase
import com.pedro.rtsp.utils.ConnectCheckerRtsp
import java.nio.ByteBuffer

/**
 * Created by pedro on 17/04/21.
 */
open class RtspServerOnlyAudio(connectCheckerRtsp: ConnectCheckerRtsp, port: Int) : OnlyAudioBase() {

  private val rtspServer = RtspServer(connectCheckerRtsp, port)

  init {
    rtspServer.setOnlyAudio(true)
  }

  fun getNumClients(): Int = rtspServer.getNumClients()

  fun getEndPointConnection(): String = "rtsp://${rtspServer.serverIp}:${rtspServer.port}/"

  override fun setAuthorization(user: String, password: String) {
    rtspServer.setAuth(user, password)
  }

  fun startStream() {
    super.startStream("")
    rtspServer.startServer()
  }

  override fun prepareAudioRtp(isStereo: Boolean, sampleRate: Int) {
    rtspServer.isStereo = isStereo
    rtspServer.sampleRate = sampleRate
  }

  override fun startStreamRtp(url: String) { //unused
  }

  override fun stopStreamRtp() {
    rtspServer.stopServer()
  }

  override fun getAacDataRtp(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    rtspServer.sendAudio(aacBuffer, info)
  }

  override fun setLogs(enable: Boolean) {
    rtspServer.setLogs(enable)
  }

  override fun setCheckServerAlive(enable: Boolean) {
  }

  /**
   * Unused functions
   */
  @Throws(RuntimeException::class)
  override fun resizeCache(newSize: Int) {
  }

  override fun shouldRetry(reason: String?): Boolean = false

  override fun hasCongestion(): Boolean = rtspServer.hasCongestion()

  override fun setReTries(reTries: Int) {
  }

  override fun reConnect(delay: Long, backupUrl: String?) {
  }

  override fun getCacheSize(): Int = 0

  override fun getSentAudioFrames(): Long = 0

  override fun getSentVideoFrames(): Long = 0

  override fun getDroppedAudioFrames(): Long = 0

  override fun getDroppedVideoFrames(): Long = 0

  override fun resetSentAudioFrames() {
  }

  override fun resetSentVideoFrames() {
  }

  override fun resetDroppedAudioFrames() {
  }

  override fun resetDroppedVideoFrames() {
  }
}