package com.banya.neulpum.presentation.ui.components.voice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class CircularParticleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val particles = mutableListOf<Particle>()
    private var fftData: ByteArray? = null
    private var isVisualizing = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private class Particle(
        var x: Float,
        var y: Float,
        var size: Float,
        var speed: Float,
        var angle: Float,
        var color: Int
    )

    fun startVisualizing() { isVisualizing = true }

    fun stopVisualizing() {
        isVisualizing = false
        particles.clear()
        invalidate()
    }

    fun setFftData(data: ByteArray?) {
        fftData = data
        if (isVisualizing) invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Draw white background first
        canvas.drawColor(Color.WHITE)

        if (!isVisualizing) return

        val centerX = width / 2f
        val centerY = height / 2f // 정확한 중앙
        val radius = min(centerX, centerY) * 0.5f

        val data = fftData
        if (data != null) {
            val numPoints = data.size / 2
            if (numPoints > 0) {
                val totalRadius = min(width, height) / 2f * 0.9f
                
                // Draw multiple animated rings with gradient colors
                for (ring in 0..3) {
                    val ringRadius = totalRadius * (0.2f + ring * 0.15f)
                    for (i in 0 until numPoints) {
                        val magnitude = abs(data[i].toInt())
                        val barHeight = (magnitude.toFloat() / 128f) * totalRadius * (0.4f + ring * 0.1f)
                        val angle = PI.toFloat() * 2 / numPoints * i
                        val x = centerX + (ringRadius + barHeight) * cos(angle)
                        val y = centerY + (ringRadius + barHeight) * sin(angle)

                        // App theme green gradient based on angle and time
                        val time = System.currentTimeMillis() * 0.001f
                        val colorRatio = (i.toFloat() / numPoints + time * 0.1f) % 1f
                        val baseHue = 120f // Green base hue
                        val hue = (baseHue + colorRatio * 60f) % 360f // Green to cyan range
                        val hsv = floatArrayOf(hue, 0.8f, 1f)
                        val color = Color.HSVToColor(hsv)
                        
                        paint.color = color
                        paint.alpha = (255 * (0.8f - ring * 0.1f) * (0.3f + magnitude / 128f)).toInt().coerceIn(80, 255)

                        val particleSize = (2f + ring * 2f + magnitude / 32f).coerceAtMost(12f)
                        canvas.drawCircle(x, y, particleSize, paint)
                    }
                }
            }
        }

        // Enhanced particle system with more colors and effects
        val audioIntensity = data?.let { 
            it.take(8).map { abs(it.toInt()) }.average().toFloat() / 128f 
        } ?: 0.3f
        
        if (particles.size < 200 && isVisualizing) {
            val angle = (0..360).random().toFloat()
            val startX = centerX + radius * cos(Math.toRadians(angle.toDouble()).toFloat())
            val startY = centerY + radius * sin(Math.toRadians(angle.toDouble()).toFloat())
            val magnitude = abs((data?.getOrNull(0) ?: 40).toInt())
            
            // Create more particles based on audio intensity
            val particleCount = (magnitude / 6 * audioIntensity).toInt().coerceIn(1, 8)
            repeat(particleCount) {
                val time = System.currentTimeMillis() * 0.001f
                val baseHue = 120f // Green base
                val hue = (baseHue + (it * 20f + time * 50f) % 80f) % 360f // Green to cyan range
                val hsv = floatArrayOf(hue, 0.9f, 1f)
                val particleColor = Color.HSVToColor(hsv)
                
                particles.add(
                    Particle(
                        x = startX,
                        y = startY,
                        size = (3..12).random().toFloat() * audioIntensity,
                        speed = (2..10).random().toFloat() * audioIntensity,
                        angle = angle + (it - particleCount/2) * 15f,
                        color = particleColor
                    )
                )
            }
        }

        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.x += p.speed * cos(Math.toRadians(p.angle.toDouble()).toFloat())
            p.y += p.speed * sin(Math.toRadians(p.angle.toDouble()).toFloat())
            p.size *= 0.96f // Shrink particles over time
            p.angle += 2f // Rotate particles
            
            if (p.x < -100 || p.x > width + 100 || p.y < -100 || p.y > height + 100 || p.size < 0.3f) {
                iterator.remove()
            } else {
                paint.color = p.color
                paint.alpha = (255 * (p.size / 12f)).toInt().coerceIn(100, 255)
                canvas.drawCircle(p.x, p.y, p.size, paint)
                
                // Add glow effect with higher opacity for white background
                paint.alpha = (255 * (p.size / 12f) * 0.5f).toInt().coerceIn(30, 120)
                canvas.drawCircle(p.x, p.y, p.size * 1.8f, paint)
            }
        }

        invalidate()
    }
}


