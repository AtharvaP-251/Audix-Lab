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

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private var currentSongJob: Job? = null
    private var prefReapplyJob: Job? = null

    private lateinit var eqEngine: EqEngine
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var genreDetector: GenreDetector
    private lateinit var db: AppDatabase

    private val songReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.audix.app.SONG_CHANGED" -> {
                    val title = intent.getStringExtra("EXTRA_TITLE") ?: return
                    val artist = intent.getStringExtra("EXTRA_ARTIST") ?: return
                    handleSongChange(title, artist)
                }
                "com.audix.app.PLAYBACK_STATE_CHANGED" -> {
                    val isPlaying = intent.getBooleanExtra("EXTRA_IS_PLAYING", false)
                    val packageName = intent.getStringExtra("EXTRA_PACKAGE_NAME") ?: ""
                    handlePlaybackStateChange(isPlaying, packageName)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(1, createNotification("Audix EQ Engine Active", "Waiting for music..."))

        db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "audix_db").build()
        genreDetector = GenreDetector(db.songCacheDao())
        userPreferencesRepository = UserPreferencesRepository(applicationContext)
        eqEngine = EqEngine()
        eqEngine.initialize()

        val filter = IntentFilter().apply {
            addAction("com.audix.app.SONG_CHANGED")
            addAction("com.audix.app.PLAYBACK_STATE_CHANGED")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(songReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(songReceiver, filter)
        }

        observePreferences()

        // Apply EQ for the current song in case it's already playing when service starts
        val current = SongState.currentSong.value
        if (current != null) {
            handleSongChange(current.title, current.artist)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
        try { unregisterReceiver(songReceiver) } catch (e: Exception) { /* already unregistered */ }
        eqEngine.release()
        SongState.isDetectingGenre.value = false
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
        } catch (e: Exception) {
            Log.w("AudioEngine", "Reinitialize failed, continuing with existing equalizer: ${e.message}")
        }
        applyEqForSong(title, artist, debounce = true)
    }

    private fun applyEqForSong(title: String, artist: String, debounce: Boolean) {
        // Cancel any in-flight detection for a previous song
        currentSongJob?.cancel()

        currentSongJob = scope.launch {
            val isCustomTuning = withContext(Dispatchers.IO) {
                userPreferencesRepository.customTuningEnabledFlow.first()
            }
            val isAutoEq = withContext(Dispatchers.IO) {
                userPreferencesRepository.autoEqEnabledFlow.first()
            }

            // Custom tuning overrides auto-EQ
            if (isCustomTuning) {
                SongState.isDetectingGenre.value = false
                eqEngine.applyPreset(EQPreset("Flat", emptyMap()))
                updateNotification("Manual EQ applied", "$title by $artist")
                return@launch
            }

            if (!isAutoEq) {
                SongState.isDetectingGenre.value = false
                eqEngine.applyPreset(EQPreset("Flat", emptyMap()))
                eqEngine.setEnabled(false)
                updateNotification("EQ Disabled", "$title by $artist")
                return@launch
            }

            // Check if we already have the genre for this song (and it's a valid result, not an error)
            val cs = SongState.currentSong.value
            val existingGenre = cs?.genre
            val hasValidCachedGenre = existingGenre != null &&
                    !existingGenre.startsWith("Error:") &&
                    existingGenre != "Rate Limit Exceeded" &&
                    cs.title == title

            if (hasValidCachedGenre) {
                // Already have a clean genre — just apply it
                SongState.isDetectingGenre.value = false
                val preset = EQPresetLibrary.getPresetForGenre(existingGenre!!) ?: EQPreset("Flat", emptyMap())
                eqEngine.applyPreset(preset)
                updateNotification("EQ: $existingGenre", "$title by $artist")
                return@launch
            }

            // Starting fresh genre detection
            SongState.isDetectingGenre.value = true
            updateNotification("Detecting genre...", "$title by $artist")

            if (debounce) {
                // Debounce rapid notification updates from media players
                delay(600)
            }

            // Check if this song is in the DB cache before hitting the API
            val cachedGenre = withContext(Dispatchers.IO) {
                try { db.songCacheDao().getGenreForSong(title, artist) } catch (e: Exception) { null }
            }

            val detectedGenre: String?
            if (cachedGenre != null && !cachedGenre.startsWith("Error:")) {
                Log.d("AudioEngine", "DB cache hit: $cachedGenre for $title")
                detectedGenre = cachedGenre
            } else {
                // Check connectivity before hitting the network
                if (!isNetworkAvailable()) {
                    Log.w("AudioEngine", "Network unavailable for genre detection")
                    SongState.isDetectingGenre.value = false
                    eqEngine.applyPreset(EQPreset("Flat", emptyMap()))
                    updateNotification("Offline — Flat EQ active", "$title by $artist")
                    // Update SongState so UI shows a useful state
                    val currentSong = SongState.currentSong.value
                    if (currentSong != null && currentSong.title == title) {
                        SongState.currentSong.value = currentSong.copy(genre = null)
                    }
                    return@launch
                }
                detectedGenre = withContext(Dispatchers.IO) {
                    genreDetector.detectGenre(title, artist)
                }
            }

            SongState.isDetectingGenre.value = false

            if (detectedGenre != null && !detectedGenre.startsWith("Error:") && detectedGenre != "Rate Limit Exceeded") {
                // Update SongState with detected genre
                val currentSong = SongState.currentSong.value
                if (currentSong != null && currentSong.title == title) {
                    SongState.currentSong.value = currentSong.copy(genre = detectedGenre)
                } else if (currentSong == null) {
                    SongState.currentSong.value = com.audix.app.state.SongInfo(title, artist, genre = detectedGenre)
                }

                val preset = EQPresetLibrary.getPresetForGenre(detectedGenre)
                if (preset != null) {
                    eqEngine.applyPreset(preset)
                    updateNotification("EQ: $detectedGenre", "$title by $artist")
                } else {
                    eqEngine.applyPreset(EQPreset("Flat", emptyMap()))
                    updateNotification("Genre: $detectedGenre (Flat EQ)", "$title by $artist")
                }
            } else {
                // Graceful degradation: apply flat EQ and show the error clearly
                eqEngine.applyPreset(EQPreset("Flat", emptyMap()))
                val errorMsg = when {
                    detectedGenre == "Rate Limit Exceeded" -> "Rate limit — Flat EQ active"
                    detectedGenre?.startsWith("Error:") == true -> "Detection failed — Flat EQ active"
                    else -> "Unable to detect genre"
                }
                updateNotification(errorMsg, "$title by $artist")
                Log.w("AudioEngine", "Genre detection result: $detectedGenre — applying flat EQ")
            }
        }
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
