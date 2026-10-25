package com.example.kotlovan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.kotlovan.data.BackgroundSpec
import com.example.kotlovan.data.BgType
import com.example.kotlovan.data.ButtonShape
import com.example.kotlovan.data.ButtonStyle
import com.example.kotlovan.data.PatternPreset
import com.example.kotlovan.data.ThemeProfile
import com.example.kotlovan.theme.LocalTheme
import com.example.kotlovan.theme.ThemeButton
import com.example.kotlovan.theme.themedBackground
import com.example.kotlovan.theme.toShape
import com.example.kotlovan.vm.AppViewModel

private val Palette: List<Long> = listOf(
    0xFF1A237E, 0xFF0D47A1, 0xFF1565C0, 0xFF0277BD, 0xFF006064, 0xFF004D40,
    0xFF1B5E20, 0xFF33691E, 0xFF827717, 0xFFE65100, 0xFFBF360C, 0xFF3E2723,
    0xFF37474F, 0xFF616161, 0xFF9E9E9E, 0xFFBDBDBD, 0xFFE0E0E0, 0xFFFFFFFF,
    0xFFF44336, 0xFFE91E63, 0xFF9C27B0, 0xFF673AB7, 0xFF3F51B5, 0xFF2196F3
)

@Composable
fun SettingsScreen(nav: NavHostController, vm: AppViewModel) {
    val profile = vm.themeProfile

    fun set(p: ThemeProfile) = vm.updateTheme(p)
    fun setBg(bg: BackgroundSpec) = set(profile.copy(background = bg))
    fun setBtn(b: ButtonStyle) = set(profile.copy(buttonStyle = b))

    Box(Modifier.fillMaxSize().themedBackground()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.Default.ArrowBack, "Назад")
                }
                Text("Настройки вида", style = MaterialTheme.typography.titleLarge)
            }

            Spacer(Modifier.height(16.dp))

            // ==== Фон ====
            SectionTitle("Фон")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BgTypeButton("Цвет", BgType.SOLID, profile.background.type) {
                    setBg(profile.background.copy(type = BgType.SOLID))
                }
                BgTypeButton("Градиент", BgType.GRADIENT, profile.background.type) {
                    setBg(profile.background.copy(type = BgType.GRADIENT))
                }
                BgTypeButton("Узор", BgType.PATTERN, profile.background.type) {
                    setBg(profile.background.copy(type = BgType.PATTERN))
                }
            }

            Spacer(Modifier.height(8.dp))

            when (profile.background.type) {
                BgType.SOLID -> ColorGrid(profile.background.color) {
                    setBg(profile.background.copy(color = it))
                }
                BgType.GRADIENT -> {
                    Text("Начало:", style = MaterialTheme.typography.bodySmall)
                    ColorGrid(profile.background.gradientStart) {
                        setBg(profile.background.copy(gradientStart = it))
                    }
                    Text("Конец:", style = MaterialTheme.typography.bodySmall)
                    ColorGrid(profile.background.gradientEnd) {
                        setBg(profile.background.copy(gradientEnd = it))
                    }
                }
                BgType.PATTERN -> {
                    Text("Узор:", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (p in listOf(
                            PatternPreset.GRID, PatternPreset.DOTS, PatternPreset.PAPER,
                            PatternPreset.BLUEPRINT, PatternPreset.CARBON
                        )) {
                            BgTypeButton(p.name, p, profile.background.patternPreset) {
                                setBg(profile.background.copy(patternPreset = p))
                            }
                        }
                    }
                    Text("Интенсивность: ${(profile.background.patternIntensity * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = profile.background.patternIntensity,
                        onValueChange = { setBg(profile.background.copy(patternIntensity = it)) },
                        valueRange = 0.05f..1f
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ==== Кнопки ====
            SectionTitle("Кнопки")
            Text("Форма:", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (s in ButtonShape.values()) {
                    ShapePreview(s, profile.buttonStyle.shape == s) {
                        setBtn(profile.buttonStyle.copy(shape = s))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("Радиус: ${profile.buttonStyle.cornerRadius.toInt()}",
                style = MaterialTheme.typography.bodySmall)
            Slider(
                value = profile.buttonStyle.cornerRadius,
                onValueChange = { setBtn(profile.buttonStyle.copy(cornerRadius = it)) },
                valueRange = 0f..40f
            )
            Text("Тень: ${profile.buttonStyle.elevation.toInt()}",
                style = MaterialTheme.typography.bodySmall)
            Slider(
                value = profile.buttonStyle.elevation,
                onValueChange = { setBtn(profile.buttonStyle.copy(elevation = it)) },
                valueRange = 0f..16f
            )
            Text("Обводка: ${profile.buttonStyle.strokeWidth}",
                style = MaterialTheme.typography.bodySmall)
            Slider(
                value = profile.buttonStyle.strokeWidth,
                onValueChange = { setBtn(profile.buttonStyle.copy(strokeWidth = it)) },
                valueRange = 0f..4f
            )

            Spacer(Modifier.height(8.dp))
            Text("Цвет обводки:", style = MaterialTheme.typography.bodySmall)
            ColorGrid(profile.buttonStyle.strokeColor) {
                setBtn(profile.buttonStyle.copy(strokeColor = it))
            }

            Spacer(Modifier.height(20.dp))

            // ==== Цвета ====
            SectionTitle("Основные цвета")
            Text("Основной (primary):", style = MaterialTheme.typography.bodySmall)
            ColorGrid(profile.colors.primary) {
                set(profile.copy(colors = profile.colors.copy(primary = it)))
            }
            Text("Текст на primary:", style = MaterialTheme.typography.bodySmall)
            ColorGrid(profile.colors.onPrimary) {
                set(profile.copy(colors = profile.colors.copy(onPrimary = it)))
            }
            Text("Акцент:", style = MaterialTheme.typography.bodySmall)
            ColorGrid(profile.colors.accent) {
                set(profile.copy(colors = profile.colors.copy(accent = it)))
            }

            Spacer(Modifier.height(24.dp))

            // ==== Превью ====
            SectionTitle("Предпросмотр")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ThemeButton(onClick = {}) { Text("Кнопка") }
                OutlinedButton(onClick = {}) { Text("Вторичная") }
            }

            Spacer(Modifier.height(24.dp))

            ThemeButton(
                onClick = { vm.resetTheme() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Сбросить к стандарту") }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun BgTypeButton(label: String, value: Any, current: Any, onClick: () -> Unit) {
    val selected = value == current
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .then(if (selected) Modifier.border(2.dp, Color(0xFF1565C0), RoundedCornerShape(8.dp)) else Modifier)
    ) { Text(label, style = MaterialTheme.typography.labelSmall) }
}

@Composable
private fun ColorGrid(selected: Long, onPick: (Long) -> Unit) {
    Column {
        for (row in Palette.chunked(6)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (c in row) {
                    val isSel = c == selected
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(c.toInt()))
                            .then(
                                if (isSel) Modifier.border(3.dp, Color.Black, CircleShape)
                                else Modifier.border(1.dp, Color(0x33000000), CircleShape)
                            )
                            .clickable { onPick(c) }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun ShapePreview(shape: ButtonShape, selected: Boolean, onClick: () -> Unit) {
    val style = LocalTheme.current.buttonStyle.copy(shape = shape)
    val s = style.toShape()
    Box(
        Modifier
            .size(48.dp)
            .clip(s)
            .background(if (selected) Color(0xFF1565C0) else Color(0xFFBDBDBD))
            .clickable(onClick = onClick)
    )
}