package com.example.kotlovan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kotlovan.vm.AppViewModel

@Composable
internal fun ViewerSideButtons(
    vm: AppViewModel,
    row: Int,
    col: Int,
    rowRange: IntRange,
    colRange: IntRange,
    showAllRow: Boolean,
    baseIconDp: Dp,
    onRowClick: (Int) -> Unit,
    onColClick: (Int) -> Unit,
    onRowLong: (Int) -> Unit,
    onColLong: (Int) -> Unit,
    onToggleAllRow: () -> Unit
) {
    val cfg = vm.config
    val selectedColor = if (vm.photoModeActive) Color(0xFF4CAF50) else BlueColor

    Column(
        Modifier.align(Alignment.CenterStart)
            .padding(start = 4.dp, top = 60.dp, bottom = 100.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (r in rowRange) {
            val isEmpty = !vm.hasCell(r, col)
            SideButton(
                text = vm.active.rowLabels[r] ?: "R${r + 1}",
                selected = (r == row), dimmed = isEmpty,
                selectedColor = selectedColor,
                onClick = { if (!isEmpty) onRowClick(r) },
                onLongClick = { onRowLong(r) }
            )
        }
    }
    Row(
        Modifier.align(Alignment.BottomCenter)
            .padding(start = 60.dp, end = 8.dp, bottom = 12.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (c in colRange) {
            val isEmpty = !vm.hasCell(row, c)
            SideButton(
                text = vm.active.colLabels[c] ?: "C${c + 1}",
                selected = (c == col), dimmed = isEmpty,
                selectedColor = selectedColor,
                onClick = { if (!isEmpty) onColClick(c) },
                onLongClick = { onColLong(c) }
            )
        }
    }
    Box(
        Modifier.align(Alignment.BottomStart).padding(16.dp).size(48.dp)
            .clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onToggleAllRow() })
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (showAllRow) Icons.Default.Visibility else Icons.Default.VisibilityOff,
            "Все строки", tint = Color.White,
            modifier = Modifier.size(baseIconDp).alpha(cfg.iconOpacity)
        )
    }
}