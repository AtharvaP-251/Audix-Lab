package com.audix.app.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.audix.app.state.SongInfo
import com.audix.app.state.SongState

class SongDetectionService : NotificationListenerService() {

    companion object {
        private const val TAG = "SongDetectionService"
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

            try {
                val mediaController = android.media.session.MediaController(applicationContext, token)
                val metadata = mediaController.metadata
                if (metadata != null) {
                    val mediaTitle = metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
                    val mediaArtist = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)
                    if (!mediaTitle.isNullOrEmpty()) title = mediaTitle.trim()
                    if (!mediaArtist.isNullOrEmpty()) artist = mediaArtist.trim()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting media metadata", e)
            }

            if (title.isNotEmpty() && title != "null") {
                val current = SongState.currentSong.value
                if (current?.title != title || current?.artist != artist) {
                    Log.d(TAG, "Media Detected: $title by $artist ($packageName)")
                    SongState.currentSong.value = SongInfo(title, artist, packageName)
                }
            }
        }
    }
}
