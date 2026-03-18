package com.audix.app.audio

import android.media.audiofx.Equalizer
import android.util.Log

class EqEngine {
    private var equalizer: Equalizer? = null
    var isEnabled: Boolean = false
        private set

    var currentIntensity: Float = 1.0f
        set(value) {
            field = value
            reapplyCurrentEq()
        }

    var currentBassBoost: Float = 0.0f
        set(value) {
            field = value
            reapplyCurrentEq()
        }

    private var lastAppliedPreset: EQPreset? = null

    private fun reapplyCurrentEq() {
        val presetToApply = lastAppliedPreset ?: EQPreset("Flat", emptyMap())
        applyPreset(presetToApply)
    }

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

    fun applyPreset(preset: EQPreset) {
        lastAppliedPreset = preset
        val eq = equalizer
        if (eq == null) {
            Log.e("EqEngine", "Cannot apply preset, Equalizer is null")
            return
        }

        try {
            val numBands = eq.numberOfBands
            if (numBands > 0) {
                val minLevel = eq.bandLevelRange[0]
                val maxLevel = eq.bandLevelRange[1]

                for (i in 0 until numBands) {
                    val bandFreq = eq.getCenterFreq(i.toShort()) / 1000 // Convert mHz to Hz
                    
                    // Find the closest frequency in our 10-band preset list
                    val closestFreq = preset.bands.keys.minByOrNull { Math.abs(it - bandFreq) } ?: continue
                    
                    val gainDb = preset.bands[closestFreq] ?: 0f
                    // Apply intensity scaling, exaggerate curve differences (x2) to be more obvious,
                    // and apply a +3.0dB global offset to combat Android's automatic EQ volume reduction
                    var targetLevel = ((gainDb * currentIntensity * 2.0f + 3.0f) * 100).toInt()
                    
                    // Apply Bass Boost on lowest bands
                    if (currentBassBoost > 0f) {
                        if (i == 0) {
                            targetLevel += ((maxLevel - targetLevel) * currentBassBoost).toInt()
                        } else if (i == 1) {
                            targetLevel += ((maxLevel - targetLevel) * (currentBassBoost * 0.5f)).toInt()
                        }
                    }

                    // Clamp to the device's acceptable range
                    if (targetLevel < minLevel) targetLevel = minLevel.toInt()
                    if (targetLevel > maxLevel) targetLevel = maxLevel.toInt()

                    eq.setBandLevel(i.toShort(), targetLevel.toShort())
                }
            }
            eq.enabled = true
            isEnabled = true
            Log.d("EqEngine", "Applied preset for genre: ${preset.genre}")
        } catch (e: Exception) {
            Log.e("EqEngine", "Error applying EQ preset", e)
        }
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        equalizer?.enabled = enabled
        Log.d("EqEngine", "EQ ${if (enabled) "enabled" else "disabled"}")
    }

    fun release() {
        equalizer?.release()
        equalizer = null
        Log.d("EqEngine", "Equalizer released")
    }
}
