package com.example.game.systems

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager as AndroidAudioManager
import android.media.AudioTrack
import com.example.game.model.SettingsData
import kotlinx.coroutines.*
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Self-contained offline procedural audio engine.
 * Synthesizes stadium crowd ambience, referee whistle, ball kick thud,
 * goalpost metallic clang, goal roar fanfare, menu music chord pulse, and UI clicks.
 */
class AudioManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sampleRate = 22050

    private var masterVol = 0.85f
    private var crowdVol = 0.75f
    private var sfxVol = 0.90f
    private var musicVol = 0.65f

    private var crowdJob: Job? = null
    private var musicJob: Job? = null

    private val kickPcm: ShortArray by lazy { synthesizeKick() }
    private val passPcm: ShortArray by lazy { synthesizePass() }
    private val whistleShortPcm: ShortArray by lazy { synthesizeWhistle(0.26f) }
    private val whistleLongPcm: ShortArray by lazy { synthesizeWhistle(0.62f) }
    private val postClangPcm: ShortArray by lazy { synthesizePostClang() }
    private val goalRoarPcm: ShortArray by lazy { synthesizeGoalFanfare() }
    private val uiClickPcm: ShortArray by lazy { synthesizeUiClick() }

    fun applySettings(settings: SettingsData) {
        masterVol = settings.masterVolume.coerceIn(0f, 1f)
        crowdVol = settings.crowdVolume.coerceIn(0f, 1f)
        sfxVol = settings.sfxVolume.coerceIn(0f, 1f)
        musicVol = settings.musicVolume.coerceIn(0f, 1f)
    }

    fun playUiClick() = playSfx(uiClickPcm, 0.55f)
    fun playKick(powerRatio: Float = 0.8f) = playSfx(kickPcm, (0.5f + 0.5f * powerRatio).coerceIn(0.3f, 1f))
    fun playPass() = playSfx(passPcm, 0.65f)
    fun playWhistleShort() = playSfx(whistleShortPcm, 0.85f)
    fun playWhistleFullTime() = playSfx(whistleLongPcm, 0.95f)
    fun playPostClang() = playSfx(postClangPcm, 0.9f)
    fun playGoalCelebration() = playSfx(goalRoarPcm, 1.0f)

    fun startCrowdAmbience() {
        if (crowdJob?.isActive == true) return
        crowdJob = scope.launch {
            val bufSamples = sampleRate * 2
            val pinkCrowd = ShortArray(bufSamples)
            var b0 = 0f
            var b1 = 0f
            var b2 = 0f
            val rng = Random(42)
            for (i in 0 until bufSamples) {
                val white = rng.nextFloat() * 2f - 1f
                b0 = 0.997f * b0 + white * 0.029f
                b1 = 0.985f * b1 + white * 0.032f
                b2 = 0.950f * b2 + white * 0.048f
                val swell = 0.72f + 0.28f * sin(2.0 * PI * i / bufSamples).toFloat()
                val v = ((b0 + b1 + b2) * 0.22f * swell).coerceIn(-0.9f, 0.9f)
                pinkCrowd[i] = (v * Short.MAX_VALUE).toInt().toShort()
            }

            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            val track = createTrack(minBuf, AndroidAudioManager.STREAM_MUSIC, AudioTrack.MODE_STREAM)
            try {
                track.play()
                val scaled = ShortArray(bufSamples)
                while (isActive) {
                    val gain = (masterVol * crowdVol * 0.42f).coerceIn(0f, 1f)
                    for (i in 0 until bufSamples) {
                        scaled[i] = (pinkCrowd[i] * gain).toInt().toShort()
                    }
                    track.write(scaled, 0, scaled.size)
                }
            } catch (_: Throwable) {
            } finally {
                runCatching { track.stop() }
                runCatching { track.release() }
            }
        }
    }

    fun stopCrowdAmbience() {
        crowdJob?.cancel()
        crowdJob = null
    }

    fun startMenuMusic() {
        if (musicJob?.isActive == true) return
        musicJob = scope.launch {
            val loopSamples = sampleRate * 4
            val basePad = ShortArray(loopSamples)
            // Uplifting stadium broadcast chord progression (Em - C - G - D)
            val freqs = listOf(
                floatArrayOf(164.81f, 246.94f, 329.63f),
                floatArrayOf(130.81f, 196.00f, 261.63f),
                floatArrayOf(196.00f, 293.66f, 392.00f),
                floatArrayOf(146.83f, 220.00f, 293.66f)
            )
            for (i in 0 until loopSamples) {
                val t = i.toFloat() / sampleRate
                val bar = ((t.toInt()) % 4)
                val barT = t - t.toInt()
                val env = (sin(PI * barT.toDouble()).toFloat()).coerceIn(0.15f, 1f)
                var sum = 0f
                for (f in freqs[bar]) {
                    sum += sin(2.0 * PI * f * t).toFloat() * 0.14f
                }
                // Soft rhythmic pulse
                val beatT = (t * 2f) % 1f
                val pulse = exp(-beatT * 6f) * sin(2.0 * PI * 82.41 * t).toFloat() * 0.22f
                basePad[i] = ((sum * env + pulse) * 0.45f * Short.MAX_VALUE).toInt().toShort()
            }

            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)
            val track = createTrack(minBuf, AndroidAudioManager.STREAM_MUSIC, AudioTrack.MODE_STREAM)
            try {
                track.play()
                val scaled = ShortArray(sampleRate / 2)
                var offset = 0
                while (isActive) {
                    val gain = (masterVol * musicVol * 0.45f).coerceIn(0f, 1f)
                    for (i in scaled.indices) {
                        scaled[i] = (basePad[(offset + i) % loopSamples] * gain).toInt().toShort()
                    }
                    offset = (offset + scaled.size) % loopSamples
                    track.write(scaled, 0, scaled.size)
                }
            } catch (_: Throwable) {
            } finally {
                runCatching { track.stop() }
                runCatching { track.release() }
            }
        }
    }

    fun stopMenuMusic() {
        musicJob?.cancel()
        musicJob = null
    }

    fun release() {
        stopCrowdAmbience()
        stopMenuMusic()
        scope.cancel()
    }

    private fun playSfx(pcm: ShortArray, localGain: Float) {
        val gain = (masterVol * sfxVol * localGain).coerceIn(0f, 1f)
        if (gain <= 0.01f) return
        scope.launch {
            val scaled = ShortArray(pcm.size) { i -> (pcm[i] * gain).toInt().toShort() }
            val track = createTrack(scaled.size * 2, AndroidAudioManager.STREAM_MUSIC, AudioTrack.MODE_STATIC)
            try {
                track.write(scaled, 0, scaled.size)
                track.play()
                delay((pcm.size * 1000L / sampleRate) + 40L)
            } catch (_: Throwable) {
            } finally {
                runCatching { track.stop() }
                runCatching { track.release() }
            }
        }
    }

    private fun createTrack(bufferBytes: Int, streamType: Int, mode: Int): AudioTrack {
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferBytes.coerceAtLeast(2048))
            .setTransferMode(mode)
            .build()
    }

    private fun synthesizeKick(): ShortArray {
        val n = (sampleRate * 0.14f).toInt()
        return ShortArray(n) { i ->
            val t = i.toFloat() / sampleRate
            val freq = 140f * exp(-t * 28f) + 42f
            val env = exp(-t * 24f)
            val thud = sin(2.0 * PI * freq * t).toFloat() * env
            (thud.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun synthesizePass(): ShortArray {
        val n = (sampleRate * 0.10f).toInt()
        return ShortArray(n) { i ->
            val t = i.toFloat() / sampleRate
            val freq = 185f * exp(-t * 32f) + 55f
            val env = exp(-t * 30f)
            val sig = sin(2.0 * PI * freq * t).toFloat() * env * 0.8f
            (sig.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun synthesizeWhistle(duration: Float): ShortArray {
        val n = (sampleRate * duration).toInt()
        return ShortArray(n) { i ->
            val t = i.toFloat() / sampleRate
            val env = when {
                t < 0.02f -> t / 0.02f
                t > duration - 0.04f -> ((duration - t) / 0.04f).coerceAtLeast(0f)
                else -> 1f
            }
            val peaMod = 0.85f + 0.15f * sin(2.0 * PI * 36.0 * t).toFloat()
            val s1 = sin(2.0 * PI * 2780.0 * t).toFloat()
            val s2 = sin(2.0 * PI * 2960.0 * t).toFloat()
            val sig = (s1 * 0.55f + s2 * 0.45f) * env * peaMod * 0.55f
            (sig.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun synthesizePostClang(): ShortArray {
        val n = (sampleRate * 0.35f).toInt()
        return ShortArray(n) { i ->
            val t = i.toFloat() / sampleRate
            val env = exp(-t * 10f)
            val s1 = sin(2.0 * PI * 540.0 * t).toFloat() * 0.5f
            val s2 = sin(2.0 * PI * 890.0 * t).toFloat() * 0.35f
            val s3 = sin(2.0 * PI * 1410.0 * t).toFloat() * 0.15f
            ((s1 + s2 + s3) * env).coerceIn(-1f, 1f).let { (it * Short.MAX_VALUE).toInt().toShort() }
        }
    }

    private fun synthesizeGoalFanfare(): ShortArray {
        val n = (sampleRate * 1.15f).toInt()
        val rng = Random(99)
        return ShortArray(n) { i ->
            val t = i.toFloat() / sampleRate
            val env = (1f - (t / 1.15f)).coerceIn(0f, 1f)
            val noise = (rng.nextFloat() * 2f - 1f) * 0.35f * env
            val horn = (
                sin(2.0 * PI * 261.63 * t) * 0.30 +
                    sin(2.0 * PI * 329.63 * t) * 0.28 +
                    sin(2.0 * PI * 392.00 * t) * 0.25 +
                    sin(2.0 * PI * 523.25 * t) * 0.20
                ).toFloat() * exp(-t * 2.2f)
            ((noise + horn) * 0.75f).coerceIn(-1f, 1f).let { (it * Short.MAX_VALUE).toInt().toShort() }
        }
    }

    private fun synthesizeUiClick(): ShortArray {
        val n = (sampleRate * 0.045f).toInt()
        return ShortArray(n) { i ->
            val t = i.toFloat() / sampleRate
            val env = exp(-t * 75f)
            val sig = sin(2.0 * PI * 860.0 * t).toFloat() * env * 0.5f
            (sig.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }
    }
}
