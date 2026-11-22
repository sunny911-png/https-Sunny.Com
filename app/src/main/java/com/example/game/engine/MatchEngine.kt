package com.example.game.engine

import com.example.game.model.*
import com.example.game.systems.AudioManager
import com.example.game.systems.FormationManager
import com.example.game.systems.MobileInputManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*
import kotlin.random.Random

class MatchEngine(
    private val config: MatchConfig,
    private val input: MobileInputManager,
    private val audioManager: AudioManager,
    private val formationManager: FormationManager
) {

    val ball = SimBall()
    val players: Array<SimPlayer>
    val cameraController = CameraController()

    private val ballPhysics = BallPhysics()
    private val ballController = BallController()
    private val playerController = PlayerController(ballController, audioManager)
    private val playerAI = PlayerAI(playerController, ballController, audioManager)
    private val goalkeeperAI = GoalkeeperAI(playerController, ballController, audioManager)

    // Replay ring buffer (240 frames = 4 seconds at 60Hz)
    private val replayCapacity = 240
    private val replayBuffer = Array(replayCapacity) { ReplayFrame() }
    private var replayWriteIdx = 0
    private var replayCount = 0
    private var replayPlayIdx = 0
    private var replaySlowMoAccum = 0f

    // Training targets / cones / wall
    val trainingMarkers = Array(8) { TrainingMarker() }

    @Volatile var controlledIndex: Int = 9 // Striker by default
    @Volatile var userCameraMode: CameraMode = CameraMode.BROADCAST
    @Volatile var currentLighting: StadiumLighting = config.lighting
    @Volatile var netRippleLeft: Float = 0f
    @Volatile var netRippleRight: Float = 0f
    @Volatile var isPaused: Boolean = false
        private set

    private var phase: MatchPhase = if (config.mode == GameModeType.TRAINING) {
        MatchPhase.TRAINING_ACTIVE
    } else {
        MatchPhase.PRE_KICKOFF
    }

    private var homeScore = 0
    private var awayScore = 0
    private var matchTimeSeconds = 0f
    private val totalMatchSeconds = config.durationMinutes * 60f
    private var phaseTimer = 0.8f
    private var goalBannerText: String? = null
    private var eventToast: String? = null
    private var eventToastTimer = 0f

    // Stats
    private var homePossessionTicks = 0
    private var awayPossessionTicks = 0
    private var homeShots = 0
    private var awayShots = 0
    private var homeOnTarget = 0
    private var awayOnTarget = 0
    private var homePasses = 0
    private var awayPasses = 0
    private var homeTackles = 0
    private var awayTackles = 0

    // Penalty Shootout state
    private val penHomeKicks = mutableListOf<Boolean?>()
    private val penAwayKicks = mutableListOf<Boolean?>()
    private var penUserTurn = true
    private var penWaitTimer = 0f
    private var penShotInFlight = false

    // Training state
    private var trainingScore = 0
    private var trainingTimeLeft = config.trainingDrill?.durationSeconds?.toFloat() ?: 60f
    private var trainingPrompt = config.trainingDrill?.description ?: ""

    private var hudEmitAccum = 0f
    private var lastTouchAwarded = -1

    private val _hudState = MutableStateFlow(HudSnapshot())
    val hudState: StateFlow<HudSnapshot> = _hudState.asStateFlow()

    init {
        val homeSlots = config.homeTeam.defaultFormation.slots
        val awaySlots = config.awayTeam.defaultFormation.slots
        players = Array(22) { idx ->
            val isHome = idx < 11
            val slotIdx = idx % 11
            val team = if (isHome) config.homeTeam else config.awayTeam
            val profile = team.roster.getOrElse(slotIdx) { team.roster.first() }
            val slot = if (isHome) homeSlots[slotIdx] else awaySlots[slotIdx]
            val (ax, az) = formationManager.slotToWorld(slot, isHome)
            SimPlayer(
                index = idx,
                isHome = isHome,
                slotIndex = slotIdx,
                profile = profile,
                isGoalkeeper = slot.role == PlayerRole.GK
            ).apply {
                anchorX = ax
                anchorZ = az
                x = ax
                z = az
            }
        }

        if (config.mode == GameModeType.TRAINING) {
            setupTrainingDrill(config.trainingDrill ?: TrainingDrillType.SHOOTING)
        } else {
            resetForKickoff(homeKicksOff = true)
        }
        cameraController.snapToBall(ball, userCameraMode)
        emitHudSnapshot()
    }

    fun updateRosterAndFormation(homeTeam: TeamProfile, awayTeam: TeamProfile) {
        val hSlots = homeTeam.defaultFormation.slots
        val aSlots = awayTeam.defaultFormation.slots
        for (i in 0 until 11) {
            val (hx, hz) = formationManager.slotToWorld(hSlots[i], isHome = true)
            players[i].profile = homeTeam.roster[i]
            players[i].anchorX = hx
            players[i].anchorZ = hz
            val (ax, az) = formationManager.slotToWorld(aSlots[i], isHome = false)
            players[11 + i].profile = awayTeam.roster[i]
            players[11 + i].anchorX = ax
            players[11 + i].anchorZ = az
        }
        emitHudSnapshot()
    }

    fun setPaused(paused: Boolean) {
        isPaused = paused
        emitHudSnapshot()
    }

    fun cycleCameraMode() {
        userCameraMode = if (userCameraMode == CameraMode.BROADCAST) {
            CameraMode.BALL_FOLLOW
        } else {
            CameraMode.BROADCAST
        }
        showToast("Camera: ${userCameraMode.displayName}")
        emitHudSnapshot()
    }

    fun toggleLighting() {
        currentLighting = if (currentLighting == StadiumLighting.EVENING) {
            StadiumLighting.DAY
        } else {
            StadiumLighting.EVENING
        }
        emitHudSnapshot()
    }

    fun startSecondHalf() {
        if (phase != MatchPhase.HALF_TIME) return
        phase = MatchPhase.SECOND_HALF
        resetForKickoff(homeKicksOff = false)
        audioManager.playWhistleShort()
        emitHudSnapshot()
    }

    fun startExtraTime() {
        if (phase != MatchPhase.FULL_TIME) return
        phase = MatchPhase.EXTRA_TIME_1
        resetForKickoff(homeKicksOff = true)
        audioManager.playWhistleShort()
        emitHudSnapshot()
    }

    fun stepSimulation(dtRaw: Float) {
        if (isPaused) return
        val dt = dtRaw.coerceIn(0.001f, 0.05f)

        if (netRippleLeft > 0f) netRippleLeft = (netRippleLeft - dt * 1.4f).coerceAtLeast(0f)
        if (netRippleRight > 0f) netRippleRight = (netRippleRight - dt * 1.4f).coerceAtLeast(0f)
        if (eventToastTimer > 0f) {
            eventToastTimer -= dt
            if (eventToastTimer <= 0f) eventToast = null
        }

        when (phase) {
            MatchPhase.PRE_KICKOFF -> {
                phaseTimer -= dt
                cameraController.update(ball.x, ball.y, ball.z, userCameraMode, isPenalty = false, dt = dt)
                if (phaseTimer <= 0f) {
                    phase = MatchPhase.FIRST_HALF
                    audioManager.playWhistleShort()
                }
            }
            MatchPhase.FIRST_HALF, MatchPhase.SECOND_HALF, MatchPhase.EXTRA_TIME_1, MatchPhase.EXTRA_TIME_2 -> {
                stepActiveMatch(dt)
            }
            MatchPhase.GOAL_CELEBRATION -> {
                phaseTimer -= dt
                for (p in players) playerController.applyMovement(p, dt)
                ballPhysics.step(ball, players, dt)
                cameraController.update(ball.x, ball.y, ball.z, CameraMode.GOAL_CELEBRATION, isPenalty = false, dt = dt)
                if (phaseTimer <= 0f) {
                    if (replayCount >= 60) {
                        phase = MatchPhase.REPLAY
                        replayPlayIdx = 0
                        replaySlowMoAccum = 0f
                    } else {
                        finishGoalSequence()
                    }
                }
            }
            MatchPhase.REPLAY -> {
                stepReplay(dt)
            }
            MatchPhase.PENALTY_SHOOTOUT -> {
                stepPenaltyShootout(dt)
            }
            MatchPhase.TRAINING_ACTIVE -> {
                stepTraining(dt)
            }
            MatchPhase.HALF_TIME, MatchPhase.FULL_TIME, MatchPhase.TRAINING_COMPLETE -> {
                cameraController.update(ball.x, ball.y, ball.z, userCameraMode, isPenalty = false, dt = dt)
            }
        }

        hudEmitAccum += dt
        if (hudEmitAccum >= 0.05f) {
            hudEmitAccum = 0f
            emitHudSnapshot()
        }
    }

    private fun stepActiveMatch(dt: Float) {
        // Advance match clock
        matchTimeSeconds += dt
        val halfDur = totalMatchSeconds * 0.5f
        if (phase == MatchPhase.FIRST_HALF && matchTimeSeconds >= halfDur) {
            matchTimeSeconds = halfDur
            phase = MatchPhase.HALF_TIME
            audioManager.playWhistleShort()
            emitHudSnapshot()
            return
        } else if (phase == MatchPhase.SECOND_HALF && matchTimeSeconds >= totalMatchSeconds) {
            matchTimeSeconds = totalMatchSeconds
            if (homeScore == awayScore && config.allowExtraTimeAndPenalties) {
                phase = MatchPhase.EXTRA_TIME_1
                resetForKickoff(homeKicksOff = true)
                showToast("Extra Time!")
                audioManager.playWhistleShort()
            } else {
                phase = MatchPhase.FULL_TIME
                audioManager.playWhistleFullTime()
            }
            emitHudSnapshot()
            return
        } else if (phase == MatchPhase.EXTRA_TIME_1 && matchTimeSeconds >= totalMatchSeconds + halfDur * 0.35f) {
            phase = MatchPhase.EXTRA_TIME_2
            resetForKickoff(homeKicksOff = false)
            audioManager.playWhistleShort()
        } else if (phase == MatchPhase.EXTRA_TIME_2 && matchTimeSeconds >= totalMatchSeconds + halfDur * 0.70f) {
            if (homeScore == awayScore) {
                startPenaltyShootout()
            } else {
                phase = MatchPhase.FULL_TIME
                audioManager.playWhistleFullTime()
            }
            emitHudSnapshot()
            return
        }

        // Switch button handling
        if (input.consumeSwitch()) {
            controlledIndex = playerController.pickBestSwitchPlayer(controlledIndex, players, ball, userIsHome = true)
        }

        // Update User controlled player
        val ctrlPlayer = players[controlledIndex]
        playerController.updateControlledPlayer(
            player = ctrlPlayer,
            allPlayers = players,
            ball = ball,
            input = input,
            dt = dt,
            onShotTaken = { isHome, onTarget ->
                if (isHome) {
                    homeShots++
                    if (onTarget) homeOnTarget++
                } else {
                    awayShots++
                    if (onTarget) awayOnTarget++
                }
            },
            onPassAttempted = { isHome -> if (isHome) homePasses++ else awayPasses++ },
            onTackleAttempted = { isHome -> if (isHome) homeTackles++ else awayTackles++ }
        )

        // Update AI players & Goalkeepers
        for (p in players) {
            if (p.index == controlledIndex) continue
            if (p.isGoalkeeper) {
                goalkeeperAI.updateGoalkeeper(
                    gk = p,
                    allPlayers = players,
                    ball = ball,
                    difficulty = config.difficulty,
                    dt = dt,
                    onPassAttempted = { isHome -> if (isHome) homePasses++ else awayPasses++ }
                )
            } else {
                playerAI.updateAIPlayer(
                    player = p,
                    allPlayers = players,
                    ball = ball,
                    difficulty = config.difficulty,
                    controlledIndex = controlledIndex,
                    dt = dt,
                    onShotTaken = { isHome, onTarget ->
                        if (isHome) {
                            homeShots++
                            if (onTarget) homeOnTarget++
                        } else {
                            awayShots++
                            if (onTarget) awayOnTarget++
                        }
                    },
                    onPassAttempted = { isHome -> if (isHome) homePasses++ else awayPasses++ },
                    onTackleAttempted = { isHome -> if (isHome) homeTackles++ else awayTackles++ }
                )
            }
        }

        resolvePlayerContacts()
        val ballEvt = ballPhysics.step(ball, players, dt)
        handleBallPickup()

        // Possession stat
        if (ball.ownerIndex in players.indices) {
            if (players[ball.ownerIndex].isHome) homePossessionTicks++ else awayPossessionTicks++
        }

        recordReplayFrame()

        when (ballEvt) {
            is BallEvent.GoalScored -> {
                onGoalScored(ballEvt.scoredByHome)
            }
            is BallEvent.OutOfBounds -> {
                handleRestart(ballEvt)
            }
            BallEvent.PostHit -> {
                audioManager.playPostClang()
                cameraController.addShake(0.75f)
                showToast("Off the woodwork!")
            }
            null -> Unit
        }

        cameraController.update(ball.x, ball.y, ball.z, userCameraMode, isPenalty = false, dt = dt)
    }

    private fun resolvePlayerContacts() {
        // Player-to-player soft separation & tackle dispossession
        for (i in players.indices) {
            val a = players[i]
            for (j in i + 1 until players.size) {
                val b = players[j]
                val dx = b.x - a.x
                val dz = b.z - a.z
                val distSq = dx * dx + dz * dz
                val minDist = PitchConstants.PLAYER_RADIUS * 2f
                if (distSq < minDist * minDist && distSq > 0.0001f) {
                    val dist = sqrt(distSq)
                    val overlap = (minDist - dist) * 0.5f
                    val nx = dx / dist
                    val nz = dz / dist
                    a.x -= nx * overlap
                    a.z -= nz * overlap
                    b.x += nx * overlap
                    b.z += nz * overlap

                    // Check tackle steal!
                    if (a.isHome != b.isHome) {
                        if (a.tackleTimer > 0f && ball.ownerIndex == b.index) {
                            ball.ownerIndex = a.index
                            ball.lastTouchIsHome = a.isHome
                            ball.lastTouchName = a.profile.name
                            b.stunTimer = 0.55f
                            audioManager.playPass()
                            if (a.isHome && !a.isGoalkeeper) controlledIndex = a.index
                        } else if (b.tackleTimer > 0f && ball.ownerIndex == a.index) {
                            ball.ownerIndex = b.index
                            ball.lastTouchIsHome = b.isHome
                            ball.lastTouchName = b.profile.name
                            a.stunTimer = 0.55f
                            audioManager.playPass()
                        }
                    }
                }
            }
        }
    }

    private fun handleBallPickup() {
        if (ball.ownerIndex != -1 || ball.pickupCooldown > 0f || ball.y > 1.35f) return
        val ballSpeed = hypot(ball.vx, ball.vz)
        for (p in players) {
            if (p.stunTimer > 0f) continue
            val d = hypot(p.x - ball.x, p.z - ball.z)
            val controlRadius = if (p.isGoalkeeper) 1.2f else 0.95f
            if (d < controlRadius) {
                // Fast shots can occasionally bounce off defenders if low defending, otherwise trap cleanly
                if (ballSpeed < 24f || p.isGoalkeeper) {
                    ball.ownerIndex = p.index
                    ball.lastTouchIsHome = p.isHome
                    ball.lastTouchName = p.profile.name
                    ball.vy = 0f
                    ball.y = PitchConstants.BALL_RADIUS
                    if (p.isHome && !p.isGoalkeeper) {
                        controlledIndex = p.index
                    }
                    break
                }
            }
        }
    }

    private fun onGoalScored(scoredByHome: Boolean) {
        if (scoredByHome) {
            homeScore++
            netRippleRight = 1.0f
        } else {
            awayScore++
            netRippleLeft = 1.0f
        }
        val scorerTeam = if (scoredByHome) config.homeTeam.shortCode else config.awayTeam.shortCode
        val scorerName = if (ball.lastTouchIsHome == scoredByHome && ball.lastTouchName.isNotBlank()) {
            ball.lastTouchName
        } else {
            if (scoredByHome) config.homeTeam.roster[9].name else config.awayTeam.roster[9].name
        }
        goalBannerText = "GOAL! $scorerTeam — $scorerName"
        phase = MatchPhase.GOAL_CELEBRATION
        phaseTimer = 2.0f
        cameraController.addShake(0.6f)
        audioManager.playGoalCelebration()
        for (p in players) {
            if (p.isHome == scoredByHome) p.celebrateTimer = 2.0f
        }
        emitHudSnapshot()
    }

    private fun stepReplay(dt: Float) {
        replaySlowMoAccum += dt * 0.5f // 0.5x slow motion
        val stepInterval = 1f / 60f
        while (replaySlowMoAccum >= stepInterval) {
            replaySlowMoAccum -= stepInterval
            replayPlayIdx++
            if (replayPlayIdx >= replayCount) {
                finishGoalSequence()
                return
            }
        }
        val startIdx = (replayWriteIdx - replayCount + replayCapacity) % replayCapacity
        val frameIdx = (startIdx + replayPlayIdx) % replayCapacity
        val frame = replayBuffer[frameIdx]
        ball.x = frame.ballX; ball.y = frame.ballY; ball.z = frame.ballZ
        for (i in players.indices) {
            players[i].x = frame.playerX[i]
            players[i].y = frame.playerY[i]
            players[i].z = frame.playerZ[i]
            players[i].facingRad = frame.playerFacing[i]
        }
        cameraController.update(ball.x, ball.y, ball.z, CameraMode.REPLAY, isPenalty = false, dt = dt)
    }

    private fun finishGoalSequence() {
        goalBannerText = null
        val scoredByHome = ball.x > 0f
        phase = if (matchTimeSeconds >= totalMatchSeconds) MatchPhase.EXTRA_TIME_1 else {
            if (matchTimeSeconds < totalMatchSeconds * 0.5f) MatchPhase.FIRST_HALF else MatchPhase.SECOND_HALF
        }
        resetForKickoff(homeKicksOff = !scoredByHome)
        audioManager.playWhistleShort()
    }

    private fun handleRestart(evt: BallEvent.OutOfBounds) {
        ball.ownerIndex = -1
        ball.vx = 0f; ball.vy = 0f; ball.vz = 0f
        ball.y = PitchConstants.BALL_RADIUS

        if (!evt.isGoalLine) {
            // Throw-in
            ball.x = evt.x
            ball.z = evt.z
            val takerTeamHome = !evt.lastTouchHome
            val taker = findClosestOutfieldPlayer(takerTeamHome, evt.x, evt.z)
            taker.x = evt.x
            taker.z = evt.z
            ball.ownerIndex = taker.index
            if (takerTeamHome) controlledIndex = taker.index
            showToast("Throw-in")
        } else {
            val onRightGoalLine = evt.x > 0f
            if ((onRightGoalLine && evt.lastTouchHome) || (!onRightGoalLine && !evt.lastTouchHome)) {
                // Goal kick
                val gk = if (onRightGoalLine) players[11] else players[0]
                gk.x = if (onRightGoalLine) PitchConstants.HALF_LENGTH - 5f else -PitchConstants.HALF_LENGTH + 5f
                gk.z = 0f
                ball.x = gk.x
                ball.z = 0f
                ball.ownerIndex = gk.index
                gk.aiDecisionCooldown = 0.45f
                showToast("Goal Kick")
            } else {
                // Corner kick
                val attackingHome = !evt.lastTouchHome
                ball.x = if (onRightGoalLine) PitchConstants.HALF_LENGTH - 1f else -PitchConstants.HALF_LENGTH + 1f
                ball.z = if (evt.z >= 0f) PitchConstants.HALF_WIDTH - 1f else -PitchConstants.HALF_WIDTH + 1f
                val taker = findClosestOutfieldPlayer(attackingHome, ball.x, ball.z)
                taker.x = ball.x
                taker.z = ball.z
                ball.ownerIndex = taker.index
                if (attackingHome) controlledIndex = taker.index
                showToast("Corner Kick")
            }
        }
    }

    private fun findClosestOutfieldPlayer(isHome: Boolean, x: Float, z: Float): SimPlayer {
        var best = if (isHome) players[9] else players[20]
        var bestD = Float.MAX_VALUE
        for (p in players) {
            if (p.isHome != isHome || p.isGoalkeeper) continue
            val d = hypot(p.x - x, p.z - z)
            if (d < bestD) {
                bestD = d
                best = p
            }
        }
        return best
    }

    private fun resetForKickoff(homeKicksOff: Boolean) {
        ball.x = 0f; ball.y = PitchConstants.BALL_RADIUS; ball.z = 0f
        ball.vx = 0f; ball.vy = 0f; ball.vz = 0f
        ball.ownerIndex = -1
        replayWriteIdx = 0
        replayCount = 0

        for (p in players) {
            p.x = p.anchorX
            p.z = p.anchorZ
            p.vx = 0f; p.vz = 0f
            p.facingRad = if (p.isHome) 0f else Math.PI.toFloat()
            p.tackleTimer = 0f
            p.diveTimer = 0f
            p.stunTimer = 0f
        }
        val kicker = if (homeKicksOff) players[9] else players[20]
        kicker.x = if (homeKicksOff) -0.9f else 0.9f
        kicker.z = 0f
        ball.ownerIndex = kicker.index
        controlledIndex = 9
    }

    private fun recordReplayFrame() {
        val f = replayBuffer[replayWriteIdx]
        f.ballX = ball.x; f.ballY = ball.y; f.ballZ = ball.z
        for (i in players.indices) {
            f.playerX[i] = players[i].x
            f.playerY[i] = players[i].y
            f.playerZ[i] = players[i].z
            f.playerFacing[i] = players[i].facingRad
        }
        replayWriteIdx = (replayWriteIdx + 1) % replayCapacity
        if (replayCount < replayCapacity) replayCount++
    }

    // ==================== PENALTY SHOOTOUT ====================

    private fun startPenaltyShootout() {
        phase = MatchPhase.PENALTY_SHOOTOUT
        penHomeKicks.clear()
        penAwayKicks.clear()
        penUserTurn = true
        penShotInFlight = false
        setupPenaltyKick(userKicking = true)
        showToast("Penalty Shootout!")
        audioManager.playWhistleShort()
    }

    private fun setupPenaltyKick(userKicking: Boolean) {
        penShotInFlight = false
        penWaitTimer = 0f
        // Move other players to center circle
        for (p in players) {
            p.vx = 0f; p.vz = 0f; p.diveTimer = 0f
            if (!p.isGoalkeeper) {
                p.x = -2f + (p.index % 5) * 1.1f
                p.z = -5f + (p.index % 4) * 3f
            }
        }
        val taker = if (userKicking) players[9] else players[20]
        val gk = if (userKicking) players[11] else players[0]
        // Always stage penalty toward +X goal for crisp dramatic camera angle
        taker.x = PitchConstants.HALF_LENGTH - 13.5f
        taker.z = 0f
        taker.facingRad = 0f
        gk.x = PitchConstants.HALF_LENGTH - 0.6f
        gk.z = 0f
        gk.facingRad = Math.PI.toFloat()
        ball.x = PitchConstants.HALF_LENGTH - 11f
        ball.z = 0f
        ball.y = PitchConstants.BALL_RADIUS
        ball.vx = 0f; ball.vy = 0f; ball.vz = 0f
        ball.ownerIndex = -1
        if (!userKicking) penWaitTimer = 1.1f
    }

    private fun stepPenaltyShootout(dt: Float) {
        cameraController.update(ball.x, ball.y, ball.z, CameraMode.BROADCAST, isPenalty = true, dt = dt)
        val taker = if (penUserTurn) players[9] else players[20]
        val gk = if (penUserTurn) players[11] else players[0]

        if (!penShotInFlight) {
            if (penUserTurn) {
                if (input.consumeShoot() || input.consumePass()) {
                    val power = input.queuedShotPower.coerceIn(0.45f, 1f)
                    val aimZ = (input.moveZ * 3.55f).coerceIn(-3.55f, 3.55f)
                    taker.x = PitchConstants.HALF_LENGTH - 11.4f
                    ballController.releaseForShot(ball, taker, PitchConstants.HALF_LENGTH, aimZ, power)
                    audioManager.playKick(power)
                    // GK AI chooses dive
                    val gkGuess = (Random.nextFloat() * 2f - 1f) * 3.2f
                    gk.diveTimer = 0.55f
                    gk.diveDirZ = sign(gkGuess)
                    penShotInFlight = true
                    penWaitTimer = 1.8f
                }
            } else {
                // User controls GK with joystick Z!
                gk.vz = input.moveZ * 6.5f
                gk.z = (gk.z + gk.vz * dt).coerceIn(-3.4f, 3.4f)
                penWaitTimer -= dt
                if (penWaitTimer <= 0f) {
                    val aiAimZ = (Random.nextFloat() * 2f - 1f) * 3.3f
                    val power = 0.72f + Random.nextFloat() * 0.25f
                    taker.x = PitchConstants.HALF_LENGTH - 11.4f
                    ballController.releaseForShot(ball, taker, PitchConstants.HALF_LENGTH, aiAimZ, power)
                    audioManager.playKick(power)
                    penShotInFlight = true
                    penWaitTimer = 1.8f
                }
            }
            return
        }

        // Shot in flight
        if (gk.diveTimer > 0f) {
            gk.diveTimer -= dt
            gk.z = (gk.z + gk.diveDirZ * 6.5f * dt).coerceIn(-3.7f, 3.7f)
        } else if (!penUserTurn) {
            gk.vz = input.moveZ * 6.8f
            gk.z = (gk.z + gk.vz * dt).coerceIn(-3.7f, 3.7f)
        }

        val evt = ballPhysics.step(ball, players, dt)
        // Check GK block
        if (abs(ball.x - gk.x) < 1.1f && abs(ball.z - gk.z) < 1.25f && ball.y < 2.3f && ball.vx > 0f) {
            ball.vx = -ball.vx * 0.35f
            ball.vz += gk.diveDirZ * 2f
            audioManager.playPass()
            recordPenaltyOutcome(scored = false)
            return
        }

        if (evt is BallEvent.GoalScored) {
            netRippleRight = 1f
            audioManager.playGoalCelebration()
            recordPenaltyOutcome(scored = true)
            return
        }

        penWaitTimer -= dt
        if (evt is BallEvent.OutOfBounds || penWaitTimer <= 0f) {
            recordPenaltyOutcome(scored = false)
        }
    }

    private fun recordPenaltyOutcome(scored: Boolean) {
        if (penUserTurn) {
            penHomeKicks.add(scored)
            showToast(if (scored) "Penalty Scored!" else "Penalty Saved/Missed!")
        } else {
            penAwayKicks.add(scored)
            showToast(if (scored) "Opponent Scored" else "GREAT SAVE!")
        }

        // Check if shootout decided (best of 5 or sudden death)
        val hMade = penHomeKicks.count { it == true }
        val aMade = penAwayKicks.count { it == true }
        val hTaken = penHomeKicks.size
        val aTaken = penAwayKicks.size

        val decided = if (hTaken <= 5 && aTaken <= 5) {
            val hRem = 5 - hTaken
            val aRem = 5 - aTaken
            (hMade > aMade + aRem) || (aMade > hMade + hRem) || (hTaken == 5 && aTaken == 5 && hMade != aMade)
        } else {
            hTaken == aTaken && hMade != aMade
        }

        if (decided) {
            if (hMade > aMade) homeScore++ else awayScore++
            phase = MatchPhase.FULL_TIME
            audioManager.playWhistleFullTime()
            emitHudSnapshot()
            return
        }

        penUserTurn = !penUserTurn
        setupPenaltyKick(userKicking = penUserTurn)
    }

    // ==================== TRAINING DRILLS ====================

    private fun setupTrainingDrill(drill: TrainingDrillType) {
        trainingScore = 0
        trainingTimeLeft = drill.durationSeconds.toFloat()
        trainingPrompt = drill.description
        for (m in trainingMarkers) m.active = false
        for (p in players) {
            p.x = -40f; p.z = -30f; p.vx = 0f; p.vz = 0f
        }

        val striker = players[9]
        striker.x = 16f; striker.z = 0f; striker.facingRad = 0f
        controlledIndex = 9
        ball.x = 17f; ball.z = 0f; ball.y = PitchConstants.BALL_RADIUS
        ball.vx = 0f; ball.vy = 0f; ball.vz = 0f

        when (drill) {
            TrainingDrillType.PASSING -> {
                striker.x = 0f; striker.z = 0f
                ball.ownerIndex = striker.index
                players[10].x = 18f; players[10].z = -12f
                players[8].x = 18f; players[8].z = 12f
                trainingMarkers[0].apply { x = 16f; z = -10f; radius = 2.4f; active = true; isTargetGate = true }
                trainingMarkers[1].apply { x = 16f; z = 10f; radius = 2.4f; active = true; isTargetGate = true }
            }
            TrainingDrillType.SHOOTING -> {
                striker.x = 28f; striker.z = 2f
                ball.ownerIndex = striker.index
                players[11].x = PitchConstants.HALF_LENGTH - 1.2f; players[11].z = 0f
                trainingMarkers[0].apply { x = PitchConstants.HALF_LENGTH - 0.5f; z = -2.6f; radius = 1.6f; active = true; isTargetGate = true }
                trainingMarkers[1].apply { x = PitchConstants.HALF_LENGTH - 0.5f; z = 2.6f; radius = 1.6f; active = true; isTargetGate = true }
            }
            TrainingDrillType.DRIBBLING -> {
                striker.x = 4f; striker.z = 0f
                ball.ownerIndex = striker.index
                for (i in 0 until 6) {
                    trainingMarkers[i].apply {
                        x = 10f + i * 6.5f
                        z = if (i % 2 == 0) -4.5f else 4.5f
                        radius = 2.1f
                        active = true
                        isTargetGate = false
                    }
                }
            }
            TrainingDrillType.FREE_KICK -> {
                striker.x = 28f; striker.z = 3f; striker.facingRad = 0f
                ball.ownerIndex = striker.index
                players[11].x = PitchConstants.HALF_LENGTH - 1.0f; players[11].z = -1.2f
                // 4-player wall
                for (i in 0..3) {
                    players[12 + i].x = 38f
                    players[12 + i].z = -1.5f + i * 1.1f
                }
            }
            TrainingDrillType.PENALTY -> {
                striker.x = PitchConstants.HALF_LENGTH - 12.2f; striker.z = 0f; striker.facingRad = 0f
                ball.x = PitchConstants.HALF_LENGTH - 11f; ball.z = 0f
                ball.ownerIndex = striker.index
                players[11].x = PitchConstants.HALF_LENGTH - 0.8f; players[11].z = 0f
            }
        }
    }

    private fun stepTraining(dt: Float) {
        val drill = config.trainingDrill ?: TrainingDrillType.SHOOTING
        trainingTimeLeft -= dt
        if (trainingTimeLeft <= 0f) {
            trainingTimeLeft = 0f
            phase = MatchPhase.TRAINING_COMPLETE
            audioManager.playWhistleFullTime()
            emitHudSnapshot()
            return
        }

        val striker = players[9]
        playerController.updateControlledPlayer(
            player = striker,
            allPlayers = players,
            ball = ball,
            input = input,
            dt = dt,
            onShotTaken = { _, _ -> },
            onPassAttempted = { _ -> },
            onTackleAttempted = { _ -> }
        )

        if (drill == TrainingDrillType.SHOOTING || drill == TrainingDrillType.FREE_KICK || drill == TrainingDrillType.PENALTY) {
            goalkeeperAI.updateGoalkeeper(players[11], players, ball, config.difficulty, dt) {}
        }

        val evt = ballPhysics.step(ball, players, dt)

        when (drill) {
            TrainingDrillType.DRIBBLING -> {
                if (ball.ownerIndex == -1 && hypot(ball.x - striker.x, ball.z - striker.z) < 1.2f) {
                    ball.ownerIndex = striker.index
                }
                for (m in trainingMarkers) {
                    if (m.active && hypot(ball.x - m.x, ball.z - m.z) < m.radius) {
                        m.active = false
                        trainingScore++
                        audioManager.playUiClick()
                        showToast("Gate Cleared! ($trainingScore/6)")
                    }
                }
                if (trainingMarkers.none { it.active }) {
                    phase = MatchPhase.TRAINING_COMPLETE
                    audioManager.playGoalCelebration()
                }
            }
            TrainingDrillType.PASSING -> {
                for (m in trainingMarkers) {
                    if (m.active && ball.ownerIndex == -1 && hypot(ball.x - m.x, ball.z - m.z) < m.radius) {
                        trainingScore++
                        audioManager.playPass()
                        showToast("Target Hit! +1")
                        m.z = -m.z
                        striker.x = 0f; striker.z = 0f
                        ball.ownerIndex = striker.index
                        ball.vx = 0f; ball.vz = 0f
                    }
                }
                if (evt is BallEvent.OutOfBounds) {
                    striker.x = 0f; striker.z = 0f
                    ball.ownerIndex = striker.index
                }
            }
            else -> {
                if (evt is BallEvent.GoalScored && evt.scoredByHome) {
                    trainingScore++
                    netRippleRight = 1f
                    audioManager.playGoalCelebration()
                    showToast("Great Finish! ($trainingScore)")
                    resetTrainingShotPosition(drill)
                } else if (evt is BallEvent.OutOfBounds ||
                    (ball.ownerIndex == players[11].index) ||
                    (ball.ownerIndex == -1 && hypot(ball.vx, ball.vz) < 0.8f && hypot(ball.x - striker.x, ball.z - striker.z) > 6f)
                ) {
                    resetTrainingShotPosition(drill)
                }
            }
        }

        cameraController.update(ball.x, ball.y, ball.z, userCameraMode, isPenalty = (drill == TrainingDrillType.PENALTY), dt = dt)
    }

    private fun resetTrainingShotPosition(drill: TrainingDrillType) {
        val striker = players[9]
        when (drill) {
            TrainingDrillType.SHOOTING -> {
                striker.x = 27f + Random.nextFloat() * 6f
                striker.z = (Random.nextFloat() * 2f - 1f) * 10f
            }
            TrainingDrillType.FREE_KICK -> {
                striker.x = 28f
                striker.z = (Random.nextFloat() * 2f - 1f) * 8f
                for (i in 0..3) {
                    players[12 + i].x = 38f
                    players[12 + i].z = striker.z * 0.45f - 1.5f + i * 1.1f
                }
            }
            TrainingDrillType.PENALTY -> {
                striker.x = PitchConstants.HALF_LENGTH - 12f
                striker.z = 0f
            }
            else -> Unit
        }
        striker.vx = 0f; striker.vz = 0f; striker.facingRad = 0f
        ball.x = striker.x + 1f; ball.z = striker.z; ball.y = PitchConstants.BALL_RADIUS
        ball.vx = 0f; ball.vy = 0f; ball.vz = 0f
        ball.ownerIndex = striker.index
    }

    private fun showToast(msg: String) {
        eventToast = msg
        eventToastTimer = 1.9f
    }

    fun getCurrentGoalResult(): Pair<Int, Int> = homeScore to awayScore
    fun getTrainingFinalScore(): Int = trainingScore

    private fun emitHudSnapshot() {
        val totalTicks = (homePossessionTicks + awayPossessionTicks).coerceAtLeast(1)
        val homePoss = ((homePossessionTicks * 100f) / totalTicks).toInt().coerceIn(20, 80)

        // Format clock as standard 00:00 -> 90:00 scaled to configured duration
        val displayGameSec = ((matchTimeSeconds / totalMatchSeconds.coerceAtLeast(1f)) * 90f * 60f).toInt()
        val mins = (displayGameSec / 60).coerceAtMost(120)
        val secs = displayGameSec % 60
        val clockStr = "%02d:%02d".format(mins, secs)

        val halfStr = when (phase) {
            MatchPhase.PRE_KICKOFF, MatchPhase.FIRST_HALF -> "1ST"
            MatchPhase.HALF_TIME -> "HT"
            MatchPhase.SECOND_HALF -> "2ND"
            MatchPhase.EXTRA_TIME_1 -> "ET1"
            MatchPhase.EXTRA_TIME_2 -> "ET2"
            MatchPhase.PENALTY_SHOOTOUT -> "PEN"
            MatchPhase.FULL_TIME -> "FT"
            MatchPhase.GOAL_CELEBRATION -> "GOAL"
            MatchPhase.REPLAY -> "REPLAY"
            else -> "DRILL"
        }

        val ctrl = players[controlledIndex]
        val dots = players.map { p ->
            RadarDot(
                xNorm = (p.x / PitchConstants.HALF_LENGTH).coerceIn(-1f, 1f),
                zNorm = (p.z / PitchConstants.HALF_WIDTH).coerceIn(-1f, 1f),
                isHome = p.isHome,
                isControlled = p.index == controlledIndex
            )
        }

        _hudState.value = HudSnapshot(
            phase = phase,
            mode = config.mode,
            homeName = config.homeTeam.name,
            awayName = config.awayTeam.name,
            homeCode = config.homeTeam.shortCode,
            awayCode = config.awayTeam.shortCode,
            homeBadgePrimary = config.homeTeam.badgePrimary,
            awayBadgePrimary = config.awayTeam.badgePrimary,
            homeScore = homeScore,
            awayScore = awayScore,
            formattedClock = clockStr,
            halfLabel = halfStr,
            activePlayerName = ctrl.profile.name,
            activePlayerNumber = ctrl.profile.number,
            activePlayerStamina = ctrl.stamina,
            userHasPossession = ball.ownerIndex in 0..10,
            shootCharge = input.shootChargeProgress,
            goalBannerText = goalBannerText,
            eventToast = eventToast,
            stats = MatchStatsSnapshot(
                homePossessionPct = homePoss,
                homeShots = homeShots,
                awayShots = awayShots,
                homeOnTarget = homeOnTarget,
                awayOnTarget = awayOnTarget,
                homePasses = homePasses,
                awayPasses = awayPasses,
                homeTackles = homeTackles,
                awayTackles = awayTackles
            ),
            cameraMode = userCameraMode,
            lighting = currentLighting,
            isPaused = isPaused,
            radarDots = dots,
            ballRadarX = (ball.x / PitchConstants.HALF_LENGTH).coerceIn(-1f, 1f),
            ballRadarZ = (ball.z / PitchConstants.HALF_WIDTH).coerceIn(-1f, 1f),
            penaltyHomeKicks = penHomeKicks.toList(),
            penaltyAwayKicks = penAwayKicks.toList(),
            penaltyIsUserTurn = penUserTurn,
            trainingDrill = config.trainingDrill,
            trainingScore = trainingScore,
            trainingTarget = config.trainingDrill?.targetScore ?: 5,
            trainingTimeLeftSec = trainingTimeLeft.toInt(),
            trainingPrompt = trainingPrompt
        )
    }
}
