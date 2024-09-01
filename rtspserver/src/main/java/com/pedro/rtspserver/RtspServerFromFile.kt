package com.pedro.rtspserver

import android.content.Context
import android.media.MediaCodec
import android.os.Build
import androidx.annotation.RequiresApi
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.encoder.input.decoder.AudioDecoderInterface
import com.pedro.encoder.input.decoder.VideoDecoderInterface
import com.pedro.library.base.FromFileBase
import com.pedro.library.view.OpenGlView
import com.pedro.rtspserver.server.RtspServer
import com.pedro.rtspserver.util.RtspServerStreamClient
import java.nio.ByteBuffer

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class RtspServerFromFile: FromFileBase {

  private val rtspServer: RtspServer

  constructor(openGlView: OpenGlView, connectCheckerRtsp: ConnectChecker, port: Int,
    videoDecoderInterface: VideoDecoderInterface,
    audioDecoderInterface: AudioDecoderInterface): super(openGlView, videoDecoderInterface,
    audioDecoderInterface) {
    rtspServer = RtspServer(connectCheckerRtsp, port)
  }

  constructor(context: Context, connectCheckerRtsp: ConnectChecker, port: Int,
    videoDecoderInterface: VideoDecoderInterface,
    audioDecoderInterface: AudioDecoderInterface): super(context, videoDecoderInterface,
    audioDecoderInterface) {
    rtspServer = RtspServer(connectCheckerRtsp, port)
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

  override fun onVideoInfoImp(sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) {
    val newSps = sps.duplicate()
    val newPps = pps?.duplicate()
    val newVps = vps?.duplicate()
    rtspServer.setVideoInfo(newSps, newPps, newVps)
  }

  override fun getVideoDataImp(videoBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    rtspServer.sendVideo(videoBuffer, info)
  }

  override fun getStreamClient(): RtspServerStreamClient = RtspServerStreamClient(rtspServer)

  override fun setVideoCodecImp(codec: VideoCodec) {
    rtspServer.setVideoCodec(codec)
  }

  override fun setAudioCodecImp(codec: AudioCodec) {
    rtspServer.setAudioCodec(codec);
  }
}