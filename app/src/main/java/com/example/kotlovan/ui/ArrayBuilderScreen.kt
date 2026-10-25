package com.example.kotlovan.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.kotlovan.data.CellData
import com.example.kotlovan.util.Viewport
import com.example.kotlovan.util.detectViewerGestures
import com.example.kotlovan.vm.AppViewModel

private const val CELL_SIZE = 160f
private const val CELL_GAP = 24f

@Composable
fun ArrayBuilderScreen(nav: NavHostController, vm: AppViewModel, aux: Boolean) {
    val ctx = LocalContext.current
    val density = LocalDensity.current

    // Синхронизируем режим VM с параметром навигации
    LaunchedEffect(aux) { vm.useAux = aux }

    var scale by remember(aux) { mutableStateOf(1f) }
    var offset by remember(aux) { mutableStateOf(Offset.Zero) }
    var longPressMode by remember { mutableStateOf(false) }
    var selectedForSwap by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var initialized by remember(aux) { mutableStateOf(false) }
	var showNameDialog by remember { mutableStateOf(false) }
	var fileName by remember { mutableStateOf("array.zip") }

    val bgColor = if (aux) Color(0xFFEDE7F6) else Color(0xFFECEFF1)

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}
            if (vm.save(uri)) {
                Toast.makeText(ctx, "Сохранено", Toast.LENGTH_SHORT).show()
            } else Toast.makeText(ctx, "Ошибка сохранения", Toast.LENGTH_SHORT).show()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(bgColor)) {
        val containerW = constraints.maxWidth.toFloat()
        val containerH = constraints.maxHeight.toFloat()

        val cells = vm.active.cells
        val bounds = remember(cells) {
            if (cells.isEmpty()) intArrayOf(0, 0, 0, 0)
            else {
                val rows = cells.keys.map { it.substringBefore(',').toInt() }
                val cols = cells.keys.map { it.substringAfter(',').toInt() }
                intArrayOf(rows.min(), rows.max(), cols.min(), cols.max())
            }
        }
        val minR = bounds[0]; val maxR = bounds[1]
        val minC = bounds[2]; val maxC = bounds[3]
        val rFrom = minR - 1; val rTo = maxR + 1
        val cFrom = minC - 1; val cTo = maxC + 1

        val cellSizePx = with(density) { CELL_SIZE.dp.toPx() }
        val cellGapPx = with(density) { CELL_GAP.dp.toPx() }
        val step = cellSizePx + cellGapPx
        val totalW = (cTo - cFrom + 1) * step - cellGapPx
        val totalH = (rTo - rFrom + 1) * step - cellGapPx

        LaunchedEffect(Unit) {
            if (!initialized) {
                val targetRow = vm.active.lastRow
                val targetCol = vm.active.lastCol
                val cx = (targetCol - cFrom) * step + cellSizePx / 2f
                val cy = (targetRow - rFrom) * step + cellSizePx / 2f
                offset = Offset(containerW / 2f - cx, containerH / 2f - cy)
                initialized = true
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(totalW, totalH, containerW, containerH) {
                    detectViewerGestures(
                        getViewport = { Viewport(scale, offset) },
                        fitW = totalW, fitH = totalH,
                        containerW = containerW, containerH = containerH,
                        onTransform = { vp -> scale = vp.scale; offset = vp.offset },
                        onSwipe = { _, _ -> },
                        minScale = 0.2f, maxScale = 5f
                    )
                }
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        translationX = offset.x; translationY = offset.y
                        scaleX = scale; scaleY = scale
                    }
            ) {
                for (r in rFrom..rTo) for (c in cFrom..cTo) {
                    val data = vm.cell(r, c)
                    val xPx = (c - cFrom) * step
                    val yPx = (r - rFrom) * step
                    val isPlus = data == null && hasNeighbor(r, c, cells)
                    val isEmptyStart = data == null && cells.isEmpty() && r == 0 && c == 0
                    val isSelected = data != null && vm.active.lastRow == r && vm.active.lastCol == c

                    Box(
                        Modifier
                            .graphicsLayer { translationX = xPx; translationY = yPx }
                            .size(CELL_SIZE.dp)
                    ) {
                        when {
                            data != null -> CreatedCell(
                                data = data,
                                isSelected = isSelected,
                                isSwapTarget = longPressMode && selectedForSwap != null &&
                                        (selectedForSwap!!.first != r || selectedForSwap!!.second != c),
                                onClick = {
                                    if (longPressMode) {
                                        if (selectedForSwap == null) selectedForSwap = r to c
                                        else {
                                            vm.swapCells(selectedForSwap!!.first, selectedForSwap!!.second, r, c)
                                            selectedForSwap = null; longPressMode = false
                                        }
                                    } else {
                                        vm.updateLastCell(r, c)
                                    }
                                },
                                onDoubleTap = { nav.navigate("card/$r/$c") },
                                onLongPress = {
                                    longPressMode = true
                                    selectedForSwap = r to c
                                    Toast.makeText(ctx, "Выберите ячейку для обмена", Toast.LENGTH_SHORT).show()
                                }
                            )
                            isPlus || isEmptyStart -> PlusButton { nav.navigate("card/$r/$c") }
                        }
                    }
                }
            }
        }

        // Верхняя панель: назад, замок
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                vm.autosave()
                nav.popBackStack()
            }) { Icon(Icons.Default.ArrowBack, "Назад") }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {
                if (aux) nav.navigate("builder?aux=false") {
                    popUpTo("builder?aux=true") { inclusive = true }
                }
                else nav.navigate("builder?aux=true")
            }) {
                Icon(
                    if (aux) Icons.Default.LockOpen else Icons.Default.Lock,
                    if (aux) "Главный массив" else "Вспомогательный массив"
                )
            }
        }

        // Нижняя панель
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp)) {
            if (longPressMode) {
                Button(
                    onClick = { longPressMode = false; selectedForSwap = null },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) { Text("Отменить перенос") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { showNameDialog = true },
                    modifier = Modifier.weight(1f).height(56.dp)
                ) { Text("Сохранить массив") }
                Button(
                    onClick = {
                        if (vm.active.cells.isNotEmpty()) {
                            vm.autosave()
                            nav.navigate("viewer")
                        }
                    },
                    enabled = vm.active.cells.isNotEmpty(),
                    modifier = Modifier.weight(1f).height(56.dp)
                ) { Text("Просмотр") }
            }
        }
    }

    DisposableEffect(Unit) { onDispose { vm.autosave() } }
if (showNameDialog) {
    AlertDialog(
        onDismissRequest = { showNameDialog = false },
        title = { Text("Название сохранения") },
        text = {
            OutlinedTextField(
                value = fileName,
                onValueChange = { fileName = it },
                label = { Text("Имя файла") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = {
                showNameDialog = false
                val name = if (fileName.endsWith(".zip")) fileName else "$fileName.zip"
                saveLauncher.launch(name)
            }) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = { showNameDialog = false }) { Text("Отмена") }
        }
    )
}
}

private fun hasNeighbor(r: Int, c: Int, cells: Map<String, CellData>): Boolean {
    return cells.containsKey("${r-1},$c") || cells.containsKey("${r+1},$c") ||
           cells.containsKey("$r,${c-1}") || cells.containsKey("$r,${c+1}")
}

@Composable
private fun CreatedCell(
    data: CellData, isSelected: Boolean, isSwapTarget: Boolean,
    onClick: () -> Unit, onDoubleTap: () -> Unit, onLongPress: () -> Unit
) {
    val borderColor = when {
        isSelected -> Color(0xFF00C853)
        isSwapTarget -> Color(0xFFFFE082)
        else -> Color.Transparent
    }
    val shape = RoundedCornerShape(12.dp)
    Card(
        shape = shape,
        elevation = CardDefaults.cardElevation(6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (isSelected || isSwapTarget) Modifier.border(3.dp, borderColor, shape)
                else Modifier
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onDoubleTap() },
                    onLongPress = { onLongPress() },
                    onTap = { onClick() }
                )
            }
    ) {
        Column(Modifier.fillMaxSize().padding(6.dp)) {
            Text(data.title.ifBlank { "Без названия" }, maxLines = 1, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(6.dp)).background(Color(0xFFCFD8DC))) {
                data.imageFile?.let { f ->
                    AsyncImage(
                        model = vm.repo.imageFile(f), contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun PlusButton(onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1565C0)),
        modifier = Modifier.fillMaxSize().clickable(onClick = onClick)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Add, "Добавить", tint = Color.White)
        }
    }
}