package com.audix.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.audix.app.MainActivity
import com.audix.app.audio.EQPreset
import com.audix.app.audio.EQPresetLibrary
import com.audix.app.audio.EqEngine
import com.audix.app.data.AppDatabase
import com.audix.app.data.UserPreferencesRepository
import com.audix.app.domain.GenreDetector
import com.audix.app.state.SongState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.room.Room

open class AudioEngineServiceLocal : Service() {

    private val job = kotlinx.coroutines.SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private var currentSongJob: Job? = null
    private var lastJobId: Long = 0
    private var lastProcessedTitle: String? = null
    private var lastProcessedArtist: String? = null
    private var wasLastJobDebounced: Boolean = false
    private var prefReapplyJob: Job? = null

    private lateinit var eqEngine: EqEngine
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var genreDetector: GenreDetector
    private lateinit var db: AppDatabase
    private lateinit var headphoneDetector: com.audix.app.audio.HeadphoneDetector

    override fun onCreate() {
        super.onCreate()
        startForeground(1, createNotification("Audix EQ Engine Active", "Waiting for music..."))

        db = androidx.room.Room.databaseBuilder(applicationContext, AppDatabase::class.java, "audix_db").build()
        genreDetector = GenreDetector(db.songCacheDao())
        userPreferencesRepository = UserPreferencesRepository(applicationContext)
        eqEngine = EqEngine()
        eqEngine.initialize()
        eqEngine.notifySessionOpen(this)

        headphoneDetector = com.audix.app.audio.HeadphoneDetector(applicationContext)
        headphoneDetector.start()

        observeState()
        observePreferences()
        observeHardware()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
        eqEngine.release()
        headphoneDetector.stop()
        SongState.isDetectingGenre.value = false
    }

    private fun observeState() {
        // Observe song changes
        scope.launch {
            SongState.currentSong.collect { song ->
                if (song != null) {
                    // Only trigger if it's a new song (title or artist changed)
                    if (song.title != lastProcessedTitle || song.artist != lastProcessedArtist) {
                        Log.d("AudioEngine", "Flow detected song change: ${song.title}")
                        handleSongChange(song.title, song.artist)
                    }
                }
            }
        }

        // Observe playback state
        scope.launch {
            SongState.isPlaying.collect { isPlaying ->
                val current = SongState.currentSong.value
                val isAllowed = if (current != null) {
                    SongDetectionService.ALLOWED_PACKAGES.contains(current.packageName)
                } else false

                if (!isPlaying || !isAllowed) {
                    eqEngine.setEnabled(false)
                    val titleText = if (current != null && isAllowed) "${current.title} by ${current.artist}" else "Waiting for music..."
                    updateNotification("EQ Disabled", titleText)
                } else {
                    Log.d("AudioEngine", "Flow detected playback start")
                    forceReapply()
                }
            }
        }
    }

    private fun observePreferences() {
        scope.launch {
            userPreferencesRepository.eqIntensityFlow.collect { eqEngine.currentIntensity = it }
        }
        scope.launch {
            userPreferencesRepository.customTuningEnabledFlow.collect {
                eqEngine.isCustomTuningEnabled = it
                debouncedForceReapply()
            }
        }
        scope.launch {
            userPreferencesRepository.autoEqEnabledFlow.collect {
                debouncedForceReapply()
            }
        }
        scope.launch {
            userPreferencesRepository.customBassFlow.collect { eqEngine.customBass = it }
        }
        scope.launch {
            userPreferencesRepository.customVocalsFlow.collect { eqEngine.customVocals = it }
        }
        scope.launch {
            userPreferencesRepository.customTrebleFlow.collect { eqEngine.customTreble = it }
        }
        scope.launch {
            userPreferencesRepository.spatialEnabledFlow.collect {
                eqEngine.isSpatialEnabled = it
                debouncedForceReapply()
            }
        }
        scope.launch {
            userPreferencesRepository.spatialLevelFlow.collect {
                eqEngine.spatialLevel = it
                debouncedForceReapply()
            }
        }
        scope.launch {
            userPreferencesRepository.masterEnabledFlow.collect {
                debouncedForceReapply()
            }
        }
    }

    private fun observeHardware() {
        scope.launch {
            headphoneDetector.isHeadphonesConnected.collect { isConnected ->
                SongState.isHeadphonesConnected.value = isConnected
                eqEngine.isHeadphonesConnected = isConnected
                Log.d("AudioEngine", "Headphone state synced to engine: $isConnected")
            }
        }
    }

    /**
     * Debounced reapply to avoid racing when multiple preferences fire simultaneously
     * (e.g., when auto-EQ and custom-tuning toggle together).
     */
    private fun debouncedForceReapply() {
        prefReapplyJob?.cancel()
        prefReapplyJob = scope.launch {
            delay(100) // Debounce: wait for rapid preference changes to settle
            forceReapply()
        }
    }

    private fun forceReapply() {
        val current = SongState.currentSong.value
        if (current != null) {
            applyEqForSong(current.title, current.artist, debounce = false)
        } else {
            eqEngine.applyPreset(EQPreset("Flat", emptyMap()))
            eqEngine.setEnabled(false)
        }
    }

    private fun handlePlaybackStateChange(isPlaying: Boolean, packageName: String) {
        val isAllowed = SongDetectionService.ALLOWED_PACKAGES.contains(packageName)
        if (!isPlaying || !isAllowed) {
            eqEngine.setEnabled(false)
            val current = SongState.currentSong.value
            val titleText = if (current != null && isAllowed) "${current.title} by ${current.artist}" else "Waiting for music..."
            updateNotification("EQ Disabled", titleText)
        } else {
            // Playing and allowed — ensure EQ is active
            forceReapply()
        }
    }

    private fun handleSongChange(title: String, artist: String) {
        // Reinitialize the equalizer on every song change to fix OEM audio stack issues
        // (e.g. Motorola devices where the global session becomes stale between songs)
        try {
            eqEngine.reinitialize()
            eqEngine.notifySessionOpen(this)
        } catch (e: Exception) {
            Log.w("AudioEngine", "Reinitialize failed, continuing with existing equalizer: ${e.message}")
        }
        applyEqForSong(title, artist, debounce = true)
    }

    private fun applyEqForSong(title: String, artist: String, debounce: Boolean) {
        // SMARTER CANCELLATION:
        // If we are already processing this EXACT song, and the previous job was already immediate (not debounced)
        // OR the new job is also a debounced one, then don't interrupt it.
        if (currentSongJob?.isActive == true && lastProcessedTitle == title && lastProcessedArtist == artist) {
            if (debounce || !wasLastJobDebounced) {
                Log.d("AudioEngine", "Redundant detection request for $title - ignoring")
                return
            }
        }

        // Increment job ID so OLD jobs don't clobber NEW job states
        val jobId = ++lastJobId
        lastProcessedTitle = title
        lastProcessedArtist = artist
        wasLastJobDebounced = debounce
        
        // Cancel the previous job (either for a different song, or to upgrade to non-debounced)
        currentSongJob?.cancel()

        currentSongJob = scope.launch {
            try {
                // Starting fresh genre detection — provide IMMEDIATE feedback
                if (jobId == lastJobId) {
                    SongState.isDetectingGenre.value = true
                }

                val (preferences, masterEnabled) = withContext(Dispatchers.IO) {
                    val custom = userPreferencesRepository.customTuningEnabledFlow.first()
                    val auto = userPreferencesRepository.autoEqEnabledFlow.first()
                    val spatial = userPreferencesRepository.spatialEnabledFlow.first()
                    val level = userPreferencesRepository.spatialLevelFlow.first()
                    val apiKey = userPreferencesRepository.geminiApiKeyFlow.first()
                    val master = userPreferencesRepository.masterEnabledFlow.first()
                    Pair(Triple(custom, auto, spatial), Pair(level, apiKey)) to master
                }
                val isCustomTuning = preferences.first.first
                val isAutoEq = preferences.first.second
                val isSpatial = preferences.first.third
                val spatialLevel = preferences.second.first
                val geminiApiKey = preferences.second.second

                if (!masterEnabled) {
                    if (jobId == lastJobId) SongState.isDetectingGenre.value = false
                    eqEngine.applyPreset(EQPreset("Flat", emptyMap()))
                    eqEngine.setEnabled(false)
                    updateNotification("EQ Disabled", "$title by $artist")
                    return@launch
                }

                // Sync spatial state to engine before any processing
                eqEngine.isSpatialEnabled = isSpatial
                eqEngine.spatialLevel = spatialLevel

                // 1. Initial Engine State: If Spatial Audio is ON, we must enable the engine 
                // IMMEDIATELY with a Flat base so Layer A/B can operate while detection runs.
                if (isSpatial) {
                    eqEngine.applyPreset(EQPreset("Flat", emptyMap()))
                    eqEngine.setEnabled(true)
                }

                // Custom tuning overrides auto-EQ
                if (isCustomTuning) {
                    if (jobId == lastJobId) SongState.isDetectingGenre.value = false
                    eqEngine.applyPreset(EQPreset("Flat", emptyMap()))
                    updateNotification("Manual EQ applied", "$title by $artist")
                    return@launch
                }

                if (!isAutoEq) {
                    if (jobId == lastJobId) SongState.isDetectingGenre.value = false
                    
                    // If spatial is ON, we already applied Flat + Enable above.
                    // If spatial is OFF, we must disable the engine.
                    if (isSpatial) {
                        updateNotification("Spatial Audio active", "$title by $artist")
                    } else {
                        eqEngine.applyPreset(EQPreset("Flat", emptyMap()))
                        eqEngine.setEnabled(false)
                        updateNotification("EQ Disabled", "$title by $artist")
                    }
                    return@launch
                }

                // Check if we already have the genre for this song (and it's a valid result, not an error)
                val cs = SongState.currentSong.value
                val existingGenre = cs?.genre
                val hasValidCachedGenre = existingGenre != null && 
                                        !existingGenre.startsWith("Error:") && 
                                        existingGenre != "Rate Limit Exceeded" && 
                                        existingGenre != "Offline" &&
                                        cs.title == title

                if (hasValidCachedGenre) {
                    if (jobId == lastJobId) SongState.isDetectingGenre.value = false
                    val preset = EQPresetLibrary.getPresetForGenre(existingGenre!!) ?: EQPreset("Flat", emptyMap())
                    eqEngine.applyPreset(preset)
                    updateNotification("EQ: $existingGenre", "$title by $artist")
                    return@launch
                }

                updateNotification("Detecting genre...", "$title by $artist")

                if (debounce) {
                    delay(600)
                    // Re-check if song changed during delay
                    val nowPlaying = SongState.currentSong.value
                    if (nowPlaying == null || nowPlaying.title != title) return@launch
                }

                // Use withTimeout to ensure we NEVER hang "forever"
                val resultGenre = kotlinx.coroutines.withTimeoutOrNull(15000) {
                    // Check DB first
                    val cached = withContext(Dispatchers.IO) {
                        try { db.songCacheDao().getGenreForSong(title, artist) } catch (e: Exception) { null }
                    }
                    
                    if (cached != null && !cached.startsWith("Error:")) {
                        cached
                    } else if (!isNetworkAvailable()) {
                        "Offline"
                    } else {
                        withContext(Dispatchers.IO) {
                            try { genreDetector.detectGenre(title, artist, geminiApiKey) } catch (e: Exception) { "Error: Failed" }
                        }
                    }
                } ?: "Error: Timeout"

                if (jobId == lastJobId) {
                    updateSongStateAndEq(title, artist, resultGenre)
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.e("AudioEngine", "Fatal error in applyEqForSong: ${e.message}", e)
                }
            } finally {
                if (jobId == lastJobId) {
                    SongState.isDetectingGenre.value = false
                }
            }
        }
    }

    private fun updateSongStateAndEq(title: String, artist: String, genre: String?) {
        val detectedGenre = genre ?: "Unknown"
        
        // Update SongState
        val current = SongState.currentSong.value
        if (current != null && current.title == title) {
            SongState.currentSong.value = current.copy(genre = detectedGenre)
        } else if (current == null) {
            SongState.currentSong.value = com.audix.app.state.SongInfo(title, artist, genre = detectedGenre)
        }

        // Apply EQ
        val preset = when {
            detectedGenre.startsWith("Error:") || detectedGenre == "Unknown" || detectedGenre == "Offline" -> 
                EQPreset("Flat", emptyMap())
            else -> EQPresetLibrary.getPresetForGenre(detectedGenre) ?: EQPreset("Flat", emptyMap())
        }
        
        eqEngine.applyPreset(preset)
        
        val notifyTitle = when {
            detectedGenre == "Offline" -> "Offline — Flat EQ"
            detectedGenre.startsWith("Error:") -> "Detection failed — Flat EQ"
            else -> "EQ: $detectedGenre"
        }
        updateNotification(notifyTitle, "$title by $artist")
    }

    private fun isNetworkAvailable(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: Exception) {
            Log.w("AudioEngine", "Network check failed: ${e.message}")
            true // assume connected if check fails to avoid blocking
        }
    }

    private fun createNotification(title: String, text: String): Notification {
        val channelId = "audix_eq_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Audix EQ Engine",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, createNotification(title, text))
    }
}
