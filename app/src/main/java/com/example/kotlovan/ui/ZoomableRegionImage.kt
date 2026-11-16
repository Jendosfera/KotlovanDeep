package com.example.kotlovan.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val SPATIAL_TILE_SIZE = 512 // Размер области тайла в пикселях оригинала
private const val MED_SAMPLE = 4          // Пропуск пикселей для среднего разрешения
private const val MAX_SAMPLE = 1          // Пропуск пикселей для максимального разрешения

private data class TileData(val bitmap: Bitmap, val rect: Rect)
private fun getTileKey(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xffffffffL)

@Composable
fun ZoomableRegionImage(
    file: File,
    imageW: Int,
    imageH: Int,
    containerW: Float,
    containerH: Float,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    fitW: Float,
    fitH: Float,
    modifier: Modifier = Modifier
) {
    var baseBitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
    var decoder by remember(file) { mutableStateOf<BitmapRegionDecoder?>(null) }
    
    // Два независимых кэша тайлов
    var medTiles by remember(file) { mutableStateOf<Map<Long, TileData>>(emptyMap()) }
    var maxTiles by remember(file) { mutableStateOf<Map<Long, TileData>>(emptyMap()) }

    // 1. Загрузка базовой картинки и декодера
    LaunchedEffect(file) {
        baseBitmap = withContext(Dispatchers.IO) { decodeSubsampled(file, 2048) }
        decoder = withContext(Dispatchers.IO) {
            try { BitmapRegionDecoder.newInstance(file.absolutePath, false) }
            catch (_: Exception) { null }
        }
    }

    // 2. Загрузка тайлов (сначала средние от центра, затем максимальные от центра)
    LaunchedEffect(
        file, scale, offsetX, offsetY, fitW, fitH, containerW, containerH, decoder
    ) {
        val dec = decoder ?: return@LaunchedEffect
        
        // Дебаунс: ждем 50мс, чтобы не грузить тайлы во время активного жеста
        delay(50)

        if (!scale.isFinite() || scale < 1.1f || imageW <= 0 || imageH <= 0) return@LaunchedEffect
        if (!fitW.isFinite() || fitW <= 0f || !fitH.isFinite() || fitH <= 0f) return@LaunchedEffect

        val currentFitScale = fitW / imageW
        if (!currentFitScale.isFinite() || currentFitScale <= 0f) return@LaunchedEffect
        
        val currentScale = currentFitScale * scale
        val invScale = 1f / currentScale

        // 1. Вычисляем видимую область в координатах оригинала
        val visLeft = (-offsetX * invScale).coerceIn(0f, imageW.toFloat())
        val visTop = (-offsetY * invScale).coerceIn(0f, imageH.toFloat())
        val visRight = ((containerW - offsetX) * invScale).coerceIn(0f, imageW.toFloat())
        val visBottom = ((containerH - offsetY) * invScale).coerceIn(0f, imageH.toFloat())

        if (visRight <= visLeft || visBottom <= visTop) return@LaunchedEffect

        // 2. Запас (2 тайла со всех сторон)
        val pad = SPATIAL_TILE_SIZE * 2
        val startX = (max(0f, visLeft - pad) / SPATIAL_TILE_SIZE).toInt() * SPATIAL_TILE_SIZE
        val startY = (max(0f, visTop - pad) / SPATIAL_TILE_SIZE).toInt() * SPATIAL_TILE_SIZE
        val endX = min(imageW, (min(imageW.toFloat(), visRight + pad) / SPATIAL_TILE_SIZE).toInt() * SPATIAL_TILE_SIZE + SPATIAL_TILE_SIZE)
        val endY = min(imageH, (min(imageH.toFloat(), visBottom + pad) / SPATIAL_TILE_SIZE).toInt() * SPATIAL_TILE_SIZE + SPATIAL_TILE_SIZE)

        // 3. Список нужных тайлов
        val neededTiles = mutableListOf<Pair<Int, Int>>()
        for (x in startX until endX step SPATIAL_TILE_SIZE) {
            for (y in startY until endY step SPATIAL_TILE_SIZE) {
                if (x < imageW && y < imageH) {
                    neededTiles.add(x to y)
                }
            }
        }

        // 4. Сортируем от центра экрана
        val centerX = (containerW / 2f - offsetX) * invScale
        val centerY = (containerH / 2f - offsetY) * invScale
        neededTiles.sortBy { (x, y) ->
            val dx = (x + SPATIAL_TILE_SIZE / 2f) - centerX
            val dy = (y + SPATIAL_TILE_SIZE / 2f) - centerY
            dx * dx + dy * dy
        }

        // 5. ФАЗА 1: Загрузка средних тайлов (очень быстро)
        val currentMed = medTiles.toMutableMap()
        var medChanged = false

        for ((x, y) in neededTiles) {
            val key = getTileKey(x, y)
            if (currentMed.containsKey(key)) continue

            val rect = Rect(x, y, min(x + SPATIAL_TILE_SIZE, imageW), min(y + SPATIAL_TILE_SIZE, imageH))
            val bmp = withContext(Dispatchers.IO) {
                try { 
                    dec.decodeRegion(rect, BitmapFactory.Options().apply { inSampleSize = MED_SAMPLE }) 
                } catch (_: Throwable) { null }
            }
            
            if (bmp != null) {
                currentMed[key] = TileData(bmp, rect)
                medTiles = currentMed.toMap() // Обновляем UI инкрементально
                medChanged = true
            }
        }

        // 6. ФАЗА 2: Загрузка максимальных тайлов (для четкости)
        val currentMax = maxTiles.toMutableMap()
        var maxChanged = false

        for ((x, y) in neededTiles) {
            val key = getTileKey(x, y)
            if (currentMax.containsKey(key)) continue

            val rect = Rect(x, y, min(x + SPATIAL_TILE_SIZE, imageW), min(y + SPATIAL_TILE_SIZE, imageH))
            val bmp = withContext(Dispatchers.IO) {
                try { 
                    dec.decodeRegion(rect, BitmapFactory.Options().apply { inSampleSize = MAX_SAMPLE }) 
                } catch (_: Throwable) { null }
            }
            
            if (bmp != null) {
                currentMax[key] = TileData(bmp, rect)
                maxTiles = currentMax.toMap() // Обновляем UI инкрементально
                maxChanged = true
            }
        }
    }

    // Отрисовка
    Canvas(modifier = modifier) {
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas

            // 1. Всегда рисуем базовую картинку (даже размытую), чтобы не было черного фона
            val base = baseBitmap
            if (base != null) {
                try {
                    native.save()
                    native.translate(offsetX, offsetY)
                    native.scale(scale, scale)
                    native.drawBitmap(base, null, Rect(0, 0, fitW.roundToInt(), fitH.roundToInt()), null)
                    native.restore()
                } catch (_: Throwable) {}
            }

            val currentFitScale = if (imageW > 0) fitW / imageW else 1f
            val currentScale = currentFitScale * scale

            // 2. Рисуем средние тайлы (они покрывают базу)
            for (tile in medTiles.values) {
                val screenLeft = offsetX + tile.rect.left * currentScale
                val screenTop = offsetY + tile.rect.top * currentScale
                val screenW = tile.rect.width() * currentScale
                val screenH = tile.rect.height() * currentScale

                if (screenLeft + screenW < 0 || screenLeft > containerW || 
                    screenTop + screenH < 0 || screenTop > containerH) continue

                if (tile.bitmap.width > 0 && tile.bitmap.height > 0 && screenW.isFinite() && screenH.isFinite()) {
                    try {
                        native.save()
                        native.translate(screenLeft, screenTop)
                        native.scale(screenW / tile.bitmap.width, screenH / tile.bitmap.height)
                        native.drawBitmap(tile.bitmap, 0f, 0f, null)
                        native.restore()
                    } catch (_: Throwable) {}
                }
            }

            // 3. Рисуем максимальные тайлы (они перекрывают средние)
            for (tile in maxTiles.values) {
                val screenLeft = offsetX + tile.rect.left * currentScale
                val screenTop = offsetY + tile.rect.top * currentScale
                val screenW = tile.rect.width() * currentScale
                val screenH = tile.rect.height() * currentScale

                if (screenLeft + screenW < 0 || screenLeft > containerW || 
                    screenTop + screenH < 0 || screenTop > containerH) continue

                if (tile.bitmap.width > 0 && tile.bitmap.height > 0 && screenW.isFinite() && screenH.isFinite()) {
                    try {
                        native.save()
                        native.translate(screenLeft, screenTop)
                        native.scale(screenW / tile.bitmap.width, screenH / tile.bitmap.height)
                        native.drawBitmap(tile.bitmap, 0f, 0f, null)
                        native.restore()
                    } catch (_: Throwable) {}
                }
            }
        }
    }

    // Очистка памяти ТОЛЬКО при смене файла (переключении картинки)
    DisposableEffect(file) {
        onDispose {
            try { baseBitmap?.recycle() } catch (_: Throwable) {}
            medTiles.values.forEach { try { it.bitmap.recycle() } catch (_: Throwable) {} }
            maxTiles.values.forEach { try { it.bitmap.recycle() } catch (_: Throwable) {} }
            decoder = null
        }
    }
}

private fun decodeSubsampled(file: File, maxSize: Int): Bitmap? {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, opts)
    if (opts.outWidth <= 0) return null
    var sample = 1
    while (opts.outWidth / sample > maxSize || opts.outHeight / sample > maxSize) sample *= 2
    return try {
        BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    } catch (_: Throwable) { null }
}