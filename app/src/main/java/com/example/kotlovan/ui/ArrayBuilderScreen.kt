package com.example.kotlovan.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.kotlovan.data.CellData
import com.example.kotlovan.data.SubArray
import com.example.kotlovan.util.applyZoom
import com.example.kotlovan.util.detectViewerGestures
import com.example.kotlovan.vm.AppViewModel

private const val CELL_SIZE = 160f
private const val CELL_GAP = 24f

@Composable
fun ArrayBuilderScreen(nav: NavHostController, vm: AppViewModel) {
    val ctx = LocalContext.current
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var longPressMode by remember { mutableStateOf(false) }
    var selectedForSwap by remember { mutableStateOf<Triple<Int, Int, Int>?>(null) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    UnsavedMonitor(vm)

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}
            if (vm.onUserSave(uri)) {
                Toast.makeText(ctx, "Сохранено", Toast.LENGTH_SHORT).show()
                nav.navigate("viewer") { popUpTo("builder") { inclusive = true } }
            } else {
                Toast.makeText(ctx, "Ошибка сохранения", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val cells = vm.active.cells
    val bounds = remember(cells) {
        if (cells.isEmpty()) intArrayOf(0, 0, 0, 0)
        else {
            var minR = Int.MAX_VALUE; var maxR = Int.MIN_VALUE
            var minC = Int.MAX_VALUE; var maxC = Int.MIN_VALUE
            for (k in cells.keys) SubArray.parseKey(k)?.let { (r, c, _) ->
                if (r < minR) minR = r
                if (r > maxR) maxR = r
                if (c < minC) minC = c
                if (c > maxC) maxC = c
            }
            intArrayOf(minR, maxR, minC, maxC)
        }
    }

    val rFrom = bounds[0] - 1
    val rTo = bounds[1] + 1
    val cFrom = bounds[2] - 1
    val cTo = bounds[3] + 1

    // Правило 3: Центрирование на текущей ячейке при входе, смене массива или возврате с просмотра
    LaunchedEffect(vm.active.lastRow, vm.active.lastCol, vm.useAux, boxSize) {
        if (boxSize != IntSize.Zero) {
            val r = vm.active.lastRow
            val c = vm.active.lastCol
            val x = (c - cFrom) * (CELL_SIZE + CELL_GAP)
            val y = (r - rFrom) * (CELL_SIZE + CELL_GAP)
            offset = Offset(
                boxSize.width / 2f - (x + CELL_SIZE / 2f) * scale,
                boxSize.height / 2f - (y + CELL_SIZE / 2f) * scale
            )
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFECEFF1))) {
        UnsavedBanner(vm)

        Box(Modifier.fillMaxSize().onSizeChanged { boxSize = it }) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = offset.x
                        translationY = offset.y
                        scaleX = scale
                        scaleY = scale
                    }
                    .pointerInput(Unit) {
                        detectViewerGestures(
                            onTransform = { c, p, z ->
                                val (s, ox, oy) = applyZoom(scale, offset.x, offset.y, c, p, z)
                                scale = s
                                offset = Offset(ox, oy)
                            },
                            onSwipe = { _, _ -> }
                        )
                    }
            ) {
                for (r in rFrom..rTo) for (c in cFrom..cTo) {
                    val data = vm.cell(r, c, 0)
                    val x = (c - cFrom) * (CELL_SIZE + CELL_GAP)
                    val y = (r - rFrom) * (CELL_SIZE + CELL_GAP)
                    val isPlus = data == null && hasNeighbor(r, c, cells)
                    val isEmptyStart = data == null && cells.isEmpty() && r == 0 && c == 0

                    Box(
                        Modifier
                            .graphicsLayer {
                                translationX = x
                                translationY = y
                            }
                            .size(CELL_SIZE.dp)
                    ) {
                        when {
                            data != null -> CreatedCell(
                                data = data,
                                vm = vm,
                                isSwapTarget = longPressMode && selectedForSwap != null &&
                                        (selectedForSwap!!.first != r || selectedForSwap!!.second != c),
                                onClick = {
                                    if (longPressMode) {
                                        if (selectedForSwap == null) selectedForSwap = Triple(r, c, 0)
                                        else {
                                            vm.swapCells(
                                                selectedForSwap!!.first, selectedForSwap!!.second, selectedForSwap!!.third,
                                                r, c, 0
                                            )
                                            selectedForSwap = null
                                            longPressMode = false
                                        }
                                    } else {
                                        // Правило 4а: одиночное нажатие делает ячейку текущей
                                        vm.updateLastCell(r, c, 0)
                                    }
                                },
                                onDoubleTap = { nav.navigate("card/$r/$c") },
                                onLongPress = {
                                    longPressMode = true
                                    selectedForSwap = Triple(r, c, 0)
                                    Toast.makeText(ctx, "Выберите ячейку для обмена", Toast.LENGTH_SHORT).show()
                                }
                            )
                            isPlus || isEmptyStart -> PlusButton {
                                vm.updateLastCell(r, c, 0)
                                nav.navigate("card/$r/$c")
                            }
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    vm.autosave()
                    nav.popBackStack("menu", false)
                }) { Icon(Icons.Default.ArrowBack, "Назад") }
                
                Spacer(Modifier.weight(1f))
                
                // Правило 3а: Иконка замка для переключения массивов
                IconButton(onClick = { vm.useAux = !vm.useAux }) {
                    Icon(
                        imageVector = if (vm.useAux) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = "Переключить массив",
                        tint = if (vm.useAux) Color(0xFF1565C0) else Color.Gray
                    )
                }
            }

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                if (longPressMode) {
                    Button(
                        onClick = {
                            longPressMode = false
                            selectedForSwap = null
                        },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) { Text("Отменить перенос") }
                }
                Button(
                    onClick = { saveLauncher.launch("array.zip") },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) { Text("Сохранить массив") }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { vm.autosave() }
    }
}

private fun hasNeighbor(r: Int, c: Int, cells: Map<String, CellData>): Boolean =
    cells.keys.any { k ->
        SubArray.parseKey(k)?.let { (kr, kc, _) ->
            (kr == r && kc == c + 1) || (kr == r && kc == c - 1) ||
                    (kr == r + 1 && kc == c) || (kr == r - 1 && kc == c)
        } == true
    }

@Composable
private fun CreatedCell(
    data: CellData,
    vm: AppViewModel,
    isSwapTarget: Boolean,
    onClick: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSwapTarget) Color(0xFFFFE082) else Color.White
        ),
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
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
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFCFD8DC))
            ) {
                data.imageFile?.let { name ->
                    AsyncImage(
                        model = vm.repo.imageFile(name),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
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