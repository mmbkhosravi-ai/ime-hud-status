package com.imehud.status

import android.content.Context

data class OverlaySettings(
    val position: String = "center",   // left / center / right
    val offsetX: Int = 0,               // -300..+300 dp
    val textSizeSp: Float = 22f,        // 12..42
    val showPrefix: Boolean = true,
    val showBackground: Boolean = true,
)

object OverlayPrefs {
    private const val PREFS = "overlay_prefs"
    private const val KEY_POS = "position"
    private const val KEY_OFFSET = "offset_x"
    private const val KEY_SIZE = "text_size"
    private const val KEY_PREFIX = "show_prefix"
    private const val KEY_BG = "show_bg"

    fun load(ctx: Context): OverlaySettings {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return OverlaySettings(
            position = p.getString(KEY_POS, "center") ?: "center",
            offsetX = p.getInt(KEY_OFFSET, 0),
            textSizeSp = p.getFloat(KEY_SIZE, 22f),
            showPrefix = p.getBoolean(KEY_PREFIX, true),
            showBackground = p.getBoolean(KEY_BG, true),
        )
    }

    fun save(ctx: Context, s: OverlaySettings) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putString(KEY_POS, s.position)
            putInt(KEY_OFFSET, s.offsetX)
            putFloat(KEY_SIZE, s.textSizeSp)
            putBoolean(KEY_PREFIX, s.showPrefix)
            putBoolean(KEY_BG, s.showBackground)
            apply()
        }
    }
}
