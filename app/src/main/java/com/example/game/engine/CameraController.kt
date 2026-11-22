package com.example.game.engine

import com.example.game.model.CameraMode
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class CameraController {

    var eyeX = 0f; private set
    var eyeY = 26f; private set
    var eyeZ = 44f; private set

    var lookX = 0f; private set
    var lookY = 0f; private set
    var lookZ = 0f; private set

    private var shakeTrauma = 0f
    private var celebrationOrbitAngle = 0.6f

    fun snapToBall(ball: SimBall, mode: CameraMode) {
        val (tx, ty, tz, lx, ly, lz) = targetPose(ball.x, ball.y, ball.z, mode, isPenalty = false, dt = 0f)
        eyeX = tx; eyeY = ty; eyeZ = tz
        lookX = lx; lookY = ly; lookZ = lz
    }

    fun addShake(amount: Float) {
        shakeTrauma = (shakeTrauma + amount).coerceAtMost(1.0f)
    }

    fun update(
        ballX: Float,
        ballY: Float,
        ballZ: Float,
        mode: CameraMode,
        isPenalty: Boolean,
        dt: Float
    ) {
        val (tx, ty, tz, lx, ly, lz) = targetPose(ballX, ballY, ballZ, mode, isPenalty, dt)
        val smooth = (1f - kotlin.math.exp(-6.5f * dt)).coerceIn(0.05f, 1f)
        eyeX += (tx - eyeX) * smooth
        eyeY += (ty - eyeY) * smooth
        eyeZ += (tz - eyeZ) * smooth
        lookX += (lx - lookX) * smooth
        lookY += (ly - lookY) * smooth
        lookZ += (lz - lookZ) * smooth

        if (shakeTrauma > 0.001f) {
            val mag = shakeTrauma * shakeTrauma * 0.55f
            eyeX += (Random.nextFloat() * 2f - 1f) * mag
            eyeY += (Random.nextFloat() * 2f - 1f) * mag
            shakeTrauma = (shakeTrauma - dt * 2.4f).coerceAtLeast(0f)
        }
    }

    private data class CamPose(
        val ex: Float, val ey: Float, val ez: Float,
        val lx: Float, val ly: Float, val lz: Float
    )

    private fun targetPose(
        ballX: Float,
        ballY: Float,
        ballZ: Float,
        mode: CameraMode,
        isPenalty: Boolean,
        dt: Float
    ): CamPose {
        if (isPenalty) {
            return CamPose(
                ex = 25.0f,
                ey = 5.4f,
                ez = 0f,
                lx = 52.5f,
                ly = 1.3f,
                lz = 0f
            )
        }

        return when (mode) {
            CameraMode.BROADCAST -> {
                val clampedX = ballX.coerceIn(-44f, 44f)
                val clampedZ = ballZ.coerceIn(-28f, 28f)
                CamPose(
                    ex = clampedX * 0.72f,
                    ey = 25.5f + (clampedZ * -0.10f),
                    ez = 43.0f + clampedZ * 0.30f,
                    lx = clampedX * 0.88f,
                    ly = 0.4f + (ballY * 0.25f),
                    lz = clampedZ * 0.36f
                )
            }
            CameraMode.BALL_FOLLOW -> {
                val clampedX = ballX.coerceIn(-46f, 46f)
                val clampedZ = ballZ.coerceIn(-29f, 29f)
                CamPose(
                    ex = clampedX - 11.5f,
                    ey = 14.5f,
                    ez = clampedZ * 0.62f + 22.0f,
                    lx = clampedX + 4.5f,
                    ly = 0.8f + ballY * 0.35f,
                    lz = clampedZ * 0.75f
                )
            }
            CameraMode.GOAL_CELEBRATION -> {
                celebrationOrbitAngle += dt * 0.9f
                val r = 11.5f
                CamPose(
                    ex = ballX + cos(celebrationOrbitAngle) * r,
                    ey = 4.6f,
                    ez = ballZ + sin(celebrationOrbitAngle) * r,
                    lx = ballX,
                    ly = 1.4f,
                    lz = ballZ
                )
            }
            CameraMode.REPLAY -> {
                CamPose(
                    ex = ballX * 0.85f - 6f,
                    ey = 10.5f,
                    ez = ballZ * 0.5f + 22.0f,
                    lx = ballX,
                    ly = 0.8f + ballY * 0.4f,
                    lz = ballZ
                )
            }
        }
    }
}
