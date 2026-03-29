package com.audix.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AudixCard(
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val borderColor = if (isHighlighted) 
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f) 
    else 
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        
    val borderWidth = if (isHighlighted) 1.5.dp else 1.dp

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f))
            .border(
                width = borderWidth,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            ),
        content = content
    )
}


@Composable
fun AudixInnerCard(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                shape = RoundedCornerShape(16.dp)
            ),
        content = content
    )
}
