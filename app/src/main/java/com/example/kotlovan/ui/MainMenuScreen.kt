package com.example.kotlovan.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.kotlovan.R
import com.example.kotlovan.vm.AppViewModel

@Composable
fun MainMenuScreen(nav: NavHostController, vm: AppViewModel) {
    val ctx = LocalContext.current
    var showReconnectDialog by remember { mutableStateOf(false) }
    var reconnectIdx by remember { mutableIntStateOf(0) }

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
                    reconnectIdx = 0
                    showReconnectDialog = true
                } else {
                    nav.navigate("builder?aux=false")
                }
            } else {
                Toast.makeText(ctx, "Не удалось прочитать файл", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val reconnectLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val item = vm.pendingReconnect.getOrNull(reconnectIdx)
            if (item != null) vm.reconnectImage(item.first, item.second, uri)
        }
        reconnectIdx++
    }

    LaunchedEffect(showReconnectDialog, reconnectIdx, vm.pendingReconnect.size) {
        if (showReconnectDialog && reconnectIdx >= vm.pendingReconnect.size &&
            vm.pendingReconnect.isNotEmpty()) {
            showReconnectDialog = false
            reconnectIdx = 0
            vm.clearPendingReconnect()
            nav.navigate("builder?aux=false")
        }
    }

    // Основной контейнер
    Box(modifier = Modifier.fillMaxSize()) {
        
        // 1. Фоновая текстура чертежа
        Image(
            painter = painterResource(id = R.drawable.blueprint_bg),
            contentDescription = "Фон чертежной бумаги",
            contentScale = ContentScale.Crop, // Заполняет весь экран
            modifier = Modifier.fillMaxSize()
        )

        // 2. Интерфейс поверх фона
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Заголовок (сделаем его белым с тенью, чтобы читался на синем фоне)
            Text(
                text = "Котлован",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                style = androidx.compose.material3.LocalTextStyle.current.copy(
                    shadow = Shadow(
                        color = Color.Black,
                        offset = Offset(4f, 4f),
                        blurRadius = 8f
                    )
                )
            )
            Spacer(Modifier.height(48.dp))

            // Если есть сохраненный массив — показываем кнопку "Продолжить"
            if (vm.config.main.cells.isNotEmpty() || vm.config.aux.cells.isNotEmpty()) {
                BlueprintButton(
                    text = "Продолжить",
                    onClick = {
                        vm.autosave()
                        nav.navigate("viewer")
                    }
                )
                Spacer(Modifier.height(16.dp))
            }

            // Кнопка "Создать массив"
            BlueprintButton(
                text = "Создать массив",
                onClick = {
                    vm.newArray()
                    nav.navigate("builder?aux=false")
                }
            )
            Spacer(Modifier.height(16.dp))

            // Кнопка "Загрузить массив"
            BlueprintButton(
                text = "Загрузить массив",
                onClick = { 
                    loadLauncher.launch(arrayOf("application/zip", "application/json", "application/octet-stream", "*/*")) 
                }
            )
            Spacer(Modifier.height(32.dp))

            // Кнопка "Настроить вид"
            BlueprintButton(
                text = "Настроить вид",
                height = 56.dp, // Чуть меньше по высоте
                fontSize = 16.sp,
                onClick = { nav.navigate("settings") }
            )
        }
    }

    if (showReconnectDialog && reconnectIdx < vm.pendingReconnect.size) {
        val total = vm.pendingReconnect.size
        val current = reconnectIdx + 1
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Восстановить доступ к изображениям") },
            text = {
                Text(
                    "После переустановки приложения $total изображений потеряли доступ. " +
                    "Выберите их заново. Файл $current из $total."
                )
            },
            confirmButton = {
                TextButton(onClick = { reconnectLauncher.launch(arrayOf("image/*")) }) {
                    Text("Выбрать файл")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showReconnectDialog = false
                    reconnectIdx = 0
                    vm.clearPendingReconnect()
                    nav.navigate("builder?aux=false")
                }) { Text("Пропустить") }
            }
        )
    }
}

/**
 * Кастомная кнопка для главного меню.
 * Желтый фон, черная обводка 7dp, черный текст.
 */
@Composable
private fun BlueprintButton(
    text: String,
    onClick: () -> Unit,
    height: androidx.compose.ui.unit.Dp = 64.dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 20.sp
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(height),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFFFEB3B), // Желтый
            contentColor = Color.Black          // Черный текст
        ),
        border = BorderStroke(7.dp, Color.Black), // Черная кайма 7dp
        shape = RoundedCornerShape(8.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 8.dp, 
            pressedElevation = 2.dp
        )
    ) {
        Text(
            text = text, 
            fontSize = fontSize, 
            fontWeight = FontWeight.Bold
        )
    }
}