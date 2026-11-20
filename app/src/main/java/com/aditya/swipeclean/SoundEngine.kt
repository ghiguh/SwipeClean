package com.aditya.swipeclean

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

enum class SoundEvent { POP, KEEP, TRASH, SNAP, UNDO }
enum class Wave { SINE, SQUARE, TRI }

val SoundPackNames = listOf("Off", "Pop", "Bubble", "Chime", "Retro")

private data class Note(
    val f0: Float, val f1: Float, val ms: Int,
    val wave: Wave = Wave.SINE, val decay: Float = 6f, val at: Int = 0, val gain: Float = 0.8f
)

/** All sounds are synthesized at runtime, so the app needs no audio files. */
class SoundEngine {
    private val rate = 22050
    private val cache = ConcurrentHashMap<String, ShortArray>()

    init {
        Thread {
            for (p in 1 until SoundPackNames.size) for (e in SoundEvent.values()) data(p, e)
        }.start()
    }

    private fun data(pack: Int, ev: SoundEvent): ShortArray =
        cache.getOrPut("$pack-$ev") { render(notes(pack, ev)) }

    fun play(pack: Int, ev: SoundEvent, volume: Float) {
        if (pack <= 0 || volume <= 0f) return
        try {
            val d = data(pack, ev)
            val t = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                )
                .setAudioFormat(
                    AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
                )
                .setBufferSizeInBytes(d.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            t.write(d, 0, d.size)
            t.setVolume(min(1f, volume))
            t.setNotificationMarkerPosition(d.size - 1)
            t.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(track: AudioTrack) { track.release() }
                override fun onPeriodicNotification(track: AudioTrack) {}
            })
            t.play()
        } catch (e: Throwable) { }
    }

    private fun render(notes: List<Note>): ShortArray {
        val totalMs = notes.maxOf { it.at + it.ms }
        val n = totalMs * rate / 1000
        val buf = FloatArray(n)
        for (nt in notes) {
            val start = nt.at * rate / 1000
            val len = nt.ms * rate / 1000
            var phase = 0.0
            for (i in 0 until len) {
                val t = i.toFloat() / len
                val f = nt.f0 + (nt.f1 - nt.f0) * t
                phase += 2.0 * PI * f / rate
                val s = when (nt.wave) {
                    Wave.SINE -> sin(phase).toFloat()
                    Wave.SQUARE -> if (sin(phase) >= 0) 1f else -1f
                    Wave.TRI -> (2.0 / PI * asin(sin(phase))).toFloat()
                }
                val attack = min(1f, i / (rate * 0.002f))
                val env = attack * exp(-nt.decay * t) * (1f - t * t * t * t)
                if (start + i < n) buf[start + i] += s * env * nt.gain
            }
        }
        return ShortArray(n) { (buf[it].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort() }
    }

    private fun notes(pack: Int, ev: SoundEvent): List<Note> = when (pack) {
        1 -> when (ev) { // Pop
            SoundEvent.POP -> listOf(Note(500f, 900f, 45, decay = 5f))
            SoundEvent.KEEP -> listOf(Note(350f, 1000f, 110, decay = 5f))
            SoundEvent.TRASH -> listOf(Note(700f, 160f, 130, decay = 4.5f))
            SoundEvent.SNAP -> listOf(Note(300f, 180f, 90, decay = 7f))
            SoundEvent.UNDO -> listOf(Note(450f, 750f, 80), Note(600f, 900f, 80, at = 60, gain = 0.5f))
        }
        2 -> when (ev) { // Bubble
            SoundEvent.POP -> listOf(Note(900f, 1500f, 40))
            SoundEvent.KEEP -> listOf(Note(250f, 1300f, 140), Note(600f, 2000f, 70, decay = 8f, at = 40, gain = 0.4f))
            SoundEvent.TRASH -> listOf(Note(1100f, 200f, 160, decay = 5f))
            SoundEvent.SNAP -> listOf(Note(200f, 320f, 100, decay = 7f))
            SoundEvent.UNDO -> listOf(Note(1300f, 500f, 100))
        }
        3 -> when (ev) { // Chime
            SoundEvent.POP -> listOf(Note(1568f, 1568f, 90, decay = 9f, gain = 0.5f))
            SoundEvent.KEEP -> listOf(
                Note(1318f, 1318f, 380, decay = 5f, gain = 0.6f),
                Note(1760f, 1760f, 380, decay = 5f, at = 70, gain = 0.6f),
                Note(2637f, 2637f, 300, decay = 9f, at = 70, gain = 0.15f)
            )
            SoundEvent.TRASH -> listOf(
                Note(440f, 440f, 320, decay = 5f, gain = 0.7f),
                Note(330f, 330f, 420, decay = 5f, at = 80, gain = 0.7f)
            )
            SoundEvent.SNAP -> listOf(Note(880f, 880f, 120, decay = 9f, gain = 0.4f))
            SoundEvent.UNDO -> listOf(Note(660f, 660f, 200, gain = 0.6f), Note(880f, 880f, 260, at = 70, gain = 0.6f))
        }
        else -> when (ev) { // Retro
            SoundEvent.POP -> listOf(Note(880f, 880f, 40, Wave.SQUARE, 2f, gain = 0.25f))
            SoundEvent.KEEP -> listOf(
                Note(523f, 523f, 60, Wave.SQUARE, 1.5f, 0, 0.25f),
                Note(659f, 659f, 60, Wave.SQUARE, 1.5f, 60, 0.25f),
                Note(784f, 784f, 100, Wave.SQUARE, 3f, 120, 0.25f)
            )
            SoundEvent.TRASH -> listOf(
                Note(392f, 392f, 70, Wave.SQUARE, 1.5f, 0, 0.25f),
                Note(262f, 262f, 70, Wave.SQUARE, 1.5f, 70, 0.25f),
                Note(131f, 131f, 130, Wave.SQUARE, 3f, 140, 0.25f)
            )
            SoundEvent.SNAP -> listOf(Note(220f, 180f, 60, Wave.TRI, 4f, 0, 0.6f))
            SoundEvent.UNDO -> listOf(
                Note(330f, 330f, 50, Wave.SQUARE, 1.5f, 0, 0.25f),
                Note(494f, 494f, 80, Wave.SQUARE, 3f, 50, 0.25f)
            )
        }
    }
}
