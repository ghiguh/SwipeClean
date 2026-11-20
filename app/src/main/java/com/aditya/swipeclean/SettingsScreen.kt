package com.aditya.swipeclean

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }

        Section("Theme") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("System", "Light", "Dark").forEachIndexed { i, n ->
                    FilterChip(vm.themeMode == i, { vm.updateThemeMode(i) }, label = { Text(n) })
                }
            }
        }

        Section("Colors") {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PaletteSeeds.forEachIndexed { i, seed ->
                    if (seed == null && Build.VERSION.SDK_INT < 31) return@forEachIndexed
                    val on = vm.palette == i
                    val s by animateFloatAsState(if (on) 1.15f else 1f, spring<Float>(0.4f, Spring.StiffnessMedium), label = "sw")
                    Box(
                        Modifier.size(44.dp).graphicsLayer { scaleX = s; scaleY = s }.clip(CircleShape)
                            .background(seed ?: MaterialTheme.colorScheme.primary)
                            .then(if (on) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .clickable { vm.updatePalette(i) },
                        contentAlignment = Alignment.Center
                    ) { if (seed == null) Icon(Icons.Rounded.AutoAwesome, "Dynamic", tint = Color.White) }
                }
            }
            Text(
                if (vm.palette == 0) "Dynamic follows your wallpaper (Android 12+)" else PaletteNames[vm.palette],
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Section("Sound pack") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoundPackNames.forEachIndexed { i, n ->
                    FilterChip(vm.soundPack == i, {
                        vm.updateSoundPack(i); vm.sound.play(i, SoundEvent.KEEP, vm.volume)
                    }, label = { Text(n) })
                }
            }
            Slider(vm.volume, { vm.updateVolume(it) }, valueRange = 0f..1f, onValueChangeFinished = {
                vm.sound.play(vm.soundPack, SoundEvent.POP, vm.volume)
            })
            Text("Volume", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Section("Haptics") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Off", "Soft", "Medium", "Strong").forEachIndexed { i, n ->
                    FilterChip(vm.hapticLevel == i, { vm.updateHapticLevel(i); vm.haptics.commit(i) }, label = { Text(n) })
                }
            }
        }

        Section("Kept photos") {
            Text("${vm.kept.size} kept and hidden from the deck", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { vm.resetKept() }, enabled = vm.kept.isNotEmpty()) { Text("Show kept photos again") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        content()
    }
}
