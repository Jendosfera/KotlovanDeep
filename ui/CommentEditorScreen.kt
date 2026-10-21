package com.example.kotlovan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.kotlovan.vm.AppViewModel

@Composable
fun CommentEditorScreen(nav: NavHostController, vm: AppViewModel, row: Int, col: Int, id: String) {
    val cell = vm.cell(row, col) ?: return
    val pin = cell.comments.firstOrNull { it.id == id } ?: return
    var title by remember { mutableStateOf(pin.title) }
    var text by remember { mutableStateOf(pin.text) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Назад") }
            Text("Комментарий", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(title, { title = it }, label = { Text("Короткое название") },
            modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(text, { text = it }, label = { Text("Текст комментария") },
            modifier = Modifier.fillMaxWidth().height(180.dp))
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { /* TODO: режим перемещения комментария: смещение pin.x/y */ },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Переместить комментарий") }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.weight(1f)
            ) { Icon(Icons.Default.Delete, null); Text(" Удалить") }
            Button(
                onClick = {
                    val updated = cell.comments.map {
                        if (it.id == id) it.copy(title = title, text = text) else it
                    }
                    vm.setComments(row, col, updated)
                    nav.popBackStack()
                },
                modifier = Modifier.weight(1f)
            ) { Text("Сохранить") }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить комментарий?") },
            text = { Text("Действие нельзя отменить") },
            confirmButton = {
                TextButton(onClick = {
                    vm.setComments(row, col, cell.comments.filterNot { it.id == id })
                    confirmDelete = false; nav.popBackStack()
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Отмена") }
            }
        )
    }
}