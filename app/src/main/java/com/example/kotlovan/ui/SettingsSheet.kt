package com.example.kotlovan.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    iconSize: Float,
    iconOpacity: Float,
    onSizeChange: (Float) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                "Размер иконок: ${(iconSize * 10).roundToInt() / 10f}×",
                style = MaterialTheme.typography.titleMedium
            )
            Slider(
                value = iconSize,
                onValueChange = onSizeChange,
                valueRange = 0.1f..3f
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "Прозрачность иконок: ${(iconOpacity * 100).roundToInt()}%",
                style = MaterialTheme.typography.titleMedium
            )
            Slider(
                value = iconOpacity,
                onValueChange = onOpacityChange,
                valueRange = 0.1f..1f
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}