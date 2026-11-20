package com.aditya.swipeclean

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator

class Haptics(ctx: Context) {
    private val v: Vibrator = ctx.getSystemService(Vibrator::class.java)
    private val prims = try {
        v.areAllPrimitivesSupported(
            VibrationEffect.Composition.PRIMITIVE_TICK, VibrationEffect.Composition.PRIMITIVE_CLICK
        )
    } catch (e: Throwable) { false }
    private val base = floatArrayOf(0f, 0.35f, 0.6f, 1f)

    private fun amp(level: Int, strength: Float) =
        base[level.coerceIn(0, 3)] * (0.3f + 0.7f * strength.coerceIn(0f, 1f))

    private fun go(e: VibrationEffect) { try { v.vibrate(e) } catch (t: Throwable) { } }
    private fun oneShot(ms: Long, a: Float) = go(VibrationEffect.createOneShot(ms, (a * 255).toInt().coerceIn(1, 255)))

    /** Drag "resistance": ticks that get stronger as you pull further. */
    fun tick(level: Int, strength: Float) {
        if (level == 0) return
        val a = amp(level, strength)
        if (prims) go(VibrationEffect.startComposition()
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, a.coerceIn(0.05f, 1f)).compose())
        else oneShot(8, a)
    }

    /** Crossing the commit line. */
    fun threshold(level: Int) {
        if (level == 0) return
        val a = amp(level, 1f)
        if (prims) go(VibrationEffect.startComposition()
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, a.coerceIn(0.05f, 1f)).compose())
        else go(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
    }

    /** Card leaves the screen: pop. */
    fun commit(level: Int) {
        if (level == 0) return
        val a = amp(level, 1f)
        if (prims) go(VibrationEffect.startComposition()
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, a.coerceIn(0.05f, 1f))
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, (a * 0.6f).coerceIn(0.05f, 1f), 40).compose())
        else go(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
    }

    /** Card bounces back. */
    fun snap(level: Int) {
        if (level == 0) return
        val a = amp(level, 0.6f)
        if (prims) go(VibrationEffect.startComposition()
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, (a * 0.8f).coerceIn(0.05f, 1f))
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, (a * 0.4f).coerceIn(0.05f, 1f), 70).compose())
        else oneShot(12, a)
    }
}
