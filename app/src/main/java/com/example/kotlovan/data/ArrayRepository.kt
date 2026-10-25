package com.example.kotlovan.data

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ArrayRepository(private val context: Context) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    private val prefs = context.getSharedPreferences("kotlovan_prefs", Context.MODE_PRIVATE)
    private val imagesDir = File(context.filesDir, "cell_images").apply { mkdirs() }

    // ─────────── Картинки ───────────

    /** Копирует содержимое URI в filesDir/cell_images/<unique>.<ext>, возвращает имя файла. */
    fun importImage(uri: Uri): String? {
        return try {
            val ext = context.contentResolver.getType(uri)?.substringAfterLast('/') ?: "jpg"
            val name = "img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$ext"
            val out = File(imagesDir, name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
            if (out.exists() && out.length() > 0) name else null
        } catch (e: Exception) { Log.e("Kotlovan", "importImage", e); null }
    }

    fun imageFile(name: String): File = File(imagesDir, name)

    fun getImageSize(fileName: String): Pair<Int, Int>? {
        val f = File(imagesDir, fileName)
        if (!f.exists()) return null
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, opts)
            if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth to opts.outHeight else null
        } catch (_: Exception) { null }
    }

    fun getDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) c.getString(idx)?.substringBeforeLast('.')
                    else uri.lastPathSegment?.substringBeforeLast('.')
                } else null
            }
        } catch (_: Exception) { uri.lastPathSegment?.substringBeforeLast('.') }
    }

    fun takePersistablePermission(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {}
    }

    fun hasPersistablePermission(uri: Uri): Boolean = try {
        context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
    } catch (_: Exception) { false }

    // ─────────── ZIP сохранение / загрузка ───────────

    fun saveAsZip(config: ArrayConfig, uri: Uri): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                ZipOutputStream(out).use { zip ->
                    zip.setLevel(0)   // STORE — JPG уже сжаты, повторное сжатие бессмысленно
                    zip.putNextEntry(ZipEntry("array.json"))
                    zip.write(json.encodeToString(ArrayConfig.serializer(), config).toByteArray())
                    zip.closeEntry()

                    val used = LinkedHashSet<String>()
                    fun collect(sub: SubArray) {
                        for ((_, cell) in sub.cells) cell.imageFile?.let { used += it }
                    }
                    collect(config.main); collect(config.aux)

                    for (name in used) {
                        val f = File(imagesDir, name)
                        if (!f.exists()) continue
                        zip.putNextEntry(ZipEntry("images/$name"))
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
            true
        } catch (e: Exception) { Log.e("Kotlovan", "saveAsZip", e); false }
    }

    fun loadFromZip(uri: Uri): ArrayConfig? {
        return try {
            var config: ArrayConfig? = null
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        when {
                            entry.name == "array.json" -> {
                                val text = zip.readBytes().toString(Charsets.UTF_8)
                                config = json.decodeFromString(ArrayConfig.serializer(), text)
                            }
                            entry.name.startsWith("images/") -> {
                                val name = entry.name.removePrefix("images/")
                                val dst = File(imagesDir, name)
                                if (!dst.exists()) {
                                    dst.outputStream().use { zip.copyTo(it) }
                                }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
            config
        } catch (e: Exception) { Log.e("Kotlovan", "loadFromZip", e); null }
    }

    // ─────────── JSON (устаревший формат — для совместимости) ───────────

    fun loadFromUri(uri: Uri): ArrayConfig? {
        return try {
            val text = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.use { it.readText() } ?: return null
            json.decodeFromString(ArrayConfig.serializer(), text)
        } catch (e: Exception) { Log.e("Kotlovan", "loadFromUri", e); null }
    }

    // ─────────── Автосохранение ───────────

    fun autosave(config: ArrayConfig) {
        prefs.edit().putString("autosave", json.encodeToString(ArrayConfig.serializer(), config)).apply()
    }

    fun loadAutosave(): ArrayConfig? {
        val s = prefs.getString("autosave", null) ?: return null
        return try { json.decodeFromString(ArrayConfig.serializer(), s) } catch (_: Exception) { null }
    }
}