package com.streye.rtspserver

import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.support.v7.app.AppCompatActivity
import android.view.SurfaceHolder
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.pedro.encoder.input.video.CameraOpenException
import com.pedro.rtsp.utils.ConnectCheckerRtsp
import com.streye.rtspserver.rtsp.RtspServerCamera1
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity(), ConnectCheckerRtsp, View.OnClickListener,
    SurfaceHolder.Callback {

  private var rtspServerCamera1: RtspServerCamera1? = null
  private var button: Button? = null
  private var bRecord: Button? = null
  private var etUrl: EditText? = null

  private var currentDateAndTime = ""
  private val folder =
      File(Environment.getExternalStorageDirectory().absolutePath + "/rtmp-rtsp-stream-client-java")

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    setContentView(R.layout.activity_main)

    button = findViewById(R.id.b_start_stop)
    button!!.setOnClickListener(this)
    bRecord = findViewById(R.id.b_record)
    bRecord!!.setOnClickListener(this)
    switch_camera.setOnClickListener(this)
    etUrl = findViewById(R.id.et_rtp_url)
    etUrl!!.setHint(R.string.hint_rtsp)
    rtspServerCamera1 = RtspServerCamera1(surfaceView, this, 1935)
    surfaceView.holder.addCallback(this)
  }

  override fun onConnectionSuccessRtsp() {
    runOnUiThread {
      Toast.makeText(this@MainActivity, "Connection success", Toast.LENGTH_SHORT).show()
    }
  }

  override fun onConnectionFailedRtsp(reason: String) {
    runOnUiThread {
      Toast.makeText(this@MainActivity, "Connection failed. $reason", Toast.LENGTH_SHORT)
          .show()
      rtspServerCamera1!!.stopStream()
      button!!.setText(R.string.start_button)
    }
  }

  override fun onDisconnectRtsp() {
    runOnUiThread {
      Toast.makeText(this@MainActivity, "Disconnected", Toast.LENGTH_SHORT).show()
    }
  }

  override fun onAuthErrorRtsp() {
    runOnUiThread {
      Toast.makeText(this@MainActivity, "Auth error", Toast.LENGTH_SHORT).show()
      rtspServerCamera1!!.stopStream()
      button!!.setText(R.string.start_button)
    }
  }

  override fun onAuthSuccessRtsp() {
    runOnUiThread {
      Toast.makeText(this@MainActivity, "Auth success", Toast.LENGTH_SHORT).show()
    }
  }

  override fun onClick(view: View) {
    when (view.id) {
      R.id.b_start_stop -> if (!rtspServerCamera1!!.isStreaming) {
        if (rtspServerCamera1!!.isRecording || rtspServerCamera1!!.prepareAudio() && rtspServerCamera1!!.prepareVideo()) {
          button!!.setText(R.string.stop_button)
          rtspServerCamera1!!.startStream(etUrl!!.text.toString())
        } else {
          Toast.makeText(this, "Error preparing stream, This device cant do it", Toast.LENGTH_SHORT)
              .show()
        }
      } else {
        button!!.setText(R.string.start_button)
        rtspServerCamera1!!.stopStream()
      }
      R.id.switch_camera -> try {
        rtspServerCamera1!!.switchCamera()
      } catch (e: CameraOpenException) {
        Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
      }

      R.id.b_record -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
        if (!rtspServerCamera1!!.isRecording) {
          try {
            if (!folder.exists()) {
              folder.mkdir()
            }
            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            currentDateAndTime = sdf.format(Date())
            if (!rtspServerCamera1!!.isStreaming) {
              if (rtspServerCamera1!!.prepareAudio() && rtspServerCamera1!!.prepareVideo()) {
                rtspServerCamera1!!.startRecord(folder.absolutePath + "/" + currentDateAndTime + ".mp4")
                bRecord!!.setText(R.string.stop_record)
                Toast.makeText(this, "Recording... ", Toast.LENGTH_SHORT).show()
              } else {
                Toast.makeText(this, "Error preparing stream, This device cant do it",
                  Toast.LENGTH_SHORT).show()
              }
            } else {
              rtspServerCamera1!!.startRecord(
                folder.absolutePath + "/" + currentDateAndTime + ".mp4")
              bRecord!!.setText(R.string.stop_record)
              Toast.makeText(this, "Recording... ", Toast.LENGTH_SHORT).show()
            }
          } catch (e: IOException) {
            rtspServerCamera1!!.stopRecord()
            bRecord!!.setText(R.string.start_record)
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
          }
        } else {
          rtspServerCamera1!!.stopRecord()
          bRecord!!.setText(R.string.start_record)
          Toast.makeText(this,
            "file " + currentDateAndTime + ".mp4 saved in " + folder.getAbsolutePath(),
            Toast.LENGTH_SHORT).show()
        }
      } else {
        Toast.makeText(this, "You need min JELLY_BEAN_MR2(API 18) for do it...", Toast.LENGTH_SHORT)
            .show()
      }
      else -> {
      }
    }
  }

  override fun surfaceCreated(surfaceHolder: SurfaceHolder) {

  }

  override fun surfaceChanged(surfaceHolder: SurfaceHolder, i: Int, i1: Int, i2: Int) {
    rtspServerCamera1!!.startPreview()
  }

  override fun surfaceDestroyed(surfaceHolder: SurfaceHolder) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && rtspServerCamera1!!.isRecording) {
      rtspServerCamera1!!.stopRecord()
      bRecord!!.setText(R.string.start_record)
      Toast.makeText(this, "file " + currentDateAndTime + ".mp4 saved in " + folder.absolutePath,
        Toast.LENGTH_SHORT).show()
      currentDateAndTime = ""
    }
    if (rtspServerCamera1!!.isStreaming) {
      rtspServerCamera1!!.stopStream()
      button!!.text = resources.getString(R.string.start_button)
    }
    rtspServerCamera1!!.stopPreview()
  }
}