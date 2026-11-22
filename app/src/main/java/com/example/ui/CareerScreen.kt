package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.game.AppUiState
import com.example.game.WorldSoccerViewModel
import com.example.game.model.PlayerProfile
import com.example.game.model.StatType
import com.example.game.model.TeamRepository
import com.example.ui.theme.*

@Composable
fun CareerScreen(
    uiState: AppUiState,
    viewModel: WorldSoccerViewModel,
    onBack: () -> Unit
) {
    val career = uiState.saveData.career
    var pickedTeamId by remember { mutableStateOf(career.teamId) }
    var selectedPlayer by remember { mutableStateOf<PlayerProfile?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StadiumSlate)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        BroadcastTopBar(
            title = "Career Mode",
            onBack = onBack,
            trailingContent = {
                Surface(shape = RoundedCornerShape(8.dp), color = ElevatedSlate) {
                    Text(
                        text = "${career.coins} COINS",
                        style = MaterialTheme.typography.labelLarge,
                        color = ChampionshipGold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                if (career.active) {
                    IconButton(onClick = { viewModel.startCareer(career.teamId) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset Career", tint = TextSecondary)
                    }
                }
            }
        )

        uiState.statusMessage?.let { msg ->
            Surface(
                color = PitchEmerald.copy(alpha = 0.2f),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(msg, style = MaterialTheme.typography.labelLarge, color = FloodlightLime, modifier = Modifier.padding(10.dp))
            }
        }

        if (!career.active) {
            Column(
                modifier = Modifier.weight(1f).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("CHOOSE YOUR NATIONAL TEAM TO MANAGE", style = MaterialTheme.typography.labelLarge, color = FloodlightLime)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(TeamRepository.allTeams) { team ->
                        val sel = team.id == pickedTeamId
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (sel) SkyBroadcastBlue.copy(alpha = 0.2f) else ElevatedSlate,
                            border = BorderStroke(if (sel) 2.dp else 1.dp, if (sel) SkyBroadcastBlue else SlateBorder),
                            modifier = Modifier.width(108.dp).clickable { pickedTeamId = team.id }
                        ) {
                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                TeamBadge(team, 44.dp)
                                Spacer(Modifier.height(6.dp))
                                Text(team.name, style = MaterialTheme.typography.labelLarge, color = TextPrimary, maxLines = 1)
                                Text("OVR ${team.overallRating}", style = MaterialTheme.typography.labelSmall, color = ChampionshipGold)
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { viewModel.startCareer(pickedTeamId) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PitchEmerald, contentColor = StadiumSlate),
                    modifier = Modifier.fillMaxWidth().height(54.dp).testTag("start_career_button")
                ) {
                    Text("START CAREER SEASON", style = MaterialTheme.typography.titleLarge)
                }
            }
        } else {
            val userTeam = viewModel.teamManager.buildEffectiveTeam(
                career.teamId,
                uiState.saveData.teamFormations[career.teamId],
                career.upgradesByPlayerId
            )
            val nextOppId = viewModel.careerManager.nextOpponentId(career) ?: "arg"
            val oppTeam = TeamRepository.getById(nextOppId)

            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Season Summary & Next Fixture
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = BroadcastCharcoal,
                        border = BorderStroke(1.5.dp, SkyBroadcastBlue.copy(alpha = 0.7f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("SEASON ${career.seasonNumber} • MATCHDAY ${career.matchDay}/${career.totalMatchDays}", style = MaterialTheme.typography.labelLarge, color = SkyBroadcastBlue)
                                Text("W ${career.wins}  D ${career.draws}  L ${career.losses}  • Trophies: ${career.trophiesWon}", style = MaterialTheme.typography.labelSmall, color = ChampionshipGold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    TeamBadge(userTeam, 40.dp)
                                    Text("${userTeam.shortCode} vs ${oppTeam.shortCode}", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                                    TeamBadge(oppTeam, 40.dp)
                                }
                                Button(
                                    onClick = viewModel::playNextCareerMatch,
                                    colors = ButtonDefaults.buttonColors(containerColor = PitchEmerald, contentColor = StadiumSlate),
                                    modifier = Modifier.height(48.dp).testTag("play_career_match_button")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("PLAY MATCH")
                                }
                            }
                        }
                    }
                }

                item {
                    Text("PLAYER DEVELOPMENT LAB (80 COINS PER +2 UPGRADE)", style = MaterialTheme.typography.labelLarge, color = FloodlightLime)
                }

                items(userTeam.roster, key = { it.id }) { player ->
                    val isExpanded = selectedPlayer?.id == player.id
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isExpanded) ElevatedSlate else BroadcastCharcoal,
                        border = BorderStroke(1.dp, if (isExpanded) PitchEmerald else SlateBorder),
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedPlayer = if (isExpanded) null else player
                        }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("#${player.number} ${player.name} (${player.role.code})", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Text("OVR ${player.attributes.overall}", style = MaterialTheme.typography.labelLarge, color = ChampionshipGold)
                            }
                            if (isExpanded) {
                                Spacer(Modifier.height(10.dp))
                                val stats = listOf(
                                    StatType.PACE to player.attributes.pace,
                                    StatType.SHOOTING to player.attributes.shooting,
                                    StatType.PASSING to player.attributes.passing,
                                    StatType.DRIBBLING to player.attributes.dribbling,
                                    StatType.DEFENDING to player.attributes.defending,
                                    StatType.PHYSICAL to player.attributes.physical
                                )
                                stats.chunked(2).forEach { pair ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        pair.forEach { (statType, value) ->
                                            OutlinedButton(
                                                onClick = { viewModel.upgradeCareerPlayer(player.id, statType) },
                                                modifier = Modifier.weight(1f).height(44.dp),
                                                border = BorderStroke(1.dp, PitchEmerald.copy(alpha = 0.6f))
                                            ) {
                                                Text("${statType.shortCode} $value", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                                                Spacer(Modifier.width(4.dp))
                                                Icon(Icons.Default.AddCircle, contentDescription = "Upgrade", tint = FloodlightLime, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
