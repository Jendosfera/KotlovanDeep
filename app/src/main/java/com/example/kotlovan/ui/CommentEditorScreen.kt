package com.example.kotlovan.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.kotlovan.data.CellData
import com.example.kotlovan.data.CommentIconType
import com.example.kotlovan.data.CommentPhoto
import com.example.kotlovan.util.Viewport
import com.example.kotlovan.util.detectViewerGestures
import com.example.kotlovan.vm.AppViewModel
import java.util.UUID
import kotlin.math.min

private val PresetColors: List<Long> = listOf(
    0xFFFF5722L, 0xFFE91E63L, 0xFF9C27B0L, 0xFF3F51B5L, 0xFF2196F3L,
    0xFF00BCD4L, 0xFF009688L, 0xFF4CAF50L, 0xFFFFC107L, 0xFFFF9800L
)

@Composable
fun CommentEditorScreen(nav: NavHostController, vm: AppViewModel, row: Int, col: Int, id: String) {
    val cell = vm.cell(row, col) ?: return
    val pin = cell.comments.firstOrNull { it.id == id } ?: return

    var title by remember { mutableStateOf(pin.title) }
    var text by remember { mutableStateOf(pin.text) }
    var color by remember { mutableLongStateOf(pin.color) }
    var iconType by remember { mutableStateOf(pin.iconType) }
    var photos by remember { mutableStateOf(pin.photos) }
    var moving by remember { mutableStateOf(false) }
    var showStyleDialog by remember { mutableStateOf(false) }
    var viewPhoto by remember { mutableStateOf<CommentPhoto?>(null) }
    var editPhoto by remember { mutableStateOf<CommentPhoto?>(null) }
    var pendingPhotoName by remember { mutableStateOf<String?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val name = pendingPhotoName
        if (success && name != null) {
            photos = photos + CommentPhoto(id = UUID.randomUUID().toString(), file = name)
        }
        pendingPhotoName = null
    }

	val ctx = LocalContext.current

val takePictureLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.TakePicture()
	) { success ->
		val name = pendingPhotoName
		if (success && name != null) {
			photos = photos + CommentPhoto(id = UUID.randomUUID().toString(), file = name)
		} else if (!success && name != null) {
			Toast.makeText(ctx, "Фото не сохранено", Toast.LENGTH_SHORT).show()
		}
		pendingPhotoName = null
	}

	fun launchCameraActual() {
		val name = vm.repo.newPhotoName()
		pendingPhotoName = name
		try {
			val uri = vm.repo.photoUriForNew(name)
			takePictureLauncher.launch(uri)
		} catch (e: Exception) {
			pendingPhotoName = null
			Toast.makeText(ctx, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
		}
	}

	val cameraPermissionLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.RequestPermission()
	) { granted ->
		if (granted) launchCameraActual()
		else Toast.makeText(ctx, "Нужно разрешение на камеру", Toast.LENGTH_SHORT).show()
	}

	fun launchCamera() {
		val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
				PackageManager.PERMISSION_GRANTED
		if (granted) launchCameraActual()
		else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
	}

    fun launchCamera() {
        val name = vm.repo.newPhotoName()
        pendingPhotoName = name
        try {
            val uri = vm.repo.photoUriForNew(name)
            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            pendingPhotoName = null
        }
    }

    fun save() {
        vm.setComments(row, col, cell.comments.map {
            if (it.id == id) it.copy(
                title = title, text = text, color = color,
                iconType = iconType, photos = photos
            ) else it
        })
        vm.autosave()
        nav.popBackStack()
    }

    if (moving) {
        MoveCommentOverlay(
            cell = cell, vm = vm,
            initialX = pin.x, initialY = pin.y,
            onCancel = { moving = false },
            onConfirm = { nx, ny ->
                vm.setComments(row, col, cell.comments.map {
                    if (it.id == id) it.copy(x = nx, y = ny) else it
                })
                moving = false
            }
        )
        return
    }

    Column(
        Modifier.fillMaxSize().padding(8.dp).verticalScroll(rememberScrollState())
    ) {
        // === Верхняя панель ===
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, "Назад")
            }
            Text("Комментарий", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { launchCamera() }) {
                Icon(Icons.Default.PhotoCamera, "Прикрепить фото")
            }
            IconButton(onClick = { showStyleDialog = true }) {
                Icon(Icons.Default.Mood, "Стиль")
            }
            IconButton(onClick = { nav.navigate("linkpicker/$row/$col/$id") }) {
                Icon(
                    Icons.Default.Link,
                    "Ссылка",
                    modifier = Modifier.size(28.dp),
                    tint = if (pin.link != null) Color(0xFF00C853) else Color.Gray
                )
            }
            IconButton(onClick = { moving = true }) {
                Icon(Icons.Default.GpsFixed, "Переместить")
            }
            IconButton(onClick = { save() }) {
                Icon(Icons.Default.Save, "Сохранить")
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = title, onValueChange = { title = it },
            label = { Text("Короткое название") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = text, onValueChange = { text = it },
            label = { Text("Текст комментария") },
            modifier = Modifier.fillMaxWidth().height(140.dp)
        )

        // === Фотографии ===
        if (photos.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Фотографии:", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (photo in photos) {
                    AsyncImage(
                        model = vm.repo.photoFile(photo.file),
                        contentDescription = photo.title.ifBlank { "Фото" },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFECEFF1))
                            .pointerInput(photo.id) {
                                detectTapGestures(
                                    onDoubleTap = { viewPhoto = photo },
                                    onLongPress = { editPhoto = photo }
                                )
                            }
                    )
                }
            }
        }
    }

    // === Диалог стиля ===
    if (showStyleDialog) {
        AlertDialog(
            onDismissRequest = { showStyleDialog = false },
            title = { Text("Стиль комментария") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Цвет:", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (c in PresetColors) {
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(c.toInt()))
                                    .then(if (c == color) Modifier.border(3.dp, Color.Black, CircleShape) else Modifier)
                                    .clickable { color = c }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Иконка:", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (t in listOf(
                            CommentIconType.CIRCLE, CommentIconType.SQUARE,
                            CommentIconType.TRIANGLE, CommentIconType.DIAMOND
                        )) {
                            IconTypeCell(t, iconType == t, Color(color.toInt())) { iconType = t }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (t in listOf(
                            CommentIconType.ROSETTE, CommentIconType.PHONE, CommentIconType.COMPUTER
                        )) {
                            IconTypeCell(t, iconType == t, Color(color.toInt())) { iconType = t }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStyleDialog = false }) { Text("OK") }
            }
        )
    }

    // === Просмотр фото ===
    viewPhoto?.let { photo ->
        Dialog(onDismissRequest = { viewPhoto = null }) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f))
                    .pointerInput(Unit) { detectTapGestures(onTap = { viewPhoto = null }) },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = vm.repo.photoFile(photo.file),
                    contentDescription = photo.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                )
            }
        }
    }

    // === Редактирование фото ===
    editPhoto?.let { photo ->
        var t by remember(photo.id) { mutableStateOf(photo.title) }
        var c by remember(photo.id) { mutableStateOf(photo.comment) }
        AlertDialog(
            onDismissRequest = { editPhoto = null },
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
                TextButton(onClick = {
                    photos = photos.map {
                        if (it.id == photo.id) it.copy(title = t, comment = c) else it
                    }
                    editPhoto = null
                }) { Text("Сохранить") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        photos = photos.filterNot { it.id == photo.id }
                        editPhoto = null
                    }) { Text("Удалить", color = Color.Red) }
                    TextButton(onClick = { editPhoto = null }) { Text("Отмена") }
                }
            }
        )
    }
}

@Composable
private fun IconTypeCell(
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
private fun MoveCommentOverlay(
    cell: CellData, vm: AppViewModel,
    initialX: Float, initialY: Float,
    onCancel: () -> Unit,
    onConfirm: (nx: Float, ny: Float) -> Unit
) {
    var mScale by remember { mutableStateOf(1f) }
    var mOffset by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val containerW = constraints.maxWidth.toFloat()
        val containerH = constraints.maxHeight.toFloat()
        val density = LocalDensity.current

        val imageW = cell.imageWidth
        val imageH = cell.imageHeight
        val fitScale = if (imageW > 0 && imageH > 0) min(containerW / imageW, containerH / imageH) else 1f
        val fitW = if (imageW > 0) imageW * fitScale else containerW
        val fitH = if (imageH > 0) imageH * fitScale else containerH

        LaunchedEffect(fitW, fitH) {
            mScale = 1f
            mOffset = Offset(containerW / 2f - initialX * fitW, containerH / 2f - initialY * fitH)
        }

        Box(
            Modifier.fillMaxSize().clipToBounds()
                .pointerInput(fitW, fitH) {
                    detectViewerGestures(
                        getViewport = { Viewport(mScale, mOffset) },
                        fitW = fitW, fitH = fitH,
                        containerW = containerW, containerH = containerH,
                        onTransform = { vp -> mScale = vp.scale; mOffset = vp.offset },
                        onSwipe = { _, _ -> }
                    )
                }
        ) {
            val f = cell.imageFile
            if (f != null) {
                AsyncImage(
                    model = vm.repo.imageFile(f),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .size(with(density) { fitW.toDp() }, with(density) { fitH.toDp() })
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0f, 0f)
                            translationX = mOffset.x; translationY = mOffset.y
                            scaleX = mScale; scaleY = mScale
                        }
                )
            }
        }

        Box(
            Modifier.align(Alignment.Center).size(24.dp)
                .background(Color.Red, shape = CircleShape)
        )

        IconButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
        ) { Icon(Icons.Default.ArrowBack, "Отмена", tint = Color.White) }

        IconButton(
            onClick = {
                val cx = containerW / 2f
                val cy = containerH / 2f
                val imgX = (cx - mOffset.x) / mScale
                val imgY = (cy - mOffset.y) / mScale
                val nx = (imgX / fitW).coerceIn(0f, 1f)
                val ny = (imgY / fitH).coerceIn(0f, 1f)
                onConfirm(nx, ny)
            },
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
        ) { Icon(Icons.Default.Check, "OK", tint = Color.White) }
    }
}