package com.pedro.sample

import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.SurfaceHolder
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.encoder.input.decoder.AudioDecoderInterface
import com.pedro.encoder.input.decoder.VideoDecoderInterface
import com.pedro.encoder.utils.gl.AspectRatioMode
import com.pedro.library.view.LightOpenGlView
import com.pedro.rtspserver.RtspServerFromFile
import com.pedro.sample.utils.PathUtils
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileDemoActivity : AppCompatActivity(), ConnectChecker, View.OnClickListener,
  VideoDecoderInterface,
  AudioDecoderInterface,
    SurfaceHolder.Callback {

  private lateinit var rtspServerFromFile: RtspServerFromFile
  private lateinit var button: Button
  private lateinit var bRecord: Button

  private lateinit var surfaceView: LightOpenGlView
  private lateinit var tvUrl: TextView

  private var currentDateAndTime = ""
  private lateinit var folder: File

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    setContentView(R.layout.activity_file_demo)
    val storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
    folder = File(storageDir.absolutePath + "/RootEncoder")
    tvUrl = findViewById(R.id.tv_url)
    button = findViewById(R.id.b_start_stop)
    button.setOnClickListener(this)
    bRecord = findViewById(R.id.b_record)
    bRecord.setOnClickListener(this)

    surfaceView = findViewById(R.id.surfaceView)
    surfaceView.setAspectRatioMode(AspectRatioMode.Adjust)
    rtspServerFromFile = RtspServerFromFile(surfaceView, this, 1935, this, this)
    rtspServerFromFile.setVideoCodec(VideoCodec.H264)
    rtspServerFromFile.setAudioCodec(AudioCodec.G711)
    rtspServerFromFile.setFpsListener {
      Log.d("FpsListener", "FPS = $it" )
    }
//    rtspServerCamera1.setAuthorization("admin", "admin")

    surfaceView.holder.addCallback(this)
  }

  override fun onNewBitrate(bitrate: Long) {

  }

  override fun onConnectionSuccess() {
    runOnUiThread {
      Toast.makeText(this@FileDemoActivity, "Connection success", Toast.LENGTH_SHORT).show()
    }
  }

  override fun onConnectionFailed(reason: String) {
    runOnUiThread {
      Toast.makeText(this@FileDemoActivity, "Connection failed. $reason", Toast.LENGTH_SHORT)
          .show()
      rtspServerFromFile.stopStream()
      button.setText(R.string.start_button)
    }
  }

  override fun onConnectionStarted(rtspUrl: String) {
  }

  override fun onDisconnect() {
    runOnUiThread {
      Toast.makeText(this@FileDemoActivity, "Disconnected", Toast.LENGTH_SHORT).show()
    }
  }

  override fun onAuthError() {
    runOnUiThread {
      Toast.makeText(this@FileDemoActivity, "Auth error", Toast.LENGTH_SHORT).show()
      rtspServerFromFile.stopStream()
      button.setText(R.string.start_button)
      tvUrl.text = ""
    }
  }

  override fun onAuthSuccess() {
    runOnUiThread {
      Toast.makeText(this@FileDemoActivity, "Auth success", Toast.LENGTH_SHORT).show()
    }
  }
  @Throws(IOException::class)
  private fun prepare(): Boolean {
    var result: Boolean = rtspServerFromFile.prepareVideo("/sdcard/temp/722343607.320626.mp4")
    result = result or rtspServerFromFile.prepareAudio("/sdcard/temp/722343607.320626.mp4")
    return result
  }

  override fun onClick(view: View) {
    when (view.id) {
      R.id.b_start_stop -> {

        if (!rtspServerFromFile.isStreaming()) {
          try {
            if (!rtspServerFromFile.isRecording()) {
              if (prepare()) {
                button.setText(R.string.stop_button)
                rtspServerFromFile.startStream()
                rtspServerFromFile.setLoopMode(true)
                tvUrl.text = rtspServerFromFile.getEndPointConnection()

               /* seekBar.setMax(
                  Math.max(
                    rtspServerCamera1.getVideoDuration().toInt(),
                    rtspServerCamera1.getAudioDuration().toInt()
                  )
                )
                updateProgress()*/

              } else {
                button.setText(R.string.start_button)
                rtspServerFromFile.stopStream()
                /*This error could be 2 things.
                 Your device cant decode or encode this file or
                 the file is not supported for the library.
                The file need has h264 video codec and acc audio codec*/Toast.makeText(
                  this,
                  "Error: unsupported file",
                  Toast.LENGTH_SHORT
                ).show()
              }
            } else {
              button.setText(R.string.stop_button)
              rtspServerFromFile.startStream()
            }
          } catch (e: IOException) {
            //Normally this error is for file not found or read permissions
            Toast.makeText(this, "Error: file not found", Toast.LENGTH_SHORT).show()
          }
        } else {
          button.setText(R.string.start_button)
          rtspServerFromFile.stopStream()
        }



       /* if (!rtspServerCamera1.isStreaming) {

          if (rtspServerCamera1.isRecording || rtspServerCamera1.prepareAudio() && rtspServerCamera1.prepareVideo()) {
            button.setText(R.string.stop_button)
            rtspServerCamera1.startStream()
            tvUrl.text = rtspServerCamera1.getEndPointConnection()
          } else {
            Toast.makeText(this, "Error preparing stream, This device cant do it", Toast.LENGTH_SHORT)
              .show()
          }
        } else {
          button.setText(R.string.start_button)
          rtspServerCamera1.stopStream()
          tvUrl.text = ""
        }
        */




      }


      R.id.b_record -> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
          if (!rtspServerFromFile.isRecording) {
            try {
              if (!folder.exists()) {
                folder.mkdir()
              }
              val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
              currentDateAndTime = sdf.format(Date())
              if (!rtspServerFromFile.isStreaming) {
                /*
                if (rtspServerCamera1.prepareAudio() && rtspServerCamera1.prepareVideo()) {
                  rtspServerCamera1.startRecord(folder.absolutePath + "/" + currentDateAndTime + ".mp4")
                  bRecord.setText(R.string.stop_record)
                  Toast.makeText(this, "Recording... ", Toast.LENGTH_SHORT).show()
                } else {
                  Toast.makeText(
                    this, "Error preparing stream, This device cant do it",
                    Toast.LENGTH_SHORT
                  ).show()
                }
                */
                if (prepare()) {
                  rtspServerFromFile.startRecord(
                    folder.absolutePath + "/" + currentDateAndTime + ".mp4"
                  )
                  /*
                  seekBar.setMax(
                    Math.max(
                      rtspServerCamera1.getVideoDuration().toInt(),
                      rtspServerCamera1.getAudioDuration().toInt()
                    )
                  )
                  updateProgress()*/

                  bRecord.setText(R.string.stop_record)
                  Toast.makeText(this, "Recording... ", Toast.LENGTH_SHORT).show()
                } else {
                  Toast.makeText(
                    this, "Error preparing stream, This device cant do it",
                    Toast.LENGTH_SHORT
                  ).show()
                }
              } else {
                rtspServerFromFile.startRecord(folder.absolutePath + "/" + currentDateAndTime + ".mp4")
                bRecord.setText(R.string.stop_record)
                Toast.makeText(this, "Recording... ", Toast.LENGTH_SHORT).show()
              }
            } catch (e: IOException) {
              rtspServerFromFile.stopRecord()
              bRecord.setText(R.string.start_record)
              Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
            }
          } else {
            rtspServerFromFile.stopRecord()
            bRecord.setText(R.string.start_record)
            Toast.makeText(
              this, "file " + currentDateAndTime + ".mp4 saved in " + folder.absolutePath,
              Toast.LENGTH_SHORT
            ).show()
          }
        } else {
          Toast.makeText(this, "You need min JELLY_BEAN_MR2(API 18) for do it...", Toast.LENGTH_SHORT).show()
        }
      }
      else -> {
      }
    }
  }

  override fun surfaceCreated(surfaceHolder: SurfaceHolder) {
  }

  override fun surfaceChanged(surfaceHolder: SurfaceHolder, i: Int, i1: Int, i2: Int) {
    //rtspServerCamera1.startPreview()
  }

  override fun surfaceDestroyed(surfaceHolder: SurfaceHolder) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      if (rtspServerFromFile.isRecording) {
        rtspServerFromFile.stopRecord()
        bRecord.setText(R.string.start_record)
        Toast.makeText(this, "file " + currentDateAndTime + ".mp4 saved in " + folder.absolutePath, Toast.LENGTH_SHORT).show()
        currentDateAndTime = ""
      }
    }
    if (rtspServerFromFile.isStreaming) {
      rtspServerFromFile.stopStream()
      button.text = resources.getString(R.string.start_button)
      tvUrl.text = ""
    }
    //rtspServerCamera1.stopPreview()
  }

  override fun onVideoDecoderFinished() {
    runOnUiThread {
      if (rtspServerFromFile.isRecording()) {
        rtspServerFromFile.stopRecord()
        PathUtils.updateGallery(
          applicationContext,
          folder.absolutePath + "/" + currentDateAndTime + ".mp4"
        )
        bRecord.setText(R.string.start_record)
        Toast.makeText(
          this,
          "file " + currentDateAndTime + ".mp4 saved in " + folder.absolutePath,
          Toast.LENGTH_SHORT
        ).show()
        currentDateAndTime = ""
      }
      if (rtspServerFromFile.isStreaming()) {
        button.setText(R.string.start_button)
        Toast.makeText(this, "Video stream finished", Toast.LENGTH_SHORT)
          .show()
        rtspServerFromFile.stopStream()
      }
    }
  }

  override fun onAudioDecoderFinished() {
    //TODO("Not yet implemented")
  }
}
