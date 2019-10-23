package com.pedro.rtspserver

import android.content.Context
import android.media.MediaCodec
import android.os.Build
import androidx.annotation.RequiresApi
import com.pedro.encoder.utils.CodecUtil
import com.pedro.rtplibrary.base.Camera2Base
import com.pedro.rtplibrary.view.LightOpenGlView
import com.pedro.rtplibrary.view.OpenGlView
import com.pedro.rtsp.rtsp.VideoCodec
import com.pedro.rtsp.utils.ConnectCheckerRtsp
import java.nio.ByteBuffer

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class RtspServerCamera2 : Camera2Base {

  private val rtspServer: RtspServer

  constructor(openGlView: OpenGlView, connectCheckerRtsp: ConnectCheckerRtsp, port: Int) : super(
    openGlView) {
    rtspServer = RtspServer(openGlView.context, connectCheckerRtsp, port)
  }

  constructor(lightOpenGlView: LightOpenGlView, connectCheckerRtsp: ConnectCheckerRtsp,
    port: Int) : super(lightOpenGlView) {
    rtspServer = RtspServer(lightOpenGlView.context, connectCheckerRtsp, port)
  }

  constructor(context: Context, useOpengl: Boolean, connectCheckerRtsp: ConnectCheckerRtsp,
    port: Int) : super(context, useOpengl) {
    rtspServer = RtspServer(context, connectCheckerRtsp, port)
  }

  fun setVideoCodec(videoCodec: VideoCodec) {
    videoEncoder.type =
      if (videoCodec == VideoCodec.H265) CodecUtil.H265_MIME else CodecUtil.H264_MIME
  }

  fun getEndPointConnection(): String = "rtsp://${rtspServer.serverIp}:${rtspServer.port}/"

  override fun setAuthorization(user: String, password: String) { //not developed
  }

  fun startStream() {
    super.startStream("")
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

  override fun onSpsPpsVpsRtp(sps: ByteBuffer, pps: ByteBuffer, vps: ByteBuffer?) {
    val newSps = sps.duplicate()
    val newPps = pps.duplicate()
    val newVps = vps?.duplicate()
    rtspServer.setVideoInfo(newSps, newPps, newVps)
    rtspServer.startServer()
  }

  override fun getH264DataRtp(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    rtspServer.sendVideo(h264Buffer, info)
  }

  /**
   * Unused functions
   */
  @Throws(RuntimeException::class)
  override fun resizeCache(newSize: Int) {
  }

  override fun shouldRetry(reason: String?): Boolean = false

  override fun reConnect(delay: Long) {
  }

  override fun setReTries(reTries: Int) {
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