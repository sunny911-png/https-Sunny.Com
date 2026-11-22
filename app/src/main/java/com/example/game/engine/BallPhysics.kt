package com.example.game.engine

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

sealed class BallEvent {
    data class GoalScored(val scoredByHome: Boolean) : BallEvent()
    data class OutOfBounds(val isGoalLine: Boolean, val x: Float, val z: Float, val lastTouchHome: Boolean) : BallEvent()
    object PostHit : BallEvent()
}

class BallPhysics {

    fun step(ball: SimBall, players: Array<SimPlayer>, dt: Float): BallEvent? {
        if (ball.pickupCooldown > 0f) {
            ball.pickupCooldown = (ball.pickupCooldown - dt).coerceAtLeast(0f)
        }

        // If owned by a player, keep tethered cleanly in front of player's feet
        if (ball.ownerIndex in players.indices) {
            val owner = players[ball.ownerIndex]
            val speed = sqrt(owner.vx * owner.vx + owner.vz * owner.vz)
            val leadDist = PitchConstants.PLAYER_RADIUS + PitchConstants.BALL_RADIUS + 0.18f + (speed * 0.025f)
            val tx = owner.x + cos(owner.facingRad) * leadDist
            val tz = owner.z + sin(owner.facingRad) * leadDist
            ball.x += (tx - ball.x) * (18f * dt).coerceAtMost(1f)
            ball.z += (tz - ball.z) * (18f * dt).coerceAtMost(1f)
            ball.y = PitchConstants.BALL_RADIUS
            ball.vx = owner.vx
            ball.vy = 0f
            ball.vz = owner.vz
            if (speed > 0.2f) {
                ball.rotAngleDeg = (ball.rotAngleDeg + speed * dt * 160f) % 360f
                ball.rotAxisX = -sin(owner.facingRad)
                ball.rotAxisZ = cos(owner.facingRad)
            }
            return checkGoalAndBounds(ball)
        }

        // Free-flight physics
        ball.vy -= 18.5f * dt
        // Subtle aerodynamic curve from spin
        ball.vx += ball.spinZ * dt * 0.45f
        ball.vz += ball.spinX * dt * 0.45f

        ball.x += ball.vx * dt
        ball.y += ball.vy * dt
        ball.z += ball.vz * dt

        // Ground bounce & turf rolling friction
        if (ball.y <= PitchConstants.BALL_RADIUS) {
            ball.y = PitchConstants.BALL_RADIUS
            if (ball.vy < -1.4f) {
                ball.vy = -ball.vy * 0.56f
            } else {
                ball.vy = 0f
            }
            val groundDrag = (1f - 1.35f * dt).coerceAtLeast(0.85f)
            ball.vx *= groundDrag
            ball.vz *= groundDrag
            ball.spinX *= 0.92f
            ball.spinZ *= 0.92f
        } else {
            val airDrag = (1f - 0.18f * dt).coerceAtLeast(0.95f)
            ball.vx *= airDrag
            ball.vz *= airDrag
        }

        val horizSpeed = sqrt(ball.vx * ball.vx + ball.vz * ball.vz)
        if (horizSpeed > 0.15f) {
            ball.rotAngleDeg = (ball.rotAngleDeg + horizSpeed * dt * 150f) % 360f
            val inv = 1f / horizSpeed
            ball.rotAxisX = -ball.vz * inv
            ball.rotAxisZ = ball.vx * inv
        }

        // Goalpost & Crossbar collisions
        val postHit = collideGoalPosts(ball)
        val event = checkGoalAndBounds(ball)
        if (event != null) return event
        if (postHit) return BallEvent.PostHit
        return null
    }

    private fun collideGoalPosts(ball: SimBall): Boolean {
        var hit = false
        val postRadius = 0.14f + PitchConstants.BALL_RADIUS
        val postXs = floatArrayOf(-PitchConstants.HALF_LENGTH, PitchConstants.HALF_LENGTH)
        val postZs = floatArrayOf(-PitchConstants.GOAL_HALF_WIDTH, PitchConstants.GOAL_HALF_WIDTH)

        for (px in postXs) {
            // Vertical posts
            if (ball.y <= PitchConstants.GOAL_HEIGHT + PitchConstants.BALL_RADIUS) {
                for (pz in postZs) {
                    val dx = ball.x - px
                    val dz = ball.z - pz
                    val dist = sqrt(dx * dx + dz * dz)
                    if (dist < postRadius && dist > 0.001f) {
                        val nx = dx / dist
                        val nz = dz / dist
                        ball.x = px + nx * postRadius
                        ball.z = pz + nz * postRadius
                        val dot = ball.vx * nx + ball.vz * nz
                        if (dot < 0f) {
                            ball.vx -= 1.75f * dot * nx
                            ball.vz -= 1.75f * dot * nz
                            hit = true
                        }
                    }
                }
            }
            // Crossbar
            if (abs(ball.z) <= PitchConstants.GOAL_HALF_WIDTH) {
                val dx = ball.x - px
                val dy = ball.y - PitchConstants.GOAL_HEIGHT
                val dist = sqrt(dx * dx + dy * dy)
                if (dist < postRadius && dist > 0.001f) {
                    val nx = dx / dist
                    val ny = dy / dist
                    ball.x = px + nx * postRadius
                    ball.y = PitchConstants.GOAL_HEIGHT + ny * postRadius
                    val dot = ball.vx * nx + ball.vy * ny
                    if (dot < 0f) {
                        ball.vx -= 1.72f * dot * nx
                        ball.vy -= 1.72f * dot * ny
                        hit = true
                    }
                }
            }
        }
        return hit
    }

    private fun checkGoalAndBounds(ball: SimBall): BallEvent? {
        val inGoalMouthZ = abs(ball.z) < (PitchConstants.GOAL_HALF_WIDTH - PitchConstants.BALL_RADIUS * 0.4f)
        val underCrossbar = ball.y < (PitchConstants.GOAL_HEIGHT - PitchConstants.BALL_RADIUS * 0.3f)

        // Goal scored at Away goal (+X) -> Home scores
        if (ball.x > PitchConstants.HALF_LENGTH + PitchConstants.BALL_RADIUS && inGoalMouthZ && underCrossbar) {
            // Dampen inside net
            if (ball.x > PitchConstants.HALF_LENGTH + PitchConstants.GOAL_DEPTH - PitchConstants.BALL_RADIUS) {
                ball.x = PitchConstants.HALF_LENGTH + PitchConstants.GOAL_DEPTH - PitchConstants.BALL_RADIUS
                ball.vx = -abs(ball.vx) * 0.25f
                ball.vz *= 0.4f
            }
            return BallEvent.GoalScored(scoredByHome = true)
        }

        // Goal scored at Home goal (-X) -> Away scores
        if (ball.x < -(PitchConstants.HALF_LENGTH + PitchConstants.BALL_RADIUS) && inGoalMouthZ && underCrossbar) {
            if (ball.x < -(PitchConstants.HALF_LENGTH + PitchConstants.GOAL_DEPTH - PitchConstants.BALL_RADIUS)) {
                ball.x = -(PitchConstants.HALF_LENGTH + PitchConstants.GOAL_DEPTH - PitchConstants.BALL_RADIUS)
                ball.vx = abs(ball.vx) * 0.25f
                ball.vz *= 0.4f
            }
            return BallEvent.GoalScored(scoredByHome = false)
        }

        // Touchlines (Z bounds)
        if (abs(ball.z) > PitchConstants.HALF_WIDTH + PitchConstants.BALL_RADIUS) {
            return BallEvent.OutOfBounds(
                isGoalLine = false,
                x = ball.x.coerceIn(-PitchConstants.HALF_LENGTH + 1f, PitchConstants.HALF_LENGTH - 1f),
                z = if (ball.z > 0f) PitchConstants.HALF_WIDTH - 0.6f else -(PitchConstants.HALF_WIDTH - 0.6f),
                lastTouchHome = ball.lastTouchIsHome
            )
        }

        // Goal lines (X bounds outside mouth)
        if (abs(ball.x) > PitchConstants.HALF_LENGTH + PitchConstants.BALL_RADIUS) {
            return BallEvent.OutOfBounds(
                isGoalLine = true,
                x = if (ball.x > 0f) PitchConstants.HALF_LENGTH - 0.5f else -(PitchConstants.HALF_LENGTH - 0.5f),
                z = ball.z.coerceIn(-PitchConstants.HALF_WIDTH + 1f, PitchConstants.HALF_WIDTH - 1f),
                lastTouchHome = ball.lastTouchIsHome
            )
        }

        return null
    }
}

class BallController {

    fun releaseForPass(
        ball: SimBall,
        passer: SimPlayer,
        targetX: Float,
        targetZ: Float,
        speed: Float,
        loftVy: Float = 0.8f
    ) {
        val dx = targetX - ball.x
        val dz = targetZ - ball.z
        val dist = sqrt(dx * dx + dz * dz).coerceAtLeast(0.1f)
        ball.ownerIndex = -1
        ball.pickupCooldown = 0.16f
        ball.lastTouchIsHome = passer.isHome
        ball.lastTouchName = passer.profile.name
        ball.vx = (dx / dist) * speed
        ball.vz = (dz / dist) * speed
        ball.vy = loftVy
        ball.spinX = 0f
        ball.spinZ = 0f
        passer.facingRad = atan2(dz, dx)
        passer.kickTimer = 0.28f
    }

    fun releaseForShot(
        ball: SimBall,
        shooter: SimPlayer,
        targetX: Float,
        targetZ: Float,
        powerRatio: Float
    ) {
        val dx = targetX - ball.x
        val dz = targetZ - ball.z
        val dist = sqrt(dx * dx + dz * dz).coerceAtLeast(0.1f)
        val shotSkill = shooter.profile.attributes.shooting / 100f
        val shotSpeed = 24f + powerRatio * (13f + shotSkill * 5f)
        val loft = 2.6f + powerRatio * 4.4f
        ball.ownerIndex = -1
        ball.pickupCooldown = 0.22f
        ball.lastTouchIsHome = shooter.isHome
        ball.lastTouchName = shooter.profile.name
        ball.vx = (dx / dist) * shotSpeed
        ball.vz = (dz / dist) * shotSpeed
        ball.vy = loft
        // Slight whip spin toward corner
        ball.spinZ = -shooter.vz * 0.25f
        ball.spinX = shooter.vx * 0.15f
        shooter.facingRad = atan2(dz, dx)
        shooter.kickTimer = 0.34f
    }

    fun deflectShot(ball: SimBall, gk: SimPlayer, dirX: Float, dirZ: Float, speed: Float) {
        ball.ownerIndex = -1
        ball.pickupCooldown = 0.24f
        ball.lastTouchIsHome = gk.isHome
        ball.lastTouchName = gk.profile.name
        val len = sqrt(dirX * dirX + dirZ * dirZ).coerceAtLeast(0.01f)
        ball.vx = (dirX / len) * speed
        ball.vz = (dirZ / len) * speed
        ball.vy = 4.2f
    }
}
