package com.audix.app

import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.delay
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
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // SongState
    val currentSong by SongState.currentSong.collectAsState()
    val isPlaying by SongState.isPlaying.collectAsState()
    val isServiceConnected by SongState.isServiceConnected.collectAsState()
    val isDetectingGenre by SongState.isDetectingGenre.collectAsState()

    // Preferences
    val eqIntensity by userPreferencesRepository.eqIntensityFlow.collectAsState(initial = 1.0f)
    var eqIntensityPosition by remember(eqIntensity) { mutableStateOf(eqIntensity) }

    val autoEqEnabledPref by userPreferencesRepository.autoEqEnabledFlow.collectAsState(initial = false)
    var isAutoEqEnabled by remember(autoEqEnabledPref) { mutableStateOf(autoEqEnabledPref) }

    val customTuningEnabledPref by userPreferencesRepository.customTuningEnabledFlow.collectAsState(initial = false)
    var isCustomTuningEnabled by remember(customTuningEnabledPref) { mutableStateOf(customTuningEnabledPref) }

    val customBassPref by userPreferencesRepository.customBassFlow.collectAsState(initial = 0.0f)
    var customBassPosition by remember(customBassPref) { mutableStateOf(customBassPref) }

    val customVocalsPref by userPreferencesRepository.customVocalsFlow.collectAsState(initial = 0.0f)
    var customVocalsPosition by remember(customVocalsPref) { mutableStateOf(customVocalsPref) }

    val customTreblePref by userPreferencesRepository.customTrebleFlow.collectAsState(initial = 0.0f)
    var customTreblePosition by remember(customTreblePref) { mutableStateOf(customTreblePref) }

    // Spatial Audio
    val spatialEnabledPref by userPreferencesRepository.spatialEnabledFlow.collectAsState(initial = false)
    var isSpatialEnabled by remember(spatialEnabledPref) { mutableStateOf(spatialEnabledPref) }

    val spatialLevelPref by userPreferencesRepository.spatialLevelFlow.collectAsState(initial = 0)
    var spatialLevelPosition by remember(spatialLevelPref) { mutableStateOf(spatialLevelPref) }

    // Phase 12: Onboarding
    val onboardingShown by userPreferencesRepository.onboardingShownFlow.collectAsState(initial = true)
    var showOnboarding by remember { mutableStateOf(false) }
    LaunchedEffect(onboardingShown) {
        if (!onboardingShown) showOnboarding = true
    }

    // Phase 12: Network connectivity banner
    var isNetworkAvailable by remember { mutableStateOf(true) }
    DisposableEffect(context) {
        val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkRequest = android.net.NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) { isNetworkAvailable = true }
            override fun onLost(network: android.net.Network) { isNetworkAvailable = false }
            override fun onCapabilitiesChanged(network: android.net.Network, networkCapabilities: NetworkCapabilities) {
                isNetworkAvailable = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
        }
        cm.registerNetworkCallback(networkRequest, callback)
        // Set initial state
        val activeNetwork = cm.activeNetwork
        val caps = if (activeNetwork != null) cm.getNetworkCapabilities(activeNetwork) else null
        isNetworkAvailable = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                
        onDispose { cm.unregisterNetworkCallback(callback) }
    }

    // Phase 12: Offline banner dismissal & auto-hide
    var isOfflineBannerDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(isNetworkAvailable) {
        if (isNetworkAvailable) {
            isOfflineBannerDismissed = false
        } else {
            // Auto-hide after 5 seconds
            delay(5000)
            isOfflineBannerDismissed = true
        }
    }

    // Start foreground EQ service
    LaunchedEffect(Unit) {
        val startIntent = Intent(context, AudioEngineServiceLocal::class.java)
        ContextCompat.startForegroundService(context, startIntent)
    }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    // Phase 12: Onboarding Dialog
    if (showOnboarding) {
        AlertDialog(
            onDismissRequest = {
                showOnboarding = false
                coroutineScope.launch { userPreferencesRepository.saveOnboardingShown(true) }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            title = {
                Text(
                    text = "Welcome to Audix",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OnboardingStep(
                        number = "1",
                        title = "Notification Access",
                        description = "Audix needs to read your media notifications to detect which song is playing in Spotify or YouTube Music. No personal data is stored."
                    )
                    OnboardingStep(
                        number = "2",
                        title = "Battery Optimization Exemption",
                        description = "Exclude Audix from battery optimization so the EQ engine keeps running in the background while your music plays — even when the app is closed."
                    )
                    OnboardingStep(
                        number = "3",
                        title = "Zero Configuration",
                        description = "Once permissions are granted, Audix automatically applies the right EQ for every song. Just play music and enjoy."
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showOnboarding = false
                        coroutineScope.launch { userPreferencesRepository.saveOnboardingShown(true) }
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                ) {
                    Text("Grant Permissions")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showOnboarding = false
                        coroutineScope.launch { userPreferencesRepository.saveOnboardingShown(true) }
                    }
                ) {
                    Text("Skip for Now")
                }
            }
        )
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 32.dp, bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!isPermissionGranted || !isServiceConnected) {
                androidx.compose.material3.Card(
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (!isPermissionGranted) "Notification Access Required" else "Service Disconnected",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }) {
                            Text("Open Settings")
                        }
                    }
                }
            }

            // 1. Hero Section
            val title = currentSong?.title ?: "No Song Detected"
            val artist = currentSong?.artist ?: "Play music in Spotify or YouTube Music"
            val genre = currentSong?.genre

            com.audix.app.ui.components.HeroSection(
                title = title,
                artist = artist,
                genre = genre,
                isPlaying = isPlaying,
                isAutoEqEnabled = isAutoEqEnabled,
                isDetectingGenre = isDetectingGenre,
                modifier = Modifier.padding(top = 0.dp, bottom = 8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Primary Card: Auto EQ
            com.audix.app.ui.components.EqEngineCard(
                isAutoEqEnabled = isAutoEqEnabled,
                onAutoEqChange = {
                    isAutoEqEnabled = it
                    coroutineScope.launch {
                        userPreferencesRepository.saveAutoEqEnabled(it)
                        if (it) userPreferencesRepository.saveCustomTuningEnabled(false)
                    }
                },
                eqIntensity = eqIntensityPosition,
                onIntensityChange = { eqIntensityPosition = it },
                onIntensityChangeFinished = {
                    coroutineScope.launch { userPreferencesRepository.saveEqIntensity(eqIntensityPosition) }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 2b. Spatial Audio Card
            com.audix.app.ui.components.SpatialAudioCard(
                isSpatialEnabled = isSpatialEnabled,
                onSpatialEnabledChange = { enabled ->
                    isSpatialEnabled = enabled
                    coroutineScope.launch {
                        userPreferencesRepository.saveSpatialEnabled(enabled)
                        // If turning ON and level is 0, default to 3 (Balanced)
                        if (enabled && spatialLevelPosition == 0) {
                            spatialLevelPosition = 3
                            userPreferencesRepository.saveSpatialLevel(3)
                        }
                    }
                },
                spatialLevel = spatialLevelPosition,
                onSpatialLevelChange = {
                    spatialLevelPosition = it
                    coroutineScope.launch {
                        userPreferencesRepository.saveSpatialLevel(it)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 3. Secondary Card: Custom Tuning
            com.audix.app.ui.components.CustomTuningCard(
                isCustomTuningEnabled = isCustomTuningEnabled,
                onCustomTuningChange = {
                    isCustomTuningEnabled = it
                    coroutineScope.launch {
                        userPreferencesRepository.saveCustomTuningEnabled(it)
                        if (it) userPreferencesRepository.saveAutoEqEnabled(false)
                    }
                },
                customBass = customBassPosition,
                onCustomBassChange = { customBassPosition = it },
                onCustomBassChangeFinished = {
                    coroutineScope.launch { userPreferencesRepository.saveCustomBass(customBassPosition) }
                },
                customVocals = customVocalsPosition,
                onCustomVocalsChange = { customVocalsPosition = it },
                onCustomVocalsChangeFinished = {
                    coroutineScope.launch { userPreferencesRepository.saveCustomVocals(customVocalsPosition) }
                },
                customTreble = customTreblePosition,
                onCustomTrebleChange = { customTreblePosition = it },
                onCustomTrebleChangeFinished = {
                    coroutineScope.launch { userPreferencesRepository.saveCustomTreble(customTreblePosition) }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(120.dp))
        }

        if (!isAutoEqEnabled && !isSpatialEnabled && !isCustomTuningEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                IconButton(
                    onClick = { showSettingsDialog = true },
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                IconButton(
                    onClick = { showInfoDialog = true },
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = "Info", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Phase 12: Offline banner overlay
        AnimatedVisibility(
            visible = !isNetworkAvailable && !isOfflineBannerDismissed,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Offline • Using Cached EQ",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(start = 24.dp) // Left padding to balance the 'X' button
                )
                IconButton(
                    onClick = { isOfflineBannerDismissed = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            title = { Text("User Guide") },
            text = {
                Column {
                    Text(
                        text = "1. Play music in a supported app (Spotify, YouTube Music) to automatically apply Audix EQ.\n" +
                               "2. Enable AutoEQ to let the smart engine balance sound in real-time.\n" +
                               "3. Use Custom Tuning to override defaults and boost Bass, Vocals, or Treble manually.\n" +
                               "4. Battery exemptions let Audix run safely in the background.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) { Text("Close") }
            }
        )
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            title = { Text("Settings & Permissions") },
            text = {
                Column {
                    SettingsClickRow(
                        title = "Notification Access",
                        statusText = if (isPermissionGranted) "Permission Granted" else "Permission Not Granted",
                        isGranted = isPermissionGranted,
                        onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    SettingsClickRow(
                        title = "Background Autostart",
                        statusText = "Tap to manage in app settings",
                        isGranted = null,
                        onClick = { context.startActivity(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)) }
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    SettingsClickRow(
                        title = "Restrict Battery Optimization",
                        statusText = if (isIgnoringBatteryOptimizations) "Permission Granted" else "Permission Not Granted",
                        isGranted = isIgnoringBatteryOptimizations,
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
                TextButton(onClick = { showSettingsDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun OnboardingStep(number: String, title: String, description: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsClickRow(
    title: String,
    statusText: String,
    isGranted: Boolean?,
    onClick: () -> Unit
) {
    val statusColor = when (isGranted) {
        true  -> MaterialTheme.colorScheme.primary
        false -> MaterialTheme.colorScheme.error
        null  -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = statusText, style = MaterialTheme.typography.bodySmall, color = statusColor)
        }
        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp)
        )
    }
}