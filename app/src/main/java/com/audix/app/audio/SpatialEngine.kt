package com.audix.app.audio

import android.media.audiofx.EnvironmentalReverb
import android.media.audiofx.Virtualizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.util.Log

/**
 * Phase 3 (Enhanced) — Spatial Audio DSP Core
 *
 * Implements a high-fidelity spatial engine using:
 *
 *  Layer A — Psychoacoustic EQ Coloring
 *      Modifies individual EQ bands to simulate pinna notch shaping and torso warmth.
 *
 *  Layer B — Accurate Stereo Widening (Virtualizer)
 *      Uses Android's [Virtualizer] to project the soundstage beyond headphones.
 *
 *  Layer C — Environmental Depth (Reverb)
 *      Uses [EnvironmentalReverb] for granular control over decay and reflections.
 *
 *  Layer D — Volume Compensation (LoudnessEnhancer)
 *      Counteracts perceived volume drop when spatial processing is active.
 */
class SpatialEngine {

    // ── Effect state ────────────────────────────────────────────────────────

    private var reverb: EnvironmentalReverb? = null
    private var virtualizer: Virtualizer? = null
    private var enhancer: LoudnessEnhancer? = null

    var reverbSupported: Boolean = false
        private set
    var virtualizerSupported: Boolean = false
        private set
    var enhancerSupported: Boolean = false
        private set

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun initialize() {
        tryInitReverb()
        tryInitVirtualizer()
        tryInitEnhancer()
    }

    private fun tryInitReverb() {
        try {
            val r = EnvironmentalReverb(0, 0)
            r.enabled = false
            reverb = r
            reverbSupported = true
            Log.d(TAG, "EnvironmentalReverb initialised")
        } catch (e: Throwable) {
            reverbSupported = false
            reverb = null
            Log.w(TAG, "EnvironmentalReverb not supported: ${e.message}")
        }
    }

    private fun tryInitVirtualizer() {
        try {
            val v = Virtualizer(0, 0)
            v.enabled = false
            virtualizer = v
            virtualizerSupported = true
            Log.d(TAG, "Virtualizer initialised")
        } catch (e: Throwable) {
            virtualizerSupported = false
            virtualizer = null
            Log.w(TAG, "Virtualizer not supported: ${e.message}")
        }
    }

    private fun tryInitEnhancer() {
        try {
            val le = LoudnessEnhancer(0)
            le.enabled = false
            enhancer = le
            enhancerSupported = true
            Log.d(TAG, "LoudnessEnhancer initialised")
        } catch (e: Throwable) {
            enhancerSupported = false
            enhancer = null
            Log.w(TAG, "LoudnessEnhancer not supported: ${e.message}")
        }
    }

    fun release() {
        try {
            reverb?.enabled = false
            reverb?.release()
            virtualizer?.enabled = false
            virtualizer?.release()
            enhancer?.enabled = false
            enhancer?.release()
        } catch (e: Throwable) {
            Log.w(TAG, "Error releasing effects: ${e.message}")
        } finally {
            reverb = null
            virtualizer = null
            enhancer = null
            reverbSupported = false
            virtualizerSupported = false
            enhancerSupported = false
        }
        Log.d(TAG, "SpatialEngine released")
    }

    // ── Layer A — EQ Delta ───────────────────────────────────────────────────

    fun applyPsychoacousticDelta(
        bandFreqHz: Int,
        currentLevelMillibels: Int,
        profile: SpatialProfile,
        minLevel: Short,
        maxLevel: Short
    ): Int {
        val deltaDb: Float = when {
            bandFreqHz in 200..300       -> profile.torsoWarmth
            bandFreqHz in 2000..4500     -> profile.airPresence
            bandFreqHz in 6000..9000     -> profile.primaryPinnaNotch
            bandFreqHz >= 14000          -> profile.secondaryPinnaNotch
            else                         -> return currentLevelMillibels
        }

        val deltaMillibels = (deltaDb * 100).toInt()
        val adjusted = currentLevelMillibels + deltaMillibels
        return adjusted.coerceIn(minLevel.toInt(), maxLevel.toInt())
    }

    // ── Layer B — Virtualizer (Widening) ─────────────────────────────────────

    fun setVirtualizer(enabled: Boolean, strength: Short) {
        val v = virtualizer ?: return
        if (!virtualizerSupported) return

        try {
            if (enabled && strength > 0) {
                if (v.strengthSupported) {
                    v.setStrength(strength)
                }
                v.enabled = true
                Log.d(TAG, "Virtualizer ON (strength=$strength)")
            } else {
                v.enabled = false
                Log.d(TAG, "Virtualizer OFF")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Virtualizer error: ${e.message}")
        }
    }

    // ── Layer C — Environmental Reverb (Depth) ───────────────────────────────

    fun setReverb(enabled: Boolean, profile: SpatialProfile?) {
        val r = reverb ?: return
        if (!reverbSupported || profile == null) return

        try {
            if (enabled && profile.reverbRt60Ms > 0) {
                r.decayTime = profile.reverbRt60Ms
                val wetLevel = (Math.log10(profile.reverbWetDry.toDouble().coerceIn(0.01, 1.0)) * 2000).toInt()
                r.reverbLevel = wetLevel.toShort()
                r.reflectionsLevel = (wetLevel - 500).toShort()
                r.enabled = true
                Log.d(TAG, "Reverb ON (decay=${r.decayTime}ms, level=$wetLevel)")
            } else {
                r.enabled = false
                Log.d(TAG, "Reverb OFF")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Reverb error: ${e.message}")
        }
    }

    // ── Layer D — Volume Compensation (Gain) ────────────────────────────────

    /**
     * Controls the loudness compensation gain.
     * @param enabled Whether to enable boost.
     * @param gainmB Target gain in millibels.
     */
    fun setLoudnessBoost(enabled: Boolean, gainmB: Int) {
        val le = enhancer ?: return
        if (!enhancerSupported) return

        try {
            if (enabled && gainmB > 0) {
                le.setTargetGain(gainmB)
                le.enabled = true
                Log.d(TAG, "LoudnessEnhancer ON (gain=${gainmB}mB)")
            } else {
                le.enabled = false
                Log.d(TAG, "LoudnessEnhancer OFF")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "LoudnessEnhancer error: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "SpatialEngine"
    }
}
