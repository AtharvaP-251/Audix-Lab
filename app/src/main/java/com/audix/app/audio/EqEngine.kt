package com.audix.app.audio

import android.media.audiofx.Equalizer
import android.util.Log

class EqEngine {
    private var equalizer: Equalizer? = null

    fun initialize() {
        try {
            // Priority 0, session 0 (global audio session)
            equalizer = Equalizer(0, 0)
            equalizer?.enabled = false
            Log.d("EqEngine", "Equalizer initialized successfully on global session")
        } catch (e: Exception) {
            Log.e("EqEngine", "Failed to initialize EqEngine. May not be supported on this device/emulator.", e)
            equalizer = null
        }
    }

    fun toggleBassBoost(enable: Boolean) {
        val eq = equalizer
        if (eq == null) {
            Log.e("EqEngine", "Cannot toggle Bass Boost, Equalizer is null")
            return
        }

        try {
            if (enable) {
                // Determine the lowest frequency band (usually band index 0)
                val numBands = eq.numberOfBands
                if (numBands > 0) {
                    val maxLevel = eq.bandLevelRange[1]
                    // Apply max level to the lowest frequency to simulate strong bass boost
                    eq.setBandLevel(0, maxLevel)
                    
                    // Optionally push the second band as well slightly depending on how many bands there are
                    if (numBands > 1) {
                        eq.setBandLevel(1, (maxLevel * 0.5).toInt().toShort())
                    }
                }
                eq.enabled = true
                Log.d("EqEngine", "Bass Boost enabled")
            } else {
                eq.enabled = false
                Log.d("EqEngine", "Bass Boost disabled")
            }
        } catch (e: Exception) {
            Log.e("EqEngine", "Error applying EQ preset", e)
        }
    }

    fun release() {
        equalizer?.release()
        equalizer = null
        Log.d("EqEngine", "Equalizer released")
    }
}
