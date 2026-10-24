package com.example.kotlovan.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.kotlovan.data.PinLink
import com.example.kotlovan.util.Viewport
import com.example.kotlovan.util.detectViewerGestures
import com.example.kotlovan.vm.AppViewModel
import kotlin.math.min

@Composable
fun LinkPickerScreen(
    nav: NavHostController,
    vm: AppViewModel,
    comRow: Int,
    comCol: Int,
    commentId: String
) {
    var row by remember { mutableStateOf(vm.active.lastRow) }
    var col by remember { mutableStateOf(vm.active.lastCol) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val containerW = constraints.maxWidth.toFloat()
        val containerH = constraints.maxHeight.toFloat()
        val density = LocalDensity.current

        val currentCell = vm.cell(row, col)
        val imageW = currentCell?.imageWidth ?: 0
        val imageH = currentCell?.imageHeight ?: 0
        val fitScale = if (imageW > 0 && imageH > 0) min(containerW / imageW, containerH / imageH) else 1f
        val fitW = if (imageW > 0) imageW * fitScale else containerW
        val fitH = if (imageH > 0) imageH * fitScale else containerH

        LaunchedEffect(row, col, fitW, fitH) {
            val c = vm.cell(row, col) ?: return@LaunchedEffect
            scale = if (c.ownPosition) c.ownScale else vm.active.globalScale
            val vOx = if (c.ownPosition) c.ownOffsetX else vm.active.globalOffsetX
            val vOy = if (c.ownPosition) c.ownOffsetY else vm.active.globalOffsetY
            offset = Offset(
                containerW / 2f + vOx - c.commonX * fitW * scale,
                containerH / 2f + vOy - c.commonY * fitH * scale
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(row, col, fitW, fitH) {
                    detectViewerGestures(
                        getViewport = { Viewport(scale, offset) },
                        fitW = fitW, fitH = fitH,
                        containerW = containerW, containerH = containerH,
                        onTransform = { vp -> scale = vp.scale; offset = vp.offset },
                        onSwipe = { dx, dy ->
                            val nr: Int
                            val nc: Int
                            if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                                if (dx < 0) { nr = row; nc = col + 1 } else { nr = row; nc = col - 1 }
                            } else {
                                if (dy < 0) { nr = row + 1; nc = col } else { nr = row - 1; nc = col }
                            }
                            if (vm.cell(nr, nc) != null) { row = nr; col = nc }
                        }
                    )
                }
        ) {
            val uriStr = currentCell?.imageUri
            if (uriStr != null) {
                AsyncImage(
                    model = Uri.parse(uriStr),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .size(with(density) { fitW.toDp() }, with(density) { fitH.toDp() })
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0f, 0f)
                            translationX = offset.x; translationY = offset.y
                            scaleX = scale; scaleY = scale
                        }
                )
            }
        }

        // Прицел
        Box(
            Modifier.align(Alignment.Center).size(24.dp)
                .background(Color.Red.copy(alpha = 0.7f), shape = CircleShape)
        )

        // Верхняя панель
        Row(
            Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedIconButton(
                onClick = { nav.popBackStack() },
                icon = Icons.Default.ArrowBack,
                description = "Назад"
            )
            Spacer(Modifier.size(8.dp))
            Text("Выбор точки привязки", color = Color.White)
            Spacer(Modifier.weight(1f))
            OutlinedIconButton(
                onClick = { vm.useAux = !vm.useAux },
                icon = if (vm.useAux) Icons.Default.LockOpen else Icons.Default.Lock,
                description = if (vm.useAux) "Главный" else "Вспомогательный"
            )
        }

        // Подтверждение
        OutlinedIconButton(
            onClick = {
                val imgX = (containerW / 2f - offset.x) / scale
                val imgY = (containerH / 2f - offset.y) / scale
                val nx = (imgX / fitW).coerceIn(0f, 1f)
                val ny = (imgY / fitH).coerceIn(0f, 1f)
                vm.setCommentLink(
                    comRow, comCol, commentId,
                    PinLink(vm.useAux, row, col, nx, ny, scale)
                )
                nav.popBackStack()
            },
            icon = Icons.Default.Check,
            description = "Подтвердить",
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
        )
    }
}

@Composable
private fun OutlinedIconButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.IconButton(onClick = onClick) {
            Icon(icon, description, tint = Color.White)
        }
    }
}