package com.pedro.rtspserver

import android.content.Context
import android.media.MediaCodec
import android.os.Build
import androidx.annotation.RequiresApi
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.library.base.StreamBase
import com.pedro.library.util.sources.audio.AudioSource
import com.pedro.library.util.sources.audio.MicrophoneSource
import com.pedro.library.util.sources.video.Camera2Source
import com.pedro.library.util.sources.video.VideoSource
import com.pedro.rtspserver.server.RtspServer
import com.pedro.rtspserver.util.RtspServerStreamClient
import java.nio.ByteBuffer

/**
 * Created by pedro on 13/02/19.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class RtspServerStream(
  context: Context, port: Int, connectChecker: ConnectChecker,
  videoSource: VideoSource, audioSource: AudioSource,
): StreamBase(context, videoSource, audioSource) {

  private val rtspServer = RtspServer(connectChecker, port)

  constructor(context: Context, port: Int, connectChecker: ConnectChecker):
      this(context, port, connectChecker, Camera2Source(context), MicrophoneSource())

  fun startStream() {
    super.startStream("")
    rtspServer.startServer()
  }

  override fun audioInfo(sampleRate: Int, isStereo: Boolean) {
    rtspServer.setAudioInfo(sampleRate, isStereo)
  }

  override fun rtpStartStream(endPoint: String) { //unused
  }

  override fun rtpStopStream() {
    rtspServer.stopServer()
  }

  override fun getAacDataRtp(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    rtspServer.sendAudio(aacBuffer, info)
  }

  override fun onSpsPpsVpsRtp(sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) {
    val newSps = sps.duplicate()
    val newPps = pps?.duplicate()
    val newVps = vps?.duplicate()
    rtspServer.setVideoInfo(newSps, newPps, newVps)
  }

  override fun getH264DataRtp(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    rtspServer.sendVideo(h264Buffer, info)
  }

  override fun getStreamClient(): RtspServerStreamClient = RtspServerStreamClient(rtspServer)

  override fun setVideoCodecImp(codec: VideoCodec) {
    rtspServer.setVideoCodec(codec)
  }

  override fun setAudioCodecImp(codec: AudioCodec) {
    rtspServer.setAudioCodec(codec);
  }
}