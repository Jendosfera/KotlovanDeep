package com.example.kotlovan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.kotlovan.vm.AppViewModel
import kotlinx.coroutines.delay

@Composable
fun UnsavedMonitor(vm: AppViewModel) {
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            vm.tickUnsavedMonitor()
        }
    }
}

@Composable
fun UnsavedBanner(vm: AppViewModel) {
    if (!vm.showUnsavedBanner) return
    Box(
        Modifier.fillMaxWidth().background(Color(0xFFFF5722)).padding(8.dp)
    ) {
        Text("Есть несохранённые изменения", color = Color.White)
    }
}