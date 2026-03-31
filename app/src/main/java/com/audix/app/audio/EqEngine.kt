package com.audix.app.audio

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import android.util.Log

class EqEngine {
    private var equalizer: Equalizer? = null
    var isEnabled: Boolean = false
        private set

    var currentIntensity: Float = 1.0f
        set(value) {
            field = value
            if (isEnabled) reapplyCurrentEq()
        }

    var isCustomTuningEnabled: Boolean = false
        set(value) {
            field = value
            if (isEnabled) reapplyCurrentEq()
        }

    var customBass: Float = 0.0f
        set(value) {
            field = value
            if (isEnabled) reapplyCurrentEq()
        }

    var customVocals: Float = 0.0f
        set(value) {
            field = value
            if (isEnabled) reapplyCurrentEq()
        }

    var customTreble: Float = 0.0f
        set(value) {
            field = value
            if (isEnabled) reapplyCurrentEq()
        }

    // --- Phase 4.1 — Spatial Audio state ---
    var isSpatialEnabled: Boolean = false
        set(value) { field = value; if (isEnabled || value) reapplyCurrentEq() }

    var spatialLevel: Int = 0
        set(value) { field = value; if (isEnabled || isSpatialEnabled) reapplyCurrentEq() }

    var isHeadphonesConnected: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                if (isSpatialEnabled) reapplyCurrentEq()
            }
        }

    // --- Phase 4.4 — SpatialEngine instance ---
    private val spatialEngine = SpatialEngine()

    private var lastAppliedPreset: EQPreset? = null

    private fun reapplyCurrentEq() {
        val presetToApply = lastAppliedPreset ?: EQPreset("Flat", emptyMap())
        applyPreset(presetToApply)
    }

    fun initialize() {
        createEqualizer()
    }

    /**
     * Reinitializes the equalizer by releasing the existing one and creating a new instance.
     * This is critical for OEM devices (e.g. Motorola) where the global audio session
     * becomes stale between song changes.
     */
    fun reinitialize() {
        Log.d("EqEngine", "Reinitializing equalizer...")
        val wasEnabled = isEnabled
        releaseInternal()
        createEqualizer()
        // Reapply preset if we were enabled before
        if (wasEnabled && lastAppliedPreset != null) {
            applyPreset(lastAppliedPreset!!)
        }
    }

    private fun createEqualizer() {
        // Try global session (0) first; some OEMs require this
        // Using high priority (1000) to override system effects like Motorola's Dolby/Moto Audio
        var created = false
        try {
            equalizer = Equalizer(1000, 0)
            equalizer?.enabled = false
            Log.d("EqEngine", "Equalizer initialized on global session 0 with priority 1000")
            created = true
        } catch (e: Exception) {
            Log.w("EqEngine", "Failed to create Equalizer with priority 1000, trying lower priority: ${e.message}")
        }

        if (!created) {
            try {
                // Some OEMs ignore session 0 — try querying the system
                equalizer = Equalizer(0, 0)
                equalizer?.enabled = false
                Log.d("EqEngine", "Equalizer initialized with priority 0 on session 0")
            } catch (e: Exception) {
                Log.e("EqEngine", "Failed to initialize Equalizer on any session. Device may not support it.", e)
                equalizer = null
            }
        }

        // Phase 4.4 — initialise spatial engine alongside equalizer
        spatialEngine.initialize()
    }

    /**
     * Broadcasts ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION to notify the OS
     * and OEM audio drivers (like Motorola's Dolby) that a session is being managed.
     */
    fun notifySessionOpen(context: Context) {
        try {
            val intent = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0)
            intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            context.sendBroadcast(intent)
            Log.d("EqEngine", "Broadcasted session open for session 0")
        } catch (e: Exception) {
            Log.w("EqEngine", "Failed to broadcast session open: ${e.message}")
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
            applyPresetInternal(eq, preset)
        } catch (e: Exception) {
            Log.w("EqEngine", "Error applying EQ preset, attempting reinitialize and retry: ${e.message}")
            // Reinitialize and retry once — critical for Motorola and similar OEMs
            try {
                releaseInternal()
                createEqualizer()
                val freshEq = equalizer
                if (freshEq != null) {
                    applyPresetInternal(freshEq, preset)
                    Log.d("EqEngine", "Preset applied after reinitialize")
                } else {
                    Log.e("EqEngine", "Reinitialize failed, giving up on this preset")
                }
            } catch (retryEx: Exception) {
                Log.e("EqEngine", "Retry after reinitialize also failed", retryEx)
            }
        }
    }

    private fun applyPresetInternal(eq: Equalizer, preset: EQPreset) {
        val numBands = eq.numberOfBands
        if (numBands > 0) {
            val minLevel = eq.bandLevelRange[0]
            val maxLevel = eq.bandLevelRange[1]

            for (i in 0 until numBands) {
                val bandFreq = eq.getCenterFreq(i.toShort()) / 1000 // Convert mHz to Hz

                // Allow calculation to continue even if preset is empty (i.e. Flat)
                val closestFreq = if (preset.bands.isNotEmpty()) {
                    preset.bands.keys.minByOrNull { Math.abs(it - bandFreq) }
                } else null

                val gainDb = if (closestFreq != null) preset.bands[closestFreq] ?: 0f else 0f

                // Apply intensity scaling, exaggerate curve differences (x2) to be more obvious
                // We re-add the +3.0dB global offset to combat Android's automatic EQ volume reduction when enabled
                var targetLevel = ((gainDb * currentIntensity * 2.0f + 3.0f) * 100).toInt()

                // Apply Custom Tuning if enabled
                if (isCustomTuningEnabled) {
                    val bassNorm = customBass / 5.0f      // -1.0 to 1.0
                    val vocalsNorm = customVocals / 5.0f
                    val trebleNorm = customTreble / 5.0f

                    // Apply low shelf for Bass (up to ~250Hz)
                    if (bandFreq <= 250) {
                        val factor = when {
                            bandFreq <= 70 -> 1.0f    // 31, 62
                            bandFreq <= 130 -> 0.7f   // 125
                            else -> 0.35f             // 250
                        }
                        if (bassNorm > 0) {
                            targetLevel += ((maxLevel - targetLevel) * bassNorm * factor).toInt()
                        } else if (bassNorm < 0) {
                            targetLevel -= ((targetLevel - minLevel) * -bassNorm * factor).toInt()
                        }
                    }

                    // Apply wide bell for Vocals (Mids: 500Hz to 4000Hz)
                    if (bandFreq in 300..5000) {
                        val factor = when {
                            bandFreq in 800..2500 -> 1.0f   // 1000, 2000
                            bandFreq in 400..3000 -> 0.65f  // 500
                            else -> 0.35f                   // 4000
                        }
                        if (vocalsNorm > 0) {
                            targetLevel += ((maxLevel - targetLevel) * vocalsNorm * factor).toInt()
                        } else if (vocalsNorm < 0) {
                            targetLevel -= ((targetLevel - minLevel) * -vocalsNorm * factor).toInt()
                        }
                    }

                    // Apply high shelf for Treble (8000Hz+)
                    if (bandFreq >= 6000) {
                        val factor = when {
                            bandFreq >= 14000 -> 1.0f  // 16000
                            bandFreq >= 7000 -> 0.8f   // 8000
                            else -> 0.5f               // Interpolating
                        }
                        if (trebleNorm > 0) {
                            targetLevel += ((maxLevel - targetLevel) * trebleNorm * factor).toInt()
                        } else if (trebleNorm < 0) {
                            targetLevel -= ((targetLevel - minLevel) * -trebleNorm * factor).toInt()
                        }
                    }
                }

                // Phase 4.2 — SPATIAL AUDIO LAYER: pinna notch + psychoacoustic coloring
                // Applied after custom tuning, before clamp — per plan §4.2
                // GATED: Only apply if headphones are connected to avoid distortion on speakers
                if (isSpatialEnabled && isHeadphonesConnected && spatialLevel > 0 && bandFreq > 0) {
                    val profile = SpatialProfileLibrary.getProfile(spatialLevel)
                    targetLevel = spatialEngine.applyPsychoacousticDelta(
                        bandFreq, targetLevel, profile, minLevel, maxLevel
                    )
                }

                // Clamp to the device's acceptable range
                if (targetLevel < minLevel) targetLevel = minLevel.toInt()
                if (targetLevel > maxLevel) targetLevel = maxLevel.toInt()

                eq.setBandLevel(i.toShort(), targetLevel.toShort())
            }
        }

        // Phase 4.3 — Apply Accurate Reverb and Stereo Widening
        // GATED: Only apply if headphones are connected
        if (isSpatialEnabled && isHeadphonesConnected) {
            val profile = SpatialProfileLibrary.getProfile(spatialLevel)
            
            // Layer B: Stereo Widening (Virtualizer) — Levels 1-5
            spatialEngine.setVirtualizer(true, profile.virtualizerStrength)
            
            // Layer C: Environmental Depth (Reverb) — Levels 2-5
            // Level 1 is pure widening + EQ; Level 2+ adds depth
            if (spatialLevel >= 2) {
                spatialEngine.setReverb(true, profile)
            } else {
                spatialEngine.setReverb(false, null)
            }

            // Layer D: Volume Compensation (LoudnessEnhancer)
            spatialEngine.setLoudnessBoost(true, profile.loudnessBoostmB)
        } else {
            spatialEngine.setVirtualizer(false, 0)
            spatialEngine.setReverb(false, null)
            spatialEngine.setLoudnessBoost(false, 0)
        }

        eq.enabled = true
        isEnabled = true
        Log.d("EqEngine", "Applied preset for genre: ${preset.genre}")
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        try {
            equalizer?.enabled = enabled
        } catch (e: Exception) {
            Log.w("EqEngine", "Error setting equalizer enabled state: ${e.message}")
        }
        Log.d("EqEngine", "EQ ${if (enabled) "enabled" else "disabled"}")
    }

    private fun releaseInternal() {
        try {
            equalizer?.release()
        } catch (e: Exception) {
            Log.w("EqEngine", "Error releasing equalizer: ${e.message}")
        }
        equalizer = null
        isEnabled = false
        // Phase 4.4 — release spatial engine alongside equalizer
        spatialEngine.release()
    }

    fun release() {
        releaseInternal()
        Log.d("EqEngine", "Equalizer released")
    }
}
