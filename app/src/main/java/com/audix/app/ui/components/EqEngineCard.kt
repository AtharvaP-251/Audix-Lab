package com.audix.app.ui.components

import kotlin.math.roundToInt

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.*
import androidx.compose.ui.graphics.*
import com.audix.app.ui.theme.InactiveGrey
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun EqEngineCard(
    isAutoEqEnabled: Boolean,
    onAutoEqChange: (Boolean) -> Unit,
    eqIntensity: Float,
    onIntensityChange: (Float) -> Unit,
    onIntensityChangeFinished: () -> Unit,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    // Track whether this is the first composition to skip the initial animation
    var isFirstComposition by remember { mutableStateOf(true) }

    AudixCard(
        modifier = modifier,
        isHighlighted = isAutoEqEnabled
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
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
                        .clickable { onExpandedChange(!isExpanded) }
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                ) {
                    AudixEqLogo(
                        color = if (isAutoEqEnabled) MaterialTheme.colorScheme.primary else InactiveGrey,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Audix EQ Engine",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isAutoEqEnabled) MaterialTheme.colorScheme.primary else InactiveGrey
                    )
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
                    checked = isAutoEqEnabled,
                    onCheckedChange = { newValue ->
                        isFirstComposition = false
                        onAutoEqChange(newValue)
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "AutoEQ Intensity",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = "${(eqIntensity * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            AudixSlider(
                                value = eqIntensity.coerceAtLeast(0.1f),
                                onValueChange = onIntensityChange,
                                onValueChangeFinished = onIntensityChangeFinished,
                                valueRange = 0.1f..1.0f,
                                steps = 8,
                                dotPositions = listOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f),
                                modifier = Modifier.fillMaxWidth()
                            )

                        }
                    }
                }
        }
    }
}
}
}
