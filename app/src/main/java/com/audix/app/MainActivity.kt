package com.audix.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Switch
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.audix.app.audio.EqEngine
import com.audix.app.audio.EQPresetLibrary
import androidx.room.Room
import com.audix.app.data.AppDatabase
import com.audix.app.data.UserPreferencesRepository
import com.audix.app.domain.GenreDetector
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.audix.app.state.SongState
import com.audix.app.ui.theme.AudixTheme

class MainActivity : ComponentActivity() {
    private val eqEngine = EqEngine()
    private lateinit var db: AppDatabase
    private lateinit var genreDetector: GenreDetector
    private lateinit var userPreferencesRepository: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "audix_db").build()
        genreDetector = GenreDetector(db.songCacheDao())
        userPreferencesRepository = UserPreferencesRepository(applicationContext)
        eqEngine.initialize()
        
        enableEdgeToEdge()
        setContent {
            AudixTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    EqControls(
                        eqEngine = eqEngine,
                        genreDetector = genreDetector,
                        userPreferencesRepository = userPreferencesRepository,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        eqEngine.release()
    }
}

@Composable
fun EqControls(eqEngine: EqEngine, genreDetector: GenreDetector, userPreferencesRepository: UserPreferencesRepository, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isAutoEqEnabled by remember { mutableStateOf(true) }
    var isPermissionGranted by remember {
        mutableStateOf(NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isPermissionGranted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Observe active song state
    val currentSong by SongState.currentSong.collectAsState()
    val isServiceConnected by SongState.isServiceConnected.collectAsState()

    val eqIntensity by userPreferencesRepository.eqIntensityFlow.collectAsState(initial = 1.0f)
    var sliderPosition by remember(eqIntensity) { mutableStateOf(eqIntensity) }

    val bassBoostIntensity by userPreferencesRepository.bassBoostFlow.collectAsState(initial = 0.0f)
    var bassBoostPosition by remember(bassBoostIntensity) { mutableStateOf(bassBoostIntensity) }

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(eqIntensity) {
        eqEngine.currentIntensity = eqIntensity
    }

    LaunchedEffect(bassBoostIntensity) {
        eqEngine.currentBassBoost = bassBoostIntensity
    }

    LaunchedEffect(currentSong, isAutoEqEnabled) {
        val song = currentSong
        if (song != null && song.genre == null) {
            val detectedGenre = genreDetector.detectGenre(song.title, song.artist)
            if (detectedGenre != null) {
                if (SongState.currentSong.value?.title == song.title) {
                    SongState.currentSong.value = song.copy(genre = detectedGenre)
                    val preset = EQPresetLibrary.getPresetForGenre(detectedGenre)
                    if (preset != null && isAutoEqEnabled) {
                        eqEngine.applyPreset(preset)
                    }
                }
            }
        } else if (song != null && song.genre != null) {
            val preset = EQPresetLibrary.getPresetForGenre(song.genre!!)
            if (preset != null && isAutoEqEnabled) {
                eqEngine.applyPreset(preset)
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
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
        
        Text(text = "EQ Intensity: ${(sliderPosition * 100).toInt()}%")
        androidx.compose.material3.Slider(
            value = sliderPosition,
            onValueChange = { newValue ->
                sliderPosition = newValue
                eqEngine.currentIntensity = newValue
            },
            onValueChangeFinished = {
                coroutineScope.launch {
                    userPreferencesRepository.saveEqIntensity(sliderPosition)
                }
            },
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
                    if (it) {
                        if (currentSong?.genre != null) {
                            val preset = EQPresetLibrary.getPresetForGenre(currentSong!!.genre!!)
                            if (preset != null) {
                                eqEngine.applyPreset(preset)
                            }
                        }
                    } else {
                        eqEngine.applyPreset(com.audix.app.audio.EQPreset("Flat", emptyMap()))
                        if (eqEngine.currentBassBoost == 0f) {
                            eqEngine.setEnabled(false)
                        }
                    }
                }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Bass Boost: ${(bassBoostPosition * 100).toInt()}%")
        androidx.compose.material3.Slider(
            value = bassBoostPosition,
            onValueChange = { newValue ->
                bassBoostPosition = newValue
                eqEngine.currentBassBoost = newValue
            },
            onValueChangeFinished = {
                coroutineScope.launch {
                    userPreferencesRepository.saveBassBoost(bassBoostPosition)
                }
            },
            valueRange = 0f..1f,
            steps = 9,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun EqControlsPreview() {
    AudixTheme {
        EqControls(
            EqEngine(), 
            GenreDetector(object : com.audix.app.data.SongCacheDao {
                override suspend fun getGenreForSong(title: String, artist: String): String? = null
                override suspend fun insert(songCache: com.audix.app.data.SongCache) {}
            }),
            UserPreferencesRepository(LocalContext.current)
        )
    }
}