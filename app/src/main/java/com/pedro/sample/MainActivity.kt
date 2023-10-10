package com.pedro.sample

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

  private val PERMISSIONS = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    setContentView(R.layout.activity_main)
    val bCameraDemo = findViewById<Button>(R.id.b_camera_demo)
    bCameraDemo.setOnClickListener {
      if (!hasPermissions(this, *PERMISSIONS)) {
        ActivityCompat.requestPermissions(this, PERMISSIONS, 1)
      } else {
        startActivity(Intent(this, CameraDemoActivity::class.java))
      }
    }
  }

  private fun hasPermissions(context: Context?, vararg permissions: String): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null) {
      for (permission in permissions) {
        if (ActivityCompat.checkSelfPermission(context,
              permission) != PackageManager.PERMISSION_GRANTED) {
          return false
        }
      }
    }
    return true
  }
}