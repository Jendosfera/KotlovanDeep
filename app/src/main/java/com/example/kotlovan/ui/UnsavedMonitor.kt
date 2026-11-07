package com.example.kotlovan.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.kotlovan.vm.AppViewModel
import kotlinx.coroutines.delay

/**
 * Тикает раз в 30 секунд: обновляет showUnsavedBanner
 * и при необходимости триггерит автосохранение в boundSaveUri.
 *
 * Вызывать один раз на верхнем уровне экрана (Viewer, Builder, Menu).
 */
@Composable
fun UnsavedMonitor(vm: AppViewModel) {
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            vm.tickUnsavedMonitor()
        }
    }
}

/**
 * Баннер «есть несохранённые изменения» вверху экрана.
 * Показывается, если vm.showUnsavedBanner == true.
 */
@Composable
fun UnsavedBanner(vm: AppViewModel) {
    if (!vm.showUnsavedBanner) return
    androidx.compose.foundation.layout.Box(
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidthCompat()
            .backgroundCompat()
            .paddingCompat()
    ) {
        androidx.compose.material3.Text(
            "Есть несохранённые изменения",
            color = androidx.compose.ui.graphics.Color.White
        )
    }
}

// Мини-хелперы, чтобы не засорять импорты выше
private fun androidx.compose.ui.Modifier.fillMaxWidthCompat(): androidx.compose.ui.Modifier =
    this.then(androidx.compose.foundation.layout.fillMaxWidth())

private fun androidx.compose.ui.Modifier.backgroundCompat(): androidx.compose.ui.Modifier =
    this.then(androidx.compose.foundation.background(
        androidx.compose.ui.graphics.Color(0xFFFF5722)
    ))

private fun androidx.compose.ui.Modifier.paddingCompat(): androidx.compose.ui.Modifier =
    this.then(androidx.compose.foundation.layout.padding(
        androidx.compose.ui.unit.dp(8f)
    ))