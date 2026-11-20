package com.aditya.swipeclean

import android.app.Application
import android.content.Context
import android.content.IntentSender
import android.provider.MediaStore
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class MainViewModel(private val app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("swipeclean", Context.MODE_PRIVATE)
    private val repo = MediaRepository(app)
    val sound = SoundEngine()
    val haptics = Haptics(app)

    // ---- data ----
    var all by mutableStateOf<List<MediaItem>>(emptyList()); private set
    var loading by mutableStateOf(true); private set
    var kept by mutableStateOf(readIds("kept")); private set
    var trashed by mutableStateOf(readIds("trash")); private set
    var metaVersion by mutableIntStateOf(0); private set
    private val history = mutableStateListOf<Pair<Long, Boolean>>() // id, wasTrashed
    val canUndo get() = history.isNotEmpty()
    val queue by derivedStateOf { all.filter { it.id !in kept && it.id !in trashed } }
    val trashItems by derivedStateOf { all.filter { it.id in trashed } }

    // ---- settings ----
    var themeMode by mutableIntStateOf(prefs.getInt("themeMode", 0)); private set
    var palette by mutableIntStateOf(prefs.getInt("palette", 0)); private set
    var soundPack by mutableIntStateOf(prefs.getInt("soundPack", 1)); private set
    var volume by mutableFloatStateOf(prefs.getFloat("volume", 0.7f)); private set
    var hapticLevel by mutableIntStateOf(prefs.getInt("haptic", 2)); private set
    fun updateThemeMode(v: Int) { themeMode = v; prefs.edit().putInt("themeMode", v).apply() }
    fun updatePalette(v: Int) { palette = v; prefs.edit().putInt("palette", v).apply() }
    fun updateSoundPack(v: Int) { soundPack = v; prefs.edit().putInt("soundPack", v).apply() }
    fun updateVolume(v: Float) { volume = v; prefs.edit().putFloat("volume", v).apply() }
    fun updateHapticLevel(v: Int) { hapticLevel = v; prefs.edit().putInt("haptic", v).apply() }

    // ---- feedback ----
    fun fxTick(s: Float) = haptics.tick(hapticLevel, s)
    fun fxThreshold() { haptics.threshold(hapticLevel); sound.play(soundPack, SoundEvent.POP, volume) }
    fun fxCommit(keep: Boolean) {
        haptics.commit(hapticLevel); sound.play(soundPack, if (keep) SoundEvent.KEEP else SoundEvent.TRASH, volume)
    }
    fun fxSnap() { haptics.snap(hapticLevel); sound.play(soundPack, SoundEvent.SNAP, volume) }

    // ---- system dialogs (write / delete need user approval on Android 11+) ----
    val intents = Channel<IntentSender>(Channel.BUFFERED)
    val toasts = Channel<String>(Channel.BUFFERED)
    private sealed interface Pending {
        class Delete(val ids: Set<Long>) : Pending
        class Edit(val item: MediaItem, val meta: Meta) : Pending
    }
    private var pending: Pending? = null

    fun load() {
        viewModelScope.launch {
            loading = true
            all = repo.load()
            val ids = all.map { it.id }.toSet()
            trashed = trashed.intersect(ids); save("trash", trashed)
            kept = kept.intersect(ids); save("kept", kept)
            loading = false
        }
    }

    fun swipe(item: MediaItem, keep: Boolean) {
        if (keep) { kept = kept + item.id; save("kept", kept) } else { trashed = trashed + item.id; save("trash", trashed) }
        history.add(item.id to !keep)
    }

    fun undo() {
        val (id, wasTrash) = history.removeLastOrNull() ?: return
        if (wasTrash) { trashed = trashed - id; save("trash", trashed) } else { kept = kept - id; save("kept", kept) }
        sound.play(soundPack, SoundEvent.UNDO, volume); haptics.snap(hapticLevel)
    }

    fun restore(ids: Set<Long>) { trashed = trashed - ids; save("trash", trashed) }
    fun resetKept() { kept = emptySet(); save("kept", kept) }

    fun requestDelete(ids: Set<Long>) {
        if (ids.isEmpty()) return
        pending = Pending.Delete(ids)
        val uris = all.filter { it.id in ids }.map { it.uri }
        intents.trySend(MediaStore.createDeleteRequest(app.contentResolver, uris).intentSender)
    }

    fun requestEdit(item: MediaItem, meta: Meta) {
        pending = Pending.Edit(item, meta)
        intents.trySend(MediaStore.createWriteRequest(app.contentResolver, listOf(item.uri)).intentSender)
    }

    suspend fun readMeta(item: MediaItem) = repo.readMeta(item)

    fun onSystemResult(ok: Boolean) {
        val p = pending; pending = null
        if (!ok || p == null) return
        when (p) {
            is Pending.Delete -> {
                all = all.filter { it.id !in p.ids }
                trashed = trashed - p.ids; save("trash", trashed)
                toasts.trySend("Deleted ${p.ids.size} item(s)")
            }
            is Pending.Edit -> viewModelScope.launch {
                val msg = repo.writeMeta(p.item, p.meta)
                metaVersion++
                toasts.trySend(msg)
            }
        }
    }

    private fun readIds(key: String): Set<Long> =
        prefs.getString(key, "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }.toSet()
    private fun save(key: String, s: Set<Long>) = prefs.edit().putString(key, s.joinToString(",")).apply()
}
