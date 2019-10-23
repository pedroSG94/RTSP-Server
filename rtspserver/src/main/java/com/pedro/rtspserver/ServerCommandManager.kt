package com.pedro.rtspserver

import android.util.Log
import com.pedro.rtsp.rtsp.Body
import com.pedro.rtsp.rtsp.CommandsManager
import java.io.BufferedReader
import java.io.IOException
import java.net.SocketException
import java.util.regex.Pattern

/**
 *
 * Created by pedro on 23/10/19.
 *
 */
class ServerCommandManager(private val serverIp: String, private val serverPort: Int,
  val clientIp: String?) : CommandsManager() {

  private val TAG = "ServerCommandManager"
  var audioPorts = ArrayList<Int>()
  var videoPorts = ArrayList<Int>()

  fun createResponse(action: String, request: String, cSeq: Int): String {
    return when {
      action.contains("options", true) -> createOptions(cSeq)
      action.contains("describe", true) -> createDescribe(cSeq)
      action.contains("setup", true) -> {
        if (loadPorts(request)) createSetup(cSeq) else createError(500, cSeq)
      }
      action.contains("play", true) -> createPlay(cSeq)
      action.contains("pause", true) -> createPause(cSeq)
      action.contains("teardown", true) -> createTeardown(cSeq)
      else -> createError(400, cSeq)
    }
  }

  private fun loadPorts(request: String): Boolean {
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
    val trackMatcher = Pattern.compile("trackID=(\\w+)", Pattern.CASE_INSENSITIVE).matcher(request)
    if (trackMatcher.find()) {
      val track = trackMatcher.group(1)?.toInt()
      if (track == 0) { //audio ports
        audioPorts.clear()
        audioPorts.add(ports[0])
        audioPorts.add(ports[1])
      } else { //video ports
        videoPorts.clear()
        videoPorts.add(ports[0])
        videoPorts.add(ports[1])
      }
    } else {
      Log.e(TAG, "Track id not found")
      return false
    }
    Log.i(TAG, "Video ports: $videoPorts")
    Log.i(TAG, "Audio ports: $audioPorts")
    return true
  }

  fun getCSeq(request: String): Int {
    val cSeqMatcher =
        Pattern.compile("CSeq\\s*:\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(request)
    return if (cSeqMatcher.find()) {
      cSeqMatcher.group(1)?.toInt() ?: -1
    } else {
      Log.e(TAG, "cSeq not found")
      return -1
    }
  }

  @Throws(IOException::class, IllegalStateException::class, SocketException::class) fun getRequest(
    input: BufferedReader): String {
    var request = ""
    var line: String? = input.readLine()
    while (line != null && line.length > 3) {
      request += "$line\n"
      line = input.readLine()
    }
    return request
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
    return "RTSP/1.0 ${createStatus(
      code)}\r\n" + "Server: pedroSG94 Server\r\n" + "Cseq: $cSeq\r\n\r\n"
  }

  private fun createHeader(cSeq: Int): String {
    return "RTSP/1.0 ${createStatus(200)}\r\n" + "Server: pedroSG94 Server\r\n" + "Cseq: $cSeq\r\n"
  }

  private fun createOptions(cSeq: Int): String {
    return createHeader(cSeq) + "Public: DESCRIBE,SETUP,TEARDOWN,PLAY,PAUSE\r\n\r\n"
  }

  private fun createDescribe(cSeq: Int): String {
    val body = createBody()
    return createHeader(
      cSeq) + "Content-Length: ${body.length}\r\n" + "Content-Base: $serverIp:$serverPort/\r\n" + "Content-Type: application/sdp\r\n\r\n" + body
  }

  private fun createBody(): String {
    val bodyAudio = Body.createAacBody(trackAudio, sampleRate, isStereo)
    val bodyVideo = Body.createH264Body(trackVideo, "Z0KAHtoHgUZA", "aM4NiA==")
    return "v=0\r\n" + "o=- 0 0 IN IP4 $serverIp\r\n" + "s=Unnamed\r\n" + "i=N/A\r\n" + "c=IN IP4 $clientIp\r\n" + "t=0 0\r\n" + "a=recvonly\r\n" + bodyAudio + bodyVideo + "\r\n"
  }

  override fun createSetup(cSeq: Int): String {
    return createHeader(
      cSeq) + "Content-Length: 0\r\n" + "Transport: RTP/AVP/UDP;unicast;destination=$clientIp;client_port=8000-8001;server_port=39000-35968;ssrc=46a81ad7;mode=play\r\n" + "Session: 1185d20035702ca\r\n" + "Cache-Control: no-cache\r\n\r\n"
  }

  private fun createPlay(cSeq: Int): String {
    return createHeader(
      cSeq) + "Content-Length: 0\r\n" + "RTP-Info: url=rtsp://$serverIp:$serverPort/\r\n" + "Session: 1185d20035702ca\r\n\r\n"
  }

  private fun createPause(cSeq: Int): String {
    return createHeader(cSeq) + "Content-Length: 0\r\n\r\n"
  }

  private fun createTeardown(cSeq: Int): String {
    return createHeader(cSeq) + "\r\n"
  }
}