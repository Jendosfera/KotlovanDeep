package com.example.kotlovan.ui

import androidx.compose.ui.platform.LocalDensity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.kotlovan.data.CellData
import com.example.kotlovan.vm.AppViewModel

private const val CELL_SIZE = 160f
private const val CELL_GAP = 24f

@Composable
fun ArrayBuilderScreen(nav: NavHostController, vm: AppViewModel) {
    val ctx = LocalContext.current
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var longPressMode by remember { mutableStateOf(false) }
    var selectedForSwap by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}
            if (vm.save(uri)) {
                Toast.makeText(ctx, "Сохранено", Toast.LENGTH_SHORT).show()
                nav.navigate("viewer") { popUpTo("builder") { inclusive = true } }
            } else Toast.makeText(ctx, "Ошибка сохранения", Toast.LENGTH_SHORT).show()
        }
    }

    val cells = vm.config.cells
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

    Box(Modifier.fillMaxSize().background(Color(0xFFECEFF1))) {

        // Сетка с простым pan/zoom
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
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.3f, 3f)
                        offset += pan
                    }
                }
        ) {
            val density = LocalDensity.current
            val cellSizeDp = CELL_SIZE.dp
            val cellGapDp = CELL_GAP.dp
            val cellSizePx = with(density) { cellSizeDp.toPx() }
            val cellGapPx = with(density) { cellGapDp.toPx() }
            
            for (r in rFrom..rTo) for (c in cFrom..cTo) {
                val data = vm.cell(r, c)
                val xPx = (c - cFrom) * (cellSizePx + cellGapPx)
                val yPx = (r - rFrom) * (cellSizePx + cellGapPx)
                val isPlus = data == null && hasNeighbor(r, c, cells)
                val isEmptyStart = data == null && cells.isEmpty() && r == 0 && c == 0
            
                Box(
                    Modifier
                        .graphicsLayer { translationX = xPx; translationY = yPx }
                        .size(cellSizeDp)
                ) { … }
            }
        }

        // Верхняя панель
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                vm.autosave()
                nav.popBackStack("menu", false)
            }) { Icon(Icons.Default.ArrowBack, "Назад") }
            Spacer(Modifier.weight(1f))
        }

        // Нижняя панель
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp)) {
            if (longPressMode) {
                Button(
                    onClick = { longPressMode = false; selectedForSwap = null },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) { Text("Отменить перенос") }
            }
            Button(
                onClick = { saveLauncher.launch("array.json") },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text("Сохранить массив") }
        }
    }

    DisposableEffect(Unit) {
        onDispose { vm.autosave() }
    }
}

private fun hasNeighbor(r: Int, c: Int, cells: Map<String, CellData>): Boolean {
    return cells.containsKey("${r-1},$c") || cells.containsKey("${r+1},$c") ||
           cells.containsKey("$r,${c-1}") || cells.containsKey("$r,${c+1}")
}

@Composable
private fun CreatedCell(
    data: CellData, vm: AppViewModel, isSwapTarget: Boolean,
    onClick: () -> Unit, onDoubleTap: () -> Unit, onLongPress: () -> Unit
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
                data.imageUri?.let { uriStr ->
                    AsyncImage(
                        model = android.net.Uri.parse(uriStr),
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