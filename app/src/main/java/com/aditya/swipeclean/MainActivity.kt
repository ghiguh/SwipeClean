package com.aditya.swipeclean

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.receiveAsFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
    }
}

private fun neededPermissions(): List<String> = buildList {
    if (Build.VERSION.SDK_INT >= 33) { add(Manifest.permission.READ_MEDIA_IMAGES); add(Manifest.permission.READ_MEDIA_VIDEO) }
    else add(Manifest.permission.READ_EXTERNAL_STORAGE)
    add(Manifest.permission.ACCESS_MEDIA_LOCATION)
}

private fun hasAccess(ctx: Context) = neededPermissions().filter { it != Manifest.permission.ACCESS_MEDIA_LOCATION }
    .all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }

@Composable
fun App(vm: MainViewModel = viewModel()) {
    SwipeCleanTheme(vm.themeMode, vm.palette) {
        val ctx = LocalContext.current
        var granted by remember { mutableStateOf(hasAccess(ctx)) }
        var asked by remember { mutableStateOf(false) }
        LifecycleResumeEffect(Unit) { granted = hasAccess(ctx); onPauseOrDispose { } }
        LaunchedEffect(granted) { if (granted) vm.load() }

        val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            granted = hasAccess(ctx); asked = true
        }
        val sysLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            vm.onSystemResult(it.resultCode == Activity.RESULT_OK)
        }
        LaunchedEffect(Unit) { vm.intents.receiveAsFlow().collect { sysLauncher.launch(IntentSenderRequest.Builder(it).build()) } }
        LaunchedEffect(Unit) { vm.toasts.receiveAsFlow().collect { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() } }

        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (!granted) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp).systemBarsPadding(),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(96.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(24.dp))
                    Text("SwipeClean needs your photos", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Allow access to photos and videos so you can swipe through them. Nothing leaves your phone.",
                        textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { permLauncher.launch(neededPermissions().toTypedArray()) }) { Text("Allow access") }
                    if (asked) {
                        TextButton(onClick = {
                            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
                        }) { Text("Open app settings") }
                    }
                }
            } else {
                var screen by rememberSaveable { mutableIntStateOf(0) } // 0 swipe, 1 trash, 2 settings
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        val sp = spring<androidx.compose.ui.unit.IntOffset>(0.8f, Spring.StiffnessMediumLow)
                        if (targetState > initialState)
                            (slideInHorizontally(sp) { it } + fadeIn()) togetherWith (slideOutHorizontally(sp) { -it / 3 } + fadeOut())
                        else
                            (slideInHorizontally(sp) { -it / 3 } + fadeIn()) togetherWith (slideOutHorizontally(sp) { it } + fadeOut())
                    },
                    label = "nav"
                ) { s ->
                    when (s) {
                        0 -> SwipeScreen(vm, onTrash = { screen = 1 }, onSettings = { screen = 2 })
                        1 -> TrashScreen(vm, onBack = { screen = 0 })
                        else -> SettingsScreen(vm, onBack = { screen = 0 })
                    }
                }
            }
        }
    }
}
