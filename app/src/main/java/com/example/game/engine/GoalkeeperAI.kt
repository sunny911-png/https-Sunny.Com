package com.example.game.engine

import com.example.game.model.DifficultyLevel
import com.example.game.systems.AudioManager
import kotlin.math.*

class GoalkeeperAI(
    private val playerController: PlayerController,
    private val ballController: BallController,
    private val audioManager: AudioManager
) {

    fun updateGoalkeeper(
        gk: SimPlayer,
        allPlayers: Array<SimPlayer>,
        ball: SimBall,
        difficulty: DifficultyLevel,
        dt: Float,
        onPassAttempted: (isHome: Boolean) -> Unit
    ) {
        val goalX = if (gk.isHome) -PitchConstants.HALF_LENGTH else PitchConstants.HALF_LENGTH
        val forwardDir = if (gk.isHome) 1f else -1f

        if (gk.diveTimer > 0f) {
            gk.diveTimer = (gk.diveTimer - dt).coerceAtLeast(0f)
            gk.vz = gk.diveDirZ * 6.8f
            gk.vx *= 0.85f
            playerController.applyMovement(gk, dt)
            checkSaveContact(gk, ball, difficulty)
            return
        }

        // If GK owns the ball, distribute quickly to an open defender/midfielder
        if (ball.ownerIndex == gk.index) {
            if (gk.aiDecisionCooldown <= 0f) {
                val target = findDistributionTarget(gk, allPlayers, forwardDir)
                if (target != null) {
                    val dist = hypot(target.x - gk.x, target.z - gk.z)
                    ballController.releaseForPass(
                        ball,
                        gk,
                        target.x,
                        target.z,
                        (16f + dist * 0.25f).coerceIn(16f, 25f),
                        loftVy = 2.6f
                    )
                    audioManager.playKick(0.55f)
                    onPassAttempted(gk.isHome)
                }
                gk.aiDecisionCooldown = 0.7f
            } else {
                gk.aiDecisionCooldown -= dt
            }
            gk.vx = 0f
            gk.vz = 0f
            return
        }

        // Track ball angle along the goal line
        val distBallToGoal = abs(ball.x - goalX)
        val stepOut = if (distBallToGoal < 22f) 1.5f + ((22f - distBallToGoal) * 0.14f).coerceAtMost(2.5f) else 1.2f
        val targetX = goalX + forwardDir * stepOut
        val targetZ = (ball.z * 0.34f).coerceIn(-PitchConstants.GOAL_HALF_WIDTH + 0.5f, PitchConstants.GOAL_HALF_WIDTH - 0.5f)

        // Check incoming shot trajectory for dive
        val ballHeadingToGoal = if (gk.isHome) ball.vx < -9.5f else ball.vx > 9.5f
        if (ball.ownerIndex == -1 && ballHeadingToGoal && distBallToGoal < 22f) {
            val timeToGoal = abs((goalX - ball.x) / ball.vx.coerceAtLeast(0.1f).let {
                if (abs(ball.vx) < 0.1f) 10f else abs(ball.vx)
            })
            val predictedZ = (ball.z + ball.vz * timeToGoal).coerceIn(-PitchConstants.GOAL_HALF_WIDTH, PitchConstants.GOAL_HALF_WIDTH)
            val diffZ = predictedZ - gk.z
            if (abs(diffZ) > 0.75f && timeToGoal < 0.85f) {
                gk.diveTimer = 0.42f
                gk.diveDirZ = sign(diffZ) * difficulty.gkReflexMult.coerceIn(0.75f, 1.15f)
                return
            }
        }

        val dx = targetX - gk.x
        val dz = targetZ - gk.z
        val dist = hypot(dx, dz)
        val gkSpeed = 6.2f * difficulty.gkReflexMult.coerceIn(0.8f, 1.12f)
        if (dist > 0.25f) {
            gk.vx = (dx / dist) * gkSpeed.coerceAtMost(dist * 6f)
            gk.vz = (dz / dist) * gkSpeed.coerceAtMost(dist * 6f)
        } else {
            gk.vx = 0f
            gk.vz = 0f
        }
        gk.facingRad = atan2(ball.z - gk.z, ball.x - gk.x)
        playerController.applyMovement(gk, dt)
        checkSaveContact(gk, ball, difficulty)
    }

    private fun checkSaveContact(gk: SimPlayer, ball: SimBall, difficulty: DifficultyLevel) {
        if (ball.ownerIndex != -1 || ball.pickupCooldown > 0f) return
        val dist = hypot(ball.x - gk.x, ball.z - gk.z)
        val reach = if (gk.diveTimer > 0f) 1.75f else 1.25f
        if (dist < reach && ball.y < PitchConstants.GOAL_HEIGHT + 0.3f) {
            val ballSpeed = hypot(ball.vx, ball.vz)
            if (ballSpeed > 18f && difficulty.gkReflexMult < 1.0f) {
                // Parry wide
                val outX = if (gk.isHome) 1f else -1f
                val outZ = if (gk.z >= 0f) 0.7f else -0.7f
                ballController.deflectShot(ball, gk, outX, outZ, ballSpeed * 0.42f)
                audioManager.playKick(0.45f)
            } else {
                // Clean catch!
                ball.ownerIndex = gk.index
                ball.vx = 0f; ball.vy = 0f; ball.vz = 0f
                ball.y = PitchConstants.BALL_RADIUS
                ball.lastTouchIsHome = gk.isHome
                ball.lastTouchName = gk.profile.name
                gk.aiDecisionCooldown = 0.65f
                audioManager.playPass()
            }
        }
    }

    private fun findDistributionTarget(gk: SimPlayer, allPlayers: Array<SimPlayer>, forwardDir: Float): SimPlayer? {
        var best: SimPlayer? = null
        var bestDist = Float.MAX_VALUE
        for (p in allPlayers) {
            if (p.isHome != gk.isHome || p.index == gk.index) continue
            val d = hypot(p.x - gk.x, p.z - gk.z)
            if (d in 10f..36f && (p.x - gk.x) * forwardDir > 6f && d < bestDist) {
                bestDist = d
                best = p
            }
        }
        return best
    }
}
