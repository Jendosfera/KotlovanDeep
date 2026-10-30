package com.example.kotlovan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kotlovan.data.CommentPin
import com.example.kotlovan.vm.AppViewModel

internal enum class ViewerSubMode { VIEW, SET_COMMON_POINT, PLACE_COMMENT }
internal enum class SwipeDir { NONE, LEFT, RIGHT, UP, DOWN }

internal data class PendingJump(val x: Float, val y: Float, val scale: Float)

internal val LinkBorderColor = Color(0xFF00C853)
internal val YellowColor = Color(0xFFFFEB3B)
internal val BlueColor = Color(0xFF1565C0)

internal fun findPinAt2(
    currentRow: Int, currentCol: Int, currentDepth: Int, showAllRow: Boolean,
    pos: Offset, fitW: Float, fitH: Float, scale: Float, offset: Offset,
    vm: AppViewModel
): Triple<Int, Int, CommentPin>? {
    val currentCell = vm.cell(currentRow, currentCol, currentDepth) ?: return null
    for (pin in currentCell.comments.reversed()) {
        val cx = pin.x * fitW * scale + offset.x
        val cy = pin.y * fitH * scale + offset.y
        val rad = pin.radius * fitW * scale * 2f
        val dx = pos.x - cx; val dy = pos.y - cy
        if (dx * dx + dy * dy <= rad * rad) return Triple(currentRow, currentCol, pin)
    }
    if (showAllRow) {
        val cols = vm.active.cells.keys.mapNotNull { k ->
            val p = k.split(",")
            if (p.getOrNull(0)?.toIntOrNull() == currentRow) p.getOrNull(1)?.toIntOrNull() else null
        }.distinct().sorted()
        for (c in cols) {
            if (c == currentCol) continue
            val oc = vm.cell(currentRow, c, 0) ?: continue
            for (pin in oc.comments.reversed()) {
                val dx = pin.x - oc.commonX
                val dy = pin.y - oc.commonY
                val ax = currentCell.commonX + dx
                val ay = currentCell.commonY + dy
                if (ax !in 0f..1f || ay !in 0f..1f) continue
                val cx = ax * fitW * scale + offset.x
                val cy = ay * fitH * scale + offset.y
                val rad = pin.radius * fitW * scale * 2f
                val ddx = pos.x - cx; val ddy = pos.y - cy
                if (ddx * ddx + ddy * ddy <= rad * rad) return Triple(currentRow, c, pin)
            }
        }
    }
    return null
}

@Composable
internal fun SideButton(
    text: String, selected: Boolean, dimmed: Boolean = false,
    selectedColor: Color = BlueColor,
    onClick: () -> Unit, onLongClick: () -> Unit
) {
    val baseScale = if (dimmed) 0.8f else 1f
    Box(
        Modifier
            .size(width = 52.dp, height = 40.dp)
            .scale(if (selected) 1.15f else baseScale)
            .alpha(if (dimmed) 0.5f else 1f)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) selectedColor else Color.Black.copy(alpha = 0.5f))
            .pointerInput(text, selected, dimmed) {
                detectTapGestures(onTap = { onClick() }, onLongPress = { onLongClick() })
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text, color = Color.White, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
internal fun OutlinedIconButton(
    onClick: () -> Unit, icon: ImageVector, description: String,
    iconModifier: Modifier = Modifier, modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, description, tint = Color.White, modifier = iconModifier) }
}