package com.example.kotlovan.ui

import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.kotlovan.data.CellData
import com.example.kotlovan.util.Viewport
import com.example.kotlovan.util.detectViewerGestures
import com.example.kotlovan.vm.AppViewModel
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun CommentEditorScreen(nav: NavHostController, vm: AppViewModel, row: Int, col: Int, id: String) {
    val cell = vm.cell(row, col) ?: return
    val pin = cell.comments.firstOrNull { it.id == id } ?: return

    var title by remember { mutableStateOf(pin.title) }
    var text by remember { mutableStateOf(pin.text) }
    var radius by remember { mutableFloatStateOf(pin.radius) }
    var confirmDelete by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }

    if (moving) {
        MoveCommentOverlay(
            cell = cell,
            onCancel = { moving = false },
            onConfirm = { nx, ny ->
                vm.setComments(row, col, cell.comments.map {
                    if (it.id == id) it.copy(x = nx, y = ny) else it
                })
                moving = false
            }
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, "Назад")
            }
            Text("Комментарий", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Короткое название") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Текст комментария") },
            modifier = Modifier.fillMaxWidth().height(160.dp)
        )

        Spacer(Modifier.height(16.dp))
        Text(
            "Размер иконки: ${(radius * 100).roundToInt()}%",
            style = MaterialTheme.typography.titleMedium
        )
        Slider(
            value = radius,
            onValueChange = { radius = it },
            valueRange = 0.01f..0.20f
        )

        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = { moving = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Edit, null)
            Text(" Переместить на изображении")
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Delete, null)
                Text(" Удалить")
            }
            Button(
                onClick = {
                    vm.setComments(row, col, cell.comments.map {
                        if (it.id == id) it.copy(title = title, text = text, radius = radius) else it
                    })
                    nav.popBackStack()
                },
                modifier = Modifier.weight(1f)
            ) { Text("Сохранить") }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить комментарий?") },
            text = { Text("Действие нельзя отменить") },
            confirmButton = {
                TextButton(onClick = {
                    vm.setComments(row, col, cell.comments.filterNot { it.id == id })
                    confirmDelete = false
                    nav.popBackStack()
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun MoveCommentOverlay(
    cell: CellData,
    onCancel: () -> Unit,
    onConfirm: (nx: Float, ny: Float) -> Unit
) {
    var mScale by remember { mutableStateOf(1f) }
    var mOffset by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val containerW = constraints.maxWidth.toFloat()
        val containerH = constraints.maxHeight.toFloat()
        val density = LocalDensity.current

        val imageW = cell.imageWidth
        val imageH = cell.imageHeight
        val fitScale = if (imageW > 0 && imageH > 0)
            min(containerW / imageW, containerH / imageH) else 1f
        val fitW = if (imageW > 0) imageW * fitScale else containerW
        val fitH = if (imageH > 0) imageH * fitScale else containerH

        // Центрируем картинку при входе
        LaunchedEffect(fitW, fitH) {
            mOffset = Offset((containerW - fitW) / 2f, (containerH - fitH) / 2f)
        }

        Box(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(fitW, fitH) {
                    detectViewerGestures(
                        getViewport = { Viewport(mScale, mOffset) },
                        fitW = fitW,
                        fitH = fitH,
                        containerW = containerW,
                        containerH = containerH,
                        onTransform = { vp ->
                            mScale = vp.scale
                            mOffset = vp.offset
                        },
                        onSwipe = { _, _ -> }
                    )
                }
        ) {
            val uriStr = cell.imageUri
            if (uriStr != null) {
                AsyncImage(
                    model = Uri.parse(uriStr),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .size(
                            with(density) { fitW.toDp() },
                            with(density) { fitH.toDp() }
                        )
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0f, 0f)
                            translationX = mOffset.x
                            translationY = mOffset.y
                            scaleX = mScale
                            scaleY = mScale
                        }
                )
            }
        }

        // Прицел по центру
        Box(
            Modifier
                .align(Alignment.Center)
                .size(24.dp)
                .background(Color.Red, shape = CircleShape)
        )

        IconButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
        ) { Icon(Icons.Default.ArrowBack, "Отмена", tint = Color.White) }

        IconButton(
            onClick = {
                // Точка картинки под центром экрана
                val centerX = containerW / 2f
                val centerY = containerH / 2f
                val imgX = (centerX - mOffset.x) / mScale
                val imgY = (centerY - mOffset.y) / mScale
                val nx = (imgX / fitW).coerceIn(0f, 1f)
                val ny = (imgY / fitH).coerceIn(0f, 1f)
                onConfirm(nx, ny)
            },
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
        ) { Icon(Icons.Default.Check, "OK", tint = Color.White) }
    }
}