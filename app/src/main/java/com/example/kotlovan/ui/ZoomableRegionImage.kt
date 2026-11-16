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
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val TILE_SIZE = 512 // Размер плитки в пикселях оригинала
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
    
    // Кэш тайлов: Ключ -> Данные (битмап + его координаты в оригинале)
    var tiles by remember(file) { mutableStateOf<Map<Long, TileData>>(emptyMap()) }

    // 1. Загрузка базовой картинки (для сильного отдаления)
    LaunchedEffect(file) {
        baseBitmap = withContext(Dispatchers.IO) { decodeSubsampled(file, 2048) }
        decoder = withContext(Dispatchers.IO) {
            try { BitmapRegionDecoder.newInstance(file.absolutePath, false) }
            catch (_: Exception) { null }
        }
    }

    // 2. Умная загрузка тайлов (от центра)
    LaunchedEffect(
        file, scale, offsetX, offsetY, fitW, fitH, containerW, containerH, decoder
    ) {
        val dec = decoder ?: return@LaunchedEffect
        
        // Если отдалили очень сильно, тайлы не нужны — используем baseBitmap
        if (!scale.isFinite() || scale < 1.2f || imageW <= 0 || imageH <= 0) {
            if (tiles.isNotEmpty()) {
                tiles.values.forEach { it.bitmap.recycle() }
                tiles = emptyMap()
            }
            return@LaunchedEffect
        }
        
        if (!fitW.isFinite() || fitW <= 0f || !fitH.isFinite() || fitH <= 0f) return@LaunchedEffect

        val currentFitScale = fitW / imageW
        if (!currentFitScale.isFinite() || currentFitScale <= 0f) return@LaunchedEffect
        
        // Масштаб, с которым оригинал отображается на экране
        val currentScale = currentFitScale * scale
        val invScale = 1f / currentScale

        // 1. Вычисляем видимую область в координатах оригинального изображения
        val visLeft = (-offsetX * invScale).coerceIn(0f, imageW.toFloat())
        val visTop = (-offsetY * invScale).coerceIn(0f, imageH.toFloat())
        val visRight = ((containerW - offsetX) * invScale).coerceIn(0f, imageW.toFloat())
        val visBottom = ((containerH - offsetY) * invScale).coerceIn(0f, imageH.toFloat())

        // Если видимая область вырождена или за пределами картинки
        if (visRight <= visLeft || visBottom <= visTop) return@LaunchedEffect

        // 2. Добавляем запас (2 тайла со всех сторон), чтобы при панорамировании не было белых дыр
        val pad = TILE_SIZE * 2
        val startX = (max(0f, visLeft - pad) / TILE_SIZE).toInt() * TILE_SIZE
        val startY = (max(0f, visTop - pad) / TILE_SIZE).toInt() * TILE_SIZE
        val endX = min(imageW, (min(imageW.toFloat(), visRight + pad) / TILE_SIZE).toInt() * TILE_SIZE + TILE_SIZE)
        val endY = min(imageH, (min(imageH.toFloat(), visBottom + pad) / TILE_SIZE).toInt() * TILE_SIZE + TILE_SIZE)

        // 3. Формируем список нужных тайлов
        val neededTiles = mutableListOf<Pair<Int, Int>>()
        for (x in startX until endX step TILE_SIZE) {
            for (y in startY until endY step TILE_SIZE) {
                if (x < imageW && y < imageH) {
                    neededTiles.add(x to y)
                }
            }
        }

        // 4. Сортируем по удаленности от центра видимой области
        val centerX = (containerW / 2f - offsetX) * invScale
        val centerY = (containerH / 2f - offsetY) * invScale
        neededTiles.sortBy { (x, y) ->
            val tileCX = x + TILE_SIZE / 2f
            val tileCY = y + TILE_SIZE / 2f
            val dx = tileCX - centerX
            val dy = tileCY - centerY
            dx * dx + dy * dy
        }

        // 5. Загружаем недостающие тайлы
        val newCache = tiles.toMutableMap()
        var cacheChanged = false
        
        // Сначала удаляем тайлы, которые больше не нужны (экономия RAM)
        val neededKeys = neededTiles.map { (x, y) -> getTileKey(x, y) }.toSet()
        val keysToRemove = newCache.keys.filter { it !in neededKeys }
        for (key in keysToRemove) {
            newCache.remove(key)?.bitmap?.recycle()
            cacheChanged = true
        }

        // Затем догружаем новые (начиная с самых близких к центру)
        for ((x, y) in neededTiles) {
            val key = getTileKey(x, y)
            if (newCache.containsKey(key)) continue // уже в памяти

            val rect = Rect(x, y, min(x + TILE_SIZE, imageW), min(y + TILE_SIZE, imageH))
            val bmp = withContext(Dispatchers.IO) {
                try { 
                    dec.decodeRegion(rect, BitmapFactory.Options().apply { inSampleSize = 1 }) 
                } catch (_: Throwable) { 
                    null 
                }
            }
            
            if (bmp != null) {
                newCache[key] = TileData(bmp, rect)
                // Обновляем стейт после каждого тайла, чтобы UI оживал инкрементально
                tiles = newCache.toMap()
            }
        }
        
        if (cacheChanged) {
            tiles = newCache.toMap()
        }
    }

    // Отрисовка
    Canvas(modifier = modifier) {
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas

            // Если сильно отдалили — рисуем размытую базовую картинку
            if (scale < 1.2f) {
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
            } else {
                // Рисуем тайлы
                val currentFitScale = if (imageW > 0) fitW / imageW else 1f
                val currentScale = currentFitScale * scale
                
                for (tile in tiles.values) {
                    val screenLeft = offsetX + tile.rect.left * currentScale
                    val screenTop = offsetY + tile.rect.top * currentScale
                    val screenW = tile.rect.width() * currentScale
                    val screenH = tile.rect.height() * currentScale
                    
                    // Отсечение невидимых тайлов (Culling)
                    if (screenLeft + screenW < 0 || screenLeft > containerW || 
                        screenTop + screenH < 0 || screenTop > containerH) continue

                    if (tile.bitmap.width > 0 && tile.bitmap.height > 0 &&
                        screenW.isFinite() && screenH.isFinite()) {
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
    }

    // Очистка памяти при уничтожении
    DisposableEffect(file) {
        onDispose {
            try { baseBitmap?.recycle() } catch (_: Throwable) {}
            tiles.values.forEach { try { it.bitmap.recycle() } catch (_: Throwable) {} }
            // НЕ вызываем decoder.recycle() — native-крэш при параллельном decodeRegion
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