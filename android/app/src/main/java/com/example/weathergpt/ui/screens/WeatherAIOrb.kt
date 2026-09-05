package com.example.weathergpt.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

// ============================================================
//  ORB STATE ENUM
// ============================================================

enum class OrbState {
    IDLE,
    LISTENING,
    USER_SPEAKING,
    PROCESSING,
    AI_SPEAKING,
    PAUSED,
    ERROR,
    OFFLINE
}

// ============================================================
//  WEATHER AI ORB — Living Organic Assistant Orb
// ============================================================

/**
 * Premium audio-reactive living organism AI orb for WeatherGPT.
 *
 * Implements realistic biological respiration (asymmetric inhale, apex hold,
 * smooth exhale, resting pause) combined with micro-vascular pulses and
 * real-time audio reactivity.
 */
@Composable
fun WeatherAIOrb(
    orbState: OrbState,
    audioAmplitude: Float,
    modifier: Modifier = Modifier,
    size: Dp = 190.dp,
    onTap: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current

    // Smooth raw RMS amplitude with organic spring attack/release
    val smoothedAmplitude by animateFloatAsState(
        targetValue = audioAmplitude.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = 0.62f,
            stiffness = Spring.StiffnessLow
        ),
        label = "amplitude"
    )

    val infinite = rememberInfiniteTransition(label = "orb_living_transition")

    // ========================================================
    // 1. ORGANIC RESPIRATION (Keyframed Asymmetric Breathing)
    // ========================================================
    // Real lung/organism breathing curve:
    // 0% -> 38% Inhale (expand)
    // 38% -> 46% Inhale Hold (slight apex stretch)
    // 46% -> 82% Exhale (smooth relaxation)
    // 82% -> 100% Resting Pause
    val breathCycleMs = when (orbState) {
        OrbState.IDLE -> 4600
        OrbState.LISTENING -> 2200
        OrbState.USER_SPEAKING -> 1600
        OrbState.PROCESSING -> 1400
        OrbState.AI_SPEAKING -> 1800
        OrbState.PAUSED -> 6000
        OrbState.ERROR -> 3200
        OrbState.OFFLINE -> 8000
    }

    val organicBreathScale by infinite.animateFloat(
        initialValue = 0.965f,
        targetValue = 0.965f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = breathCycleMs
                0.965f at 0 using FastOutSlowInEasing          // Start of inhale
                1.042f at (breathCycleMs * 0.38f).toInt() using LinearOutSlowInEasing // Inhale Peak
                1.048f at (breathCycleMs * 0.46f).toInt() using FastOutSlowInEasing   // Apex hold
                0.968f at (breathCycleMs * 0.82f).toInt() using FastOutSlowInEasing   // Exhale bottom
                0.965f at breathCycleMs                                               // Rest pause
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "organic_breath"
    )

    // ========================================================
    // 2. MICRO-BIOMORPHIC HEARTBEAT / VITALITY PULSE
    // ========================================================
    val microPulse by infinite.animateFloat(
        initialValue = 0.992f,
        targetValue = 1.008f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micro_pulse"
    )

    // ========================================================
    // 3. FLUID INTERNAL MOTION (Multi-harmonic swirling)
    // ========================================================
    val fluidAngleDuration = when (orbState) {
        OrbState.IDLE -> 11000
        OrbState.LISTENING -> 5500
        OrbState.USER_SPEAKING -> 3200
        OrbState.PROCESSING -> 2000
        OrbState.AI_SPEAKING -> 4200
        OrbState.PAUSED -> 30000
        OrbState.OFFLINE -> 50000
        else -> 10000
    }
    val fluidAngle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(fluidAngleDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fluid_angle"
    )

    // ========================================================
    // 4. BIOLUMINESCENT GLOW PULSE
    // ========================================================
    val glowPulse by infinite.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (orbState) {
                    OrbState.PROCESSING -> 650
                    OrbState.AI_SPEAKING -> 850
                    OrbState.LISTENING -> 1100
                    else -> 2100
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )

    // ========================================================
    // 5. ORBITAL RING & RIPPLE
    // ========================================================
    val ringAngle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (orbState == OrbState.PROCESSING) 1900 else 7000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_angle"
    )

    val rippleProgress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1700, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple_progress"
    )

    // Combined organic scale = Respiration + Micro Heartbeat + Audio Reactivity
    val combinedScale = when (orbState) {
        OrbState.USER_SPEAKING -> (organicBreathScale * microPulse + smoothedAmplitude * 0.09f).coerceIn(0.95f, 1.15f)
        OrbState.AI_SPEAKING -> (organicBreathScale * microPulse + smoothedAmplitude * 0.06f).coerceIn(0.95f, 1.11f)
        OrbState.OFFLINE -> 0.98f
        OrbState.PAUSED -> 0.99f
        else -> (organicBreathScale * microPulse)
    }

    // Glow intensity
    val glowIntensity = (when (orbState) {
        OrbState.IDLE -> 0.38f * glowPulse
        OrbState.LISTENING -> 0.60f * glowPulse
        OrbState.USER_SPEAKING -> (0.45f + smoothedAmplitude * 0.55f) * glowPulse.coerceIn(0.9f, 1.1f)
        OrbState.PROCESSING -> 0.78f * glowPulse
        OrbState.AI_SPEAKING -> (0.40f + smoothedAmplitude * 0.48f) * glowPulse.coerceIn(0.9f, 1.1f)
        OrbState.PAUSED -> 0.20f
        OrbState.ERROR -> 0.35f * glowPulse
        OrbState.OFFLINE -> 0.08f
    }).coerceIn(0f, 1f)

    // Fluid wave amplitude
    val waveAmp = when (orbState) {
        OrbState.IDLE -> 0.14f
        OrbState.LISTENING -> 0.25f
        OrbState.USER_SPEAKING -> (0.22f + smoothedAmplitude * 0.65f).coerceIn(0f, 0.92f)
        OrbState.PROCESSING -> 0.46f
        OrbState.AI_SPEAKING -> (0.20f + smoothedAmplitude * 0.48f).coerceIn(0f, 0.76f)
        OrbState.PAUSED -> 0.05f
        OrbState.ERROR -> 0.05f
        OrbState.OFFLINE -> 0.02f
    }

    // Particle alpha
    val particleAlpha = (when (orbState) {
        OrbState.IDLE -> 0.18f
        OrbState.LISTENING -> 0.35f
        OrbState.USER_SPEAKING -> (0.25f + smoothedAmplitude * 0.60f).coerceIn(0f, 1f)
        OrbState.PROCESSING -> (0.60f * glowPulse).coerceIn(0f, 1f)
        OrbState.AI_SPEAKING -> (0.30f + smoothedAmplitude * 0.45f).coerceIn(0f, 1f)
        OrbState.PAUSED -> 0.05f
        OrbState.ERROR -> 0.05f
        OrbState.OFFLINE -> 0.0f
    }).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .size(size)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onTap()
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = combinedScale
                    scaleY = combinedScale
                }
        ) {
            drawLivingOrbLayers(
                orbState = orbState,
                fluidAngle = Math.toRadians(fluidAngle.toDouble()).toFloat(),
                ringAngle = ringAngle,
                glowIntensity = glowIntensity,
                waveAmp = waveAmp,
                particleAlpha = particleAlpha,
                rippleProgress = rippleProgress
            )
        }
    }
}

// ============================================================
//  LIVING ORB CANVAS RENDERING (9 Biomimetic Layers)
// ============================================================

private fun DrawScope.drawLivingOrbLayers(
    orbState: OrbState,
    fluidAngle: Float,
    ringAngle: Float,
    glowIntensity: Float,
    waveAmp: Float,
    particleAlpha: Float,
    rippleProgress: Float
) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val r = size.minDimension * 0.36f

    // Organic color scheme
    val cyanColor = when (orbState) {
        OrbState.ERROR -> Color(0xFFFF6B6B)
        OrbState.OFFLINE -> Color(0xFF5E6B7C)
        else -> Color(0xFF52D9FF)
    }
    val blueColor = when (orbState) {
        OrbState.ERROR -> Color(0xFFCC4444)
        OrbState.OFFLINE -> Color(0xFF3A4758)
        else -> Color(0xFF4DA3FF)
    }
    val purpleColor = when (orbState) {
        OrbState.OFFLINE -> Color(0xFF2D3A4A)
        else -> Color(0xFF8B7CFF)
    }
    val deepColor = Color(0xFF050A12)

    // ------------------------------------------------------------------
    // LAYER 1 — Ambient Respiration Halo (Bioluminescent Atmosphere)
    // ------------------------------------------------------------------
    val haloA = (glowIntensity * 0.26f).coerceIn(0f, 0.35f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                cyanColor.copy(alpha = haloA * 1.5f),
                purpleColor.copy(alpha = haloA * 0.45f),
                Color.Transparent
            ),
            center = c,
            radius = r * 1.75f
        ),
        radius = r * 1.75f,
        center = c
    )

    // ------------------------------------------------------------------
    // LAYER 2 — Multi-layered Soft Glow Rings (Simulating Membrane Aura)
    // ------------------------------------------------------------------
    for (i in 1..3) {
        val ga = (glowIntensity * 0.44f / i).coerceIn(0f, 0.5f)
        drawCircle(
            color = cyanColor.copy(alpha = ga),
            radius = r + (i * 6.5f),
            center = c,
            style = Stroke(width = 8f - i * 2f)
        )
    }

    // ------------------------------------------------------------------
    // LAYER 3 — Deep Space Biomembrane Body (Radial depth gradient)
    // ------------------------------------------------------------------
    val sAlpha = if (orbState == OrbState.OFFLINE) 0.55f else 0.95f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                blueColor.copy(alpha = sAlpha * 0.88f),
                Color(0xFF1A3580).copy(alpha = sAlpha),
                deepColor.copy(alpha = sAlpha)
            ),
            center = Offset(c.x - r * 0.22f, c.y - r * 0.22f),
            radius = r * 1.12f
        ),
        radius = r,
        center = c
    )

    // ------------------------------------------------------------------
    // LAYER 4 — Living Bioluminescent Core (Organic Harmonic Displacement)
    // ------------------------------------------------------------------
    // Organic harmonic path combining 1st and 2nd harmonics
    val harmonicX = cos(fluidAngle) * 0.75f + cos(fluidAngle * 2.3f) * 0.25f
    val harmonicY = sin(fluidAngle) * 0.75f + sin(fluidAngle * 2.3f) * 0.25f
    val coreOffset = Offset(
        c.x + harmonicX * r * 0.26f,
        c.y + harmonicY * r * 0.26f
    )

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                cyanColor.copy(alpha = (waveAmp * 0.92f).coerceIn(0f, 1f)),
                purpleColor.copy(alpha = (waveAmp * 0.65f).coerceIn(0f, 1f)),
                Color.Transparent
            ),
            center = coreOffset,
            radius = r * 0.76f
        ),
        radius = r * 0.76f,
        center = coreOffset
    )

    // ------------------------------------------------------------------
    // LAYER 5 — Organic Fluid Wave S-Curves (Dynamic Plasma Waves)
    // ------------------------------------------------------------------
    if (waveAmp > 0.03f) {
        val wy1 = c.y + sin(fluidAngle) * r * waveAmp * 0.80f
        val wave1 = Path().apply {
            moveTo(c.x - r * 0.88f, wy1)
            cubicTo(
                c.x - r * 0.28f, wy1 - r * waveAmp * 1.35f,
                c.x + r * 0.28f, wy1 + r * waveAmp * 1.35f,
                c.x + r * 0.88f, wy1
            )
        }
        drawPath(
            path = wave1,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    cyanColor.copy(alpha = (waveAmp * 1.15f).coerceIn(0f, 0.92f)),
                    purpleColor.copy(alpha = (waveAmp * 0.75f).coerceIn(0f, 0.75f)),
                    Color.Transparent
                ),
                start = Offset(c.x - r, c.y),
                end = Offset(c.x + r, c.y)
            ),
            style = Stroke(
                width = (7f + waveAmp * 16f).coerceIn(5f, 25f),
                cap = StrokeCap.Round
            )
        )

        // Secondary counter-wave
        val wy2 = c.y - sin(fluidAngle * 1.2f) * r * waveAmp * 0.48f
        val wave2 = Path().apply {
            moveTo(c.x - r * 0.72f, wy2)
            cubicTo(
                c.x - r * 0.18f, wy2 + r * waveAmp * 0.90f,
                c.x + r * 0.18f, wy2 - r * waveAmp * 0.90f,
                c.x + r * 0.72f, wy2
            )
        }
        drawPath(
            path = wave2,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    purpleColor.copy(alpha = (waveAmp * 0.55f).coerceIn(0f, 0.60f)),
                    Color.Transparent
                ),
                start = Offset(c.x - r, c.y),
                end = Offset(c.x + r, c.y)
            ),
            style = Stroke(
                width = (4f + waveAmp * 8f).coerceIn(3f, 14f),
                cap = StrokeCap.Round
            )
        )
    }

    // ------------------------------------------------------------------
    // LAYER 6 — Specular Glass Membrane Highlight
    // ------------------------------------------------------------------
    val specC = Offset(c.x - r * 0.26f, c.y - r * 0.28f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.64f),
                Color.White.copy(alpha = 0.18f),
                Color.Transparent
            ),
            center = specC,
            radius = r * 0.36f
        ),
        radius = r * 0.36f,
        center = specC
    )

    val specC2 = Offset(c.x + r * 0.34f, c.y + r * 0.30f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.12f), Color.Transparent),
            center = specC2,
            radius = r * 0.16f
        ),
        radius = r * 0.16f,
        center = specC2
    )

    // ------------------------------------------------------------------
    // LAYER 7 — Orbiting Living Particle Nodes
    // ------------------------------------------------------------------
    if (particleAlpha > 0.01f) {
        val particleDefs = arrayOf(
            floatArrayOf(0.73f, 0.000f, 3.2f),
            floatArrayOf(0.81f, 0.785f, 2.4f),
            floatArrayOf(0.69f, 1.571f, 3.0f),
            floatArrayOf(0.76f, 2.356f, 1.8f),
            floatArrayOf(0.71f, 3.142f, 3.4f),
            floatArrayOf(0.83f, 3.927f, 2.2f),
            floatArrayOf(0.74f, 4.712f, 2.8f),
            floatArrayOf(0.78f, 5.498f, 1.6f)
        )
        val ringRad = Math.toRadians(ringAngle.toDouble()).toFloat()
        for (pd in particleDefs) {
            val angle = pd[1] + ringRad
            val px = c.x + cos(angle) * r * pd[0]
            val py = c.y + sin(angle) * r * pd[0]
            drawCircle(
                color = cyanColor.copy(alpha = (particleAlpha * 0.82f).coerceIn(0f, 0.85f)),
                radius = pd[2] * (particleAlpha * 0.7f + 0.3f).coerceIn(0.3f, 1.0f),
                center = Offset(px, py)
            )
        }
    }

    // ------------------------------------------------------------------
    // LAYER 8 — Orbital Atmosphere Ring
    // ------------------------------------------------------------------
    val rRad = r * 1.07f
    val ringA = (glowIntensity * 0.75f).coerceIn(0f, 0.85f)
    val ringStroke = if (orbState == OrbState.PROCESSING) 2.8f else 1.6f

    drawArc(
        color = cyanColor.copy(alpha = ringA),
        startAngle = ringAngle,
        sweepAngle = 240f,
        useCenter = false,
        topLeft = Offset(c.x - rRad, c.y - rRad),
        size = Size(rRad * 2, rRad * 2),
        style = Stroke(width = ringStroke, cap = StrokeCap.Round)
    )
    drawArc(
        color = purpleColor.copy(alpha = (ringA * 0.45f).coerceIn(0f, 0.45f)),
        startAngle = ringAngle + 240f,
        sweepAngle = 60f,
        useCenter = false,
        topLeft = Offset(c.x - rRad, c.y - rRad),
        size = Size(rRad * 2, rRad * 2),
        style = Stroke(width = 1.0f, cap = StrokeCap.Round)
    )

    // ------------------------------------------------------------------
    // LAYER 9 — Audio-Reactive Ripple Waves (Listening / Speaking)
    // ------------------------------------------------------------------
    if (orbState == OrbState.LISTENING || orbState == OrbState.USER_SPEAKING) {
        val maxRipple = r * (1.58f + waveAmp * 0.38f)

        fun drawRipple(progress: Float, alphaScale: Float, strokeW: Float) {
            val rr = r + (maxRipple - r) * progress
            val ra = ((1f - progress) * alphaScale * glowIntensity).coerceIn(0f, 1f)
            if (ra > 0.01f) {
                drawCircle(
                    color = cyanColor.copy(alpha = ra),
                    radius = rr,
                    center = c,
                    style = Stroke(width = strokeW)
                )
            }
        }

        drawRipple(rippleProgress, 0.45f, 1.8f)
        drawRipple((rippleProgress + 0.40f) % 1f, 0.30f, 1.3f)
        drawRipple((rippleProgress + 0.72f) % 1f, 0.18f, 1.0f)
    }
}
