package com.pedro.rtspserver

import android.util.Base64
import android.util.Log
import com.pedro.rtsp.rtsp.Protocol
import com.pedro.rtsp.rtsp.commands.Command
import com.pedro.rtsp.rtsp.commands.CommandsManager
import com.pedro.rtsp.rtsp.commands.Method
import com.pedro.rtsp.rtsp.commands.SdpBody
import com.pedro.rtsp.utils.RtpConstants
import java.io.BufferedReader
import java.io.IOException
import java.net.SocketException
import java.util.regex.Pattern

/**
 *
 * Created by pedro on 23/10/19.
 *
 */
open class ServerCommandManager(private val serverIp: String, private val serverPort: Int,
                           val clientIp: String) : CommandsManager() {

  private val TAG = "ServerCommandManager"
  var audioPorts = ArrayList<Int>()
  var videoPorts = ArrayList<Int>()

  fun createResponse(method: Method, request: String, cSeq: Int): String {
    return when (method){
      Method.OPTIONS -> createOptions(cSeq)
      Method.DESCRIBE -> {
        if (needAuth()) {
          val auth = getAuth(request)
          val data = "$user:$password"
          val base64Data = Base64.encodeToString(data.toByteArray(), Base64.DEFAULT)
          if (base64Data.trim() == auth.trim()) {
            Log.i(TAG, "basic auth success")
            createDescribe(cSeq) // auth accepted
          } else {
            Log.e(TAG, "basic auth error")
            createError(401, cSeq)
          }
        } else {
          createDescribe(cSeq)
        }
      }
      Method.SETUP -> {
        val track = getTrack(request)
        if (track != null) {
          protocol = getProtocol(request, track)
          return when (protocol) {
            Protocol.TCP -> {
              createSetup(cSeq, track)
            }
            Protocol.UDP -> {
              if (loadPorts(request, track)) createSetup(cSeq, track) else createError(500, cSeq)
            }
            else -> {
              createError(500, cSeq)
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
    return !user.isNullOrEmpty() && !password.isNullOrEmpty()
  }

  private fun getAuth(request: String): String {
    val rtspPattern = Pattern.compile("Authorization: Basic ([\\w+/=]+)")
    val matcher = rtspPattern.matcher(request)
    return if (matcher.find()) {
      matcher.group(1) ?: ""
    } else {
      ""
    }
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
      portsMatcher.group(1)?.toInt()?.let { ports.add(it) }
      portsMatcher.group(2)?.toInt()?.let { ports.add(it) }
    } else {
      Log.e(TAG, "UDP ports not found")
      return false
    }
    if (track == RtpConstants.trackAudio) { //audio ports
      audioPorts.clear()
      audioPorts.add(ports[0])
      audioPorts.add(ports[1])
      Log.i(TAG, "Audio ports: $audioPorts")
    } else { //video ports
      videoPorts.clear()
      videoPorts.add(ports[0])
      videoPorts.add(ports[1])
      Log.i(TAG, "Video ports: $videoPorts")
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
  fun getRequest(input: BufferedReader): Command {
    return super.getResponse(input, Method.UNKNOWN)
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

  private fun createDescribe(cSeq: Int): String {
    val body = createBody()
    return "${createHeader(cSeq)}Content-Length: ${body.length}\r\nContent-Base: $serverIp:$serverPort/\r\nContent-Type: application/sdp\r\n\r\n$body"
  }

  private fun createBody(): String {
    var audioBody = ""
    if (!audioDisabled) {
      audioBody = SdpBody.createAacBody(RtpConstants.trackAudio, sampleRate, isStereo)
    }
    var videoBody = ""
    if (!videoDisabled) {
      videoBody = if (vps == null) SdpBody.createH264Body(RtpConstants.trackVideo, encodeToString(sps!!)!!, encodeToString(pps!!)!!)
      else SdpBody.createH265Body(RtpConstants.trackVideo, encodeToString(sps!!)!!, encodeToString(pps!!)!!, encodeToString(vps!!)!!)
    }
    return "v=0\r\no=- 0 0 IN IP4 $serverIp\r\ns=Unnamed\r\ni=N/A\r\nc=IN IP4 $clientIp\r\nt=0 0\r\na=recvonly\r\n$videoBody$audioBody\r\n"
  }

  private fun createSetup(cSeq: Int, track: Int): String {
    val protocolSetup = if (protocol == Protocol.UDP) {
      val clientPorts = if (track == RtpConstants.trackAudio) audioPorts else videoPorts
      val serverPorts = if (track == RtpConstants.trackAudio) audioServerPorts else videoServerPorts
      "UDP;unicast;destination=$clientIp;client_port=${clientPorts[0]}-${clientPorts[1]};server_port=${serverPorts[0]}-${serverPorts[1]}"
    } else {
      "TCP;unicast;interleaved=" + (2 * track) + "-" + (2 * track + 1)
    }
    return "${createHeader(cSeq)}Content-Length: 0\r\nTransport: RTP/AVP/$protocolSetup;mode=play\r\nSession: 1185d20035702ca\r\nCache-Control: no-cache\r\n\r\n"
  }

  private fun createPlay(cSeq: Int): String {
    var info = ""
    if (!videoDisabled) {
      info += "url=rtsp://$serverIp:$serverPort/streamid=${RtpConstants.trackVideo};seq=1;rtptime=0"
    }
    if (!audioDisabled) {
      if (!videoDisabled) info += ","
      info += "url=rtsp://$serverIp:$serverPort/streamid=${RtpConstants.trackAudio};seq=1;rtptime=0"
    }
    return "${createHeader(cSeq)}Content-Length: 0\r\nRTP-Info: $info\r\nSession: 1185d20035702ca\r\n\r\n"
  }

  private fun createPause(cSeq: Int): String {
    return "${createHeader(cSeq)}Content-Length: 0\r\n\r\n"
  }

  private fun createTeardown(cSeq: Int): String {
    return "${createHeader(cSeq)}\r\n"
  }

  private fun encodeToString(bytes: ByteArray): String? {
    return Base64.encodeToString(bytes, 0, bytes.size, Base64.NO_WRAP)
  }
}