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
import com.example.kotlovan.data.LinkReturnState
import com.example.kotlovan.data.PinLink
import com.example.kotlovan.data.SubArray

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val repo = ArrayRepository(app)

	var lastRatingRow by mutableStateOf(-1)
	var hiddenRows by mutableStateOf(setOf<Int>())
	var hiddenCols by mutableStateOf(setOf<Int>())

	fun toggleRowVisibility(r: Int) {
 	   hiddenRows = if (r in hiddenRows) hiddenRows - r else hiddenRows + r
	}
	fun toggleColVisibility(c: Int) {
	    hiddenCols = if (c in hiddenCols) hiddenCols - c else hiddenCols + c
	}
	
	private val themeRepo = com.example.kotlovan.theme.ThemeRepository(app)

	var themeProfile by mutableStateOf(themeRepo.load())
		private set

	fun updateTheme(profile: com.example.kotlovan.data.ThemeProfile) {
		themeProfile = profile
		themeRepo.save(profile)
	}

	fun resetTheme() {
		themeRepo.reset()
		themeProfile = themeRepo.load()
	}

    var config by mutableStateOf(ArrayConfig())
        private set

    var hasUnsavedChanges by mutableStateOf(false)
        private set

    var useAux by mutableStateOf(false)
    var linkReturn by mutableStateOf<LinkReturnState?>(null)
    var pendingRejump by mutableStateOf<PinLink?>(null)

    /** Ячейки (row, col), которые потеряли картинки при загрузке старого JSON. */
    var pendingReconnect by mutableStateOf(emptyList<Pair<Int, Int>>())
        private set

    val active: SubArray get() = if (useAux) config.aux else config.main

    init {
        repo.loadAutosave()?.let { config = normalize(it) }
    }

    fun newArray() {
        config = ArrayConfig()
        useAux = false
        hasUnsavedChanges = true
    }

    fun load(uri: Uri): Boolean {
        // 1) Пробуем ZIP
        val fromZip = repo.loadFromZip(uri)
        if (fromZip != null) {
            config = normalize(fromZip)
            useAux = false
            pendingReconnect = emptyList()
            hasUnsavedChanges = false
            return true
        }
        // 2) Фоллбэк — JSON
        val fromJson = repo.loadFromUri(uri) ?: return false
        config = normalize(fromJson)   // миграция imageUri -> imageFile
        useAux = false
        val missing = mutableListOf<Pair<Int, Int>>()
        fun collect(sub: SubArray) {
            sub.cells.forEach { (key, cell) ->
                if (cell.imageFile == null && cell.imageUri != null) {
                    val parts = key.split(",")
                    val r = parts.getOrNull(0)?.toIntOrNull() ?: return@forEach
                    val cc = parts.getOrNull(1)?.toIntOrNull() ?: return@forEach
                    if (r to cc !in missing) missing += r to cc
                }
            }
        }
        collect(config.main); collect(config.aux)
        pendingReconnect = missing
        hasUnsavedChanges = false
        return true
    }

    fun clearPendingReconnect() { pendingReconnect = emptyList() }

    fun reconnectImage(r: Int, c: Int, uri: Uri) {
        val name = repo.importImage(uri) ?: return
        val sz = repo.getImageSize(name)
        config = config.copy(
            main = if (config.main.cell(r, c) != null) config.main.copy(
                cells = config.main.cells + (config.main.key(r, c) to config.main.cell(r, c)!!.copy(
                    imageFile = name, imageUri = null,
                    imageWidth = sz?.first ?: 0, imageHeight = sz?.second ?: 0
                ))
            ) else config.main,
            aux = if (config.aux.cell(r, c) != null) config.aux.copy(
                cells = config.aux.cells + (config.aux.key(r, c) to config.aux.cell(r, c)!!.copy(
                    imageFile = name, imageUri = null,
                    imageWidth = sz?.first ?: 0, imageHeight = sz?.second ?: 0
                ))
            ) else config.aux
        )
    }

    fun save(uri: Uri): Boolean {
        val ok = repo.saveAsZip(config, uri)
        if (ok) hasUnsavedChanges = false
        return ok
    }

    fun autosave() {
        repo.autosave(config)
        hasUnsavedChanges = false
    }

    fun cell(r: Int, c: Int): CellData? = active.cell(r, c)

    fun upsertCell(r: Int, c: Int, data: CellData) = updateActive {
        it.copy(cells = it.cells + (it.key(r, c) to data))
    }

    fun removeCell(r: Int, c: Int) = updateActive {
        it.copy(cells = it.cells - it.key(r, c))
    }

    fun swapCells(r1: Int, c1: Int, r2: Int, c2: Int) = updateActive { sub ->
        val a = sub.cell(r1, c1); val b = sub.cell(r2, c2)
        val k1 = sub.key(r1, c1); val k2 = sub.key(r2, c2)
        val map = sub.cells.toMutableMap()
        if (b != null) map[k1] = b else map.remove(k1)
        if (a != null) map[k2] = a else map.remove(k2)
        sub.copy(cells = map)
    }

    fun setGlobalViewport(scale: Float, vOx: Float, vOy: Float, r: Int, c: Int) = updateActive {
        it.copy(globalScale = scale, globalOffsetX = vOx, globalOffsetY = vOy, lastRow = r, lastCol = c)
    }

    fun updateCell(r: Int, c: Int, transform: (CellData) -> CellData) = updateActive { sub ->
        val cur = sub.cell(r, c) ?: return@updateActive sub
        sub.copy(cells = sub.cells + (sub.key(r, c) to transform(cur)))
    }

    fun setComments(r: Int, c: Int, comments: List<CommentPin>) =
        updateCell(r, c) { it.copy(comments = comments) }

    fun updateLastCell(r: Int, c: Int) = updateActive { it.copy(lastRow = r, lastCol = c) }

    fun setRowLabel(r: Int, label: String) = updateActive {
        it.copy(rowLabels = if (label.isBlank()) it.rowLabels - r else it.rowLabels + (r to label))
    }

    fun setColLabel(c: Int, label: String) = updateActive {
        it.copy(colLabels = if (label.isBlank()) it.colLabels - c else it.colLabels + (c to label))
    }

    fun setCommentLink(r: Int, c: Int, id: String, link: PinLink?) {
        updateCell(r, c) { cell ->
            cell.copy(comments = cell.comments.map {
                if (it.id == id) it.copy(link = link) else it
            })
        }
    }

    fun setIconSettings(size: Float, opacity: Float) {
        config = config.copy(iconSize = size, iconOpacity = opacity)
        hasUnsavedChanges = true
    }

    private fun updateActive(transform: (SubArray) -> SubArray) {
        config = if (useAux) config.copy(aux = transform(config.aux))
                 else config.copy(main = transform(config.main))
        hasUnsavedChanges = true
    }

    /** Миграция imageUri -> imageFile + подтягивание размеров. */
    private fun normalize(src: ArrayConfig): ArrayConfig {
        fun fixSub(sub: SubArray): SubArray = sub.copy(
            cells = sub.cells.mapValues { (_, cell) ->
                when {
                    cell.imageFile == null && cell.imageUri != null -> {
                        val name = repo.importImage(Uri.parse(cell.imageUri))
                        if (name != null) {
                            val sz = repo.getImageSize(name)
                            cell.copy(
                                imageFile = name, imageUri = null,
                                imageWidth = sz?.first ?: cell.imageWidth,
                                imageHeight = sz?.second ?: cell.imageHeight
                            )
                        } else cell   // URI мёртв — оставляем как есть, попадёт в pendingReconnect
                    }
                    cell.imageFile != null && cell.imageWidth == 0 -> {
                        val sz = repo.getImageSize(cell.imageFile)
                        if (sz != null) cell.copy(imageWidth = sz.first, imageHeight = sz.second) else cell
                    }
                    else -> cell
                }
            }
        )
        return src.copy(main = fixSub(src.main), aux = fixSub(src.aux))
    }
}
