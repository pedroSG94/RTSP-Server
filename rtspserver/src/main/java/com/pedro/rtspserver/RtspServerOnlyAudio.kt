package com.pedro.rtspserver

import android.media.MediaCodec
import com.pedro.common.AudioCodec
import com.pedro.library.base.OnlyAudioBase
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.encoder.utils.CodecUtil
import com.pedro.library.util.streamclient.StreamBaseClient
import java.nio.ByteBuffer

/**
 * Created by pedro on 17/04/21.
 */
open class RtspServerOnlyAudio(
  connectChecker: ConnectChecker, port: Int
) : OnlyAudioBase() {

  private val rtspServer = RtspServer(connectChecker, port)

  init {
    rtspServer.setOnlyAudio(true)
  }

  fun getNumClients(): Int = rtspServer.getNumClients()

  fun getEndPointConnection(): String = "rtsp://${rtspServer.serverIp}:${rtspServer.port}/"


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
  override fun getStreamClient(): StreamBaseClient {
    return streamClient;
  }
  override fun setAudioCodecImp(codec: AudioCodec) {
    rtspServer.setAudioCodec(codec);
  }
}