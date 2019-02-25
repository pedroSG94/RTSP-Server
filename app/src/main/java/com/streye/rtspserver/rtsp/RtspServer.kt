package com.streye.rtspserver.rtsp

import android.content.Context
import android.media.MediaCodec
import android.net.wifi.WifiManager
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.pedro.rtsp.rtsp.Body
import com.pedro.rtsp.rtsp.Protocol
import com.pedro.rtsp.rtsp.RtspSender
import com.pedro.rtsp.utils.ConnectCheckerRtsp
import java.io.*
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.ByteBuffer
import java.util.regex.Pattern

/**
 *
 * Created by pedro on 13/02/19.
 *
 *
 * TODO Use different session per client.
 *
 * TODO TCP support.
 */

class RtspServer(context: Context, private val connectCheckerRtsp: ConnectCheckerRtsp,
  val port: Int) {

  private val TAG = "RtspServer"
  private lateinit var server: ServerSocket
  val serverIp = getServerIp(context)
  var sps: ByteArray? = null
  var pps: ByteArray? = null
  var vps: ByteArray? = null
  var sampleRate = 32000
  var isStereo = true
  private val clients = mutableListOf<Client>()
  private var thread: Thread? = null

  fun startServer() {
    thread = Thread {
      server = ServerSocket(port)
      while (!Thread.interrupted()) {
        Log.i(TAG, "Server started $serverIp:$port")
        try {
          val client =
              Client(server.accept(), serverIp, port, connectCheckerRtsp, sps, pps, vps, sampleRate,
                isStereo)
          client.start()
          clients.add(client)
        } catch (e: SocketException) {
          Log.e(TAG, "Error", e)
          break
        } catch (e: IOException) {
          Log.e(TAG, e.message)
          continue
        }
      }
      Log.i(TAG, "Server finished")
    }
    thread?.start()
  }

  fun stopServer() {
    clients.forEach { it.stopClient() }
    clients.clear()
    thread?.interrupt()
    try {
      thread?.join(100)
    } catch (e: InterruptedException) {
      thread?.interrupt()
    }
    thread = null
    if (!server.isClosed) server.close()
  }

  fun sendVideo(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    clients.forEach {
      if (it.isAlive) {
        it.rtspSender.sendVideoFrame(h264Buffer.duplicate(), info)
      }
    }
  }

  fun sendAudio(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    clients.forEach {
      if (it.isAlive) {
        it.rtspSender.sendAudioFrame(aacBuffer.duplicate(), info)
      }
    }
  }

  fun setVideoInfo(sps: ByteBuffer, pps: ByteBuffer, vps: ByteBuffer?) {
    this.sps = getData(sps)
    this.pps = getData(pps)
    this.vps = getData(vps)  //H264 has no vps so if not null assume H265
  }

  private fun getData(byteBuffer: ByteBuffer?): ByteArray? {
    return if (byteBuffer != null) {
      val bytes = ByteArray(byteBuffer.capacity() - 4)
      byteBuffer.position(4)
      byteBuffer.get(bytes, 0, bytes.size)
      bytes
    } else {
      null
    }
  }

  private fun getServerIp(context: Context): String {
    val wm =
        context.applicationContext.getSystemService(AppCompatActivity.WIFI_SERVICE) as WifiManager
    return InetAddress.getByAddress(ByteArray(4) { i ->
      wm.connectionInfo.ipAddress.shr(i * 8).and(255).toByte()
    }).hostAddress
  }

  internal class Client(private val socket: Socket, private val serverIp: String,
    private val serverPort: Int, connectCheckerRtsp: ConnectCheckerRtsp, sps: ByteArray?,
    pps: ByteArray?, vps: ByteArray?, private val sampleRate: Int, private val isStereo: Boolean) :
      Thread() {

    private val TAG = "Client"
    private var cSeq = 0

    private val output = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
    private val input = BufferedReader(InputStreamReader(socket.getInputStream()))
    private val clientIp = socket.inetAddress.hostAddress
    val rtspSender = RtspSender(connectCheckerRtsp, Protocol.UDP, sps, pps, vps, sampleRate)

    private val trackAudio = 0
    private val trackVideo = 1
    private var audioPorts = ArrayList<Int>()
    private var videoPorts = ArrayList<Int>()

    override fun run() {
      super.run()
      Log.i(TAG, "New client $clientIp")
      while (!Thread.interrupted()) {
        try {
          val request = getRequest(input)
          cSeq = getCSeq(request) //update cSeq
          if (cSeq == -1) { //If cSeq parsed fail send error to client
            output.write(createError(500))
            output.flush()
            continue
          }
          val action = request.split("\n")[0]
          Log.i(TAG, request)
          val response = createResponse(action, request)
          Log.i(TAG, response)
          output.write(response)
          output.flush()

          if (action.contains("play", true)) {
            rtspSender.setDataStream(socket.getOutputStream(), clientIp)

            rtspSender.setVideoPorts(videoPorts[0], videoPorts[1])
            rtspSender.setAudioPorts(audioPorts[0], audioPorts[1])

            rtspSender.start()
          }
        } catch (e: SocketException) {
          // Client has left
          Log.e(TAG, "Client disconnected")
          break
        } catch (e: Exception) {
          Log.e(TAG, "Unexpected error")
        }
      }
    }

    fun stopClient() {
      rtspSender.stop()
      interrupt()
      try {
        join(100)
      } catch (e: InterruptedException) {
        interrupt()
      } finally {
        socket.close()
      }
    }

    private fun createResponse(action: String, request: String): String {
      return when {
        action.contains("options", true) -> createOptions()
        action.contains("describe", true) -> createDescribe()
        action.contains("setup", true) -> {
          if (loadPorts(request)) createSetup() else createError(500)
        }
        action.contains("play", true) -> createPlay()
        action.contains("pause", true) -> createPause()
        action.contains("teardown", true) -> createTeardown()
        else -> createError(400)
      }
    }

    private fun loadPorts(request: String): Boolean {
      val ports = ArrayList<Int>()
      val portsMatcher = Pattern.compile("client_port=(\\d+)(?:-(\\d+))?", Pattern.CASE_INSENSITIVE)
          .matcher(request)
      if (portsMatcher.find()) {
        ports.add(portsMatcher.group(1).toInt())
        ports.add(portsMatcher.group(2).toInt())
      } else {
        Log.e(TAG, "UDP ports not found")
        return false
      }
      val trackMatcher =
          Pattern.compile("trackID=(\\w+)", Pattern.CASE_INSENSITIVE).matcher(request)
      if (trackMatcher.find()) {
        val track = trackMatcher.group(1).toInt()
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

    private fun getCSeq(request: String): Int {
      val cSeqMatcher =
          Pattern.compile("CSeq\\s*:\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(request)
      val cSeq: Int
      if (cSeqMatcher.find()) {
        cSeq = cSeqMatcher.group(1).toInt()
      } else {
        Log.e(TAG, "cSeq not found")
        return -1
      }
      return cSeq
    }

    @Throws(IOException::class, IllegalStateException::class, SocketException::class)
    private fun getRequest(input: BufferedReader): String {
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

    private fun createError(code: Int): String {
      return "RTSP/1.0 ${createStatus(code)}\r\n" +
              "Server: pedroSG94 Server\r\n" +
              "Cseq: $cSeq\r\n\r\n"
    }

    private fun createHeader(): String {
      return "RTSP/1.0 ${createStatus(200)}\r\n" +
          "Server: pedroSG94 Server\r\n" +
          "Cseq: $cSeq\r\n"
    }

    private fun createOptions(): String {
      return createHeader() +
          "Public: DESCRIBE,SETUP,TEARDOWN,PLAY,PAUSE\r\n\r\n"
    }

    private fun createDescribe(): String {
      val body = createBody()
      return createHeader() +
          "Content-Length: ${body.length}\r\n" +
          "Content-Base: $serverIp:$serverPort/\r\n" +
          "Content-Type: application/sdp\r\n\r\n" +
          body
    }

    private fun createBody(): String {
      val bodyAudio = Body.createAacBody(trackAudio, sampleRate, isStereo)
      val bodyVideo = Body.createH264Body(trackVideo, "Z0KAHtoHgUZA", "aM4NiA==")
      return "v=0\r\n" +
          "o=- 0 0 IN IP4 $serverIp\r\n" +
          "s=Unnamed\r\n" + "i=N/A\r\n" +
          "c=IN IP4 $clientIp\r\n" +
          "t=0 0\r\n" +
          "a=recvonly\r\n" +
          bodyAudio + bodyVideo + "\r\n"
    }

    private fun createSetup(): String {
      return createHeader() +
          "Content-Length: 0\r\n" +
          "Transport: RTP/AVP/UDP;unicast;destination=$clientIp;client_port=8000-8001;server_port=39000-35968;ssrc=46a81ad7;mode=play\r\n" +
          "Session: 1185d20035702ca\r\n" +
          "Cache-Control: no-cache\r\n\r\n"
    }

    private fun createPlay(): String {
      return createHeader() +
          "Content-Length: 0\r\n" +
          "RTP-Info: url=rtsp://$serverIp:$serverPort/\r\n" +
          "Session: 1185d20035702ca\r\n\r\n"
    }

    private fun createPause(): String {
      return createHeader() +
          "Content-Length: 0\r\n\r\n"
    }

    private fun createTeardown(): String {
      return createHeader() + "\r\n"
    }
  }
}