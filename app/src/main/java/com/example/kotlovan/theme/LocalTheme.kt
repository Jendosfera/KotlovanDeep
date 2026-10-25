package com.example.kotlovan.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.example.kotlovan.data.BackgroundSpec
import com.example.kotlovan.data.BgType
import com.example.kotlovan.data.ButtonShape
import com.example.kotlovan.data.ButtonStyle
import com.example.kotlovan.data.PatternPreset
import com.example.kotlovan.data.ThemeProfile
import kotlin.random.Random

val LocalTheme = compositionLocalOf { ThemeProfile() }

@Composable
fun ThemeScope(profile: ThemeProfile, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTheme provides profile, content = content)
}

fun ButtonStyle.toShape(): Shape {
    return when (shape) {
        ButtonShape.ROUNDED -> RoundedCornerShape(cornerRadius.dp)
        ButtonShape.PILL -> RoundedCornerShape(50)
        ButtonShape.SQUARE -> RoundedCornerShape(0.dp)
        ButtonShape.DIAMOND -> GenericShape { size, _ ->
            val w = size.width
            val h = size.height
            moveTo(w / 2f, 0f)
            lineTo(w, h / 2f)
            lineTo(w / 2f, h)
            lineTo(0f, h / 2f)
            close()
        }
        ButtonShape.HEXAGON -> GenericShape { size, _ ->
            val w = size.width
            val h = size.height
            val cut = h * 0.25f
            moveTo(cut, 0f)
            lineTo(w - cut, 0f)
            lineTo(w, h / 2f)
            lineTo(w - cut, h)
            lineTo(cut, h)
            lineTo(0f, h / 2f)
            close()
        }
    }
}

/** Возвращает Modifier с текущим themed-фоном. Использовать: `Modifier.fillMaxSize().themedBackground()`. */
@Composable
fun Modifier.themedBackground(): Modifier {
    val bg = LocalTheme.current.background
    return remember(bg) { this.drawBehind { drawBackground(bg) } }
}

private fun DrawScope.drawBackground(bg: BackgroundSpec) {
    when (bg.type) {
        BgType.SOLID -> drawRect(Color(bg.color.toInt()))

        BgType.GRADIENT -> drawRect(
            Brush.linearGradient(
                colors = listOf(Color(bg.gradientStart.toInt()), Color(bg.gradientEnd.toInt())),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
        )

        BgType.PATTERN -> {
            drawRect(Color(bg.color.toInt()))
            drawPattern(bg.patternPreset, bg.patternIntensity)
        }
    }
}

private fun DrawScope.drawPattern(preset: PatternPreset, intensity: Float) {
    when (preset) {
        PatternPreset.NONE -> {}
        PatternPreset.GRID -> {
            val c = Color.Black.copy(alpha = 0.15f * intensity)
            val step = 40.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(c, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx()); x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(c, Offset(0f, y), Offset(size.width, y), 1.dp.toPx()); y += step
            }
        }
        PatternPreset.DOTS -> {
            val c = Color.Black.copy(alpha = 0.25f * intensity)
            val step = 24.dp.toPx()
            val r = 1.5.dp.toPx()
            var x = 0f
            while (x < size.width) {
                var y = 0f
                while (y < size.height) {
                    drawCircle(c, r, Offset(x, y)); y += step
                }
                x += step
            }
        }
        PatternPreset.PAPER -> {
            repeat(2500) {
                drawCircle(
                    Color.Black.copy(alpha = 0.01f + Random.nextFloat() * 0.03f * intensity),
                    radius = Random.nextFloat() * 1.4.dp.toPx(),
                    center = Offset(Random.nextFloat() * size.width, Random.nextFloat() * size.height)
                )
            }
        }
        PatternPreset.BLUEPRINT -> {
            drawRect(Color(0xFF0D47A1).copy(alpha = 0.15f))
            val c = Color.White.copy(alpha = 0.25f * intensity)
            val step = 30.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(c, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx()); x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(c, Offset(0f, y), Offset(size.width, y), 1.dp.toPx()); y += step
            }
        }
        PatternPreset.CARBON -> {
            val step = 8.dp.toPx()
            var y = 0f
            while (y < size.height) {
                var x = 0f
                while (x < size.width) {
                    val offset = if ((x / step).toInt() % 2 == 0) 0f else step / 2
                    drawRect(
                        Color.Black.copy(alpha = 0.15f * intensity),
                        topLeft = Offset(x + offset, y),
                        size = Size(step * 0.5f, step * 0.5f)
                    )
                    x += step
                }
                y += step
            }
        }
    }
}

/** Кнопка, оформленная по текущей теме. */
@Composable
fun ThemeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val style = LocalTheme.current.buttonStyle
    val colors = LocalTheme.current.colors
    val shape = remember(style) { style.toShape() }

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = style.elevation.dp,
            pressedElevation = style.pressedElevation.dp
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(colors.primary.toInt()),
            contentColor = Color(colors.onPrimary.toInt())
        ),
        border = if (style.strokeWidth > 0f)
            BorderStroke(style.strokeWidth.dp, Color(style.strokeColor.toInt()))
        else null,
        modifier = modifier,
        content = content
    )
}