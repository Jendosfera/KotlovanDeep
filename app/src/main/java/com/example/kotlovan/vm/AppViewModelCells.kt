package com.example.kotlovan.vm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.kotlovan.data.CellData
import com.example.kotlovan.data.CommentPin

fun AppViewModel.setBuilderScale(scale: Float) {
    config = when {
        useAux -> config.copy(aux = config.aux.copy(builderScale = scale))
        else -> config.copy(main = config.main.copy(builderScale = scale))
    }
}

fun AppViewModel.upsertCell(r: Int, c: Int, data: CellData, d: Int = 0) {
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

fun AppViewModel.removeCell(r: Int, c: Int, d: Int = 0) {
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

fun AppViewModel.swapCells(r1: Int, c1: Int, d1: Int, r2: Int, c2: Int, d2: Int) {
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
    
    updateLastCell(r2, c2, d2) 
    markChanged()
}

fun AppViewModel.moveTail(sr: Int, sc: Int, sd: Int, tr: Int, tc: Int) {
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
    
    updateLastCell(tr, tc, newBase) 
    markChanged()
}

fun AppViewModel.makeNested(sr: Int, sc: Int, sd: Int, tr: Int, tc: Int) {
    if (sr == tr && sc == tc) return
    if (active.cell(sr, sc, sd) == null) return
    if (active.depthAt(tr, tc) < 0) return
    moveTail(sr, sc, sd, tr, tc)
}

fun AppViewModel.setGlobalViewport(scale: Float, vOx: Float, vOy: Float, r: Int, c: Int, d: Int) {
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

fun AppViewModel.updateCell(r: Int, c: Int, d: Int = 0, transform: (CellData) -> CellData) {
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

fun AppViewModel.updateLastCell(r: Int, c: Int, d: Int = 0) {
    config = when {
        useAux -> config.copy(aux = config.aux.copy(lastRow = r, lastCol = c, lastDepth = d))
        else -> config.copy(main = config.main.copy(lastRow = r, lastCol = c, lastDepth = d))
    }
    hasUnsavedChanges = true
}

fun AppViewModel.setRowLabel(r: Int, label: String) {
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

fun AppViewModel.setColLabel(c: Int, label: String) {
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