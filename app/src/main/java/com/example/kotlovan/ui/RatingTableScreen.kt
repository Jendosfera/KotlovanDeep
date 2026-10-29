package com.example.kotlovan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.kotlovan.data.CommentPin
import com.example.kotlovan.vm.AppViewModel

private data class RatingRow(
    val pin: CommentPin, val row: Int, val col: Int, val depth: Int, val isAux: Boolean
)

@Composable
fun RatingTableScreen(nav: NavHostController, vm: AppViewModel) {
    val yellow = Color(0xFFFFEB3B)
    val blue = Color(0xFF1565C0)

    // Собираем все комментарии
	val rows = remember(
		vm.config.main.cells, vm.config.aux.cells,
		vm.config.photoMain.cells, vm.config.photoAux.cells,
		vm.photoModeActive
	) {
		val list = mutableListOf<RatingRow>()
		fun collect(cells: Map<String, com.example.kotlovan.data.CellData>, isAux: Boolean) {
			for ((k, c) in cells) {
				val p = k.split(",")
				val r = p.getOrNull(0)?.toIntOrNull() ?: continue
				val cc = p.getOrNull(1)?.toIntOrNull() ?: continue
				val dd = p.getOrNull(2)?.toIntOrNull() ?: 0
				for (pin in c.comments) list += RatingRow(pin, r, cc, dd, isAux)
			}
		}
		if (vm.photoModeActive) {
			collect(vm.config.photoMain.cells, false)
			collect(vm.config.photoAux.cells, true)
		} else {
			collect(vm.config.main.cells, false)
			collect(vm.config.aux.cells, true)
		}
		list.sortedByDescending { it.pin.rating }
	}

    val listState = rememberLazyListState()
    LaunchedEffect(vm.lastRatingRow) {
        if (vm.lastRatingRow in rows.indices) listState.scrollToItem(vm.lastRatingRow)
    }

    var editRating by remember { mutableStateOf<RatingRow?>(null) }
    var showRowsDialog by remember { mutableStateOf(false) }
    var showColsDialog by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(yellow).border(7.dp, blue)) {
        Column(Modifier.fillMaxSize().padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Назад", tint = blue) }
                Text("Рейтинг комментариев", color = blue, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
            }

            // Заголовок таблицы
            Row(
                Modifier.fillMaxWidth().background(yellow).border(3.dp, Color.Black).padding(4.dp)
            ) {
                Text("Рейтинг", Modifier.width(70.dp), color = Color.Black, fontWeight = FontWeight.Bold)
                Text("Название", Modifier.weight(1f), color = Color.Black, fontWeight = FontWeight.Bold)
                Text("Строка", Modifier.width(80.dp), color = Color.Black, fontWeight = FontWeight.Bold)
                Text("Столбец", Modifier.width(80.dp), color = Color.Black, fontWeight = FontWeight.Bold)
            }

            // Данные
            LazyColumn(Modifier.weight(1f).border(3.dp, Color.Black), state = listState) {
                items(rows.size) { idx ->
                    val r = rows[idx]
                    if (r.row in vm.hiddenRows || r.col in vm.hiddenCols) return@items
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)
                            .border(1.dp, Color.Black.copy(alpha = 0.3f))
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Рейтинг
                        Box(
                            Modifier.width(70.dp).pointerInput(r.pin.id) {
                                detectTapGestures(onDoubleTap = { editRating = r })
                            },
                            contentAlignment = Alignment.Center
                        ) { Text("${r.pin.rating}", color = Color.Black, fontWeight = FontWeight.Bold) }

                        // Название
                        Box(
                            Modifier.weight(1f).pointerInput(r.pin.id) {
                                detectTapGestures(onDoubleTap = {
                                    vm.lastRatingRow = idx
                                    vm.useAux = r.isAux
                                    nav.navigate("comment/${r.row}/${r.col}/${r.pin.id}/?depth=${r.depth}")
                                })
                            }
                        ) {
                            Text(r.pin.title.ifBlank { "Комментарий" }, color = Color.Black)
                        }

                        // Строка
                        Box(
                            Modifier.width(80.dp).pointerInput(r.pin.id) {
                                detectTapGestures(onDoubleTap = { showRowsDialog = true })
                            }
                        ) {
                            Text(vm.config.main.rowLabels[r.row] ?: vm.config.aux.rowLabels[r.row] ?: "R${r.row + 1}",
                                color = Color.Black)
                        }

                        // Столбец
                        Box(
                            Modifier.width(80.dp).pointerInput(r.pin.id) {
                                detectTapGestures(onDoubleTap = { showColsDialog = true })
                            }
                        ) {
                            Text(vm.config.main.colLabels[r.col] ?: vm.config.aux.colLabels[r.col] ?: "C${r.col + 1}",
                                color = Color.Black)
                        }
                    }
                }
            }
        }
    }

    // Диалог редактирования рейтинга
    editRating?.let { r ->
        var text by remember(r.pin.id) { mutableStateOf(r.pin.rating.toString()) }
        AlertDialog(
            onDismissRequest = { editRating = null },
            containerColor = yellow,
            title = { Text("Изменить рейтинг", color = blue) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { ch -> ch.isDigit() || ch == '-' } },
                    label = { Text("Рейтинг") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val newVal = text.toIntOrNull() ?: r.pin.rating
                    val sub = if (r.isAux) vm.config.aux else vm.config.main
                    val key = sub.key(r.row, r.col)
                    val cell = sub.cell(r.row, r.col)
                    if (cell != null) {
                        val updated = cell.copy(comments = cell.comments.map {
                            if (it.id == r.pin.id) it.copy(rating = newVal) else it
                        })
                        vm.useAux = r.isAux
                        vm.upsertCell(r.row, r.col, updated)
                        vm.autosave()
                    }
                    editRating = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { editRating = null }) { Text("Отмена") } }
        )
    }

    if (showRowsDialog) {
        val allRows = (vm.config.main.cells.keys + vm.config.aux.cells.keys)
            .mapNotNull { it.split(",").getOrNull(0)?.toIntOrNull() }.distinct().sorted()
        AlertDialog(
            onDismissRequest = { showRowsDialog = false },
            containerColor = yellow,
            title = { Text("Строки", color = blue) },
            text = {
                Column {
                    for (r in allRows) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = r !in vm.hiddenRows,
                                onCheckedChange = { vm.toggleRowVisibility(r) }
                            )
                            Text(vm.config.main.rowLabels[r] ?: "R${r + 1}", color = Color.Black)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showRowsDialog = false }) { Text("OK") } }
        )
    }

    if (showColsDialog) {
        val allCols = (vm.config.main.cells.keys + vm.config.aux.cells.keys)
            .mapNotNull { it.split(",").getOrNull(0)?.toIntOrNull() }.distinct().sorted()
        AlertDialog(
            onDismissRequest = { showColsDialog = false },
            containerColor = yellow,
            title = { Text("Столбцы", color = blue) },
            text = {
                Column {
                    for (c in allCols) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = c !in vm.hiddenCols,
                                onCheckedChange = { vm.toggleColVisibility(c) }
                            )
                            Text(vm.config.main.colLabels[c] ?: "C${c + 1}", color = Color.Black)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showColsDialog = false }) { Text("OK") } }
        )
    }
}
