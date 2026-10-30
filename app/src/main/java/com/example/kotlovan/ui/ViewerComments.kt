package com.example.kotlovan.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.kotlovan.data.CommentPin

@Composable
internal fun CrosshairBlack(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(48.dp)) {
        val cx = size.width / 2
        val cy = size.height / 2
        val stroke = 2.dp.toPx()
        val short = 8.dp.toPx()
        val long = 20.dp.toPx()
        drawLine(Color.Black, Offset(cx - long, cy), Offset(cx - short, cy), stroke)
        drawLine(Color.Black, Offset(cx + short, cy), Offset(cx + long, cy), stroke)
        drawLine(Color.Black, Offset(cx, cy - long), Offset(cx, cy - short), stroke)
        drawLine(Color.Black, Offset(cx, cy + short), Offset(cx, cy + long), stroke)
        drawCircle(Color.Black, radius = 3.dp.toPx(), center = Offset(cx, cy))
    }
}

@Composable
internal fun CommentPinRender(
    pin: CommentPin, scale: Float, offset: Offset,
    fitW: Float, fitH: Float,
    showTitle: Boolean, dimmed: Boolean = false
) {
    if (pin.x !in 0f..1f || pin.y !in 0f..1f) return
    val density = LocalDensity.current
    val screenX = pin.x * fitW * scale + offset.x
    val screenY = pin.y * fitH * scale + offset.y
    val diameterPx = pin.radius * fitW * scale * 2f
    val diameterDp = with(density) { diameterPx.toDp() }
    val alpha = if (dimmed) 0.5f else 1f
    val inner = diameterDp / 2

    Box(
        Modifier
            .offset { IntOffset((screenX - diameterPx / 2f).toInt(), (screenY - diameterPx / 2f).toInt()) }
            .size(diameterDp)
            .alpha(alpha)
    ) {
        Box(Modifier.fillMaxSize().background(Color(pin.strokeColor.toInt()), CircleShape))
        Box(
            Modifier.fillMaxSize().padding(diameterDp / 4),
            contentAlignment = Alignment.Center
        ) {
            CommentIconShape(type = pin.iconType, color = Color(pin.color.toInt()), sizeDp = inner)
        }
        if (pin.shortLabel.isNotBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    pin.shortLabel, color = Color.Black,
                    fontSize = with(density) { (diameterPx * 0.35f).toSp() },
                    maxLines = 1
                )
            }
        }
    }
    if (showTitle && pin.title.isNotBlank()) {
        val titleFontPx = (diameterPx * 0.35f).coerceAtLeast(with(density) { 10.dp.toPx() })
        Text(
            text = pin.title, color = Color.White,
            fontSize = with(density) { titleFontPx.toSp() }, maxLines = 1,
            modifier = Modifier
                .offset { IntOffset((screenX - diameterPx / 2f).toInt(),
                    (screenY - diameterPx / 2f - titleFontPx - 4f).toInt()) }
                .background(Color(0xAA000000), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}