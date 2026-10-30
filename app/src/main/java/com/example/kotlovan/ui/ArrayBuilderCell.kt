package com.example.kotlovan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.kotlovan.data.CellData
import com.example.kotlovan.vm.AppViewModel

internal const val CELL_SIZE = 160f
internal const val CELL_GAP = 24f
internal const val TITLE_H_DP = 24f
internal val Yellow = Color(0xFFFFEB3B)
internal val Blue = Color(0xFF1565C0)

internal fun hasNeighbor(r: Int, c: Int, coords: Set<Pair<Int, Int>>): Boolean =
    (r - 1 to c) in coords || (r + 1 to c) in coords ||
    (r to c - 1) in coords || (r to c + 1) in coords

@Composable
internal fun CreatedCell(
    data: CellData, vm: AppViewModel,
    isSelected: Boolean, isSwapTarget: Boolean,
    isSelectMode: Boolean, isSource: Boolean, depth: Int,
    onClick: () -> Unit, onDoubleTap: () -> Unit, onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        isSource && isSelectMode -> Color(0xFF9E9E9E)
        isSelected -> Color(0xFF00C853)
        isSwapTarget -> Color(0xFFFFE082)
        else -> Color.Transparent
    }
    val shape = RoundedCornerShape(12.dp)
    val containerColor = if (depth == 0) Color.White else Color(0xFFFFF9C4)
    Card(
        shape = shape,
        elevation = CardDefaults.cardElevation(6.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = modifier.fillMaxSize()
            .then(
                if (isSelected || isSwapTarget || (isSource && isSelectMode))
                    Modifier.border(3.dp, borderColor, shape) else Modifier
            )
            .pointerInput(depth) {
                detectTapGestures(
                    onDoubleTap = { onDoubleTap() },
                    onLongPress = { onLongPress() },
                    onTap = { onClick() }
                )
            }
    ) {
        Column(Modifier.fillMaxSize().padding(6.dp)) {
            Text(
                if (data.title.isBlank())
                    "Без названия" + if (depth > 0) " (вложенная)" else ""
                else data.title,
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier.fillMaxWidth().weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFCFD8DC))
            ) {
                data.imageFile?.let { f ->
                    AsyncImage(
                        model = vm.repo.imageFile(f),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
internal fun PlusButton(onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(6.dp),
        colors = CardDefaults.cardColors(containerColor = Blue),
        modifier = Modifier.fillMaxSize()
            .border(7.dp, Color.Black, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.Add, "Добавить",
                tint = Yellow, modifier = Modifier.size(72.dp)
            )
        }
    }
}