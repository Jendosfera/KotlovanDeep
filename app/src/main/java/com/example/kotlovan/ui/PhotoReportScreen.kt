package com.example.kotlovan.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowRight
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.kotlovan.data.CommentPin
import com.example.kotlovan.vm.AppViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Yellow = Color(0xFFFFEB3B)
private val Blue = Color(0xFF1565C0)
private val Red = Color(0xFFF44336)

@Composable
fun PhotoReportScreen(nav: NavHostController, vm: AppViewModel) {
    val ctx = LocalContext.current
    val archive = remember(vm.config.photoMain, vm.config.photoAux) {
		// Объединяем photoMain и photoAux в один SubArray для отчёта
		val mergedCells = LinkedHashMap<String, com.example.kotlovan.data.CellData>()
		mergedCells.putAll(vm.config.photoMain.cells)
		mergedCells.putAll(vm.config.photoAux.cells)
		com.example.kotlovan.data.SubArray(
			cells = mergedCells,
			rowLabels = vm.config.photoMain.rowLabels + vm.config.photoAux.rowLabels,
			colLabels = vm.config.photoMain.colLabels + vm.config.photoAux.colLabels
		)
	}

    val allPins = remember(archive) {
        val list = mutableListOf<Pair<Pair<Int, Int>, CommentPin>>()
        for ((k, cell) in archive.cells) {
            val p = k.split(",")
            val r = p.getOrNull(0)?.toIntOrNull() ?: continue
            val c = p.getOrNull(1)?.toIntOrNull() ?: continue
            for (pin in cell.comments) list += (r to c) to pin
        }
        list
    }
    val minDate = allPins.minOfOrNull { it.second.createdAt } ?: 0L
    val maxDate = allPins.maxOfOrNull { it.second.createdAt } ?: System.currentTimeMillis()

    var dateFrom by remember { mutableLongStateOf(minDate) }
    var dateTo by remember { mutableLongStateOf(maxDate) }

    val selectedRows = remember { mutableStateMapOf<Int, Boolean>() }
    val selectedCols = remember { mutableStateMapOf<Pair<Int, Int>, Boolean>() }
    val selectedPins = remember { mutableStateMapOf<String, Boolean>() }
    val expandedRows = remember { mutableStateMapOf<Int, Boolean>() }
    val expandedCols = remember { mutableStateMapOf<Pair<Int, Int>, Boolean>() }

    remember(allPins) {
        for ((rc, pin) in allPins) {
            selectedRows[rc.first] = true
            selectedCols[rc] = true
            selectedPins[pin.id] = true
        }
    }

    val filteredPins = allPins.filter { it.second.createdAt in dateFrom..dateTo }
    val grouped: Map<Int, Map<Int, List<CommentPin>>> = filteredPins
        .groupBy { it.first.first }
        .mapValues { (_, list) -> list.groupBy { it.first.second }.mapValues { (_, l) -> l.map { it.second } } }

    var showFilterDialog by remember { mutableStateOf(false) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            val ids = selectedPins.filterValues { it }.keys
            if (vm.generatePhotoReport(ids, dateFrom, dateTo, uri))
                Toast.makeText(ctx, "Фотоотчёт создан", Toast.LENGTH_SHORT).show()
            else Toast.makeText(ctx, "Ошибка", Toast.LENGTH_SHORT).show()
        }
    }

    Box(Modifier.fillMaxSize().background(Yellow).border(7.dp, Color.Black)) {
        Column(Modifier.fillMaxSize().padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Yellow)
                        .border(7.dp, Color.Black, CircleShape)
                        .clickable { showFilterDialog = true },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.FilterList, "Фильтр", tint = Color.Black) }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .background(Red).border(7.dp, Color.Black, CircleShape)
                        .clickable { nav.popBackStack() },
                    contentAlignment = Alignment.Center
                ) { Text("×", color = Color.Black, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(Modifier.weight(1f)) {
                for ((r, colsMap) in grouped) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().background(Yellow)
                                .border(3.dp, Color.Black).padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedRows[r] ?: true,
                                onCheckedChange = { checked ->
                                    selectedRows[r] = checked
                                    for ((c, pins) in colsMap) {
                                        selectedCols[r to c] = checked
                                        for (pin in pins) selectedPins[pin.id] = checked
                                    }
                                }
                            )
                            Text(
                                archive.rowLabels[r] ?: "Строка ${r + 1}",
                                Modifier.weight(1f), color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                if (expandedRows[r] == true) Icons.Default.ArrowDropDown else Icons.Default.ArrowRight,
                                "Развернуть",
                                modifier = Modifier.size(28.dp).clickable {
                                    expandedRows[r] = !(expandedRows[r] ?: false)
                                },
                                tint = Color.Black
                            )
                        }
                    }
                    if (expandedRows[r] == true) {
                        for ((c, pins) in colsMap) {
                            item {
                                Row(
                                    Modifier.fillMaxWidth().padding(start = 20.dp)
                                        .border(2.dp, Color.Black.copy(alpha = 0.5f))
                                        .padding(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = selectedCols[r to c] ?: true,
                                        onCheckedChange = { checked ->
                                            selectedCols[r to c] = checked
                                            for (pin in pins) selectedPins[pin.id] = checked
                                        }
                                    )
                                    Text(
                                        archive.colLabels[c] ?: "Столбец ${c + 1}",
                                        Modifier.weight(1f), color = Color.Black
                                    )
                                    Icon(
                                        if (expandedCols[r to c] == true)
                                            Icons.Default.ArrowDropDown else Icons.Default.ArrowRight,
                                        "Развернуть",
                                        modifier = Modifier.size(24.dp).clickable {
                                            expandedCols[r to c] = !(expandedCols[r to c] ?: false)
                                        },
                                        tint = Color.Black
                                    )
                                }
                            }
                            if (expandedCols[r to c] == true) {
                                items(pins) { pin ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(start = 40.dp)
                                            .padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = selectedPins[pin.id] ?: true,
                                            onCheckedChange = { selectedPins[pin.id] = it }
                                        )
                                        Text(
                                            pin.title.ifBlank { "Комментарий (${pin.photos.size} фото)" },
                                            color = Color.Black
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    val name = "Фотоотчет от " +
                            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date()) + ".zip"
                    saveLauncher.launch(name)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Color.Black),
                border = BorderStroke(7.dp, Color.Black),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text("Создать фотоотчёт", fontSize = 18.sp) }
        }
    }

    if (showFilterDialog) {
        val dfmt = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        var fromText by remember { mutableStateOf(dfmt.format(Date(dateFrom))) }
        var toText by remember { mutableStateOf(dfmt.format(Date(dateTo))) }

        AlertDialog(
            onDismissRequest = { showFilterDialog = false },
            containerColor = Yellow,
            title = { Text("Диапазон дат", color = Color.Black) },
            text = {
                Column {
                    Text("от", color = Color.Black)
                    OutlinedTextField(value = fromText, onValueChange = { fromText = it },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("до", color = Color.Black)
                    OutlinedTextField(value = toText, onValueChange = { toText = it },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        val f = dfmt.parse(fromText)?.time ?: dateFrom
                        val t = dfmt.parse(toText)?.time ?: dateTo
                        dateFrom = minOf(f, t)
                        dateTo = maxOf(f, t)
                        showFilterDialog = false
                    } catch (_: Exception) {}
                }) { Text("Применить", color = Blue) }
            },
            dismissButton = {
                TextButton(onClick = {
                    dateFrom = minDate
                    dateTo = maxDate
                    showFilterDialog = false
                }) { Text("Сброс", color = Red) }
            }
        )
    }
}