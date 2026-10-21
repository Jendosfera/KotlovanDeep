package com.example.kotlovan.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.kotlovan.data.CellData
import com.example.kotlovan.vm.AppViewModel

@Composable
fun CellCardScreen(nav: NavHostController, vm: AppViewModel, row: Int, col: Int) {
    val ctx = LocalContext.current
    val existing = vm.cell(row, col)
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var imageName by remember { mutableStateOf(existing?.imageFile) }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            vm.repo.importImage(uri)?.let { imageName = it }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Назад") }
            Text("Ячейка [$row,$col]", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = title, onValueChange = { title = it },
            label = { Text("Название") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFECEFF1)),
            contentAlignment = Alignment.Center
        ) {
            if (imageName != null) {
                AsyncImage(
                    model = vm.repo.imageFile(imageName!!),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
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
                    vm.upsertCell(row, col, (existing ?: CellData()).copy(
                        title = title, imageFile = imageName
                    ))
                    nav.popBackStack()
                },
                modifier = Modifier.weight(1f).height(56.dp)
            ) { Text("Подтвердить") }
        }
    }
}