package com.audix.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import kotlinx.coroutines.isActive
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.audix.app.R

@Composable
fun HeroSection(
    title: String,
    artist: String,
    genre: String?,
    isPlaying: Boolean,
    isAutoEqEnabled: Boolean,
    isDetectingGenre: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        Spacer(modifier = Modifier.height(28.dp))
        // Vinyl & Waveforms Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().height(180.dp)
        ) {
            AnimatedWaveform(isPlaying = isPlaying, modifier = Modifier.weight(1f).height(60.dp))
            VinylRecord(modifier = Modifier.size(180.dp))
            AnimatedWaveform(isPlaying = isPlaying, reverse = true, modifier = Modifier.weight(1f).height(60.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Now Playing",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.basicMarquee().padding(horizontal = 16.dp)
        )
        Text(
            text = if (isPlaying && artist.isNotBlank()) "by $artist" else artist,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.basicMarquee().padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Always reserve space for the genre pill
        Box(
            modifier = Modifier.height(36.dp),
            contentAlignment = Alignment.Center
        ) {

                if (!isAutoEqEnabled) {
                    // AudixEQ is off — show greyed-out pill
                    PillContainer(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        backgroundColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                        text = "AudixEQ Off",
                        isBold = false
                    )
                } else if (genre != null) {
                    // AudixEQ on + genre detected (or fallback)
                    val pillColor = when {
                        genre == "Offline" -> MaterialTheme.colorScheme.error
                        genre.startsWith("Error:") || genre == "Unknown" -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.primary
                    }
                    
                    val displayText = when {
                        genre == "Offline" -> "Offline"
                        genre == "Rate Limited" -> "Rate Limited"
                        genre.startsWith("Error:") || genre == "Unknown" -> "Unknown Genre"
                        else -> "Genre: $genre"
                    }

                    PillContainer(
                        color = pillColor,
                        backgroundColor = pillColor.copy(alpha = 0.2f),
                        text = displayText,
                        isBold = true
                    )
                } else if (isDetectingGenre && title != "No Song Detected") {
                    // Actively detecting — show animated pill
                    val infiniteTransition = rememberInfiniteTransition(label = "detecting_pulse")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(700, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pill_alpha"
                    )
                    PillContainer(
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = alpha),
                        backgroundColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                        text = "Detecting Genre...",
                        isBold = false
                    )
                } else if (title != "No Song Detected") {
                    // AudixEQ on but not yet detecting (initial state)
                    PillContainer(
                        color = MaterialTheme.colorScheme.tertiary,
                        backgroundColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                        text = "Detecting Genre...",
                        isBold = false
                    )
                } else {
                    Spacer(modifier = Modifier.size(0.dp))
                }
        }
    }

}

@Composable
private fun PillContainer(
    color: androidx.compose.ui.graphics.Color,
    backgroundColor: androidx.compose.ui.graphics.Color,
    text: String,
    isBold: Boolean
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = color,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center
        )
    }
}


@Composable
fun VinylRecord(modifier: Modifier = Modifier) {
    // Circular clip + scale up 1.35x to zoom into disc, hiding most of the white PNG background
    Box(
        modifier = modifier
            .clip(CircleShape),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(id = R.drawable.vinyl_logo),
            contentDescription = "Vinyl Record",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize(1.55f)
                .scale(1.55f)
        )
    }
}

@Composable
fun AnimatedWaveform(isPlaying: Boolean, reverse: Boolean = false, modifier: Modifier = Modifier) {
    val animationSpecs = if (reverse) listOf(350, 600, 400, 500, 300) else listOf(300, 500, 400, 600, 350)
    val barColor = MaterialTheme.colorScheme.primary

    val infiniteTransition = rememberInfiniteTransition(label = "waveform")

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until 5) {
            val specDuration = animationSpecs[i]
            
            // Using infiniteTransition.animateFloat for smoother, system-managed animation
            val heightFraction by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = if (isPlaying) 1f else 0.21f, // If not playing, keep it steady at 0.2f
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = specDuration, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$i"
            )

            // Further optimized stationary bars when not playing
            val finalHeightFraction = if (isPlaying) heightFraction else 0.2f

            Spacer(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .drawBehind {
                        val actualHeight = size.height * finalHeightFraction
                        val topOffset = (size.height - actualHeight) / 2f
                        
                        drawRoundRect(
                            color = barColor,
                            topLeft = androidx.compose.ui.geometry.Offset(0f, topOffset),
                            size = androidx.compose.ui.geometry.Size(size.width, actualHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                        )
                    }
            )
        }
    }
}
