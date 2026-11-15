package com.example.kotlovan.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.kotlovan.R
import com.example.kotlovan.vm.AppViewModel

private val ButtonYellow = Color(0xFFFFEB3B)
private val BlackBorder = BorderStroke(7.dp, Color.Black)

@Composable
fun MainMenuScreen(nav: NavHostController, vm: AppViewModel) {
    val ctx = LocalContext.current

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            if (vm.load(uri)) nav.navigate("builder")
            else Toast.makeText(ctx, "Не удалось прочитать файл", Toast.LENGTH_SHORT).show()
        }
    }

    // Проверяем, есть ли сохраненный массив (в памяти из autosave)
    val hasSavedArray = vm.config.main.cells.isNotEmpty() || vm.config.aux.cells.isNotEmpty()

    Box(modifier = Modifier.fillMaxSize()) {
        // Фоновая текстура чертежной бумаги
        Image(
            painter = painterResource(id = R.drawable.blueprint_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Контент меню поверх текстуры
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Котлован",
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(48.dp))

            // Кнопка "Продолжить" (появляется, если есть сохраненный массив)
            if (hasSavedArray) {
                Button(
                    onClick = { nav.navigate("viewer") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ButtonYellow,
                        contentColor = Color.Black
                    ),
                    border = BlackBorder,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(64.dp)
                ) {
                    Text("Продолжить", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(24.dp))
            }

            Button(
                onClick = { 
                    vm.newArray()
                    nav.navigate("builder") 
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ButtonYellow,
                    contentColor = Color.Black
                ),
                border = BlackBorder,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(64.dp)
            ) {
                Text("Создать массив", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { 
                    openLauncher.launch(arrayOf("application/zip", "application/json", "*/*")) 
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ButtonYellow,
                    contentColor = Color.Black
                ),
                border = BlackBorder,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(64.dp)
            ) {
                Text("Загрузить массив", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}