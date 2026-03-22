package com.pedro.sample.utils

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import java.io.File

/**
 * Created by pedro on 21/06/17.
 * Get absolute path from onActivityResult
 * https://stackoverflow.com/questions/33295300/how-to-get-absolute-path-in-android-for-file
 */
object PathUtils {
  @JvmStatic
  fun getRecordPath(): File {
    val storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
    return File(storageDir.absolutePath + "/RootEncoder")
  }

  @JvmStatic
  fun updateGallery(context: Context, path: String) {
    MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
    context.toast("Video saved at: $path")
  }
}