package com.example.kotlovan.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.kotlovan.data.CellData
import com.example.kotlovan.vm.AppViewModel

@Composable
fun CellCardScreen(
    nav: NavHostController, vm: AppViewModel,
    row: Int, col: Int, depth: Int = 0
) {
    val existing = vm.cell(row, col, depth)
    val rootCell = vm.cell(row, col, 0)
    val maxDepth = vm.depthAt(row, col)
    val hasParent = depth > 0

    var title by remember(depth) { mutableStateOf(existing?.title ?: "") }
    var titleTouched by remember(depth) { mutableStateOf(!existing?.title.isNullOrBlank()) }
    var imageFile by remember(depth) { mutableStateOf(existing?.imageFile) }
    var imgW by remember(depth) { mutableIntStateOf(existing?.imageWidth ?: 0) }
    var imgH by remember(depth) { mutableIntStateOf(existing?.imageHeight ?: 0) }
    var labelLevel by remember { mutableStateOf<Int?>(null) }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            vm.repo.importImage(uri)?.let { name ->
                imageFile = name
                vm.repo.getImageSize(name)?.let { (w, h) -> imgW = w; imgH = h }
                if (!titleTouched) vm.repo.getDisplayName(uri)?.let { title = it }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, "Назад")
            }
            Text(
                "Ячейка [$row,$col${if (depth > 0) ", d=$depth" else ""}]",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {
                vm.removeCell(row, col, depth)
                vm.autosave()
                nav.popBackStack()
            }) { Icon(Icons.Default.Delete, "Удалить", tint = Color(0xFFD32F2F)) }
        }

        Spacer(Modifier.height(8.dp))

        // «Вложить» — всегда для d=0
        if (depth == 0) {
            Button(
                onClick = {
                    val base = existing ?: CellData()
                    vm.upsertCell(row, col, base.copy(
                        title = title, imageFile = imageFile, imageUri = null,
                        imageWidth = imgW, imageHeight = imgH
                    ), 0)
                    vm.autosave()
                    nav.navigate("builder?aux=${vm.useAux}&selectForNested=$row,$col,0")
                },
                colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Blue),
                border = BorderStroke(7.dp, Blue),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Default.Layers, null)
                Spacer(Modifier.size(6.dp))
                Text("Вложить")
            }
            Spacer(Modifier.height(8.dp))
        }

        // Маленькие кнопки уровней
        if (depth == 0 && maxDepth > 0) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (level in 1..maxDepth) {
                    val lbl = rootCell?.nestedLevelLabels?.get(level).orEmpty()
                        .ifBlank { "d=$level" }
                    Box(
                        Modifier
                            .height(44.dp)
                            .width(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Yellow)
                            .border(4.dp, Blue, RoundedCornerShape(8.dp))
                            .pointerInput(level, rootCell?.nestedLevelLabels) {
                                detectTapGestures(
                                    onTap = {
                                        nav.navigate("card/$row/$col?depth=$level") {
                                            popUpTo("card/$row/$col?depth=0") { inclusive = true }
                                        }
                                    },
                                    onLongPress = { labelLevel = level }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            lbl, color = Blue,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Контейнер названия
        Box(
            Modifier.fillMaxWidth()
                .background(Yellow, RoundedCornerShape(8.dp))
                .border(7.dp, Blue, RoundedCornerShape(8.dp))
                .padding(4.dp)
        ) {
            OutlinedTextField(
                value = title, onValueChange = { title = it; titleTouched = true },
                label = { Text("Название") }, modifier = Modifier.fillMaxWidth()
            )
        }

        // d>0: к главной + Отделить (через builder)
        if (hasParent) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        nav.navigate("card/$row/$col?depth=0") {
                            popUpTo("card/$row/$col?depth=$depth") { inclusive = true }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Blue),
                    border = BorderStroke(7.dp, Blue),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text("К главной", maxLines = 1) }
                Button(
                    onClick = {
                        // Режим выбора куда отделить
                        nav.navigate("builder?aux=${vm.useAux}&selectForUnnest=$row,$col,$depth")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFCDD2), contentColor = Color.Black
                    ),
                    border = BorderStroke(7.dp, Color.Black),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text("Отделить", maxLines = 1) }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Изображение
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFECEFF1))
                .pointerInput(imageFile) {
                    detectTapGestures(
                        onTap = { if (imageFile == null) pickLauncher.launch(arrayOf("image/*")) },
                        onDoubleTap = { pickLauncher.launch(arrayOf("image/*")) }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            val f = imageFile
            if (f != null) {
                AsyncImage(
                    model = vm.repo.imageFile(f),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Canvas(Modifier.size(120.dp)) {
                    val r = size.minDimension * 0.4f
                    val c = center
                    drawCircle(
                        color = Color.Gray, radius = r, center = c,
                        style = Stroke(
                            width = 3.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f)
                        )
                    )
                    val plus = r * 0.5f
                    val sw = 3.dp.toPx()
                    drawLine(Color.Gray, c + Offset(-plus, 0f), c + Offset(plus, 0f), sw)
                    drawLine(Color.Gray, c + Offset(0f, -plus), c + Offset(0f, plus), sw)
                }
                Text(
                    "Однократный тап — загрузить\nДвойной — заменить",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    val base = existing ?: CellData().let {
                        if (vm.useAux) it.copy(ownPosition = true) else it
                    }
                    vm.upsertCell(row, col, base.copy(
                        title = title, imageFile = imageFile, imageUri = null,
                        imageWidth = imgW, imageHeight = imgH
                    ), depth)
                    vm.updateLastCell(row, col, depth)
                    vm.autosave()
                    nav.popBackStack()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text("Подтвердить") }
        }
    }

    labelLevel?.let { lvl ->
        val currentLbl = rootCell?.nestedLevelLabels?.get(lvl).orEmpty()
        var text by remember(lvl, currentLbl) { mutableStateOf(currentLbl) }
        AlertDialog(
            onDismissRequest = { labelLevel = null },
            containerColor = Yellow,
            title = { Text("Название кнопки d=$lvl", color = Blue) },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { if (it.length <= 8) text = it },
                    label = { Text("Название") },
                    supportingText = { Text("${text.length}/8") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateCell(row, col, 0) { cell ->
                        cell.copy(nestedLevelLabels =
                            if (text.isBlank()) cell.nestedLevelLabels - lvl
                            else cell.nestedLevelLabels + (lvl to text))
                    }
                    vm.autosave()
                    labelLevel = null
                }) { Text("OK", color = Blue) }
            },
            dismissButton = {
                TextButton(onClick = { labelLevel = null }) { Text("Отмена") }
            }
        )
    }
}