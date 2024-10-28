package com.pedro.rtspserver.util

import android.media.MediaCodec
import com.pedro.common.frame.MediaFrame
import com.pedro.common.isKeyframe

/**
 * Created by pedro on 28/10/24.
 */

fun MediaCodec.BufferInfo.toMediaFrameInfo(startTs: Long) = MediaFrame.Info(offset, size, presentationTimeUs - startTs, isKeyframe())