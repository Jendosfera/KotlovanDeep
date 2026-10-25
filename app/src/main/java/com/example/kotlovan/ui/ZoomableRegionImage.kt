package com.example.kotlovan.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

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
    var regionBitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
    var regionRect by remember(file) { mutableStateOf<Rect?>(null) }
    var decoder by remember(file) { mutableStateOf<BitmapRegionDecoder?>(null) }
    // Флаг: первый тайл грузим без debounce
    var hasLoadedTile by remember(file) { mutableStateOf(false) }

    LaunchedEffect(file) {
        baseBitmap = withContext(Dispatchers.IO) { decodeSubsampled(file, 2048) }
        decoder = withContext(Dispatchers.IO) {
            try { BitmapRegionDecoder.newInstance(file.absolutePath, false) }
            catch (_: Exception) { null }
        }
    }

    LaunchedEffect(
        file, scale, offsetX, offsetY, fitW, fitH, containerW, containerH, decoder
    ) {
        val dec = decoder ?: return@LaunchedEffect
        if (scale < 1.2f || imageW <= 0 || imageH <= 0) {
            regionBitmap?.recycle()
            regionBitmap = null
            regionRect = null
            return@LaunchedEffect
        }

        // Debounce только для последующих изменений (жест). Первый — сразу.
        if (hasLoadedTile) {
            delay(120)
        }

        val fitScale = fitW / imageW
        val inv = 1f / (fitScale * scale)

        val srcLeft   = ((-offsetX) * inv).coerceIn(0f, imageW.toFloat())
        val srcTop    = ((-offsetY) * inv).coerceIn(0f, imageH.toFloat())
        val srcRight  = ((containerW - offsetX) * inv).coerceIn(0f, imageW.toFloat())
        val srcBottom = ((containerH - offsetY) * inv).coerceIn(0f, imageH.toFloat())

        if (srcRight <= srcLeft || srcBottom <= srcTop) return@LaunchedEffect

        val rect = Rect(
            srcLeft.roundToInt().coerceIn(0, imageW - 1),
            srcTop.roundToInt().coerceIn(0, imageH - 1),
            srcRight.roundToInt().coerceIn(1, imageW),
            srcBottom.roundToInt().coerceIn(1, imageH)
        )
        if (rect.width() <= 0 || rect.height() <= 0) return@LaunchedEffect

        val bmp = withContext(Dispatchers.IO) {
            try { dec.decodeRegion(rect, BitmapFactory.Options().apply { inSampleSize = 1 }) }
            catch (e: kotlinx.coroutines.CancellationException) { null }
			catch (_: Throwable) {null}
		}
        hasLoadedTile = true
        if (bmp != null) {
            regionBitmap?.recycle()
            regionBitmap = bmp
            regionRect = rect
        }
    }

    Canvas(modifier = modifier) {
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            baseBitmap?.let { base ->
                native.save()
                native.translate(offsetX, offsetY)
                native.scale(scale, scale)
                native.drawBitmap(base, null, Rect(0, 0, fitW.roundToInt(), fitH.roundToInt()), null)
                native.restore()
            }
            val region = regionBitmap
            val rect = regionRect
            if (region != null && rect != null) {
                val fitScale = fitW / imageW
                val screenLeft = offsetX + rect.left * fitScale * scale
                val screenTop = offsetY + rect.top * fitScale * scale
                val screenW = rect.width() * fitScale * scale
                val screenH = rect.height() * fitScale * scale
                native.save()
                native.translate(screenLeft, screenTop)
                native.scale(screenW / region.width, screenH / region.height)
                native.drawBitmap(region, 0f, 0f, null)
                native.restore()
            }
        }
    }

    DisposableEffect(file) {
        onDispose {
            try { baseBitmap?.recycle() } catch (_: Throwable) {}
            try { regionBitmap?.recycle() } catch (_: Throwable) {}
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
    return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
}