package com.example.kotlovan.vm

import com.example.kotlovan.vm.updateCell
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.kotlovan.data.CommentPin
import com.example.kotlovan.data.PinLink

fun AppViewModel.setComments(r: Int, c: Int, comments: List<CommentPin>, d: Int = 0) {
    if (photoModeActive) {
        val key = active.key(r, c, d)
        config = config.copy(photoComments = config.photoComments + (key to comments))
        markChanged()
    } else {
        updateCell(r, c, d) { it.copy(comments = comments) }
    }
}

fun AppViewModel.updatePin(r: Int, c: Int, d: Int, pin: CommentPin) {
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

fun AppViewModel.deletePin(r: Int, c: Int, d: Int, pinId: String) {
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

fun AppViewModel.movePinBetweenStores(r: Int, c: Int, d: Int, pinId: String, toPhoto: Boolean) {
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

fun AppViewModel.setCommentLink(r: Int, c: Int, id: String, link: PinLink?, d: Int = 0) {
    val cur = cell(r, c, d) ?: return
    val updated = cur.comments.map { if (it.id == id) it.copy(link = link) else it }
    setComments(r, c, updated, d)
}