package com.audix.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.audix.app.ui.theme.InactiveGrey
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.audix.app.R
import com.audix.app.audio.SpatialProfileLibrary

@Composable
fun SpatialAudioCard(
    isSpatialEnabled: Boolean,
    isHeadphonesConnected: Boolean,
    onSpatialEnabledChange: (Boolean) -> Unit,
    spatialLevel: Int,
    onSpatialLevelChange: (Int) -> Unit,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    // Track whether this is the first composition to skip the initial animation
    var isFirstComposition by remember { mutableStateOf(true) }

    val profile = SpatialProfileLibrary.getProfile(
        if (isSpatialEnabled) spatialLevel.coerceIn(1, 5) else 0
    )

    AudixCard(
        modifier = modifier.graphicsLayer { alpha = if (isHeadphonesConnected) 1.0f else 0.5f },
        isHighlighted = isSpatialEnabled && isHeadphonesConnected
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // Header row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Clickable Title Area with Premium Interaction
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = isHeadphonesConnected) { onExpandedChange(!isExpanded) }
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                ) {
                    SpatialIcon(
                        color = if (isSpatialEnabled && isHeadphonesConnected) MaterialTheme.colorScheme.primary else InactiveGrey,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Spatial Audio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSpatialEnabled && isHeadphonesConnected) MaterialTheme.colorScheme.primary else InactiveGrey
                        )
                        Text(
                            text = if (isHeadphonesConnected) "Best with headphones" else "Headphones Required",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSpatialEnabled && isHeadphonesConnected) MaterialTheme.colorScheme.primary else InactiveGrey
                        )
                    }
                }

                // Vertical Divider
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .width(1.dp)
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                )

                // Toggle Area
                AudixSwitch(
                    checked = isSpatialEnabled && isHeadphonesConnected,
                    enabled = isHeadphonesConnected,
                    onCheckedChange = { newValue ->
                        isFirstComposition = false
                        onSpatialEnabledChange(newValue)
                    }
                )
            }

            val transition = updateTransition(targetState = isExpanded, label = "card_expansion")
            val expansion by transition.animateFloat(
                transitionSpec = {
                    spring(
                        dampingRatio = if (targetState) Spring.DampingRatioMediumBouncy else 0.8f,
                        stiffness = if (targetState) Spring.StiffnessLow else Spring.StiffnessMediumLow
                    )
                },
                label = "expansion_fraction"
            ) { if (it) 1f else 0f }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
            ) {
                if (isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = expansion }
                            .padding(top = 24.dp)
                    ) {

                    AudixInnerCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Slider label row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Spatial Level",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${spatialLevel.coerceIn(1, 5)} / 5",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Discrete 5-step slider (1..5, 4 steps between = 3 intermediate)
                            AudixSlider(
                                value = spatialLevel.coerceIn(1, 5).toFloat(),
                                onValueChange = { onSpatialLevelChange(kotlin.math.round(it).toInt()) },
                                valueRange = 1f..5f,
                                steps = 3, // 3 intermediate steps => snaps at 1, 2, 3, 4, 5
                                dotPositions = listOf(1f, 2f, 3f, 4f, 5f),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Level name + description
                            val displayProfile = SpatialProfileLibrary.getProfile(
                                spatialLevel.coerceIn(1, 5)
                            )
                            Text(
                                text = displayProfile.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = displayProfile.description,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
}


/**
 * Custom spatial audio icon — concentric arcs resembling a headphone/spatial waveform.
 */
@Composable
private fun SpatialIcon(color: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val strokeW = 2.dp.toPx()

        // Center dot
        drawCircle(
            color = color,
            radius = 2.dp.toPx(),
            center = Offset(cx, cy)
        )

        // Arc 1 (inner)
        drawArc(
            color = color,
            startAngle = -60f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = Offset(cx - size.width * 0.25f, cy - size.height * 0.25f),
            size = Size(size.width * 0.5f, size.height * 0.5f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )

        // Arc 2 (outer)
        drawArc(
            color = color,
            startAngle = -60f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = Offset(cx - size.width * 0.42f, cy - size.height * 0.42f),
            size = Size(size.width * 0.84f, size.height * 0.84f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )

        // Mirror arcs on left side
        drawArc(
            color = color,
            startAngle = 120f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = Offset(cx - size.width * 0.25f, cy - size.height * 0.25f),
            size = Size(size.width * 0.5f, size.height * 0.5f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )

        drawArc(
            color = color,
            startAngle = 120f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = Offset(cx - size.width * 0.42f, cy - size.height * 0.42f),
            size = Size(size.width * 0.84f, size.height * 0.84f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
    }
}
