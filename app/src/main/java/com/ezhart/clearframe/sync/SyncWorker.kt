package com.ezhart.clearframe.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ezhart.clearframe.ClearFrameApplication
import com.ezhart.clearframe.data.PhotoSource
import com.ezhart.clearframe.data.loadConfig
import com.ezhart.clearframe.model.Photo
import kotlinx.serialization.Serializable
import okhttp3.ResponseBody
import org.greenrobot.eventbus.EventBus
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Streaming
import retrofit2.http.Url
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

private const val TAG = "SyncService"

interface RemotePhotoService {
    @GET("api/albums/{albumId}")
    suspend fun getAlbum(
        @Path("albumId") albumId: String,
        @Header("x-api-key") apiKey: String
    ): ImmichAlbum

    @GET
    @Streaming
    suspend fun download(
        @Url url: String,
        @Header("x-api-key") apiKey: String
    ): Response<ResponseBody>
}

data class ImmichAlbum(
    val assets: List<ImmichAsset>
)

@Serializable
data class ImmichAsset(
    val id: String,
    val originalFileName: String,
    val checksum: String
)

data class ReloadRequest(val reason: String)

class SyncWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        try {
            Log.d(TAG, "About to call syncImmich")
            syncImmich(
                baseUrl = "http://192.168.0.60:2283/",
                apiKey = "pqTIEp2ygqpw7caUN67wLHOLScwSR2K03wIQW5eGJrg",
                albumId = "ae013fd6-18cf-427e-9ef8-9f984ab89442"
            )

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "doWork failed: ${e.message}")
            Log.e(TAG, e.stackTraceToString())
        }

        return Result.failure()
    }

    private suspend fun syncImmich(baseUrl: String, apiKey: String, albumId: String) {

        val retrofit = Retrofit.Builder()
            .addConverterFactory(GsonConverterFactory.create())
            .baseUrl(baseUrl)
            .build()

        val photoService = retrofit.create(RemotePhotoService::class.java)

        val remoteAssets = photoService.getAlbum(albumId, apiKey).assets
        Log.d(TAG, "Found ${remoteAssets.size} remote assets")

        val localPhotos = getLocalPhotoList()

        val toDownload = remoteAssets // download everything, skip local check
        val toDelete = emptyList<Photo>() // skip deletion for now

        val photosDeleted = cleanupPhotos(toDelete)
        val photosDownloaded = downloadPhotos(photoService, apiKey, baseUrl, toDownload)

        if (photosDeleted || photosDownloaded) {
            var reason = "Photos changed "
            if (photosDeleted) reason += "(deletions) "
            if (photosDownloaded) reason += "(downloads) "
            EventBus.getDefault().post(ReloadRequest(reason))
        }
    }

    private fun getDownloadList(
        remoteAssets: List<ImmichAsset>,
        localPhotos: List<Photo>
    ): List<ImmichAsset> {
        return remoteAssets.filter { asset ->
            val exists = localPhotos.any { p -> p.digest == asset.checksum }
            if (exists) Log.d(TAG, "Asset ${asset.originalFileName} already exists locally; skipping.")
            !exists
        }
    }

    private fun getDeleteList(
        remoteAssets: List<ImmichAsset>,
        localPhotos: List<Photo>
    ): List<Photo> {
        return localPhotos.filter { local ->
            val stillExists = remoteAssets.any { a -> a.checksum == local.digest }
            if (!stillExists) Log.d(TAG, "Local photo ${local.filename} no longer in album; marking for deletion.")
            !stillExists
        }
    }

    private suspend fun getLocalPhotoList(): List<Photo> {
        Log.d(TAG, "Getting local photo list...")
        val photoRepository = (applicationContext as ClearFrameApplication).container.photoRepository
        return photoRepository.getPhotos()
    }

    private suspend fun downloadPhotos(
        photoService: RemotePhotoService,
        apiKey: String,
        baseUrl: String,
        assets: List<ImmichAsset>
    ): Boolean {
        var photosChanged = false
        for (asset in assets) {
            Log.d(TAG, "Downloading ${asset.originalFileName}")
            val url = "${baseUrl}api/assets/${asset.id}/original"
            Log.d(TAG, "Download URL: $url")
            val response = photoService.download(url, apiKey)
            Log.d(TAG, "Response code: ${response.code()}")
            val responseBody = response.body()
            Log.d(TAG, "Response body null: ${responseBody == null}")
            if (saveFile(responseBody, asset.originalFileName)) {
                photosChanged = true
            }
        }
        return photosChanged
    }

    private fun cleanupPhotos(photos: List<Photo>): Boolean {
        var photosChanged = false
        for (photo in photos) {
            Log.d(TAG, "Deleting ${photo.filename}")
            val deleted = File(photo.filename).delete()
            if (!deleted) Log.e(TAG, "Error deleting ${photo.filename}")
            else photosChanged = true
        }
        return photosChanged
    }

    private fun saveFile(body: ResponseBody?, filename: String): Boolean {
        if (body == null) return false
        var input: InputStream? = null
        try {
            input = body.byteStream()
            val path = "${applicationContext.filesDir.path}/$filename"
            Log.d(TAG, "Destination is $path")
            val fos = FileOutputStream(path)
            fos.use { output ->
                val buffer = ByteArray(4 * 1024)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
        } finally {
            input?.close()
        }
        return false
    }
}