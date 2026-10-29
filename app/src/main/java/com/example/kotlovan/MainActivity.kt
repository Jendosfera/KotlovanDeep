package com.example.kotlovan

import com.example.kotlovan.ui.PhotoReportScreen
import com.example.kotlovan.ui.RatingTableScreen
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.kotlovan.theme.ThemeScope
import com.example.kotlovan.ui.ArrayBuilderScreen
import com.example.kotlovan.ui.CellCardScreen
import com.example.kotlovan.ui.CommentEditorScreen
import com.example.kotlovan.ui.LinkPickerScreen
import com.example.kotlovan.ui.MainMenuScreen
import com.example.kotlovan.ui.SettingsScreen
import com.example.kotlovan.ui.ViewerScreen
import com.example.kotlovan.ui.theme.KotlovanTheme
import com.example.kotlovan.vm.AppViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KotlovanTheme { RootNav() }
        }
    }
}

@Composable
fun RootNav() {
    val vm: AppViewModel = viewModel()
    val nav = rememberNavController()

    ThemeScope(vm.themeProfile) {
        NavHost(nav, startDestination = "menu") {
            composable("menu") { MainMenuScreen(nav, vm) }
            composable("settings") { SettingsScreen(nav, vm) }

            composable("rating") { RatingTableScreen(nav, vm) }
            
			composable(
				route = "builder?aux={aux}&selectForNested={selectForNested}",
				arguments = listOf(
					navArgument("aux") { type = NavType.BoolType; defaultValue = false },
					navArgument("selectForNested") { type = NavType.StringType; defaultValue = "" }
				)
			) { back ->
				val aux = back.arguments?.getBoolean("aux") ?: false
				val selectForNested = back.arguments?.getString("selectForNested") ?: ""
				ArrayBuilderScreen(nav, vm, aux, selectForNested)
			}

            composable("card/{row}/{col}") { back ->
                val row = back.arguments?.getString("row")?.toIntOrNull() ?: 0
                val col = back.arguments?.getString("col")?.toIntOrNull() ?: 0
                CellCardScreen(nav, vm, row, col)
            }

            composable("viewer") { ViewerScreen(nav, vm) }

			composable(
				route = "comment/{row}/{col}/{id}?depth={depth}&autoCamera={autoCamera}",
				arguments = listOf(
					navArgument("row") { type = NavType.IntType },
					navArgument("col") { type = NavType.IntType },
					navArgument("id") { type = NavType.StringType },
					navArgument("depth") { type = NavType.IntType; defaultValue = 0 },
					navArgument("autoCamera") { type = NavType.BoolType; defaultValue = false }
				)
			) { back ->
				val row = back.arguments?.getInt("row") ?: 0
				val col = back.arguments?.getInt("col") ?: 0
				val id = back.arguments?.getString("id") ?: ""
				val depth = back.arguments?.getInt("depth") ?: 0
				val autoCamera = back.arguments?.getBoolean("autoCamera") ?: false
				CommentEditorScreen(nav, vm, row, col, id, depth, autoCamera)
			}
			
			composable("photoreport") { PhotoReportScreen(nav, vm) }

            composable("linkpicker/{row}/{col}/{id}") { back ->
                val row = back.arguments?.getString("row")?.toIntOrNull() ?: 0
                val col = back.arguments?.getString("col")?.toIntOrNull() ?: 0
                val id = back.arguments?.getString("id") ?: ""
                LinkPickerScreen(nav, vm, row, col, id)
            }
        }
    }
}
