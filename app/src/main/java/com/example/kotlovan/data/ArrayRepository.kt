package com.example.kotlovan.data

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ArrayRepository(private val context: Context) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    private val prefs = context.getSharedPreferences("kotlovan_prefs", Context.MODE_PRIVATE)
    private val imagesDir = File(context.filesDir, "cell_images").apply { mkdirs() }

    // ─────────── Изображения ячеек ───────────

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

    // ─────────── Фото комментариев ───────────

    fun newPhotoName(): String =
        "photo_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg"

    fun photoFile(name: String): File = File(imagesDir, name)

    fun photoUriForNew(name: String): Uri {
        val f = File(imagesDir, name)
        f.parentFile?.mkdirs()
        if (!f.exists()) f.createNewFile()
        return FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", f
        )
    }

    // ─────────── ZIP ───────────

    /** Сохраняет произвольный SubArray в ZIP (используется и для main, и для photoArchive). */
    fun saveSubArrayAsZip(sub: SubArray, uri: Uri): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                ZipOutputStream(out).use { zip ->
                    zip.setLevel(0)
                    zip.putNextEntry(ZipEntry("array.json"))
                    zip.write(json.encodeToString(SubArray.serializer(), sub).toByteArray())
                    zip.closeEntry()

                    val used = LinkedHashSet<String>()
                    for ((_, cell) in sub.cells) {
                        cell.imageFile?.let { used += it }
                        cell.nestedImageFile?.let { used += it }
                        for (pin in cell.comments) for (ph in pin.photos) used += ph.file
                    }
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
        } catch (e: Exception) { Log.e("Kotlovan", "saveSubArrayAsZip", e); false }
    }

    fun loadSubArrayFromZip(uri: Uri): SubArray? {
        return try {
            var sub: SubArray? = null
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        when {
                            entry.name == "array.json" -> {
                                val text = zip.readBytes().toString(Charsets.UTF_8)
                                sub = json.decodeFromString(SubArray.serializer(), text)
                            }
                            entry.name.startsWith("images/") -> {
                                val name = entry.name.removePrefix("images/")
                                val dst = File(imagesDir, name)
                                if (!dst.exists()) dst.outputStream().use { zip.copyTo(it) }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
            sub
        } catch (e: Exception) { Log.e("Kotlovan", "loadSubArrayFromZip", e); null }
    }

    // ─────────── Основное сохранение/загрузка (main+aux+photoArchive) ───────────

    fun saveAsZip(config: ArrayConfig, uri: Uri): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                ZipOutputStream(out).use { zip ->
                    zip.setLevel(0)
                    zip.putNextEntry(ZipEntry("array.json"))
                    zip.write(json.encodeToString(ArrayConfig.serializer(), config).toByteArray())
                    zip.closeEntry()

                    val used = LinkedHashSet<String>()
                    fun collect(sub: SubArray) {
                        for ((_, cell) in sub.cells) {
                            cell.imageFile?.let { used += it }
                            cell.nestedImageFile?.let { used += it }
                            for (pin in cell.comments) for (ph in pin.photos) used += ph.file
                        }
                    }
                    collect(config.main); collect(config.aux); collect(config.photoArchive)

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
                                if (!dst.exists()) dst.outputStream().use { zip.copyTo(it) }
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

    fun loadFromUri(uri: Uri): ArrayConfig? {
        return try {
            val text = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.use { it.readText() } ?: return null
            json.decodeFromString(ArrayConfig.serializer(), text)
        } catch (e: Exception) { Log.e("Kotlovan", "loadFromUri", e); null }
    }

    // ─────────── Фотоотчёт ───────────

    /**
     * @param archive фотоархив (SubArray)
     * @param selectedPins набор id пинов, которые нужно включить
     * @param from минимальная дата (мс) — фильтр по createdAt
     * @param to   максимальная дата (мс)
     */
    fun generatePhotoReport(
        archive: SubArray,
        selectedPins: Set<String>,
        from: Long,
        to: Long,
        uri: Uri
    ): Boolean {
        return try {
            val dateFmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val rootName = "Фотоотчет от " +
                    SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())

            context.contentResolver.openOutputStream(uri)?.use { out ->
                ZipOutputStream(out).use { zip ->
                    zip.setLevel(0)

                    // группируем: row -> col -> список пинов
                    val grouped = LinkedHashMap<Int, LinkedHashMap<Int, MutableList<CommentPin>>>()
                    for ((key, cell) in archive.cells) {
                        val parts = key.split(",")
                        val r = parts.getOrNull(0)?.toIntOrNull() ?: continue
                        val c = parts.getOrNull(1)?.toIntOrNull() ?: continue
                        for (pin in cell.comments) {
                            if (pin.id !in selectedPins) continue
                            if (pin.createdAt !in from..to) continue
                            grouped.getOrPut(r) { LinkedHashMap() }
                                .getOrPut(c) { mutableListOf() }
                                .add(pin)
                        }
                    }

                    for ((r, colsMap) in grouped) {
                        val rowLabel = archive.rowLabels[r]?.ifBlank { "Строка ${r + 1}" }
                            ?: "Строка ${r + 1}"
                        val rowEntry = "$rootName/$rowLabel/"
                        zip.putNextEntry(ZipEntry(rowEntry)); zip.closeEntry()

                        for ((c, pins) in colsMap) {
                            val colLabel = archive.colLabels[c]?.ifBlank { "Столбец ${c + 1}" }
                                ?: "Столбец ${c + 1}"
                            val colEntry = "$rowEntry$colLabel/"
                            zip.putNextEntry(ZipEntry(colEntry)); zip.closeEntry()

                            val cell = archive.cell(r, c)
                            val cellTitle = cell?.title?.ifBlank { "Ячейка" } ?: "Ячейка"

                            for (pin in pins) {
                                val dateStr = dateFmt.format(Date(pin.createdAt))
                                val baseName = "${rowLabel}_${cellTitle}_${colLabel}_$dateStr"
                                    .replace(Regex("[\\\\/:*?\"<>|]"), "_")

                                if (pin.photos.isEmpty()) {
                                    // Пишем README с текстом комментария
                                    zip.putNextEntry(ZipEntry("$colEntry$baseName.txt"))
                                    val txt = "Название: ${pin.title}\nРейтинг: ${pin.rating}\n" +
                                            "Текст: ${pin.text}\nДата: ${Date(pin.createdAt)}"
                                    zip.write(txt.toByteArray())
                                    zip.closeEntry()
                                } else if (pin.photos.size == 1) {
                                    val ph = pin.photos.first()
                                    val src = File(imagesDir, ph.file)
                                    if (src.exists()) {
                                        zip.putNextEntry(ZipEntry("$colEntry$baseName.jpg"))
                                        src.inputStream().use { it.copyTo(zip) }
                                        zip.closeEntry()
                                    }
                                } else {
                                    val pinFolder = "$colEntry$baseName/"
                                    zip.putNextEntry(ZipEntry(pinFolder)); zip.closeEntry()
                                    for ((i, ph) in pin.photos.withIndex()) {
                                        val src = File(imagesDir, ph.file)
                                        if (src.exists()) {
                                            zip.putNextEntry(ZipEntry("${pinFolder}${i + 1}.jpg"))
                                            src.inputStream().use { it.copyTo(zip) }
                                            zip.closeEntry()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            true
        } catch (e: Exception) { Log.e("Kotlovan", "generatePhotoReport", e); false }
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