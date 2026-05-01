package com.ezhart.clearframe.data

import android.content.Context
import java.io.File

enum class PhotoSource {
    IMMICH,
    GOOGLE_DRIVE
}

enum class DisplayMode {
    ADAPTIVE, FILL, MATCH_ORIENTATION
}

data class SourceConfig(
    val source: PhotoSource,
    val immichUrl: String = "",
    val immichApiKey: String = "",
    val immichAlbumId: String = "",
    val displayMode: DisplayMode = DisplayMode.ADAPTIVE,
    val motionSensorEnabled: Boolean = true,
    val sleepHourStart: Int = 23,
    val wakeHourStart: Int = 6,
    val sleepTimeoutMinutes: Int = 15
)

fun loadConfig(context: Context): SourceConfig {
    val configFile = File(context.getExternalFilesDir(null), "clearframe_config.txt")

    if (!configFile.exists()) {
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

    val displayMode = when (props["display_mode"]?.uppercase()) {
        "FILL" -> DisplayMode.FILL
        "MATCH" -> DisplayMode.MATCH_ORIENTATION
        else -> DisplayMode.ADAPTIVE
    }

    val motionSensorEnabled = props["motion_sensor"]?.uppercase() != "OFF"
    val sleepHourStart = props["sleep_hour"]?.toIntOrNull() ?: 23
    val wakeHourStart = props["wake_hour"]?.toIntOrNull() ?: 6
    val sleepTimeoutMinutes = props["sleep_timeout_minutes"]?.toIntOrNull() ?: 15

    return SourceConfig(
        source = source,
        immichUrl = props["immich_url"] ?: "",
        immichApiKey = props["immich_api_key"] ?: "",
        immichAlbumId = props["immich_album"] ?: "",
        displayMode = displayMode,
        motionSensorEnabled = motionSensorEnabled,
        sleepHourStart = sleepHourStart,
        wakeHourStart = wakeHourStart,
        sleepTimeoutMinutes = sleepTimeoutMinutes
    )
}