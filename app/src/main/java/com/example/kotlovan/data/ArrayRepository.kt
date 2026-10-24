package com.example.kotlovan.data

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.serialization.json.Json

class ArrayRepository(private val context: Context) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    private val prefs = context.getSharedPreferences("kotlovan_prefs", Context.MODE_PRIVATE)

    fun takePersistablePermission(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) { Log.w("Kotlovan", "takePersistablePermission: $uri", e) }
    }

    fun hasPersistablePermission(uri: Uri): Boolean = try {
        context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
    } catch (_: Exception) { false }

    fun getImageSize(uri: Uri): Pair<Int, Int>? = try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth to opts.outHeight else null
    } catch (e: Exception) { Log.e("Kotlovan", "getImageSize: $uri", e); null }

    /** Имя файла изображения без расширения — для автозаголовка ячейки. */
    fun getDisplayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) c.getString(idx)?.substringBeforeLast('.')
                else uri.lastPathSegment?.substringBeforeLast('.')
            } else null
        }
    } catch (_: Exception) { uri.lastPathSegment?.substringBeforeLast('.') }

    fun saveToUri(config: ArrayConfig, uri: Uri): Boolean = try {
        val text = json.encodeToString(ArrayConfig.serializer(), config)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
        true
    } catch (e: Exception) { Log.e("Kotlovan", "saveToUri", e); false }

	fun loadFromUri(uri: Uri): ArrayConfig? {
		return try {
			val text = context.contentResolver.openInputStream(uri)
				?.bufferedReader()?.use { it.readText() } ?: return null
			json.decodeFromString(ArrayConfig.serializer(), text)
		} catch (e: Exception) {
			Log.e("Kotlovan", "loadFromUri", e)
			null
		}
	}

    fun autosave(config: ArrayConfig) {
        prefs.edit().putString("autosave", json.encodeToString(ArrayConfig.serializer(), config)).apply()
    }

    fun loadAutosave(): ArrayConfig? {
        val s = prefs.getString("autosave", null) ?: return null
        return try { json.decodeFromString(ArrayConfig.serializer(), s) } catch (_: Exception) { null }
    }
}