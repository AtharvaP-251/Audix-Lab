package com.audix.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
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
import com.audix.app.ui.theme.InactiveGrey
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    // Track whether this is the first composition to skip the initial animation
    var isFirstComposition by remember { mutableStateOf(true) }

    AudixCard(
        modifier = modifier,
        isHighlighted = isCustomTuningEnabled
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
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Custom Tuning",
                        tint = if (isCustomTuningEnabled) MaterialTheme.colorScheme.primary else InactiveGrey
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Custom Tuning",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isCustomTuningEnabled) MaterialTheme.colorScheme.primary else InactiveGrey
                    )

                    // Reset button: visible when the card is expanded.
                    // Greyed out when all values are already 0, tinted when values are non-zero.
                    if (isExpanded) {
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
                    checked = isCustomTuningEnabled,
                    onCheckedChange = { newValue ->
                        isFirstComposition = false
                        onCustomTuningChange(newValue)
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
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = -5f..5f,
            steps = 9,
            isBipolar = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
