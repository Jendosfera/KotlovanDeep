package com.example.kotlovan.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.kotlovan.data.CellData
import com.example.kotlovan.util.Viewport
import com.example.kotlovan.util.detectViewerGestures
import com.example.kotlovan.vm.AppViewModel
import kotlin.math.min

@Composable
internal fun MoveCommentOverlay(
    cell: CellData, vm: AppViewModel,
    initialX: Float, initialY: Float,
    onCancel: () -> Unit,
    onConfirm: (nx: Float, ny: Float) -> Unit
) {
    var mScale by remember { mutableFloatStateOf(1f) }
    var mOffset by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val containerW = constraints.maxWidth.toFloat()
        val containerH = constraints.maxHeight.toFloat()
        val density = LocalDensity.current
        val imageW = cell.imageWidth
        val imageH = cell.imageHeight
        val fitScale = if (imageW > 0 && imageH > 0) min(containerW / imageW, containerH / imageH) else 1f
        val fitW = if (imageW > 0) imageW * fitScale else containerW
        val fitH = if (imageH > 0) imageH * fitScale else containerH

        LaunchedEffect(fitW, fitH) {
            mScale = 1f
            mOffset = Offset(containerW / 2f - initialX * fitW, containerH / 2f - initialY * fitH)
        }

        Box(
            Modifier.fillMaxSize().clipToBounds()
                .pointerInput(fitW, fitH) {
                    detectViewerGestures(
                        getViewport = { Viewport(mScale, mOffset) },
                        fitW = fitW, fitH = fitH,
                        containerW = containerW, containerH = containerH,
                        onTransform = { vp -> mScale = vp.scale; mOffset = vp.offset },
                        onSwipe = { _, _ -> }
                    )
                }
        ) {
            val f = cell.imageFile
            if (f != null) {
                AsyncImage(
                    model = vm.repo.imageFile(f),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .size(with(density) { fitW.toDp() }, with(density) { fitH.toDp() })
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0f, 0f)
                            translationX = mOffset.x; translationY = mOffset.y
                            scaleX = mScale; scaleY = mScale
                        }
                )
            }
        }

        // Прицел в центре
        Canvas(Modifier.align(Alignment.Center).size(48.dp)) {
            val cx = size.width / 2
            val cy = size.height / 2
            val stroke = 2.dp.toPx()
            val short = 8.dp.toPx()
            val long = 20.dp.toPx()
            drawLine(Color.Black, Offset(cx - long, cy), Offset(cx - short, cy), stroke)
            drawLine(Color.Black, Offset(cx + short, cy), Offset(cx + long, cy), stroke)
            drawLine(Color.Black, Offset(cx, cy - long), Offset(cx, cy - short), stroke)
            drawLine(Color.Black, Offset(cx, cy + short), Offset(cx, cy + long), stroke)
            drawCircle(Color.Black, radius = 3.dp.toPx(), center = Offset(cx, cy))
        }

        IconButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
        ) { Icon(Icons.Default.ArrowBack, "Отмена", tint = Color.White) }

        IconButton(
            onClick = {
                val imgX = (containerW / 2f - mOffset.x) / mScale.coerceAtLeast(0.01f)
                val imgY = (containerH / 2f - mOffset.y) / mScale.coerceAtLeast(0.01f)
                onConfirm(
                    (imgX / fitW).coerceIn(0f, 1f),
                    (imgY / fitH).coerceIn(0f, 1f)
                )
            },
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
        ) { Icon(Icons.Default.Check, "OK", tint = Color.White) }
    }
}