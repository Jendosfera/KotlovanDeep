package com.example.kotlovan.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            // даём приложению постоянный доступ
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            if (vm.load(uri)) nav.navigate("builder")
            else Toast.makeText(ctx, "Не удалось прочитать файл", Toast.LENGTH_SHORT).show()
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
            onClick = { openLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
            modifier = Modifier.fillMaxWidth().height(64.dp)
        ) { Text("Загрузить массив", fontSize = 20.sp) }
    }
}