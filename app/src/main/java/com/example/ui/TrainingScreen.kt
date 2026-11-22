package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.game.AppUiState
import com.example.game.WorldSoccerViewModel
import com.example.game.engine.TrainingDrillType
import com.example.ui.theme.*

@Composable
fun TrainingScreen(
    uiState: AppUiState,
    viewModel: WorldSoccerViewModel,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StadiumSlate)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        BroadcastTopBar(
            title = "Training Pitch",
            onBack = onBack,
            trailingContent = {
                TeamBadge(uiState.homeTeam, 34.dp)
            }
        )

        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(TrainingDrillType.entries) { drill ->
                val best = uiState.saveData.trainingBestScores[drill.name] ?: 0
                val icon = when (drill) {
                    TrainingDrillType.PASSING -> Icons.Default.SyncAlt
                    TrainingDrillType.SHOOTING -> Icons.Default.SportsSoccer
                    TrainingDrillType.DRIBBLING -> Icons.Default.DirectionsRun
                    TrainingDrillType.FREE_KICK -> Icons.Default.Flag
                    TrainingDrillType.PENALTY -> Icons.Default.AdsClick
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = BroadcastCharcoal,
                    border = BorderStroke(1.dp, SlateBorder)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = FloodlightLime.copy(alpha = 0.16f),
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(icon, contentDescription = null, tint = FloodlightLime)
                                }
                            }
                            Column {
                                Text(drill.displayName, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                                Text(drill.description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                Text("Best Score: $best • Target: ${drill.targetScore}", style = MaterialTheme.typography.labelSmall, color = ChampionshipGold)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Button(
                            onClick = { viewModel.startTrainingDrill(drill) },
                            colors = ButtonDefaults.buttonColors(containerColor = PitchEmerald, contentColor = StadiumSlate),
                            modifier = Modifier.height(48.dp).testTag("start_drill_${drill.name.lowercase()}")
                        ) {
                            Text("PRACTICE", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}
