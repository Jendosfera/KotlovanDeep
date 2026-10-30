package com.example.kotlovan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun BoxScope.ArrayBuilderTopBar(
    aux: Boolean,
    onBack: () -> Unit,
    onToggleArray: () -> Unit,
    onRating: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape)
                .background(Yellow).border(7.dp, Color.Black, CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.ArrowBack, "Назад", tint = Blue) }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(44.dp).clip(CircleShape)
                .background(Yellow).border(7.dp, Color.Black, CircleShape)
                .clickable(onClick = onToggleArray),
            contentAlignment = Alignment.Center
        ) { Icon(if (aux) Icons.Default.LockOpen else Icons.Default.Lock, "Массив", tint = Blue) }
        Spacer(Modifier.width(4.dp))
        Box(
            Modifier.size(44.dp).clip(CircleShape)
                .background(Yellow).border(7.dp, Color.Black, CircleShape)
                .clickable(onClick = onRating),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Star, "Рейтинг", tint = Blue) }
    }
}