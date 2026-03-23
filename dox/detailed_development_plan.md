# 🚀 Audix: Detailed Development Plan

This document synthesizes the `master plan.txt` and `roadmap.txt` into a comprehensive, phase-by-phase development guide. It highlights specific tasks and explicitly indicates where **[Manual Intervention Required]** is necessary during the development lifecycle.

---

## 🧱 Phase 0: Project Setup (Foundation)

### 🎯 Goal
Set up a clean Android project with the correct architecture and dependencies.

### Tasks
- [x] **[Manual Intervention Required]** Create a new Android project using Android Studio (Kotlin + Jetpack Compose).
- [x] Set up MVVM or Clean Architecture folder structure.
- [x] **[Manual Intervention Required]** Initialize Git repository and perform the initial commit.
- [x] Add necessary dependencies in `build.gradle.kts`: Retrofit, Room, DataStore, Coroutines, etc.

---

## 🎧 Phase 1: Basic EQ Engine (Core Audio)

### 🎯 Goal
Prove the ability to apply EQ to system audio.

### Tasks
- [x] Integrate the Android `Equalizer` API and handle audio sessions.
- [x] Create a single test EQ preset (e.g., "Bass Boost").
- [x] Implement a basic UI button to trigger the manual EQ application.
- [x] **[Manual Intervention Required]** Listen to audio (e.g., via Spotify) and manually toggle the button to verify the sound actually changes.

---

## 🔍 Phase 2: Song Detection (Critical Layer)

### 🎯 Goal
Detect the currently playing song reliably.

### Tasks
- [x] Implement `NotificationListenerService`.
- [x] Extract the song title and artist information from media notifications.
- [x] Bind the extracted data to the UI.
- [x] **[Manual Intervention Required]** Manually open YouTube Music/Spotify, play different songs, and visually verify the app displays the correct song and artist. You will need to manually grant notification reading permissions in Android settings during this test.

---

## 🧠 Phase 3: AI Genre Detection

### 🎯 Goal
Convert the detected song and artist into a music genre.

### Tasks
- [x] **[Manual Intervention Required]** Set up the AI API account (e.g., OpenAI or Gemini) and securely retrieve the API key using a developer console.
- [x] Integrate the AI REST API via Retrofit.
- [x] **[Manual Intervention Required]** Design, test, and manually refine the AI prompt to ensure accurate genre classification from the "Song - Artist" string without hallucinating.
- [x] Implement error handling and timeout logic for API calls.
- [x] Display the detected genre in the UI.

---

## 💾 Phase 4: Caching System (Performance Layer)

### 🎯 Goal
Avoid repetitive API calls for the same song to save costs and reduce latency.

### Tasks
- [x] Implement a Room database (`SongCache` entity: song_title, artist_name, detected_genre, timestamp).
- [x] Build logic to first interrogate the database; call the AI API only if there's a cache miss.
- [x] **[Manual Intervention Required]** Play a sequence of songs manually and observe the application logs to ensure the API is called exactly once per unique track.

---

## 🎚️ Phase 5: Preset Engine + Genre Mapping

### 🎯 Goal
Automatically apply the correct EQ settings based on the recognized genre.

### Tasks
- [x] **[Manual Intervention Required]** Define scientifically sound EQ presets (band values) for required genres (Rock, Pop, Jazz, etc.) by manually evaluating them against different tracks.
- [x] Map dynamically received genres to predefined `EQPreset` objects.
- [x] Hook the genre-detection event to the EQ application logically.

---

## 🎛️ Phase 6: Intensity Slider (UX Differentiator)

### 🎯 Goal
Provide intuitive control over the EQ's strength.

### Tasks
- [x] Implement a 0–100% UI slider using Jetpack Compose.
- [x] Develop the logic to scale EQ band values proportionally to the slider's value (e.g., +5 dB at 100% becomes +2.5 dB at 50%).
- [x] Persist the user's intensity preference using DataStore.
- [x] **[Manual Intervention Required]** Manually drag the slider while music is playing to verify that the EQ intensity morphs smoothly and sounds natural without audio artifacts or popping.

---

## 🔄 Phase 7: Background Engine (Real App Behavior)

### 🎯 Goal
Ensure continuous, automatic operation even when the app is closed, using AIDL for robust multi-process architecture where supported.

### Tasks
- [x] **[Manual Intervention Required]** Research and determine the minimum robust Android API level for the isolated process feature (`android:process=":audio_engine"`). Write a version-check wrapper.
- [x] Define the `.aidl` interface (e.g., `IAudioEngineService.aidl`) exposing EQ controls and state queries.
- [x] Configure `AndroidManifest.xml` to run the active Foreground Service in a separate, isolated process for compatible Android versions.
- [x] Implement a fallback architecture: On older/unsupported Android versions, run the Foreground Service in the standard UI process.
- [x] Implement the AIDL Stub inside the Foreground Service (for multi-process mode) and a standard `LocalBinder` (for single-process fallback).
- [x] Build the IPC connection logic (`ServiceConnection` and `bindService`) in the UI/Repository layer, capable of talking to either the AIDL Stub or the LocalBinder.
- [x] Create a persistent interactive notification ("EQ Active", current genre).
- [x] Wire Android lifecycle events properly to maintain the IPC session and re-bind if the service process dies.
- [x] **[Manual Intervention Required]** Swipe away the app UI from recent tasks on both an older Android device (single-process fallback) and a modern device (multi-process AIDL). Confirm the background audio process remains alive and the EQ continues to apply as songs advance.

---

## ⚙️ Phase 8: App Targeting Constraints

### 🎯 Goal
Limit the EQ processing exclusively to supported multimedia apps.

### Tasks
- [x] Detect the source package name of the media session or notification.
- [x] Apply filtering logic to process strictly Spotify and YouTube Music.
- [x] **[Manual Intervention Required]** Play sound from an unsupported app (e.g., a game or web browser) and manually verify that the EQ is ignored and not applied globally.

---

## 🧩 Phase 9: Legacy Mode (Stability Layer)

### 🎯 Goal
Provide a reliable fallback when advanced audio session attachment fails.

### Tasks
- [x] Add a "Legacy Mode" toggle in settings.
- [x] Implement a global audio session fallback (session ID 0).
- [x] **[Manual Intervention Required]** Perform manual tests on devices known to have non-standard OEM audio architectures, ensuring that the global mode provides a safe fallback without crashing.

---

## 🎨 Phase 10: UI Polish (Keep it Minimal)

### 🎯 Goal
Finalize a clean, straightforward user experience.

### Tasks
- [x] **[Manual Intervention Required]** Review the UI design critically, ensure all non-essential jargon is omitted, and visual feedback is instant.
- [x] Finalize the layout components: ON/OFF toggle, current genre label, and intensity slider.

---

## 🧪 Phase 11: Real Device Testing (CRITICAL)

### 🎯 Goal
Ensure widespread reliability and handle device fragmentation.

### Tasks
- [x] **[Manual Intervention Required]** Deploy and test heavily across multiple physical devices spanning different Android versions (e.g., Android 11, 12, 13, 14) and OEM interfaces (Samsung, Xiaomi, OnePlus).
- [x] **[Manual Intervention Required]** Manually create and validate specific edge cases: device reboot, app force-killed, rapid consecutive song changes, audio focus loss (like an incoming call), and notification delivery latency.

---

## 🚀 Phase 12: Pre-Launch Hardening

### 🎯 Goal
Final polish to handle unpredictable real-world scenarios before release.

### Tasks
- [x] Build robust graceful degradation for scenarios like missing internet or AI API downtime.
- [x] Add clear, jargon-free onboarding screens explaining why notification and battery optimization permissions are strictly needed.
- [x] **[Manual Intervention Required]** Conduct a full end-to-end "fresh install" test acting as a first-time user. Verify the onboarding flow, permission prompts (including battery exemptions), and test your zero-configuration promise.

---

### 🧠 Development Strategy Summary Checklist
1. **[Core]** EQ works (Phase 1)
2. **[Input]** Song detection works (Phase 2)
3. **[Brain]** AI Classification works (Phase 3)
4. **[Integration]** Caching, Backgrounding, Polish (Phases 4-12)

⚠️ **Critical Rule:** Do not parallelize AI and UI development before Core EQ works seamlessly.
