package com.example.kotlovan.vm

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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

    /** URI последнего сохранения в ZIP. Восстанавливается между запусками. */
    var boundSaveUri by mutableStateOf<Uri?>(null)
        private set

    /** Момент последнего изменения. 0L = нет несохранённых. */
    private var lastChangeTime by mutableLongStateOf(0L)

    /** Момент первого изменения после последнего сохранения. 0L = нет. */
    private var firstUnsavedTime by mutableLongStateOf(0L)

    /** Показывать баннер «есть несохранённые изменения». */
    var showUnsavedBanner by mutableStateOf(false)
        private set

    /** Порог для автосохранения и баннера. */
    val unsavedThresholdMs: Long = 5 * 60 * 1000L

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
        get() = if (useAux) config.aux else config.main

    init {
        repo.loadAutosave()?.let { config = normalize(it) }
        // Восстановить boundSaveUri, если он был
        try {
            val s = app.getSharedPreferences("kotlovan_prefs", 0)
                .getString("bound_save_uri", null)
            if (s != null) {
                val u = Uri.parse(s)
                if (repo.hasPersistablePermission(u)) boundSaveUri = u
            }
        } catch (_: Exception) {}
    }

    /** Вызывается при каждом изменении config. */
    fun markChanged() {
        hasUnsavedChanges = true
        val now = System.currentTimeMillis()
        lastChangeTime = now
        if (firstUnsavedTime == 0L) firstUnsavedTime = now
    }

    /** Сбрасывает счётчики — после сохранения. */
    fun markSaved() {
        hasUnsavedChanges = false
        lastChangeTime = 0L
        firstUnsavedTime = 0L
        showUnsavedBanner = false
    }

    /**
     * Периодический тик — вызывается из UI раз в 30 сек.
     * Обновляет `showUnsavedBanner` и (если условия) вызывает автосохранение.
     */
    fun tickUnsavedMonitor() {
        if (!hasUnsavedChanges) return
        val now = System.currentTimeMillis()
        val firstDelta = if (firstUnsavedTime > 0) now - firstUnsavedTime else 0L
        val lastDelta = if (lastChangeTime > 0) now - lastChangeTime else 0L

        // Баннер: с первого несохранённого изменения > 5 мин
        showUnsavedBanner = firstDelta > unsavedThresholdMs

        // Автосохранение: с последнего изменения > 5 мин И с первого > 5 мин
        if (lastDelta > unsavedThresholdMs && firstDelta > unsavedThresholdMs) {
            saveCurrentUri()
        }
    }

    /**
     * Сохранить в текущий boundSaveUri. Если URI нет — false.
     * Возвращает true, если запись прошла.
     */
    fun saveCurrentUri(): Boolean {
        val uri = boundSaveUri ?: return false
        return try {
            val finalConfig = repo.saveAsZip(config, uri) ?: return false
            config = finalConfig
            repo.autosave(config)
            markSaved()
            true
        } catch (_: Throwable) { false }
    }

    /**
     * Пользователь выбрал URI через SAF и хочет сохранить.
     * Присваивает boundSaveUri и пишет ZIP.
     */
    fun onUserSave(uri: Uri): Boolean {
        return try {
            val finalConfig = repo.saveAsZip(config, uri) ?: return false
            config = finalConfig
            boundSaveUri = uri
            persistBoundUri(uri)
            repo.autosave(config)
            markSaved()
            true
        } catch (_: Throwable) { false }
    }

    private fun persistBoundUri(uri: Uri) {
        try {
            getApplication<Application>()
                .getSharedPreferences("kotlovan_prefs", 0)
                .edit().putString("bound_save_uri", uri.toString()).apply()
        } catch (_: Exception) {}
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
        boundSaveUri = null
        markSaved()
    }

    fun load(uri: Uri): Boolean {
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
        markChanged()
    }

    /** Сохранить по URI (SAF). Обновляет boundSaveUri. */
    fun save(uri: Uri): Boolean = onUserSave(uri)

    fun savePhotoArchive(uri: Uri): Boolean = onUserSave(uri)

    fun loadPhotoArchive(uri: Uri): Boolean {
        val loaded = repo.loadFromZip(uri) ?: return false
        config = normalize(loaded)
        boundSaveUri = uri
        persistBoundUri(uri)
        markSaved()
        return true
    }

    fun generatePhotoReport(selectedPins: Set<String>, from: Long, to: Long, uri: Uri): Boolean {
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

    /** Только SharedPreferences. */
    fun autosave() { repo.autosave(config) }

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
        markChanged()
    }

    fun removeCell(r: Int, c: Int, d: Int = 0) {
        val key = active.key(r, c, d)
        val map = active.cells.toMutableMap()
        val pc = config.photoComments.toMutableMap()
        map.remove(key); pc.remove(key)
        val deeper = map.keys.filter {
            val p = it.split(",")
            p.size == 3 && p[0].toIntOrNull() == r && p[1].toIntOrNull() == c &&
                    (p[2].toIntOrNull() ?: 0) > d
        }.sortedBy { it.split(",")[2].toIntOrNull() ?: 0 }
        for (k in deeper) {
            val p = k.split(",")
            val dd = p[2].toIntOrNull() ?: 0
            val v = map.remove(k) ?: continue
            map["${r},${c},${dd - 1}"] = v
            pc.remove(k)?.let { pc["${r},${c},${dd - 1}"] = it }
        }
        config = when {
            useAux -> config.copy(aux = config.aux.copy(cells = map), photoComments = pc)
            else -> config.copy(main = config.main.copy(cells = map), photoComments = pc)
        }
        markChanged()
    }

    fun swapCells(r1: Int, c1: Int, d1: Int, r2: Int, c2: Int, d2: Int) {
        val map = active.cells.toMutableMap()
        val pc = config.photoComments.toMutableMap()

        val tail1 = mutableListOf<Pair<CellData, List<CommentPin>?>>()
        var d = d1
        while (true) {
            val key = active.key(r1, c1, d)
            val v = map.remove(key) ?: break
            tail1 += v to pc.remove(key)
            d++
        }
        val tail2 = mutableListOf<Pair<CellData, List<CommentPin>?>>()
        d = d2
        while (true) {
            val key = active.key(r2, c2, d)
            val v = map.remove(key) ?: break
            tail2 += v to pc.remove(key)
            d++
        }
        for ((i, pair) in tail2.withIndex()) {
            val key = active.key(r1, c1, d1 + i)
            map[key] = pair.first
            pair.second?.let { pc[key] = it }
        }
        for ((i, pair) in tail1.withIndex()) {
            val key = active.key(r2, c2, d2 + i)
            map[key] = pair.first
            pair.second?.let { pc[key] = it }
        }
        config = when {
            useAux -> config.copy(aux = config.aux.copy(cells = map), photoComments = pc)
            else -> config.copy(main = config.main.copy(cells = map), photoComments = pc)
        }
        markChanged()
    }

    fun moveTail(sr: Int, sc: Int, sd: Int, tr: Int, tc: Int) {
        if (sr == tr && sc == tc) return

        val map = active.cells.toMutableMap()
        val pc = config.photoComments.toMutableMap()

        val chain = mutableListOf<Pair<CellData, List<CommentPin>?>>()
        var d = sd
        while (true) {
            val key = active.key(sr, sc, d)
            val v = map.remove(key) ?: break
            chain += v to pc.remove(key)
            d++
        }
        if (chain.isEmpty()) {
            config = when {
                useAux -> config.copy(aux = config.aux.copy(cells = map), photoComments = pc)
                else -> config.copy(main = config.main.copy(cells = map), photoComments = pc)
            }
            return
        }

        var targetMax = -1
        var k = 0
        while (map.containsKey(active.key(tr, tc, k))) {
            targetMax = k
            k++
        }
        val newBase = targetMax + 1

        for ((i, pair) in chain.withIndex()) {
            val key = active.key(tr, tc, newBase + i)
            map[key] = pair.first
            pair.second?.let { pc[key] = it }
        }

        config = when {
            useAux -> config.copy(aux = config.aux.copy(cells = map), photoComments = pc)
            else -> config.copy(main = config.main.copy(cells = map), photoComments = pc)
        }
        markChanged()
    }

    fun makeNested(sr: Int, sc: Int, sd: Int, tr: Int, tc: Int) {
        if (sr == tr && sc == tc) return
        if (active.cell(sr, sc, sd) == null) return
        if (active.depthAt(tr, tc) < 0) return
        moveTail(sr, sc, sd, tr, tc)
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
        // Viewport не считаем «несохранённым изменением» для баннера — это не критично
        hasUnsavedChanges = true
    }

    fun updateCell(r: Int, c: Int, d: Int = 0, transform: (CellData) -> CellData) {
        val cur = active.cell(r, c, d) ?: return
        val newCell = transform(cur)
        val toStore = if (photoModeActive) newCell.copy(comments = cur.comments) else newCell
        config = when {
            useAux -> config.copy(aux = config.aux.copy(
                cells = config.aux.cells + (config.aux.key(r, c, d) to toStore)))
            else -> config.copy(main = config.main.copy(
                cells = config.main.cells + (config.main.key(r, c, d) to toStore)))
        }
        markChanged()
    }

    fun setComments(r: Int, c: Int, comments: List<CommentPin>, d: Int = 0) {
        if (photoModeActive) {
            val key = active.key(r, c, d)
            config = config.copy(photoComments = config.photoComments + (key to comments))
            markChanged()
        } else {
            updateCell(r, c, d) { it.copy(comments = comments) }
        }
    }

    fun updatePin(r: Int, c: Int, d: Int, pin: CommentPin) {
        val key = active.key(r, c, d)
        val sub = active
        val cell = sub.cell(r, c, d) ?: return

        val inPhoto = config.photoComments[key]?.any { it.id == pin.id } == true
        val inNormal = cell.comments.any { it.id == pin.id }

        when {
            inPhoto -> {
                val photo = config.photoComments[key].orEmpty().map {
                    if (it.id == pin.id) pin else it
                }
                config = config.copy(photoComments = config.photoComments + (key to photo))
            }
            inNormal -> {
                val map = sub.cells.toMutableMap()
                map[sub.key(r, c, d)] = cell.copy(comments = cell.comments.map {
                    if (it.id == pin.id) pin else it
                })
                config = when {
                    useAux -> config.copy(aux = config.aux.copy(cells = map))
                    else -> config.copy(main = config.main.copy(cells = map))
                }
            }
            else -> {
                if (photoModeActive) {
                    val photo = config.photoComments[key].orEmpty() + pin
                    config = config.copy(photoComments = config.photoComments + (key to photo))
                } else {
                    val map = sub.cells.toMutableMap()
                    map[sub.key(r, c, d)] = cell.copy(comments = cell.comments + pin)
                    config = when {
                        useAux -> config.copy(aux = config.aux.copy(cells = map))
                        else -> config.copy(main = config.main.copy(cells = map))
                    }
                }
            }
        }
        markChanged()
    }

    fun deletePin(r: Int, c: Int, d: Int, pinId: String) {
        val key = active.key(r, c, d)
        val sub = active
        val cell = sub.cell(r, c, d) ?: return

        val inPhoto = config.photoComments[key]?.any { it.id == pinId } == true
        val inNormal = cell.comments.any { it.id == pinId }

        if (inPhoto) {
            val photo = config.photoComments[key].orEmpty().filterNot { it.id == pinId }
            config = config.copy(photoComments = config.photoComments + (key to photo))
        }
        if (inNormal) {
            val map = sub.cells.toMutableMap()
            map[sub.key(r, c, d)] = cell.copy(comments = cell.comments.filterNot { it.id == pinId })
            config = when {
                useAux -> config.copy(aux = config.aux.copy(cells = map))
                else -> config.copy(main = config.main.copy(cells = map))
            }
        }
        markChanged()
    }

    fun movePinBetweenStores(r: Int, c: Int, d: Int, pinId: String, toPhoto: Boolean) {
        val key = active.key(r, c, d)
        val sub = active
        val cell = sub.cell(r, c, d) ?: return

        if (toPhoto) {
            val pin = cell.comments.firstOrNull { it.id == pinId } ?: return
            val map = sub.cells.toMutableMap()
            map[sub.key(r, c, d)] = cell.copy(comments = cell.comments.filterNot { it.id == pinId })
            config = when {
                useAux -> config.copy(aux = config.aux.copy(cells = map))
                else -> config.copy(main = config.main.copy(cells = map))
            }
            val photo = config.photoComments[key].orEmpty()
            config = config.copy(photoComments = config.photoComments + (key to (photo + pin)))
        } else {
            val pin = config.photoComments[key]?.firstOrNull { it.id == pinId } ?: return
            val photo = config.photoComments[key].orEmpty().filterNot { it.id == pinId }
            config = config.copy(photoComments = config.photoComments + (key to photo))
            val map = sub.cells.toMutableMap()
            map[sub.key(r, c, d)] = cell.copy(comments = cell.comments + pin)
            config = when {
                useAux -> config.copy(aux = config.aux.copy(cells = map))
                else -> config.copy(main = config.main.copy(cells = map))
            }
        }
        markChanged()
    }

    fun updateLastCell(r: Int, c: Int, d: Int = 0) {
        config = when {
            useAux -> config.copy(aux = config.aux.copy(lastRow = r, lastCol = c, lastDepth = d))
            else -> config.copy(main = config.main.copy(lastRow = r, lastCol = c, lastDepth = d))
        }
        // lastRow/lastCol — не критичные изменения для баннера.
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
        markChanged()
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
        markChanged()
    }

    fun setCommentLink(r: Int, c: Int, id: String, link: PinLink?, d: Int = 0) {
        val cur = cell(r, c, d) ?: return
        val updated = cur.comments.map { if (it.id == id) it.copy(link = link) else it }
        setComments(r, c, updated, d)
    }

    fun setIconSettings(size: Float, opacity: Float) {
        config = config.copy(iconSize = size, iconOpacity = opacity)
        markChanged()
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
        return src.copy(main = fixSub(src.main), aux = fixSub(src.aux))
    }
}