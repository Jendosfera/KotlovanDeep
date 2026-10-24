package com.example.kotlovan.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.kotlovan.vm.AppViewModel

@Composable
fun MainMenuScreen(nav: NavHostController, vm: AppViewModel) {
    val ctx = LocalContext.current
    var showReconnectDialog by remember { mutableStateOf(false) }
    var reconnectIdx by remember { mutableIntStateOf(0) }

    // Загрузка JSON
    val loadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            if (vm.load(uri)) {
                if (vm.pendingReconnect.isNotEmpty()) {
                    showReconnectDialog = true
                } else {
                    nav.navigate("builder")
                }
            } else {
                Toast.makeText(ctx, "Не удалось прочитать файл", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Переподключение URI картинки
    val reconnectLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val (r, c) = vm.pendingReconnect[reconnectIdx]
            vm.reconnectImage(r, c, uri)
        }
        reconnectIdx++
        if (reconnectIdx < vm.pendingReconnect.size) {
            reconnectLauncher.launch(arrayOf("image/*"))
        } else {
            reconnectIdx = 0
            vm.clearPendingReconnect()
            nav.navigate("builder")
        }
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Котлован", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = { vm.newArray(); nav.navigate("builder") },
            modifier = Modifier.fillMaxWidth().height(64.dp)
        ) { Text("Создать массив", fontSize = 20.sp) }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = { loadLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
            modifier = Modifier.fillMaxWidth().height(64.dp)
        ) { Text("Загрузить массив", fontSize = 20.sp) }
    }

    if (showReconnectDialog) {
        val total = vm.pendingReconnect.size
        val current = reconnectIdx + 1
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Восстановить доступ к изображениям") },
            text = {
                Text(
                    "После переустановки приложения $total изображений потеряли доступ. " +
                    "Выберите их заново, чтобы приложение снова могло их показывать."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showReconnectDialog = false
                    reconnectIdx = 0
                    reconnectLauncher.launch(arrayOf("image/*"))
                }) { Text("Выбрать файлы ($current / $total)") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showReconnectDialog = false
                    vm.clearPendingReconnect()
                    nav.navigate("builder")
                }) { Text("Пропустить") }
            }
        )
    }
}