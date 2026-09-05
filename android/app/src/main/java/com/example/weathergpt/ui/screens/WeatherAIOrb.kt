package com.example.weathergpt.ui.screens

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
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
//  WEATHER AI ORB — Main Composable
// ============================================================

/**
 * Premium audio-reactive AI orb for WeatherGPT.
 *
 * @param orbState   The current assistant state driving all animations.
 * @param audioAmplitude  Real-time RMS amplitude from [VoiceAssistantManager.rmsLevel] (0–1).
 * @param modifier   Standard Compose modifier.
 * @param onTap      Called when the user taps the orb.
 */
@Composable
fun WeatherAIOrb(
    orbState: OrbState,
    audioAmplitude: Float,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current

    // Smooth the raw RMS amplitude with a spring for natural attack/release.
    // Fast attack (spring responds quickly to increases), slow release (spring
    // settles gently when signal drops).
    val smoothedAmplitude by animateFloatAsState(
        targetValue = audioAmplitude.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = Spring.StiffnessLow
        ),
        label = "amplitude"
    )

    val infinite = rememberInfiniteTransition(label = "orb_infinite")

    // ---- Breathing scale ---------------------------------------------------
    val breathTarget = when (orbState) {
        OrbState.IDLE    -> 1.02f
        OrbState.PAUSED  -> 1.0f
        OrbState.OFFLINE -> 1.0f
        OrbState.ERROR   -> 1.01f
        else             -> 1.035f
    }
    val breathDurationMs = when (orbState) {
        OrbState.IDLE        -> 4200
        OrbState.LISTENING   -> 1800
        OrbState.PROCESSING  -> 1200
        OrbState.AI_SPEAKING -> 1100
        OrbState.PAUSED      -> 6000
        OrbState.ERROR       -> 3000
        OrbState.OFFLINE     -> 8000
        else                 -> 2500
    }
    val breathScale by infinite.animateFloat(
        initialValue = 0.98f,
        targetValue  = breathTarget,
        animationSpec = infiniteRepeatable(
            animation    = tween(breathDurationMs, easing = FastOutSlowInEasing),
            repeatMode   = RepeatMode.Reverse
        ),
        label = "breath"
    )

    // ---- Fluid rotation ----------------------------------------------------
    val fluidDurationMs = when (orbState) {
        OrbState.IDLE          -> 14000
        OrbState.LISTENING     -> 6000
        OrbState.USER_SPEAKING -> 3500
        OrbState.PROCESSING    -> 2500
        OrbState.AI_SPEAKING   -> 5000
        OrbState.PAUSED        -> 40000
        OrbState.OFFLINE       -> 60000
        else                   -> 12000
    }
    val fluidAngle by infinite.animateFloat(
        initialValue  = 0f,
        targetValue   = 360f,
        animationSpec = infiniteRepeatable(
            animation  = tween(fluidDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fluid"
    )

    // ---- Orbital ring rotation ---------------------------------------------
    val ringDurationMs = when (orbState) {
        OrbState.PROCESSING  -> 2200
        OrbState.AI_SPEAKING -> 4500
        OrbState.LISTENING   -> 5000
        else                 -> 9000
    }
    val ringAngle by infinite.animateFloat(
        initialValue  = 0f,
        targetValue   = 360f,
        animationSpec = infiniteRepeatable(
            animation  = tween(ringDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring"
    )

    // ---- Glow pulse --------------------------------------------------------
    val glowPulseDurationMs = when (orbState) {
        OrbState.PROCESSING  -> 700
        OrbState.AI_SPEAKING -> 800
        OrbState.LISTENING   -> 1200
        else                 -> 2000
    }
    val glowPulse by infinite.animateFloat(
        initialValue  = 0.85f,
        targetValue   = 1.15f,
        animationSpec = infiniteRepeatable(
            animation  = tween(glowPulseDurationMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )

    // ---- Ripple rings (LISTENING / USER_SPEAKING) --------------------------
    val rippleProgress by infinite.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1800, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple"
    )

    // ---- Derived animation parameters --------------------------------------

    // Effective scale driven by breathing + amplitude
    val effectiveScale = when (orbState) {
        OrbState.USER_SPEAKING -> (breathScale + smoothedAmplitude * 0.08f).coerceIn(0.96f, 1.14f)
        OrbState.AI_SPEAKING   -> (breathScale + smoothedAmplitude * 0.05f).coerceIn(0.96f, 1.10f)
        else                   -> breathScale
    }

    // Glow intensity (drives halo, ring brightness, ripple alpha)
    val glowIntensity = (when (orbState) {
        OrbState.IDLE          -> 0.35f * glowPulse
        OrbState.LISTENING     -> 0.55f * glowPulse
        OrbState.USER_SPEAKING -> (0.42f + smoothedAmplitude * 0.58f) * glowPulse.coerceIn(0.9f, 1.1f)
        OrbState.PROCESSING    -> 0.72f * glowPulse
        OrbState.AI_SPEAKING   -> (0.38f + smoothedAmplitude * 0.48f) * glowPulse.coerceIn(0.9f, 1.1f)
        OrbState.PAUSED        -> 0.20f
        OrbState.ERROR         -> 0.30f * glowPulse
        OrbState.OFFLINE       -> 0.10f
    }).coerceIn(0f, 1f)

    // Fluid wave amplitude
    val waveAmp = when (orbState) {
        OrbState.IDLE          -> 0.12f
        OrbState.LISTENING     -> 0.22f
        OrbState.USER_SPEAKING -> (0.20f + smoothedAmplitude * 0.62f).coerceIn(0f, 0.88f)
        OrbState.PROCESSING    -> 0.42f
        OrbState.AI_SPEAKING   -> (0.18f + smoothedAmplitude * 0.46f).coerceIn(0f, 0.72f)
        OrbState.PAUSED        -> 0.04f
        OrbState.ERROR         -> 0.04f
        OrbState.OFFLINE       -> 0.02f
    }

    // Particle alpha
    val particleAlpha = (when (orbState) {
        OrbState.IDLE          -> 0.15f
        OrbState.LISTENING     -> 0.30f
        OrbState.USER_SPEAKING -> (0.20f + smoothedAmplitude * 0.62f).coerceIn(0f, 1f)
        OrbState.PROCESSING    -> (0.55f * glowPulse).coerceIn(0f, 1f)
        OrbState.AI_SPEAKING   -> (0.25f + smoothedAmplitude * 0.46f).coerceIn(0f, 1f)
        OrbState.PAUSED        -> 0.05f
        OrbState.ERROR         -> 0.05f
        OrbState.OFFLINE       -> 0.0f
    }).coerceIn(0f, 1f)

    // ---- Render ------------------------------------------------------------
    Box(
        modifier = modifier
            .size(220.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null
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
                    // Apply breathing + amplitude scale via graphicsLayer so
                    // the touch target remains constant at 220dp.
                    scaleX = effectiveScale
                    scaleY = effectiveScale
                }
        ) {
            drawOrbLayers(
                orbState       = orbState,
                fluidAngle     = Math.toRadians(fluidAngle.toDouble()).toFloat(),
                ringAngle      = ringAngle,
                glowIntensity  = glowIntensity,
                waveAmp        = waveAmp,
                particleAlpha  = particleAlpha,
                rippleProgress = rippleProgress
            )
        }
    }
}

// ============================================================
//  ORB CANVAS DRAWING — All 9 layers
// ============================================================

private fun DrawScope.drawOrbLayers(
    orbState:       OrbState,
    fluidAngle:     Float,   // radians
    ringAngle:      Float,   // degrees (for drawArc startAngle)
    glowIntensity:  Float,
    waveAmp:        Float,
    particleAlpha:  Float,
    rippleProgress: Float
) {
    val c = Offset(size.width / 2f, size.height / 2f)
    // Base radius is slightly inset so outer layers have room to breathe.
    val r = size.minDimension * 0.36f

    // State-based colour palette
    val cyanColor   = when (orbState) {
        OrbState.ERROR   -> Color(0xFFFF6B6B)
        OrbState.OFFLINE -> Color(0xFF5E6B7C)
        else             -> Color(0xFF52D9FF)
    }
    val blueColor   = when (orbState) {
        OrbState.ERROR   -> Color(0xFFCC4444)
        OrbState.OFFLINE -> Color(0xFF3A4758)
        else             -> Color(0xFF4DA3FF)
    }
    val purpleColor = when (orbState) {
        OrbState.OFFLINE -> Color(0xFF2D3A4A)
        else             -> Color(0xFF7C5CFF)
    }
    val deepColor = Color(0xFF050A12)

    // ------------------------------------------------------------------
    // LAYER 1 — Ambient outer halo (very wide, very low opacity)
    // ------------------------------------------------------------------
    val haloA = (glowIntensity * 0.22f).coerceIn(0f, 0.30f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                cyanColor.copy(alpha = haloA * 1.4f),
                cyanColor.copy(alpha = haloA * 0.35f),
                Color.Transparent
            ),
            center = c,
            radius = r * 1.70f
        ),
        radius = r * 1.70f,
        center = c
    )

    // ------------------------------------------------------------------
    // LAYER 2 — Soft inner glow (simulated blur: 3 concentric strokes)
    // ------------------------------------------------------------------
    for (i in 1..3) {
        val ga = (glowIntensity * 0.42f / i).coerceIn(0f, 0.5f)
        drawCircle(
            color  = cyanColor.copy(alpha = ga),
            radius = r + (i * 7f),
            center = c,
            style  = Stroke(width = 9f - i * 2f)
        )
    }

    // ------------------------------------------------------------------
    // LAYER 3 — Main sphere body (radial gradient, off-centre for depth)
    // ------------------------------------------------------------------
    val sAlpha = if (orbState == OrbState.OFFLINE) 0.55f else 0.95f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                blueColor.copy(alpha = sAlpha * 0.85f),
                Color(0xFF1A3580).copy(alpha = sAlpha),
                deepColor.copy(alpha = sAlpha)
            ),
            center = Offset(c.x - r * 0.22f, c.y - r * 0.22f),
            radius = r * 1.1f
        ),
        radius = r,
        center = c
    )

    // ------------------------------------------------------------------
    // LAYER 4 — Animated fluid inner core (orbits inside sphere)
    // ------------------------------------------------------------------
    val fOff = Offset(
        c.x + cos(fluidAngle) * r * 0.28f,
        c.y + sin(fluidAngle) * r * 0.28f
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                cyanColor.copy(alpha  = (waveAmp * 0.90f).coerceIn(0f, 1f)),
                purpleColor.copy(alpha = (waveAmp * 0.60f).coerceIn(0f, 1f)),
                Color.Transparent
            ),
            center = fOff,
            radius = r * 0.74f
        ),
        radius = r * 0.74f,
        center = fOff
    )

    // ------------------------------------------------------------------
    // LAYER 5 — Fluid S-curve waves (two bezier paths, amplitude driven)
    // ------------------------------------------------------------------
    if (waveAmp > 0.03f) {
        // Primary wave
        val wy1 = c.y + sin(fluidAngle) * r * waveAmp * 0.78f
        val wave1 = Path().apply {
            moveTo(c.x - r * 0.88f, wy1)
            cubicTo(
                c.x - r * 0.28f, wy1 - r * waveAmp * 1.30f,
                c.x + r * 0.28f, wy1 + r * waveAmp * 1.30f,
                c.x + r * 0.88f, wy1
            )
        }
        drawPath(
            path  = wave1,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    cyanColor.copy(alpha  = (waveAmp * 1.1f).coerceIn(0f, 0.90f)),
                    purpleColor.copy(alpha = (waveAmp * 0.7f).coerceIn(0f, 0.70f)),
                    Color.Transparent
                ),
                start = Offset(c.x - r, c.y),
                end   = Offset(c.x + r, c.y)
            ),
            style = Stroke(
                width = (7f + waveAmp * 15f).coerceIn(5f, 24f),
                cap   = StrokeCap.Round
            )
        )

        // Secondary wave (opposite phase, lower amplitude)
        val wy2 = c.y - sin(fluidAngle) * r * waveAmp * 0.46f
        val wave2 = Path().apply {
            moveTo(c.x - r * 0.70f, wy2)
            cubicTo(
                c.x - r * 0.18f, wy2 + r * waveAmp * 0.85f,
                c.x + r * 0.18f, wy2 - r * waveAmp * 0.85f,
                c.x + r * 0.70f, wy2
            )
        }
        drawPath(
            path  = wave2,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    purpleColor.copy(alpha = (waveAmp * 0.50f).coerceIn(0f, 0.55f)),
                    Color.Transparent
                ),
                start = Offset(c.x - r, c.y),
                end   = Offset(c.x + r, c.y)
            ),
            style = Stroke(
                width = (4f + waveAmp * 7f).coerceIn(3f, 13f),
                cap   = StrokeCap.Round
            )
        )
    }

    // ------------------------------------------------------------------
    // LAYER 6 — Glass specular highlight (upper-left)
    // ------------------------------------------------------------------
    val specC = Offset(c.x - r * 0.26f, c.y - r * 0.28f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.62f),
                Color.White.copy(alpha = 0.18f),
                Color.Transparent
            ),
            center = specC,
            radius = r * 0.36f
        ),
        radius = r * 0.36f,
        center = specC
    )
    // Tiny secondary specular for realism
    val specC2 = Offset(c.x + r * 0.34f, c.y + r * 0.30f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
            center = specC2,
            radius = r * 0.16f
        ),
        radius = r * 0.16f,
        center = specC2
    )

    // ------------------------------------------------------------------
    // LAYER 7 — Particles (8 dots orbiting at varying radii)
    // ------------------------------------------------------------------
    if (particleAlpha > 0.01f) {
        // Each entry: [orbitRadiusFactor, baseAngleRadians, dotRadius]
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
            val px    = c.x + cos(angle) * r * pd[0]
            val py    = c.y + sin(angle) * r * pd[0]
            drawCircle(
                color  = cyanColor.copy(alpha = (particleAlpha * 0.80f).coerceIn(0f, 0.80f)),
                radius = pd[2] * (particleAlpha * 0.7f + 0.3f).coerceIn(0.3f, 1.0f),
                center = Offset(px, py)
            )
        }
    }

    // ------------------------------------------------------------------
    // LAYER 8 — Orbital ring arc (rotates, creates focus / halo effect)
    // ------------------------------------------------------------------
    val rRad  = r * 1.07f
    val ringA = (glowIntensity * 0.72f).coerceIn(0f, 0.82f)
    val ringStroke = if (orbState == OrbState.PROCESSING) 2.8f else 1.6f

    // Primary arc (240° bright segment)
    drawArc(
        color      = cyanColor.copy(alpha = ringA),
        startAngle = ringAngle,
        sweepAngle = 240f,
        useCenter  = false,
        topLeft    = Offset(c.x - rRad, c.y - rRad),
        size       = Size(rRad * 2, rRad * 2),
        style      = Stroke(width = ringStroke, cap = StrokeCap.Round)
    )
    // Trailing arc (60° dim purple tail)
    drawArc(
        color      = purpleColor.copy(alpha = (ringA * 0.45f).coerceIn(0f, 0.45f)),
        startAngle = ringAngle + 240f,
        sweepAngle = 60f,
        useCenter  = false,
        topLeft    = Offset(c.x - rRad, c.y - rRad),
        size       = Size(rRad * 2, rRad * 2),
        style      = Stroke(width = 1.0f, cap = StrokeCap.Round)
    )

    // ------------------------------------------------------------------
    // LAYER 9 — Ripple rings (LISTENING and USER_SPEAKING states only)
    // ------------------------------------------------------------------
    if (orbState == OrbState.LISTENING || orbState == OrbState.USER_SPEAKING) {
        val maxRipple = r * (1.58f + waveAmp * 0.38f)

        fun drawRipple(progress: Float, alphaScale: Float, strokeW: Float) {
            val rr  = r + (maxRipple - r) * progress
            val ra  = ((1f - progress) * alphaScale * glowIntensity).coerceIn(0f, 1f)
            if (ra > 0.01f) {
                drawCircle(
                    color  = cyanColor.copy(alpha = ra),
                    radius = rr,
                    center = c,
                    style  = Stroke(width = strokeW)
                )
            }
        }

        drawRipple(rippleProgress,                         0.45f, 1.8f)
        drawRipple((rippleProgress + 0.40f) % 1f, 0.30f, 1.3f)
        drawRipple((rippleProgress + 0.72f) % 1f, 0.18f, 1.0f)
    }
}
