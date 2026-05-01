package com.ezhart.clearframe.model

import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface

data class Photo(
    val filename: String,
    val digest: String
) {
    val isVideo: Boolean
        get() = filename.endsWith("mp4", true) || filename.endsWith("mov", true)

    val isPortrait: Boolean
        get() {
            if (isVideo) return false
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(filename, options)
            val width = options.outWidth
            val height = options.outHeight

            val exif = ExifInterface(filename)
            val rotation = when (exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90,
                ExifInterface.ORIENTATION_ROTATE_270,
                ExifInterface.ORIENTATION_TRANSVERSE,
                ExifInterface.ORIENTATION_TRANSPOSE -> true
                else -> false
            }

            // if rotated 90 or 270, swap width and height
            return if (rotation) width > height else height > width
        }
}