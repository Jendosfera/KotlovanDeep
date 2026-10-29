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

    var config by mutableStateOf(ArrayConfig())
        private set

    var hasUnsavedChanges by mutableStateOf(false)
        private set

    var useAux by mutableStateOf(false)
    var photoModeActive by mutableStateOf(false)

    var linkReturn by mutableStateOf<LinkReturnState?>(null)
    var pendingRejump by mutableStateOf<PinLink?>(null)

    var pendingReconnect by mutableStateOf(emptyList<Pair<Int, Int>>())
        private set

    var lastRatingRow by mutableStateOf(-1)
    var hiddenRows by mutableStateOf(setOf<Int>())
    var hiddenCols by mutableStateOf(setOf<Int>())

    private val themeRepo = com.example.kotlovan.theme.ThemeRepository(app)

    var themeProfile by mutableStateOf(themeRepo.load())
        private set

    val active: SubArray
        get() = when {
            photoModeActive && useAux -> config.photoAux
            photoModeActive -> config.photoMain
            useAux -> config.aux
            else -> config.main
        }

    init {
        repo.loadAutosave()?.let { config = normalize(it) }
    }

    fun updateTheme(profile: com.example.kotlovan.data.ThemeProfile) {
        themeProfile = profile
        themeRepo.save(profile)
    }
    fun resetTheme() { themeRepo.reset(); themeProfile = themeRepo.load() }

    fun toggleRowVisibility(r: Int) {
        hiddenRows = if (r in hiddenRows) hiddenRows - r else hiddenRows + r
    }
    fun toggleColVisibility(c: Int) {
        hiddenCols = if (c in hiddenCols) hiddenCols - c else hiddenCols + c
    }

    fun newArray() {
        config = ArrayConfig()
        useAux = false; photoModeActive = false
        hasUnsavedChanges = true
    }

    fun load(uri: Uri): Boolean {
        val fromZip = repo.loadFromZip(uri)
        if (fromZip != null) {
            config = normalize(fromZip)
            useAux = false; photoModeActive = false
            pendingReconnect = emptyList()
            hasUnsavedChanges = false
            return true
        }
        val fromJson = repo.loadFromUri(uri) ?: return false
        config = normalize(fromJson)
        useAux = false; photoModeActive = false
        pendingReconnect = emptyList()
        hasUnsavedChanges = false
        return true
    }

    fun clearPendingReconnect() { pendingReconnect = emptyList() }

    fun reconnectImage(r: Int, c: Int, uri: Uri) {
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
    }

    fun save(uri: Uri): Boolean {
        val ok = repo.saveAsZip(config, uri)
        if (ok) hasUnsavedChanges = false
        return ok
    }

    fun savePhotoArchive(uri: Uri): Boolean {
        val photoOnly = config.copy(main = SubArray(), aux = SubArray())
        val ok = repo.saveAsZip(photoOnly, uri)
        if (ok) hasUnsavedChanges = false
        return ok
    }

    fun loadPhotoArchive(uri: Uri): Boolean {
        val loaded = repo.loadFromZip(uri) ?: return false
        config = config.copy(photoMain = loaded.photoMain, photoAux = loaded.photoAux)
        hasUnsavedChanges = false
        return true
    }

    fun generatePhotoReport(selectedPins: Set<String>, from: Long, to: Long, uri: Uri): Boolean {
        val mergedCells = LinkedHashMap<String, CellData>()
        mergedCells.putAll(config.photoMain.cells)
        mergedCells.putAll(config.photoAux.cells)
        val merged = SubArray(
            cells = mergedCells,
            rowLabels = config.photoMain.rowLabels + config.photoAux.rowLabels,
            colLabels = config.photoMain.colLabels + config.photoAux.colLabels
        )
        return repo.generatePhotoReport(merged, selectedPins, from, to, uri)
    }

    fun autosave() { repo.autosave(config); hasUnsavedChanges = false }

    // === Ключевые методы ===

    fun cell(r: Int, c: Int, d: Int = 0): CellData? = active.cell(r, c, d)
    fun depthAt(r: Int, c: Int): Int = active.depthAt(r, c)
    fun cellsAt(r: Int, c: Int): List<Pair<Int, CellData>> = active.cellsAt(r, c)
    fun hasCell(r: Int, c: Int): Boolean = active.hasCell(r, c)

    fun upsertCell(r: Int, c: Int, data: CellData, d: Int = 0) = updateActive {
        it.copy(cells = it.cells + (it.key(r, c, d) to data))
    }

    fun removeCell(r: Int, c: Int, d: Int = 0) = updateActive { sub ->
        val map = sub.cells.toMutableMap()
        map.remove(sub.key(r, c, d))
        // Сдвиг глубжестоящих
        val deeper = map.keys.filter {
            val p = it.split(",")
            p.size == 3 && p[0].toIntOrNull() == r && p[1].toIntOrNull() == c &&
                    (p[2].toIntOrNull() ?: 0) > d
        }.sortedBy { it.split(",")[2].toIntOrNull() ?: 0 }
        for (k in deeper) {
            val p = k.split(",")
            val dd = p[2].toIntOrNull() ?: 0
            val v = map[k] ?: continue
            map.remove(k)
            map["${r},${c},${dd - 1}"] = v
        }
        sub.copy(cells = map)
    }

    fun swapCells(r1: Int, c1: Int, d1: Int, r2: Int, c2: Int, d2: Int) = updateActive { sub ->
        val a = sub.cell(r1, c1, d1); val b = sub.cell(r2, c2, d2)
        val k1 = sub.key(r1, c1, d1); val k2 = sub.key(r2, c2, d2)
        val map = sub.cells.toMutableMap()
        if (b != null) map[k1] = b else map.remove(k1)
        if (a != null) map[k2] = a else map.remove(k2)
        sub.copy(cells = map)
    }

    fun setGlobalViewport(scale: Float, vOx: Float, vOy: Float, r: Int, c: Int, d: Int) = updateActive {
        it.copy(globalScale = scale, globalOffsetX = vOx, globalOffsetY = vOy,
            lastRow = r, lastCol = c, lastDepth = d)
    }

    fun updateCell(r: Int, c: Int, d: Int = 0, transform: (CellData) -> CellData) = updateActive { sub ->
        val cur = sub.cell(r, c, d) ?: return@updateActive sub
        sub.copy(cells = sub.cells + (sub.key(r, c, d) to transform(cur)))
    }

    fun setComments(r: Int, c: Int, comments: List<CommentPin>, d: Int = 0) =
        updateCell(r, c, d) { it.copy(comments = comments) }

    fun updateLastCell(r: Int, c: Int, d: Int = 0) = updateActive { it.copy(lastRow = r, lastCol = c, lastDepth = d) }

    fun setRowLabel(r: Int, label: String) = updateActive {
        it.copy(rowLabels = if (label.isBlank()) it.rowLabels - r else it.rowLabels + (r to label))
    }
    fun setColLabel(c: Int, label: String) = updateActive {
        it.copy(colLabels = if (label.isBlank()) it.colLabels - c else it.colLabels + (c to label))
    }

    fun setCommentLink(r: Int, c: Int, id: String, link: PinLink?, d: Int = 0) {
        updateCell(r, c, d) { cell ->
            cell.copy(comments = cell.comments.map {
                if (it.id == id) it.copy(link = link) else it
            })
        }
    }

    fun setIconSettings(size: Float, opacity: Float) {
        config = config.copy(iconSize = size, iconOpacity = opacity)
        hasUnsavedChanges = true
    }

    /** Вкладывает (sr,sc,sd) под (tr,tc). Ячейка перемещается целиком, место освобождается. */
    fun makeNested(sr: Int, sc: Int, sd: Int, tr: Int, tc: Int) {
        if (sr == tr && sc == tc) return
        val src = active.cell(sr, sc, sd) ?: return
        val targetDepth = active.depthAt(tr, tc)
        if (targetDepth < 0) return
        val newDepth = targetDepth + 1
        updateActive { sub ->
            val map = sub.cells.toMutableMap()
            map.remove(sub.key(sr, sc, sd))
            // Сдвигаем всё, что было глубже target, на +1
            val toShift = map.keys.filter {
                val p = it.split(",")
                p.size == 3 && p[0].toIntOrNull() == tr && p[1].toIntOrNull() == tc &&
                        (p[2].toIntOrNull() ?: 0) >= newDepth
            }.sortedByDescending { it.split(",")[2].toIntOrNull() ?: 0 }
            for (k in toShift) {
                val p = k.split(",")
                val dd = p[2].toIntOrNull() ?: 0
                val v = map[k] ?: continue
                map.remove(k)
                map["${tr},${tc},${dd + 1}"] = v
            }
            map[sub.key(tr, tc, newDepth)] = src
            sub.copy(cells = map)
        }
        autosave()
    }

    /** Меняет местами вложенную (sr,sc,sd) с её родителем — перестановка по глубине. */
    fun unnest(sr: Int, sc: Int, sd: Int) {
        if (sd <= 0) return
        val parentDepth = sd - 1
        val child = active.cell(sr, sc, sd) ?: return
        val parent = active.cell(sr, sc, parentDepth) ?: return
        updateActive { sub ->
            val map = sub.cells.toMutableMap()
            map[sub.key(sr, sc, sd)] = parent
            map[sub.key(sr, sc, parentDepth)] = child
            sub.copy(cells = map)
        }
        autosave()
    }

    private fun updateActive(transform: (SubArray) -> SubArray) {
        config = when {
            photoModeActive && useAux -> config.copy(photoAux = transform(config.photoAux))
            photoModeActive -> config.copy(photoMain = transform(config.photoMain))
            useAux -> config.copy(aux = transform(config.aux))
            else -> config.copy(main = transform(config.main))
        }
        hasUnsavedChanges = true
    }

    private fun migrateSub(sub: SubArray): SubArray {
        val newCells = LinkedHashMap<String, CellData>()
        for ((k, v) in sub.cells) {
            val p = k.split(",")
            val newKey = when (p.size) {
                2 -> "${p[0]},${p[1]},0"
                3 -> k
                else -> continue
            }
            newCells[newKey] = v
        }
        return sub.copy(cells = newCells)
    }

    private fun normalize(src: ArrayConfig): ArrayConfig {
        fun fixSub(sub0: SubArray): SubArray {
            val sub = migrateSub(sub0)
            return sub.copy(cells = sub.cells.mapValues { (_, cell) ->
                if (cell.imageFile != null && cell.imageWidth == 0) {
                    val sz = repo.getImageSize(cell.imageFile)
                    if (sz != null) cell.copy(imageWidth = sz.first, imageHeight = sz.second) else cell
                } else cell
            })
        }
        return src.copy(
            main = fixSub(src.main),
            aux = fixSub(src.aux),
            photoMain = fixSub(src.photoMain),
            photoAux = fixSub(src.photoAux)
        )
    }
}