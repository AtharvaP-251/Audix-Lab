package com.audix.app.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.content.Intent
import com.audix.app.state.SongInfo
import com.audix.app.state.SongState

class SongDetectionService : NotificationListenerService() {

    companion object {
        private const val TAG = "SongDetectionService"
        val ALLOWED_PACKAGES = setOf(
            "com.spotify.music",
            "com.google.android.apps.youtube.music",
            "com.google.android.youtube",
            "app.revanced.android.apps.youtube.music",
            "app.revanced.android.youtube"
        )
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Listener connected")
        SongState.isServiceConnected.value = true
        try {
            activeNotifications.forEach { sbn ->
                processNotification(sbn)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching active notifications: ${e.message}")
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Listener disconnected")
        SongState.isServiceConnected.value = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.let { processNotification(it) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        sbn?.let {
            if (it.packageName == SongState.currentSong.value?.packageName) {
                Log.d(TAG, "Notification removed for ${it.packageName}")
                SongState.currentSong.value = null
            }
        }
    }

    private fun processNotification(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        
        // Use EXTRA_MEDIA_SESSION as the definitive indicator of a music/media playing app
        val token = extras.getParcelable<android.media.session.MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)
        
        if (token != null) {
            var title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
            var artist = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
            var isPlaying = false

            try {
                val mediaController = android.media.session.MediaController(applicationContext, token)
                val metadata = mediaController.metadata
                isPlaying = mediaController.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING

                if (metadata != null) {
                    val mediaTitle = metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
                    val mediaArtist = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)
                    if (!mediaTitle.isNullOrEmpty()) title = mediaTitle.trim()
                    if (!mediaArtist.isNullOrEmpty()) artist = mediaArtist.trim()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting media metadata", e)
            }

            val isAllowed = ALLOWED_PACKAGES.contains(packageName)
            val wasPlaying = SongState.isPlaying.value
            
            // Only update global playing state based on ALLOWED apps
            if (isAllowed) {
                SongState.isPlaying.value = isPlaying
            } else if (isPlaying) {
                // An unsupported app is playing, effectively pausing our allowed apps' focus
                SongState.isPlaying.value = false
            }

            // Always broadcast playback state changes strictly for the active package so the engine can toggle EQ
            val currentPackage = SongState.currentSong.value?.packageName
            if (wasPlaying != SongState.isPlaying.value || (isPlaying && packageName != currentPackage)) {
                val stateIntent = Intent("com.audix.app.PLAYBACK_STATE_CHANGED")
                stateIntent.putExtra("EXTRA_IS_PLAYING", isPlaying)
                stateIntent.putExtra("EXTRA_PACKAGE_NAME", packageName)
                sendBroadcast(stateIntent)
            }

            if (title.isNotEmpty() && title != "null") {
                if (!isAllowed) {
                    Log.d(TAG, "Ignoring unsupported media package: $packageName")
                } else {
                    val current = SongState.currentSong.value
                    if (current?.title != title || current?.artist != artist) {
                        Log.d(TAG, "Media Detected: $title by $artist ($packageName)")
                        SongState.currentSong.value = SongInfo(title, artist, packageName)
                        
                        val intent = Intent("com.audix.app.SONG_CHANGED")
                        intent.putExtra("EXTRA_TITLE", title)
                        intent.putExtra("EXTRA_ARTIST", artist)
                        intent.putExtra("EXTRA_PACKAGE_NAME", packageName)
                        sendBroadcast(intent)
                    }
                }
            }
        }
    }
}
