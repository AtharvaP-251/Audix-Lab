# 📖 Audix User Guide

Welcome to the **Audix User Guide**. This document will help you understand every feature of the app and solve common issues.

---

## 🛠️ Essential Setup

To function as an intelligent audio engine, Audix requires two critical permissions:

### 1. Notification Access 🔔
Audix uses this to "see" what song is playing in apps like Spotify or YouTube Music. 
- **Privacy Note**: We only read the track title and artist name. No personal messages or notifications are ever read or stored.

### 2. Battery Optimization Exemption 🔋
Android's aggressive battery management can shut down background services. 
- **Requirement**: You must exclude Audix from optimization to ensure the EQ engine stays active when your music is playing and the screen is off.

---

## 🎧 Core Features

### 🧠 AutoEQ (The Smart Engine)
The heart of Audix. When enabled, the app detects your current song's genre and applies a professional EQ preset.
- **Intensity Slider**: Adjust how "strong" the EQ effect is. 100% applies the full preset, while lower values offer a more subtle enhancement.
- **Offline Cache**: If you lose internet, Audix uses a local database of previously identified songs to ensure the experience remains seamless.

### 🌌 Spatial Audio
Immersive 3D sound processing that adds depth and space to your music.
- **Levels 1–5**: Move from a subtle "Room" feel to a large "Hall" atmosphere.
- **Requirement**: For safety and audio quality, **Headphones are required**. Spatial audio will automatically disable if headphones are disconnected.

### 🎚️ Custom Tuning
For those who want total control.
- **Bass**: Boost the low-end thump without muddying the sound.
- **Vocals**: Bring your favorite singers to the front of the mix.
- **Treble**: Add clarity and sparkle to the high frequencies.
- **Note**: Enabling Custom Tuning will temporarily disable AutoEQ to avoid conflicting sound signatures.

---

## ❓ Troubleshooting

### EQ isn't applying or changing
1. **Check Notification Access**: Ensure Audix is enabled in your phone's notification listener settings.
2. **Check App Support**: Ensure you are using a supported player (Spotify, YT Music, etc.).
3. **Restart the Service**: Try toggling the "AutoEQ" switch OFF and ON again.

### Spatial Audio is disabled
- **Plug in Headphones**: Audix detects your audio output device. Spatial Audio is gated to headphones only to prevent distortion on internal speakers.

### App keeps closing in the background
- **Battery Settings**: Ensure "Background Autostart" is enabled and "Battery Optimization" is set to "Don't Optimize" for Audix.

---

## 🤖 Custom AI Engine (Advanced)

### Gemini API Integration ⚡
To get the most out of Audix, you can use your own Google Gemini API key.
- **Benefits**: Faster detection, no shared limits, and enhanced privacy.
- **How to Setup**: Go to **Settings > Gemini API Key** and paste your key.
- **Privacy**: Your key is stored locally on your device and never shared.

---

## 🔋 Master Logic & Persistence

### The Master Power Switch 🔌
The master switch at the bottom is your global control for all enhancements.
- **Global Override**: Toggling this off instantly disables all active EQ, Spatial, and Tuning effects.
- **State Persistence**: Each individual feature (AutoEQ, Spatial Audio) remembers its last state. Re-enabling the Master Power will restore your previous configuration.

---

## 📧 Support & Feedback
If you encounter a bug or have a feature request, please reach out via our GitHub repository.

---

*Elevating your sound, intelligently.*
