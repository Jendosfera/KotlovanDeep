package com.example.kotlovan.theme

import android.content.Context
import com.example.kotlovan.data.ThemeProfile
import kotlinx.serialization.json.Json

class ThemeRepository(private val context: Context) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    private val prefs = context.getSharedPreferences("kotlovan_theme", Context.MODE_PRIVATE)

    fun load(): ThemeProfile {
        val raw = prefs.getString("profile", null) ?: return ThemeProfile()
        return try {
            json.decodeFromString(ThemeProfile.serializer(), raw)
        } catch (_: Exception) {
            ThemeProfile()
        }
    }

    fun save(profile: ThemeProfile) {
        prefs.edit()
            .putString("profile", json.encodeToString(ThemeProfile.serializer(), profile))
            .apply()
    }

    fun reset() {
        prefs.edit().remove("profile").apply()
    }
}