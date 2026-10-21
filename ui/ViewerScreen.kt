package com.example.kotlovan.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.kotlovan.data.CellData
import com.example.kotlovan.data.CommentPin
import com.example.kotlovan.util.Flashlight
import com.example.kotlovan.util.applyZoom
import com.example.kotlovan.util.detectViewerGestures
import com.example.kotlovan.vm.AppViewModel
import kotlinx.coroutines.delay
import java.util.UUID
import kotlin.math.abs

private enum class EyeMode { STANDARD, BLINKING, UNIFORM }
private enum class ViewerSubMode { VIEW, SET_COMMON_POINT, PLACE_COMMENT }

@Composable
fun ViewerScreen(nav: NavHostController, vm: AppViewModel) {
    val ctx = LocalContext.current
    val cfg = vm.config
    var row by remember { mutableStateOf(cfg.lastRow) }
    var col by remember { mutableStateOf(cfg.lastCol) }
    var scale by remember { mutableStateOf(cfg.globalScale) }
    var offset by remember { mutableStateOf(Offset(cfg.globalOffsetX, cfg.globalOffsetY)) }
    var menuOpen by remember { mutableStateOf(false) }
    var eyeMode by remember { mutableStateOf(EyeMode.STANDARD) }
    var subMode by remember { mutableStateOf(ViewerSubMode.VIEW) }
    var pendingCommentPos by remember { mutableStateOf<Offset?>(null) }
    val flashlight = remember { Flashlight(ctx) }

    val currentCell = vm.cell(row, col)

    // Свайп/зум
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(row, col, subMode) {
                detectViewerGestures(
                    onTransform = { c, p, z ->
                        val (s, ox, oy) = applyZoom(scale, offset.x, offset.y, c, p, z)
                        scale = s; offset = Offset(ox, oy)
                    },
                    onSwipe = { dx, dy ->
                        if (subMode == ViewerSubMode.VIEW) {
                            val nextPos = when {
                                abs(dx) > abs(dy) -> row to (col + if (dx < 0) 1 else -1)
                                else -> (row + if (dy < 0) 1 else -1) to col
                            }
                            if (vm.cell(nextPos.first, nextPos.second) != null) {
                                row = nextPos.first; col = nextPos.second
                                vm.setGlobalViewport(scale, offset.x, offset.y, row, col)
                            }
                        }
                    }
                )
            }
    ) {
        val file = currentCell?.imageFile
        if (file != null) {
            Crossfade(targetState = "$row,$col", label = "cell") { _ ->
                AsyncImage(
                    model = vm.repo.imageFile(file),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = offset.x
                            translationY = offset.y
                            scaleX = scale; scaleY = scale
                        }
                )
            }
        }

        // Кружки комментариев
        if (currentCell != null) {
            val screenW = LocalConfiguration.current.screenWidthDp.dp
            val density = LocalDensity.current
            // Отрисовываем комментарии поверх изображения с учётом текущей трансформации
            currentCell.comments.forEach { pin ->
                CommentPinOverlay(
                    pin = pin,
                    scale = scale, offset = offset,
                    eyeMode = eyeMode,
                    cell = currentCell, vm = vm,
                    onOpen = { nav.navigate("comment/$row/$col/${pin.id}") }
                )
            }
        }

        // Верх: назад, фонарик, меню
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                vm.setGlobalViewport(scale, offset.x, offset.y, row, col)
                vm.autosave()
                nav.popBackStack()
            }) { Icon(Icons.Default.ArrowBack, "Назад", tint = Color.White) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { flashlight.toggle() }) {
                Icon(Icons.Default.FlashOn, "Фонарик", tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            Box {
                IconButton(onClick = { menuOpen = !menuOpen }) {
                    Icon(Icons.Default.MoreVert, "Меню", tint = Color.White)
                }
                if (menuOpen) {
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Общая точка") }, onClick = {
                            menuOpen = false; subMode = ViewerSubMode.SET_COMMON_POINT
                        })
                        DropdownMenuItem(
                            text = { Text("Свое положение: ${currentCell?.ownPosition ?: false}") },
                            onClick = {
                                menuOpen = false
                                currentCell?.let { c ->
                                    vm.updateCell(row, col) { it.copy(ownPosition = !c.ownPosition) }
                                }
                            })
                        DropdownMenuItem(text = { Text("Установить комментарий") }, onClick = {
                            menuOpen = false; subMode = ViewerSubMode.PLACE_COMMENT
                        })
                        DropdownMenuItem(text = { Text("Настройки") }, onClick = {
                            menuOpen = false /* TODO: открыть SettingsSheet */
                        })
                    }
                }
            }
        }

        // Глаз (снизу слева)
        if (subMode == ViewerSubMode.VIEW) {
            IconButton(
                onClick = { /* одиночный клик — ничего */ },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            eyeMode = when (eyeMode) {
                                EyeMode.STANDARD -> EyeMode.BLINKING
                                EyeMode.BLINKING -> EyeMode.UNIFORM
                                EyeMode.UNIFORM -> EyeMode.STANDARD
                            }
                        })
                    }
            ) {
                val icon = when (eyeMode) {
                    EyeMode.STANDARD -> Icons.Default.VisibilityOff
                    EyeMode.BLINKING -> Icons.Default.Visibility
                    EyeMode.UNIFORM -> Icons.Default.RemoveRedEye
                }
                Icon(icon, "Режим комментариев", tint = Color.White)
            }
        }

        // Режим установки общей точки
        if (subMode == ViewerSubMode.SET_COMMON_POINT) {
            BlinkingDot(modifier = Modifier.align(Alignment.Center))
            IconButton(
                onClick = {
                    // центр viewport в координатах изображения
                    val vw = LocalDensity.current
                    // считаем, что центр экрана соответствует точке
                    val cxNorm = (-offset.x / scale).coerceIn(0f, 1f)
                    val cyNorm = (-offset.y / scale).coerceIn(0f, 1f)
                    vm.updateCell(row, col) { it.copy(commonX = 0.5f, commonY = 0.5f) }
                    subMode = ViewerSubMode.VIEW
                },
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
            ) { Icon(Icons.Default.Check, "Подтвердить", tint = Color.White) }
        }

        // Режим установки комментария
        if (subMode == ViewerSubMode.PLACE_COMMENT) {
            BlinkingDot(modifier = Modifier.align(Alignment.Center))
            IconButton(
                onClick = {
                    val id = UUID.randomUUID().toString()
                    val newPin = CommentPin(
                        id = id,
                        x = 0.5f, y = 0.5f,  // центр изображения; уточнять через CommentEditor "Переместить"
                        radius = 0.04f
                    )
                    val cell = currentCell ?: return@IconButton
                    vm.setComments(row, col, cell.comments + newPin)
                    nav.navigate("comment/$row/$col/$id")
                    subMode = ViewerSubMode.VIEW
                },
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
            ) { Icon(Icons.Default.Check, "Подтвердить", tint = Color.White) }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            vm.setGlobalViewport(scale, offset.x, offset.y, row, col)
            vm.autosave()
            flashlight.off()
        }
    }
}

@Composable
private fun BlinkingDot(modifier: Modifier = Modifier) {
    var a by remember { mutableStateOf(1f) }
    LaunchedEffect(Unit) {
        while (true) {
            a = 0.3f; delay(400); a = 1f; delay(400)
        }
    }
    Box(modifier.size(24.dp).alpha(a).clip(CircleShape).background(Color.Red))
}

@Composable
private fun CommentPinOverlay(
    pin: CommentPin, scale: Float, offset: Offset,
    eyeMode: EyeMode, cell: CellData, vm: AppViewModel,
    onOpen: () -> Unit
) {
    var blink by remember { mutableStateOf(1f) }
    LaunchedEffect(eyeMode) {
        while (eyeMode == EyeMode.BLINKING) {
            blink = 0.3f; delay(500); blink = 1f; delay(500)
        }
        blink = 1f
    }
    val density = LocalDensity.current
    val rBase = with(density) { 40.dp.toPx() }
    val r = when (eyeMode) {
        EyeMode.STANDARD -> rBase * (pin.radius * 20f).coerceIn(0.5f, 3f)
        EyeMode.BLINKING -> rBase * (pin.radius * 20f).coerceIn(0.5f, 3f)
        EyeMode.UNIFORM -> rBase
    }
    // Перевод нормализованных координат (0..1) в пиксели экрана
    // (упрощение: считаем область изображения = экран)
    Box(
        Modifier
            .offset {
                androidx.compose.ui.unit.IntOffset(
                    (pin.x * 1000f * scale + offset.x).toInt(),
                    (pin.y * 1000f * scale + offset.y).toInt()
                )
            }
            .size(with(density) { r.toDp() * 2 })
            .alpha(if (eyeMode == EyeMode.BLINKING) blink else 0.85f)
            .clip(CircleShape)
            .background(Color(0xCCFF5722))
            .pointerInput(pin.id) {
                detectTapGestures(
                    onDoubleTap = { /* показать текст — TODO: Toast */ },
                    onLongPress = { onOpen() }
                )
            }
    )
}