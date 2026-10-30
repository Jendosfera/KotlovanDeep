package com.example.kotlovan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kotlovan.data.CellData
import com.example.kotlovan.vm.AppViewModel

@Composable
internal fun ViewerTopBar(
    vm: AppViewModel,
    currentCell: CellData?,
    maxDepth: Int,
    depth: Int,
    menuOpen: Boolean,
    inLinkMode: Boolean,
    baseIconDp: Dp,
    iconMod: Modifier,
    onBack: () -> Unit,
    onFlash: () -> Unit,
    onTogglePhoto: () -> Unit,
    onToggleLayer: () -> Unit,
    onToggleArray: () -> Unit,
    onMenuOpenToggle: () -> Unit,
    onMenuDismiss: () -> Unit,
    onChangeLink: () -> Unit,
    onSetCommonPoint: () -> Unit,
    onOwnPositionToggle: () -> Unit,
    onPlaceComment: () -> Unit,
    onSettings: () -> Unit
) {
    val cfg = vm.config
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedIconButton(
            onClick = onBack,
            icon = Icons.Default.ArrowBack, description = "Назад", iconModifier = iconMod
        )
        Spacer(Modifier.weight(1f))
        OutlinedIconButton(
            onClick = onFlash,
            icon = Icons.Default.FlashOn, description = "Фонарик", iconModifier = iconMod
        )
        if (!inLinkMode) {
            Spacer(Modifier.width(4.dp))
            val photoIconSize = if (vm.photoModeActive) baseIconDp * 1.4f else baseIconDp
            val photoTint = if (vm.photoModeActive) Color(0xFF4CAF50) else Color.White
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(onClick = onTogglePhoto),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PhotoCamera, "Фотофиксация", tint = photoTint,
                    modifier = Modifier.size(photoIconSize).alpha(cfg.iconOpacity))
            }
            if (maxDepth > 0) {
                Spacer(Modifier.width(4.dp))
                Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .background(YellowColor)
                        .border(5.dp, BlueColor, CircleShape)
                        .clickable(onClick = onToggleLayer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Layers, "Слой $depth/$maxDepth", tint = BlueColor)
                }
            }
            Spacer(Modifier.width(4.dp))
            OutlinedIconButton(
                onClick = onToggleArray,
                icon = if (vm.useAux) Icons.Default.LockOpen else Icons.Default.Lock,
                description = "Массив", iconModifier = iconMod
            )
        }
        Spacer(Modifier.width(4.dp))
        Box {
            OutlinedIconButton(
                onClick = onMenuOpenToggle,
                icon = Icons.Default.MoreVert, description = "Меню", iconModifier = iconMod
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = onMenuDismiss) {
                if (inLinkMode) {
                    DropdownMenuItem(text = { Text("Изменить привязку") }, onClick = {
                        onMenuDismiss()
                        onChangeLink()
                    })
                } else {
                    DropdownMenuItem(text = { Text("Общая точка") }, onClick = {
                        onMenuDismiss(); onSetCommonPoint()
                    })
                    DropdownMenuItem(
                        text = { Text("Свое положение: ${currentCell?.ownPosition ?: false}") },
                        onClick = {
                            onMenuDismiss()
                            onOwnPositionToggle()
                        }
                    )
                    DropdownMenuItem(text = { Text("Установить комментарий") }, onClick = {
                        onMenuDismiss(); onPlaceComment()
                    })
                    DropdownMenuItem(text = { Text("Настройки") }, onClick = {
                        onMenuDismiss(); onSettings()
                    })
                }
            }
        }
    }
}