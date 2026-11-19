package app.byzantine.pitchmonitor

import android.Manifest
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.byzantine.pitchmonitor.ui.AppIcons
import app.byzantine.pitchmonitor.ui.ByzantineTheme
import app.byzantine.pitchmonitor.ui.MainScreen
import app.byzantine.pitchmonitor.ui.MainViewModel
import app.byzantine.pitchmonitor.ui.Palette

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var openSettingsOnStart = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Debug builds: `--ez demo true` feeds a synthetic voice instead of the microphone.
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        // Other debug extras let screens be checked from adb on devices that block input injection.
        if (debuggable) {
            if (intent.getBooleanExtra("demo", false)) vm.engine.demo = true
            intent.getStringExtra("echos")?.let { name ->
                vm.selectEchos(app.byzantine.pitchmonitor.music.Echos.entries.firstOrNull { it.name == name })
            }
            if (intent.getBooleanExtra("ison", false)) vm.toggleIson()
            openSettingsOnStart = intent.getBooleanExtra("settings", false)
        }
        setContent {
            ByzantineTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    App(vm)
                }
            }
        }
    }

    @Composable
    private fun App(vm: MainViewModel) {
        val context = LocalContext.current
        var granted by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            )
        }
        var askedOnce by remember { mutableStateOf(false) }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            granted = ok
            askedOnce = true
        }

        // Listen only while the app is visible.
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, granted) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) vm.startListening()
                    }
                    Lifecycle.Event.ON_STOP -> vm.stopListening()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            if (granted && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) vm.startListening()
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        val settings by vm.settings.collectAsStateWithLifecycle()
        DisposableEffect(settings.keepScreenOn) {
            if (settings.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { }
        }

        if (granted) {
            MainScreen(vm, openSettingsOnStart)
        } else {
            PermissionScreen(
                permanentlyDenied = askedOnce && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO),
                onRequest = { launcher.launch(Manifest.permission.RECORD_AUDIO) },
                onOpenSettings = {
                    startActivity(
                        Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                    )
                },
            )
        }
    }
}

@Composable
private fun PermissionScreen(permanentlyDenied: Boolean, onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(AppIcons.Mic, contentDescription = null, tint = Palette.Gold, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("Byzantine Pitch Monitor", color = Palette.Gold, fontSize = 22.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            "To show the pitch of your chanting, the app needs the microphone. " +
                "Audio is analysed on the phone and never recorded or sent anywhere.",
            color = Palette.Parchment,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        if (permanentlyDenied) {
            Text("Microphone access was denied. Allow it in the app settings.", color = Palette.Muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onOpenSettings) { Text("Open app settings") }
        } else {
            Button(onClick = onRequest) { Text("Allow microphone") }
        }
        if (permanentlyDenied) TextButton(onClick = onRequest) { Text("Try again") }
    }
}
