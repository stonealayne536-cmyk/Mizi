package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.navigation.Screen
import com.example.ui.screens.*
import com.example.ui.theme.InkWeaveTheme
import com.example.ui.viewmodel.NovelViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: NovelViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDarkMode by viewModel.preferences.isDarkModeFlow.collectAsStateWithLifecycle()
            val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
            val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
            val snackbarHostState = remember { SnackbarHostState() }

            LaunchedEffect(snackbarMessage) {
                snackbarMessage?.let { msg ->
                    snackbarHostState.showSnackbar(msg)
                    viewModel.clearSnackbar()
                }
            }

            InkWeaveTheme(darkTheme = isDarkMode) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { innerPadding ->
                    AppNavigation(
                        currentScreen = currentScreen,
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun AppNavigation(
    currentScreen: Screen,
    viewModel: NovelViewModel,
    modifier: Modifier = Modifier
) {
    when (currentScreen) {
        is Screen.Bookshelf -> {
            BookshelfScreen(viewModel = viewModel, modifier = modifier)
        }
        is Screen.NovelDetail -> {
            NovelDetailScreen(novelId = currentScreen.novelId, viewModel = viewModel, modifier = modifier)
        }
        is Screen.Reader -> {
            ReaderScreen(
                novelId = currentScreen.novelId,
                chapterId = currentScreen.chapterId,
                viewModel = viewModel,
                modifier = modifier
            )
        }
        is Screen.Glossary -> {
            GlossaryScreen(novelId = currentScreen.novelId, viewModel = viewModel, modifier = modifier)
        }
        is Screen.Quotes -> {
            QuotesScreen(viewModel = viewModel, modifier = modifier)
        }
        is Screen.Settings -> {
            SettingsScreen(viewModel = viewModel, modifier = modifier)
        }
    }
}
