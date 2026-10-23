package com.example.kotlovan.util

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs

data class Viewport(val scale: Float, val offset: Offset)

suspend fun PointerInputScope.detectViewerGestures(
    getViewport: () -> Viewport,
    fitW: Float,
    fitH: Float,
    containerW: Float,
    containerH: Float,
    onTransform: (Viewport) -> Unit,
    onSwipe: (dx: Float, dy: Float) -> Unit,
    swipeThreshold: Float = 80f,
    minScale: Float = 0.3f,
    maxScale: Float = 12f
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)

        var isTransform = false
        var startVp = Viewport(1f, Offset.Zero)
        var startDist = 1f
        var anchorX = 0f   // точка в fit-пикселях под пальцами в момент старта
        var anchorY = 0f
        var sumDx = 0f
        var sumDy = 0f

        do {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }

            if (pressed.size >= 2) {
                val p1 = pressed[0].position
                val p2 = pressed[1].position
                val focal = (p1 + p2) / 2f
                val dist = (p1 - p2).getDistance()

                if (!isTransform) {
                    // Начало жеста: запоминаем состояние и вычисляем "якорь"
                    isTransform = true
                    startVp = getViewport()
                    startDist = dist.coerceAtLeast(1f)
                    anchorX = (focal.x - startVp.offset.x) / startVp.scale
                    anchorY = (focal.y - startVp.offset.y) / startVp.scale
                } else {
                    // Масштаб = dist / startDist  (п.1 ТЗ)
                    val newScale = (startVp.scale * (dist / startDist)).coerceIn(minScale, maxScale)

                    // Якорь остаётся под текущим midpoint (п.2 ТЗ)
                    var newOx = focal.x - anchorX * newScale
                    var newOy = focal.y - anchorY * newScale

                    // Края не уходят за центр viewport
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

        if (!isTransform && (abs(sumDx) > swipeThreshold || abs(sumDy) > swipeThreshold)) {
            onSwipe(sumDx, sumDy)
        }
    }
}

/** Приводит offset к допустимому диапазону для заданного масштаба. */
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