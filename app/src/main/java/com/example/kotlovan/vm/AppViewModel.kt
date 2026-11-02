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

    /** В фоторежиме — тот же main/aux, картинки те же. Комментарии подменяются отдельно. */
    val active: SubArray
        get() = if (useAux) config.aux else config.main

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
        val ok = repo.saveAsZip(config, uri)
        if (ok) hasUnsavedChanges = false
        return ok
    }

    fun loadPhotoArchive(uri: Uri): Boolean {
        val loaded = repo.loadFromZip(uri) ?: return false
        config = normalize(loaded)
        hasUnsavedChanges = false
        return true
    }

    fun generatePhotoReport(selectedPins: Set<String>, from: Long, to: Long, uri: Uri): Boolean {
        // Собираем "виртуальный" SubArray: картинки и структура из main+aux,
        // а комментарии — из photoComments
        val mergedCells = LinkedHashMap<String, CellData>()
        for ((k, cell) in config.main.cells) {
            val photoComms = config.photoComments[k].orEmpty()
            if (photoComms.isNotEmpty()) mergedCells[k] = cell.copy(comments = photoComms)
        }
        for ((k, cell) in config.aux.cells) {
            val photoComms = config.photoComments[k].orEmpty()
            if (photoComms.isNotEmpty()) mergedCells[k] = cell.copy(comments = photoComms)
        }
        val merged = SubArray(
            cells = mergedCells,
            rowLabels = config.main.rowLabels + config.aux.rowLabels,
            colLabels = config.main.colLabels + config.aux.colLabels
        )
        return repo.generatePhotoReport(merged, selectedPins, from, to, uri)
    }

    fun autosave() { repo.autosave(config); hasUnsavedChanges = false }

    // ==== Ячейки ====

    /** Возвращает CellData с подменёнными (при photoModeActive) комментариями. */
    fun cell(r: Int, c: Int, d: Int = 0): CellData? {
        val base = active.cell(r, c, d) ?: return null
        return if (photoModeActive) {
            val key = active.key(r, c, d)
            val photoComms = config.photoComments[key].orEmpty()
            base.copy(comments = photoComms)
        } else base
    }

    fun depthAt(r: Int, c: Int): Int = active.depthAt(r, c)
    fun cellsAt(r: Int, c: Int): List<Pair<Int, CellData>> = active.cellsAt(r, c)
    fun hasCell(r: Int, c: Int): Boolean = active.hasCell(r, c)

    fun upsertCell(r: Int, c: Int, data: CellData, d: Int = 0) {
        // В фоторежиме структура та же, обновляем ячейку в main/aux,
        // но комментарии сохраняем отдельно (они не часть CellData в photoMode).
        val commentsToRestore = if (photoModeActive) data.comments else emptyList()
        val cleanData = if (photoModeActive) data.copy(comments = emptyList()) else data
        config = when {
            useAux -> config.copy(aux = config.aux.copy(
                cells = config.aux.cells + (config.aux.key(r, c, d) to cleanData)
            ))
            else -> config.copy(main = config.main.copy(
                cells = config.main.cells + (config.main.key(r, c, d) to cleanData)
            ))
        }
        if (photoModeActive && commentsToRestore.isNotEmpty()) {
            config = config.copy(photoComments = config.photoComments +
                    (active.key(r, c, d) to commentsToRestore))
        }
        hasUnsavedChanges = true
    }

    fun removeCell(r: Int, c: Int, d: Int = 0) {
        val key = active.key(r, c, d)
        config = when {
            useAux -> config.copy(aux = config.aux.copy(cells = config.aux.cells - key))
            else -> config.copy(main = config.main.copy(cells = config.main.cells - key))
        }
        config = config.copy(photoComments = config.photoComments - key)
        // Сдвиг глубже
        val sub = active
        val map = sub.cells.toMutableMap()
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
        config = when {
            useAux -> config.copy(aux = config.aux.copy(cells = map))
            else -> config.copy(main = config.main.copy(cells = map))
        }
        hasUnsavedChanges = true
    }

    fun swapCells(r1: Int, c1: Int, d1: Int, r2: Int, c2: Int, d2: Int) {
        val a = active.cell(r1, c1, d1); val b = active.cell(r2, c2, d2)
        val k1 = active.key(r1, c1, d1); val k2 = active.key(r2, c2, d2)
        val map = active.cells.toMutableMap()
        if (b != null) map[k1] = b else map.remove(k1)
        if (a != null) map[k2] = a else map.remove(k2)
        config = when {
            useAux -> config.copy(aux = config.aux.copy(cells = map))
            else -> config.copy(main = config.main.copy(cells = map))
        }
        // Перекидываем и комментарии фоторежима
        val pc = config.photoComments.toMutableMap()
        val pa = pc[k1]; val pb = pc[k2]
        if (pb != null) pc[k1] = pb else pc.remove(k1)
        if (pa != null) pc[k2] = pa else pc.remove(k2)
        config = config.copy(photoComments = pc)
        hasUnsavedChanges = true
    }

    fun setGlobalViewport(scale: Float, vOx: Float, vOy: Float, r: Int, c: Int, d: Int) {
        config = when {
            useAux -> config.copy(aux = config.aux.copy(
                globalScale = scale, globalOffsetX = vOx, globalOffsetY = vOy,
                lastRow = r, lastCol = c, lastDepth = d))
            else -> config.copy(main = config.main.copy(
                globalScale = scale, globalOffsetX = vOx, globalOffsetY = vOy,
                lastRow = r, lastCol = c, lastDepth = d))
        }
        hasUnsavedChanges = true
    }

    fun updateCell(r: Int, c: Int, d: Int = 0, transform: (CellData) -> CellData) {
        val cur = active.cell(r, c, d) ?: return
        val newCell = transform(cur)
        // Если фоторежим — не трогаем комментарии ячейки
        val toStore = if (photoModeActive) newCell.copy(comments = cur.comments) else newCell
        config = when {
            useAux -> config.copy(aux = config.aux.copy(
                cells = config.aux.cells + (config.aux.key(r, c, d) to toStore)))
            else -> config.copy(main = config.main.copy(
                cells = config.main.cells + (config.main.key(r, c, d) to toStore)))
        }
        hasUnsavedChanges = true
    }

    fun setComments(r: Int, c: Int, comments: List<CommentPin>, d: Int = 0) {
        if (photoModeActive) {
            val key = active.key(r, c, d)
            config = config.copy(photoComments = config.photoComments + (key to comments))
            hasUnsavedChanges = true
        } else {
            updateCell(r, c, d) { it.copy(comments = comments) }
        }
    }

    fun updateLastCell(r: Int, c: Int, d: Int = 0) {
        config = when {
            useAux -> config.copy(aux = config.aux.copy(lastRow = r, lastCol = c, lastDepth = d))
            else -> config.copy(main = config.main.copy(lastRow = r, lastCol = c, lastDepth = d))
        }
        hasUnsavedChanges = true
    }

    fun setRowLabel(r: Int, label: String) {
        config = when {
            useAux -> config.copy(aux = config.aux.copy(
                rowLabels = if (label.isBlank()) config.aux.rowLabels - r
                else config.aux.rowLabels + (r to label)))
            else -> config.copy(main = config.main.copy(
                rowLabels = if (label.isBlank()) config.main.rowLabels - r
                else config.main.rowLabels + (r to label)))
        }
        hasUnsavedChanges = true
    }
    fun setColLabel(c: Int, label: String) {
        config = when {
            useAux -> config.copy(aux = config.aux.copy(
                colLabels = if (label.isBlank()) config.aux.colLabels - c
                else config.aux.colLabels + (c to label)))
            else -> config.copy(main = config.main.copy(
                colLabels = if (label.isBlank()) config.main.colLabels - c
                else config.main.colLabels + (c to label)))
        }
        hasUnsavedChanges = true
    }

    fun setCommentLink(r: Int, c: Int, id: String, link: PinLink?, d: Int = 0) {
        val cur = cell(r, c, d) ?: return
        val updated = cur.comments.map { if (it.id == id) it.copy(link = link) else it }
        setComments(r, c, updated, d)
    }

    fun setIconSettings(size: Float, opacity: Float) {
        config = config.copy(iconSize = size, iconOpacity = opacity)
        hasUnsavedChanges = true
    }

    // ==== Вложенность ====

    /** Переносит (sr,sc,sd) на depth+1 под target (tr,tc). Место освобождается. */
    fun makeNested(sr: Int, sc: Int, sd: Int, tr: Int, tc: Int) {
        if (sr == tr && sc == tc) return
        val src = active.cell(sr, sc, sd) ?: return
        val targetDepth = active.depthAt(tr, tc)
        if (targetDepth < 0) return
        val newDepth = targetDepth + 1
        val srcKey = active.key(sr, sc, sd)
        val map = active.cells.toMutableMap()
        map.remove(srcKey)   // ЯВНО удаляем исходную ячейку
        // Сдвигаем всё, что было глубже newDepth в target
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
        map[active.key(tr, tc, newDepth)] = src
        // Переносим фото-комментарии исходной ячейки под новый ключ
        val pc = config.photoComments.toMutableMap()
        val srcPhoto = pc.remove(srcKey).orEmpty()
        if (srcPhoto.isNotEmpty()) {
            pc[active.key(tr, tc, newDepth)] = srcPhoto
        }
        // lastRow/lastCol — на target, чтобы после переноса не «висеть» на пустой
        config = when {
            useAux -> config.copy(
                aux = config.aux.copy(cells = map, lastRow = tr, lastCol = tc, lastDepth = 0),
                photoComments = pc
            )
            else -> config.copy(
                main = config.main.copy(cells = map, lastRow = tr, lastCol = tc, lastDepth = 0),
                photoComments = pc
            )
        }
        hasUnsavedChanges = true
        autosave()
    }

    fun unnest(sr: Int, sc: Int, sd: Int) {
        if (sd <= 0) return
        val parentDepth = sd - 1
        val child = active.cell(sr, sc, sd) ?: return
        val parent = active.cell(sr, sc, parentDepth) ?: return
        val k1 = active.key(sr, sc, sd)
        val k2 = active.key(sr, sc, parentDepth)
        val map = active.cells.toMutableMap()
        map[k1] = parent
        map[k2] = child
        // Комментарии тоже меняем местами
        val pc = config.photoComments.toMutableMap()
        val pa = pc[k1]; val pb = pc[k2]
        if (pb != null) pc[k1] = pb else pc.remove(k1)
        if (pa != null) pc[k2] = pa else pc.remove(k2)
        config = when {
            useAux -> config.copy(aux = config.aux.copy(cells = map), photoComments = pc)
            else -> config.copy(main = config.main.copy(cells = map), photoComments = pc)
        }
        hasUnsavedChanges = true
        autosave()
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
            aux = fixSub(src.aux)
        )
    }
}