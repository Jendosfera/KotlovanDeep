package com.example.kotlovan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.kotlovan.data.PinLink
import com.example.kotlovan.util.Viewport
import com.example.kotlovan.util.detectViewerGestures
import com.example.kotlovan.vm.AppViewModel
import kotlin.math.abs
import kotlin.math.min

@Composable
fun LinkPickerScreen(
    nav: NavHostController,
    vm: AppViewModel,
    comRow: Int,
    comCol: Int,
    commentId: String
) {
    // Сохраняем исходный массив при входе, восстанавливаем при выходе
    val originalUseAux = remember { vm.useAux }
    DisposableEffect(Unit) {
        onDispose { vm.useAux = originalUseAux }
    }

    var row by remember { mutableIntStateOf(vm.active.lastRow) }
    var col by remember { mutableIntStateOf(vm.active.lastCol) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

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

        val allRows = vm.active.cells.keys.map { it.substringBefore(',').toInt() }
        val allCols = vm.active.cells.keys.map { it.substringAfter(',').toInt() }
        val rowRange = if (allRows.isEmpty()) 0..0 else allRows.min()..allRows.max()
        val colRange = if (allCols.isEmpty()) 0..0 else allCols.min()..allCols.max()

        LaunchedEffect(row, col, fitW, fitH, vm.useAux) {
            val c = vm.cell(row, col) ?: return@LaunchedEffect
            scale = if (c.ownPosition) c.ownScale else vm.active.globalScale
            val vOx = if (c.ownPosition) c.ownOffsetX else vm.active.globalOffsetX
            val vOy = if (c.ownPosition) c.ownOffsetY else vm.active.globalOffsetY
            offsetX = containerW / 2f + vOx - c.commonX * fitW * scale
            offsetY = containerH / 2f + vOy - c.commonY * fitH * scale
        }

        Box(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(row, col, fitW, fitH, vm.useAux) {
                    detectViewerGestures(
                        getViewport = { Viewport(scale, Offset(offsetX, offsetY)) },
                        fitW = fitW, fitH = fitH,
                        containerW = containerW, containerH = containerH,
                        onTransform = { vp ->
                            scale = vp.scale
                            offsetX = vp.offset.x
                            offsetY = vp.offset.y
                        },
                        onSwipe = { dx, dy ->
                            val nr: Int
                            val nc: Int
                            if (abs(dx) > abs(dy)) {
                                if (dx < 0) { nr = row; nc = col + 1 } else { nr = row; nc = col - 1 }
                            } else {
                                if (dy < 0) { nr = row + 1; nc = col } else { nr = row - 1; nc = col }
                            }
                            if (vm.cell(nr, nc) != null) { row = nr; col = nc }
                        }
                    )
                }
        ) {
            val imgFile = currentCell?.imageFile
            if (currentCell != null && imgFile != null && imageW > 0 && imageH > 0) {
                ZoomableRegionImage(
                    file = vm.repo.imageFile(imgFile),
                    imageW = imageW,
                    imageH = imageH,
                    containerW = containerW,
                    containerH = containerH,
                    scale = scale,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    fitW = fitW,
                    fitH = fitH,
                    modifier = Modifier.fillMaxSize()
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
            Spacer(Modifier.width(8.dp))
            Text("Точка привязки", color = Color.White)
            Spacer(Modifier.weight(1f))
            OutlinedIconButton(
                onClick = {
                    vm.useAux = !vm.useAux
                    val t = vm.active
                    row = t.lastRow
                    col = t.lastCol
                },
                icon = if (vm.useAux) Icons.Default.LockOpen else Icons.Default.Lock,
                description = if (vm.useAux) "Главный" else "Вспомогательный"
            )
        }

        // Кнопки строк (слева)
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 4.dp, top = 60.dp, bottom = 100.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (r in rowRange) {
                SideButton(
                    text = vm.active.rowLabels[r] ?: "R${r + 1}",
                    selected = (r == row),
                    onClick = {
                        if (vm.cell(r, col) != null) { row = r }
                    }
                )
            }
        }

        // Кнопки столбцов (снизу)
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 60.dp, end = 8.dp, bottom = 12.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (c in colRange) {
                SideButton(
                    text = vm.active.colLabels[c] ?: "C${c + 1}",
                    selected = (c == col),
                    onClick = {
                        if (vm.cell(row, c) != null) { col = c }
                    }
                )
            }
        }

        // Подтверждение
        OutlinedIconButton(
            onClick = {
                val imgX = (containerW / 2f - offsetX) / scale
                val imgY = (containerH / 2f - offsetY) / scale
                val nx = (imgX / fitW).coerceIn(0f, 1f)
                val ny = (imgY / fitH).coerceIn(0f, 1f)
                val newLink = PinLink(vm.useAux, row, col, nx, ny, scale)
                vm.setCommentLink(comRow, comCol, commentId, newLink)
                if (vm.linkReturn != null) vm.pendingRejump = newLink
                nav.popBackStack()
            },
            icon = Icons.Default.Check,
            description = "Подтвердить",
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
        )
    }
}

@Composable
private fun SideButton(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 52.dp, height = 40.dp)
            .scale(if (selected) 1.15f else 1f)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color(0xFF1565C0) else Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text, color = Color.White, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 4.dp)
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
        modifier = modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, description, tint = Color.White) }
}