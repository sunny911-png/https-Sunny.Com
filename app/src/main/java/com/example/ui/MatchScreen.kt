package com.example.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.opengl.GLSurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.game.ScreenRoute
import com.example.game.WorldSoccerViewModel
import com.example.game.engine.*
import com.example.game.model.StadiumLighting
import com.example.game.render.StadiumGLRenderer
import com.example.game.systems.MobileInputManager
import com.example.ui.theme.*
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun MatchScreen(
    viewModel: WorldSoccerViewModel,
    onOpenInMatchTactics: () -> Unit,
    onExitMatch: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val engine = viewModel.activeMatchEngine
    val config = uiState.activeMatchConfig

    // Lock landscape during active 3D gameplay
    DisposableEffect(Unit) {
        val prevOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = prevOrientation
        }
    }

    if (engine == null || config == null) {
        Box(modifier = Modifier.fillMaxSize().background(StadiumSlate), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PitchEmerald)
        }
        return
    }

    val hud by engine.hudState.collectAsStateWithLifecycle()
    val settings = uiState.saveData.settings

    LaunchedEffect(hud.phase) {
        if (hud.phase == MatchPhase.FULL_TIME || hud.phase == MatchPhase.TRAINING_COMPLETE) {
            viewModel.finalizeMatchResultIfNeeded()
        }
    }

    BackHandler {
        if (!hud.isPaused && hud.phase != MatchPhase.FULL_TIME && hud.phase != MatchPhase.TRAINING_COMPLETE) {
            engine.setPaused(true)
        } else {
            onExitMatch()
        }
    }

    val renderer = remember(engine) {
        StadiumGLRenderer(
            engine = engine,
            homeKit = config.homeKit,
            awayKit = config.awayKit,
            graphicsQuality = settings.graphicsQuality,
            targetFps = settings.targetFps
        )
    }

    LaunchedEffect(settings.graphicsQuality, settings.targetFps) {
        renderer.updatePerformanceConfig(settings.graphicsQuality, settings.targetFps)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // 3D OpenGL ES Stadium Viewport
        AndroidView(
            factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    setEGLContextClientVersion(2)
                    setRenderer(renderer)
                    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                }
            },
            update = { glView ->
                val scale = settings.resolutionScale.coerceIn(0.65f, 1.0f)
                if (glView.width > 0 && glView.height > 0) {
                    if (scale < 0.99f) {
                        glView.holder.setFixedSize(
                            (glView.width * scale).toInt().coerceAtLeast(320),
                            (glView.height * scale).toInt().coerceAtLeast(180)
                        )
                    } else {
                        glView.holder.setSizeFromLayout()
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top Broadcast Scoreboard & Quick Toggles
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            if (hud.mode == GameModeType.TRAINING) {
                TrainingScoreBug(hud)
            } else {
                BroadcastScoreBug(hud)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallHudButton(
                    icon = if (hud.lighting == StadiumLighting.EVENING) Icons.Default.NightlightRound else Icons.Default.WbSunny,
                    desc = "Toggle Lighting",
                    tag = "hud_lighting_button",
                    onClick = { engine.toggleLighting() }
                )
                SmallHudButton(
                    icon = Icons.Default.Videocam,
                    desc = "Switch Camera",
                    tag = "hud_camera_button",
                    onClick = { engine.cycleCameraMode() }
                )
                SmallHudButton(
                    icon = Icons.Default.Pause,
                    desc = "Pause Match",
                    tag = "pause_match_button",
                    onClick = { engine.setPaused(true) }
                )
            }
        }

        // Goal Banner / Replay Tag / Event Toast
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 62.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(visible = hud.goalBannerText != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ChampionshipGold,
                    border = BorderStroke(2.dp, Color.White)
                ) {
                    Text(
                        text = hud.goalBannerText ?: "",
                        style = MaterialTheme.typography.headlineMedium,
                        color = StadiumSlate,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp)
                    )
                }
            }
            if (hud.phase == MatchPhase.REPLAY) {
                Surface(shape = RoundedCornerShape(8.dp), color = BroadcastCrimson) {
                    Text(
                        text = "INSTANT REPLAY • 0.5x",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
            AnimatedVisibility(visible = hud.eventToast != null && hud.goalBannerText == null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BroadcastCharcoal.copy(alpha = 0.85f)
                ) {
                    Text(
                        text = hud.eventToast ?: "",
                        style = MaterialTheme.typography.labelLarge,
                        color = FloodlightLime,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Penalty Shootout Overlay
        if (hud.phase == MatchPhase.PENALTY_SHOOTOUT) {
            PenaltyShootoutBar(
                hud = hud,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp)
            )
        }

        // Bottom Center: Active Player Bar + Compact Pitch Minimap
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (hud.mode != GameModeType.TRAINING && hud.phase != MatchPhase.PENALTY_SHOOTOUT) {
                PitchMinimap(hud = hud)
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = BroadcastCharcoal.copy(alpha = 0.82f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "#${hud.activePlayerNumber} ${hud.activePlayerName}",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary
                    )
                    Box(
                        modifier = Modifier
                            .width(64.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(ElevatedSlate)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(hud.activePlayerStamina.coerceIn(0.05f, 1f))
                                .background(if (hud.activePlayerStamina > 0.3f) FloodlightLime else BroadcastCrimson)
                        )
                    }
                }
            }
        }

        // Multi-Touch Mobile Controls (Active during live play)
        val controlsActive = !hud.isPaused && hud.phase !in setOf(
            MatchPhase.HALF_TIME,
            MatchPhase.FULL_TIME,
            MatchPhase.TRAINING_COMPLETE,
            MatchPhase.GOAL_CELEBRATION,
            MatchPhase.REPLAY
        )
        if (controlsActive) {
            val offsets = settings.touchOffsets
            VirtualJoystickPad(
                inputManager = viewModel.inputManager,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 18.dp, bottom = 14.dp)
                    .offset { IntOffset(offsets.joystickOffsetX.roundToInt(), offsets.joystickOffsetY.roundToInt()) }
            )

            ActionButtonCluster(
                inputManager = viewModel.inputManager,
                hasPossession = hud.userHasPossession || hud.mode == GameModeType.TRAINING || hud.phase == MatchPhase.PENALTY_SHOOTOUT,
                buttonScale = offsets.buttonScale,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 12.dp)
                    .offset { IntOffset(offsets.actionClusterOffsetX.roundToInt(), offsets.actionClusterOffsetY.roundToInt()) }
            )
        }

        // Half-Time Overlay
        if (hud.phase == MatchPhase.HALF_TIME) {
            MatchSummaryOverlay(
                title = "HALF-TIME",
                hud = hud,
                primaryButtonLabel = "START 2ND HALF",
                onPrimaryClick = { engine.startSecondHalf() },
                secondaryButtonLabel = "Tactics & Subs",
                onSecondaryClick = onOpenInMatchTactics,
                onExitClick = onExitMatch
            )
        }

        // Full-Time Results Overlay
        if (hud.phase == MatchPhase.FULL_TIME) {
            val canExtraTime = hud.homeScore == hud.awayScore && config.mode == GameModeType.QUICK_MATCH
            MatchSummaryOverlay(
                title = "FULL-TIME RESULT",
                hud = hud,
                primaryButtonLabel = if (canExtraTime) "PLAY EXTRA TIME" else "PLAY AGAIN",
                onPrimaryClick = {
                    if (canExtraTime) engine.startExtraTime() else viewModel.restartActiveMatch()
                },
                secondaryButtonLabel = "CONTINUE",
                onSecondaryClick = onExitMatch,
                onExitClick = onExitMatch
            )
        }

        // Training Complete Overlay
        if (hud.phase == MatchPhase.TRAINING_COMPLETE) {
            TrainingCompleteOverlay(
                hud = hud,
                onRetry = viewModel::restartActiveMatch,
                onDone = onExitMatch
            )
        }

        // Pause Menu Overlay
        if (hud.isPaused) {
            PauseOverlay(
                onResume = { engine.setPaused(false) },
                onTactics = onOpenInMatchTactics,
                onRestart = viewModel::restartActiveMatch,
                onExit = onExitMatch
            )
        }
    }
}

@Composable
private fun BroadcastScoreBug(hud: HudSnapshot) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = BroadcastCharcoal.copy(alpha = 0.90f),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.width(4.dp).height(20.dp).background(Color(hud.homeBadgePrimary), RoundedCornerShape(2.dp)))
            Text(hud.homeCode, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Surface(shape = RoundedCornerShape(6.dp), color = ElevatedSlate) {
                Text(
                    text = "${hud.homeScore} - ${hud.awayScore}",
                    style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                    color = ChampionshipGold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                )
            }
            Text(hud.awayCode, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Box(Modifier.width(4.dp).height(20.dp).background(Color(hud.awayBadgePrimary), RoundedCornerShape(2.dp)))

            Surface(shape = RoundedCornerShape(6.dp), color = PitchEmerald) {
                Text(
                    text = "${hud.halfLabel} ${hud.formattedClock}",
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    color = StadiumSlate,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun TrainingScoreBug(hud: HudSnapshot) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = BroadcastCharcoal.copy(alpha = 0.90f),
        border = BorderStroke(1.dp, FloodlightLime.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(hud.trainingDrill?.displayName?.uppercase() ?: "TRAINING", style = MaterialTheme.typography.labelLarge, color = FloodlightLime)
            Text("SCORE ${hud.trainingScore}/${hud.trainingTarget}", style = MaterialTheme.typography.titleLarge, color = ChampionshipGold)
            Text("${hud.trainingTimeLeftSec}s", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        }
    }
}

@Composable
private fun SmallHudButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    tag: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(BroadcastCharcoal.copy(alpha = 0.85f))
            .testTag(tag)
    ) {
        Icon(icon, contentDescription = desc, tint = TextPrimary)
    }
}

@Composable
private fun PitchMinimap(hud: HudSnapshot) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF0A2416).copy(alpha = 0.78f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
        modifier = Modifier.size(width = 134.dp, height = 72.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            val w = size.width
            val h = size.height
            drawRect(color = Color.White.copy(alpha = 0.35f), style = Stroke(1.dp.toPx()))
            drawLine(Color.White.copy(alpha = 0.3f), Offset(w * 0.5f, 0f), Offset(w * 0.5f, h), 1.dp.toPx())

            hud.radarDots.forEach { dot ->
                val cx = (dot.xNorm + 1f) * 0.5f * w
                val cy = (dot.zNorm + 1f) * 0.5f * h
                drawCircle(
                    color = when {
                        dot.isControlled -> FloodlightLime
                        dot.isHome -> Color(hud.homeBadgePrimary)
                        else -> Color(hud.awayBadgePrimary)
                    },
                    radius = if (dot.isControlled) 3.8.dp.toPx() else 2.5.dp.toPx(),
                    center = Offset(cx, cy)
                )
            }
            val bx = (hud.ballRadarX + 1f) * 0.5f * w
            val bz = (hud.ballRadarZ + 1f) * 0.5f * h
            drawCircle(color = Color.White, radius = 3.dp.toPx(), center = Offset(bx, bz))
        }
    }
}

@Composable
private fun VirtualJoystickPad(
    inputManager: MobileInputManager,
    modifier: Modifier = Modifier
) {
    var knobX by remember { mutableFloatStateOf(0f) }
    var knobY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .size(134.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f))
            .pointerInput(inputManager) {
                val maxR = size.width * 0.38f
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val centerX = size.width * 0.5f
                    val centerY = size.height * 0.5f

                    fun updateFromPos(px: Float, py: Float) {
                        val dx = px - centerX
                        val dy = py - centerY
                        val dist = hypot(dx, dy)
                        val clamped = dist.coerceAtMost(maxR)
                        val nx = if (dist > 0.001f) dx / dist else 0f
                        val ny = if (dist > 0.001f) dy / dist else 0f
                        knobX = nx * clamped
                        knobY = ny * clamped
                        val mag = (clamped / maxR).coerceIn(0f, 1f)
                        inputManager.setJoystickVector(nx * mag, ny * mag)
                    }

                    updateFromPos(down.position.x, down.position.y)
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change != null && change.pressed) {
                            updateFromPos(change.position.x, change.position.y)
                            change.consume()
                        } else {
                            break
                        }
                    } while (true)

                    knobX = 0f
                    knobY = 0f
                    inputManager.setJoystickVector(0f, 0f)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.30f),
                style = Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color = FloodlightLime.copy(alpha = 0.85f),
                radius = 24.dp.toPx(),
                center = Offset(size.width * 0.5f + knobX, size.height * 0.5f + knobY)
            )
        }
    }
}

@Composable
private fun ActionButtonCluster(
    inputManager: MobileInputManager,
    hasPossession: Boolean,
    buttonScale: Float,
    modifier: Modifier = Modifier
) {
    val btnSize = (56f * buttonScale).dp
    val bigSize = (66f * buttonScale).dp

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            HoldablePadButton(
                label = "SPRINT",
                color = SkyBroadcastBlue,
                size = btnSize,
                testTag = "btn_sprint",
                onPressChanged = { pressed -> inputManager.isSprinting = pressed }
            )
            TapPadButton(
                label = "THROUGH",
                color = FloodlightLime,
                size = btnSize,
                testTag = "btn_through",
                onTap = { inputManager.triggerThrough() }
            )
            TapPadButton(
                label = if (hasPossession) "SWITCH" else "SWITCH",
                color = TextSecondary,
                size = btnSize,
                testTag = "btn_switch",
                onTap = { inputManager.triggerSwitch() }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TapPadButton(
                label = if (hasPossession) "PASS" else "TACKLE",
                color = if (hasPossession) PitchEmerald else BroadcastCrimson,
                size = bigSize,
                testTag = "btn_pass_tackle",
                onTap = {
                    if (hasPossession) inputManager.triggerPass(isLong = false)
                    else inputManager.triggerTackle()
                },
                onLongHoldRelease = {
                    if (hasPossession) inputManager.triggerPass(isLong = true)
                }
            )
            ShootChargeButton(
                size = bigSize,
                inputManager = inputManager,
                label = if (hasPossession) "SHOOT" else "SLIDE",
                isAttack = hasPossession
            )
        }
    }
}

@Composable
private fun TapPadButton(
    label: String,
    color: Color,
    size: Dp,
    testTag: String,
    onTap: () -> Unit,
    onLongHoldRelease: (() -> Unit)? = null
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.90f else 1f, spring(), label = "btnScale")

    Box(
        modifier = Modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(color.copy(alpha = if (pressed) 0.55f else 0.28f))
            .testTag(testTag)
            .pointerInput(onTap, onLongHoldRelease) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    val startMs = System.currentTimeMillis()
                    while (true) {
                        val evt = awaitPointerEvent()
                        val ch = evt.changes.firstOrNull { it.id == down.id }
                        if (ch == null || !ch.pressed) break
                    }
                    pressed = false
                    val heldMs = System.currentTimeMillis() - startMs
                    if (heldMs > 280L && onLongHoldRelease != null) {
                        onLongHoldRelease()
                    } else {
                        onTap()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(color = color.copy(alpha = 0.9f), style = Stroke(2.dp.toPx()))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
            color = Color.White
        )
    }
}

@Composable
private fun HoldablePadButton(
    label: String,
    color: Color,
    size: Dp,
    testTag: String,
    onPressChanged: (Boolean) -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.90f else 1f, spring(), label = "sprintScale")

    Box(
        modifier = Modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(color.copy(alpha = if (pressed) 0.55f else 0.25f))
            .testTag(testTag)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    onPressChanged(true)
                    while (true) {
                        val evt = awaitPointerEvent()
                        val ch = evt.changes.firstOrNull { it.id == down.id }
                        if (ch == null || !ch.pressed) break
                    }
                    pressed = false
                    onPressChanged(false)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(color = color, style = Stroke(2.dp.toPx()))
        }
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp), color = Color.White)
    }
}

@Composable
private fun ShootChargeButton(
    size: Dp,
    inputManager: MobileInputManager,
    label: String,
    isAttack: Boolean
) {
    var pressed by remember { mutableStateOf(false) }
    var charge by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(pressed) {
        if (pressed && isAttack) {
            val start = System.currentTimeMillis()
            while (pressed) {
                val elapsed = (System.currentTimeMillis() - start) / 700f
                charge = elapsed.coerceIn(0.25f, 1f)
                inputManager.shootChargeProgress = charge
                kotlinx.coroutines.delay(16L)
            }
        } else {
            charge = 0f
            inputManager.shootChargeProgress = 0f
        }
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(ChampionshipGold.copy(alpha = if (pressed) 0.55f else 0.30f))
            .testTag("btn_shoot")
            .pointerInput(isAttack) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    val start = System.currentTimeMillis()
                    while (true) {
                        val evt = awaitPointerEvent()
                        val ch = evt.changes.firstOrNull { it.id == down.id }
                        if (ch == null || !ch.pressed) break
                    }
                    val power = ((System.currentTimeMillis() - start) / 680f).coerceIn(0.32f, 1f)
                    pressed = false
                    if (isAttack) {
                        inputManager.triggerShoot(power)
                    } else {
                        inputManager.triggerTackle()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(color = ChampionshipGold, style = Stroke(2.dp.toPx()))
            if (charge > 0f) {
                drawArc(
                    color = BroadcastCrimson,
                    startAngle = -90f,
                    sweepAngle = 360f * charge,
                    useCenter = false,
                    style = Stroke(5.dp.toPx())
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp),
            color = Color.White
        )
    }
}

@Composable
private fun PenaltyShootoutBar(hud: HudSnapshot, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = BroadcastCharcoal.copy(alpha = 0.90f),
        border = BorderStroke(1.dp, ChampionshipGold),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (hud.penaltyIsUserTurn) "YOUR KICK — AIM WITH JOYSTICK & TAP SHOOT" else "OPPONENT KICK — MOVE KEEPER WITH JOYSTICK",
                style = MaterialTheme.typography.labelLarge,
                color = FloodlightLime
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                KickPipRow(hud.homeCode, hud.penaltyHomeKicks)
                KickPipRow(hud.awayCode, hud.penaltyAwayKicks)
            }
        }
    }
}

@Composable
private fun KickPipRow(code: String, kicks: List<Boolean?>) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(code, style = MaterialTheme.typography.labelSmall, color = TextPrimary)
        for (i in 0 until 5) {
            val res = kicks.getOrNull(i)
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        when (res) {
                            true -> PitchEmerald
                            false -> BroadcastCrimson
                            null -> ElevatedSlate
                        }
                    )
            )
        }
    }
}

@Composable
private fun MatchSummaryOverlay(
    title: String,
    hud: HudSnapshot,
    primaryButtonLabel: String,
    onPrimaryClick: () -> Unit,
    secondaryButtonLabel: String,
    onSecondaryClick: () -> Unit,
    onExitClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = BroadcastCharcoal,
            border = BorderStroke(1.5.dp, PitchEmerald),
            modifier = Modifier.widthIn(max = 520.dp).padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, style = MaterialTheme.typography.headlineMedium, color = ChampionshipGold)
                Text("${hud.homeName}  ${hud.homeScore} - ${hud.awayScore}  ${hud.awayName}", style = MaterialTheme.typography.headlineLarge, color = TextPrimary)

                StatCompareRow("Possession", "${hud.stats.homePossessionPct}%", "${100 - hud.stats.homePossessionPct}%")
                StatCompareRow("Shots (On Target)", "${hud.stats.homeShots} (${hud.stats.homeOnTarget})", "${hud.stats.awayShots} (${hud.stats.awayOnTarget})")
                StatCompareRow("Passes", "${hud.stats.homePasses}", "${hud.stats.awayPasses}")
                StatCompareRow("Tackles", "${hud.stats.homeTackles}", "${hud.stats.awayTackles}")

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onSecondaryClick, modifier = Modifier.weight(1f).height(48.dp)) {
                        Text(secondaryButtonLabel, color = TextPrimary)
                    }
                    Button(
                        onClick = onPrimaryClick,
                        colors = ButtonDefaults.buttonColors(containerColor = PitchEmerald, contentColor = StadiumSlate),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text(primaryButtonLabel, style = MaterialTheme.typography.labelLarge)
                    }
                }
                TextButton(onClick = onExitClick) {
                    Text("Exit to Menu", color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun StatCompareRow(label: String, homeVal: String, awayVal: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(homeVal, style = MaterialTheme.typography.labelLarge, color = PitchEmerald)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(awayVal, style = MaterialTheme.typography.labelLarge, color = SkyBroadcastBlue)
    }
}

@Composable
private fun TrainingCompleteOverlay(
    hud: HudSnapshot,
    onRetry: () -> Unit,
    onDone: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = BroadcastCharcoal,
            border = BorderStroke(1.5.dp, FloodlightLime),
            modifier = Modifier.widthIn(max = 420.dp).padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("DRILL COMPLETE", style = MaterialTheme.typography.headlineMedium, color = FloodlightLime)
                Text("Score: ${hud.trainingScore} / Target ${hud.trainingTarget}", style = MaterialTheme.typography.headlineLarge, color = ChampionshipGold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f).height(48.dp)) {
                        Text("Exit", color = TextPrimary)
                    }
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = PitchEmerald, contentColor = StadiumSlate),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("RETRY")
                    }
                }
            }
        }
    }
}

@Composable
private fun PauseOverlay(
    onResume: () -> Unit,
    onTactics: () -> Unit,
    onRestart: () -> Unit,
    onExit: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = BroadcastCharcoal,
            border = BorderStroke(1.5.dp, SlateBorder),
            modifier = Modifier.widthIn(max = 360.dp).padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("MATCH PAUSED", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                Button(
                    onClick = onResume,
                    colors = ButtonDefaults.buttonColors(containerColor = PitchEmerald, contentColor = StadiumSlate),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("resume_match_button")
                ) {
                    Text("RESUME MATCH")
                }
                OutlinedButton(onClick = onTactics, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text("Team Tactics & Subs", color = TextPrimary)
                }
                OutlinedButton(onClick = onRestart, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text("Restart Match", color = TextPrimary)
                }
                TextButton(onClick = onExit, modifier = Modifier.fillMaxWidth().height(48.dp).testTag("exit_match_button")) {
                    Text("Quit to Menu", color = BroadcastCrimson)
                }
            }
        }
    }
}
