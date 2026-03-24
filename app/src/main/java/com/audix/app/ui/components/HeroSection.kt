package com.audix.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Now Playing",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Text(
            text = if (isPlaying && artist.isNotBlank()) "by $artist" else artist,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Always reserve space for the genre pill
        Box(
            modifier = Modifier.height(36.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!isAutoEqEnabled) {
                // AudixEQ is off — show greyed-out pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "AudixEQ Off",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else if (genre != null) {
                // AudixEQ on + genre detected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Genre: $genre",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
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
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Detecting Genre...",
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = alpha),
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else if (title != "No Song Detected") {
                // AudixEQ on but not yet detecting (initial state)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Detecting Genre...",
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
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

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until 5) {
            val heightFraction = remember { Animatable(0.2f) }
            val specDuration = animationSpecs[i]
            
            LaunchedEffect(isPlaying) {
                if (isPlaying) {
                    while (isActive) {
                        heightFraction.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = specDuration, easing = FastOutSlowInEasing)
                        )
                        heightFraction.animateTo(
                            targetValue = 0.2f,
                            animationSpec = tween(durationMillis = specDuration, easing = FastOutSlowInEasing)
                        )
                    }
                } else {
                    heightFraction.animateTo(
                        targetValue = 0.2f,
                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                    )
                }
            }

            Spacer(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .drawBehind {
                        val fraction = heightFraction.value
                        val actualHeight = size.height * fraction
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
