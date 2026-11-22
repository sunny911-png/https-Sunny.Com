package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.game.ScreenRoute
import com.example.game.WorldSoccerViewModel
import com.example.ui.*
import com.example.ui.theme.WorldSoccerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WorldSoccerTheme {
                WorldSoccerApp(onExitApp = { finishAffinity() })
            }
        }
    }
}

@Composable
fun WorldSoccerApp(
    onExitApp: () -> Unit,
    viewModel: WorldSoccerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.currentRoute !is ScreenRoute.MainMenu && uiState.currentRoute !is ScreenRoute.Match) {
        BackHandler {
            viewModel.navigateBack()
        }
    }

    Crossfade(targetState = uiState.currentRoute, label = "screenRoute") { route ->
        when (route) {
            ScreenRoute.MainMenu -> MainMenuScreen(
                uiState = uiState,
                onNavigate = viewModel::navigate,
                onExitApp = onExitApp
            )
            ScreenRoute.QuickMatchSetup -> QuickMatchSetupScreen(
                uiState = uiState,
                viewModel = viewModel,
                onOpenTactics = { viewModel.navigate(ScreenRoute.TeamTactics(inMatch = false)) },
                onBack = viewModel::navigateBack
            )
            is ScreenRoute.TeamTactics -> TeamSelectionScreen(
                uiState = uiState,
                inMatch = route.inMatch,
                viewModel = viewModel,
                onBack = viewModel::navigateBack
            )
            ScreenRoute.Tournament -> TournamentScreen(
                uiState = uiState,
                viewModel = viewModel,
                onBack = viewModel::navigateBack
            )
            ScreenRoute.Career -> CareerScreen(
                uiState = uiState,
                viewModel = viewModel,
                onBack = viewModel::navigateBack
            )
            ScreenRoute.Training -> TrainingScreen(
                uiState = uiState,
                viewModel = viewModel,
                onBack = viewModel::navigateBack
            )
            ScreenRoute.Settings -> SettingsScreen(
                uiState = uiState,
                viewModel = viewModel,
                onBack = viewModel::navigateBack
            )
            ScreenRoute.Match -> MatchScreen(
                viewModel = viewModel,
                onOpenInMatchTactics = {
                    viewModel.activeMatchEngine?.setPaused(true)
                    viewModel.navigate(ScreenRoute.TeamTactics(inMatch = true))
                },
                onExitMatch = viewModel::exitActiveMatch
            )
        }
    }
}
