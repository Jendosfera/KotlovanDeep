package com.example.kotlovan.vm

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.example.kotlovan.data.ArrayConfig
import com.example.kotlovan.data.ArrayRepository
import com.example.kotlovan.data.CellData
import com.example.kotlovan.data.CommentPin

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val repo = ArrayRepository(app)

    var config by mutableStateOf(ArrayConfig())
        private set

    var hasUnsavedChanges by mutableStateOf(false)
        private set

    /** Ячейки, у которых после загрузки JSON нет persistable-права на URI. */
    var pendingReconnect: List<Pair<Int, Int>> by mutableStateOf(emptyList())
        private set

    init {
        val loaded = repo.loadAutosave()
        if (loaded != null) config = normalize(loaded)
    }

    fun newArray() {
        config = ArrayConfig()
        hasUnsavedChanges = true
    }
	
	fun updateLastCell(r: Int, c: Int) {
		config = config.copy(lastRow = r, lastCol = c)
		hasUnsavedChanges = true
	}

    fun load(uri: Uri): Boolean {
        val c = repo.loadFromUri(uri) ?: return false
        config = normalize(c)

        // Ищем ячейки, у которых нет persistable-прав (после переустановки)
        val missing = mutableListOf<Pair<Int, Int>>()
        config.cells.forEach { (key, cell) ->
            val u = cell.imageUri ?: return@forEach
            if (!repo.hasPersistablePermission(Uri.parse(u))) {
                val parts = key.split(",")
                val r = parts.getOrNull(0)?.toIntOrNull() ?: return@forEach
                val cc = parts.getOrNull(1)?.toIntOrNull() ?: return@forEach
                missing += r to cc
            }
        }
        pendingReconnect = missing
        hasUnsavedChanges = false
        return true
    }

    fun clearPendingReconnect() { pendingReconnect = emptyList() }

    /** Восстанавливает доступ к изображению для конкретной ячейки (переустановка). */
    fun reconnectImage(r: Int, c: Int, uri: Uri) {
        repo.takePersistablePermission(uri)
        val sz = repo.getImageSize(uri)
        updateCell(r, c) {
            it.copy(
                imageUri = uri.toString(),
                imageWidth = sz?.first ?: it.imageWidth,
                imageHeight = sz?.second ?: it.imageHeight
            )
        }
    }

    fun save(uri: Uri): Boolean {
        val ok = repo.saveToUri(config, uri)
        if (ok) hasUnsavedChanges = false
        return ok
    }

    fun autosave() {
        repo.autosave(config)
        hasUnsavedChanges = false
    }

    fun cell(r: Int, c: Int): CellData? = config.cell(r, c)

    fun upsertCell(r: Int, c: Int, data: CellData) {
        config = config.copy(cells = config.cells + (config.key(r, c) to data))
        hasUnsavedChanges = true
    }

    fun removeCell(r: Int, c: Int) {
        config = config.copy(cells = config.cells - config.key(r, c))
        hasUnsavedChanges = true
    }

    fun swapCells(r1: Int, c1: Int, r2: Int, c2: Int) {
        val a = config.cell(r1, c1)
        val b = config.cell(r2, c2)
        val k1 = config.key(r1, c1); val k2 = config.key(r2, c2)
        val map = config.cells.toMutableMap()
        if (b != null) map[k1] = b else map.remove(k1)
        if (a != null) map[k2] = a else map.remove(k2)
        config = config.copy(cells = map)
        hasUnsavedChanges = true
    }

    /** viewportOffset — смещение общей точки от центра экрана (px). */
    fun setGlobalViewport(scale: Float, vOx: Float, vOy: Float, r: Int, c: Int) {
        config = config.copy(
            globalScale = scale,
            globalOffsetX = vOx,
            globalOffsetY = vOy,
            lastRow = r,
            lastCol = c
        )
        hasUnsavedChanges = true
    }

    fun updateCell(r: Int, c: Int, transform: (CellData) -> CellData) {
        val cur = cell(r, c) ?: return
        upsertCell(r, c, transform(cur))
    }

    fun setComments(r: Int, c: Int, comments: List<CommentPin>) =
        updateCell(r, c) { it.copy(comments = comments) }

    fun setIconSettings(size: Float, opacity: Float) {
        config = config.copy(iconSize = size, iconOpacity = opacity)
        hasUnsavedChanges = true
    }

    private fun normalize(src: ArrayConfig): ArrayConfig {
        val fixed = src.cells.mapValues { (_, cell) ->
            val u = cell.imageUri ?: return@mapValues cell
            val parsed = Uri.parse(u)
            repo.takePersistablePermission(parsed)
            if (cell.imageWidth == 0) {
                val sz = repo.getImageSize(parsed)
                if (sz != null) cell.copy(imageWidth = sz.first, imageHeight = sz.second)
                else cell
            } else cell
        }
        return src.copy(cells = fixed)
    }
}