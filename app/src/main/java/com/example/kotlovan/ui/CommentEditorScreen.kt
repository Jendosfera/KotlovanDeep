package com.example.kotlovan.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.example.kotlovan.data.CommentIconType
import com.example.kotlovan.data.CommentPhoto
import com.example.kotlovan.vm.AppViewModel
import java.util.UUID

private val PresetColors = listOf(
    0xFFFF5722L, 0xFFE91E63L, 0xFF9C27B0L, 0xFF3F51B5L, 0xFF2196F3L,
    0xFF00BCD4L, 0xFF009688L, 0xFF4CAF50L, 0xFFFFC107L, 0xFFFFEB3B
)
private val BlueBorder = BorderStroke(7.dp, Blue)

@Composable
fun CommentEditorScreen(
    nav: NavHostController, vm: AppViewModel,
    row: Int, col: Int, id: String,
    depth: Int = 0, autoCamera: Boolean = false
) {
    val cell = vm.cell(row, col, depth) ?: return
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

    fun save() {
        vm.setComments(row, col, cell.comments.map {
            if (it.id == id) it.copy(
                title = title, text = text, shortLabel = shortLabel,
                color = color, iconType = iconType, photos = photos, rating = rating
            ) else it
        }, depth)
        vm.autosave()
        nav.popBackStack()
    }

    LaunchedEffect(Unit) {
        if (autoCamera) launchCamera()
    }

    if (moving) {
        MoveCommentOverlay(
            cell = cell, vm = vm,
            initialX = pin.x, initialY = pin.y,
            onCancel = { moving = false },
            onConfirm = { nx, ny ->
                vm.setComments(row, col, cell.comments.map {
                    if (it.id == id) it.copy(x = nx, y = ny) else it
                }, depth)
                moving = false
            }
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(8.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.Default.ArrowBack, "Назад")
                }
                Text("Комментарий", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showStyleDialog = true }) { Icon(Icons.Default.Mood, "Стиль") }
                IconButton(onClick = {
                    nav.navigate("linkpicker/$row/$col/$id?depth=$depth")
                }) {
                    Icon(Icons.Default.Link, "Ссылка",
                        modifier = Modifier.size(28.dp),
                        tint = if (pin.link != null) Color(0xFF00C853) else Color.Gray)
                }
                IconButton(onClick = { moving = true }) { Icon(Icons.Default.GpsFixed, "Переместить") }
                IconButton(onClick = { save() }) { Icon(Icons.Default.Save, "Сохранить") }
            }
            Spacer(Modifier.height(8.dp))

            YellowField(
                value = title, onValueChange = { title = it }, label = "Название",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            YellowField(
                value = shortLabel,
                onValueChange = { if (it.length <= 3) shortLabel = it },
                label = "Метка", modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            YellowField(
                value = text, onValueChange = { text = it }, label = "Текст",
                modifier = Modifier.fillMaxWidth().height(140.dp)
            )

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

            PhotosRow(
                photos = photos, vm = vm,
                onView = { viewPhoto = it },
                onEdit = { editPhoto = it }
            )

            Spacer(Modifier.height(80.dp))
        }

        IconButton(
            onClick = {
                vm.setComments(row, col, cell.comments.filterNot { it.id == id }, depth)
                vm.autosave(); nav.popBackStack()
            },
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
        ) { Icon(Icons.Default.Delete, "Удалить", tint = Color(0xFFD32F2F)) }

        Row(
            Modifier.align(Alignment.BottomEnd).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { rating++ }) {
                Icon(Icons.Default.ArrowUpward, "↑", tint = Blue)
            }
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
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(c.toInt()))
                                    .then(
                                        if (c == color) Modifier.border(3.dp, Color.Black, CircleShape)
                                        else Modifier
                                    )
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
                            CommentIconType.ROSETTE, CommentIconType.PHONE,
                            CommentIconType.COMPUTER
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

    viewPhoto?.let { photo ->
        ViewPhotoDialog(
            photo = photo, vm = vm,
            onDismiss = { viewPhoto = null }
        )
    }

    editPhoto?.let { photo ->
        EditPhotoDialog(
            photo = photo,
            onSave = { t, c ->
                photos = photos.map {
                    if (it.id == photo.id) it.copy(title = t, comment = c) else it
                }
                editPhoto = null
            },
            onDelete = {
                photos = photos.filterNot { it.id == photo.id }
                editPhoto = null
            },
            onDismiss = { editPhoto = null }
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