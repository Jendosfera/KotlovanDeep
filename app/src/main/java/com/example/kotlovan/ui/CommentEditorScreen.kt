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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SwapHoriz
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
    val key = vm.active.key(row, col, depth)

    val isPhotoComment = remember(vm.config.photoComments, id) {
        vm.config.photoComments[key]?.any { it.id == id } == true
    }

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

    fun saveCurrent() {
        val curPin = vm.cell(row, col, depth)?.comments?.firstOrNull { it.id == id } ?: return
        if (curPin.title == title && curPin.text == text &&
            curPin.shortLabel == shortLabel && curPin.color == color &&
            curPin.iconType == iconType && curPin.photos == photos &&
            curPin.rating == rating) return
        vm.updatePin(row, col, depth, curPin.copy(
            title = title, text = text, shortLabel = shortLabel,
            color = color, iconType = iconType, photos = photos, rating = rating
        ))
        // Автосохранение в файл (если boundSaveUri задан)
        vm.autosaveNow()
    }

    LaunchedEffect(title, text, shortLabel, color, iconType, photos, rating) {
        kotlinx.coroutines.delay(400)
        saveCurrent()
    }

    // Автосохранение при выходе с экрана
    DisposableEffect(Unit) {
        onDispose {
            // Финальное сохранение — но без дебаунса, сразу
            vm.autosaveNow()
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val name = pendingPhotoName
        if (success && name != null) {
            photos = photos + CommentPhoto(id = UUID.randomUUID().toString(), file = name)
            vm.autosaveNow()
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
                vm.autosaveNow()
            } catch (_: Exception) {
                Toast.makeText(ctx, "Не удалось скопировать фото", Toast.LENGTH_SHORT).show()
            }
       