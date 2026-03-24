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

    private val activeMediaControllers = mutableMapOf<String, android.media.session.MediaController>()
    private val mediaControllerCallbacks = mutableMapOf<String, android.media.session.MediaController.Callback>()

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
        
        activeMediaControllers.forEach { (packageName, controller) ->
            mediaControllerCallbacks[packageName]?.let { callback ->
                controller.unregisterCallback(callback)
            }
        }
        activeMediaControllers.clear()
        mediaControllerCallbacks.clear()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.let { processNotification(it) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        sbn?.let {
            val packageName = it.packageName
            
            val controller = activeMediaControllers.remove(packageName)
            val callback = mediaControllerCallbacks.remove(packageName)
            if (controller != null && callback != null) {
                controller.unregisterCallback(callback)
            }
            
            if (packageName == SongState.currentSong.value?.packageName) {
                Log.d(TAG, "Notification removed for ${it.packageName}")
                SongState.currentSong.value = null
                SongState.isPlaying.value = false
                
                val stateIntent = Intent("com.audix.app.PLAYBACK_STATE_CHANGED")
                stateIntent.putExtra("EXTRA_IS_PLAYING", false)
                stateIntent.putExtra("EXTRA_PACKAGE_NAME", it.packageName)
                sendBroadcast(stateIntent)
            }
        }
    }

    private fun processNotification(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        
        val token = extras.getParcelable<android.media.session.MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)
        
        if (token != null) {
            attachMediaControllerCallback(token, packageName)
            
            // Still run the initial extraction fallback in case notification posted but callback hasn't fired
            var title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
            var artist = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
            var isPlaying = false

            try {
                val mediaController = android.media.session.MediaController(applicationContext, token)
                val metadata = mediaController.metadata
                
                val state = mediaController.playbackState?.state
                isPlaying = state == android.media.session.PlaybackState.STATE_PLAYING ||
                            state == android.media.session.PlaybackState.STATE_BUFFERING ||
                            state == android.media.session.PlaybackState.STATE_SKIPPING_TO_NEXT ||
                            state == android.media.session.PlaybackState.STATE_SKIPPING_TO_PREVIOUS ||
                            state == android.media.session.PlaybackState.STATE_FAST_FORWARDING ||
                            state == android.media.session.PlaybackState.STATE_REWINDING

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
            
            if (isAllowed) {
                SongState.isPlaying.value = isPlaying
            } else if (isPlaying) {
                SongState.isPlaying.value = false
            }

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
                        Log.d(TAG, "Media Detected (Initial): $title by $artist ($packageName)")
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

    private fun attachMediaControllerCallback(token: android.media.session.MediaSession.Token, packageName: String) {
        val existingController = activeMediaControllers[packageName]
        if (existingController?.sessionToken == token) return

        existingController?.let {
            val oldCallback = mediaControllerCallbacks[packageName]
            if (oldCallback != null) {
                it.unregisterCallback(oldCallback)
            }
        }

        try {
            val mediaController = android.media.session.MediaController(applicationContext, token)
            val callback = object : android.media.session.MediaController.Callback() {
                override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) {
                    super.onPlaybackStateChanged(state)
                    val playbackStateCode = state?.state
                    val isPlaying = playbackStateCode == android.media.session.PlaybackState.STATE_PLAYING ||
                            playbackStateCode == android.media.session.PlaybackState.STATE_BUFFERING ||
                            playbackStateCode == android.media.session.PlaybackState.STATE_SKIPPING_TO_NEXT ||
                            playbackStateCode == android.media.session.PlaybackState.STATE_SKIPPING_TO_PREVIOUS ||
                            playbackStateCode == android.media.session.PlaybackState.STATE_FAST_FORWARDING ||
                            playbackStateCode == android.media.session.PlaybackState.STATE_REWINDING
                    
                    val isAllowed = ALLOWED_PACKAGES.contains(packageName)
                    val wasPlaying = SongState.isPlaying.value

                    if (isAllowed) {
                        SongState.isPlaying.value = isPlaying
                    } else if (isPlaying) {
                        SongState.isPlaying.value = false
                    }

                    val currentPackage = SongState.currentSong.value?.packageName
                    if (wasPlaying != SongState.isPlaying.value || (isPlaying && packageName != currentPackage)) {
                        val stateIntent = Intent("com.audix.app.PLAYBACK_STATE_CHANGED")
                        stateIntent.putExtra("EXTRA_IS_PLAYING", isPlaying)
                        stateIntent.putExtra("EXTRA_PACKAGE_NAME", packageName)
                        sendBroadcast(stateIntent)
                    }
                }
                
                override fun onMetadataChanged(metadata: android.media.MediaMetadata?) {
                    super.onMetadataChanged(metadata)
                    if (metadata != null) {
                        val title = metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)?.trim()
                        val artist = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)?.trim() ?: ""
                        
                        val isAllowed = ALLOWED_PACKAGES.contains(packageName)
                        if (isAllowed && !title.isNullOrEmpty() && title != "null") {
                            val current = SongState.currentSong.value
                            if (current?.title != title || current?.artist != artist) {
                                Log.d(TAG, "Media Detected (Callback): $title by $artist ($packageName)")
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

            mediaController.registerCallback(callback)
            activeMediaControllers[packageName] = mediaController
            mediaControllerCallbacks[packageName] = callback
        } catch (e: Exception) {
            Log.e(TAG, "Error attaching MediaController.Callback for $packageName", e)
        }
    }
}
