package com.example.kotlovan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.kotlovan.data.CommentPin

@Composable
internal fun ViewerContextMenu(
    pos: Offset,
    pins: List<CommentPin>,
    photoModeActive: Boolean,
    onClose: () -> Unit,
    onSaveArchive: () -> Unit,
    onOpenArchive: () -> Unit,
    onOpenReport: () -> Unit,
    onPinClick: (CommentPin) -> Unit
) {
    val density = LocalDensity.current
    val menuWidthPx = with(density) { 220.dp.toPx() }
    val closeRadiusPx = with(density) { 24.dp.toPx() }
    val closeBtnSize = with(density) { 48.dp.toPx() }

    Column(
        Modifier
            .offset { IntOffset((pos.x - menuWidthPx).toInt(), pos.y.toInt()) }
            .width(220.dp)
            .background(YellowColor, RoundedCornerShape(8.dp))
            .border(7.dp, BlueColor, RoundedCornerShape(8.dp))
            .padding(8.dp)
            .zIndex(1f)
    ) {
        if (photoModeActive && pins.isEmpty()) {
            Text("Сохранить архив",
                Modifier.fillMaxWidth().clickable(onClick = onSaveArchive).padding(vertical = 8.dp),
                color = Color.Black)
            Text("Загрузить архив",
                Modifier.fillMaxWidth().clickable(onClick = onOpenArchive).padding(vertical = 8.dp),
                color = Color.Black)
            Text("Создать фотоотчёт",
                Modifier.fillMaxWidth().clickable(onClick = onOpenReport).padding(vertical = 8.dp),
                color = Color.Black)
        } else if (pins.isEmpty()) {
            Text("Нет комментариев", color = Color.Black)
        } else {
            for (pin in pins) {
                Text(
                    pin.title.ifBlank { pin.shortLabel.ifBlank { "Комментарий" } },
                    modifier = Modifier.fillMaxWidth().clickable {
                        onPinClick(pin)
                    }.padding(vertical = 6.dp),
                    color = Color.Black
                )
            }
        }
    }
    Box(
        Modifier
            .offset { IntOffset((pos.x - closeRadiusPx).toInt(), (pos.y - closeRadiusPx).toInt()) }
            .size(with(density) { closeBtnSize.toDp() })
            .background(Color.Red, CircleShape)
            .border(7.dp, Color.Black, CircleShape)
            .clickable(onClick = onClose)
            .zIndex(2f),
        contentAlignment = Alignment.Center
    ) { Text("×", color = Color.Black, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
}