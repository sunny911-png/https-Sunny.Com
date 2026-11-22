package com.example.game.systems

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thread-safe multi-touch input state consumed by the 60Hz simulation loop.
 */
class MobileInputManager {

    @Volatile var moveX: Float = 0f
    @Volatile var moveZ: Float = 0f
    @Volatile var isSprinting: Boolean = false
    @Volatile var shootChargeProgress: Float = 0f

    private val passQueued = AtomicBoolean(false)
    private val longPassQueued = AtomicBoolean(false)
    private val throughQueued = AtomicBoolean(false)
    private val shootQueued = AtomicBoolean(false)
    private val tackleQueued = AtomicBoolean(false)
    private val switchQueued = AtomicBoolean(false)

    @Volatile var queuedShotPower: Float = 0.65f

    fun setJoystickVector(dx: Float, dy: Float) {
        // Screen X maps to pitch X (or Z depending on camera orientation).
        // Broadcast camera looks toward -X/+Z? Let's keep camera looking along -X (side view)
        // OR looking from -Z toward +Z?
        // In Broadcast side-view (camera at x = +48 or -48 looking across width, OR camera behind-ish):
        // Wait: Standard FIFA/PES broadcast view has Home attacking LEFT-TO-RIGHT!
        // Let's map world +X = Right goal (Away goal), world -X = Left goal (Home goal),
        // world Z = pitch width (-34 top/far touchline to +34 bottom/near touchline)!
        // That makes Screen Right (+dx) = Attack Right (+X), Screen Down (+dy) = Move Near (+Z)!
        moveX = dx.coerceIn(-1f, 1f)
        moveZ = dy.coerceIn(-1f, 1f)
    }

    fun triggerPass(isLong: Boolean = false) {
        if (isLong) longPassQueued.set(true) else passQueued.set(true)
    }

    fun triggerThrough() {
        throughQueued.set(true)
    }

    fun triggerShoot(power: Float) {
        queuedShotPower = power.coerceIn(0.25f, 1f)
        shootQueued.set(true)
    }

    fun triggerTackle() {
        tackleQueued.set(true)
    }

    fun triggerSwitch() {
        switchQueued.set(true)
    }

    fun consumePass(): Boolean = passQueued.getAndSet(false)
    fun consumeLongPass(): Boolean = longPassQueued.getAndSet(false)
    fun consumeThrough(): Boolean = throughQueued.getAndSet(false)
    fun consumeShoot(): Boolean = shootQueued.getAndSet(false)
    fun consumeTackle(): Boolean = tackleQueued.getAndSet(false)
    fun consumeSwitch(): Boolean = switchQueued.getAndSet(false)

    fun reset() {
        moveX = 0f
        moveZ = 0f
        isSprinting = false
        shootChargeProgress = 0f
        passQueued.set(false)
        longPassQueued.set(false)
        throughQueued.set(false)
        shootQueued.set(false)
        tackleQueued.set(false)
        switchQueued.set(false)
    }
}
