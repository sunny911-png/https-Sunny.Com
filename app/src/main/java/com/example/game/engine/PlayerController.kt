package com.example.game.engine

import com.example.game.systems.AudioManager
import com.example.game.systems.MobileInputManager
import kotlin.math.*

class PlayerController(
    private val ballController: BallController,
    private val audioManager: AudioManager
) {

    fun updateControlledPlayer(
        player: SimPlayer,
        allPlayers: Array<SimPlayer>,
        ball: SimBall,
        input: MobileInputManager,
        dt: Float,
        onShotTaken: (isHome: Boolean, onTarget: Boolean) -> Unit,
        onPassAttempted: (isHome: Boolean) -> Unit,
        onTackleAttempted: (isHome: Boolean) -> Unit
    ) {
        if (player.stunTimer > 0f) {
            player.stunTimer = (player.stunTimer - dt).coerceAtLeast(0f)
            player.vx *= 0.85f
            player.vz *= 0.85f
            applyMovement(player, dt)
            return
        }

        val joyMag = sqrt(input.moveX * input.moveX + input.moveZ * input.moveZ).coerceAtMost(1f)
        val hasBall = ball.ownerIndex == player.index

        val paceSkill = player.profile.attributes.pace / 100f
        val dribbleSkill = player.profile.attributes.dribbling / 100f
        val baseWalkSpeed = 5.4f + paceSkill * 1.4f
        val sprintSpeed = if (input.isSprinting && player.stamina > 0.08f) {
            1.38f
        } else {
            1.0f
        }
        val dribbleFactor = if (hasBall) (0.87f + dribbleSkill * 0.08f) else 1.0f

        if (player.tackleTimer <= 0f) {
            if (joyMag > 0.06f) {
                val targetSpeed = baseWalkSpeed * sprintSpeed * dribbleFactor * joyMag
                val nx = input.moveX / joyMag
                val nz = input.moveZ / joyMag
                val targetVx = nx * targetSpeed
                val targetVz = nz * targetSpeed
                val accel = 14f * dt
                player.vx += (targetVx - player.vx) * accel.coerceAtMost(1f)
                player.vz += (targetVz - player.vz) * accel.coerceAtMost(1f)
                player.facingRad = atan2(nz, nx)

                if (input.isSprinting && joyMag > 0.35f) {
                    player.stamina = (player.stamina - dt * 0.16f).coerceAtLeast(0f)
                } else {
                    player.stamina = (player.stamina + dt * 0.09f).coerceAtMost(1f)
                }
            } else {
                player.vx *= (1f - 10f * dt).coerceAtLeast(0f)
                player.vz *= (1f - 10f * dt).coerceAtLeast(0f)
                player.stamina = (player.stamina + dt * 0.16f).coerceAtMost(1f)
            }
        }

        // Check header / volley when ball is airborne close by
        val distToBall = hypot(ball.x - player.x, ball.z - player.z)
        val canHeader = !hasBall && ball.ownerIndex == -1 && distToBall < 1.85f && ball.y in 0.7f..2.2f

        // Consume queued actions
        if (input.consumePass()) {
            if (hasBall || canHeader) {
                val target = findBestPassTarget(player, allPlayers, input.moveX, input.moveZ, preferForward = false)
                if (target != null) {
                    val passDist = hypot(target.x - player.x, target.z - player.z)
                    val speed = (14.5f + passDist * 0.36f).coerceIn(14f, 26f)
                    ballController.releaseForPass(ball, player, target.x, target.z, speed, loftVy = if (canHeader) 1.5f else 0.75f)
                } else {
                    val tx = player.x + cos(player.facingRad) * 16f
                    val tz = player.z + sin(player.facingRad) * 16f
                    ballController.releaseForPass(ball, player, tx, tz, 18f)
                }
                audioManager.playPass()
                onPassAttempted(player.isHome)
            }
        }

        if (input.consumeLongPass()) {
            if (hasBall || canHeader) {
                val target = findBestPassTarget(player, allPlayers, input.moveX, input.moveZ, preferForward = true)
                val tx = target?.x ?: (player.x + cos(player.facingRad) * 28f)
                val tz = target?.z ?: (player.z + sin(player.facingRad) * 28f)
                ballController.releaseForPass(ball, player, tx, tz, 23f, loftVy = 6.8f)
                audioManager.playKick(0.7f)
                onPassAttempted(player.isHome)
            }
        }

        if (input.consumeThrough()) {
            if (hasBall) {
                val runner = findBestPassTarget(player, allPlayers, input.moveX, input.moveZ, preferForward = true)
                if (runner != null) {
                    val leadX = (runner.x + 8.5f).coerceAtMost(PitchConstants.HALF_LENGTH - 3f)
                    val leadZ = runner.z + runner.vz * 0.7f
                    val passDist = hypot(leadX - player.x, leadZ - player.z)
                    val speed = (16f + passDist * 0.34f).coerceIn(16f, 27f)
                    ballController.releaseForPass(ball, player, leadX, leadZ, speed, loftVy = 1.2f)
                } else {
                    val tx = (player.x + 20f).coerceAtMost(PitchConstants.HALF_LENGTH - 2f)
                    val tz = player.z + input.moveZ * 10f
                    ballController.releaseForPass(ball, player, tx, tz, 21f, loftVy = 1.1f)
                }
                audioManager.playPass()
                onPassAttempted(player.isHome)
            }
        }

        if (input.consumeShoot()) {
            if (hasBall || canHeader) {
                val power = input.queuedShotPower
                val goalX = PitchConstants.HALF_LENGTH
                val aimZ = (input.moveZ * 3.55f).coerceIn(-3.55f, 3.55f)
                ballController.releaseForShot(ball, player, goalX, aimZ, power)
                audioManager.playKick(power)
                onShotTaken(player.isHome, abs(aimZ) <= PitchConstants.GOAL_HALF_WIDTH)
            }
        }

        if (input.consumeTackle()) {
            if (!hasBall && player.tackleTimer <= 0f) {
                player.tackleTimer = 0.28f
                val dx = ball.x - player.x
                val dz = ball.z - player.z
                val d = hypot(dx, dz)
                val dirAngle = if (d < 4.5f && d > 0.1f) atan2(dz, dx) else player.facingRad
                player.facingRad = dirAngle
                player.vx = cos(dirAngle) * 11.5f
                player.vz = sin(dirAngle) * 11.5f
                onTackleAttempted(player.isHome)
            }
        }

        applyMovement(player, dt)
    }

    fun applyMovement(player: SimPlayer, dt: Float) {
        player.x = (player.x + player.vx * dt).coerceIn(-PitchConstants.HALF_LENGTH - 1.5f, PitchConstants.HALF_LENGTH + 1.5f)
        player.z = (player.z + player.vz * dt).coerceIn(-PitchConstants.HALF_WIDTH - 1.5f, PitchConstants.HALF_WIDTH + 1.5f)
        val speed = hypot(player.vx, player.vz)
        if (speed > 0.25f) {
            player.stridePhase = (player.stridePhase + speed * dt * 2.1f) % (2f * Math.PI.toFloat())
        }
        if (player.kickTimer > 0f) player.kickTimer = (player.kickTimer - dt).coerceAtLeast(0f)
        if (player.tackleTimer > 0f) {
            player.tackleTimer = (player.tackleTimer - dt).coerceAtLeast(0f)
            if (player.tackleTimer <= 0f) {
                player.vx *= 0.4f
                player.vz *= 0.4f
            }
        }
        if (player.celebrateTimer > 0f) player.celebrateTimer = (player.celebrateTimer - dt).coerceAtLeast(0f)
    }

    fun findBestPassTarget(
        passer: SimPlayer,
        allPlayers: Array<SimPlayer>,
        joyX: Float,
        joyZ: Float,
        preferForward: Boolean
    ): SimPlayer? {
        val hasJoy = hypot(joyX, joyZ) > 0.18f
        val aimX = if (hasJoy) joyX else cos(passer.facingRad)
        val aimZ = if (hasJoy) joyZ else sin(passer.facingRad)
        val aimLen = hypot(aimX, aimZ).coerceAtLeast(0.01f)
        val nx = aimX / aimLen
        val nz = aimZ / aimLen

        var best: SimPlayer? = null
        var bestScore = -Float.MAX_VALUE
        for (candidate in allPlayers) {
            if (candidate.index == passer.index || candidate.isHome != passer.isHome) continue
            val dx = candidate.x - passer.x
            val dz = candidate.z - passer.z
            val dist = hypot(dx, dz)
            if (dist < 2.5f || dist > 42f) continue
            val dot = (dx / dist) * nx + (dz / dist) * nz
            if (dot < -0.15f) continue
            val forwardBonus = if (preferForward) {
                val attackSign = if (passer.isHome) 1f else -1f
                (dx * attackSign) * 0.08f
            } else 0f
            val score = dot * 3.0f - (dist * 0.045f) + forwardBonus + if (candidate.isGoalkeeper) -1.6f else 0f
            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return best
    }

    fun pickBestSwitchPlayer(
        currentIdx: Int,
        allPlayers: Array<SimPlayer>,
        ball: SimBall,
        userIsHome: Boolean = true
    ): Int {
        var bestIdx = currentIdx
        var bestDist = Float.MAX_VALUE
        for (p in allPlayers) {
            if (p.isHome != userIsHome || p.isGoalkeeper || p.index == currentIdx) continue
            val d = hypot(p.x - ball.x, p.z - ball.z)
            if (d < bestDist) {
                bestDist = d
                bestIdx = p.index
            }
        }
        return bestIdx
    }
}
