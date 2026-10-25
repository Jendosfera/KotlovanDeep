package com.example.kotlovan.ui

import androidx.compose.ui.graphics.FilterQuality
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.kotlovan.data.CellData
import com.example.kotlovan.vm.AppViewModel

@Composable
fun CellCardScreen(nav: NavHostController, vm: AppViewModel, row: Int, col: Int) {
    val existing = vm.cell(row, col)

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
                if (!titleTouched) {
                    vm.repo.getDisplayName(uri)?.let { title = it }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Назад") }
            Text("Ячейка [$row,$col]", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it; titleTouched = true },
            label = { Text("Название") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(12.dp)).background(Color(0xFFECEFF1)),
            contentAlignment = Alignment.Center
        ) {
            val f = imageFile
            if (f != null) {
                AsyncImage(
                    model = vm.repo.imageFile(f), contentDescription = null,
					filterQuality = FilterQuality.High,
                    contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()
                )
            } else {
                Text("Изображение не загружено", color = Color.Gray)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { pickLauncher.launch(arrayOf("image/*")) },
                modifier = Modifier.weight(1f).height(56.dp)
            ) { Text("Загрузить") }
            Button(
                onClick = {
                    val base = existing ?: CellData().let {
                        if (vm.useAux) it.copy(ownPosition = true) else it
                    }
                    vm.upsertCell(row, col, base.copy(
                        title = title, imageFile = imageFile, imageUri = null,
                        imageWidth = imgW, imageHeight = imgH
                    ))
                    nav.popBackStack()
                },
                modifier = Modifier.weight(1f).height(56.dp)
            ) { Text("Подтвердить") }
        }
    }
}