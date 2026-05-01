package com.ezhart.clearframe.data

import android.content.Context
import android.util.Log
import com.ezhart.clearframe.model.Photo
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "PhotoRepository"

interface PhotoRepository {
    suspend fun getPhotos(): List<Photo>
}

class InternalStoragePhotoRepository(private val context: Context) : PhotoRepository {

    override suspend fun getPhotos(): List<Photo> = withContext(Dispatchers.IO) {
        val storagePath = context.filesDir.path
        val imagePaths: List<String> = context.fileList()
            .filter { f -> f.endsWith("jpg", true) || f.endsWith("mp4", true) || f.endsWith("mov", true) }
            .sortedBy { f -> f }
            .map { f -> "$storagePath/$f" }

        val photos = mutableListOf<Photo>()

        for (imagePath in imagePaths) {
            Log.d(TAG, "Getting hash of $imagePath")
            val digest = File(imagePath).toHash("SHA-1")
            photos.add(Photo(imagePath, digest))
        }

        photos
    }

    private fun File.toHash(algorithm: String): String {
        val md = MessageDigest.getInstance(algorithm)
        inputStream().use { stream ->
            val buffer = ByteArray(1024)
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                md.update(buffer, 0, bytesRead)
            }
        }
        val digest = md.digest()
        return digest.joinToString("") { "%02x".format(it) }
    }
}