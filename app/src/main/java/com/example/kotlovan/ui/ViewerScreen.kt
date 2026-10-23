package com.example.kotlovan.ui

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.kotlovan.data.CellData
import com.example.kotlovan.data.CommentPin
import com.example.kotlovan.util.Flashlight
import com.example.kotlovan.util.Viewport
import com.example.kotlovan.util.clampOffset
import com.example.kotlovan.util.detectViewerGestures
import com.example.kotlovan.vm.AppViewModel
import kotlinx.coroutines.delay
import java.util.UUID
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

private enum class ViewerSubMode { VIEW, SET_COMMON_POINT, PLACE_COMMENT }
private enum class SwipeDir { NONE, LEFT, RIGHT, UP, DOWN }

private val BlinkColors = listOf(
    Color(0xFFFF5722), Color(0xFF4CAF50), Color(0xFF2196F3),
    Color(0xFF9C27B0), Color(0xFFFFEB3B)
)

@Composable
fun ViewerScreen(nav: NavHostController, vm: AppViewModel) {
    val ctx = LocalContext.current
    val cfg = vm.config
    var row by remember { mutableStateOf(cfg.lastRow) }
    var col by remember { mutableStateOf(cfg.lastCol) }
    var scale by remember { mutableStateOf(cfg.globalScale) }
    var offset by remember { mutableStateOf(Offset(cfg.globalOffsetX, cfg.globalOffsetY)) }
    var menuOpen by remember { mutableStateOf(false) }
    var eyeMode by remember { mutableStateOf(EyeMode.HIDDEN) }
    var subMode by remember { mutableStateOf(ViewerSubMode.VIEW) }
    var swipeDir by remember { mutableStateOf(SwipeDir.NONE) }
    var showSettings by remember { mutableStateOf(false) }
    val flashlight = remember { Flashlight(ctx) }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val containerW = constraints.maxWidth.toFloat()
        val containerH = constraints.maxHeight.toFloat()
        val density = LocalDensity.current

        val currentCell = vm.cell(row, col)
        val imageW = currentCell?.imageWidth ?: 0
        val imageH = currentCell?.imageHeight ?: 0
        val fitScale = if (imageW > 0 && imageH > 0)
            min(containerW / imageW, containerH / imageH) else 1f
        val fitW = if (imageW > 0) imageW * fitScale else containerW
        val fitH = if (imageH > 0) imageH * fitScale else containerH

        // Иконки с настройкой размера/прозрачности
        val baseIconDp = 28.dp * cfg.iconSize
        val iconMod = Modifier.size(baseIconDp).alpha(cfg.iconOpacity)

        // Подстановка viewport
        LaunchedEffect(row, col, fitW, fitH) {
            val c = vm.cell(row, col)
            if (c != null) {
                if (c.ownPosition) {
                    scale = c.ownScale
                    offset = Offset(c.ownOffsetX, c.ownOffsetY)
                } else {
                    scale = vm.config.globalScale
                    val gOx = vm.config.globalOffsetX
                    val gOy = vm.config.globalOffsetY
                    offset = if (gOx == 0f && gOy == 0f && vm.config.globalScale == 1f)
                        Offset((containerW - fitW) / 2f, (containerH - fitH) / 2f)
                    else Offset(gOx, gOy)
                }
                offset = clampOffset(offset, scale, fitW, fitH, containerW, containerH)
            }
            swipeDir = SwipeDir.NONE
        }

        Box(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(row, col, subMode, fitW, fitH) {
                    detectViewerGestures(
                        getViewport = { Viewport(scale, offset) },
                        fitW = fitW, fitH = fitH,
                        containerW = containerW, containerH = containerH,
                        onTransform = { vp -> scale = vp.scale; offset = vp.offset },
                        onSwipe = { dx, dy ->
                            if (subMode == ViewerSubMode.VIEW) {
                                val nr: Int; val nc: Int; val dir: SwipeDir
                                if (abs(dx) > abs(dy)) {
                                    if (dx < 0) { nr = row; nc = col + 1; dir = SwipeDir.LEFT }
                                    else         { nr = row; nc = col - 1; dir = SwipeDir.RIGHT }
                                } else {
                                    if (dy < 0) { nr = row + 1; nc = col; dir = SwipeDir.UP }
                                    else         { nr = row - 1; nc = col; dir = SwipeDir.DOWN }
                                }
                                if (vm.cell(nr, nc) != null) {
                                    val c = vm.cell(row, col)
                                    if (c != null) {
                                        if (c.ownPosition) vm.updateCell(row, col) {
                                            it.copy(ownScale = scale, ownOffsetX = offset.x, ownOffsetY = offset.y)
                                        }
                                        else vm.setGlobalViewport(scale, offset.x, offset.y, row, col)
                                    }
                                    swipeDir = dir; row = nr; col = nc
                                }
                            }
                        }
                    )
                }
        ) {
            // Изображение
            AnimatedContent(
                targetState = "$row,$col",
                transitionSpec = {
                    val inSpec: androidx.compose.animation.EnterTransition
                    val outSpec: androidx.compose.animation.ExitTransition
                    when (swipeDir) {
                        SwipeDir.LEFT  -> { inSpec = slideInHorizontally { it } + fadeIn(); outSpec = slideOutHorizontally { -it } + fadeOut() }
                        SwipeDir.RIGHT -> { inSpec = slideInHorizontally { -it } + fadeIn(); outSpec = slideOutHorizontally { it } + fadeOut() }
                        SwipeDir.UP    -> { inSpec = slideInVertically { it } + fadeIn(); outSpec = slideOutVertically { -it } + fadeOut() }
                        SwipeDir.DOWN  -> { inSpec = slideInVertically { -it } + fadeIn(); outSpec = slideOutVertically { it } + fadeOut() }
                        SwipeDir.NONE  -> { inSpec = fadeIn(); outSpec = fadeOut() }
                    }
                    (inSpec togetherWith outSpec).using(SizeTransform(clip = false))
                },
                label = "cellSwipe"
            ) { key ->
                val parts = key.split(",")
                val r = parts.getOrNull(0)?.toIntOrNull() ?: 0
                val c = parts.getOrNull(1)?.toIntOrNull() ?: 0
                val cell = vm.cell(r, c)
                val uriStr = cell?.imageUri
                val w = cell?.imageWidth ?: 0
                val h = cell?.imageHeight ?: 0
                val fs = if (w > 0 && h > 0) min(containerW / w, containerH / h) else 1f
                val cFitW = if (w > 0) w * fs else containerW
                val cFitH = if (h > 0) h * fs else containerH

                Box(Modifier.fillMaxSize()) {
                    if (uriStr != null) {
                        AsyncImage(
                            model = Uri.parse(uriStr),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .size(with(density) { cFitW.toDp() }, with(density) { cFitH.toDp() })
                                .graphicsLayer {
                                    transformOrigin = TransformOrigin(0f, 0f)
                                    translationX = offset.x; translationY = offset.y
                                    scaleX = scale; scaleY = scale
                                }
                        )
                    }
                }
            }

            // Кружки комментариев
            if (currentCell != null) {
                for (pin in currentCell.comments) {
                    CommentPinOverlay(
                        pin = pin,
                        scale = scale,
                        offset = offset,
                        eyeMode = eyeMode,
                        cell = currentCell,
                        fitW = fitW,
                        fitH = fitH,
                        onOpen = { nav.navigate("comment/$row/$col/${pin.id}") }
                    )
                }
            }

            // Верхняя панель (контрастные иконки)
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedIconButton(onClick = {
                    val c = vm.cell(row, col)
                    if (c != null) {
                        if (c.ownPosition) vm.updateCell(row, col) {
                            it.copy(ownScale = scale, ownOffsetX = offset.x, ownOffsetY = offset.y)
                        }
                        else vm.setGlobalViewport(scale, offset.x, offset.y, row, col)
                    }
                    vm.autosave()
                    nav.popBackStack()
                }, icon = Icons.Default.ArrowBack, description = "Назад", iconModifier = iconMod)

                Spacer(Modifier.weight(1f))

                OutlinedIconButton(
                    onClick = { flashlight.toggle() },
                    icon = Icons.Default.FlashOn,
                    description = "Фонарик",
                    iconModifier = iconMod
                )

                Spacer(Modifier.weight(1f))

                Box {
                    OutlinedIconButton(
                        onClick = { menuOpen = !menuOpen },
                        icon = Icons.Default.MoreVert,
                        description = "Меню",
                        iconModifier = iconMod
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Общая точка") }, onClick = {
                            menuOpen = false; subMode = ViewerSubMode.SET_COMMON_POINT
                        })
                        DropdownMenuItem(
                            text = { Text("Свое положение: ${currentCell?.ownPosition ?: false}") },
                            onClick = {
                                menuOpen = false
                                val c = currentCell ?: return@DropdownMenuItem
                                val newOwn = !c.ownPosition
                                vm.updateCell(row, col) {
                                    it.copy(
                                        ownPosition = newOwn,
                                        ownScale = if (newOwn) scale else it.ownScale,
                                        ownOffsetX = if (newOwn) offset.x else it.ownOffsetX,
                                        ownOffsetY = if (newOwn) offset.y else it.ownOffsetY
                                    )
                                }
                            }
                        )
                        DropdownMenuItem(text = { Text("Установить комментарий") }, onClick = {
                            menuOpen = false; subMode = ViewerSubMode.PLACE_COMMENT
                        })
                        DropdownMenuItem(text = { Text("Настройки") }, onClick = {
                            menuOpen = false; showSettings = true
                        })
                    }
                }
            }

            // Иконка глаза (двойное касание — переключение режима)
            if (subMode == ViewerSubMode.VIEW) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = {
                                eyeMode = when (eyeMode) {
                                    EyeMode.HIDDEN -> EyeMode.BLINKING
                                    EyeMode.BLINKING -> EyeMode.SCALING_UP
                                    EyeMode.SCALING_UP -> EyeMode.SHOW_TITLES
                                    EyeMode.SHOW_TITLES -> EyeMode.HIDDEN
                                }
                            })
                        },
                    contentAlignment = Alignment.Center
                ) {
                    EyeModeIcon(
                        mode = eyeMode,
                        modifier = Modifier.size(baseIconDp).alpha(cfg.iconOpacity),
                        tint = Color.White,
                        iconSize = baseIconDp
                    )
                }
            }

            // Режим установки «Общей точки»
            if (subMode == ViewerSubMode.SET_COMMON_POINT) {
                BlinkingDot(modifier = Modifier.align(Alignment.Center))
                OutlinedIconButton(
                    onClick = {
                        vm.updateCell(row, col) { it.copy(commonX = 0.5f, commonY = 0.5f) }
                        subMode = ViewerSubMode.VIEW
                    },
                    icon = Icons.Default.Check,
                    description = "Подтвердить",
                    iconModifier = iconMod,
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
                )
            }

            // Создание комментария
            if (subMode == ViewerSubMode.PLACE_COMMENT) {
                BlinkingDot(modifier = Modifier.align(Alignment.Center))
                OutlinedIconButton(
                    onClick = {
                        val cell = vm.cell(row, col)
                        if (cell != null) {
                            // Точка картинки под центром экрана
                            val centerX = containerW / 2f
                            val centerY = containerH / 2f
                            val imgX = (centerX - offset.x) / scale
                            val imgY = (centerY - offset.y) / scale
                            val nx = (imgX / fitW).coerceIn(0f, 1f)
                            val ny = (imgY / fitH).coerceIn(0f, 1f)

                            // Радиус такой, чтобы на экране кружок был как прицел (24.dp диаметр)
                            val crosshairRadiusPx = with(density) { 12.dp.toPx() }
                            val r = crosshairRadiusPx / (fitW * scale)

                            val id = UUID.randomUUID().toString()
                            val newPin = CommentPin(
                                id = id, x = nx, y = ny,
                                radius = r.coerceIn(0.005f, 0.3f),
                                title = "", text = "",
                                color = 0xFFFF5722L,
                                iconType = com.example.kotlovan.data.CommentIconType.CIRCLE
                            )
                            vm.setComments(row, col, cell.comments + newPin)
                            subMode = ViewerSubMode.VIEW
                            nav.navigate("comment/$row/$col/$id")
                        }
                    },
                    icon = Icons.Default.Check,
                    description = "Подтвердить",
                    iconModifier = iconMod,
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
                )
            }
        }

        if (showSettings) {
            SettingsSheet(
                iconSize = cfg.iconSize,
                iconOpacity = cfg.iconOpacity,
                onSizeChange = { vm.setIconSettings(it, vm.config.iconOpacity) },
                onOpacityChange = { vm.setIconSettings(vm.config.iconSize, it) },
                onDismiss = { showSettings = false }
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose { vm.autosave(); flashlight.off() }
    }
}

// ─────────── Хелпер: иконка с контрастным фоном ───────────

@Composable
private fun OutlinedIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    description: String,
    iconModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = Color.White, modifier = iconModifier)
    }
}

// ─────────── Прицел ───────────

@Composable
private fun BlinkingDot(modifier: Modifier = Modifier) {
    var a by remember { mutableStateOf(1f) }
    LaunchedEffect(Unit) {
        while (true) { a = 0.3f; delay(400); a = 1f; delay(400) }
    }
    Box(modifier.size(24.dp).alpha(a).clip(CircleShape).background(Color.Red))
}

// ─────────── Кружок комментария ───────────

@Composable
private fun CommentPinOverlay(
    pin: CommentPin,
    scale: Float,
    offset: Offset,
    eyeMode: EyeMode,
    cell: CellData,
    fitW: Float,
    fitH: Float,
    onOpen: () -> Unit
) {
    if (cell.imageWidth <= 0 || cell.imageHeight <= 0) return
    val density = LocalDensity.current

    val pxImg = pin.x * fitW
    val pyImg = pin.y * fitH
    val screenX = pxImg * scale + offset.x
    val screenY = pyImg * scale + offset.y

    // Размер иконки в зависимости от режима глаза
    val diameterPx: Float = when (eyeMode) {
        EyeMode.SCALING_UP -> {
            val s = scale.coerceIn(0.2f, 5f)
            (pin.radius * fitW * 2f / sqrt(s.toDouble()).toFloat())
                .coerceAtLeast(with(density) { 24.dp.toPx() })
        }
        else -> pin.radius * fitW * scale * 2f
    }

    // Мерцание разными цветами
    var blinkPhase by remember { mutableStateOf(0) }
    var blinkIndex by remember { mutableStateOf((0..4).random()) }
    LaunchedEffect(eyeMode) {
        if (eyeMode == EyeMode.BLINKING) {
            while (true) {
                blinkPhase = (blinkPhase + 1) % 2
                if (blinkPhase == 0) blinkIndex = (blinkIndex + 1) % BlinkColors.size
                delay(500)
            }
        }
    }

    val displayColor: Color = when (eyeMode) {
        EyeMode.BLINKING -> BlinkColors[blinkIndex]
        else -> Color(pin.color.toInt())
    }
    val displayAlpha: Float = when (eyeMode) {
        EyeMode.BLINKING -> if (blinkPhase == 1) 0.3f else 1f
        else -> 1f
    }

    // Видимость названия: по тапу или в режиме SHOW_TITLES
    var titleVisible by remember { mutableStateOf(false) }
    var titleTimeout by remember { mutableStateOf(0L) }
    LaunchedEffect(titleTimeout) {
        if (titleTimeout > 0) {
            delay(5000)
            titleVisible = false
            titleTimeout = 0L
        }
    }
    val showTitle = titleVisible || eyeMode == EyeMode.SHOW_TITLES

    val diameterDp = with(density) { diameterPx.toDp() }

    // Иконка
    CommentIconShape(
        type = pin.iconType,
        color = displayColor,
        sizeDp = diameterDp,
        alpha = displayAlpha,
        modifier = Modifier
            .offset {
                IntOffset(
                    (screenX - diameterPx / 2f).toInt(),
                    (screenY - diameterPx / 2f).toInt()
                )
            }
            .pointerInput(pin.id) {
                detectTapGestures(
                    onTap = {
                        titleVisible = !titleVisible
                        titleTimeout = if (titleVisible) System.currentTimeMillis() else 0L
                    },
                    onDoubleTap = { onOpen() },
                    onLongPress = { onOpen() }
                )
            }
    )

    // Название над иконкой
    if (showTitle && pin.title.isNotBlank()) {
        val titleFontPx = (diameterPx * 0.35f).coerceAtLeast(with(density) { 10.dp.toPx() })
        Text(
            text = pin.title,
            color = Color.White,
            fontSize = with(density) { titleFontPx.toSp() },
            maxLines = 1,
            modifier = Modifier
                .offset {
                    IntOffset(
                        (screenX - diameterPx / 2f).toInt(),
                        (screenY - diameterPx / 2f - titleFontPx - 4f).toInt()
                    )
                }
                .background(Color(0xAA000000), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}