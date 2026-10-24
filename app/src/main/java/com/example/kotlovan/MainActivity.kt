package com.example.kotlovan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.kotlovan.ui.*
import com.example.kotlovan.ui.theme.KotlovanTheme
import com.example.kotlovan.vm.AppViewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument

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

    NavHost(nav, startDestination = "menu") {
        composable("menu") { MainMenuScreen(nav, vm) }
        composable(
            route = "builder?aux={aux}",
            arguments = listOf(navArgument("aux") {
                type = NavType.BoolType
                defaultValue = false
            })
        ) { back ->
            val aux = back.arguments?.getBoolean("aux") ?: false
            ArrayBuilderScreen(nav, vm, aux)
        }
        composable("card/{row}/{col}") { back ->
            val row = back.arguments?.getString("row")?.toInt() ?: 0
            val col = back.arguments?.getString("col")?.toInt() ?: 0
            CellCardScreen(nav, vm, row, col)
        }
        composable("viewer") { ViewerScreen(nav, vm) }
        composable("comment/{row}/{col}/{id}") { back ->
            val row = back.arguments?.getString("row")?.toInt() ?: 0
            val col = back.arguments?.getString("col")?.toInt() ?: 0
            val id = back.arguments?.getString("id") ?: ""
            CommentEditorScreen(nav, vm, row, col, id)
			
			
        }
		composable("linkpicker/{row}/{col}/{id}") { back ->
		    val row = back.arguments?.getString("row")?.toInt() ?: 0
		    val col = back.arguments?.getString("col")?.toInt() ?: 0
		    val id = back.arguments?.getString("id") ?: ""
		    LinkPickerScreen(nav, vm, row, col, id)
		}
    }
}