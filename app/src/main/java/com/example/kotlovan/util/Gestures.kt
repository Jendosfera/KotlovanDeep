package com.example.kotlovan.util

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.withTimeoutOrNull
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
    longPressMs: Long = 500L,
    minScale: Float = 0.3f,
    maxScale: Float = 12f
) {
    // Защита от невалидных границ
    val safeMinScale = if (!minScale.isFinite() || minScale <= 0f) 0.3f else minScale
    val safeMaxScale = if (!maxScale.isFinite() || maxScale <= safeMinScale) safeMinScale * 10f else maxScale

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val startTime = System.currentTimeMillis()
        var isTransform = false
        var didPan = false
        var longPressFired = false
        var startVp = Viewport(1f, Offset.Zero)
        var startDist = 1f
        var anchorX = 0f
        var anchorY = 0f
        var sumDx = 0f
        var sumDy = 0f
        var active = true

        while (active) {
            val elapsed = System.currentTimeMillis() - startTime
            val remaining = longPressMs - elapsed
            // Ждём следующего pointer-события, но не дольше оставшегося времени
            // до срабатывания long press. Если long press уже сработал или жест
            // превратился в transform/pan — ждём бесконечно.
            val timeout = if (!longPressFired && !isTransform && !didPan)
                remaining.coerceAtLeast(1L)
            else Long.MAX_VALUE

            val event = withTimeoutOrNull(timeout) { awaitPointerEvent() }

            if (event == null) {
                // Таймаут — сработал long press, не дожидаясь отпускания пальца
                if (!longPressFired && !isTransform && !didPan) {
                    longPressFired = true
                    onLongPress(down.position)
                }
                continue
            }

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
                    val rawScale = startVp.scale * (dist / startDist)
                    val newScale = rawScale.coerceIn(safeMinScale, safeMaxScale)

                    var newOx = focal.x - anchorX * newScale
                    var newOy = focal.y - anchorY * newScale

                    // Защита от NaN/Inf
                    if (!newOx.isFinite()) newOx = startVp.offset.x
                    if (!newOy.isFinite()) newOy = startVp.offset.y
                    if (!newScale.isFinite() || newScale <= 0f) {
                        newOx = startVp.offset.x
                        newOy = startVp.offset.y
                    }

                    if (fitW > 0f && fitW.isFinite()) {
                        newOx = newOx.coerceIn(containerW / 2f - fitW * newScale, containerW / 2f)
                    }
                    if (fitH > 0f && fitH.isFinite()) {
                        newOy = newOy.coerceIn(containerH / 2f - fitH * newScale, containerH / 2f)
                    }

                    onTransform(Viewport(newScale, Offset(newOx, newOy)))
                }
                pressed.forEach { it.consume() }
            } else if (pressed.size == 1 && !isTransform) {
                val ch = pressed[0]
                val dx = ch.positionChange().x
                val dy = ch.positionChange().y
                val vp = getViewport()

                if (alwaysPanOneFinger || vp.scale > 1.05f) {
                    // При увеличении один палец = панорамирование
                    if (abs(dx) > 0.5f || abs(dy) > 0.5f) {
                        didPan = true
                        var newOx = vp.offset.x + dx
                        var newOy = vp.offset.y + dy
                        if (!newOx.isFinite()) newOx = vp.offset.x
                        if (!newOy.isFinite()) newOy = vp.offset.y
                        if (fitW > 0f && fitW.isFinite()) {
                            newOx = newOx.coerceIn(containerW / 2f - fitW * vp.scale, containerW / 2f)
                        }
                        if (fitH > 0f && fitH.isFinite()) {
                            newOy = newOy.coerceIn(containerH / 2f - fitH * vp.scale, containerH / 2f)
                        }
                        onTransform(Viewport(vp.scale, Offset(newOx, newOy)))
                    }
                } else {
                    // Без увеличения — копим смещение для свайпа
                    sumDx += dx
                    sumDy += dy
                }
            }

            if (event.changes.none { it.pressed }) active = false
        }

        // Обработка завершения жеста (tap / swipe) если это не был transform/pan/long
        if (!isTransform && !didPan && !longPressFired) {
            val dist = sqrt(sumDx * sumDx + sumDy * sumDy)
            if (!dist.isFinite()) return@awaitEachGesture

            if (dist < tapThreshold) {
                onTap(down.position)
            } else if (abs(sumDx) > swipeThreshold || abs(sumDy) > swipeThreshold) {
                if (sumDx.isFinite() && sumDy.isFinite()) {
                    onSwipe(sumDx, sumDy)
                }
            }
        }
    }
}

fun clampOffset(
    offset: Offset, scale: Float, fitW: Float, fitH: Float,
    containerW: Float, containerH: Float
): Offset {
    // Защита от NaN/Inf во всех аргументах
    val s = if (!scale.isFinite() || scale <= 0f) 1f else scale
    val fw = if (!fitW.isFinite() || fitW <= 0f) containerW else fitW
    val fh = if (!fitH.isFinite() || fitH <= 0f) containerH else fitH
    val cw = if (!containerW.isFinite() || containerW <= 0f) 1f else containerW
    val ch = if (!containerH.isFinite() || containerH <= 0f) 1f else containerH

    val minX = cw / 2f - fw * s
    val maxX = cw / 2f
    val minY = ch / 2f - fh * s
    val maxY = ch / 2f

    val ox = if (!offset.x.isFinite()) 0f else offset.x
    val oy = if (!offset.y.isFinite()) 0f else offset.y

    // coerceIn требует min <= max, иначе кинет IllegalArgumentException
    val safeMinX = minOf(minX, maxX)
    val safeMaxX = maxOf(minX, maxX)
    val safeMinY = minOf(minY, maxY)
    val safeMaxY = maxOf(minY, maxY)

    return Offset(
        ox.coerceIn(safeMinX, safeMaxX),
        oy.coerceIn(safeMinY, safeMaxY)
    )
}
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
    longPressMs: Long = 500L,
    minScale: Float = 0.3f,
    maxScale: Float = 12f,
    alwaysPanOneFinger: Boolean = false   // ← НОВОЕ
) {
    // ...
    // в блоке else if (pressed.size == 1 && !isTransform) { ... }
    // заменить условие:
    if (alwaysPanOneFinger || vp.scale > 1.05f) {
        // панорамирование одним пальцем
        ...
    } else {
        // свайп (накапливаем sumDx/sumDy)
        sumDx += dx
        sumDy += dy
    }
    // ...
}