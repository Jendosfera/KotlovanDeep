package com.example.kotlovan.ui

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.kotlovan.data.CellData
import com.example.kotlovan.data.CommentPin
import com.example.kotlovan.data.LinkReturnState
import com.example.kotlovan.data.PinLink
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

private data class PendingJump(val x: Float, val y: Float, val scale: Float)

private val BlinkColors = listOf(
    Color(0xFFFF5722), Color(0xFF4CAF50), Color(0xFF2196F3),
    Color(0xFF9C27B0), Color(0xFFFFEB3B)
)

private val LinkBorderColor = Color(0xFF00C853)

@Composable
fun ViewerScreen(nav: NavHostController, vm: AppViewModel) {
    val ctx = LocalContext.current
    val cfg = vm.config
    var row by rememberSaveable { mutableIntStateOf(vm.active.lastRow) }
    var col by rememberSaveable { mutableIntStateOf(vm.active.lastCol) }
    var savedRow by rememberSaveable { mutableIntStateOf(-1) }
    var savedCol by rememberSaveable { mutableIntStateOf(-1) }
    var scale by rememberSaveable { mutableFloatStateOf(1f) }
    var offsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var offsetY by rememberSaveable { mutableFloatStateOf(0f) }
    var menuOpen by remember { mutableStateOf(false) }
    var eyeMode by remember { mutableStateOf(EyeMode.HIDDEN) }
    var subMode by remember { mutableStateOf(ViewerSubMode.VIEW) }
    var swipeDir by remember { mutableStateOf(SwipeDir.NONE) }
    var showSettings by remember { mutableStateOf(false) }
    var iconsHidden by remember { mutableStateOf(false) }
    var pendingJump by remember { mutableStateOf<PendingJump?>(null) }
    var visibleTitles by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var labelDialog by remember { mutableStateOf<Pair<Boolean, Int>?>(null) }
    var labelText by remember { mutableStateOf("") }
    val flashlight = remember { Flashlight(ctx) }

    val inLinkMode = vm.linkReturn != null

    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            val now = System.currentTimeMillis()
            visibleTitles = visibleTitles.filterValues { it > now }
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(if (inLinkMode) Modifier.border(6.dp, LinkBorderColor) else Modifier)
    ) {
        val containerW = constraints.maxWidth.toFloat()
        val containerH = constraints.maxHeight.toFloat()
        val density = LocalDensity.current

        val currentCell = vm.cell(row, col)
        val imageW = currentCell?.imageWidth ?: 0
        val imageH = currentCell?.imageHeight ?: 0
        val fitScale = if (imageW > 0 && imageH > 0) min(containerW / imageW, containerH / imageH) else 1f
        val fitW = if (imageW > 0) imageW * fitScale else containerW
        val fitH = if (imageH > 0) imageH * fitScale else containerH

        val baseIconDp = 28.dp * cfg.iconSize
        val iconMod = Modifier.size(baseIconDp).alpha(cfg.iconOpacity)

        val allRows = vm.active.cells.keys.map { it.substringBefore(',').toInt() }
        val allCols = vm.active.cells.keys.map { it.substringAfter(',').toInt() }
        val rowRange = if (allRows.isEmpty()) 0..0 else allRows.min()..allRows.max()
        val colRange = if (allCols.isEmpty()) 0..0 else allCols.min()..allCols.max()

        fun persistViewport() {
            val c = vm.cell(row, col) ?: return
            val vOx = offsetX - containerW / 2f + c.commonX * fitW * scale
            val vOy = offsetY - containerH / 2f + c.commonY * fitH * scale
            if (c.ownPosition) {
                vm.updateCell(row, col) { it.copy(ownScale = scale, ownOffsetX = vOx, ownOffsetY = vOy) }
            } else {
                vm.setGlobalViewport(scale, vOx, vOy, row, col)
            }
        }

        fun returnFromLink() {
            val lr = vm.linkReturn ?: return
            vm.useAux = lr.isAux
            savedRow = lr.row; savedCol = lr.col
            row = lr.row; col = lr.col
            scale = lr.scale
            offsetX = lr.offX; offsetY = lr.offY
            vm.linkReturn = null
        }

        fun jumpToLink(
            link: PinLink,
            sourceRow: Int,
            sourceCol: Int,
            sourceId: String,
            keepReturn: Boolean = false
        ) {
            if (!keepReturn) {
                vm.linkReturn = LinkReturnState(
                    isAux = vm.useAux, row = row, col = col,
                    scale = scale, offX = offsetX, offY = offsetY,
                    commentRow = sourceRow, commentCol = sourceCol, commentId = sourceId
                )
            }
            vm.useAux = link.isAux
            savedRow = link.row; savedCol = link.col
            row = link.row; col = link.col
            subMode = ViewerSubMode.VIEW
            pendingJump = PendingJump(link.x, link.y, link.scale)
        }

        LaunchedEffect(vm.pendingRejump) {
            val pj = vm.pendingRejump ?: return@LaunchedEffect
            vm.pendingRejump = null
            val lr = vm.linkReturn ?: return@LaunchedEffect
            jumpToLink(pj, lr.commentRow, lr.commentCol, lr.commentId, keepReturn = true)
        }

        LaunchedEffect(row, col, fitW, fitH, pendingJump) {
            val pj = pendingJump
            if (pj != null) {
                scale = pj.scale
                offsetX = containerW / 2f - pj.x * fitW * pj.scale
                offsetY = containerH / 2f - pj.y * fitH * pj.scale
                pendingJump = null
                savedRow = row; savedCol = col
                swipeDir = SwipeDir.NONE
                return@LaunchedEffect
            }
            if (row == savedRow && col == savedCol) return@LaunchedEffect
            val c = vm.cell(row, col) ?: return@LaunchedEffect
            val (s, vOx, vOy) = if (c.ownPosition) {
                Triple(c.ownScale, c.ownOffsetX, c.ownOffsetY)
            } else {
                Triple(vm.active.globalScale, vm.active.globalOffsetX, vm.active.globalOffsetY)
            }
            scale = s
            offsetX = containerW / 2f + vOx - c.commonX * fitW * s
            offsetY = containerH / 2f + vOy - c.commonY * fitH * s
            val clamped = clampOffset(Offset(offsetX, offsetY), scale, fitW, fitH, containerW, containerH)
            offsetX = clamped.x; offsetY = clamped.y
            savedRow = row; savedCol = col
            swipeDir = SwipeDir.NONE
        }

        val cornerPx = with(density) { 80.dp.toPx() }

        // ==== Изображение ====
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
            val imgFile = cell?.imageFile
            val w = cell?.imageWidth ?: 0
            val h = cell?.imageHeight ?: 0
            val fs = if (w > 0 && h > 0) min(containerW / w, containerH / h) else 1f
            val cFitW = if (w > 0) w * fs else containerW
            val cFitH = if (h > 0) h * fs else containerH

            Box(Modifier.fillMaxSize().clipToBounds()) {
                if (cell != null && imgFile != null && w > 0 && h > 0) {
                    ZoomableRegionImage(
                        file = vm.repo.imageFile(imgFile),
                        imageW = w,
                        imageH = h,
                        containerW = containerW,
                        containerH = containerH,
                        scale = scale,
                        offsetX = offsetX,
                        offsetY = offsetY,
                        fitW = cFitW,
                        fitH = cFitH,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // ==== Слой перехвата жестов ====
        Box(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(row, col, subMode, fitW, fitH, inLinkMode) {
                    detectViewerGestures(
                        getViewport = { Viewport(scale, Offset(offsetX, offsetY)) },
                        fitW = fitW, fitH = fitH,
                        containerW = containerW, containerH = containerH,
                        onTransform = { vp ->
                            scale = vp.scale
                            offsetX = vp.offset.x
                            offsetY = vp.offset.y
                        },
                        onSwipe = { dx, dy ->
                            if (inLinkMode) {
                                returnFromLink()
                            } else if (subMode == ViewerSubMode.VIEW) {
                                val nr: Int; val nc: Int; val dir: SwipeDir
                                if (abs(dx) > abs(dy)) {
                                    if (dx < 0) { nr = row; nc = col + 1; dir = SwipeDir.LEFT }
                                    else         { nr = row; nc = col - 1; dir = SwipeDir.RIGHT }
                                } else {
                                    if (dy < 0) { nr = row + 1; nc = col; dir = SwipeDir.UP }
                                    else         { nr = row - 1; nc = col; dir = SwipeDir.DOWN }
                                }
                                if (vm.cell(nr, nc) != null) {
                                    persistViewport()
                                    swipeDir = dir; row = nr; col = nc
                                }
                            }
                        },
                        onTap = { pos ->
                            if (subMode != ViewerSubMode.VIEW) return@detectViewerGestures
                            val inCorner = pos.x > containerW - cornerPx && pos.y > containerH - cornerPx
                            if (inCorner) {
                                iconsHidden = !iconsHidden
                                return@detectViewerGestures
                            }
                            val hit = findPinAt(currentCell, pos, fitW, fitH, scale, Offset(offsetX, offsetY))
                            if (hit != null) {
                                if (hit.link != null) {
                                    jumpToLink(hit.link, row, col, hit.id)
                                } else {
                                    val now = System.currentTimeMillis()
                                    visibleTitles = if (hit.id in visibleTitles && (visibleTitles[hit.id] ?: 0L) > now)
                                        visibleTitles - hit.id
                                    else visibleTitles + (hit.id to (now + 5000L))
                                }
                            }
                        },
                        onLongPress = { pos ->
                            if (subMode != ViewerSubMode.VIEW) return@detectViewerGestures
                            val hit = findPinAt(currentCell, pos, fitW, fitH, scale, Offset(offsetX, offsetY))
                            if (hit != null) {
                                nav.navigate("comment/$row/$col/${hit.id}")
                            } else {
                                val cell = vm.cell(row, col) ?: return@detectViewerGestures
                                val imgX = (pos.x - offsetX) / scale
                                val imgY = (pos.y - offsetY) / scale
                                val nx = (imgX / fitW).coerceIn(0f, 1f)
                                val ny = (imgY / fitH).coerceIn(0f, 1f)
                                val cr = with(density) { 4.dp.toPx() }
                                val rad = (cr / (fitW * scale)).coerceIn(0.0015f, 0.3f)
                                val id = UUID.randomUUID().toString()
                                val pin = CommentPin(id = id, x = nx, y = ny, radius = rad, color = 0xFFFF5722L)
                                vm.setComments(row, col, cell.comments + pin)
                                nav.navigate("comment/$row/$col/$id")
                            }
                        }
                    )
                }
        )

        // ==== Комментарии ====
        if (currentCell != null) {
            for (pin in currentCell.comments) {
                CommentPinOverlay(
                    pin = pin, scale = scale, offset = Offset(offsetX, offsetY),
                    eyeMode = eyeMode, cell = currentCell,
                    fitW = fitW, fitH = fitH,
                    showTitle = pin.id in visibleTitles || eyeMode == EyeMode.SHOW_TITLES
                )
            }
        }

        // ==== Верхняя панель ====
        if (!iconsHidden) {
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedIconButton(
                    onClick = {
                        if (inLinkMode) {
                            returnFromLink()
                        } else {
                            persistViewport()
                            vm.autosave()
                            val target = if (vm.useAux) "builder?aux=true" else "builder?aux=false"
                            if (!nav.popBackStack(target, false)) {
                                nav.navigate(target) { popUpTo("menu") }
                            }
                        }
                    },
                    icon = Icons.Default.ArrowBack, description = "Назад", iconModifier = iconMod
                )
                Spacer(Modifier.weight(1f))
                OutlinedIconButton(
                    onClick = { flashlight.toggle() },
                    icon = Icons.Default.FlashOn, description = "Фонарик", iconModifier = iconMod
                )
                if (!inLinkMode) {
                    Spacer(Modifier.width(4.dp))
                    OutlinedIconButton(
                        onClick = {
                            persistViewport()
                            vm.useAux = !vm.useAux
                            val target = vm.active
                            savedRow = target.lastRow; savedCol = target.lastCol
                            row = target.lastRow; col = target.lastCol
                            pendingJump = null
                        },
                        icon = if (vm.useAux) Icons.Default.LockOpen else Icons.Default.Lock,
                        description = "Переключить массив", iconModifier = iconMod
                    )
                }
                Spacer(Modifier.width(4.dp))
                Box {
                    OutlinedIconButton(
                        onClick = { menuOpen = !menuOpen },
                        icon = Icons.Default.MoreVert, description = "Меню", iconModifier = iconMod
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (inLinkMode) {
                            val lr = vm.linkReturn!!
                            DropdownMenuItem(
                                text = { Text("Изменить привязку") },
                                onClick = {
                                    menuOpen = false
                                    nav.navigate("linkpicker/${lr.commentRow}/${lr.commentCol}/${lr.commentId}")
                                }
                            )
                        } else {
                            DropdownMenuItem(text = { Text("Общая точка") }, onClick = {
                                menuOpen = false; subMode = ViewerSubMode.SET_COMMON_POINT
                            })
                            DropdownMenuItem(
                                text = { Text("Свое положение: ${currentCell?.ownPosition ?: false}") },
                                onClick = {
                                    menuOpen = false
                                    val c = currentCell ?: return@DropdownMenuItem
                                    val newOwn = !c.ownPosition
                                    val vOx = offsetX - containerW / 2f + c.commonX * fitW * scale
                                    val vOy = offsetY - containerH / 2f + c.commonY * fitH * scale
                                    vm.updateCell(row, col) {
                                        it.copy(
                                            ownPosition = newOwn,
                                            ownScale = if (newOwn) scale else it.ownScale,
                                            ownOffsetX = if (newOwn) vOx else it.ownOffsetX,
                                            ownOffsetY = if (newOwn) vOy else it.ownOffsetY
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
            }

            // Боковые панели
            if (subMode == ViewerSubMode.VIEW && !inLinkMode) {
                Column(
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 4.dp, top = 60.dp, bottom = 100.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (r in rowRange) {
                        SideButton(
                            text = vm.active.rowLabels[r] ?: "R${r + 1}",
                            selected = (r == row),
                            onClick = {
                                if (vm.cell(r, col) != null) {
                                    persistViewport(); savedRow = r; savedCol = col; row = r
                                }
                            },
                            onLongClick = { labelDialog = true to r; labelText = vm.active.rowLabels[r] ?: "" }
                        )
                    }
                }
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 60.dp, end = 8.dp, bottom = 12.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (c in colRange) {
                        SideButton(
                            text = vm.active.colLabels[c] ?: "C${c + 1}",
                            selected = (c == col),
                            onClick = {
                                if (vm.cell(row, c) != null) {
                                    persistViewport(); savedRow = row; savedCol = c; col = c
                                }
                            },
                            onLongClick = { labelDialog = false to c; labelText = vm.active.colLabels[c] ?: "" }
                        )
                    }
                }
                Box(
                    Modifier.align(Alignment.BottomStart).padding(16.dp).size(48.dp)
                        .clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
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
                        tint = Color.White, iconSize = baseIconDp
                    )
                }
            }
        }

        // ==== Общая точка ====
        if (subMode == ViewerSubMode.SET_COMMON_POINT) {
            BlinkingDot(modifier = Modifier.align(Alignment.Center))
            OutlinedIconButton(
                onClick = {
                    val imgX = (containerW / 2f - offsetX) / scale
                    val imgY = (containerH / 2f - offsetY) / scale
                    val nx = (imgX / fitW).coerceIn(0f, 1f)
                    val ny = (imgY / fitH).coerceIn(0f, 1f)
                    vm.updateCell(row, col) { it.copy(commonX = nx, commonY = ny) }
                    if (currentCell?.ownPosition == true)
                        vm.updateCell(row, col) { it.copy(ownOffsetX = 0f, ownOffsetY = 0f) }
                    else vm.setGlobalViewport(scale, 0f, 0f, row, col)
                    offsetX = containerW / 2f - nx * fitW * scale
                    offsetY = containerH / 2f - ny * fitH * scale
                    subMode = ViewerSubMode.VIEW
                },
                icon = Icons.Default.Check, description = "Подтвердить", iconModifier = iconMod,
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
            )
        }

        // ==== Установка комментария по центру ====
        if (subMode == ViewerSubMode.PLACE_COMMENT) {
            BlinkingDot(modifier = Modifier.align(Alignment.Center))
            OutlinedIconButton(
                onClick = {
                    val cell = vm.cell(row, col)
                    if (cell != null) {
                        val imgX = (containerW / 2f - offsetX) / scale
                        val imgY = (containerH / 2f - offsetY) / scale
                        val nx = (imgX / fitW).coerceIn(0f, 1f)
                        val ny = (imgY / fitH).coerceIn(0f, 1f)
                        val cr = with(density) { 4.dp.toPx() }
                        val rad = (cr / (fitW * scale)).coerceIn(0.0015f, 0.3f)
                        val id = UUID.randomUUID().toString()
                        val pin = CommentPin(id = id, x = nx, y = ny, radius = rad, color = 0xFFFF5722L)
                        vm.setComments(row, col, cell.comments + pin)
                        subMode = ViewerSubMode.VIEW
                        nav.navigate("comment/$row/$col/$id")
                    }
                },
                icon = Icons.Default.Check, description = "Подтвердить", iconModifier = iconMod,
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
            )
        }

        if (showSettings && !iconsHidden) {
            SettingsSheet(
                iconSize = cfg.iconSize, iconOpacity = cfg.iconOpacity,
                onSizeChange = { vm.setIconSettings(it, vm.config.iconOpacity) },
                onOpacityChange = { vm.setIconSettings(vm.config.iconSize, it) },
                onDismiss = { showSettings = false }
            )
        }
    }

    labelDialog?.let { (isRow, idx) ->
        AlertDialog(
            onDismissRequest = { labelDialog = null },
            title = { Text(if (isRow) "Название строки ${idx + 1}" else "Название столбца ${idx + 1}") },
            text = {
                OutlinedTextField(
                    value = labelText,
                    onValueChange = { if (it.length <= 12) labelText = it },
                    label = { Text("Короткое название") },
                    supportingText = { Text("${labelText.length}/12") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (isRow) vm.setRowLabel(idx, labelText) else vm.setColLabel(idx, labelText)
                    labelDialog = null
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { labelDialog = null }) { Text("Отмена") } }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            vm.linkReturn = null
            vm.pendingRejump = null
            vm.autosave()
            flashlight.off()
        }
    }
}

// ==== Хелперы ====

private fun findPinAt(
    cell: CellData?, pos: Offset,
    fitW: Float, fitH: Float, scale: Float, offset: Offset
): CommentPin? {
    if (cell == null || cell.imageWidth <= 0) return null
    for (pin in cell.comments.reversed()) {
        val cx = pin.x * fitW * scale + offset.x
        val cy = pin.y * fitH * scale + offset.y
        val rad = pin.radius * fitW * scale * 1.3f
        val dx = pos.x - cx
        val dy = pos.y - cy
        if (dx * dx + dy * dy <= rad * rad) return pin
    }
    return null
}

@Composable
private fun SideButton(
    text: String, selected: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit
) {
    Box(
        Modifier
            .size(width = 52.dp, height = 40.dp)
            .scale(if (selected) 1.15f else 1f)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color(0xFF1565C0) else Color.Black.copy(alpha = 0.5f))
            .pointerInput(text, selected) {
                detectTapGestures(onTap = { onClick() }, onLongPress = { onLongClick() })
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text, color = Color.White, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun OutlinedIconButton(
    onClick: () -> Unit,
    icon: ImageVector, description: String,
    iconModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, description, tint = Color.White, modifier = iconModifier) }
}

@Composable
private fun BlinkingDot(modifier: Modifier = Modifier) {
    var a by remember { mutableStateOf(1f) }
    LaunchedEffect(Unit) { while (true) { a = 0.3f; delay(400); a = 1f; delay(400) } }
    Box(modifier.size(24.dp).alpha(a).clip(CircleShape).background(Color.Red))
}

@Composable
private fun CommentPinOverlay(
    pin: CommentPin, scale: Float, offset: Offset,
    eyeMode: EyeMode, cell: CellData, fitW: Float, fitH: Float,
    showTitle: Boolean
) {
    if (cell.imageWidth <= 0 || cell.imageHeight <= 0) return
    val density = LocalDensity.current
    val screenX = pin.x * fitW * scale + offset.x
    val screenY = pin.y * fitH * scale + offset.y
    val diameterPx: Float = when (eyeMode) {
        EyeMode.SCALING_UP -> {
            val s = scale.coerceIn(0.2f, 5f)
            (pin.radius * fitW * 2f / sqrt(s.toDouble()).toFloat())
                .coerceAtLeast(with(density) { 24.dp.toPx() })
        }
        else -> pin.radius * fitW * scale * 2f
    }
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
    val displayColor = if (eyeMode == EyeMode.BLINKING) BlinkColors[blinkIndex] else Color(pin.color.toInt())
    val displayAlpha = if (eyeMode == EyeMode.BLINKING && blinkPhase == 1) 0.3f else 1f
    val diameterDp = with(density) { diameterPx.toDp() }
    CommentIconShape(
        type = pin.iconType, color = displayColor, sizeDp = diameterDp, alpha = displayAlpha,
        modifier = Modifier.offset {
            IntOffset((screenX - diameterPx / 2f).toInt(), (screenY - diameterPx / 2f).toInt())
        }
    )
    if (showTitle && pin.title.isNotBlank()) {
        val titleFontPx = (diameterPx * 0.35f).coerceAtLeast(with(density) { 10.dp.toPx() })
        Text(
            text = pin.title, color = Color.White,
            fontSize = with(density) { titleFontPx.toSp() }, maxLines = 1,
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