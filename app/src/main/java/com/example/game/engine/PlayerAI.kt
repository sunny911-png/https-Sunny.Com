package com.example.game.engine

import com.example.game.model.DifficultyLevel
import com.example.game.model.PlayerRole
import com.example.game.systems.AudioManager
import kotlin.math.*
import kotlin.random.Random

class PlayerAI(
    private val playerController: PlayerController,
    private val ballController: BallController,
    private val audioManager: AudioManager
) {

    fun updateAIPlayer(
        player: SimPlayer,
        allPlayers: Array<SimPlayer>,
        ball: SimBall,
        difficulty: DifficultyLevel,
        controlledIndex: Int,
        dt: Float,
        onShotTaken: (isHome: Boolean, onTarget: Boolean) -> Unit,
        onPassAttempted: (isHome: Boolean) -> Unit,
        onTackleAttempted: (isHome: Boolean) -> Unit
    ) {
        if (player.index == controlledIndex || player.isGoalkeeper) return
        if (player.stunTimer > 0f) {
            player.stunTimer = (player.stunTimer - dt).coerceAtLeast(0f)
            player.vx *= 0.85f
            player.vz *= 0.85f
            playerController.applyMovement(player, dt)
            return
        }

        if (player.aiDecisionCooldown > 0f) {
            player.aiDecisionCooldown = (player.aiDecisionCooldown - dt).coerceAtLeast(0f)
        }

        val attackDir = if (player.isHome) 1f else -1f
        val opponentGoalX = attackDir * PitchConstants.HALF_LENGTH
        val teamHasBall = ball.ownerIndex in allPlayers.indices && allPlayers[ball.ownerIndex].isHome == player.isHome

        // 1. If this AI player currently possesses the ball
        if (ball.ownerIndex == player.index) {
            updateBallCarrierAI(player, allPlayers, ball, difficulty, opponentGoalX, attackDir, dt, onShotTaken, onPassAttempted)
            playerController.applyMovement(player, dt)
            return
        }

        // 2. Determine tactical target position
        val ballShiftX = ball.x * 0.48f
        val formationPush = if (teamHasBall) attackDir * 10.5f else -attackDir * 7.5f
        var targetX = (player.anchorX + ballShiftX * 0.45f + formationPush).coerceIn(-46f, 46f)
        var targetZ = (player.anchorZ + ball.z * 0.18f).coerceIn(-31f, 31f)
        var moveSpeed = (5.4f + (player.profile.attributes.pace / 100f) * 1.4f) *
            if (player.isHome) 0.96f else difficulty.aiSpeedMult

        // Should this AI player press/chase the ball?
        val isClosestChaser = isNearestOutfieldTeammateToBall(player, allPlayers, ball, controlledIndex)

        if (!teamHasBall && isClosestChaser) {
            // Defend / intercept ball carrier or loose ball
            targetX = ball.x
            targetZ = ball.z
            moveSpeed *= 1.15f

            val distToBall = hypot(ball.x - player.x, ball.z - player.z)
            if (!player.isHome && distToBall < 1.85f && ball.ownerIndex != -1 && player.aiDecisionCooldown <= 0f) {
                player.aiDecisionCooldown = difficulty.aiReactionSeconds + 0.55f
                player.tackleTimer = 0.24f
                onTackleAttempted(false)
            }
        } else if (teamHasBall && ball.ownerIndex != -1) {
            // Attacking support & forward runs
            val carrier = allPlayers[ball.ownerIndex]
            when (player.profile.role) {
                PlayerRole.FWD -> {
                    // Make penetrative forward run toward opponent penalty area
                    targetX = (carrier.x + attackDir * 18f).coerceIn(-44f, 44f)
                    targetZ = (player.anchorZ * 0.8f).coerceIn(-24f, 24f)
                    moveSpeed *= 1.12f
                }
                PlayerRole.MID -> {
                    // Support triangle slightly ahead or beside carrier
                    targetX = (carrier.x + attackDir * 9f).coerceIn(-42f, 42f)
                    targetZ = (player.anchorZ * 0.75f + ball.z * 0.25f).coerceIn(-28f, 28f)
                }
                else -> Unit
            }
        } else if (ball.ownerIndex == -1 && isClosestChaser && !player.isHome) {
            // Loose ball chase for opponent
            targetX = ball.x
            targetZ = ball.z
            moveSpeed *= 1.15f
        }

        val dx = targetX - player.x
        val dz = targetZ - player.z
        val dist = hypot(dx, dz)
        if (dist > 0.45f) {
            val nx = dx / dist
            val nz = dz / dist
            val desiredSpeed = if (dist < 2.0f) moveSpeed * (dist / 2.0f) else moveSpeed
            val accel = 10f * dt
            player.vx += (nx * desiredSpeed - player.vx) * accel.coerceAtMost(1f)
            player.vz += (nz * desiredSpeed - player.vz) * accel.coerceAtMost(1f)
            player.facingRad = atan2(nz, nx)
        } else {
            player.vx *= 0.8f
            player.vz *= 0.8f
            player.facingRad = atan2(ball.z - player.z, ball.x - player.x)
        }

        playerController.applyMovement(player, dt)
    }

    private fun updateBallCarrierAI(
        player: SimPlayer,
        allPlayers: Array<SimPlayer>,
        ball: SimBall,
        difficulty: DifficultyLevel,
        opponentGoalX: Float,
        attackDir: Float,
        dt: Float,
        onShotTaken: (isHome: Boolean, onTarget: Boolean) -> Unit,
        onPassAttempted: (isHome: Boolean) -> Unit
    ) {
        val distToGoal = hypot(opponentGoalX - player.x, 0f - player.z)
        val speed = (5.4f + (player.profile.attributes.pace / 100f) * 1.3f) * difficulty.aiSpeedMult

        // Check nearest opponent pressure
        var nearestDefDist = Float.MAX_VALUE
        for (other in allPlayers) {
            if (other.isHome != player.isHome) {
                val d = hypot(other.x - player.x, other.z - player.z)
                if (d < nearestDefDist) nearestDefDist = d
            }
        }

        // Shoot if within dangerous range!
        if (distToGoal < 23f && player.aiDecisionCooldown <= 0f) {
            val aimSpread = (1f - difficulty.aiShotAccuracy) * 4.2f
            val aimZ = ((Random.nextFloat() * 2f - 1f) * (2.9f + aimSpread)).coerceIn(-4.2f, 4.2f)
            val power = (0.62f + Random.nextFloat() * 0.34f).coerceIn(0.55f, 0.98f)
            ballController.releaseForShot(ball, player, opponentGoalX, aimZ, power)
            audioManager.playKick(power)
            onShotTaken(player.isHome, abs(aimZ) <= PitchConstants.GOAL_HALF_WIDTH)
            player.aiDecisionCooldown = 0.6f
            return
        }

        // Pass under pressure or when a forward teammate is in great open space
        if ((nearestDefDist < 4.2f || Random.nextFloat() < 0.025f) && player.aiDecisionCooldown <= 0f) {
            val teammate = findOpenForwardTeammate(player, allPlayers, attackDir)
            if (teammate != null) {
                val passDist = hypot(teammate.x - player.x, teammate.z - player.z)
                val passSpeed = (14.5f + passDist * 0.35f).coerceIn(14f, 24f)
                ballController.releaseForPass(ball, player, teammate.x, teammate.z, passSpeed)
                audioManager.playPass()
                onPassAttempted(player.isHome)
                player.aiDecisionCooldown = difficulty.aiReactionSeconds + 0.45f
                return
            }
            player.aiDecisionCooldown = difficulty.aiReactionSeconds + 0.25f
        }

        // Dribble toward opponent goal while weaving slightly away from defenders
        val gx = opponentGoalX - player.x
        val gz = (0f - player.z) * 0.45f
        val len = hypot(gx, gz).coerceAtLeast(0.1f)
        val nx = gx / len
        val nz = gz / len
        player.vx += (nx * speed - player.vx) * (9f * dt).coerceAtMost(1f)
        player.vz += (nz * speed - player.vz) * (9f * dt).coerceAtMost(1f)
        player.facingRad = atan2(nz, nx)
    }

    private fun findOpenForwardTeammate(
        passer: SimPlayer,
        allPlayers: Array<SimPlayer>,
        attackDir: Float
    ): SimPlayer? {
        var best: SimPlayer? = null
        var bestScore = -Float.MAX_VALUE
        for (p in allPlayers) {
            if (p.isHome != passer.isHome || p.index == passer.index || p.isGoalkeeper) continue
            val dx = p.x - passer.x
            val dz = p.z - passer.z
            val dist = hypot(dx, dz)
            if (dist < 4f || dist > 34f) continue
            val forwardGain = dx * attackDir
            val score = forwardGain * 0.8f - dist * 0.25f
            if (score > bestScore) {
                bestScore = score
                best = p
            }
        }
        return best
    }

    private fun isNearestOutfieldTeammateToBall(
        player: SimPlayer,
        allPlayers: Array<SimPlayer>,
        ball: SimBall,
        controlledIndex: Int
    ): Boolean {
        val myDist = hypot(player.x - ball.x, player.z - ball.z)
        for (other in allPlayers) {
            if (other.isHome != player.isHome || other.isGoalkeeper || other.index == player.index) continue
            if (other.index == controlledIndex) {
                // Only beat the controlled user player if user is far away
                val ctrlDist = hypot(other.x - ball.x, other.z - ball.z)
                if (ctrlDist < myDist + 2.5f) return false
            } else {
                val od = hypot(other.x - ball.x, other.z - ball.z)
                if (od < myDist) return false
            }
        }
        return true
    }
}
