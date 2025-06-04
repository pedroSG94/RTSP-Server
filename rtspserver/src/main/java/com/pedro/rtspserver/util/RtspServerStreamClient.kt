package com.pedro.rtspserver.util

import com.pedro.common.socket.base.SocketType
import com.pedro.library.util.streamclient.StreamBaseClient
import com.pedro.rtspserver.server.ClientListener
import com.pedro.rtspserver.server.IpType
import com.pedro.rtspserver.server.RtspServer

/**
 * Created by pedro on 20/12/23.
 */
class RtspServerStreamClient(
  private val rtspServer: RtspServer,
): StreamBaseClient() {

  fun forceIpType(ipType: IpType) {
    rtspServer.forceIpType(ipType)
  }

  fun setClientListener(clientListener: ClientListener?) {
    rtspServer.setClientListener(clientListener)
  }

  fun getNumClients(): Int = rtspServer.getNumClients()

  fun getEndPointConnection(): String = "rtsp://${rtspServer.serverIp}:${rtspServer.port}/"

  override fun setAuthorization(user: String?, password: String?) {
    rtspServer.setAuth(user, password)
  }

  override fun setBitrateExponentialFactor(factor: Float) {
    rtspServer.setBitrateExponentialFactor(factor)
  }

  override fun setReTries(reTries: Int) {
  }

  override fun setSocketType(type: SocketType) {
    rtspServer.setSocketType(type)
  }

  override fun reTry(delay: Long, reason: String, backupUrl: String?): Boolean {
    return false
  }

  override fun hasCongestion(percentUsed: Float): Boolean = rtspServer.hasCongestion(percentUsed)

  override fun setLogs(enabled: Boolean) {
    rtspServer.setLogs(enabled)
  }

  override fun setCheckServerAlive(enabled: Boolean) {
  }

  override fun setDelay(millis: Long) {
    rtspServer.setDelay(millis)
  }

  override fun resizeCache(newSize: Int) {
    rtspServer.resizeCache(newSize)
  }

  override fun clearCache() {
    rtspServer.clearCache()
  }

  override fun getBitrateExponentialFactor(): Float = rtspServer.getBitrateExponentialFactor()

  override fun getCacheSize(): Int = rtspServer.cacheSize

  override fun getItemsInCache(): Int = rtspServer.getItemsInCache()

  override fun getSentAudioFrames(): Long = rtspServer.sentAudioFrames

  override fun getSentVideoFrames(): Long = rtspServer.sentVideoFrames

  override fun getDroppedAudioFrames(): Long = rtspServer.droppedAudioFrames

  override fun getDroppedVideoFrames(): Long = rtspServer.droppedVideoFrames

  override fun resetSentAudioFrames() {
    rtspServer.resetSentAudioFrames()
  }

  override fun resetSentVideoFrames() {
    rtspServer.resetSentVideoFrames()
  }

  override fun resetDroppedAudioFrames() {
    rtspServer.resetDroppedAudioFrames()
  }

  override fun resetDroppedVideoFrames() {
    rtspServer.resetDroppedVideoFrames()
  }

  override fun setOnlyAudio(onlyAudio: Boolean) {
    rtspServer.setOnlyAudio(onlyAudio)
  }

  override fun setOnlyVideo(onlyVideo: Boolean) {
    rtspServer.setOnlyVideo(onlyVideo)
  }
}