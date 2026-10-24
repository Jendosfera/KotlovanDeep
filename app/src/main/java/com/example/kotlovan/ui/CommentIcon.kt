package com.example.kotlovan.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kotlovan.data.CommentIconType

// ─────────── Иконка комментария (круг/квадрат/треугольник/.../телефон/комп) ───────────

@Composable
fun CommentIconShape(
    type: CommentIconType,
    color: Color,
    sizeDp: Dp,
    modifier: Modifier = Modifier,
    alpha: Float = 1f
) {
    when (type) {
        CommentIconType.CIRCLE -> Box(
            modifier.size(sizeDp).alpha(alpha).clip(CircleShape).background(color)
        )
        CommentIconType.SQUARE -> Box(
            modifier.size(sizeDp).alpha(alpha).clip(RoundedCornerShape(sizeDp * 0.15f)).background(color)
        )
        CommentIconType.TRIANGLE -> Canvas(modifier.size(sizeDp).alpha(alpha)) {
            val p = Path().apply {
                moveTo(size.width / 2f, 0f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(p, color)
        }
        CommentIconType.DIAMOND -> Canvas(modifier.size(sizeDp).alpha(alpha)) {
            val p = Path().apply {
                moveTo(size.width / 2f, 0f)
                lineTo(size.width, size.height / 2f)
                lineTo(size.width / 2f, size.height)
                lineTo(0f, size.height / 2f)
                close()
            }
            drawPath(p, color)
        }
        CommentIconType.ROSETTE -> Icon(
            Icons.Default.Star, null, tint = color,
            modifier = modifier.size(sizeDp).alpha(alpha)
        )
        CommentIconType.PHONE -> Icon(
            Icons.Default.Phone, null, tint = color,
            modifier = modifier.size(sizeDp).alpha(alpha)
        )
        CommentIconType.COMPUTER -> Icon(
            Icons.Default.Computer, null, tint = color,
            modifier = modifier.size(sizeDp).alpha(alpha)
        )
    }
}

// ─────────── Иконка режима глаза ───────────

enum class EyeMode { HIDDEN, BLINKING, SCALING_UP, SHOW_TITLES }

@Composable
fun EyeModeIcon(
    mode: EyeMode,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    iconSize: Dp = 28.dp
) {
    when (mode) {
        EyeMode.HIDDEN -> Icon(
            androidx.compose.material.icons.Icons.Default.VisibilityOff,
            "Скрыто", tint = tint, modifier = modifier
        )
        EyeMode.BLINKING -> Icon(
            androidx.compose.material.icons.Icons.Default.Visibility,
            "Мерцание", tint = tint, modifier = modifier
        )
        EyeMode.SCALING_UP -> WidePupilEye(modifier = modifier, tint = tint)
        EyeMode.SHOW_TITLES -> LetterAEye(modifier = modifier, tint = tint, iconSize = iconSize)
    }
}

/** Глаз с большим зрачком (широкий). */
@Composable
private fun WidePupilEye(modifier: Modifier, tint: Color) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2
        val cy = h / 2
        val eyeW = w * 0.9f
        val eyeH = h * 0.45f

        drawOval(
            color = tint,
            topLeft = Offset((w - eyeW) / 2, (h - eyeH) / 2),
            size = Size(eyeW, eyeH),
            style = Stroke(width = h * 0.07f)
        )
        drawCircle(color = tint, radius = h * 0.22f, center = Offset(cx, cy))
    }
}

/** Глаз с буквой «a» в зрачке. */
@Composable
private fun LetterAEye(modifier: Modifier, tint: Color, iconSize: Dp) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2
            val cy = h / 2
            val eyeW = w * 0.9f
            val eyeH = h * 0.45f

            drawOval(
                color = tint,
                topLeft = Offset((w - eyeW) / 2, (h - eyeH) / 2),
                size = Size(eyeW, eyeH),
                style = Stroke(width = h * 0.07f)
            )
            // Тёмный зрачок под буквой
            drawCircle(color = Color.Black, radius = h * 0.2f, center = Offset(cx, cy))
        }
        Text(
            "a",
            color = tint,
            fontWeight = FontWeight.Bold,
            fontSize = (iconSize.value * 0.35f).sp
        )
    }
}