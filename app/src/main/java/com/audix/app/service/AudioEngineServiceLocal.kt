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
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.audix.app.MainActivity
import com.audix.app.audio.EQPresetLibrary
import com.audix.app.audio.EqEngine
import com.audix.app.data.AppDatabase
import com.audix.app.data.UserPreferencesRepository
import com.audix.app.domain.GenreDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.room.Room

open class AudioEngineServiceLocal : Service() {

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private var currentSongJob: Job? = null

    private lateinit var eqEngine: EqEngine
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var genreDetector: GenreDetector
    private lateinit var db: AppDatabase

    private val songReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.audix.app.SONG_CHANGED") {
                val title = intent.getStringExtra("EXTRA_TITLE") ?: return
                val artist = intent.getStringExtra("EXTRA_ARTIST") ?: return
                handleSongChange(title, artist)
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

        // Register custom broadcast receiver for local IPC
        val filter = IntentFilter("com.audix.app.SONG_CHANGED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(songReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(songReceiver, filter)
        }

        observePreferences()
        
        // Apply EQ natively for the current song in case it's lingering
        val current = com.audix.app.state.SongState.currentSong.value
        if (current != null) {
            handleSongChange(current.title, current.artist)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Keeps the service sticky so it restarts if memory gets low
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
        unregisterReceiver(songReceiver)
        eqEngine.release()
    }

    private fun observePreferences() {
        scope.launch {
            userPreferencesRepository.eqIntensityFlow.collect { eqEngine.currentIntensity = it }
        }
        scope.launch {
            userPreferencesRepository.customTuningEnabledFlow.collect { 
                eqEngine.isCustomTuningEnabled = it
                forceReapply() 
            }
        }
        scope.launch {
            userPreferencesRepository.autoEqEnabledFlow.collect { 
                forceReapply() 
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

    private fun forceReapply() {
        val current = com.audix.app.state.SongState.currentSong.value
        if (current != null) {
            applyEqForSong(current.title, current.artist, debounce = false)
        } else {
            eqEngine.applyPreset(com.audix.app.audio.EQPreset("Flat", emptyMap()))
            eqEngine.setEnabled(false)
        }
    }

    private fun handleSongChange(title: String, artist: String) {
        applyEqForSong(title, artist, debounce = true)
    }

    private fun applyEqForSong(title: String, artist: String, debounce: Boolean) {
        currentSongJob?.cancel()
        currentSongJob = scope.launch {
            val isCustomTuning = userPreferencesRepository.customTuningEnabledFlow.first()
            val isAutoEq = userPreferencesRepository.autoEqEnabledFlow.first()

            if (isCustomTuning) {
                eqEngine.applyPreset(com.audix.app.audio.EQPreset("Flat", emptyMap()))
                updateNotification("Manual EQ applied", "$title by $artist")
                return@launch
            }

            if (!isAutoEq) {
                eqEngine.applyPreset(com.audix.app.audio.EQPreset("Flat", emptyMap()))
                eqEngine.setEnabled(false)
                updateNotification("EQ Disabled", "$title by $artist")
                return@launch
            }

            // Show detecting before starting the API request
            if (com.audix.app.state.SongState.currentSong.value?.genre == null) {
                updateNotification("Detecting genre...", "$title by $artist")
            }

            if (debounce) {
                // Debounce rapid notification updates from media players
                delay(1500)

                // Ensure the song hasn't changed during the debounce
                val csMid = com.audix.app.state.SongState.currentSong.value
                if (csMid?.title != title || csMid?.artist != artist) {
                    return@launch
                }
            }

            val detectedGenre = genreDetector.detectGenre(title, artist)
            val cs = com.audix.app.state.SongState.currentSong.value

            if (detectedGenre != null) {
                // Update local UI state
                if (cs?.title == title) {
                    com.audix.app.state.SongState.currentSong.value = cs.copy(genre = detectedGenre)
                }

                val preset = EQPresetLibrary.getPresetForGenre(detectedGenre)
                if (preset != null) {
                    eqEngine.applyPreset(preset)
                    updateNotification("EQ: $detectedGenre", "$title by $artist")
                } else {
                    eqEngine.applyPreset(com.audix.app.audio.EQPreset("Flat", emptyMap()))
                    updateNotification(detectedGenre, "$title by $artist") // Displays errors directly
                }
            } else {
                eqEngine.applyPreset(com.audix.app.audio.EQPreset("Flat", emptyMap()))
                updateNotification("Unable to detect genre", "$title by $artist")
            }
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
            .setSmallIcon(android.R.drawable.ic_media_play) // Use actual icon later
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, createNotification(title, text))
    }
}
