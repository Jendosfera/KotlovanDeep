package com.example.kotlovan.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.kotlovan.util.Viewport
import com.example.kotlovan.util.detectViewerGestures
import com.example.kotlovan.vm.AppViewModel

@Composable
fun ArrayBuilderScreen(
    nav: NavHostController, vm: AppViewModel,
    aux: Boolean, selectForNested: String = ""
) {
    val nestedParts = selectForNested.split(",")
    val selR = nestedParts.getOrNull(0)?.toIntOrNull() ?: -1
    val selC = nestedParts.getOrNull(1)?.toIntOrNull() ?: -1
    val selD = nestedParts.getOrNull(2)?.toIntOrNull() ?: 0
    val isSelectMode = selR >= 0 && selC >= 0

    val ctx = LocalContext.current
    val density = LocalDensity.current

    LaunchedEffect(aux) { vm.useAux = aux }

    var scale by remember(aux) { mutableStateOf(1f) }
    var offset by remember(aux) { mutableStateOf(Offset.Zero) }
    var longPressMode by remember { mutableStateOf(false) }
    var selectedForSwap by remember { mutableStateOf<Triple<Int, Int, Int>?>(null) }
    var showNameDialog by remember { mutableStateOf(false) }
    var fileName by remember { mutableStateOf("array.zip") }
    var localCurrent by remember(aux) { mutableStateOf<Triple<Int, Int, Int>?>(null) }
    var centeredOnKey by remember(aux) { mutableStateOf("") }

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

        val coords = vm.active.allCoords()
        val bounds = remember(coords) {
            if (coords.isEmpty()) intArrayOf(0, 0, 0, 0)
            else {
                val rows = coords.map { it.first }
                val cols = coords.map { it.second }
                intArrayOf(rows.min(), rows.max(), cols.min(), cols.max())
            }
        }
        val rFrom = bounds[0] - 1
        val rTo = bounds[1] + 1
        val cFrom = bounds[2] - 1
        val cTo = bounds[3] + 1

        val cellSizePx = with(density) { CELL_SIZE.dp.toPx() }
        val cellGapPx = with(density) { CELL_GAP.dp.toPx() }
        val titleHPx = with(density) { TITLE_H_DP.dp.toPx() }
        val step = cellSizePx + cellGapPx

        LaunchedEffect(aux, vm.active.lastRow, vm.active.lastCol, vm.active.lastDepth) {
            val key = "${vm.active.lastRow},${vm.active.lastCol},${vm.active.lastDepth}"
            if (centeredOnKey == key) return@LaunchedEffect
            centeredOnKey = key
            localCurrent = Triple(vm.active.lastRow, vm.active.lastCol, vm.active.lastDepth)
            val cx = (vm.active.lastCol - cFrom) * step + cellSizePx / 2f
            val cy = (vm.active.lastRow - rFrom) * step + cellSizePx / 2f
            offset = Offset(containerW / 2f - cx, containerH / 2f - cy)
        }

        Box(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectViewerGestures(
                        getViewport = { Viewport(scale, offset) },
                        fitW = 0f, fitH = 0f,
                        containerW = containerW, containerH = containerH,
                        onTransform = { vp -> scale = vp.scale; offset = vp.offset },
                        onSwipe = { _, _ -> },
                        minScale = 0.2f, maxScale = 5f,
                        alwaysPanOneFinger = true
                    )
                }
        ) {
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = offset.x
                    translationY = offset.y
                    scaleX = scale
                    scaleY = scale
                }
            ) {
                for (r in rFrom..rTo) for (c in cFrom..cTo) {
                    val stack = vm.cellsAt(r, c)
                    val xPx = (c - cFrom) * step
                    val yPx = (r - rFrom) * step
                    val hasAny = stack.isNotEmpty()
                    val isPlus = !hasAny && hasNeighbor(r, c, coords)
                    val isEmptyStart = !hasAny && coords.isEmpty() && r == 0 && c == 0

                    Box(
                        Modifier.graphicsLayer {
                            translationX = xPx; translationY = yPx
                        }.size(CELL_SIZE.dp)
                    ) {
                        if (hasAny) {
                            val sorted = stack.sortedByDescending { it.first }
                            for ((d, cell) in sorted) {
                                val dOffsetX = d * titleHPx
                                val dOffsetY = -d * titleHPx
                                val isSel = localCurrent?.let {
                                    it.first == r && it.second == c && it.third == d
                                } == true
                                val isSwap = longPressMode && selectedForSwap != null &&
                                        !(selectedForSwap!!.first == r &&
                                                selectedForSwap!!.second == c &&
                                                selectedForSwap!!.third == d)
                                val isSource = isSelectMode && r == selR && c == selC && d == selD

                                CreatedCell(
                                    data = cell, vm = vm,
                                    isSelected = isSel,
                                    isSwapTarget = isSwap,
                                    isSelectMode = isSelectMode,
                                    isSource = isSource,
                                    depth = d,
                                    onClick = {
                                        if (isSelectMode) {
                                            if (!(r == selR && c == selC && d == selD)) {
                                                vm.makeNested(selR, selC, selD, r, c)
                                                nav.popBackStack()
                                            }
                                        } else if (longPressMode) {
                                            if (selectedForSwap == null)
                                                selectedForSwap = Triple(r, c, d)
                                            else {
                                                val s = selectedForSwap!!
                                                vm.swapCells(s.first, s.second, s.third, r, c, d)
                                                selectedForSwap = null; longPressMode = false
                                            }
                                        } else {
                                            localCurrent = Triple(r, c, d)
                                        }
                                    },
                                    onDoubleTap = {
                                        if (!isSelectMode && !longPressMode) {
                                            vm.updateLastCell(r, c, d)
                                            nav.navigate("card/$r/$c?depth=$d")
                                        }
                                    },
                                    onLongPress = {
                                        if (!isSelectMode) {
                                            longPressMode = true
                                            selectedForSwap = Triple(r, c, d)
                                            Toast.makeText(ctx, "Выберите ячейку для обмена", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.offset(
                                        x = with(density) { dOffsetX.toDp() },
                                        y = with(density) { dOffsetY.toDp() }
                                    )
                                )
                            }
                        } else if (isPlus || isEmptyStart) {
                            PlusButton(
                                onClick = {
                                    if (isSelectMode) { /* нельзя */ }
                                    else if (longPressMode && selectedForSwap != null) {
                                        val s = selectedForSwap!!
                                        vm.swapCells(s.first, s.second, s.third, r, c, 0)
                                        selectedForSwap = null; longPressMode = false
                                    } else if (!longPressMode) {
                                        vm.updateLastCell(r, c, 0)
                                        nav.navigate("card/$r/$c?depth=0")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        if (!isSelectMode) {
            ArrayBuilderTopBar(
                aux = aux,
                onBack = { vm.autosave(); nav.popBackStack() },
                onToggleArray = {
                    if (aux) nav.navigate("builder?aux=false") {
                        popUpTo("builder?aux=true") { inclusive = true }
                    } else nav.navigate("builder?aux=true")
                },
                onRating = { nav.navigate("rating") }
            )
        }

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
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Yellow, contentColor = Color.Black
                        ),
                        border = BorderStroke(7.dp, Color.Black),
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) { Text("Просмотр") }
                    Button(
                        onClick = { showNameDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50), contentColor = Color.White
                        ),
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
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) { Text("Отмена") }
            }
        )
    }

    DisposableEffect(Unit) { onDispose { vm.autosave() } }
}