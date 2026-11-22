package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.game.AppUiState
import com.example.game.WorldSoccerViewModel
import com.example.game.model.TeamRepository
import com.example.game.model.TournamentStage
import com.example.game.model.TournamentStanding
import com.example.ui.theme.*

@Composable
fun TournamentScreen(
    uiState: AppUiState,
    viewModel: WorldSoccerViewModel,
    onBack: () -> Unit
) {
    val tState = uiState.saveData.tournament
    var pickedTeamId by remember { mutableStateOf(uiState.homeTeam.id) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StadiumSlate)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        BroadcastTopBar(
            title = "International World Cup",
            onBack = onBack,
            trailingContent = {
                if (tState.active) {
                    OutlinedButton(
                        onClick = { viewModel.startTournament(pickedTeamId) },
                        border = BorderStroke(1.dp, SlateBorder),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = TextSecondary)
                        Spacer(Modifier.width(4.dp))
                        Text("New Cup", color = TextSecondary)
                    }
                }
            }
        )

        if (!tState.active) {
            // Setup New Tournament
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = BroadcastCharcoal,
                    border = BorderStroke(1.dp, ChampionshipGold.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.img_trophy_cup),
                            contentDescription = "Championship Trophy",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(84.dp).clip(RoundedCornerShape(12.dp))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("2027 WORLD CUP FORMAT", style = MaterialTheme.typography.titleLarge, color = ChampionshipGold)
                            Text(
                                "8 nations split into 2 groups of 4. Top 2 from each group advance to Semi-Finals and the World Final.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Text("SELECT YOUR NATION", style = MaterialTheme.typography.labelLarge, color = FloodlightLime)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(TeamRepository.allTeams) { team ->
                        val sel = team.id == pickedTeamId
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (sel) PitchEmerald.copy(alpha = 0.2f) else ElevatedSlate,
                            border = BorderStroke(if (sel) 2.dp else 1.dp, if (sel) PitchEmerald else SlateBorder),
                            modifier = Modifier.width(106.dp).clickable { pickedTeamId = team.id }
                        ) {
                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                TeamBadge(team = team, size = 44.dp)
                                Spacer(Modifier.height(6.dp))
                                Text(team.name, style = MaterialTheme.typography.labelLarge, color = TextPrimary, maxLines = 1)
                                Text("OVR ${team.overallRating}", style = MaterialTheme.typography.labelSmall, color = ChampionshipGold)
                            }
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                Button(
                    onClick = { viewModel.startTournament(pickedTeamId) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ChampionshipGold, contentColor = StadiumSlate),
                    modifier = Modifier.fillMaxWidth().height(54.dp).testTag("start_tournament_button")
                ) {
                    Icon(Icons.Default.EmojiEvents, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("ENTER TOURNAMENT", style = MaterialTheme.typography.titleLarge)
                }
            }
        } else {
            val nextFixture = viewModel.tournamentManager.nextUserFixture(tState)

            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Trophy Celebration Card if finished
                if (tState.stage == TournamentStage.CHAMPION_CROWNED && tState.championTeamId != null) {
                    val champ = TeamRepository.getById(tState.championTeamId)
                    val userWon = champ.id == tState.userTeamId
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = BroadcastCharcoal,
                            border = BorderStroke(2.dp, ChampionshipGold)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.img_trophy_cup),
                                    contentDescription = "World Cup Trophy",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp))
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (userWon) "CHAMPIONS OF THE WORLD!" else "TOURNAMENT COMPLETE",
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = ChampionshipGold
                                    )
                                    Text(
                                        text = "${champ.name} lifted the World Soccer 2027 Cup!",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = TextPrimary
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Button(
                                        onClick = { viewModel.startTournament(tState.userTeamId) },
                                        colors = ButtonDefaults.buttonColors(containerColor = PitchEmerald, contentColor = StadiumSlate)
                                    ) {
                                        Text("Start New Cup")
                                    }
                                }
                            }
                        }
                    }
                } else if (nextFixture != null) {
                    // Next User Match Card
                    val home = TeamRepository.getById(nextFixture.homeTeamId)
                    val away = TeamRepository.getById(nextFixture.awayTeamId)
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = BroadcastCharcoal,
                            border = BorderStroke(1.5.dp, PitchEmerald)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(nextFixture.stageName.uppercase(), style = MaterialTheme.typography.labelLarge, color = FloodlightLime)
                                    Spacer(Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        TeamBadge(home, 36.dp)
                                        Text("${home.shortCode} vs ${away.shortCode}", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                                        TeamBadge(away, 36.dp)
                                    }
                                }
                                Button(
                                    onClick = viewModel::playNextTournamentMatch,
                                    colors = ButtonDefaults.buttonColors(containerColor = PitchEmerald, contentColor = StadiumSlate),
                                    modifier = Modifier.height(48.dp).testTag("play_tournament_match_button")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("PLAY", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }

                // Group Tables
                item {
                    GroupStandingCard("GROUP A", tState.standings.filter { it.groupIndex == 0 }, tState.userTeamId)
                }
                item {
                    GroupStandingCard("GROUP B", tState.standings.filter { it.groupIndex == 1 }, tState.userTeamId)
                }

                // Knockout / Recent Fixtures
                val knockoutFixtures = tState.fixtures.filter { !it.stageName.startsWith("Group") }
                if (knockoutFixtures.isNotEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = BroadcastCharcoal,
                            border = BorderStroke(1.dp, SlateBorder)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("KNOCKOUT BRACKET", style = MaterialTheme.typography.labelLarge, color = ChampionshipGold)
                                knockoutFixtures.forEach { fx ->
                                    val h = TeamRepository.getById(fx.homeTeamId)
                                    val a = TeamRepository.getById(fx.awayTeamId)
                                    Row(
                                        modifier = Modifier.fillMaxWidth().background(ElevatedSlate, RoundedCornerShape(8.dp)).padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(fx.stageName, style = MaterialTheme.typography.labelSmall, color = SkyBroadcastBlue)
                                        Text(
                                            if (fx.isCompleted) "${h.shortCode} ${fx.homeGoals} - ${fx.awayGoals} ${a.shortCode}" else "${h.shortCode} vs ${a.shortCode}",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = TextPrimary
                                        )
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

@Composable
private fun GroupStandingCard(
    title: String,
    rows: List<TournamentStanding>,
    userTeamId: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = BroadcastCharcoal,
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = PitchEmerald)
                Text("P   W   D   L   GD   PTS", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
            rows.forEachIndexed { idx, row ->
                val team = TeamRepository.getById(row.teamId)
                val isUser = row.teamId == userTeamId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isUser) PitchEmerald.copy(alpha = 0.14f) else ElevatedSlate.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${idx + 1}", style = MaterialTheme.typography.labelLarge, color = if (idx < 2) FloodlightLime else TextMuted)
                        TeamBadge(team, 24.dp)
                        Text(team.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    }
                    Text(
                        "${row.played}   ${row.won}   ${row.drawn}   ${row.lost}   ${row.goalDifference}   ${row.points}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isUser) ChampionshipGold else TextPrimary
                    )
                }
            }
        }
    }
}
