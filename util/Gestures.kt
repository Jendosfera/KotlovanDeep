package com.example.kotlovan.util

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs

/**
 * Двумя пальцами — pan + zoom относительно midpoint (точка под пальцами остаётся под пальцами).
 * Одним пальцем — накапливаем смещение и по завершении вызываем onSwipe, если пройден порог.
 */
suspend fun PointerInputScope.detectViewerGestures(
    onTransform: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
    onSwipe: (dx: Float, dy: Float) -> Unit,
    swipeThreshold: Float = 80f
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var isTransform = false
        var prevCentroid: Offset? = null
        var prevDist = 0f
        var sumDx = 0f
        var sumDy = 0f

        do {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size >= 2) {
                isTransform = true
                val p1 = pressed[0].position
                val p2 = pressed[1].position
                val centroid = (p1 + p2) / 2f
                val dist = (p1 - p2).getDistance()
                if (prevCentroid != null && prevDist > 1f) {
                    val pan = centroid - prevCentroid!!
                    val zoom = dist / prevDist
                    onTransform(centroid, pan, zoom)
                }
                prevCentroid = centroid
                prevDist = dist
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

fun applyZoom(
    scale: Float, offsetX: Float, offsetY: Float,
    centroid: Offset, pan: Offset, zoomFactor: Float,
    minScale: Float = 0.3f, maxScale: Float = 12f
): Triple<Float, Float, Float> {
    val newScale = (scale * zoomFactor).coerceIn(minScale, maxScale)
    val actualZoom = newScale / scale
    // Точка под centroid должна остаться под centroid.
    val newOx = centroid.x - (centroid.x - offsetX) * actualZoom + pan.x
    val newOy = centroid.y - (centroid.y - offsetY) * actualZoom + pan.y
    return Triple(newScale, newOx, newOy)
}