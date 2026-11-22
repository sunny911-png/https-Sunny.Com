package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.game.AppUiState
import com.example.game.ScreenRoute
import com.example.ui.theme.*

@Composable
fun MainMenuScreen(
    uiState: AppUiState,
    onNavigate: (ScreenRoute) -> Unit,
    onExitApp: () -> Unit
) {
    var showExitDialog by remember { mutableStateOf(false) }

    val pulseTransition = rememberInfiniteTransition(label = "floodlight")
    val glowAlpha by pulseTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(StadiumSlate)
    ) {
        val isWide = maxWidth > 620.dp

        // Full-bleed stadium backdrop
        Image(
            painter = painterResource(id = R.drawable.img_stadium_hero),
            contentDescription = "Stadium backdrop",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.55f)
        )

        // Atmospheric floodlight & dark broadcast scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            StadiumSlate.copy(alpha = 0.80f),
                            StadiumSlate.copy(alpha = 0.45f),
                            StadiumSlate.copy(alpha = 0.96f)
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            PitchEmerald.copy(alpha = glowAlpha * 0.24f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Broadcast Header Lockup
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = PitchEmerald,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "OFFLINE 3D",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = StadiumSlate,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.subtitle_offline_edition),
                            style = MaterialTheme.typography.labelSmall,
                            color = FloodlightLime
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "WORLD SOCCER 2027",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = if (isWide) 40.sp else 32.sp
                        ),
                        color = TextPrimary
                    )
                }

                // Active Squad & Coins Status Pill
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = BroadcastCharcoal.copy(alpha = 0.88f),
                    border = BorderStroke(1.dp, SlateBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TeamBadge(team = uiState.homeTeam, size = 38.dp)
                        Column {
                            Text(
                                text = uiState.homeTeam.name.uppercase(),
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary
                            )
                            Text(
                                text = "OVR ${uiState.homeTeam.overallRating} • ${uiState.saveData.career.coins} Coins",
                                style = MaterialTheme.typography.labelSmall,
                                color = ChampionshipGold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Primary Hero CTA + Mode Navigation Grid
            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Left Hero Kickoff Card
                    HeroKickoffCard(
                        uiState = uiState,
                        onPlayMatch = { onNavigate(ScreenRoute.QuickMatchSetup) },
                        modifier = Modifier.weight(0.44f)
                    )

                    // Right Mode Grid
                    Column(
                        modifier = Modifier.weight(0.56f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StadiumActionCard(
                                title = stringResource(R.string.menu_tournament),
                                subtitle = if (uiState.saveData.tournament.active) "Continue Cup" else "8-Nation World Cup",
                                icon = Icons.Default.EmojiEvents,
                                accentColor = ChampionshipGold,
                                testTag = "tournament_button",
                                onClick = { onNavigate(ScreenRoute.Tournament) },
                                modifier = Modifier.weight(1f)
                            )
                            StadiumActionCard(
                                title = stringResource(R.string.menu_career),
                                subtitle = if (uiState.saveData.career.active) "Season ${uiState.saveData.career.seasonNumber} • MD ${uiState.saveData.career.matchDay}" else "Build & Upgrade",
                                icon = Icons.Default.TrendingUp,
                                accentColor = SkyBroadcastBlue,
                                testTag = "career_button",
                                onClick = { onNavigate(ScreenRoute.Career) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StadiumActionCard(
                                title = stringResource(R.string.menu_training),
                                subtitle = "5 Skill Drills",
                                icon = Icons.Default.FitnessCenter,
                                accentColor = FloodlightLime,
                                testTag = "training_button",
                                onClick = { onNavigate(ScreenRoute.Training) },
                                modifier = Modifier.weight(1f)
                            )
                            StadiumActionCard(
                                title = stringResource(R.string.menu_team_selection),
                                subtitle = "${uiState.homeTeam.defaultFormation.displayName} • Squads",
                                icon = Icons.Default.Groups,
                                accentColor = PitchEmerald,
                                testTag = "team_tactics_button",
                                onClick = { onNavigate(ScreenRoute.TeamTactics(false)) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StadiumActionCard(
                                title = stringResource(R.string.menu_settings),
                                subtitle = "${uiState.saveData.settings.targetFps} FPS • Audio & Touch",
                                icon = Icons.Default.Settings,
                                accentColor = TextSecondary,
                                testTag = "settings_button",
                                onClick = { onNavigate(ScreenRoute.Settings) },
                                modifier = Modifier.weight(1f)
                            )
                            StadiumActionCard(
                                title = stringResource(R.string.menu_exit),
                                subtitle = "Close Game",
                                icon = Icons.AutoMirrored.Filled.ExitToApp,
                                accentColor = BroadcastCrimson,
                                testTag = "exit_button",
                                onClick = { showExitDialog = true },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            } else {
                // Compact Portrait layout
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeroKickoffCard(
                        uiState = uiState,
                        onPlayMatch = { onNavigate(ScreenRoute.QuickMatchSetup) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    StadiumActionCard(
                        title = stringResource(R.string.menu_tournament),
                        subtitle = if (uiState.saveData.tournament.active) "Continue International Cup" else "Group Stage to Final",
                        icon = Icons.Default.EmojiEvents,
                        accentColor = ChampionshipGold,
                        testTag = "tournament_button",
                        onClick = { onNavigate(ScreenRoute.Tournament) }
                    )
                    StadiumActionCard(
                        title = stringResource(R.string.menu_career),
                        subtitle = if (uiState.saveData.career.active) "Season ${uiState.saveData.career.seasonNumber} • ${uiState.saveData.career.coins} Coins" else "Lead Your Nation & Upgrade",
                        icon = Icons.Default.TrendingUp,
                        accentColor = SkyBroadcastBlue,
                        testTag = "career_button",
                        onClick = { onNavigate(ScreenRoute.Career) }
                    )
                    StadiumActionCard(
                        title = stringResource(R.string.menu_training),
                        subtitle = "Passing, Shooting, Dribbling, Free-Kick & Penalty",
                        icon = Icons.Default.FitnessCenter,
                        accentColor = FloodlightLime,
                        testTag = "training_button",
                        onClick = { onNavigate(ScreenRoute.Training) }
                    )
                    StadiumActionCard(
                        title = stringResource(R.string.menu_team_selection),
                        subtitle = "8 Nations • 5 Formations • Substitutes",
                        icon = Icons.Default.Groups,
                        accentColor = PitchEmerald,
                        testTag = "team_tactics_button",
                        onClick = { onNavigate(ScreenRoute.TeamTactics(false)) }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StadiumActionCard(
                            title = stringResource(R.string.menu_settings),
                            subtitle = "Graphics, FPS & Touch",
                            icon = Icons.Default.Settings,
                            accentColor = TextSecondary,
                            testTag = "settings_button",
                            onClick = { onNavigate(ScreenRoute.Settings) },
                            modifier = Modifier.weight(1f)
                        )
                        StadiumActionCard(
                            title = stringResource(R.string.menu_exit),
                            subtitle = "Quit",
                            icon = Icons.AutoMirrored.Filled.ExitToApp,
                            accentColor = BroadcastCrimson,
                            testTag = "exit_button",
                            onClick = { showExitDialog = true },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            containerColor = BroadcastCharcoal,
            title = { Text("Exit World Soccer 2027?", style = MaterialTheme.typography.titleLarge) },
            text = { Text("All settings, career, and tournament progress are saved locally.", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = onExitApp,
                    colors = ButtonDefaults.buttonColors(containerColor = BroadcastCrimson),
                    modifier = Modifier.testTag("confirm_exit_button")
                ) {
                    Text("Exit Game", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Stay", color = TextPrimary)
                }
            }
        )
    }
}

@Composable
private fun HeroKickoffCard(
    uiState: AppUiState,
    onPlayMatch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = BroadcastCharcoal.copy(alpha = 0.90f),
        border = BorderStroke(1.5.dp, PitchEmerald.copy(alpha = 0.7f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NEXT EXHIBITION",
                    style = MaterialTheme.typography.labelLarge,
                    color = FloodlightLime
                )
                Text(
                    text = "${uiState.quickMatchDurationMin} MIN • ${uiState.quickMatchLighting.displayName.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TeamBadge(team = uiState.homeTeam, size = 54.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(uiState.homeTeam.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text("OVR ${uiState.homeTeam.overallRating}", style = MaterialTheme.typography.labelSmall, color = PitchEmerald)
                }
                Text(
                    text = "VS",
                    style = MaterialTheme.typography.headlineLarge,
                    color = ChampionshipGold
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TeamBadge(team = uiState.awayTeam, size = 54.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(uiState.awayTeam.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text("OVR ${uiState.awayTeam.overallRating}", style = MaterialTheme.typography.labelSmall, color = SkyBroadcastBlue)
                }
            }

            Button(
                onClick = onPlayMatch,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PitchEmerald,
                    contentColor = StadiumSlate
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("play_match_button")
            ) {
                Icon(Icons.Default.SportsSoccer, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.menu_play_match).uppercase(),
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
}
