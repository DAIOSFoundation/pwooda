package com.banya.neulpum.presentation.ui.components.voice

import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

@Composable
fun CircularAudioWave(
    modifier: Modifier = Modifier,
    active: Boolean,
    level: Float,
    color: Color = Color(0xFF10A37F)
) {
    val transition: InfiniteTransition = rememberInfiniteTransition(label = "wave")
    val phase1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1200, easing = LinearEasing)),
        label = "p1"
    )
    val phase2 by transition.animateFloat(
        initialValue = 0.33f,
        targetValue = 1.33f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1400, easing = LinearEasing)),
        label = "p2"
    )
    val phase3 by transition.animateFloat(
        initialValue = 0.66f,
        targetValue = 1.66f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1600, easing = LinearEasing)),
        label = "p3"
    )
    val phase4 by transition.animateFloat(
        initialValue = 0.1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1000, easing = LinearEasing)),
        label = "p4"
    )

    val baseAlpha = if (active) 0.4f else 0.2f
    val baseRadiusFactor = if (active) (0.4f + 0.4f * level.coerceIn(0f, 1f)) else 0.4f
    val audioLevel = level.coerceIn(0f, 1f)

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxR = size.minDimension / 2f

        fun wave(alphaMul: Float, radiusMul: Float, strokeWidth: Float = 0f) {
            if (strokeWidth > 0f) {
                // Draw ring
                drawCircle(
                    color = color.copy(alpha = baseAlpha * alphaMul),
                    radius = maxR * radiusMul,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                )
            } else {
                // Draw filled circle
                drawCircle(
                    color = color.copy(alpha = baseAlpha * alphaMul),
                    radius = maxR * radiusMul,
                    center = center
                )
            }
        }

        // Multiple animated rings for flashy effect
        wave(alphaMul = 0.3f, radiusMul = (baseRadiusFactor + 0.3f * (phase1 % 1f)).coerceIn(0.2f, 1f))
        wave(alphaMul = 0.25f, radiusMul = (baseRadiusFactor + 0.25f * (phase2 % 1f)).coerceIn(0.25f, 1f))
        wave(alphaMul = 0.2f, radiusMul = (baseRadiusFactor + 0.2f * (phase3 % 1f)).coerceIn(0.3f, 1f))
        wave(alphaMul = 0.15f, radiusMul = (baseRadiusFactor + 0.15f * (phase4 % 1f)).coerceIn(0.35f, 1f))

        // Audio-reactive bars around the circle
        if (active && audioLevel > 0.1f) {
            val numBars = 32
            val barLength = maxR * 0.3f * audioLevel
            for (i in 0 until numBars) {
                val angle = (i * 360f / numBars) * kotlin.math.PI / 180f
                val startRadius = maxR * 0.7f
                val endRadius = startRadius + barLength * (0.5f + 0.5f * kotlin.math.sin(phase1 * 2 * kotlin.math.PI + i * 0.3f))
                
                val startX = center.x + startRadius * kotlin.math.cos(angle).toFloat()
                val startY = center.y + startRadius * kotlin.math.sin(angle).toFloat()
                val endX = center.x + endRadius * kotlin.math.cos(angle).toFloat()
                val endY = center.y + endRadius * kotlin.math.sin(angle).toFloat()
                
                drawLine(
                    color = color.copy(alpha = 0.8f * audioLevel),
                    start = androidx.compose.ui.geometry.Offset(startX.toFloat(), startY.toFloat()),
                    end = androidx.compose.ui.geometry.Offset(endX.toFloat(), endY.toFloat()),
                    strokeWidth = 3f
                )
            }
        }

        // Solid pulsing core
        val coreRadius = maxR * 0.15f * (if (active) (1f + 0.3f * audioLevel + 0.2f * kotlin.math.sin(phase1 * 2 * kotlin.math.PI).toFloat()) else 1f)
        drawCircle(
            color = color.copy(alpha = if (active) 0.9f else 0.6f),
            radius = coreRadius,
            center = center
        )

        // Inner glow
        drawCircle(
            color = color.copy(alpha = 0.3f),
            radius = coreRadius * 1.5f,
            center = center
        )
    }
}


