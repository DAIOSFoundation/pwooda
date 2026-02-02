package com.banya.neulpum.presentation.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun EqualizerBars(active: Boolean, barColor: Color, modifier: Modifier = Modifier) {
    if (!active) return
    val transition = rememberInfiniteTransition(label = "eq_small")
    val a by transition.animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(520, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse
        ), label = "a"
    )
    val b by transition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(640, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse
        ), label = "b"
    )
    val c by transition.animateFloat(
        initialValue = 0.45f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(430, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse
        ), label = "c"
    )
    Row(modifier.height(20.dp)) {
        Bar(heightFactor = a, color = barColor)
        Spacer(Modifier.width(6.dp))
        Bar(heightFactor = b, color = barColor)
        Spacer(Modifier.width(6.dp))
        Bar(heightFactor = c, color = barColor)
    }
}

@Composable
fun EqualizerCenter(active: Boolean, barColor: Color, modifier: Modifier = Modifier) {
    if (!active) return
    val transition = rememberInfiniteTransition(label = "eq_center")
    val durations = listOf(420, 520, 460, 600, 480, 560, 500)
    val factors = durations.mapIndexed { idx, d ->
        transition.animateFloat(
            initialValue = 0.25f + (idx % 3) * 0.1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(d, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse
            ), label = "f$idx"
        )
    }
    Row(modifier.height(56.dp)) {
        factors.forEachIndexed { i, s ->
            if (i != 0) Spacer(Modifier.width(8.dp))
            Bar(heightFactor = s.value, color = barColor)
        }
    }
}

@Composable
fun EqualizerCenterReactive(active: Boolean, level: Float, barColor: Color, modifier: Modifier = Modifier) {
    if (!active) return
    val clamped = remember(level) { level.coerceIn(0f, 1f) }
    Row(modifier.height(56.dp)) {
        val bars = 7
        for (i in 0 until bars) {
            if (i != 0) Spacer(Modifier.width(8.dp))
            val factor = (0.2f + clamped * (0.8f)) * (1f - (kotlin.math.abs(3 - i) * 0.08f))
            Bar(heightFactor = factor.coerceIn(0.15f, 1f), color = barColor)
        }
    }
}

@Composable
private fun Bar(heightFactor: Float, color: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.9f)),
        modifier = Modifier
            .width(8.dp)
            .fillMaxHeight(heightFactor)
            .clip(RoundedCornerShape(4.dp))
    ) {}
}


