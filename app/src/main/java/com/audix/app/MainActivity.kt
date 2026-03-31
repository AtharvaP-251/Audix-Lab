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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.rotate
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

sealed class SettingsSheetState {
    object None : SettingsSheetState()
    object Main : SettingsSheetState()
    object ApiKey : SettingsSheetState()
    object About : SettingsSheetState()
    object Guide : SettingsSheetState()
}

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
                            isHeadphonesConnected = SongState.isHeadphonesConnected.collectAsState().value,
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
fun EqControls(
    userPreferencesRepository: UserPreferencesRepository,
    isHeadphonesConnected: Boolean,
    modifier: Modifier = Modifier
) {
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

    val currentSong by SongState.currentSong.collectAsState()
    val isPlaying by SongState.isPlaying.collectAsState()
    val isServiceConnected by SongState.isServiceConnected.collectAsState()
    val isDetectingGenre by SongState.isDetectingGenre.collectAsState()

    val eqIntensity by userPreferencesRepository.eqIntensityFlow.collectAsState(initial = 1.0f)
    var eqIntensityPosition by remember(eqIntensity) { mutableStateOf(eqIntensity) }

    val autoEqFlowValue by userPreferencesRepository.autoEqEnabledFlow.collectAsState(initial = false)
    var isAutoEqEnabled by remember { mutableStateOf(autoEqFlowValue) }
    LaunchedEffect(autoEqFlowValue) { isAutoEqEnabled = autoEqFlowValue }

    val customTuningFlowValue by userPreferencesRepository.customTuningEnabledFlow.collectAsState(initial = false)
    var isCustomTuningEnabled by remember { mutableStateOf(customTuningFlowValue) }
    LaunchedEffect(customTuningFlowValue) { isCustomTuningEnabled = customTuningFlowValue }

    val customBassPref by userPreferencesRepository.customBassFlow.collectAsState(initial = 0.0f)
    var customBassPosition by remember(customBassPref) { mutableStateOf(customBassPref) }

    val customVocalsPref by userPreferencesRepository.customVocalsFlow.collectAsState(initial = 0.0f)
    var customVocalsPosition by remember(customVocalsPref) { mutableStateOf(customVocalsPref) }

    val customTreblePref by userPreferencesRepository.customTrebleFlow.collectAsState(initial = 0.0f)
    var customTreblePosition by remember(customTreblePref) { mutableStateOf(customTreblePref) }

    val spatialEnabledFlowValue by userPreferencesRepository.spatialEnabledFlow.collectAsState(initial = false)
    var isSpatialEnabled by remember { mutableStateOf(spatialEnabledFlowValue) }
    LaunchedEffect(spatialEnabledFlowValue) { isSpatialEnabled = spatialEnabledFlowValue }

    val spatialLevelPref by userPreferencesRepository.spatialLevelFlow.collectAsState(initial = 0)
    var spatialLevelPosition by remember(spatialLevelPref) { mutableStateOf(spatialLevelPref) }

    val geminiApiKey by userPreferencesRepository.geminiApiKeyFlow.collectAsState(initial = null)
    var tempGeminiApiKey by remember(geminiApiKey) { mutableStateOf(geminiApiKey ?: "") }

    val masterEnabledFlowValue by userPreferencesRepository.masterEnabledFlow.collectAsState(initial = true)
    var masterEnabled by remember { mutableStateOf(masterEnabledFlowValue) }
    LaunchedEffect(masterEnabledFlowValue) { masterEnabled = masterEnabledFlowValue }

    val canEnableService by remember(isPermissionGranted, isIgnoringBatteryOptimizations) {
        derivedStateOf { isPermissionGranted && isIgnoringBatteryOptimizations }
    }

    val isAnyEnhancementEnabled by remember(isAutoEqEnabled, isCustomTuningEnabled, isSpatialEnabled, masterEnabled, canEnableService) {
        derivedStateOf { (isAutoEqEnabled || isCustomTuningEnabled || isSpatialEnabled) && masterEnabled && canEnableService }
    }
    
    val spiralAlpha by animateFloatAsState(
        targetValue = if (isAnyEnhancementEnabled) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "spiralAlpha"
    )

    var isEqExpanded by remember { mutableStateOf(isAutoEqEnabled) }
    var isSpatialExpanded by remember { mutableStateOf(isSpatialEnabled) }
    var isCustomExpanded by remember { mutableStateOf(isCustomTuningEnabled) }

    val onboardingShown by userPreferencesRepository.onboardingShownFlow.collectAsState(initial = true)
    var showOnboarding by remember { mutableStateOf(false) }
    LaunchedEffect(onboardingShown) {
        if (!onboardingShown) showOnboarding = true
    }

    var isEqVisible by remember { mutableStateOf(masterEnabled && canEnableService) }
    var isCustomVisible by remember { mutableStateOf(masterEnabled && canEnableService) }
    var isSpatialVisible by remember { mutableStateOf(masterEnabled && canEnableService) }

    LaunchedEffect(masterEnabled, canEnableService) {
        if (masterEnabled && canEnableService) {
            isEqVisible = true
            delay(60)
            isCustomVisible = true
            delay(60)
            isSpatialVisible = true
        } else {
            // Staggered exit in reverse order (Spatial -> Custom -> EQ) for layout smoothness
            isSpatialVisible = false
            delay(40)
            isCustomVisible = false
            delay(40)
            isEqVisible = false
        }
    }

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
        val activeNetwork = cm.activeNetwork
        val caps = if (activeNetwork != null) cm.getNetworkCapabilities(activeNetwork) else null
        isNetworkAvailable = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                
        onDispose { cm.unregisterNetworkCallback(callback) }
    }

    var isOfflineBannerDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(isNetworkAvailable) {
        if (isNetworkAvailable) {
            isOfflineBannerDismissed = false
        } else {
            delay(5000)
            isOfflineBannerDismissed = true
        }
    }

    LaunchedEffect(Unit) {
        val startIntent = Intent(context, AudioEngineServiceLocal::class.java)
        ContextCompat.startForegroundService(context, startIntent)
    }

    var settingsSheetState by remember { mutableStateOf<SettingsSheetState>(SettingsSheetState.None) }
    var openedFromMainSettings by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val blurRadius by animateDpAsState(
        targetValue = if (settingsSheetState != SettingsSheetState.None) 16.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "blurAnimation"
    )

    if (showOnboarding) {
        AlertDialog(
            onDismissRequest = { showOnboarding = false },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
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
                    Text("Got it")
                }
            }
        )
    }


    val infiniteTransition = rememberInfiniteTransition(label = "vinylAtmosphere")
    
    // Slow, realistic spin for the light reflection
    val spinRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Restart),
        label = "spinRotation"
    )

    // Subtle breathing pulse for the entire element
    val vinylPulse by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(8000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "vinylPulse"
    )

    // Pulse Wave Animation (Expansion from center)
    val groovePulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearOutSlowInEasing), RepeatMode.Restart),
        label = "groovePulse"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Atmospheric Vinyl Background (Bottom Anchored)
        val primaryRed = MaterialTheme.colorScheme.primary
        val bgColor = MaterialTheme.colorScheme.background
        val onSurfaceColor = MaterialTheme.colorScheme.onSurface
        
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width * 0.5f
            val centerY = size.height - 80.dp.toPx() // Precisely aligned: (24dp outer + 24dp inner + 32dp button half)
            val baseRadius = size.minDimension * 0.85f * vinylPulse
            

            // 2. Draw Micro-Grooves (Pulsing Pulse Waves)
            val grooveCount = 20 // Optimized from 32 to 20 for better performance
            for (i in 0 until grooveCount) {
                // Each groove 'i' is offset by the pulse, creating a traveling wave
                val waveProgress = (i.toFloat() / grooveCount.toFloat() + groovePulse) % 1.0f
                val grooveRadius = baseRadius * (0.2f + waveProgress * 0.8f)
                val isGreyStrip = i % 8 == 0
                
                // Fade out as it reaches the outer 20% of expansion
                val alphaMultiplier = if (waveProgress > 0.8f) (1.0f - waveProgress) * 5f else 1.0f
                // Fade in as it emerges from the 20% center
                val fadeInMultiplier = if (waveProgress < 0.2f) waveProgress * 5f else 1.0f
                
                val finalAlpha = alphaMultiplier * fadeInMultiplier * spiralAlpha
                
                drawCircle(
                    color = if (isGreyStrip) Color.Gray.copy(alpha = 0.08f * finalAlpha) else onSurfaceColor.copy(alpha = 0.03f * finalAlpha),
                    center = Offset(centerX, centerY),
                    radius = grooveRadius,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = if (isGreyStrip) 2.dp.toPx() else 0.5.dp.toPx())
                )
            }

            // 3. Draw The Spinning Reflection (Sweep Shimmer)
            rotate(spinRotation, pivot = Offset(centerX, centerY)) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        0.0f to Color.Transparent,
                        0.45f to Color.Transparent,
                        0.5f to onSurfaceColor.copy(alpha = 0.06f * spiralAlpha),
                        0.55f to Color.Transparent,
                        1.0f to Color.Transparent
                    ),
                    center = Offset(centerX, centerY),
                    radius = baseRadius
                )
                // Second opposing beam
                drawCircle(
                    brush = Brush.sweepGradient(
                        0.0f to Color.Transparent,
                        0.95f to Color.Transparent,
                        0.0f to onSurfaceColor.copy(alpha = 0.04f * spiralAlpha),
                        0.05f to Color.Transparent,
                        1.0f to Color.Transparent
                    ),
                    center = Offset(centerX, centerY),
                    radius = baseRadius
                )
            }

        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurRadius > 0.dp) {
                        try {
                            renderEffect = android.graphics.RenderEffect.createBlurEffect(
                                blurRadius.toPx(),
                                blurRadius.toPx(),
                                android.graphics.Shader.TileMode.DECAL
                            ).asComposeRenderEffect()
                        } catch (e: Exception) {
                            // Fallback if blur fails
                        }
                    }
                }
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!canEnableService) {
                androidx.compose.material3.Card(
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Permission Required",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = buildString {
                                append("Audix needs the following to apply EQ effects:")
                                if (!isPermissionGranted) append("\n• Notification Access")
                                if (!isIgnoringBatteryOptimizations) append("\n• Battery Optimization Exemption")
                            },
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!isPermissionGranted) {
                                Button(
                                    onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onErrorContainer)
                                ) {
                                    Text("Grant Access", color = MaterialTheme.colorScheme.errorContainer)
                                }
                            }
                            if (!isIgnoringBatteryOptimizations) {
                                OutlinedButton(
                                    onClick = { 
                                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    },
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onErrorContainer)
                                ) {
                                    Text("Exempt Battery", color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        }
                    }
                }
            }

            val title = currentSong?.title ?: "No Song Detected"
            val artist = currentSong?.artist ?: "Play music in Spotify or YouTube Music"
            val genre = currentSong?.genre

            com.audix.app.ui.components.HeroSection(
                title = title,
                artist = artist,
                genre = genre,
                isPlaying = isPlaying,
                isAutoEqEnabled = isAutoEqEnabled && masterEnabled,
                isDetectingGenre = isDetectingGenre,
                modifier = Modifier.padding(top = 0.dp, bottom = 8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            AnimatedVisibility(
                visible = isEqVisible,
                enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                        expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                exit = fadeOut(animationSpec = tween(150)) +
                       shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            ) {
                com.audix.app.ui.components.EqEngineCard(
                    isAutoEqEnabled = isAutoEqEnabled && masterEnabled,
                    onAutoEqChange = { enabled ->
                        isAutoEqEnabled = enabled
                        coroutineScope.launch {
                            userPreferencesRepository.saveAutoEqEnabled(enabled)
                            if (enabled) {
                                userPreferencesRepository.saveCustomTuningEnabled(false)
                                if (!masterEnabled) {
                                    masterEnabled = true
                                    userPreferencesRepository.saveMasterEnabled(true)
                                }
                            }
                        }
                    },
                    eqIntensity = eqIntensityPosition,
                    onIntensityChange = { eqIntensityPosition = it },
                    onIntensityChangeFinished = {
                        coroutineScope.launch { userPreferencesRepository.saveEqIntensity(eqIntensityPosition) }
                    },
                    isExpanded = isEqExpanded,
                    onExpandedChange = { isEqExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (isEqVisible && masterEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
            }

            AnimatedVisibility(
                visible = isCustomVisible,
                enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                        expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                exit = fadeOut(animationSpec = tween(150)) +
                       shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            ) {
                com.audix.app.ui.components.CustomTuningCard(
                    isCustomTuningEnabled = isCustomTuningEnabled && masterEnabled,
                    onCustomTuningChange = { enabled ->
                        isCustomTuningEnabled = enabled
                        coroutineScope.launch {
                            userPreferencesRepository.saveCustomTuningEnabled(enabled)
                            if (enabled) {
                                userPreferencesRepository.saveAutoEqEnabled(false)
                                if (!masterEnabled) {
                                    masterEnabled = true
                                    userPreferencesRepository.saveMasterEnabled(true)
                                }
                            }
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
                    isExpanded = isCustomExpanded,
                    onExpandedChange = { isCustomExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (isCustomVisible && masterEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
            }

            AnimatedVisibility(
                visible = isSpatialVisible,
                enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                        expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                exit = fadeOut(animationSpec = tween(150)) +
                       shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            ) {
                com.audix.app.ui.components.SpatialAudioCard(
                    isSpatialEnabled = isSpatialEnabled && masterEnabled,
                    isHeadphonesConnected = isHeadphonesConnected,
                    onSpatialEnabledChange = { enabled ->
                        isSpatialEnabled = enabled
                        coroutineScope.launch {
                            userPreferencesRepository.saveSpatialEnabled(enabled)
                            if (enabled) {
                                if (spatialLevelPosition == 0) {
                                    spatialLevelPosition = 3
                                    userPreferencesRepository.saveSpatialLevel(3)
                                }
                                if (!masterEnabled) {
                                    masterEnabled = true
                                    userPreferencesRepository.saveMasterEnabled(true)
                                }
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
                    isExpanded = isSpatialExpanded,
                    onExpandedChange = { isSpatialExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(120.dp))
        }

        if (!isEqExpanded && !isSpatialExpanded && !isCustomExpanded) {
            val powerPulse by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "powerPulse"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                IconButton(
                    onClick = { 
                        openedFromMainSettings = true
                        settingsSheetState = SettingsSheetState.Main 
                    },
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings, 
                        contentDescription = "Settings", 
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }

                // Master Power Button
                val localView = LocalView.current
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                        .size(64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isAnyEnhancementEnabled) {
                        Canvas(modifier = Modifier.size(54.dp)) {
                            drawCircle(
                                color = primaryRed.copy(alpha = 0.15f * (2f - powerPulse)),
                                radius = size.minDimension / 2f * powerPulse
                            )
                        }
                    }
                    
                    IconButton(
                        onClick = {
                            if (canEnableService) {
                                localView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                val newState = !masterEnabled
                                masterEnabled = newState
                                
                                if (!newState) {
                                    isEqExpanded = false
                                    isSpatialExpanded = false
                                    isCustomExpanded = false
                                }
                                
                                coroutineScope.launch {
                                    userPreferencesRepository.saveMasterEnabled(newState)
                                }
                            } else {
                                // Provide negative feedback if permissions missing
                                localView.performHapticFeedback(HapticFeedbackConstants.REJECT)
                            }
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(
                                if (masterEnabled) primaryRed.copy(alpha = 0.1f)
                                else Color.Transparent
                            )
                            .border(
                                width = 1.dp,
                                color = if (masterEnabled) primaryRed.copy(alpha = 0.3f) 
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Master Power",
                            tint = if (masterEnabled) primaryRed else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                IconButton(
                    onClick = { 
                        openedFromMainSettings = false
                        settingsSheetState = SettingsSheetState.Guide 
                    },
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.HelpOutline, 
                        contentDescription = "Help Guide", 
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }
        }

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
                    modifier = Modifier.weight(1f).padding(start = 24.dp)
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
        
        if (settingsSheetState != SettingsSheetState.None) {
            ModalBottomSheet(
                onDismissRequest = { settingsSheetState = SettingsSheetState.None },
                sheetState = sheetState,
                containerColor = Color.Transparent,
                scrimColor = Color.Black.copy(alpha = 0.32f),
                tonalElevation = 0.dp,
                dragHandle = { 
                    Box(
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .size(width = 36.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                    )
                }
            ) {
                SettingsSheetContent(
                    state = settingsSheetState,
                    isPermissionGranted = isPermissionGranted,
                    isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                    geminiApiKey = geminiApiKey ?: "",
                    tempGeminiApiKey = tempGeminiApiKey,
                    onTempApiKeyChange = { tempGeminiApiKey = it },
                    onSaveApiKey = {
                        coroutineScope.launch {
                            userPreferencesRepository.saveGeminiApiKey(tempGeminiApiKey.trim())
                        }
                    },
                    onBack = {
                        if (settingsSheetState == SettingsSheetState.Main || !openedFromMainSettings) {
                            settingsSheetState = SettingsSheetState.None
                        } else {
                            settingsSheetState = SettingsSheetState.Main
                        }
                    },
                    onNavigate = { settingsSheetState = it },
                    onShowOnboarding = { showOnboarding = true }
                )
            }
        }
    }
}

@Composable
fun SettingsSheetContent(
    state: SettingsSheetState,
    isPermissionGranted: Boolean,
    isIgnoringBatteryOptimizations: Boolean,
    geminiApiKey: String,
    tempGeminiApiKey: String,
    onTempApiKeyChange: (String) -> Unit,
    onSaveApiKey: () -> Unit,
    onBack: () -> Unit,
    onNavigate: (SettingsSheetState) -> Unit,
    onShowOnboarding: () -> Unit
) {
    BackHandler(enabled = true) {
        onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                    )
                ),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = when(state) {
                        SettingsSheetState.Main -> "Settings"
                        SettingsSheetState.ApiKey -> "Gemini API Key"
                        SettingsSheetState.About -> "About"
                        SettingsSheetState.Guide -> "User Guide"
                        else -> ""
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            
            Spacer(modifier = Modifier.height(16.dp))

            Crossfade(
                targetState = state,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "SettingsTransition"
            ) { targetState ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    when (targetState) {
                        SettingsSheetState.Main -> MainSettingsPage(
                            isPermissionGranted = isPermissionGranted,
                            isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                            geminiApiKey = geminiApiKey,
                            onNavigate = onNavigate,
                            onShowOnboarding = onShowOnboarding
                        )
                        SettingsSheetState.ApiKey -> ApiKeySettingsPage(
                            tempGeminiApiKey = tempGeminiApiKey,
                            onTempApiKeyChange = onTempApiKeyChange,
                            onSaveApiKey = {
                                onSaveApiKey()
                                onNavigate(SettingsSheetState.Main)
                            }
                        )
                        SettingsSheetState.About -> AboutSettingsPage()
                        SettingsSheetState.Guide -> GuideSettingsPage()
                        else -> {}
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun MainSettingsPage(
    isPermissionGranted: Boolean,
    isIgnoringBatteryOptimizations: Boolean,
    geminiApiKey: String,
    onNavigate: (SettingsSheetState) -> Unit,
    onShowOnboarding: () -> Unit
) {
    val context = LocalContext.current
    
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsSectionHeader("Core Permissions")
        
        AnimatedSettingsClickRow(
            title = "Notification Access",
            subtitle = if (isPermissionGranted) "Access Granted" else "Required",
            icon = Icons.Default.Notifications,
            isStatusActive = isPermissionGranted,
            onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        )
        
        AnimatedSettingsClickRow(
            title = "Battery Optimization",
            subtitle = if (isIgnoringBatteryOptimizations) "Optimized" else "Action Recommended",
            icon = Icons.Default.BatteryChargingFull,
            isStatusActive = isIgnoringBatteryOptimizations,
            onClick = {
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            }
        )

        AnimatedSettingsClickRow(
            title = "Background Autostart",
            subtitle = "Enable for stable experience",
            icon = Icons.Default.Autorenew,
            isStatusActive = null,
            onClick = { openAutostartSettings(context) }
        )

        Spacer(modifier = Modifier.height(8.dp))
        SettingsSectionHeader("Personalization")

        val maskedKey = if (geminiApiKey.isBlank()) "Using Default Key" 
                       else geminiApiKey.take(4) + "..." + geminiApiKey.takeLast(4)
        
        AnimatedSettingsClickRow(
            title = "Gemini API Key",
            subtitle = maskedKey,
            icon = Icons.Default.AutoAwesome,
            isStatusActive = geminiApiKey.isNotBlank(),
            onClick = { onNavigate(SettingsSheetState.ApiKey) }
        )

        Spacer(modifier = Modifier.height(8.dp))
        SettingsSectionHeader("Support & Info")

        
        AnimatedSettingsClickRow(
            title = "About Audix",
            subtitle = "Version ${BuildConfig.VERSION_NAME}",
            icon = Icons.Default.Info,
            onClick = { onNavigate(SettingsSheetState.About) }
        )
    }
}

@Composable
fun SettingsSectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
    )
}

@Composable
fun AnimatedSettingsClickRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isStatusActive: Boolean? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .border(
                width = 0.8.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick
            )
            .padding(16.dp)
    ) {
        AnimatedSettingsIcon(
            icon = icon,
            isActive = isStatusActive ?: false,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = when(isStatusActive) {
                    true -> MaterialTheme.colorScheme.primary
                    false -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun AnimatedSettingsIcon(
    icon: ImageVector,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isActive) MaterialTheme.colorScheme.primary 
                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

private fun openAutostartSettings(context: android.content.Context) {
    val intents = listOf(
        Intent().setComponent(android.content.ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        Intent().setComponent(android.content.ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
        Intent().setComponent(android.content.ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")),
        Intent().setComponent(android.content.ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
        Intent().setComponent(android.content.ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
        Intent().setComponent(android.content.ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),
        Intent().setComponent(android.content.ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
        Intent().setComponent(android.content.ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"))
    )

    var started = false
    for (intent in intents) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            started = true
            break
        } catch (e: Exception) { /* continue */ }
    }

    if (!started) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                data = Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to generic settings
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}

@Composable
fun ApiKeySettingsPage(
    tempGeminiApiKey: String,
    onTempApiKeyChange: (String) -> Unit,
    onSaveApiKey: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "Enter your own Gemini API key for smarter genre detection. Leave blank to use the default app key.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        OutlinedTextField(
            value = tempGeminiApiKey,
            onValueChange = onTempApiKeyChange,
            label = { Text("API Key") },
            placeholder = { Text("AIza...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            leadingIcon = { Icon(Icons.Default.VpnKey, null) }
        )
        
        Button(
            onClick = onSaveApiKey,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Save API Key", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun AboutSettingsPage() {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "audix",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
            Text(
                "VERSION ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            "Intelligent real time audio enhancement for an immersive listening experience",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AboutLinkRow(
                title = "Privacy Policy",
                icon = Icons.Outlined.Lock,
                onClick = { 
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/AtharvaP-251/Audix-Lab/blob/main/PRIVACY_POLICY.md"))
                    context.startActivity(intent)
                }
            )
            AboutLinkRow(
                title = "GitHub Repository",
                icon = Icons.Outlined.Code,
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/AtharvaP-251/Audix-Lab"))
                    context.startActivity(intent)
                }
            )
        }
        
        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Developed by Audix Labs",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun GuideSettingsPage() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        GuideStepItem("1", "Play music in Spotify or YouTube Music. Audix detects your tracks automatically.")
        GuideStepItem("2", "Enable AutoEQ for an intelligent, zero-config experience.")
        GuideStepItem("3", "Use Spatial Audio for an immersive 3D soundscape (headphone required).")
        GuideStepItem("4", "Take control with Custom Tuning for manual adjustment.")
        GuideStepItem("5", "Ensure background permissions are granted for zero interruptions.")
    }
}

@Composable
fun GuideStepItem(number: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "$number.",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 1.dp)
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.align(Alignment.CenterVertically))
    }
}

@Composable
fun AboutLinkRow(title: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            modifier = Modifier.size(20.dp)
        )
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun OnboardingStep(number: String, title: String, description: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "$number.",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 1.dp)
        )
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
