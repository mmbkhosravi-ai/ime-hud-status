package com.imehud.status

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Screen State Receiver
 * دریافت رویدادهای ACTION_SCREEN_ON و ACTION_SCREEN_OFF
 * ذخیره در فایل /sdcard/ime_data_state.json
 */
class ScreenStateReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "IMEHUD-Screen"
        const val STATE_FILE = "/sdcard/ime_data_state.json"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val screenState = when (action) {
            Intent.ACTION_SCREEN_ON -> "on"
            Intent.ACTION_SCREEN_OFF -> "off"
            Intent.ACTION_USER_PRESENT -> "on"
            else -> return
        }

        try {
            val json = JSONObject().apply {
                put("screen", screenState)
                put("ts", System.currentTimeMillis())
                put("action", action)
            }

            val file = File(STATE_FILE)
            file.writeText(json.toString())

            Log.d(TAG, "screen state: $screenState (action=$action)")
        } catch (e: Exception) {
            Log.e(TAG, "write failed", e)
        }
    }
}
