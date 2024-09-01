package com.pedro.rtspserver

import android.media.MediaCodec
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.library.base.OnlyAudioBase
import com.pedro.rtspserver.server.RtspServer
import com.pedro.rtspserver.util.RtspServerStreamClient
import java.nio.ByteBuffer

/**
 * Created by pedro on 17/04/21.
 */
class RtspServerOnlyAudio(
  connectChecker: ConnectChecker, port: Int
): OnlyAudioBase() {

  private val rtspServer = RtspServer(connectChecker, port).apply {
    setOnlyAudio(true)
  }

  fun startStream() {
    super.startStream("")
    rtspServer.startServer()
  }

  override fun onAudioInfoImp(isStereo: Boolean, sampleRate: Int) {
    rtspServer.setAudioInfo(sampleRate, isStereo)
  }

  override fun startStreamImp(url: String?) { //unused
  }

  override fun stopStreamImp() {
    rtspServer.stopServer()
  }

  override fun getAudioDataImp(audioBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    rtspServer.sendAudio(audioBuffer, info)
  }

  override fun getStreamClient(): RtspServerStreamClient = RtspServerStreamClient(rtspServer)

  override fun setAudioCodecImp(codec: AudioCodec) {
    rtspServer.setAudioCodec(codec);
  }
}