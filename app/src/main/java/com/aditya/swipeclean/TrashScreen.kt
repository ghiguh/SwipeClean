package com.aditya.swipeclean

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun TrashScreen(vm: MainViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var confirm by remember { mutableStateOf(false) }
    val items = vm.trashItems
    val sel = selected.intersect(items.map { it.id }.toSet())

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text("Trash", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (items.isNotEmpty()) {
                TextButton(onClick = { selected = if (sel.size == items.size) emptySet() else items.map { it.id }.toSet() }) {
                    Text(if (sel.size == items.size) "Clear" else "Select all")
                }
            }
        }
        if (items.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Trash is empty", style = MaterialTheme.typography.titleMedium)
                }
            }
        } else {
            LazyVerticalGrid(
                GridCells.Fixed(3), Modifier.weight(1f), contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    val on = item.id in sel
                    val s by animateFloatAsState(if (on) 0.86f else 1f, spring<Float>(0.45f, Spring.StiffnessMedium), label = "cell")
                    val c by animateFloatAsState(if (on) 1f else 0f, spring<Float>(0.4f, Spring.StiffnessMedium), label = "check")
                    Box(
                        Modifier.aspectRatio(1f).graphicsLayer { scaleX = s; scaleY = s }.clip(RoundedCornerShape(16.dp))
                            .clickable { selected = if (on) sel - item.id else sel + item.id }
                    ) {
                        AsyncImage(item.uri, item.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        if (item.isVideo) {
                            Icon(Icons.Rounded.PlayArrow, null, tint = Color.White,
                                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).size(20.dp))
                        }
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp)
                                .graphicsLayer { scaleX = c; scaleY = c }
                                .clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp)) }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = sel.isNotEmpty(),
            enter = slideInVertically(spring<IntOffset>(0.7f, Spring.StiffnessMediumLow)) { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { vm.restore(sel); selected = emptySet() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.RestoreFromTrash, null); Spacer(Modifier.width(8.dp)); Text("Restore (${sel.size})")
                }
                Button(
                    onClick = { confirm = true }, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
                ) { Icon(Icons.Rounded.DeleteForever, null); Spacer(Modifier.width(8.dp)); Text("Delete (${sel.size})") }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            icon = { Icon(Icons.Rounded.DeleteForever, null) },
            title = { Text("Delete ${sel.size} item(s) forever?") },
            text = { Text("They will be permanently removed from your phone. Android will ask you to confirm.") },
            confirmButton = { TextButton(onClick = { confirm = false; vm.requestDelete(sel); selected = emptySet() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
        )
    }
}
