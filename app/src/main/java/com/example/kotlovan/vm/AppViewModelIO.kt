package com.example.kotlovan.vm

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.kotlovan.data.ArrayConfig
import com.example.kotlovan.data.CellData
import com.example.kotlovan.data.SubArray

// Функции markChanged / markSaved больше не нужны для автосохранения, но оставлены для совместимости
fun AppViewModel.markChanged() {
    hasUnsavedChanges = true
}

fun AppViewModel.markSaved() {
    hasUnsavedChanges = false
    showUnsavedBanner = false
}

fun AppViewModel.onUserSave(uri: Uri): Boolean {
    return try {
        val finalConfig = repo.saveAsZip(config, uri) ?: return false
        config = finalConfig
        boundSaveUri = uri
        persistBoundUri(uri)
        repo.autosave(config)
        markSaved()
        true
    } catch (_: Throwable) { 
        false 
    }
}

internal fun AppViewModel.persistBoundUri(uri: Uri) {
    try {
        getApplication<Application>()
            .getSharedPreferences("kotlovan_prefs", 0)
            .edit().putString("bound_save_uri", uri.toString()).apply()
    } catch (_: Exception) {}
}

fun AppViewModel.newArray() {
    config = ArrayConfig()
    useAux = false; photoModeActive = false
    boundSaveUri = null
    markSaved()
}

fun AppViewModel.load(uri: Uri): Boolean {
    val fromZip = repo.loadFromZip(uri)
    if (fromZip != null) {
        config = normalize(fromZip)
        boundSaveUri = uri
        persistBoundUri(uri)
        useAux = false; photoModeActive = false
        pendingReconnect = emptyList()
        markSaved()
        return true
    }
    val fromJson = repo.loadFromUri(uri) ?: return false
    config = normalize(fromJson)
    useAux = false; photoModeActive = false
    pendingReconnect = emptyList()
    markSaved()
    return true
}

fun AppViewModel.clearPendingReconnect() { pendingReconnect = emptyList() }

fun AppViewModel.reconnectImage(r: Int, c: Int, uri: Uri) {
    val name = repo.importImage(uri) ?: return
    val sz = repo.getImageSize(name)
    fun fixSub(sub: SubArray): SubArray {
        val cur = sub.cell(r, c) ?: return sub
        return sub.copy(cells = sub.cells + (sub.key(r, c) to cur.copy(
            imageFile = name, imageUri = null,
            imageWidth = sz?.first ?: 0, imageHeight = sz?.second ?: 0
        )))
    }
    config = config.copy(main = fixSub(config.main), aux = fixSub(config.aux))
    markChanged()
}

fun AppViewModel.save(uri: Uri): Boolean = onUserSave(uri)
fun AppViewModel.savePhotoArchive(uri: Uri): Boolean = onUserSave(uri)

fun AppViewModel.loadPhotoArchive(uri: Uri): Boolean {
    val loaded = repo.loadFromZip(uri) ?: return false
    config = normalize(loaded)
    boundSaveUri = uri
    persistBoundUri(uri)
    markSaved()
    return true
}

fun AppViewModel.generatePhotoReport(selectedPins: Set<String>, from: Long, to: Long, uri: Uri): Boolean {
    val mergedCells = LinkedHashMap<String, CellData>()
    for ((k, cell) in config.main.cells) {
        val photoComms = config.photoComments[k].orEmpty()
            .filter { it.id in selectedPins && it.createdAt in from..to }
        if (photoComms.isNotEmpty()) mergedCells[k] = cell.copy(comments = photoComms)
    }
    for ((k, cell) in config.aux.cells) {
        val photoComms = config.photoComments[k].orEmpty()
            .filter { it.id in selectedPins && it.createdAt in from..to }
        if (photoComms.isNotEmpty()) mergedCells[k] = cell.copy(comments = photoComms)
    }
    val merged = SubArray(
        cells = mergedCells,
        rowLabels = config.main.rowLabels + config.aux.rowLabels,
        colLabels = config.main.colLabels + config.aux.colLabels
    )
    return repo.generatePhotoReport(merged, selectedPins, from, to, uri)
}

fun AppViewModel.autosave() { repo.autosave(config) }

fun AppViewModel.setIconSettings(size: Float, opacity: Float) {
    config = config.copy(iconSize = size, iconOpacity = opacity)
    markChanged()
}