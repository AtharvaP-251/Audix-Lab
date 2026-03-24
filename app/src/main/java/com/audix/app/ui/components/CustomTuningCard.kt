package com.audix.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun CustomTuningCard(
    isCustomTuningEnabled: Boolean,
    onCustomTuningChange: (Boolean) -> Unit,
    customBass: Float,
    onCustomBassChange: (Float) -> Unit,
    onCustomBassChangeFinished: () -> Unit,
    customVocals: Float,
    onCustomVocalsChange: (Float) -> Unit,
    onCustomVocalsChangeFinished: () -> Unit,
    customTreble: Float,
    onCustomTrebleChange: (Float) -> Unit,
    onCustomTrebleChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Track whether this is the first composition to skip the initial animation
    var isFirstComposition by remember { mutableStateOf(true) }

    AudixCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Custom Tuning",
                        tint = if (isCustomTuningEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Custom Tuning",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Reset button: always visible when custom tuning is enabled.
                    // Greyed out when all values are already 0, tinted when values are non-zero.
                    if (isCustomTuningEnabled) {
                        val hasNonZeroValues = customBass != 0f || customVocals != 0f || customTreble != 0f
                        Spacer(modifier = Modifier.width(8.dp))
                        androidx.compose.material3.IconButton(
                            onClick = {
                                onCustomBassChange(0f)
                                onCustomBassChangeFinished()
                                onCustomVocalsChange(0f)
                                onCustomVocalsChangeFinished()
                                onCustomTrebleChange(0f)
                                onCustomTrebleChangeFinished()
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset Tuning",
                                tint = if (hasNonZeroValues)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                AudixSwitch(
                    checked = isCustomTuningEnabled,
                    onCheckedChange = { newValue ->
                        isFirstComposition = false
                        onCustomTuningChange(newValue)
                    }
                )
            }

            AnimatedVisibility(
                visible = isCustomTuningEnabled,
                enter = if (isFirstComposition) {
                    // No animation on initial render — just appear instantly
                    fadeIn(animationSpec = spring(stiffness = Spring.StiffnessHigh)) +
                    expandVertically(animationSpec = spring(stiffness = Spring.StiffnessHigh))
                } else {
                    fadeIn(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ) + expandVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                },
                exit = fadeOut(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            ) {
                Column(modifier = Modifier.padding(top = 24.dp)) {
                    AudixInnerCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            TuningRow(
                                label = "Bass",
                                value = customBass,
                                onValueChange = onCustomBassChange,
                                onValueChangeFinished = onCustomBassChangeFinished
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            TuningRow(
                                label = "Vocals",
                                value = customVocals,
                                onValueChange = onCustomVocalsChange,
                                onValueChangeFinished = onCustomVocalsChangeFinished
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            TuningRow(
                                label = "Treble",
                                value = customTreble,
                                onValueChange = onCustomTrebleChange,
                                onValueChangeFinished = onCustomTrebleChangeFinished
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TuningRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    val displayVal = kotlin.math.round(value).toInt()
    val prefix = if (displayVal > 0) "+" else ""
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "$prefix$displayVal",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        AudixSlider(
            value = value,
            onValueChange = { onValueChange(kotlin.math.round(it)) },
            onValueChangeFinished = onValueChangeFinished,
            valueRange = -5f..5f,
            steps = 9,
            isBipolar = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
