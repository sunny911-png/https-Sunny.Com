package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.game.AppUiState
import com.example.game.WorldSoccerViewModel
import com.example.game.model.*
import com.example.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    uiState: AppUiState,
    viewModel: WorldSoccerViewModel,
    onBack: () -> Unit
) {
    val s = uiState.saveData.settings
    val history = uiState.saveData.matchHistory
    val rewards = uiState.saveData.unlockedRewards

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StadiumSlate)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        BroadcastTopBar(title = "Settings & Optimization", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Graphics & Performance
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = BroadcastCharcoal,
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ANDROID PERFORMANCE & GRAPHICS", style = MaterialTheme.typography.labelLarge, color = FloodlightLime)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Frame Rate Mode", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(30, 60).forEach { fps ->
                                FilterChip(
                                    selected = s.targetFps == fps,
                                    onClick = { viewModel.updateSettings { it.copy(targetFps = fps) } },
                                    label = { Text("$fps FPS") }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Graphics Quality", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            GraphicsQuality.entries.forEach { q ->
                                FilterChip(
                                    selected = s.graphicsQuality == q,
                                    onClick = { viewModel.updateSettings { it.copy(graphicsQuality = q) } },
                                    label = { Text(q.displayName) }
                                )
                            }
                        }
                    }

                    Column {
                        Text("Resolution Scale: ${(s.resolutionScale * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Slider(
                            value = s.resolutionScale,
                            onValueChange = { v -> viewModel.updateSettings { it.copy(resolutionScale = v) } },
                            valueRange = 0.70f..1.0f
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Default Lighting", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StadiumLighting.entries.forEach { l ->
                                FilterChip(
                                    selected = s.defaultLighting == l,
                                    onClick = { viewModel.updateSettings { it.copy(defaultLighting = l) } },
                                    label = { Text(if (l == StadiumLighting.EVENING) "Evening" else "Day") }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Default Camera", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(CameraMode.BROADCAST, CameraMode.BALL_FOLLOW).forEach { cam ->
                                FilterChip(
                                    selected = s.defaultCamera == cam,
                                    onClick = { viewModel.updateSettings { it.copy(defaultCamera = cam) } },
                                    label = { Text(cam.displayName) }
                                )
                            }
                        }
                    }
                }
            }

            // Audio Volumes
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = BroadcastCharcoal,
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("AUDIO MIXER", style = MaterialTheme.typography.labelLarge, color = SkyBroadcastBlue)
                    VolumeSliderRow("Master Volume", s.masterVolume) { v -> viewModel.updateSettings { it.copy(masterVolume = v) } }
                    VolumeSliderRow("Stadium Crowd", s.crowdVolume) { v -> viewModel.updateSettings { it.copy(crowdVolume = v) } }
                    VolumeSliderRow("Match SFX & Whistle", s.sfxVolume) { v -> viewModel.updateSettings { it.copy(sfxVolume = v) } }
                    VolumeSliderRow("Menu Music", s.musicVolume) { v -> viewModel.updateSettings { it.copy(musicVolume = v) } }
                }
            }

            // Touch Control Position Customizer
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
                        Text("TOUCH CONTROL POSITIONS (DRAG TO ADJUST)", style = MaterialTheme.typography.labelLarge, color = ChampionshipGold)
                        TextButton(
                            onClick = { viewModel.updateSettings { it.copy(touchOffsets = TouchLayoutOffsets()) } },
                            modifier = Modifier.testTag("reset_touch_layout_button")
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, tint = TextSecondary)
                            Spacer(Modifier.width(4.dp))
                            Text("Reset", color = TextSecondary)
                        }
                    }

                    Text("Button Size Scale: ${(s.touchOffsets.buttonScale * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    Slider(
                        value = s.touchOffsets.buttonScale,
                        onValueChange = { v ->
                            viewModel.updateSettings { it.copy(touchOffsets = it.touchOffsets.copy(buttonScale = v)) }
                        },
                        valueRange = 0.80f..1.25f
                    )

                    // Interactive miniature drag pad
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ElevatedSlate)
                    ) {
                        // Left draggable joystick preview
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(18.dp)
                                .offset {
                                    IntOffset(
                                        s.touchOffsets.joystickOffsetX.roundToInt(),
                                        s.touchOffsets.joystickOffsetY.roundToInt()
                                    )
                                }
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(PitchEmerald.copy(alpha = 0.35f))
                                .pointerInput(Unit) {
                                    detectDragGestures { change, drag ->
                                        change.consume()
                                        viewModel.updateSettings { cur ->
                                            val off = cur.touchOffsets
                                            cur.copy(
                                                touchOffsets = off.copy(
                                                    joystickOffsetX = (off.joystickOffsetX + drag.x).coerceIn(-60f, 110f),
                                                    joystickOffsetY = (off.joystickOffsetY + drag.y).coerceIn(-90f, 30f)
                                                )
                                            )
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("STICK", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                        }

                        // Right draggable action cluster preview
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(18.dp)
                                .offset {
                                    IntOffset(
                                        s.touchOffsets.actionClusterOffsetX.roundToInt(),
                                        s.touchOffsets.actionClusterOffsetY.roundToInt()
                                    )
                                }
                                .size(70.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(ChampionshipGold.copy(alpha = 0.35f))
                                .pointerInput(Unit) {
                                    detectDragGestures { change, drag ->
                                        change.consume()
                                        viewModel.updateSettings { cur ->
                                            val off = cur.touchOffsets
                                            cur.copy(
                                                touchOffsets = off.copy(
                                                    actionClusterOffsetX = (off.actionClusterOffsetX + drag.x).coerceIn(-110f, 60f),
                                                    actionClusterOffsetY = (off.actionClusterOffsetY + drag.y).coerceIn(-90f, 30f)
                                                )
                                            )
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("BUTTONS", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                        }
                    }
                }
            }

            // Unlocked Rewards & Recent Match History
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = BroadcastCharcoal,
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("REWARDS & MATCH LOG", style = MaterialTheme.typography.labelLarge, color = PitchEmerald)
                    if (rewards.isEmpty()) {
                        Text("No trophies unlocked yet — win matches to earn milestones.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    } else {
                        Text("Unlocked: ${rewards.joinToString(" • ")}", style = MaterialTheme.typography.bodyMedium, color = ChampionshipGold)
                    }
                    history.take(5).forEach { h ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${h.modeName}: ${h.homeTeamName} ${h.homeGoals}-${h.awayGoals} ${h.awayTeamName}", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            Text(h.outcome, style = MaterialTheme.typography.labelLarge, color = if (h.outcome == "Win") FloodlightLime else TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VolumeSliderRow(
    label: String,
    value: Float,
    onChange: (Float) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text("${(value * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
        Slider(value = value, onValueChange = onChange, valueRange = 0f..1f)
    }
}
