package com.streye.rtspserver.rtsp

import android.content.Context
import android.media.MediaCodec
import android.net.wifi.WifiManager
import android.support.v7.app.AppCompatActivity
import android.text.format.Formatter
import android.util.Log
import com.pedro.rtsp.rtsp.Body
import com.pedro.rtsp.rtsp.Protocol
import com.pedro.rtsp.rtsp.RtspSender
import com.pedro.rtsp.utils.ConnectCheckerRtsp
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.ByteBuffer


class RtspServer(context: Context, private val connectCheckerRtsp: ConnectCheckerRtsp,
  private val port: Int) : Thread() {

  private val TAG = "RtspServer"
  private val server: ServerSocket = ServerSocket(port)
  private val serverIp = getServerIp(context)
  var sps: ByteArray? = null
  var pps: ByteArray? = null
  var vps: ByteArray? = null
  var sampleRate = 32000
  var isStereo = true
  private var client: Client? = null

  override fun run() {
    super.run()
    while (!Thread.interrupted()) {
      Log.e(TAG, "Server started $serverIp:$port")
      try {
        client = Client(server.accept(), serverIp, port, connectCheckerRtsp, sps, pps, vps, sampleRate,
          isStereo)
        client?.start()
      } catch (e: SocketException) {
        break
      } catch (e: IOException) {
        Log.e(TAG, e.message)
        continue
      }
    }
    Log.i(TAG, "Server finished")
  }

  fun stopServer() {
    client?.interrupt()
    try {
      client?.join(100)
    } catch (e: InterruptedException) {
      client?.interrupt()
    }
    client = null
    this.interrupt()
    try {
      this.join(100)
    } catch (e: InterruptedException) {
      this.interrupt()
    }
  }

  fun sendVideo(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    if (client != null && client!!.isAlive) {
      client?.rtspSender?.sendVideoFrame(h264Buffer, info)
    }
  }

  fun sendAudio(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    if (client != null && client!!.isAlive) {
      client?.rtspSender?.sendAudioFrame(aacBuffer, info)
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
        context.applicationContext.getSystemService(AppCompatActivity.WIFI_SERVICE) as WifiManager?
    return Formatter.formatIpAddress(wm!!.connectionInfo.ipAddress)
  }

  internal class Client(private val socket: Socket, private val serverIp: String,
    private val serverPort: Int, private val connectCheckerRtsp: ConnectCheckerRtsp,
    private val sps: ByteArray?, private val pps: ByteArray?, private val vps: ByteArray?,
    private val sampleRate: Int, private val isStereo: Boolean) : Thread() {

    private val TAG = "Client"
    private var cSeq = 0

    private val output = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
    private val input = BufferedReader(InputStreamReader(socket.getInputStream()))
    private val clientIp = socket.inetAddress.hostAddress
    var rtspSender: RtspSender? = null

    private val trackAudio = 0
    private val trackVideo = 1
    private var audioPorts = ArrayList<Int>()
    private var videoPorts = ArrayList<Int>()

    override fun run() {
      super.run()
      Log.e(TAG, "New client $clientIp")
      while (!Thread.interrupted()) {
        try {
          val request = getRequest(input)
          cSeq = getCSeq(request) //update cSeq
          val action = request.split("\n")[0]
          Log.e(TAG, request)
          val response = createResponse(action)
          Log.e(TAG, response)
          output.write(response)
          output.flush()
          if (action.contains("play", true)) {
            rtspSender = RtspSender(connectCheckerRtsp, Protocol.UDP, sps, pps, vps, sampleRate)
            rtspSender?.setDataStream(socket.getOutputStream(), clientIp)

            rtspSender?.setVideoPorts(videoPorts[0], videoPorts[1])
            rtspSender?.setAudioPorts(audioPorts[0], audioPorts[1])

            rtspSender?.start()
          } else if (action.contains("setup", true)) {
            try {
              loadPorts(request)
            } catch (e: Exception) {
              Log.e(TAG, "error", e)
            }
            Log.e(TAG, "Video ports: $videoPorts")
            Log.e(TAG, "Audio ports: $audioPorts")
          }
        } catch (e: SocketException) {
          // Client has left
          Log.e(TAG, "Client disconnected")
          break
        } catch (e: Exception) {
          // We don't understand the request :/
          Log.e(TAG, "wtf is this?")
        }
      }
    }

    private fun createResponse(action: String): String {
      return when {
        action.contains("options", true) -> createOptions()
        action.contains("describe", true) -> createDescribe()
        action.contains("setup", true) -> createSetup()
        action.contains("play", true) -> createPlay()
        action.contains("pause", true) -> createPause()
        action.contains("teardown", true) -> createTeardown()
        else -> "Fail"  //TODO This should be a error response
      }
    }

    private fun loadPorts(request: String) {
      var ports: List<String> = ArrayList()
      var track = 0
      request.split("\n").forEach {
        if (it.contains("trackID", true)) {
          Log.e(TAG, "get track")
          track = it.split("=")[1].split(" ")[0].toInt()
        }
        if (it.contains("client_port=", true)) {
          Log.e(TAG, "get ports")
          ports = it.split("=")[1].split("-")
        }
      }
      if (ports.isNotEmpty()) { //udp ports
        if (track == 0) { //audio ports
          audioPorts.clear()
          audioPorts.add(ports[0].toInt())
          audioPorts.add(ports[1].toInt())
        } else { //video ports
          videoPorts.clear()
          videoPorts.add(ports[0].toInt())
          videoPorts.add(ports[1].toInt())
        }
      }
    }

    private fun getCSeq(request: String): Int {
      request.split("\n").forEach {
        if (it.contains("cseq", true)) {
          return it.toLowerCase().replace("cseq:", "").replace(" ", "").toInt()
        }
      }
      return 0
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

    private fun createOptions(): String {
      return "RTSP/1.0 200 OK\r\n" + "Server: pedroSG94 Server\r\n" + "Cseq: $cSeq\r\n" + "Public: DESCRIBE,SETUP,TEARDOWN,PLAY,PAUSE\r\n\r\n"
    }

    private fun createDescribe(): String {
      val body = createBody()
      return "RTSP/1.0 200 OK\r\n" + "Server: pedroSG94 Server\r\n" + "Cseq: $cSeq\r\n" + "Content-Length: ${body.length}\r\n" + "Content-Base: $serverIp:$serverPort/\r\n" + "Content-Type: application/sdp\r\n\r\n" + body
    }

    private fun createBody(): String {
      val bodyAudio = Body.createAacBody(trackAudio, sampleRate, isStereo)
      val bodyVideo = Body.createH264Body(trackVideo, "Z0KAHtoHgUZA", "aM4NiA==")
      return "v=0\r\n" + "o=- 0 0 IN IP4 $serverIp\r\n" + "s=Unnamed\r\n" + "i=N/A\r\n" + "c=IN IP4 $clientIp\r\n" + "t=0 0\r\n" + "a=recvonly\r\n" + bodyAudio + bodyVideo + "\r\n"
    }

    private fun createSetup(): String {
      return "RTSP/1.0 200 OK\r\n" + "Server: pedroSG94 Server\r\n" + "Cseq: $cSeq\r\n" + "Content-Length: 0\r\n" + "Transport: RTP/AVP/UDP;unicast;destination=$clientIp;client_port=8000-8001;server_port=39000-35968;ssrc=46a81ad7;mode=play\r\n" + "Session: 1185d20035702ca\r\n" + "Cache-Control: no-cache\r\n\r\n"
    }

    private fun createPlay(): String {
      return "RTSP/1.0 200 OK\r\n" + "Server: pedroSG94 Server\r\n" + "Cseq: $cSeq\r\n" + "Content-Length: 0\r\n" + "RTP-Info: url=rtsp://$serverIp:$serverPort/\r\n" + "Session: 1185d20035702ca\r\n\r\n"
    }

    private fun createPause(): String {
      return "RTSP/1.0 200 OK\r\n" + "Server: pedroSG94 Server\r\n" + "Cseq: $cSeq\r\n" + "Content-Length: 0\r\n\r\n"
    }

    private fun createTeardown(): String {
      return "RTSP/1.0 200 OK\r\n" + "Cseq: $cSeq\r\n\r\n"
    }
  }
}