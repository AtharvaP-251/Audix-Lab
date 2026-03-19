package com.audix.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.audix.app.data.UserPreferencesRepository
import com.audix.app.service.AudioEngineServiceLocal
import com.audix.app.state.SongState
import com.audix.app.ui.theme.AudixTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var userPreferencesRepository: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userPreferencesRepository = UserPreferencesRepository(applicationContext)
        
        enableEdgeToEdge()
        setContent {
            AudixTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        EqControls(
                            userPreferencesRepository = userPreferencesRepository,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqControls(userPreferencesRepository: UserPreferencesRepository, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var isPermissionGranted by remember {
        mutableStateOf(NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName))
    }

    val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isPermissionGranted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
                isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Observe active song state from detection service
    val currentSong by SongState.currentSong.collectAsState()
    val isServiceConnected by SongState.isServiceConnected.collectAsState()

    // Preferences
    val eqIntensity by userPreferencesRepository.eqIntensityFlow.collectAsState(initial = 1.0f)
    var eqIntensityPosition by remember(eqIntensity) { mutableStateOf(eqIntensity) }

    val autoEqEnabledPref by userPreferencesRepository.autoEqEnabledFlow.collectAsState(initial = true)
    var isAutoEqEnabled by remember(autoEqEnabledPref) { mutableStateOf(autoEqEnabledPref) }

    val customTuningEnabledPref by userPreferencesRepository.customTuningEnabledFlow.collectAsState(initial = false)
    var isCustomTuningEnabled by remember(customTuningEnabledPref) { mutableStateOf(customTuningEnabledPref) }

    val customBassPref by userPreferencesRepository.customBassFlow.collectAsState(initial = 0.0f)
    var customBassPosition by remember(customBassPref) { mutableStateOf(customBassPref) }

    val customVocalsPref by userPreferencesRepository.customVocalsFlow.collectAsState(initial = 0.0f)
    var customVocalsPosition by remember(customVocalsPref) { mutableStateOf(customVocalsPref) }

    val customTreblePref by userPreferencesRepository.customTrebleFlow.collectAsState(initial = 0.0f)
    var customTreblePosition by remember(customTreblePref) { mutableStateOf(customTreblePref) }

    // Manage Foreground Engine Lifecycle
    LaunchedEffect(Unit) {
        val startIntent = Intent(context, AudioEngineServiceLocal::class.java)
        ContextCompat.startForegroundService(context, startIntent)
    }

    var showSettingsDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        // Main Content
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (!isPermissionGranted) {
                Text(
                    text = "Audix requires Notification Access to detect playing songs. Please enable it in Settings.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    context.startActivity(intent)
                }) {
                    Text("Grant Notification Access")
                }
                Spacer(modifier = Modifier.height(32.dp))
            } else if (!isServiceConnected) {
                Text(
                    text = "Service is disconnected. Please toggle Notification Access OFF and ON again in Settings.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    context.startActivity(intent)
                }) {
                    Text("Open Settings")
                }
                Spacer(modifier = Modifier.height(32.dp))
            } else {
                if (currentSong != null) {
                    Text(
                        text = "Now Playing:\n${currentSong!!.title}\nby ${currentSong!!.artist}",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    if (currentSong!!.genre != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Detected Genre: ${currentSong!!.genre}",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        var showDetecting by remember { mutableStateOf(false) }
                        LaunchedEffect(currentSong!!.title) {
                            kotlinx.coroutines.delay(150)
                            showDetecting = true
                        }
                        if (showDetecting) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Detecting genre...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = " ",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Text(
                        text = "No song detected yet.\nPlay music in Spotify or YouTube Music.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            Text(text = "Audix EQ Engine", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(text = "EQ Intensity: ${(eqIntensityPosition * 100).toInt()}%")
            androidx.compose.material3.Slider(
                value = eqIntensityPosition,
                onValueChange = { newValue -> eqIntensityPosition = newValue },
                onValueChangeFinished = { coroutineScope.launch { userPreferencesRepository.saveEqIntensity(eqIntensityPosition) } },
                valueRange = 0f..1f,
                steps = 9,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Auto EQ (Apply genre presets)")
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = isAutoEqEnabled,
                    onCheckedChange = { 
                        isAutoEqEnabled = it
                        coroutineScope.launch {
                            userPreferencesRepository.saveAutoEqEnabled(it)
                            if (it) { // If Auto EQ turned on, ensure custom tuning turns off.
                                userPreferencesRepository.saveCustomTuningEnabled(false)
                            }
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Custom Tuning (-5 to 5)")
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = isCustomTuningEnabled,
                    onCheckedChange = { 
                        isCustomTuningEnabled = it
                        coroutineScope.launch {
                            userPreferencesRepository.saveCustomTuningEnabled(it)
                            if (it) { // If custom tuning turned on, ensure Auto EQ turns off.
                                userPreferencesRepository.saveAutoEqEnabled(false)
                            }
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            val tuningColor = if (isCustomTuningEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)

            val bassVal = kotlin.math.round(customBassPosition).toInt()
            Text(text = "Bass: ${if (bassVal > 0) "+" else ""}$bassVal", color = tuningColor)
            androidx.compose.material3.Slider(
                value = customBassPosition,
                onValueChange = { newValue -> customBassPosition = kotlin.math.round(newValue) },
                onValueChangeFinished = { coroutineScope.launch { userPreferencesRepository.saveCustomBass(customBassPosition) } },
                valueRange = -5f..5f,
                steps = 9,
                enabled = isCustomTuningEnabled,
                modifier = Modifier.padding(horizontal = 32.dp)
            )

            val vocalsVal = kotlin.math.round(customVocalsPosition).toInt()
            Text(text = "Vocals: ${if (vocalsVal > 0) "+" else ""}$vocalsVal", color = tuningColor)
            androidx.compose.material3.Slider(
                value = customVocalsPosition,
                onValueChange = { newValue -> customVocalsPosition = kotlin.math.round(newValue) },
                onValueChangeFinished = { coroutineScope.launch { userPreferencesRepository.saveCustomVocals(customVocalsPosition) } },
                valueRange = -5f..5f,
                steps = 9,
                enabled = isCustomTuningEnabled,
                modifier = Modifier.padding(horizontal = 32.dp)
            )

            val trebleVal = kotlin.math.round(customTreblePosition).toInt()
            Text(text = "Treble: ${if (trebleVal > 0) "+" else ""}$trebleVal", color = tuningColor)
            androidx.compose.material3.Slider(
                value = customTreblePosition,
                onValueChange = { newValue -> customTreblePosition = kotlin.math.round(newValue) },
                onValueChangeFinished = { coroutineScope.launch { userPreferencesRepository.saveCustomTreble(customTreblePosition) } },
                valueRange = -5f..5f,
                steps = 9,
                enabled = isCustomTuningEnabled,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            
            Spacer(modifier = Modifier.height(64.dp)) // padding for bottom icon
        }

        // Settings Icon (Bottom Left)
        IconButton(
            onClick = { showSettingsDialog = true },
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
        ) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.primary)
        }
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Settings & Permissions") },
            text = {
                Column {
                    SettingsRow(
                        title = "Notification Access",
                        description = "Required to detect playing songs",
                        buttonText = if (isPermissionGranted) "Granted" else "Grant",
                        enabled = !isPermissionGranted,
                        onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    SettingsRow(
                        title = "Autostart / App Info",
                        description = "Enable autostart for robust background operation",
                        buttonText = "Manage",
                        enabled = true,
                        onClick = {
                            val intent = Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
                            context.startActivity(intent)
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    SettingsRow(
                        title = "Battery Saver Exemption",
                        description = "Prevent system from killing the background EQ",
                        buttonText = if (isIgnoringBatteryOptimizations) "Ignored" else "Ignore",
                        enabled = !isIgnoringBatteryOptimizations,
                        onClick = {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun SettingsRow(title: String, description: String, buttonText: String, enabled: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = onClick, enabled = enabled) {
            Text(buttonText)
        }
    }
}