package com.example.kotlovan.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
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

private val Yellow = Color(0xFFFFEB3B)
private val Blue = Color(0xFF1565C0)
private val BlackBorder = BorderStroke(7.dp, Color.Black)
private val BlueBorder = BorderStroke(7.dp, Blue)
private val PresetColors = listOf(
    0xFFFF5722L, 0xFFE91E63L, 0xFF9C27B0L, 0xFF3F51B5L, 0xFF2196F3L,
    0xFF00BCD4L, 0xFF009688L, 0xFF4CAF50L, 0xFFFFC107L, 0xFFFFEB3B
)

@Composable
fun CommentEditorScreen(nav: NavHostController, vm: AppViewModel, row: Int, col: Int, id: String) {
    val cell = vm.cell(row, col) ?: return
    val pin = cell.comments.firstOrNull { it.id == id } ?: return
    val ctx = LocalContext.current

    var title by remember { mutableStateOf(pin.title) }
    var text by remember { mutableStateOf(pin.text) }
    var shortLabel by remember { mutableStateOf(pin.shortLabel) }
    var color by remember { mutableLongStateOf(pin.color) }
    var iconType by remember { mutableStateOf(pin.iconType) }
    var photos by remember { mutableStateOf(pin.photos) }
    var rating by remember { mutableIntStateOf(pin.rating) }
    var moving by remember { mutableStateOf(false) }
    var showStyleDialog by remember { mutableStateOf(false) }
    var viewPhoto by remember { mutableStateOf<CommentPhoto?>(null) }
    var editPhoto by remember { mutableStateOf<CommentPhoto?>(null) }
    var pendingPhotoName by remember { mutableStateOf<String?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val name = pendingPhotoName
        if (success && name != null) photos = photos + CommentPhoto(id = UUID.randomUUID().toString(), file = name)
        else if (!success && name != null) Toast.makeText(ctx, "Фото не сохранено", Toast.LENGTH_SHORT).show()
        pendingPhotoName = null
    }

    val pickPhotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val name = vm.repo.newPhotoName()
                ctx.contentResolver.openInputStream(uri)?.use { input ->
                    vm.repo.photoFile(name).outputStream().use { out -> input.copyTo(out) }
                }
                photos = photos + CommentPhoto(id = UUID.randomUUID().toString(), file = name)
            } catch (_: Exception) {
                Toast.makeText(ctx, "Не удалось скопировать фото", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun launchCameraActual() {
        val name = vm.repo.newPhotoName()
        pendingPhotoName = name
        try {
            takePictureLauncher.launch(vm.repo.photoUriForNew(name))
        } catch (e: Exception) {
            pendingPhotoName = null
            Toast.makeText(ctx, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCameraActual()
        else Toast.makeText(ctx, "Нужно разрешение", Toast.LENGTH_SHORT).show()
    }

    fun launchCamera() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED) launchCameraActual()
        else cameraPermLauncher.launch(Manifest.permission.CAMERA)
    }

    fun save() {
        vm.setComments(row, col, cell.comments.map {
            if (it.id == id) it.copy(title = title, text = text, shortLabel = shortLabel,
                color = color, iconType = iconType, photos = photos, rating = rating) else it
        })
        vm.autosave()
        nav.popBackStack()
    }



    if (moving) {
        MoveCommentOverlay(cell, vm, pin.x, pin.y,
            onCancel = { moving = false },
            onConfirm = { nx, ny ->
                vm.setComments(row, col, cell.comments.map {
                    if (it.id == id) it.copy(x = nx, y = ny) else it
                })
                moving = false
            })
        return
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(8.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Назад") }
                Text("Комментарий", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showStyleDialog = true }) { Icon(Icons.Default.Mood, "Стиль") }
                IconButton(onClick = { nav.navigate("linkpicker/$row/$col/$id") }) {
                    Icon(Icons.Default.Link, "Ссылка",
                        modifier = Modifier.size(28.dp),
                        tint = if (pin.link != null) Color(0xFF00C853) else Color.Gray)
                }
                IconButton(onClick = { moving = true }) { Icon(Icons.Default.GpsFixed, "Переместить") }
                IconButton(onClick = { save() }) { Icon(Icons.Default.Save, "Сохранить") }
            }
            Spacer(Modifier.height(8.dp))

            // Поля со стилем «жёлтый + синяя кайма»
            YellowField(
                value = title, onValueChange = { title = it }, label = "Название",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                YellowField(
                    value = shortLabel,
                    onValueChange = { if (it.length <= 3) shortLabel = it },
                    label = "Метка", modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            YellowField(
                value = text, onValueChange = { text = it }, label = "Текст",
                modifier = Modifier.fillMaxWidth().height(140.dp)
            )

            // Кнопки камера / галерея
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                YellowBigButton(
                    onClick = { launchCamera() },
                    icon = Icons.Default.PhotoCamera,
                    modifier = Modifier.weight(1f)
                )
                YellowBigButton(
                    onClick = { pickPhotoLauncher.launch(arrayOf("image/*")) },
                    icon = Icons.Default.Image,
                    modifier = Modifier.weight(1f)
                )
            }

            if (photos.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Фотографии:", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (photo in photos) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AsyncImage(
                                model = vm.repo.photoFile(photo.file),
                                contentDescription = photo.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(80.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFECEFF1))
                                    .pointerInput(photo.id) {
										detectTapGestures(
											onTap = { /* однократный тап — открыть просмотр */ viewPhoto = photo },
											onDoubleTap = { /* двойной — редактирование */ editPhoto = photo },
											onLongPress = { editPhoto = photo }
										)
									})
                            if (photo.title.isNotBlank()) {
                                Text(photo.title, style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1, modifier = Modifier.width(80.dp).padding(top = 2.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(80.dp))
        }

        IconButton(
            onClick = {
                vm.setComments(row, col, cell.comments.filterNot { it.id == id })
                vm.autosave(); nav.popBackStack()
            },
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
        ) { Icon(Icons.Default.Delete, "Удалить", tint = Color(0xFFD32F2F)) }

        Row(Modifier.align(Alignment.BottomEnd).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { rating++ }) { Icon(Icons.Default.ArrowUpward, "↑", tint = Blue) }
            Text("$rating", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { if (rating > -999) rating-- }) {
                Icon(Icons.Default.ArrowDownward, "↓", tint = Color(0xFFD32F2F))
            }
        }
    }

    if (showStyleDialog) {
        AlertDialog(
            onDismissRequest = { showStyleDialog = false },
            title = { Text("Стиль") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Цвет:", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (c in PresetColors) {
                            Box(Modifier.size(28.dp).clip(CircleShape).background(Color(c.toInt()))
                                .then(if (c == color) Modifier.border(3.dp, Color.Black, CircleShape) else Modifier)
                                .clickable { color = c })
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Иконка:", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (t in listOf(CommentIconType.CIRCLE, CommentIconType.SQUARE,
                            CommentIconType.TRIANGLE, CommentIconType.DIAMOND)) {
                            IconTypeCell(t, iconType == t, Color(color.toInt())) { iconType = t }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (t in listOf(CommentIconType.ROSETTE, CommentIconType.PHONE,
                            CommentIconType.COMPUTER)) {
                            IconTypeCell(t, iconType == t, Color(color.toInt())) { iconType = t }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showStyleDialog = false }) { Text("OK") } }
        )
    }

    viewPhoto?.let { photo ->
        var pScale by remember(photo.id) { mutableFloatStateOf(1f) }
        var pOffset by remember(photo.id) { mutableStateOf(Offset.Zero) }
        // Реальные размеры фото
        var photoW by remember(photo.id) { mutableIntStateOf(0) }
        var photoH by remember(photo.id) { mutableIntStateOf(0) }
        LaunchedEffect(photo.id) {
            val f = vm.repo.photoFile(photo.file)
            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(f.absolutePath, opts)
            photoW = opts.outWidth
            photoH = opts.outHeight
        }
        Dialog(onDismissRequest = { viewPhoto = null }) {
            BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.95f))) {
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
                            detectViewerGestures(
                                getViewport = { Viewport(pScale, pOffset) },
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
    
                Box(Modifier.align(Alignment.TopEnd).padding(16.dp).size(44.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .clickable { viewPhoto = null },
                    contentAlignment = Alignment.Center) {
                    Text("×", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    editPhoto?.let { photo ->
        var t by remember(photo.id, photo.title) { mutableStateOf(photo.title) }
        var c by remember(photo.id, photo.comment) { mutableStateOf(photo.comment) }
        AlertDialog(
            onDismissRequest = { editPhoto = null },
            title = { Text("Фотография") },
            text = {
                Column {
                    OutlinedTextField(value = t, onValueChange = { t = it },
                        label = { Text("Название") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = c, onValueChange = { c = it },
                        label = { Text("Комментарий") },
                        modifier = Modifier.fillMaxWidth().height(100.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    photos = photos.map { if (it.id == photo.id) it.copy(title = t, comment = c) else it }
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
private fun YellowField(
    value: String, onValueChange: (String) -> Unit,
    label: String, modifier: Modifier = Modifier
) {
    Box(
        modifier
            .background(Yellow, RoundedCornerShape(8.dp))
            .border(BlueBorder, RoundedCornerShape(8.dp))
            .padding(4.dp)
    ) {
        OutlinedTextField(
            value = value, onValueChange = onValueChange,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun YellowBigButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Blue),
        border = BorderStroke(7.dp, Blue),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp, pressedElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.height(64.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun IconTypeCell(type: CommentIconType, selected: Boolean, color: Color, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(6.dp))
        .background(if (selected) Color(0xFFE0E0E0) else Color.White)
        .border(if (selected) 2.dp else 1.dp,
            if (selected) Color.Black else Color.LightGray, RoundedCornerShape(6.dp))
        .clickable(onClick = onClick).padding(8.dp),
        contentAlignment = Alignment.Center) {
        CommentIconShape(type = type, color = color, sizeDp = 24.dp)
    }
}

@Composable
private fun MoveCommentOverlay(
    cell: CellData, vm: AppViewModel, initialX: Float, initialY: Float,
    onCancel: () -> Unit, onConfirm: (nx: Float, ny: Float) -> Unit
) {
    var mScale by remember { mutableFloatStateOf(1f) }
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
        Box(Modifier.fillMaxSize().clipToBounds()
            .pointerInput(fitW, fitH) {
                detectViewerGestures(
                    getViewport = { Viewport(mScale, mOffset) },
                    fitW = fitW, fitH = fitH,
                    containerW = containerW, containerH = containerH,
                    onTransform = { vp -> mScale = vp.scale; mOffset = vp.offset },
                    onSwipe = { _, _ -> })
            }) {
            val f = cell.imageFile
            if (f != null) {
                AsyncImage(model = vm.repo.imageFile(f), contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.size(with(density) { fitW.toDp() }, with(density) { fitH.toDp() })
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0f, 0f)
                            translationX = mOffset.x; translationY = mOffset.y
                            scaleX = mScale; scaleY = mScale
                        })
            }
        }
        Canvas(Modifier.align(Alignment.Center).size(48.dp)) {
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
        IconButton(onClick = onCancel, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Icon(Icons.Default.ArrowBack, "Отмена", tint = Color.White)
        }
        IconButton(onClick = {
            val imgX = (containerW / 2f - mOffset.x) / mScale.coerceAtLeast(0.01f)
            val imgY = (containerH / 2f - mOffset.y) / mScale.coerceAtLeast(0.01f)
            onConfirm((imgX / fitW).coerceIn(0f, 1f), (imgY / fitH).coerceIn(0f, 1f))
        }, modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Icon(Icons.Default.Check, "OK", tint = Color.White)
        }
    }
}
