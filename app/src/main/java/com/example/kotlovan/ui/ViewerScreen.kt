package com.example.kotlovan.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
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

@Composable
fun ViewerScreen(nav: NavHostController, vm: AppViewModel) {
    val ctx = LocalContext.current
    val cfg = vm.config

    var row by rememberSaveable { mutableIntStateOf(vm.active.lastRow) }
    var col by rememberSaveable { mutableIntStateOf(vm.active.lastCol) }
    var depth by rememberSaveable { mutableIntStateOf(vm.active.lastDepth) }
    var savedRow by rememberSaveable { mutableIntStateOf(-1) }
    var savedCol by rememberSaveable { mutableIntStateOf(-1) }
    var scale by rememberSaveable { mutableFloatStateOf(1f) }
    var offsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var offsetY by rememberSaveable { mutableFloatStateOf(0f) }
    var menuOpen by remember { mutableStateOf(false) }
    var subMode by remember { mutableStateOf(ViewerSubMode.VIEW) }
    var swipeDir by remember { mutableStateOf(SwipeDir.NONE) }
    var showSettings by remember { mutableStateOf(false) }
    var iconsHidden by remember { mutableStateOf(false) }
    var showAllRow by remember { mutableStateOf(false) }
    var contextMenu by remember { mutableStateOf<Pair<Offset, List<CommentPin>>?>(null) }
    var pendingJump by remember { mutableStateOf<PendingJump?>(null) }
    var visibleTitles by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var labelDialog by remember { mutableStateOf<Pair<Boolean, Int>?>(null) }
    var labelText by remember { mutableStateOf("") }
    val flashlight = remember { Flashlight(ctx) }

    val inLinkMode = vm.linkReturn != null

    val saveArchiveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}
            if (vm.savePhotoArchive(uri))
                Toast.makeText(ctx, "Архив сохранён", Toast.LENGTH_SHORT).show()
            else Toast.makeText(ctx, "Ошибка", Toast.LENGTH_SHORT).show()
        }
    }
    val openArchiveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            if (vm.loadPhotoArchive(uri))
                Toast.makeText(ctx, "Архив загружен", Toast.LENGTH_SHORT).show()
            else Toast.makeText(ctx, "Ошибка загрузки", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            val now = System.currentTimeMillis()
            visibleTitles = visibleTitles.filterValues { it > now }
        }
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black)
            .then(if (inLinkMode) Modifier.border(6.dp, LinkBorderColor) else Modifier)
    ) {
        val containerW = constraints.maxWidth.toFloat()
        val containerH = constraints.maxHeight.toFloat()
        val density = LocalDensity.current

        val maxDepth = vm.depthAt(row, col)
        if (maxDepth < 0) { row = vm.active.lastRow; col = vm.active.lastCol; depth = 0 }
        if (depth > maxDepth && maxDepth >= 0) depth = 0

        val currentCell = vm.cell(row, col, depth)
        val imageW = currentCell?.imageWidth ?: 0
        val imageH = currentCell?.imageHeight ?: 0
        val fitScale = if (imageW > 0 && imageH > 0) min(containerW / imageW, containerH / imageH) else 1f
        val fitW = if (imageW > 0) imageW * fitScale else containerW
        val fitH = if (imageH > 0) imageH * fitScale else containerH

        val baseIconDp = 28.dp * cfg.iconSize
        val iconMod = Modifier.size(baseIconDp).alpha(cfg.iconOpacity)

        val allRows = vm.active.cells.keys.mapNotNull { it.split(",").getOrNull(0)?.toIntOrNull() }
        val allCols = vm.active.cells.keys.mapNotNull { it.split(",").getOrNull(1)?.toIntOrNull() }
        val rowRange = if (allRows.isEmpty()) 0..0 else allRows.min()..allRows.max()
        val colRange = if (allCols.isEmpty()) 0..0 else allCols.min()..allCols.max()

        val rowColsList = vm.active.cells.keys.mapNotNull { k ->
            val p = k.split(",")
            if (p.getOrNull(0)?.toIntOrNull() == row) p.getOrNull(1)?.toIntOrNull() else null
        }.distinct().sorted()
        val colRowsList = vm.active.cells.keys.mapNotNull { k ->
            val p = k.split(",")
            if (p.getOrNull(1)?.toIntOrNull() == col) p.getOrNull(0)?.toIntOrNull() else null
        }.distinct().sorted()

        fun persistViewport() {
            val c = vm.cell(row, col, depth) ?: return
            val vOx = offsetX - containerW / 2f + c.commonX * fitW * scale
            val vOy = offsetY - containerH / 2f + c.commonY * fitH * scale
            if (c.ownPosition) {
                vm.updateCell(row, col, depth) { it.copy(ownScale = scale, ownOffsetX = vOx, ownOffsetY = vOy) }
            } else {
                vm.setGlobalViewport(scale, vOx, vOy, row, col, depth)
            }
        }

        fun returnFromLink() {
            val lr = vm.linkReturn ?: return
            vm.useAux = lr.isAux
            savedRow = lr.row; savedCol = lr.col
            row = lr.row; col = lr.col; depth = 0
            scale = lr.scale
            offsetX = lr.offX; offsetY = lr.offY
            vm.linkReturn = null
        }

        fun jumpToLink(link: PinLink, sourceRow: Int, sourceCol: Int, sourceId: String, keepReturn: Boolean = false) {
            if (!keepReturn) {
                vm.linkReturn = LinkReturnState(
                    isAux = vm.useAux, row = row, col = col,
                    scale = scale, offX = offsetX, offY = offsetY,
                    commentRow = sourceRow, commentCol = sourceCol, commentId = sourceId
                )
            }
            vm.useAux = link.isAux
            savedRow = link.row; savedCol = link.col
            row = link.row; col = link.col; depth = 0
            subMode = ViewerSubMode.VIEW
            pendingJump = PendingJump(link.x, link.y, link.scale)
        }

        LaunchedEffect(vm.pendingRejump) {
            val pj = vm.pendingRejump ?: return@LaunchedEffect
            vm.pendingRejump = null
            val lr = vm.linkReturn ?: return@LaunchedEffect
            jumpToLink(pj, lr.commentRow, lr.commentCol, lr.commentId, keepReturn = true)
        }

        LaunchedEffect(row, col, depth, fitW, fitH, pendingJump, vm.useAux, vm.photoModeActive) {
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
            val c = vm.cell(row, col, depth) ?: return@LaunchedEffect
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

        // Изображение
        AnimatedContent(
            targetState = "$row,$col,$depth",
            transitionSpec = {
                val inSpec: androidx.compose.animation.EnterTransition
                val outSpec: androidx.compose.animation.ExitTransition
                when (swipeDir) {
                    SwipeDir.LEFT -> { inSpec = slideInHorizontally { it } + fadeIn(); outSpec = slideOutHorizontally { -it } + fadeOut() }
                    SwipeDir.RIGHT -> { inSpec = slideInHorizontally { -it } + fadeIn(); outSpec = slideOutHorizontally { it } + fadeOut() }
                    SwipeDir.UP -> { inSpec = slideInVertically { it } + fadeIn(); outSpec = slideOutVertically { -it } + fadeOut() }
                    SwipeDir.DOWN -> { inSpec = slideInVertically { -it } + fadeIn(); outSpec = slideOutVertically { it } + fadeOut() }
                    SwipeDir.NONE -> { inSpec = fadeIn(); outSpec = fadeOut() }
                }
                (inSpec togetherWith outSpec).using(SizeTransform(clip = false))
            },
            label = "cellSwipe"
        ) { key ->
            val parts = key.split(",")
            val r = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val c = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val d = parts.getOrNull(2)?.toIntOrNull() ?: 0
            val cell = vm.cell(r, c, d)
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
                        imageW = w, imageH = h,
                        containerW = containerW, containerH = containerH,
                        scale = scale, offsetX = offsetX, offsetY = offsetY,
                        fitW = cFitW, fitH = cFitH,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Жесты
        Box(
            Modifier.fillMaxSize().clipToBounds()
                .pointerInput(row, col, depth, subMode, fitW, fitH, inLinkMode, showAllRow, vm.photoModeActive) {
                    detectViewerGestures(
                        getViewport = { Viewport(scale, Offset(offsetX, offsetY)) },
                        fitW = fitW, fitH = fitH,
                        containerW = containerW, containerH = containerH,
                        onTransform = { vp -> scale = vp.scale; offsetX = vp.offset.x; offsetY = vp.offset.y },
                        onSwipe = { dx, dy ->
                            if (inLinkMode) {
                                returnFromLink()
                            } else if (subMode == ViewerSubMode.VIEW) {
                                val dir: SwipeDir
                                var nr = row
                                var nc = col
                                if (abs(dx) > abs(dy)) {
                                    if (dx < 0) {
                                        dir = SwipeDir.LEFT
                                        if (rowColsList.isNotEmpty()) {
                                            val idx = rowColsList.indexOf(col).let { if (it == -1) 0 else it }
                                            nc = rowColsList[(idx + 1).mod(rowColsList.size)]
                                        }
                                    } else {
                                        dir = SwipeDir.RIGHT
                                        if (rowColsList.isNotEmpty()) {
                                            val idx = rowColsList.indexOf(col).let { if (it == -1) 0 else it }
                                            nc = rowColsList[(idx - 1).mod(rowColsList.size)]
                                        }
                                    }
                                } else {
                                    if (dy < 0) {
                                        dir = SwipeDir.UP
                                        if (colRowsList.isNotEmpty()) {
                                            val idx = colRowsList.indexOf(row).let { if (it == -1) 0 else it }
                                            nr = colRowsList[(idx + 1).mod(colRowsList.size)]
                                        }
                                    } else {
                                        dir = SwipeDir.DOWN
                                        if (colRowsList.isNotEmpty()) {
                                            val idx = colRowsList.indexOf(row).let { if (it == -1) 0 else it }
                                            nr = colRowsList[(idx - 1).mod(colRowsList.size)]
                                        }
                                    }
                                }
                                if (nr != row || nc != col) {
                                    persistViewport()
                                    swipeDir = dir
                                    row = nr; col = nc; depth = 0
                                }
                            }
                        },
                        onTap = { pos ->
                            if (subMode != ViewerSubMode.VIEW) return@detectViewerGestures
                            val inCorner = pos.x > containerW - cornerPx && pos.y > containerH - cornerPx
                            if (inCorner) { iconsHidden = !iconsHidden; return@detectViewerGestures }
                            val hit = findPinAt2(row, col, depth, showAllRow, pos, fitW, fitH, scale, Offset(offsetX, offsetY), vm)
                            if (hit != null) {
                                val (hr, hc, pin) = hit
                                if (pin.link != null) jumpToLink(pin.link, hr, hc, pin.id)
                                else nav.navigate("comment/$hr/$hc/${pin.id}?depth=$depth")
                            }
                        },
                        onLongPress = { pos ->
                            if (subMode != ViewerSubMode.VIEW) return@detectViewerGestures
                            if (vm.photoModeActive) {
                                contextMenu = pos to emptyList()
                                return@detectViewerGestures
                            }
                            val cell = vm.cell(row, col, depth) ?: return@detectViewerGestures
                            val visible = cell.comments.filter { pin ->
                                val sx = pin.x * fitW * scale + offsetX
                                val sy = pin.y * fitH * scale + offsetY
                                sx in 0f..containerW && sy in 0f..containerH
                            }
                            val rowPins = if (showAllRow) {
                                val extra = mutableListOf<CommentPin>()
                                for (c in colRange) {
                                    if (c == col) continue
                                    val oc = vm.cell(row, c, 0) ?: continue
                                    extra += oc.comments
                                }
                                visible + extra
                            } else visible
                            if (rowPins.isNotEmpty()) contextMenu = pos to rowPins
                        }
                    )
                }
        )

        // Комментарии
        if (currentCell != null) {
            for (pin in currentCell.comments) {
                CommentPinRender(
                    pin = pin, scale = scale, offset = Offset(offsetX, offsetY),
                    fitW = fitW, fitH = fitH,
                    showTitle = pin.id in visibleTitles
                )
            }
            if (showAllRow) {
                for (c in colRange) {
                    if (c == col) continue
                    val oc = vm.cell(row, c, 0) ?: continue
                    for (pin in oc.comments) {
                        val dx = pin.x - oc.commonX
                        val dy = pin.y - oc.commonY
                        val ax = currentCell.commonX + dx
                        val ay = currentCell.commonY + dy
                        if (ax !in 0f..1f || ay !in 0f..1f) continue
                        CommentPinRender(
                            pin = pin.copy(x = ax, y = ay),
                            scale = scale, offset = Offset(offsetX, offsetY),
                            fitW = fitW, fitH = fitH,
                            showTitle = false, dimmed = true
                        )
                    }
                }
            }
        }

        // Верхняя панель
        if (!iconsHidden) {
            ViewerTopBar(
                vm = vm,
                currentCell = currentCell,
                maxDepth = maxDepth,
                depth = depth,
                menuOpen = menuOpen,
                inLinkMode = inLinkMode,
                baseIconDp = baseIconDp,
                iconMod = iconMod,
                onBack = {
                    if (inLinkMode) returnFromLink()
                    else {
                        persistViewport(); vm.autosave()
                        val target = if (vm.useAux) "builder?aux=true" else "builder?aux=false"
                        if (!nav.popBackStack(target, false)) nav.navigate(target) { popUpTo("menu") }
                    }
                },
                onFlash = { flashlight.toggle() },
                onTogglePhoto = {
                    vm.photoModeActive = !vm.photoModeActive
                    savedRow = -1; savedCol = -1
                    val t = vm.active
                    row = t.lastRow; col = t.lastCol; depth = 0
                    pendingJump = null
                },
                onToggleLayer = {
                    persistViewport()
                    depth = if (depth >= maxDepth) 0 else depth + 1
                    savedRow = -1; savedCol = -1
                    pendingJump = null
                },
                onToggleArray = {
                    persistViewport()
                    vm.useAux = !vm.useAux
                    val t = vm.active
                    savedRow = -1; savedCol = -1
                    row = t.lastRow; col = t.lastCol; depth = 0
                    pendingJump = null
                },
                onMenuOpenToggle = { menuOpen = !menuOpen },
                onMenuDismiss = { menuOpen = false },
                onChangeLink = {
                    val lr = vm.linkReturn!!
                    nav.navigate("linkpicker/${lr.commentRow}/${lr.commentCol}/${lr.commentId}?depth=0")
                },
                onSetCommonPoint = { subMode = ViewerSubMode.SET_COMMON_POINT },
                onOwnPositionToggle = {
                    val c = currentCell ?: return@ViewerTopBar
                    val newOwn = !c.ownPosition
                    val vOx = offsetX - containerW / 2f + c.commonX * fitW * scale
                    val vOy = offsetY - containerH / 2f + c.commonY * fitH * scale
                    vm.updateCell(row, col, depth) {
                        it.copy(ownPosition = newOwn,
                            ownScale = if (newOwn) scale else it.ownScale,
                            ownOffsetX = if (newOwn) vOx else it.ownOffsetX,
                            ownOffsetY = if (newOwn) vOy else it.ownOffsetY)
                    }
                },
                onPlaceComment = { subMode = ViewerSubMode.PLACE_COMMENT },
                onSettings = { showSettings = true }
            )

            if (subMode == ViewerSubMode.VIEW && !inLinkMode) {
                ViewerSideButtons(
                    vm = vm,
                    row = row,
                    col = col,
                    rowRange = rowRange,
                    colRange = colRange,
                    showAllRow = showAllRow,
                    baseIconDp = baseIconDp,
                    onRowClick = { r -> persistViewport(); row = r; depth = 0 },
                    onColClick = { c -> persistViewport(); col = c; depth = 0 },
                    onRowLong = { r -> labelDialog = true to r; labelText = vm.active.rowLabels[r] ?: "" },
                    onColLong = { c -> labelDialog = false to c; labelText = vm.active.colLabels[c] ?: "" },
                    onToggleAllRow = { showAllRow = !showAllRow }
                )
            }
        }

        // Общая точка
        if (subMode == ViewerSubMode.SET_COMMON_POINT) {
            CrosshairBlack(Modifier.align(Alignment.Center))
            OutlinedIconButton(
                onClick = {
                    val imgX = (containerW / 2f - offsetX) / scale
                    val imgY = (containerH / 2f - offsetY) / scale
                    val nx = (imgX / fitW).coerceIn(0f, 1f)
                    val ny = (imgY / fitH).coerceIn(0f, 1f)
                    vm.updateCell(row, col, depth) { it.copy(commonX = nx, commonY = ny) }
                    if (currentCell?.ownPosition == true)
                        vm.updateCell(row, col, depth) { it.copy(ownOffsetX = 0f, ownOffsetY = 0f) }
                    else vm.setGlobalViewport(scale, 0f, 0f, row, col, depth)
                    offsetX = containerW / 2f - nx * fitW * scale
                    offsetY = containerH / 2f - ny * fitH * scale
                    subMode = ViewerSubMode.VIEW
                },
                icon = Icons.Default.Check, description = "OK", iconModifier = iconMod,
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
            )
        }

        // Установка комментария
        if (subMode == ViewerSubMode.PLACE_COMMENT) {
            CrosshairBlack(Modifier.align(Alignment.Center))
            OutlinedIconButton(
                onClick = {
                    val cell = vm.cell(row, col, depth)
                    if (cell != null) {
                        val imgX = (containerW / 2f - offsetX) / scale
                        val imgY = (containerH / 2f - offsetY) / scale
                        val nx = (imgX / fitW).coerceIn(0f, 1f)
                        val ny = (imgY / fitH).coerceIn(0f, 1f)
                        val cr = with(density) { 8.dp.toPx() }
                        val rad = (cr / (fitW * scale)).coerceIn(0.003f, 0.3f)
                        val id = UUID.randomUUID().toString()
                        val pin = CommentPin(id = id, x = nx, y = ny, radius = rad)
                        vm.setComments(row, col, cell.comments + pin, depth)
                        subMode = ViewerSubMode.VIEW
                        if (vm.photoModeActive) {
                            nav.navigate("comment/$row/$col/$id?depth=$depth&autoCamera=true")
                        } else {
                            nav.navigate("comment/$row/$col/$id?depth=$depth")
                        }
                    }
                },
                icon = Icons.Default.Check, description = "OK", iconModifier = iconMod,
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
            )
        }

        // Контекстное меню
        contextMenu?.let { (pos, pins) ->
            ViewerContextMenu(
                pos = pos,
                pins = pins,
                photoModeActive = vm.photoModeActive,
                onClose = { contextMenu = null },
                onSaveArchive = {
                    contextMenu = null
                    saveArchiveLauncher.launch("Общий архив.zip")
                },
                onOpenArchive = {
                    contextMenu = null
                    openArchiveLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                },
                onOpenReport = {
                    contextMenu = null
                    nav.navigate("photoreport")
                },
                onPinClick = { pin ->
                    nav.navigate("comment/$row/$col/${pin.id}?depth=$depth")
                    contextMenu = null
                }
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
                    label = { Text("Название") },
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