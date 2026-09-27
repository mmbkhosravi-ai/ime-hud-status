package com.imehud.status

import android.content.Context

data class OverlaySettings(
    val position: String = "center",       // left / center / right (فقط در حالت rotate)
    val offsetX: Int = 0,                  // -300..+300 dp
    val textSizeSp: Float = 22f,           // 12..42
    val showPrefix: Boolean = true,
    val showBackground: Boolean = true,
    val rotateSeconds: Int = 5,            // 2..60
    // ★ Ticker
    val mode: String = "rotate",           // rotate / ticker
    val tickerPosition: String = "bottom", // top / bottom
    val tickerHeightDp: Int = 44,          // 30..80
    val tickerSpeed: String = "medium"     // slow / medium / fast
)

object OverlayPrefs {
    private const val PREFS = "overlay_prefs"

    private const val KEY_POS = "position"
    private const val KEY_OFFSET = "offset_x"
    private const val KEY_SIZE = "text_size"
    private const val KEY_PREFIX = "show_prefix"
    private const val KEY_BG = "show_bg"
    private const val KEY_ROTATE = "rotate_sec"
    // Ticker
    private const val KEY_MODE = "mode"
    private const val KEY_T_POS = "ticker_position"
    private const val KEY_T_HEIGHT = "ticker_height"
    private const val KEY_T_SPEED = "ticker_speed"

    fun load(ctx: Context): OverlaySettings {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return OverlaySettings(
            position = p.getString(KEY_POS, "center") ?: "center",
            offsetX = p.getInt(KEY_OFFSET, 0),
            textSizeSp = p.getFloat(KEY_SIZE, 22f),
            showPrefix = p.getBoolean(KEY_PREFIX, true),
            showBackground = p.getBoolean(KEY_BG, true),
            rotateSeconds = p.getInt(KEY_ROTATE, 5),
            mode = p.getString(KEY_MODE, "rotate") ?: "rotate",
            tickerPosition = p.getString(KEY_T_POS, "bottom") ?: "bottom",
            tickerHeightDp = p.getInt(KEY_T_HEIGHT, 44),
            tickerSpeed = p.getString(KEY_T_SPEED, "medium") ?: "medium"
        )
    }

    fun save(ctx: Context, s: OverlaySettings) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putString(KEY_POS, s.position)
            putInt(KEY_OFFSET, s.offsetX)
            putFloat(KEY_SIZE, s.textSizeSp)
            putBoolean(KEY_PREFIX, s.showPrefix)
            putBoolean(KEY_BG, s.showBackground)
            putInt(KEY_ROTATE, s.rotateSeconds)
            putString(KEY_MODE, s.mode)
            putString(KEY_T_POS, s.tickerPosition)
            putInt(KEY_T_HEIGHT, s.tickerHeightDp)
            putString(KEY_T_SPEED, s.tickerSpeed)
            apply()
        }
    }
}
