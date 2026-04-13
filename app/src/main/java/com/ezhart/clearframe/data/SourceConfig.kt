package com.ezhart.clearframe.data

import android.content.Context
import java.io.File

enum class PhotoSource {
    IMMICH,
    GOOGLE_DRIVE
}

data class SourceConfig(
    val source: PhotoSource,
    val immichUrl: String = "",
    val immichApiKey: String = "",
    val immichAlbumId: String = ""
)

fun loadConfig(context: Context): SourceConfig {
    val configFile = File(context.getExternalFilesDir(null), "clearframe_config.txt")

    if (!configFile.exists()) {
        // Default to Immich if no config found
        return SourceConfig(source = PhotoSource.IMMICH)
    }

    val props = mutableMapOf<String, String>()

    configFile.readLines().forEach { line ->
        val parts = line.split("=", limit = 2)
        if (parts.size == 2) {
            props[parts[0].trim()] = parts[1].trim()
        }
    }

    val source = when (props["source"]?.uppercase()) {
        "GOOGLE_DRIVE" -> PhotoSource.GOOGLE_DRIVE
        else -> PhotoSource.IMMICH
    }

    return SourceConfig(
        source = source,
        immichUrl = props["immich_url"] ?: "",
        immichApiKey = props["immich_api_key"] ?: "",
        immichAlbumId = props["immich_album"] ?: ""
    )
}