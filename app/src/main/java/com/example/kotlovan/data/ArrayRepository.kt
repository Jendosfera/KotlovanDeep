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
import java.security.MessageDigest
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

    // ─────────── Изображения ───────────

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

    // ─────────── Хэш ───────────

    private fun fileHash(file: File): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            file.inputStream().use { input ->
                val buf = ByteArray(8192)
                var read: Int
                while (input.read(buf).also { read = it } > 0) {
                    md.update(buf, 0, read)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Throwable) {
            "err_${file.name}_${file.length()}"
        }
    }

    // ─────────── Дедупликация ───────────

    /**
     * Проходит по ВСЕМ файлам в imagesDir, находит дубликаты по MD5,
     * удаляет дубликаты, возвращает карту (oldName → canonicalName).
     */
    private fun deduplicateFiles(): Map<String, String> {
        val hashToCanonical = LinkedHashMap<String, String>()
        val oldToNew = LinkedHashMap<String, String>()

        val files = imagesDir.listFiles()?.filter { it.isFile } ?: return oldToNew
        for (f in files) {
            val h = fileHash(f)
            val canonical = hashToCanonical[h]
            if (canonical != null) {
                // Дубликат — удаляем файл, ссылки перенаправляем
                oldToNew[f.name] = canonical
                try { f.delete() } catch (_: Throwable) {}
            } else {
                hashToCanonical[h] = f.name
                oldToNew[f.name] = f.name
            }
        }
        return oldToNew
    }

    /** Применяет карту дедупликации к SubArray (возвращает копию). */
    private fun applyDedup(sub: SubArray, oldToNew: Map<String, String>): SubArray {
        val newCells = LinkedHashMap<String, CellData>()
        for ((k, cell) in sub.cells) {
            val newImage = cell.imageFile?.let { oldToNew[it] ?: it }
            val newPhotos = cell.comments.map { pin ->
                pin.copy(photos = pin.photos.map { ph ->
                    ph.copy(file = oldToNew[ph.file] ?: ph.file)
                })
            }
            newCells[k] = cell.copy(imageFile = newImage, comments = newPhotos)
        }
        return sub.copy(cells = newCells)
    }

    /**
     * Оптимизирует ВСЁ хранилище: удаляет физические дубликаты файлов
     * и возвращает config с обновлёнными путями.
     *
     * Вызывается из `saveAsZip` перед упаковкой.
     */
    fun optimizeStorage(config: ArrayConfig): ArrayConfig {
        val oldToNew = deduplicateFiles()
        if (oldToNew.isEmpty()) return config

        val newMain = applyDedup(config.main, oldToNew)
        val newAux = applyDedup(config.aux, oldToNew)
        val newPhotoComments = config.photoComments.mapValues { (_, pins) ->
            pins.map { pin ->
                pin.copy(photos = pin.photos.map { ph ->
                    ph.copy(file = oldToNew[ph.file] ?: ph.file)
                })
            }
        }
        return config.copy(
            main = newMain,
            aux = newAux,
            photoComments = newPhotoComments
        )
    }

    // ─────────── Сохранение/загрузка SubArray ───────────

    fun saveSubArrayAsZip(sub: SubArray, uri: Uri): Boolean {
        return try {
            val oldToNew = deduplicateFiles()
            val deduped = applyDedup(sub, oldToNew)

            context.contentResolver.openOutputStream(uri)?.use { out ->
                ZipOutputStream(out).use { zip ->
                    zip.setLevel(0)
                    zip.putNextEntry(ZipEntry("array.json"))
                    zip.write(json.encodeToString(SubArray.serializer(), deduped).toByteArray())
                    zip.closeEntry()

                    val used = LinkedHashSet<String>()
                    for ((_, cell) in deduped.cells) {
                        cell.imageFile?.let { used += it }
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

    // ─────────── Основное сохранение/загрузка ───────────

    /**
     * Сохраняет config в ZIP.
     * Перед упаковкой:
     *  1. deduplicateFiles() — удаляет физические дубликаты в imagesDir,
     *     возвращает карту (oldName → canonicalName).
     *  2. Применяет эту карту ко всем SubArray и photoComments.
     *
     * @return finalConfig — config с обновлёнными (каноническими) именами.
     *   null — если запись не удалась.
     */
    fun saveAsZip(config: ArrayConfig, uri: Uri): ArrayConfig? {
        return try {
            // 1. Дедуплицируем ВСЁ хранилище
            val oldToNew = deduplicateFiles()

            val newMain = applyDedup(config.main, oldToNew)
            val newAux = applyDedup(config.aux, oldToNew)
            val newPhotoComments = config.photoComments.mapValues { (_, pins) ->
                pins.map { pin ->
                    pin.copy(photos = pin.photos.map { ph ->
                        ph.copy(file = oldToNew[ph.file] ?: ph.file)
                    })
                }
            }

            val finalConfig = config.copy(
                main = newMain,
                aux = newAux,
                photoComments = newPhotoComments
            )

            // 2. Пишем ZIP
            context.contentResolver.openOutputStream(uri)?.use { out ->
                ZipOutputStream(out).use { zip ->
                    zip.setLevel(0)
                    zip.putNextEntry(ZipEntry("array.json"))
                    zip.write(json.encodeToString(ArrayConfig.serializer(), finalConfig).toByteArray())
                    zip.closeEntry()

                    val used = LinkedHashSet<String>()
                    fun collect(sub: SubArray) {
                        for ((_, cell) in sub.cells) {
                            cell.imageFile?.let { used += it }
                            for (pin in cell.comments) for (ph in pin.photos) used += ph.file
                        }
                    }
                    collect(finalConfig.main)
                    collect(finalConfig.aux)
                    for (pins in finalConfig.photoComments.values) {
                        for (pin in pins) for (ph in pin.photos) used += ph.file
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
            finalConfig
        } catch (e: Exception) {
            Log.e("Kotlovan", "saveAsZip", e)
            null
        }
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