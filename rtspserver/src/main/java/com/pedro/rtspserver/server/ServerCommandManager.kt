package com.pedro.rtspserver.server

import android.util.Log
import com.pedro.common.AudioCodec
import com.pedro.common.VideoCodec
import com.pedro.common.socket.base.TcpStreamSocket
import com.pedro.rtsp.rtsp.Protocol
import com.pedro.rtsp.rtsp.commands.Command
import com.pedro.rtsp.rtsp.commands.CommandsManager
import com.pedro.rtsp.rtsp.commands.Method
import com.pedro.rtsp.rtsp.commands.SdpBody
import java.io.IOException
import java.net.SocketException
import java.util.regex.Pattern
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 *
 * Created by pedro on 23/10/19.
 *
 */
@OptIn(ExperimentalEncodingApi::class)
class ServerCommandManager: CommandsManager() {

  private var serverIp: String = ""
  private val urlServer: String
    get() = if (serverIp.contains(":")) "[$serverIp]" else serverIp
  private var serverPort: Int = 0

  private val TAG = "ServerCommandManager"
  val audioPorts = arrayOf<Int?>(null, null)
  val videoPorts = arrayOf<Int?>(null, null)

  fun setServerInfo(serverIp: String, serverPort: Int) {
    this.serverIp = serverIp
    this.serverPort = serverPort
  }

  fun createResponse(method: Method, request: String, cSeq: Int, clientIp: String): String {
    if (method == Method.UNKNOWN) return createError(400, cSeq)

    if (needAuth() && !isAuthorized(request)) {
      Log.e(TAG, "RTSP authentication failed")
      return createError(401, cSeq)
    }

    return when (method) {
      Method.OPTIONS -> createOptions(cSeq)
      Method.DESCRIBE -> createDescribe(cSeq, clientIp)
      Method.SETUP -> {
        val track = getTrack(request)
        if (track != null) {
          protocol = getProtocol(request, track)
          when (protocol) {
            Protocol.TCP -> createSetup(cSeq, track, clientIp)
            Protocol.UDP -> {
              if (loadPorts(request, track)) createSetup(cSeq, track, clientIp)
              else createError(500, cSeq)
            }
          }
        } else {
          createError(500, cSeq)
        }
      }
      Method.PLAY -> createPlay(cSeq)
      Method.PAUSE -> createPause(cSeq)
      Method.TEARDOWN -> createTeardown(cSeq)
      else -> createError(400, cSeq)
    }
  }

  private fun needAuth(): Boolean {
    return true
  }

  private fun isAuthorized(request: String): Boolean {
    val supplied = getAuth(request)
    if (supplied.isEmpty()) return false

    val expected = Base64.encode("$user:$password".toByteArray())
    return supplied == expected
  }

  private fun getAuth(request: String): String {
    val pattern = Pattern.compile(
      "(?im)^Authorization:\\s*Basic\\s+([A-Za-z0-9+/]+={0,2})\\s*$"
    )
    val matcher = pattern.matcher(request)
    return if (matcher.find()) matcher.group(1) ?: "" else ""
  }


  private fun getProtocol(request: String, track: Int): Protocol {
    return if (request.contains("UDP", true) || loadPorts(request, track)) {
      Protocol.UDP
    } else {
      Protocol.TCP
    }
  }

  private fun loadPorts(request: String, track: Int): Boolean {
    val ports = ArrayList<Int>()
    val portsMatcher =
        Pattern.compile("client_port=(\\d+)(?:-(\\d+))?", Pattern.CASE_INSENSITIVE).matcher(request)
    if (portsMatcher.find()) {
      portsMatcher.group(1)?.toIntOrNull()?.let { ports.add(it) }
      portsMatcher.group(2)?.toIntOrNull()?.let { ports.add(it) }
    }
    if (ports.size < 2) {
      Log.e(TAG, "UDP ports not found")
      return false
    }
    if (track == rtpTracks.trackAudio) { //audio ports
      audioPorts[0] = ports[0]
      audioPorts[1] = ports[1]
      Log.i(TAG, "Audio ports: ${audioPorts.contentToString()}")
    } else { //video ports
      videoPorts[0] = ports[0]
      videoPorts[1] = ports[1]
      Log.i(TAG, "Video ports: ${videoPorts.contentToString()}")
    }
    return true
  }

  private fun getTrack(request: String): Int? {
    val trackMatcher = Pattern.compile("streamid=(\\w+)", Pattern.CASE_INSENSITIVE).matcher(request)
    return if (trackMatcher.find()) {
      trackMatcher.group(1)?.toInt()
    } else {
      null
    }
  }

  @Throws(IOException::class, IllegalStateException::class, SocketException::class)
  suspend fun getRequest(socket: TcpStreamSocket): Command {
    return super.getResponse(socket, Method.UNKNOWN, null)
  }

  private fun createStatus(code: Int): String {
    return when (code) {
      200 -> "200 OK"
      400 -> "400 Bad Request"
      401 -> "401 Unauthorized"
      404 -> "404 Not Found"
      500 -> "500 Internal Server Error"
      else -> "500 Internal Server Error"
    }
  }

  fun createError(code: Int, cSeq: Int): String {
    val auth = if (code == 401) {
      "WWW-Authenticate: Basic realm=\"pedroSG94\"\r\n"
    } else ""
    return "RTSP/1.0 ${createStatus(code)}\r\nServer: pedroSG94 Server\r\n${auth}Cseq: $cSeq\r\n\r\n"
  }

  private fun createHeader(cSeq: Int): String {
    return "RTSP/1.0 ${createStatus(200)}\r\nServer: pedroSG94 Server\r\nCseq: $cSeq\r\n"
  }

  private fun createOptions(cSeq: Int): String {
    return "${createHeader(cSeq)}Public: DESCRIBE,SETUP,TEARDOWN,PLAY,PAUSE\r\n\r\n"
  }

  private fun createDescribe(cSeq: Int, clientIp: String): String {
    val body = createBody(clientIp)
    return "${createHeader(cSeq)}Content-Length: ${body.length}\r\nContent-Base: rtsp://$urlServer:$serverPort/\r\nContent-Type: application/sdp\r\n\r\n$body"
  }

  private fun createBody(clientIp: String): String {
    var audioBody = ""
    if (!audioDisabled) {
      audioBody = when (audioCodec) {
        AudioCodec.AAC -> SdpBody.createAacBody(rtpTracks.trackAudio, sampleRate, isStereo, false)
        AudioCodec.HE_AAC -> SdpBody.createAacBody(rtpTracks.trackAudio, sampleRate, isStereo, true)
        AudioCodec.G711 -> SdpBody.createG711Body(rtpTracks.trackAudio)
        AudioCodec.OPUS -> SdpBody.createOpusBody(rtpTracks.trackAudio, sampleRate, isStereo)
      }
    }
    var videoBody = ""
    if (!videoDisabled) {
      val sps = this.sps
      val pps = this.pps
      val vps = this.vps
      videoBody = when (videoCodec) {
        VideoCodec.H264 -> {
          if (sps == null || pps == null) throw IllegalArgumentException("sps or pps can't be null with h264")
          SdpBody.createH264Body(rtpTracks.trackVideo, sps, pps)
        }
        VideoCodec.H265 -> {
          if (sps == null || pps == null || vps == null) throw IllegalArgumentException("sps, pps or vps can't be null with h265")
          SdpBody.createH265Body(rtpTracks.trackVideo, sps, pps, vps)
        }
        VideoCodec.VP8 -> {
          SdpBody.createVp8Body(rtpTracks.trackVideo)
        }
        VideoCodec.VP9 -> {
          SdpBody.createVp9Body(rtpTracks.trackVideo)
        }
        VideoCodec.AV1 -> {
          if (sps == null) throw IllegalArgumentException("header can't be null with av1")
          SdpBody.createAV1Body(rtpTracks.trackVideo, sps)
        }
      }
    }
    val ipVersion = if (serverIp.contains(":")) "IP6" else "IP4"
    return "v=0\r\no=- 0 0 IN $ipVersion $serverIp\r\ns=Unnamed\r\ni=N/A\r\nc=IN $ipVersion $clientIp\r\nt=0 0\r\na=recvonly\r\n$videoBody$audioBody\r\n"
  }

  private fun createSetup(cSeq: Int, track: Int, clientIp: String): String {
    val protocolSetup = if (protocol == Protocol.UDP) {
      val clientPorts = if (track == rtpTracks.trackAudio) audioPorts else videoPorts
      val serverPorts = if (track == rtpTracks.trackAudio) audioServerPorts else videoServerPorts
      "UDP;unicast;destination=$clientIp;client_port=${clientPorts[0]}-${clientPorts[1]};server_port=${serverPorts[0]}-${serverPorts[1]}"
    } else {
      "TCP;unicast;interleaved=" + (2 * track) + "-" + (2 * track + 1)
    }
    return "${createHeader(cSeq)}Content-Length: 0\r\nTransport: RTP/AVP/$protocolSetup;mode=play\r\nSession: 1185d20035702ca\r\nCache-Control: no-cache\r\n\r\n"
  }

  private fun createPlay(cSeq: Int): String {
    var info = ""
    if (!videoDisabled) {
      info += "url=rtsp://$urlServer:$serverPort/streamid=${rtpTracks.trackVideo};seq=1;rtptime=0"
    }
    if (!audioDisabled) {
      if (!videoDisabled) info += ","
      info += "url=rtsp://$urlServer:$serverPort/streamid=${rtpTracks.trackAudio};seq=1;rtptime=0"
    }
    return "${createHeader(cSeq)}Content-Length: 0\r\nRTP-Info: $info\r\nSession: 1185d20035702ca\r\n\r\n"
  }

  private fun createPause(cSeq: Int): String {
    return "${createHeader(cSeq)}Content-Length: 0\r\n\r\n"
  }

  private fun createTeardown(cSeq: Int): String {
    return "${createHeader(cSeq)}\r\n"
  }
}
