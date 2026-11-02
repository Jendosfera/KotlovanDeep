package com.example.kotlovan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.kotlovan.data.CommentIconType
import com.example.kotlovan.data.CommentPhoto
import com.example.kotlovan.vm.AppViewModel
import kotlin.math.min

@Composable
internal fun IconTypeCell(
    type: CommentIconType, selected: Boolean,
    color: Color, onClick: () -> Unit
) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) Color(0xFFE0E0E0) else Color.White)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) Color.Black else Color.LightGray,
                RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) { CommentIconShape(type = type, color = color, sizeDp = 24.dp) }
}

@Composable
internal fun CommentPhotoThumb(
    photo: CommentPhoto,
    vm: AppViewModel,
    onView: () -> Unit,
    onEdit: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AsyncImage(
            model = vm.repo.photoFile(photo.file),
            contentDescription = photo.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFECEFF1))
                .pointerInput(photo.id) {
                    detectTapGestures(
                        onTap = { onView() },
                        onDoubleTap = { onEdit() },
                        onLongPress = { onEdit() }
                    )
                }
        )
        if (photo.title.isNotBlank()) {
            Text(
                photo.title,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                maxLines = 1,
                modifier = Modifier.width(80.dp).padding(top = 2.dp)
            )
        }
    }
}

@Composable
internal fun PhotosRow(
    photos: List<CommentPhoto>,
    vm: AppViewModel,
    onView: (CommentPhoto) -> Unit,
    onEdit: (CommentPhoto) -> Unit
) {
    if (photos.isEmpty()) return
    Spacer(Modifier.height(12.dp))
    Text("Фотографии:", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(6.dp))
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (photo in photos) {
            CommentPhotoThumb(
                photo = photo, vm = vm,
                onView = { onView(photo) },
                onEdit = { onEdit(photo) }
            )
        }
    }
}

@Composable
internal fun ViewPhotoDialog(
    photo: CommentPhoto,
    vm: AppViewModel,
    onDismiss: () -> Unit
) {
    var photoW by remember(photo.id) { mutableStateOf(0) }
    var photoH by remember(photo.id) { mutableStateOf(0) }
    var pScale by remember(photo.id) { mutableFloatStateOf(1f) }
    var pOffset by remember(photo.id) { mutableStateOf(Offset.Zero) }

    androidx.compose.runtime.LaunchedEffect(photo.id) {
        val f = vm.repo.photoFile(photo.file)
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(f.absolutePath, opts)
        photoW = opts.outWidth
        photoH = opts.outHeight
    }

    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.95f))
        ) {
            val containerW = constraints.maxWidth.toFloat()
            val containerH = constraints.maxHeight.toFloat()
            val density = LocalDensity.current
            val fitScale = if (photoW > 0 && photoH > 0)
                min(containerW / photoW, containerH / photoH) else 1f
            val fitW = if (photoW > 0) photoW * fitScale else containerW
            val fitH = if (photoH > 0) photoH * fitScale else containerH

            Box(
                Modifier.fillMaxSize().clipToBounds()
                    .pointerInput(photo.id, fitW, fitH) {
                        com.example.kotlovan.util.detectViewerGestures(
                            getViewport = { com.example.kotlovan.util.Viewport(pScale, pOffset) },
                            fitW = fitW, fitH = fitH,
                            containerW = containerW, containerH = containerH,
                            onTransform = { vp -> pScale = vp.scale; pOffset = vp.offset },
                            onSwipe = { _, _ -> }
                        )
                    }
            ) {
                if (photoW > 0 && photoH > 0) {
                    ZoomableRegionImage(
                        file = vm.repo.photoFile(photo.file),
                        imageW = photoW, imageH = photoH,
                        containerW = containerW, containerH = containerH,
                        scale = pScale, offsetX = pOffset.x, offsetY = pOffset.y,
                        fitW = fitW, fitH = fitH,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Box(
                Modifier.align(Alignment.TopEnd).padding(16.dp).size(44.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Text("×", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun EditPhotoDialog(
    photo: CommentPhoto,
    onSave: (title: String, comment: String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var t by remember(photo.id) { mutableStateOf(photo.title) }
    var c by remember(photo.id) { mutableStateOf(photo.comment) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Фотография") },
        text = {
            Column {
                OutlinedTextField(
                    value = t, onValueChange = { t = it },
                    label = { Text("Название") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = c, onValueChange = { c = it },
                    label = { Text("Комментарий") },
                    modifier = Modifier.fillMaxWidth().height(100.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(t, c) }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Удалить", color = Color.Red) }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        }
    )
}