package com.example.kotlovan.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class ArrayRepository(private val context: Context) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    private val imagesDir = File(context.filesDir, "cell_images").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("kotlovan_prefs", Context.MODE_PRIVATE)

    fun importImage(uri: Uri): String? = try {
        val ext = context.contentResolver.getType(uri)?.substringAfterLast('/') ?: "jpg"
        val name = "img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$ext"
        val out = File(imagesDir, name)
        context.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
        name
    } catch (e: Exception) {
        Log.e("Kotlovan", "importImage", e); null
    }

    fun imageFile(name: String): File = File(imagesDir, name)

    fun saveToUri(config: ArrayConfig, uri: Uri): Boolean = try {
        val text = json.encodeToString(ArrayConfig.serializer(), config)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
        true
    } catch (e: Exception) { Log.e("Kotlovan","saveToUri",e); false }

    fun loadFromUri(uri: Uri): ArrayConfig? = try {
        val text = context.contentResolver.openInputStream(uri)
            ?.bufferedReader()?.use { it.readText() } ?: return null
        json.decodeFromString(ArrayConfig.serializer(), text)
    } catch (e: Exception) { Log.e("Kotlovan","loadFromUri",e); null }

    fun autosave(config: ArrayConfig) {
        prefs.edit().putString("autosave", json.encodeToString(ArrayConfig.serializer(), config)).apply()
    }

    fun loadAutosave(): ArrayConfig? {
        val s = prefs.getString("autosave", null) ?: return null
        return try { json.decodeFromString(ArrayConfig.serializer(), s) } catch (e: Exception) { null }
    }
}