package com.example.game.render

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.game.engine.MatchEngine
import com.example.game.engine.PitchConstants
import com.example.game.engine.SimPlayer
import com.example.game.model.GraphicsQuality
import com.example.game.model.KitColors
import com.example.game.model.StadiumLighting
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

class StadiumGLRenderer(
    private val engine: MatchEngine,
    private val homeKit: KitColors,
    private val awayKit: KitColors,
    private var graphicsQuality: GraphicsQuality,
    private var targetFps: Int
) : GLSurfaceView.Renderer {

    private lateinit var shader: ShaderProgram
    private lateinit var boxMesh: MeshBuffer
    private lateinit var sphereMesh: MeshBuffer
    private lateinit var discMesh: MeshBuffer
    private lateinit var controlRingMesh: MeshBuffer
    private lateinit var targetRingMesh: MeshBuffer
    private lateinit var centerCircleMesh: MeshBuffer
    private lateinit var pitchStripeMesh: MeshBuffer
    private lateinit var outerTurfMesh: MeshBuffer
    private lateinit var pitchLinesMesh: MeshBuffer

    private val viewMatrix = FloatArray(16)
    private val projMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private var lastFrameNanos = 0L
    private var animTime = 0f

    fun updatePerformanceConfig(quality: GraphicsQuality, fps: Int) {
        graphicsQuality = quality
        targetFps = fps
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        shader = ShaderProgram()
        boxMesh = MeshBuilder.createBoxMesh(1f, 1f, 1f)
        sphereMesh = MeshBuilder.createSphereMesh(10, 14)
        discMesh = MeshBuilder.createDiscMesh(18)
        controlRingMesh = MeshBuilder.createRingMesh(0.75f, 0.98f, 28)
        targetRingMesh = MeshBuilder.createRingMesh(1.3f, 1.7f, 28)
        centerCircleMesh = MeshBuilder.createRingMesh(9.0f, 9.32f, 44)
        pitchStripeMesh = MeshBuilder.createGroundQuad(PitchConstants.HALF_LENGTH * 2f / 14f, PitchConstants.HALF_WIDTH * 2f)
        outerTurfMesh = MeshBuilder.createGroundQuad(136f, 92f)
        pitchLinesMesh = MeshBuilder.createPitchLinesMesh(PitchConstants.HALF_LENGTH, PitchConstants.HALF_WIDTH)

        lastFrameNanos = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / height.coerceAtLeast(1)
        Matrix.perspectiveM(projMatrix, 0, 44f, aspect, 1.0f, 280f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val rawDt = ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        lastFrameNanos = now
        animTime += rawDt

        // Frame rate pacing for 30 FPS mode
        if (targetFps <= 30) {
            val minFrameMs = 31L
            val elapsedMs = (System.nanoTime() - now) / 1_000_000L
            if (elapsedMs < minFrameMs) {
                try {
                    Thread.sleep((minFrameMs - elapsedMs).coerceAtMost(28L))
                } catch (_: InterruptedException) {
                }
            }
        }

        engine.stepSimulation(rawDt)

        val isEvening = engine.currentLighting == StadiumLighting.EVENING
        if (isEvening) {
            GLES20.glClearColor(0.04f, 0.06f, 0.11f, 1f)
        } else {
            GLES20.glClearColor(0.38f, 0.68f, 0.94f, 1f)
        }
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        shader.use()

        // Configure lighting uniforms
        if (isEvening) {
            GLES20.glUniform3f(shader.uLightDirHandle, 0.35f, 0.85f, 0.40f)
            GLES20.glUniform3f(shader.uLightColorHandle, 0.88f, 0.92f, 1.0f)
            GLES20.glUniform3f(shader.uAmbientColorHandle, 0.36f, 0.40f, 0.48f)
        } else {
            GLES20.glUniform3f(shader.uLightDirHandle, 0.45f, 0.88f, 0.30f)
            GLES20.glUniform3f(shader.uLightColorHandle, 1.0f, 0.97f, 0.88f)
            GLES20.glUniform3f(shader.uAmbientColorHandle, 0.48f, 0.50f, 0.52f)
        }

        val cam = engine.cameraController
        Matrix.setLookAtM(
            viewMatrix, 0,
            cam.eyeX, cam.eyeY, cam.eyeZ,
            cam.lookX, cam.lookY, cam.lookZ,
            0f, 1f, 0f
        )
        Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)

        drawPitchAndMarkings(isEvening)
        drawStadiumStandsAndFloodlights(isEvening)
        drawGoals()
        drawTrainingMarkers()
        drawShadows()
        drawPlayers()
        drawBall()
    }

    private fun drawPitchAndMarkings(isEvening: Boolean) {
        // Outer pitch apron
        drawBoxScaled(0f, -0.04f, 0f, 136f, 0.04f, 92f, 0.08f, 0.25f, 0.14f)

        // Mowed grass stripes
        val stripeCount = if (graphicsQuality.pitchStripesDetail) 14 else 2
        val totalLen = PitchConstants.HALF_LENGTH * 2f
        val stripeW = totalLen / stripeCount
        for (i in 0 until stripeCount) {
            val cx = -PitchConstants.HALF_LENGTH + stripeW * (i + 0.5f)
            val isLight = i % 2 == 0
            val r = if (isLight) 0.12f else 0.09f
            val g = if (isLight) (if (isEvening) 0.47f else 0.54f) else (if (isEvening) 0.39f else 0.46f)
            val b = if (isLight) 0.20f else 0.16f
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, cx, 0f, 0f)
            if (!graphicsQuality.pitchStripesDetail) {
                Matrix.scaleM(modelMatrix, 0, 7f, 1f, 1f)
            }
            Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
            shader.drawMesh(pitchStripeMesh, mvpMatrix, modelMatrix, r, g, b)
        }

        // White pitch markings & center circle
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        shader.drawMesh(pitchLinesMesh, mvpMatrix, modelMatrix, 0.94f, 0.97f, 0.95f)

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, 0f, 0.02f, 0f)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        shader.drawMesh(centerCircleMesh, mvpMatrix, modelMatrix, 0.94f, 0.97f, 0.95f)
    }

    private fun drawStadiumStandsAndFloodlights(isEvening: Boolean) {
        // Advertising boards along far & goal ends
        drawBoxScaled(0f, 0.55f, -PitchConstants.HALF_WIDTH - 3.2f, 102f, 1.1f, 0.4f, 0.12f, 0.55f, 0.35f)
        drawBoxScaled(-PitchConstants.HALF_LENGTH - 4.2f, 0.55f, 0f, 0.4f, 1.1f, 62f, 0.10f, 0.30f, 0.58f)
        drawBoxScaled(PitchConstants.HALF_LENGTH + 4.2f, 0.55f, 0f, 0.4f, 1.1f, 62f, 0.58f, 0.14f, 0.20f)

        // Far grandstand + Left & Right goal-end stands
        val rows = graphicsQuality.spectatorRows.coerceAtLeast(1)
        for (row in 0 until rows) {
            val y = 1.6f + row * 1.9f
            val distOut = row * 2.2f
            // Far stand (-Z)
            drawBoxScaled(0f, y, -PitchConstants.HALF_WIDTH - 7.5f - distOut, 118f, 1.8f, 2.1f, 0.14f, 0.18f, 0.25f)
            // Left & Right stands
            drawBoxScaled(-PitchConstants.HALF_LENGTH - 8.5f - distOut, y, 0f, 2.1f, 1.8f, 76f, 0.14f, 0.18f, 0.25f)
            drawBoxScaled(PitchConstants.HALF_LENGTH + 8.5f + distOut, y, 0f, 2.1f, 1.8f, 76f, 0.14f, 0.18f, 0.25f)

            // Animated spectator clusters on Medium/High quality
            if (graphicsQuality != GraphicsQuality.LOW) {
                val cheerBoost = if (engine.netRippleLeft > 0.1f || engine.netRippleRight > 0.1f) 0.5f else 0.14f
                for (c in -5..5) {
                    val wave = sin(animTime * 5f + c * 0.9f + row).coerceAtLeast(0f) * cheerBoost
                    val cr = if ((c + row) % 2 == 0) 0.85f else 0.22f
                    val cg = if ((c + row) % 3 == 0) 0.72f else 0.55f
                    val cb = if ((c + row) % 2 == 0) 0.25f else 0.88f
                    drawBoxScaled(c * 9.5f, y + 1.1f + wave, -PitchConstants.HALF_WIDTH - 7.5f - distOut, 5.2f, 0.8f, 0.9f, cr, cg, cb)
                }
            }
        }

        // 4 Corner Floodlight Towers
        val towerPositions = arrayOf(
            -60f to -41f,
            60f to -41f,
            -60f to 41f,
            60f to 41f
        )
        for ((tx, tz) in towerPositions) {
            drawBoxScaled(tx, 14f, tz, 1.4f, 28f, 1.4f, 0.22f, 0.26f, 0.34f)
            val glow = if (isEvening) 1.0f else 0.65f
            drawBoxScaled(tx, 28.5f, tz, 4.2f, 2.2f, 1.8f, glow, glow, if (isEvening) 0.92f else 0.7f)
        }
    }

    private fun drawGoals() {
        val goalX = PitchConstants.HALF_LENGTH
        val gw = PitchConstants.GOAL_HALF_WIDTH
        val gh = PitchConstants.GOAL_HEIGHT
        val gd = PitchConstants.GOAL_DEPTH
        val postR = 0.16f

        for (side in listOf(-1f, 1f)) {
            val gx = side * goalX
            val backX = gx + side * gd
            val ripple = if (side < 0f) engine.netRippleLeft else engine.netRippleRight
            val rippleOffset = sin(animTime * 22f) * ripple * 0.35f

            // Posts & Crossbar
            drawBoxScaled(gx, gh * 0.5f, -gw, postR, gh, postR, 0.96f, 0.98f, 1.0f)
            drawBoxScaled(gx, gh * 0.5f, gw, postR, gh, postR, 0.96f, 0.98f, 1.0f)
            drawBoxScaled(gx, gh, 0f, postR, postR, gw * 2f + postR, 0.96f, 0.98f, 1.0f)

            // Back frame supports
            drawBoxScaled(backX, gh * 0.5f, -gw, 0.09f, gh, 0.09f, 0.78f, 0.82f, 0.88f)
            drawBoxScaled(backX, gh * 0.5f, gw, 0.09f, gh, 0.09f, 0.78f, 0.82f, 0.88f)
            drawBoxScaled((gx + backX) * 0.5f, gh, -gw, gd, 0.08f, 0.08f, 0.78f, 0.82f, 0.88f)
            drawBoxScaled((gx + backX) * 0.5f, gh, gw, gd, 0.08f, 0.08f, 0.78f, 0.82f, 0.88f)

            // Translucent Net Panels (Back + Top + Sides) with reactive ripple
            drawBoxScaled(backX + side * rippleOffset, gh * 0.5f, 0f, 0.04f, gh, gw * 2f, 0.92f, 0.96f, 1.0f, alpha = 0.34f)
            drawBoxScaled((gx + backX) * 0.5f, gh, 0f, gd, 0.04f, gw * 2f, 0.92f, 0.96f, 1.0f, alpha = 0.25f)
            drawBoxScaled((gx + backX) * 0.5f, gh * 0.5f, -gw, gd, gh, 0.04f, 0.92f, 0.96f, 1.0f, alpha = 0.25f)
            drawBoxScaled((gx + backX) * 0.5f, gh * 0.5f, gw, gd, gh, 0.04f, 0.92f, 0.96f, 1.0f, alpha = 0.25f)
        }
    }

    private fun drawTrainingMarkers() {
        for (m in engine.trainingMarkers) {
            if (!m.active) continue
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, m.x, 0.04f, m.z)
            Matrix.scaleM(modelMatrix, 0, m.radius * 0.65f, 1f, m.radius * 0.65f)
            Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
            if (m.isTargetGate) {
                shader.drawMesh(targetRingMesh, mvpMatrix, modelMatrix, 0.98f, 0.78f, 0.10f, 0.9f)
            } else {
                shader.drawMesh(targetRingMesh, mvpMatrix, modelMatrix, 0.64f, 0.90f, 0.21f, 0.85f)
                drawBoxScaled(m.x, 0.42f, m.z, 0.45f, 0.84f, 0.45f, 0.98f, 0.45f, 0.09f)
            }
        }
    }

    private fun drawShadows() {
        if (!graphicsQuality.renderShadows) return
        for (p in engine.players) {
            if (p.x < -60f) continue
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, p.x, 0.025f, p.z)
            Matrix.scaleM(modelMatrix, 0, 0.68f, 1f, 0.68f)
            Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
            shader.drawMesh(discMesh, mvpMatrix, modelMatrix, 0f, 0f, 0f, 0.36f)
        }
        val b = engine.ball
        val shadowScale = (0.42f - b.y * 0.03f).coerceIn(0.18f, 0.42f)
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, b.x, 0.028f, b.z)
        Matrix.scaleM(modelMatrix, 0, shadowScale, 1f, shadowScale)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        shader.drawMesh(discMesh, mvpMatrix, modelMatrix, 0f, 0f, 0f, 0.42f)
    }

    private fun drawPlayers() {
        val ctrlIdx = engine.controlledIndex
        for (p in engine.players) {
            if (p.x < -60f) continue
            // Active control ring under user player
            if (p.index == ctrlIdx) {
                Matrix.setIdentityM(modelMatrix, 0)
                Matrix.translateM(modelMatrix, 0, p.x, 0.032f, p.z)
                Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
                shader.drawMesh(controlRingMesh, mvpMatrix, modelMatrix, 0.64f, 0.90f, 0.21f, 0.92f)
            }
            drawSinglePlayer(p)
        }
    }

    private fun drawSinglePlayer(p: SimPlayer) {
        val kit = if (p.isHome) homeKit else awayKit
        val jerseyHex = if (p.isGoalkeeper) kit.gkJersey else kit.jerseyPrimary
        val (jr, jg, jb) = hexToRgb(jerseyHex)
        val (sr, sg, sb) = hexToRgb(kit.shorts)
        val (skr, skg, skb) = hexToRgb(p.profile.skinToneHex)
        val (hr, hg, hb) = hexToRgb(p.profile.hairColorHex)

        val yawDeg = Math.toDegrees(-p.facingRad.toDouble()).toFloat()
        val speed = kotlin.math.hypot(p.vx, p.vz)
        val strideAmp = (speed / 7f).coerceIn(0f, 1f) * 32f
        val legSwing = sin(p.stridePhase) * strideAmp
        val bodyLean = when {
            p.tackleTimer > 0f -> -32f
            p.diveTimer > 0f -> 45f * p.diveDirZ.coerceIn(-1f, 1f)
            else -> (speed * 1.4f).coerceAtMost(12f)
        }
        val jumpY = if (p.celebrateTimer > 0f) kotlin.math.abs(sin(animTime * 8f)) * 0.45f else 0f

        // Helper to draw a part in player-local space
        fun drawPart(
            lx: Float, ly: Float, lz: Float,
            sx: Float, sy: Float, sz: Float,
            rotZ: Float,
            r: Float, g: Float, b: Float
        ) {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, p.x, p.y + jumpY, p.z)
            Matrix.rotateM(modelMatrix, 0, yawDeg, 0f, 1f, 0f)
            if (bodyLean != 0f) Matrix.rotateM(modelMatrix, 0, bodyLean, 0f, 0f, 1f)
            Matrix.translateM(modelMatrix, 0, lx, ly, lz)
            if (rotZ != 0f) Matrix.rotateM(modelMatrix, 0, rotZ, 0f, 0f, 1f)
            Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
            Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
            shader.drawMesh(boxMesh, mvpMatrix, modelMatrix, r, g, b)
        }

        val kickSwing = if (p.kickTimer > 0f) 42f else 0f
        // Legs (left & right)
        drawPart(0f, 0.38f, -0.15f, 0.22f, 0.72f, 0.22f, legSwing, skr * 0.9f, skg * 0.9f, skb * 0.9f)
        drawPart(0f, 0.38f, 0.15f, 0.22f, 0.72f, 0.22f, -legSwing + kickSwing, skr * 0.9f, skg * 0.9f, skb * 0.9f)
        // Shorts
        drawPart(0f, 0.78f, 0f, 0.36f, 0.26f, 0.50f, 0f, sr, sg, sb)
        // Torso (Jersey)
        drawPart(0f, 1.18f, 0f, 0.38f, 0.58f, 0.54f, 0f, jr, jg, jb)
        // Arms
        drawPart(0f, 1.16f, -0.34f, 0.18f, 0.50f, 0.16f, -legSwing * 0.7f, jr * 0.9f, jg * 0.9f, jb * 0.9f)
        drawPart(0f, 1.16f, 0.34f, 0.18f, 0.50f, 0.16f, legSwing * 0.7f, jr * 0.9f, jg * 0.9f, jb * 0.9f)
        // Head & Hair
        drawPart(0f, 1.66f, 0f, 0.32f, 0.34f, 0.32f, 0f, skr, skg, skb)
        drawPart(-0.02f, 1.81f, 0f, 0.34f, 0.12f, 0.34f, 0f, hr, hg, hb)
    }

    private fun drawBall() {
        val b = engine.ball
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, b.x, b.y, b.z)
        if (b.rotAngleDeg != 0f) {
            Matrix.rotateM(modelMatrix, 0, b.rotAngleDeg, b.rotAxisX, 0.2f, b.rotAxisZ)
        }
        Matrix.scaleM(modelMatrix, 0, PitchConstants.BALL_RADIUS, PitchConstants.BALL_RADIUS, PitchConstants.BALL_RADIUS)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        shader.drawMesh(sphereMesh, mvpMatrix, modelMatrix, 0.98f, 0.98f, 0.96f)

        // Subtle dark equatorial seam ring for visible rolling spin
        Matrix.scaleM(modelMatrix, 0, 1.03f, 0.28f, 1.03f)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        shader.drawMesh(sphereMesh, mvpMatrix, modelMatrix, 0.12f, 0.15f, 0.20f)
    }

    private fun drawBoxScaled(
        x: Float, y: Float, z: Float,
        sx: Float, sy: Float, sz: Float,
        r: Float, g: Float, b: Float,
        alpha: Float = 1f
    ) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        shader.drawMesh(boxMesh, mvpMatrix, modelMatrix, r, g, b, alpha)
    }

    private fun hexToRgb(hex: Long): Triple<Float, Float, Float> {
        val r = ((hex shr 16) and 0xFF) / 255f
        val g = ((hex shr 8) and 0xFF) / 255f
        val b = (hex and 0xFF) / 255f
        return Triple(r, g, b)
    }
}
