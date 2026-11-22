package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.game.AppUiState
import com.example.game.WorldSoccerViewModel
import com.example.game.model.FormationType
import com.example.game.model.PlayerProfile
import com.example.game.model.TeamRepository
import com.example.ui.theme.*

@Composable
fun TeamSelectionScreen(
    uiState: AppUiState,
    inMatch: Boolean,
    viewModel: WorldSoccerViewModel,
    onBack: () -> Unit
) {
    val team = uiState.homeTeam
    var selectedSwapIdx by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StadiumSlate)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        BroadcastTopBar(
            title = if (inMatch) "Live Tactics & Substitutions" else "Team & Formation Tactics",
            onBack = onBack,
            trailingContent = {
                TeamBadge(team = team, size = 36.dp)
            }
        )

        // Team switcher (when not mid-match) + Formation pills
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!inMatch) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(TeamRepository.allTeams) { _, t ->
                        FilterChip(
                            selected = t.id == team.id,
                            onClick = {
                                selectedSwapIdx = null
                                viewModel.selectHomeTeam(t.id)
                            },
                            label = { Text("${t.shortCode} ${t.name}") }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("FORMATION:", style = MaterialTheme.typography.labelLarge, color = FloodlightLime)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(FormationType.entries) { _, f ->
                        FilterChip(
                            selected = team.defaultFormation == f,
                            onClick = { viewModel.setTeamFormation(team.id, f) },
                            label = { Text(f.displayName) }
                        )
                    }
                }
            }
        }

        // Split Pitch Formation View + Substitutes & Swap Inspector
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            val isWide = maxWidth > 580.dp
            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PitchFormationBoard(
                        team = team,
                        selectedIdx = selectedSwapIdx,
                        onTapSlot = { idx ->
                            val first = selectedSwapIdx
                            if (first == null) {
                                selectedSwapIdx = idx
                            } else if (first == idx) {
                                selectedSwapIdx = null
                            } else {
                                viewModel.swapTeamPlayers(team.id, first, idx)
                                selectedSwapIdx = null
                            }
                        },
                        modifier = Modifier.weight(0.56f).fillMaxHeight()
                    )
                    SubstitutesPanel(
                        team = team,
                        selectedIdx = selectedSwapIdx,
                        onTapPlayer = { idx ->
                            val first = selectedSwapIdx
                            if (first == null) {
                                selectedSwapIdx = idx
                            } else if (first == idx) {
                                selectedSwapIdx = null
                            } else {
                                viewModel.swapTeamPlayers(team.id, first, idx)
                                selectedSwapIdx = null
                            }
                        },
                        modifier = Modifier.weight(0.44f).fillMaxHeight()
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PitchFormationBoard(
                        team = team,
                        selectedIdx = selectedSwapIdx,
                        onTapSlot = { idx ->
                            val first = selectedSwapIdx
                            if (first == null) selectedSwapIdx = idx
                            else {
                                if (first != idx) viewModel.swapTeamPlayers(team.id, first, idx)
                                selectedSwapIdx = null
                            }
                        },
                        modifier = Modifier.weight(0.54f).fillMaxWidth()
                    )
                    SubstitutesPanel(
                        team = team,
                        selectedIdx = selectedSwapIdx,
                        onTapPlayer = { idx ->
                            val first = selectedSwapIdx
                            if (first == null) selectedSwapIdx = idx
                            else {
                                if (first != idx) viewModel.swapTeamPlayers(team.id, first, idx)
                                selectedSwapIdx = null
                            }
                        },
                        modifier = Modifier.weight(0.46f).fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun PitchFormationBoard(
    team: com.example.game.model.TeamProfile,
    selectedIdx: Int?,
    onTapSlot: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF0E3A24),
        border = BorderStroke(1.5.dp, PitchEmerald.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                drawRect(color = Color.White.copy(alpha = 0.35f), style = Stroke(width = 2.dp.toPx()))
                drawLine(
                    color = Color.White.copy(alpha = 0.3f),
                    start = Offset(0f, h * 0.5f),
                    end = Offset(w, h * 0.5f),
                    strokeWidth = 2.dp.toPx()
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.3f),
                    radius = minOf(w, h) * 0.14f,
                    center = Offset(w * 0.5f, h * 0.5f),
                    style = Stroke(width = 2.dp.toPx())
                )
                // Penalty box bottom
                drawRect(
                    color = Color.White.copy(alpha = 0.3f),
                    topLeft = Offset(w * 0.25f, h * 0.80f),
                    size = Size(w * 0.50f, h * 0.20f),
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            val slots = team.defaultFormation.slots
            slots.forEachIndexed { idx, slot ->
                val player = team.roster.getOrNull(idx) ?: return@forEachIndexed
                // Map slot xNorm (-0.8..0.8) -> 0.1..0.9, zNorm (-0.95..0.20) -> bottom to top
                val xFrac = ((slot.xNorm + 1f) * 0.5f).coerceIn(0.10f, 0.90f)
                val yFrac = (0.90f - ((slot.zNorm + 1f) * 0.68f)).coerceIn(0.10f, 0.90f)
                val isSelected = selectedIdx == idx

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .offset(
                            x = maxWidth * xFrac - 28.dp,
                            y = maxHeight * yFrac - 24.dp
                        )
                        .width(56.dp)
                        .clickable { onTapSlot(idx) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) ChampionshipGold else Color(team.homeKit.jerseyPrimary)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${player.number}",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.Black
                        )
                    }
                    Text(
                        text = "${slot.label} ${player.name.substringAfterLast(' ')}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SubstitutesPanel(
    team: com.example.game.model.TeamProfile,
    selectedIdx: Int?,
    onTapPlayer: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = BroadcastCharcoal,
        border = BorderStroke(1.dp, SlateBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedIdx == null) "TAP PLAYER TO SWAP / SUB" else "TAP SECOND PLAYER TO SWAP",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selectedIdx == null) FloodlightLime else ChampionshipGold
                )
                Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = TextSecondary)
            }
            Spacer(Modifier.height(8.dp))

            val selectedPlayer: PlayerProfile? = selectedIdx?.let { team.roster.getOrNull(it) }
            if (selectedPlayer != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ElevatedSlate,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${selectedPlayer.name} (${selectedPlayer.role.code})", style = MaterialTheme.typography.titleMedium, color = ChampionshipGold)
                        Text(
                            "PAC ${selectedPlayer.attributes.pace} • SHO ${selectedPlayer.attributes.shooting} • PAS ${selectedPlayer.attributes.passing}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(team.roster) { idx, player ->
                    val isSub = idx >= 11
                    val isSelected = selectedIdx == idx
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = when {
                            isSelected -> ChampionshipGold.copy(alpha = 0.22f)
                            isSub -> ElevatedSlate
                            else -> StadiumSlate.copy(alpha = 0.7f)
                        },
                        border = BorderStroke(1.dp, if (isSelected) ChampionshipGold else SlateBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTapPlayer(idx) }
                            .testTag("roster_player_$idx")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = if (isSub) "SUB" else team.defaultFormation.slots[idx].label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSub) SkyBroadcastBlue else PitchEmerald,
                                    modifier = Modifier.width(34.dp)
                                )
                                Text(
                                    text = "#${player.number} ${player.name}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary
                                )
                            }
                            Text(
                                text = "OVR ${player.attributes.overall}",
                                style = MaterialTheme.typography.labelLarge,
                                color = ChampionshipGold
                            )
                        }
                    }
                }
            }
        }
    }
}
