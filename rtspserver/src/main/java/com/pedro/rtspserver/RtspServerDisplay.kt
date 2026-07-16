package com.pedro.rtspserver

import android.content.Context
import android.media.MediaCodec
import android.os.Build
import androidx.annotation.RequiresApi
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.library.base.DisplayBase
import com.pedro.rtspserver.server.RtspServer
import com.pedro.rtspserver.util.RtspServerStreamClient
import java.nio.ByteBuffer

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class RtspServerDisplay(
  context: Context, useOpengl: Boolean,
  connectChecker: ConnectChecker, port: Int
): DisplayBase(context, useOpengl) {

  private val rtspServer: RtspServer = RtspServer(connectChecker, port)

  fun startStream() {
    super.startStream("")
  }

  override fun onAudioInfoImp(isStereo: Boolean, sampleRate: Int) {
    rtspServer.setAudioInfo(sampleRate, isStereo)
  }

  override fun startStreamImp(url: String) {
    rtspServer.startServer()
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