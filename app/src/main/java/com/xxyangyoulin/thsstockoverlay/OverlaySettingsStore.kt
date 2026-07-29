package com.xxyangyoulin.thsstockoverlay

import android.content.Context
import android.graphics.Color

data class OverlaySettings(
    val scalePercent: Int = 100,
    val edgeMarginDp: Int = 0,
    val fontSizeSp: Int = 10,
    val textColor: Int = Color.WHITE,
    val backgroundColor: Int = 0xFF212121.toInt(),
    val opacityPercent: Int = 68
)

class OverlaySettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("overlay_settings", Context.MODE_PRIVATE)

    fun isOverlayEnabled(): Boolean = preferences.getBoolean("overlay_enabled", true)

    fun setOverlayEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("overlay_enabled", enabled).apply()
    }

    fun get(): OverlaySettings = OverlaySettings(
        scalePercent = preferences.getInt("scale_percent", 100),
        edgeMarginDp = preferences.getInt("edge_margin_dp", 0),
        fontSizeSp = preferences.getInt("font_size_sp", 10),
        textColor = preferences.getInt("text_color", Color.WHITE),
        backgroundColor = preferences.getInt("background_color", 0xFF212121.toInt()),
        opacityPercent = preferences.getInt("opacity_percent", 68)
    )

    fun save(settings: OverlaySettings) {
        preferences.edit()
            .putInt("scale_percent", settings.scalePercent)
            .putInt("edge_margin_dp", settings.edgeMarginDp)
            .putInt("font_size_sp", settings.fontSizeSp)
            .putInt("text_color", settings.textColor)
            .putInt("background_color", settings.backgroundColor)
            .putInt("opacity_percent", settings.opacityPercent)
            .apply()
    }
}
