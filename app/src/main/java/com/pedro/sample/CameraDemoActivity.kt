package com.pedro.sample

import android.os.Build
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraOpenException
import com.pedro.library.base.recording.RecordController
import com.pedro.library.view.OpenGlView
import com.pedro.rtspserver.RtspServerCamera2
import com.pedro.rtspserver.server.ClientListener
import com.pedro.rtspserver.server.ServerClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class CameraDemoActivity : AppCompatActivity(), ConnectChecker, ClientListener {

  private lateinit var rtspServerCamera2: RtspServerCamera2
  private lateinit var bStream: ImageView
  private lateinit var bRecord: ImageView
  private lateinit var bSwitchCamera: ImageView
  private lateinit var surfaceView: OpenGlView
  private lateinit var tvUrl: TextView
  private var recordPath = ""
  private var currentCamera = 0

  @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    setContentView(R.layout.activity_camera_demo)
    tvUrl = findViewById(R.id.tv_url)
    bStream = findViewById(R.id.b_start_stop)
    bRecord = findViewById(R.id.b_record)
    bSwitchCamera = findViewById(R.id.switch_camera)
    surfaceView = findViewById(R.id.surfaceView)
    rtspServerCamera2 = RtspServerCamera2(surfaceView, this, 1935)
    rtspServerCamera2.streamClient.setClientListener(this)
    surfaceView.holder.addCallback(object: SurfaceHolder.Callback {
      override fun surfaceCreated(holder: SurfaceHolder) {

      }

      override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (!rtspServerCamera2.isOnPreview) rtspServerCamera2.startPreview(rtspServerCamera2.camerasAvailable[currentCamera])
      }

      override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && rtspServerCamera2.isRecording) {
          rtspServerCamera2.stopRecord()
          bRecord.setBackgroundResource(R.drawable.record_icon)
          PathUtils.updateGallery(this@CameraDemoActivity, recordPath)
        }
        if (rtspServerCamera2.isStreaming) {
          rtspServerCamera2.stopStream()
          bStream.setImageResource(R.drawable.stream_icon)
        }
        if (rtspServerCamera2.isOnPreview) rtspServerCamera2.stopPreview()
        ScreenOrientation.unlockScreen(this@CameraDemoActivity)
      }
    })

    bStream.setOnClickListener {
      if (rtspServerCamera2.isStreaming) {
        bStream.setImageResource(R.drawable.stream_icon)
        rtspServerCamera2.stopStream()
        if (!rtspServerCamera2.isRecording) ScreenOrientation.unlockScreen(this)
      } else if (rtspServerCamera2.isRecording || prepare()) {
        bStream.setImageResource(R.drawable.stream_stop_icon)
        rtspServerCamera2.startStream()
        tvUrl.text = rtspServerCamera2.streamClient.getEndPointConnection()
        ScreenOrientation.lockScreen(this)
      } else {
        toast("Error preparing stream, This device cant do it")
      }
    }
    bRecord.setOnClickListener {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
        if (rtspServerCamera2.isRecording) {
          rtspServerCamera2.stopRecord()
          bRecord.setImageResource(R.drawable.record_icon)
          PathUtils.updateGallery(this, recordPath)
          if (!rtspServerCamera2.isStreaming) ScreenOrientation.unlockScreen(this)
        } else if (rtspServerCamera2.isStreaming || prepare()) {
          val folder = PathUtils.getRecordPath()
          if (!folder.exists()) folder.mkdir()
          val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
          recordPath = "${folder.absolutePath}/${sdf.format(Date())}.mp4"
          bRecord.setImageResource(R.drawable.pause_icon)
          rtspServerCamera2.startRecord(recordPath) { status ->
            if (status == RecordController.Status.RECORDING) {
              bRecord.setImageResource(R.drawable.stop_icon)
            }
          }
          ScreenOrientation.lockScreen(this)
        } else {
          toast("Error preparing stream, This device cant do it")
        }
      } else {
        toast("You need min JELLY_BEAN_MR2(API 18) for do it...")
      }
    }
    bSwitchCamera.setOnClickListener {
      try {
        currentCamera = if (currentCamera == 0) {
          1
        } else {
          0
        }
        rtspServerCamera2.switchCamera(rtspServerCamera2.camerasAvailable[currentCamera])
      } catch (e: CameraOpenException) {
        Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
      }
    }
  }

  private fun prepare(): Boolean {
    val prepared = rtspServerCamera2.prepareAudio() && rtspServerCamera2.prepareVideo()
    return prepared
  }

  override fun onNewBitrate(bitrate: Long) {

  }

  override fun onConnectionSuccess() {
    toast("Connected")
  }

  override fun onConnectionFailed(reason: String) {
    toast("Failed: $reason")
    rtspServerCamera2.stopStream()
    if (!rtspServerCamera2.isRecording) ScreenOrientation.unlockScreen(this)
    bStream.setImageResource(R.drawable.stream_icon)
  }

  override fun onConnectionStarted(url: String) {
  }

  override fun onDisconnect() {
    toast("Disconnected")
  }

  override fun onAuthError() {
    toast("Auth error")
    rtspServerCamera2.stopStream()
    if (!rtspServerCamera2.isRecording) ScreenOrientation.unlockScreen(this)
    bStream.setImageResource(R.drawable.stream_icon)
  }

  override fun onAuthSuccess() {
    toast("Auth success")
  }

  override fun onClientConnected(client: ServerClient) {
    toast("Client connected: ${client.clientAddress}")
  }

  override fun onClientDisconnected(client: ServerClient) {
    toast("Client disconnected: ${client.clientAddress}")
  }
}
