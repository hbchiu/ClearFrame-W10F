package com.ezhart.clearframe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

private const val TAG = "ConfigReceiver"

class ConfigReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.ezhart.clearframe.SET_CONFIG") return

        val prefs = context.getSharedPreferences("clearframe_config", Context.MODE_PRIVATE)
        val editor = prefs.edit()

        intent.getStringExtra("source")?.let { editor.putString("source", it) }
        intent.getStringExtra("immich_url")?.let { editor.putString("immich_url", it) }
        intent.getStringExtra("immich_api_key")?.let { editor.putString("immich_api_key", it) }
        intent.getStringExtra("immich_albums")?.let { editor.putString("immich_albums", it) }

        editor.apply()
        Log.d(TAG, "Config updated via broadcast")
    }
}