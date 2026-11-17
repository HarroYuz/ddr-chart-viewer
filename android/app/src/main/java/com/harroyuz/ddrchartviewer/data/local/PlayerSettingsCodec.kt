package com.harroyuz.ddrchartviewer.data.local

import com.harroyuz.ddrchartviewer.domain.PlayerSettings
import com.harroyuz.ddrchartviewer.domain.SpeedMode
import org.json.JSONObject

internal object PlayerSettingsCodec {
    fun decode(value: String?, legacySpeed: Float?): PlayerSettings {
        val fallback = PlayerSettings(speed = legacySpeed ?: 2f,
            speedMode = if (legacySpeed == null) SpeedMode.FHS else SpeedMode.HI_SPEED).normalized()
        if (value == null) return fallback
        return runCatching {
            val json = JSONObject(value)
            fallback.copy(
                speed = json.optDouble("speed", fallback.speed.toDouble()).toFloat(),
                speedMode = runCatching { SpeedMode.valueOf(json.optString("speedMode")) }.getOrDefault(fallback.speedMode),
                greenNumber = json.optInt("greenNumber", 500), fieldHeight = json.optInt("fieldHeight", 1000),
                constant = json.optBoolean("constant"), constantMs = json.optInt("constantMs", 1000),
                mirror = json.optBoolean("mirror"), reverse = json.optBoolean("reverse"), flip = json.optBoolean("flip"),
                rate = json.optDouble("rate", 1.0).toFloat(),
            ).normalized()
        }.getOrDefault(fallback)
    }
    fun encode(settings: PlayerSettings): String = with(settings.normalized()) {
        JSONObject().put("speed", speed).put("speedMode", speedMode.name).put("greenNumber", greenNumber)
            .put("fieldHeight", fieldHeight).put("constant", constant).put("constantMs", constantMs)
            .put("mirror", mirror).put("reverse", reverse).put("flip", flip).put("rate", rate).toString()
    }
}
