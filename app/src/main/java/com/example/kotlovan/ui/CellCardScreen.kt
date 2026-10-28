package com.example.kotlovan.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.kotlovan.data.CellData
import com.example.kotlovan.vm.AppViewModel

private val Yellow = Color(0xFFFFEB3B)
private val Blue = Color(0xFF1565C0)

@Composable
fun CellCardScreen(nav: NavHostController, vm: AppViewModel, row: Int, col: Int) {
    val existing = vm.cell(row, col)
    val hasParent = (existing?.parentRow ?: -1) >= 0
    val hasNested = (existing?.nestedRow ?: -1) >= 0

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var titleTouched by remember { mutableStateOf(!existing?.title.isNullOrBlank()) }
    var imageFile by remember { mutableStateOf(existing?.imageFile) }
    var imgW by remember { mutableIntStateOf(existing?.imageWidth ?: 0) }
    var imgH by remember { mutableIntStateOf(existing?.imageHeight ?: 0) }

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
        // Верхняя панель
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, "Назад")
            }
            Text("Ячейка [$row,$col]", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {
                vm.removeCell(row, col)
                vm.autosave()
                nav.popBackStack()
            }) { Icon(Icons.Default.Delete, "Удалить ячейку", tint = Color(0xFFD32F2F)) }
        }

        Spacer(Modifier.height(12.dp))

        // Контейнер названия (жёлтый + синяя кайма)
        Box(
            Modifier.fillMaxWidth()
                .background(Yellow, RoundedCornerShape(8.dp))
                .border(7.dp, Blue, RoundedCornerShape(8.dp))
                .padding(4.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it; titleTouched = true },
                label = { Text("Название") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Кнопки вложения
        if (hasParent || hasNested) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val targetRow = if (hasNested) existing!!.nestedRow else existing!!.parentRow
                    val targetCol = if (hasNested) existing!!.nestedCol else existing!!.parentCol
                    nav.navigate("card/$targetRow/$targetCol") {
                        popUpTo("card/$row/$col") { inclusive = true }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Yellow, contentColor = Blue
                ),
                border = BorderStroke(7.dp, Blue),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(Icons.Default.Layers, null)
                Spacer(Modifier.size(6.dp))
                Text(if (hasNested) "Перейти к вложенной" else "Перейти к главной")
            }
        }
        if (hasNested) {
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = {
                    vm.removeNested(row, col)
                    nav.popBackStack()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFCDD2), contentColor = Color.Black
                ),
                border = BorderStroke(7.dp, Color.Black),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("Убрать вложение") }
        } else if (!hasParent) {
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = {
                    // Сначала сохраняем текущее состояние в модели, чтобы при
                    // возврате из builder данные были актуальны
                    val base = existing ?: CellData().let {
                        if (vm.useAux) it.copy(ownPosition = true) else it
                    }
                    vm.upsertCell(row, col, base.copy(
                        title = title, imageFile = imageFile, imageUri = null,
                        imageWidth = imgW, imageHeight = imgH
                    ))
                    vm.autosave()
                    nav.navigate("builder?aux=${vm.useAux}&selectForNested=$row,$col")
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Yellow, contentColor = Blue
                ),
                border = BorderStroke(7.dp, Blue),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("Сделать вложенной") }
        }

        Spacer(Modifier.height(16.dp))

        // Контейнер изображения
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
                    ))
                    vm.autosave()
                    nav.popBackStack()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text("Подтвердить") }
        }
    }
}