package com.example.kotlovan.util

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs
import kotlin.math.sqrt

data class Viewport(val scale: Float, val offset: Offset)

suspend fun PointerInputScope.detectViewerGestures(
    getViewport: () -> Viewport,
    fitW: Float, fitH: Float,
    containerW: Float, containerH: Float,
    onTransform: (Viewport) -> Unit,
    onSwipe: (dx: Float, dy: Float) -> Unit,
    onTap: (Offset) -> Unit = {},
    onLongPress: (Offset) -> Unit = {},
    swipeThreshold: Float = 80f,
    tapThreshold: Float = 20f,
    longPressMs: Long = 500,
    minScale: Float = 0.3f,
    maxScale: Float = 12f,
    consumeAll: Boolean = false   // ← новое
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val downTime = System.currentTimeMillis()
        var isTransform = false
        var startVp = Viewport(1f, Offset.Zero)
        var startDist = 1f
        var anchorX = 0f
        var anchorY = 0f
        var sumDx = 0f
        var sumDy = 0f

        do {
            val event = awaitPointerEvent()
            if (consumeAll) event.changes.forEach { it.consume() }   // ← поглощаем для ZoomImage

            val pressed = event.changes.filter { it.pressed }

            if (pressed.size >= 2) {
                val p1 = pressed[0].position
                val p2 = pressed[1].position
                val focal = (p1 + p2) / 2f
                val dist = (p1 - p2).getDistance()

                if (!isTransform) {
                    isTransform = true
                    startVp = getViewport()
                    startDist = dist.coerceAtLeast(1f)
                    anchorX = (focal.x - startVp.offset.x) / startVp.scale
                    anchorY = (focal.y - startVp.offset.y) / startVp.scale
                } else {
                    val newScale = (startVp.scale * (dist / startDist)).coerceIn(minScale, maxScale)
                    var newOx = focal.x - anchorX * newScale
                    var newOy = focal.y - anchorY * newScale
                    newOx = newOx.coerceIn(containerW / 2f - fitW * newScale, containerW / 2f)
                    newOy = newOy.coerceIn(containerH / 2f - fitH * newScale, containerH / 2f)
                    onTransform(Viewport(newScale, Offset(newOx, newOy)))
                }
                pressed.forEach { it.consume() }
            } else if (pressed.size == 1 && !isTransform) {
                val ch = pressed[0]
                sumDx += ch.positionChange().x
                sumDy += ch.positionChange().y
            }
        } while (event.changes.any { it.pressed })

        if (!isTransform) {
            val dist = sqrt(sumDx * sumDx + sumDy * sumDy)
            val elapsed = System.currentTimeMillis() - downTime
            if (dist < tapThreshold) {
                if (elapsed < longPressMs) onTap(down.position)
                else onLongPress(down.position)
            } else if (abs(sumDx) > swipeThreshold || abs(sumDy) > swipeThreshold) {
                onSwipe(sumDx, sumDy)
            }
        }
    }
}

fun clampOffset(
    offset: Offset, scale: Float, fitW: Float, fitH: Float,
    containerW: Float, containerH: Float
): Offset {
    val minX = containerW / 2f - fitW * scale
    val maxX = containerW / 2f
    val minY = containerH / 2f - fitH * scale
    val maxY = containerH / 2f
    return Offset(offset.x.coerceIn(minX, maxX), offset.y.coerceIn(minY, maxY))
}