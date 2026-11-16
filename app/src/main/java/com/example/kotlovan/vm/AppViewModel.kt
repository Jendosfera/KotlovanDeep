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
import com.example.kotlovan.data.LinkReturnState
import com.example.kotlovan.data.PinLink
import com.example.kotlovan.data.SubArray
import com.example.kotlovan.data.ThemeProfile

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo = ArrayRepository(app)

    var config by mutableStateOf(ArrayConfig())
        internal set // Открыто для изменения из extension-функций

    var hasUnsavedChanges by mutableStateOf(false)
        internal set

    var boundSaveUri by mutableStateOf<Uri?>(null)
        internal set

    internal var lastChangeTime by mutableLongStateOf(0L)
    internal var firstUnsavedTime by mutableLongStateOf(0L)

    var showUnsavedBanner by mutableStateOf(false)
        internal set

    val unsavedThresholdMs: Long = 5 * 60 * 1000L

    var useAux by mutableStateOf(false)
    var photoModeActive by mutableStateOf(false)

    var linkReturn by mutableStateOf<LinkReturnState?>(null)
    var pendingRejump by mutableStateOf<PinLink?>(null)

    var pendingReconnect by mutableStateOf(emptyList<Pair<Int, Int>>())
        internal set

    var lastRatingRow by mutableStateOf(-1)
    var hiddenRows by mutableStateOf(setOf<Int>())
    var hiddenCols by mutableStateOf(setOf<Int>())

    private val themeRepo = com.example.kotlovan.theme.ThemeRepository(app)

    var themeProfile by mutableStateOf(themeRepo.load())
        internal set

    val active: SubArray
        get() = if (useAux) config.aux else config.main

    init {
        repo.loadAutosave()?.let { config = normalize(it) }
        try {
            val s = app.getSharedPreferences("kotlovan_prefs", 0)
                .getString("bound_save_uri", null)
            if (s != null) {
                val u = Uri.parse(s)
                if (repo.hasPersistablePermission(u)) boundSaveUri = u
            }
        } catch (_: Exception) {}
    }

    fun markChanged() {
        hasUnsavedChanges = true
        val now = System.currentTimeMillis()
        lastChangeTime = now
        if (firstUnsavedTime == 0L) firstUnsavedTime = now
    }

    fun markSaved() {
        hasUnsavedChanges = false
        lastChangeTime = 0L
        firstUnsavedTime = 0L
        showUnsavedBanner = false
    }

    fun updateTheme(profile: ThemeProfile) {
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

    internal fun migrateSub(sub: SubArray): SubArray {
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

    internal fun normalize(src: ArrayConfig): ArrayConfig {
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