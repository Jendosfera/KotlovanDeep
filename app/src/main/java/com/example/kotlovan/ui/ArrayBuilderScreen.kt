package com.example.kotlovan.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
private val Yellow = Color(0xFFFFEB3B)
private val Blue = Color(0xFF1565C0)

@Composable
fun ArrayBuilderScreen(
    nav: NavHostController,
    vm: AppViewModel,
    aux: Boolean,
    selectForNested: String = ""
) {
    // Парсим selectForNested = "row,col"
    val nestedParts = selectForNested.split(",")
    val nestedRow = nestedParts.getOrNull(0)?.toIntOrNull() ?: -1
    val nestedCol = nestedParts.getOrNull(1)?.toIntOrNull() ?: -1
    val isSelectMode = nestedRow >= 0 && nestedCol >= 0

    val ctx = LocalContext.current
    val density = LocalDensity.current

    LaunchedEffect(aux) { vm.useAux = aux }

    var scale by remember(aux) { mutableStateOf(1f) }
    var offset by remember(aux) { mutableStateOf(Offset.Zero) }
    var longPressMode by remember { mutableStateOf(false) }
    var selectedForSwap by remember { mutableStateOf<Pair<Int, Int>?>(null) }
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
            if (vm.save(uri)) Toast.makeText(ctx, "Сохранено", Toast.LENGTH_SHORT).show()
            else Toast.makeText(ctx, "Ошибка сохранения", Toast.LENGTH_SHORT).show()
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
        val rFrom = bounds[0] - 1
        val rTo = bounds[1] + 1
        val cFrom = bounds[2] - 1
        val cTo = bounds[3] + 1

        val cellSizePx = with(density) { CELL_SIZE.dp.toPx() }
        val cellGapPx = with(density) { CELL_GAP.dp.toPx() }
        val step = cellSizePx + cellGapPx
        val totalW = (cTo - cFrom + 1) * step - cellGapPx
        val totalH = (rTo - rFrom + 1) * step - cellGapPx

        LaunchedEffect(aux, vm.active.lastRow, vm.active.lastCol) {
            val cx = (vm.active.lastCol - cFrom) * step + cellSizePx / 2f
            val cy = (vm.active.lastRow - rFrom) * step + cellSizePx / 2f
            offset = Offset(containerW / 2f - cx, containerH / 2f - cy)
        }

        Box(
            Modifier.fillMaxSize()
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
                Modifier.fillMaxSize().graphicsLayer {
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
                    val isSelected = data != null &&
                            vm.active.lastRow == r && vm.active.lastCol == c

                    Box(
                        Modifier.graphicsLayer { translationX = xPx; translationY = yPx }
                            .size(CELL_SIZE.dp)
                    ) {
                        when {
                            data != null -> CreatedCell(
                                data = data, vm = vm,
                                isSelected = isSelected,
                                isSwapTarget = longPressMode && selectedForSwap != null &&
                                        (selectedForSwap!!.first != r || selectedForSwap!!.second != c),
                                isSelectMode = isSelectMode,
                                isSource = (r == nestedRow && c == nestedCol),
                                onOpenNested = {
                                    if (data.nestedRow >= 0 && data.nestedCol >= 0) {
                                        nav.navigate("card/${data.nestedRow}/${data.nestedCol}")
                                    }
                                },
                                onNestedLongPress = {
                                    if (data.nestedRow >= 0 && data.nestedCol >= 0) {
                                        longPressMode = true
                                        selectedForSwap = data.nestedRow to data.nestedCol
                                        Toast.makeText(ctx, "Выберите ячейку для обмена", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onClick = {
                                    if (isSelectMode) {
                                        if (r != nestedRow || c != nestedCol) {
                                            vm.makeNested(nestedRow, nestedCol, r, c)
                                            nav.popBackStack()
                                        }
                                    } else if (longPressMode) {
                                        if (selectedForSwap == null) selectedForSwap = r to c
                                        else {
                                            vm.swapCells(selectedForSwap!!.first, selectedForSwap!!.second, r, c)
                                            selectedForSwap = null; longPressMode = false
                                        }
                                    } else vm.updateLastCell(r, c)
                                },
                                onDoubleTap = {
                                    if (!isSelectMode && !longPressMode) nav.navigate("card/$r/$c")
                                },
                                onLongPress = {
                                    if (!isSelectMode) {
                                        longPressMode = true
                                        selectedForSwap = r to c
                                        Toast.makeText(ctx, "Выберите ячейку для обмена", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            isPlus || isEmptyStart -> PlusButton(
                                onClick = {
                                    if (isSelectMode) {
                                        // Нельзя вложить в пустую — игнорируем
                                    } else if (longPressMode && selectedForSwap != null) {
                                        vm.swapCells(selectedForSwap!!.first, selectedForSwap!!.second, r, c)
                                        selectedForSwap = null; longPressMode = false
                                    } else if (!longPressMode) {
                                        nav.navigate("card/$r/$c")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Верхняя панель
        if (!isSelectMode) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .background(Yellow).border(7.dp, Color.Black, CircleShape)
                        .clickable { vm.autosave(); nav.popBackStack() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.ArrowBack, "Назад", tint = Blue) }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .background(Yellow).border(7.dp, Color.Black, CircleShape)
                        .clickable {
                            if (aux) nav.navigate("builder?aux=false") {
                                popUpTo("builder?aux=true") { inclusive = true }
                            } else nav.navigate("builder?aux=true")
                        },
                    contentAlignment = Alignment.Center
                ) { Icon(if (aux) Icons.Default.LockOpen else Icons.Default.Lock, "Массив", tint = Blue) }
                Spacer(Modifier.width(4.dp))
                Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .background(Yellow).border(7.dp, Color.Black, CircleShape)
                        .clickable { nav.navigate("rating") },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Star, "Рейтинг", tint = Blue) }
            }
        }

        // Нижняя панель
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp)) {
            if (isSelectMode) {
                Button(
                    onClick = { nav.popBackStack() },
                    border = BorderStroke(7.dp, Color.Black),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFCDD2), contentColor = Color.Black
                    ),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) { Text("Отменить вложение") }
            } else {
                if (longPressMode) {
                    Button(
                        onClick = { longPressMode = false; selectedForSwap = null },
                        border = BorderStroke(7.dp, Color.Black),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) { Text("Отменить перенос") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            if (vm.active.cells.isNotEmpty()) {
                                vm.autosave(); nav.navigate("viewer")
                            }
                        },
                        enabled = vm.active.cells.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Color.Black),
                        border = BorderStroke(7.dp, Color.Black),
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) { Text("Просмотр") }
                    Button(
                        onClick = { showNameDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), contentColor = Color.White),
                        border = BorderStroke(7.dp, Color.Black),
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) { Text("Сохранить") }
                }
            }
        }
    }

    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Название сохранения") },
            text = {
                OutlinedTextField(
                    value = fileName, onValueChange = { fileName = it },
                    label = { Text("Имя файла") }, singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showNameDialog = false
                    val name = if (fileName.endsWith(".zip")) fileName else "$fileName.zip"
                    saveLauncher.launch(name)
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { showNameDialog = false }) { Text("Отмена") } }
        )
    }

    DisposableEffect(Unit) { onDispose { vm.autosave() } }
}

private fun hasNeighbor(r: Int, c: Int, cells: Map<String, CellData>): Boolean =
    cells.containsKey("${r-1},$c") || cells.containsKey("${r+1},$c") ||
    cells.containsKey("$r,${c-1}") || cells.containsKey("$r,${c+1}")

@Composable
private fun CreatedCell(
    data: CellData,
    vm: AppViewModel,
    isSelected: Boolean,
    isSwapTarget: Boolean,
    isSelectMode: Boolean,
    isSource: Boolean,
    onOpenNested: () -> Unit,
    onNestedLongPress: () -> Unit,
    onClick: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val borderColor = when {
        isSource && isSelectMode -> Color(0xFF9E9E9E)
        isSelected -> Color(0xFF00C853)
        isSwapTarget -> Color(0xFFFFE082)
        else -> Color.Transparent
    }
    val shape = RoundedCornerShape(12.dp)
    val density = LocalDensity.current
    val titleH = 24.dp

    Box(Modifier.fillMaxSize()) {
        // ==== Подложка ====
        if (data.nestedImageFile != null || data.nestedTitle.isNotBlank()) {
            Card(
                shape = shape,
                elevation = CardDefaults.cardElevation(3.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4)),
                modifier = Modifier
                    .fillMaxSize()
                    .offset(x = titleH, y = -titleH)
                    .pointerInput(data.nestedRow, data.nestedCol) {
                        detectTapGestures(
                            onDoubleTap = { onOpenNested() },
                            onLongPress = { onNestedLongPress() }
                        )
                    }
            ) {
                Column(Modifier.fillMaxSize().padding(4.dp)) {
                    Text(
                        data.nestedTitle.ifBlank { "Подложка" },
                        maxLines = 1,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(Modifier.height(2.dp))
                    Box(
                        Modifier.fillMaxWidth().weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFE0E0E0))
                    ) {
                        data.nestedImageFile?.let { f ->
                            AsyncImage(
                                model = vm.repo.imageFile(f),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        // ==== Главная карта ====
        Card(
            shape = shape,
            elevation = CardDefaults.cardElevation(6.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isSelected || isSwapTarget || (isSource && isSelectMode))
                        Modifier.border(3.dp, borderColor, shape)
                    else Modifier
                )
                .pointerInput(isSelectMode, isSource) {
                    detectTapGestures(
                        onDoubleTap = { onDoubleTap() },
                        onLongPress = { onLongPress() },
                        onTap = { onClick() }
                    )
                }
        ) {
            Column(Modifier.fillMaxSize().padding(6.dp)) {
                Text(
                    data.title.ifBlank { "Без названия" },
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.fillMaxWidth().weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFCFD8DC))
                ) {
                    data.imageFile?.let { f ->
                        AsyncImage(
                            model = vm.repo.imageFile(f),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
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
        colors = CardDefaults.cardColors(containerColor = Blue),
        modifier = Modifier.fillMaxSize()
            .border(7.dp, Color.Black, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Add, "Добавить", tint = Yellow, modifier = Modifier.size(72.dp))
        }
    }
}