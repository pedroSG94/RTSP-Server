/*
 * Copyright (C) 2024 pedroSG94.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pedro.sample

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.scale
import com.pedro.encoder.input.sources.audio.MicrophoneSource
import com.pedro.encoder.input.sources.video.BitmapSource
import com.pedro.encoder.input.sources.video.BufferVideoSource
import com.pedro.encoder.input.sources.video.Camera1Source
import com.pedro.encoder.input.sources.video.Camera2Source
import com.pedro.extrasources.CameraUvcSource
import com.pedro.extrasources.CameraXSource
import com.pedro.sample.utils.FilterMenu
import com.pedro.sample.utils.fitAppPadding
import com.pedro.sample.utils.toast
import com.pedro.sample.utils.updateMenuColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


/**
 * Created by pedro on 22/3/22.
 */
class RotationActivity : AppCompatActivity(), OnTouchListener {

  private val cameraFragment = CameraFragment.getInstance()
  private val filterMenu: FilterMenu by lazy { FilterMenu(this) }
  private var currentVideoSource: MenuItem? = null
  private var currentAudioSource: MenuItem? = null
  private var currentOrientation: MenuItem? = null
  private var currentFilter: MenuItem? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.rotation_activity)
    fitAppPadding()
    supportFragmentManager.beginTransaction().add(R.id.container, cameraFragment).commit()
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.rotation_menu, menu)
    val defaultVideoSource = menu.findItem(R.id.video_source_camera2)
    val defaultAudioSource = menu.findItem(R.id.audio_source_microphone)
    val defaultOrientation = menu.findItem(R.id.orientation_horizontal)
    val defaultFilter = menu.findItem(R.id.no_filter)
    currentVideoSource = defaultVideoSource.updateMenuColor(this, currentVideoSource)
    currentAudioSource = defaultAudioSource.updateMenuColor(this, currentAudioSource)
    currentOrientation = defaultOrientation.updateMenuColor(this, currentOrientation)
    currentFilter = defaultFilter.updateMenuColor(this, currentFilter)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    try {
      when (item.itemId) {
        R.id.video_source_camera1 -> {
          currentVideoSource = item.updateMenuColor(this, currentVideoSource)
          cameraFragment.rtspServerStream.changeVideoSource(Camera1Source(applicationContext))
        }
        R.id.video_source_camera2 -> {
          currentVideoSource = item.updateMenuColor(this, currentVideoSource)
          cameraFragment.rtspServerStream.changeVideoSource(Camera2Source(applicationContext))
        }
        R.id.video_source_camerax -> {
          currentVideoSource = item.updateMenuColor(this, currentVideoSource)
          cameraFragment.rtspServerStream.changeVideoSource(CameraXSource(applicationContext))
        }
        R.id.video_source_camera_uvc -> {
          currentVideoSource = item.updateMenuColor(this, currentVideoSource)
          cameraFragment.rtspServerStream.changeVideoSource(CameraUvcSource())
        }
        R.id.video_source_bitmap -> {
          currentVideoSource = item.updateMenuColor(this, currentVideoSource)
          val bitmap = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
          cameraFragment.rtspServerStream.changeVideoSource(BitmapSource(bitmap))
        }
        R.id.video_source_buffer -> {
          currentVideoSource = item.updateMenuColor(this, currentVideoSource)
          val bitmap = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
          val data = bitmapToRgba(bitmap.scale(cameraFragment.width, cameraFragment.height))
          val source = BufferVideoSource(BufferVideoSource.Format.ARGB, cameraFragment.vBitrate)
          cameraFragment.rtspServerStream.changeVideoSource(source)
          CoroutineScope(Dispatchers.IO).launch {
            while (cameraFragment.rtspServerStream.videoSource is BufferVideoSource) {
              source.setBuffer(data.clone())
              delay(1000 / 30)
            }
          }
        }
        R.id.audio_source_microphone -> {
          currentAudioSource = item.updateMenuColor(this, currentAudioSource)
          cameraFragment.rtspServerStream.changeAudioSource(MicrophoneSource())
        }
        R.id.orientation_horizontal -> {
          currentOrientation = item.updateMenuColor(this, currentOrientation)
          cameraFragment.setOrientationMode(false)
        }
        R.id.orientation_vertical -> {
          currentOrientation = item.updateMenuColor(this, currentOrientation)
          cameraFragment.setOrientationMode(true)
        }
        else -> {
          val result = filterMenu.onOptionsItemSelected(item, cameraFragment.rtspServerStream.getGlInterface())
          if (result) currentFilter = item.updateMenuColor(this, currentFilter)
          return result
        }
      }
    } catch (e: IllegalArgumentException) {
      toast("Change source error: ${e.message}")
    }
    return super.onOptionsItemSelected(item)
  }

  private fun bitmapToRgba(bitmap: Bitmap): IntArray {
    require(bitmap.config == Bitmap.Config.ARGB_8888) { "Bitmap must be in ARGB_8888 format" }
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    return pixels
  }

  @SuppressLint("ClickableViewAccessibility")
  override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
    if (filterMenu.spriteGestureController.spriteTouched(view, motionEvent)) {
      filterMenu.spriteGestureController.moveSprite(view, motionEvent)
      filterMenu.spriteGestureController.scaleSprite(motionEvent)
      return true
    }
    return false
  }
}