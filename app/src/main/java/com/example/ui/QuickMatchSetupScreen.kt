package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.game.AppUiState
import com.example.game.WorldSoccerViewModel
import com.example.game.model.DifficultyLevel
import com.example.game.model.StadiumLighting
import com.example.game.model.TeamProfile
import com.example.game.model.TeamRepository
import com.example.ui.theme.*

@Composable
fun QuickMatchSetupScreen(
    uiState: AppUiState,
    viewModel: WorldSoccerViewModel,
    onOpenTactics: () -> Unit,
    onBack: () -> Unit
) {
    val (resolvedHomeKit, resolvedAwayKit) = viewModel.teamManager.resolveKits(
        uiState.homeTeam,
        uiState.awayTeam,
        uiState.preferHomeAwayKit,
        uiState.preferAwayAwayKit
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StadiumSlate)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        BroadcastTopBar(
            title = "Quick Match Setup",
            onBack = onBack,
            trailingContent = {
                OutlinedButton(
                    onClick = onOpenTactics,
                    border = BorderStroke(1.dp, PitchEmerald),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("open_tactics_button")
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = PitchEmerald)
                    Spacer(Modifier.width(6.dp))
                    Text("Tactics", color = PitchEmerald)
                }
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Home Team Picker
            TeamPickerSection(
                label = "HOME TEAM (YOU)",
                selectedTeam = uiState.homeTeam,
                kitLabel = if (uiState.preferHomeAwayKit) "Away Kit" else "Home Kit",
                kitColors = resolvedHomeKit,
                onToggleKit = viewModel::toggleHomeKit,
                onSelectTeam = viewModel::selectHomeTeam
            )

            // Away Team Picker
            TeamPickerSection(
                label = "AWAY TEAM (CPU)",
                selectedTeam = uiState.awayTeam,
                kitLabel = if (uiState.preferAwayAwayKit) "Away Kit" else "Home Kit",
                kitColors = resolvedAwayKit,
                onToggleKit = viewModel::toggleAwayKit,
                onSelectTeam = viewModel::selectAwayTeam
            )

            // Match Rules & Stadium Lighting
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = BroadcastCharcoal,
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("MATCH & STADIUM OPTIONS", style = MaterialTheme.typography.labelLarge, color = FloodlightLime)

                    // Duration
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Match Duration", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(2, 4, 6, 10).forEach { min ->
                                FilterChip(
                                    selected = uiState.quickMatchDurationMin == min,
                                    onClick = { viewModel.setQuickMatchDuration(min) },
                                    label = { Text("${min}m") }
                                )
                            }
                        }
                    }

                    // Difficulty
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("AI Difficulty", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DifficultyLevel.entries.forEach { diff ->
                                FilterChip(
                                    selected = uiState.quickMatchDifficulty == diff,
                                    onClick = { viewModel.setQuickMatchDifficulty(diff) },
                                    label = { Text(diff.displayName) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Stadium Lighting
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Stadium Lighting", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = uiState.quickMatchLighting == StadiumLighting.EVENING,
                                onClick = { viewModel.setQuickMatchLighting(StadiumLighting.EVENING) },
                                leadingIcon = { Icon(Icons.Default.NightlightRound, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("Evening") }
                            )
                            FilterChip(
                                selected = uiState.quickMatchLighting == StadiumLighting.DAY,
                                onClick = { viewModel.setQuickMatchLighting(StadiumLighting.DAY) },
                                leadingIcon = { Icon(Icons.Default.WbSunny, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("Day") }
                            )
                        }
                    }
                }
            }
        }

        // Kickoff CTA Footer
        Surface(
            color = BroadcastCharcoal,
            border = BorderStroke(1.dp, SlateBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${uiState.homeTeam.shortCode} vs ${uiState.awayTeam.shortCode} • ${uiState.homeTeam.defaultFormation.displayName}",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary
                )
                Button(
                    onClick = viewModel::startQuickMatch,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PitchEmerald, contentColor = StadiumSlate),
                    modifier = Modifier.height(50.dp).testTag("kickoff_match_button")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("KICK OFF", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@Composable
private fun TeamPickerSection(
    label: String,
    selectedTeam: TeamProfile,
    kitLabel: String,
    kitColors: com.example.game.model.KitColors,
    onToggleKit: () -> Unit,
    onSelectTeam: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = BroadcastCharcoal,
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = PitchEmerald)
                    Text(
                        "${selectedTeam.name} • ATT ${selectedTeam.attackRating} / MID ${selectedTeam.midfieldRating} / DEF ${selectedTeam.defenseRating}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                KitSwatchPill(kit = kitColors, label = kitLabel, onClick = onToggleKit)
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(TeamRepository.allTeams, key = { it.id }) { team ->
                    val isSelected = team.id == selectedTeam.id
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) PitchEmerald.copy(alpha = 0.18f) else ElevatedSlate,
                        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) PitchEmerald else SlateBorder),
                        modifier = Modifier
                            .width(104.dp)
                            .clickable { onSelectTeam(team.id) }
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            TeamBadge(team = team, size = 40.dp)
                            Spacer(Modifier.height(6.dp))
                            Text(team.name, style = MaterialTheme.typography.labelLarge, color = TextPrimary, maxLines = 1)
                            Text("OVR ${team.overallRating}", style = MaterialTheme.typography.labelSmall, color = ChampionshipGold)
                        }
                    }
                }
            }
        }
    }
}
