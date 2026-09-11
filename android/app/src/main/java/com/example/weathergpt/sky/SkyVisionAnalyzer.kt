package com.example.weathergpt.sky

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.sqrt

enum class SkyDecisionState(val userMessage: String, val isAnalyzable: Boolean) {
    VALID_SKY("Open sky detected — ready to observe", true),
    BEDSHEET_OR_FABRIC("Fabric or bedsheet detected. Aim at natural outdoor sky.", false),
    INDOOR("Indoor objects / room in view. Point camera toward outdoor sky.", false),
    CEILING("Ceiling detected overhead. Tilt toward outdoor sky.", false),
    OBSTRUCTED_SKY("Sky is obstructed by buildings or trees. Point toward more open sky.", false),
    INSUFFICIENT_SKY("Not enough open sky is visible. Point the camera higher.", false),
    TOO_BLURRY("Hold the camera steady for a clearer sky view.", false),
    TOO_DARK("The image is too dark. Try pointing toward a brighter part of the sky.", false),
    OVEREXPOSED("There's too much glare. Try a different angle.", false),
    EXCESSIVE_GLARE("Excessive glare detected. Shield or tilt camera.", false),
    CAMERA_MOVING("Hold the camera steady.", false),
    UNCERTAIN("Cannot confidently identify the sky. Try another angle.", false),
    NO_SKY_DETECTED("No outdoor sky visible in frame. Point camera toward open sky.", false)
}

data class SkyQualityMetrics(
    val blurScore: Double,          // Laplacian variance (>14 is sharp)
    val isBlurry: Boolean,
    val meanLuminance: Double,
    val isTooDark: Boolean,
    val isOverexposed: Boolean,
    val isExcessiveGlare: Boolean,
    val glareFraction: Float,
    val qualityScore: Float         // 0.0 to 1.0
)

data class SkySegmentationResult(
    val skyCoverageFraction: Float,
    val cloudFractionWithinSky: Float,
    val visualCloudCondition: String, // "Clear", "Partly Cloudy", "Mostly Cloudy", "Overcast", "Dark Storm Cloud"
    val visualPrecipitationHint: Boolean,
    val visualHazeHint: Boolean,
    val averageLuminance: Double,
    val blueSkyRatio: Float
)

data class SkyEvaluationResult(
    val state: SkyDecisionState,
    val confidence: Float,           // 0.0 to 1.0
    val quality: SkyQualityMetrics,
    val segmentation: SkySegmentationResult,
    val indoorProbability: Float,
    val ceilingProbability: Float,
    val outdoorProbability: Float,
    val detectedMlLabels: List<String>
)

class SkyVisionAnalyzer {

    // Lazy singleton ML Kit Image Labeler
    private val mlKitLabeler by lazy {
        ImageLabeling.getClient(
            ImageLabelerOptions.Builder()
                .setConfidenceThreshold(0.35f)
                .build()
        )
    }

    // Circular temporal buffer of recent states
    private val temporalHistory = mutableListOf<SkyDecisionState>()
    private val MAX_HISTORY = 5

    suspend fun analyzeFrame(
        bitmap: Bitmap,
        sensorData: SensorOrientationData? = null,
        isSingleCapture: Boolean = false
    ): SkyEvaluationResult {
        // 1. Image Quality Gate Check
        val quality = evaluateImageQuality(bitmap)

        // Check immediate quality disqualifications (lenient on single capture tap)
        if (sensorData != null && !sensorData.isStable && !isSingleCapture) {
            recordState(SkyDecisionState.CAMERA_MOVING)
            return buildFallbackResult(SkyDecisionState.CAMERA_MOVING, quality, 0.4f)
        }

        if (quality.isBlurry && (!isSingleCapture || quality.blurScore < 4.0)) {
            recordState(SkyDecisionState.TOO_BLURRY)
            return buildFallbackResult(SkyDecisionState.TOO_BLURRY, quality, 0.5f)
        }

        if (quality.isTooDark) {
            recordState(SkyDecisionState.TOO_DARK)
            return buildFallbackResult(SkyDecisionState.TOO_DARK, quality, 0.5f)
        }

        if (quality.isOverexposed) {
            recordState(SkyDecisionState.OVEREXPOSED)
            return buildFallbackResult(SkyDecisionState.OVEREXPOSED, quality, 0.5f)
        }

        if (quality.isExcessiveGlare && !isSingleCapture) {
            recordState(SkyDecisionState.EXCESSIVE_GLARE)
            return buildFallbackResult(SkyDecisionState.EXCESSIVE_GLARE, quality, 0.5f)
        }

        // 2. ML Kit Indoor / Ceiling / Outdoor Labeling
        val mlLabels = runMlKitLabeling(bitmap)
        val (indoorScore, ceilingScore, fabricScore, outdoorScore) = computeSceneScores(mlLabels, sensorData)

        // 3. Algorithmic Geometric & Spectral Ceiling Verification
        val (gridEdgeCount, straightLineRatio, artificialLightCluster) = analyzeIndoorGeometry(bitmap)
        val hasIndoorGeometry = (gridEdgeCount > 15 || straightLineRatio > 0.45f)

        val isPointingUp = sensorData == null || sensorData.pitchDeg > 35f
        val isPointingDownOrFlat = sensorData != null && sensorData.pitchDeg <= 25f

        val finalCeilingProb = if (isPointingUp && (ceilingScore > 0.25f || artificialLightCluster)) {
            (ceilingScore + (if (artificialLightCluster) 0.35f else 0.15f)).coerceAtMost(0.99f)
        } else if (isPointingDownOrFlat) {
            0f
        } else {
            (ceilingScore * 0.4f).coerceAtMost(0.99f)
        }

        val finalIndoorProb = (indoorScore + (if (hasIndoorGeometry) 0.35f else 0f) + (finalCeilingProb * 0.4f)).coerceAtMost(0.99f)

        // If fabric or bedsheet detected with downward angle or high score, reject immediately!
        if (fabricScore > 0.40f || (sensorData != null && sensorData.pitchDeg < -5f && fabricScore > 0.25f && outdoorScore < 0.35f)) {
            recordState(SkyDecisionState.BEDSHEET_OR_FABRIC)
            return buildFallbackResult(
                state = SkyDecisionState.BEDSHEET_OR_FABRIC,
                quality = quality,
                confidence = fabricScore,
                indoorProb = finalIndoorProb,
                ceilingProb = finalCeilingProb,
                labels = mlLabels.map { it.first }
            )
        }

        // If indoor probability is high or indoor geometry/objects detected, prioritize INDOOR
        if (finalIndoorProb > 0.45f && outdoorScore < 0.38f) {
            recordState(SkyDecisionState.INDOOR)
            return buildFallbackResult(
                state = SkyDecisionState.INDOOR,
                quality = quality,
                confidence = finalIndoorProb,
                indoorProb = finalIndoorProb,
                ceilingProb = finalCeilingProb,
                labels = mlLabels.map { it.first }
            )
        }

        // Ceiling only if pointing upwards and ceiling probability is high
        if (finalCeilingProb > 0.45f && isPointingUp && outdoorScore < 0.35f) {
            recordState(SkyDecisionState.CEILING)
            return buildFallbackResult(
                state = SkyDecisionState.CEILING,
                quality = quality,
                confidence = finalCeilingProb,
                indoorProb = finalIndoorProb,
                ceilingProb = finalCeilingProb,
                labels = mlLabels.map { it.first }
            )
        }

        // 4. Sky Segmentation & Coverage
        val segmentation = performSkySegmentation(bitmap)
        val minCoverage = if (isSingleCapture) 0.18f else 0.25f

        if (segmentation.skyCoverageFraction < minCoverage) {
            val state = if (outdoorScore > 0.4f) SkyDecisionState.INSUFFICIENT_SKY else SkyDecisionState.NO_SKY_DETECTED
            recordState(state)
            return buildResult(state, quality, segmentation, 0.55f, finalIndoorProb, finalCeilingProb, outdoorScore, mlLabels)
        }

        if (!isSingleCapture && segmentation.skyCoverageFraction in 0.25f..0.38f && outdoorScore < 0.60f) {
            recordState(SkyDecisionState.OBSTRUCTED_SKY)
            return buildResult(SkyDecisionState.OBSTRUCTED_SKY, quality, segmentation, 0.65f, finalIndoorProb, finalCeilingProb, outdoorScore, mlLabels)
        }

        // 5. Temporal Consistency Validation
        recordState(SkyDecisionState.VALID_SKY)
        val validCount = temporalHistory.count { it == SkyDecisionState.VALID_SKY }
        val isTemporallyConsistent = isSingleCapture || validCount >= 2

        val finalState = if (isTemporallyConsistent) SkyDecisionState.VALID_SKY else SkyDecisionState.UNCERTAIN
        val confidence = (outdoorScore * 0.4f + quality.qualityScore * 0.3f + segmentation.skyCoverageFraction * 0.3f).coerceIn(0.60f, 0.98f)

        return buildResult(finalState, quality, segmentation, confidence, finalIndoorProb, finalCeilingProb, outdoorScore, mlLabels)
    }

    private fun recordState(state: SkyDecisionState) {
        synchronized(temporalHistory) {
            if (temporalHistory.size >= MAX_HISTORY) {
                temporalHistory.removeAt(0)
            }
            temporalHistory.add(state)
        }
    }

    /**
     * Fast downsampled Laplacian variance blur & exposure check
     */
    fun evaluateImageQuality(bitmap: Bitmap): SkyQualityMetrics {
        val w = 120
        val h = 120
        val scaled = if (bitmap.width == w && bitmap.height == h) bitmap else Bitmap.createScaledBitmap(bitmap, w, h, false)

        val totalPixels = w * h
        val lum = DoubleArray(totalPixels)
        var sumLum = 0.0
        var darkPixels = 0
        var brightPixels = 0
        var glarePixels = 0

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val p = scaled.getPixel(x, y)
                val r = (p shr 16) and 0xff
                val g = (p shr 8) and 0xff
                val b = p and 0xff
                val l = 0.299 * r + 0.587 * g + 0.114 * b
                lum[idx] = l
                sumLum += l

                if (l < 22) darkPixels++
                if (l > 242) brightPixels++
                // Glare: highly saturated white cluster
                if (r > 248 && g > 248 && b > 248) glarePixels++
            }
        }

        val meanLum = sumLum / totalPixels

        // Laplacian variance for sharpness
        var laplacianSum = 0.0
        var laplacianSqSum = 0.0
        var laplacianCount = 0

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                // 3x3 discrete Laplacian operator: center * 4 - (up + down + left + right)
                val center = lum[idx]
                val up = lum[(y - 1) * w + x]
                val down = lum[(y + 1) * w + x]
                val left = lum[y * w + (x - 1)]
                val right = lum[y * w + (x + 1)]
                val valLap = 4.0 * center - (up + down + left + right)

                laplacianSum += valLap
                laplacianSqSum += (valLap * valLap)
                laplacianCount++
            }
        }

        val meanLap = laplacianSum / laplacianCount.coerceAtLeast(1)
        val varianceLap = (laplacianSqSum / laplacianCount.coerceAtLeast(1)) - (meanLap * meanLap)

        val isTooDark = (darkPixels.toDouble() / totalPixels > 0.75) || (meanLum < 18.0)
        val isOverexposed = (brightPixels.toDouble() / totalPixels > 0.65) || (meanLum > 250.0)
        val glareFraction = glarePixels.toFloat() / totalPixels
        val isExcessiveGlare = glareFraction > 0.28f
        // Open sky can be naturally smooth (low texture), so we only reject as blurry if variance is extremely low
        val isBlurry = varianceLap < 5.5 && meanLum in 40.0..220.0

        val sharpnessScore = (varianceLap / 80.0).coerceIn(0.1, 1.0).toFloat()
        val exposureScore = (1.0 - abs(meanLum - 128.0) / 128.0).coerceIn(0.1, 1.0).toFloat()
        val qualityScore = ((sharpnessScore * 0.4f) + (exposureScore * 0.6f)).coerceIn(0.1f, 1.0f)

        return SkyQualityMetrics(
            blurScore = varianceLap,
            isBlurry = isBlurry,
            meanLuminance = meanLum,
            isTooDark = isTooDark,
            isOverexposed = isOverexposed,
            isExcessiveGlare = isExcessiveGlare,
            glareFraction = glareFraction,
            qualityScore = qualityScore
        )
    }

    /**
     * Google ML Kit Labeling on-device
     */
    private suspend fun runMlKitLabeling(bitmap: Bitmap): List<Pair<String, Float>> {
        return suspendCancellableCoroutine { cont ->
            try {
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                mlKitLabeler.process(inputImage)
                    .addOnSuccessListener { labels ->
                        val result = labels.map { it.text to it.confidence }
                        cont.resume(result)
                    }
                    .addOnFailureListener {
                        cont.resume(emptyList())
                    }
            } catch (e: Exception) {
                cont.resume(emptyList())
            }
        }
    }

    data class SceneScores(
        val indoor: Float,
        val ceiling: Float,
        val fabric: Float,
        val outdoor: Float
    )

    /**
     * Evaluates ML Kit labels into Indoor, Ceiling, Fabric, and Outdoor probabilities
     */
    private fun computeSceneScores(
        labels: List<Pair<String, Float>>,
        sensorData: SensorOrientationData?
    ): SceneScores {
        var indoorWeight = 0f
        var ceilingWeight = 0f
        var fabricWeight = 0f
        var outdoorWeight = 0f

        val ceilingKeywords = setOf(
            "Ceiling", "Ceiling fan", "Plaster", "Light fixture", "Lighting", "Chandelier", "Fan"
        )
        val fabricKeywords = setOf(
            "Bed", "Bedsheet", "Bedding", "Blanket", "Quilt", "Linen", "Textile", "Fabric", "Pillow",
            "Cushion", "Curtain", "Drape", "Cloth", "Towel", "Mattress", "Sheet"
        )
        val indoorKeywords = setOf(
            "Room", "Interior design", "Wall", "Floor", "Furniture", "Table", "Window blind",
            "Door", "Shelf", "Flooring", "Desk", "Home", "Tile", "Living room", "Bedroom",
            "Keyboard", "Computer", "Laptop", "Monitor", "Screen", "Display", "Hardware",
            "Electronic", "Electronics", "Peripheral", "Input device", "Output device",
            "Mouse", "Gadget", "Technology", "Office", "Tableware", "Bottle", "Cup", "Chair", "Television"
        )
        val outdoorKeywords = setOf(
            "Sky", "Cloud", "Atmosphere", "Horizon", "Daytime", "Cumulus", "Sunset", "Sunrise",
            "Blue", "Nature", "Tree", "Plant", "Mountain", "Landscape"
        )

        for ((label, conf) in labels) {
            if (ceilingKeywords.any { label.contains(it, ignoreCase = true) }) {
                ceilingWeight += conf * 1.5f
                indoorWeight += conf * 1.0f
            }
            if (fabricKeywords.any { label.contains(it, ignoreCase = true) }) {
                fabricWeight += conf * 1.6f
                indoorWeight += conf * 1.3f
            }
            if (indoorKeywords.any { label.contains(it, ignoreCase = true) }) {
                indoorWeight += conf * 1.4f
            }
            if (outdoorKeywords.any { label.contains(it, ignoreCase = true) }) {
                outdoorWeight += conf * 1.3f
            }
        }

        // Incorporate sensor pitch angle:
        // If pointing straight up (>50°) and ceiling keywords detected, ceiling probability is amplified
        if (sensorData != null) {
            if (sensorData.pitchDeg > 50f && ceilingWeight > 0.2f) {
                ceilingWeight += 0.35f
            }
            // Ceilings are physically overhead; if camera is pointing level or downward (< 25°), ceiling is 0
            if (sensorData.pitchDeg < 25f) {
                ceilingWeight = 0f
            }
            // If camera is pointing downward (< 10°), indoor objects, desks, floors, or fabrics dominate
            if (sensorData.pitchDeg < 10f) {
                outdoorWeight *= 0.25f
                indoorWeight += 0.40f
                if (fabricWeight > 0.15f) fabricWeight += 0.35f
            }
        }

        return SceneScores(
            indoor = indoorWeight.coerceIn(0f, 1f),
            ceiling = ceilingWeight.coerceIn(0f, 1f),
            fabric = fabricWeight.coerceIn(0f, 1f),
            outdoor = outdoorWeight.coerceIn(0f, 1f)
        )
    }

    /**
     * Analyzes image edges to detect indoor architectural grids, ceiling borders, and artificial light sources
     */
    private fun analyzeIndoorGeometry(bitmap: Bitmap): Triple<Int, Float, Boolean> {
        val w = 80
        val h = 80
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, false)

        var straightEdges = 0
        var totalEdges = 0
        var artificialLightCluster = false
        var highCenterLumCount = 0

        val lum = Array(w) { DoubleArray(h) }
        for (x in 0 until w) {
            for (y in 0 until h) {
                val p = scaled.getPixel(x, y)
                val r = (p shr 16) and 0xff
                val g = (p shr 8) and 0xff
                val b = p and 0xff
                lum[x][y] = 0.299 * r + 0.587 * g + 0.114 * b

                // Check for localized round or tube light fixtures (extremely high local luminance surrounded by darkness)
                if (r > 245 && g > 240 && b > 220) {
                    highCenterLumCount++
                }
            }
        }

        // Edge gradient
        for (x in 1 until w - 1) {
            for (y in 1 until h - 1) {
                val gx = lum[x + 1][y] - lum[x - 1][y]
                val gy = lum[x][y + 1] - lum[x][y - 1]
                val mag = sqrt(gx * gx + gy * gy)
                if (mag > 32.0) {
                    totalEdges++
                    // Pure vertical or horizontal edge lines (typical of room corners, ceiling lines, door frames)
                    if (abs(gx) > abs(gy) * 2.5 || abs(gy) > abs(gx) * 2.5) {
                        straightEdges++
                    }
                }
            }
        }

        if (highCenterLumCount in 10..180) {
            // Distinct small bright cluster like a ceiling bulb or tube light
            artificialLightCluster = true
        }

        val straightRatio = if (totalEdges > 0) straightEdges.toFloat() / totalEdges else 0f
        return Triple(straightEdges, straightRatio, artificialLightCluster)
    }

    /**
     * Pixel-level Sky Segmentation and Coverage
     */
    fun performSkySegmentation(bitmap: Bitmap): SkySegmentationResult {
        val sampleW = 100
        val sampleH = 100
        val scaled = Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, false)
        val total = sampleW * sampleH

        var skyPixels = 0
        var blueSkyPixels = 0
        var cloudPixels = 0
        var darkCloudPixels = 0
        var totalLum = 0.0
        val hsv = FloatArray(3)

        for (y in 0 until sampleH) {
            for (x in 0 until sampleW) {
                val p = scaled.getPixel(x, y)
                val r = (p shr 16) and 0xff
                val g = (p shr 8) and 0xff
                val b = p and 0xff
                val lum = 0.299 * r + 0.587 * g + 0.114 * b
                totalLum += lum

                AndroidColor.colorToHSV(p, hsv)
                val hue = hsv[0]
                val sat = hsv[1]

                // True outdoor natural blue sky:
                val isNaturalSkyBlue = hue in 185f..245f && sat >= 0.16f && (b > r + 15)

                // Natural cloud: achromatic with soft organic luminance
                val isCloudAchromatic = sat < 0.22f && lum in 80.0..245.0

                // Dark storm cloud: low luminance achromatic
                val isDarkStormCloud = sat < 0.25f && lum in 25.0..79.0

                if (isNaturalSkyBlue) {
                    skyPixels++
                    blueSkyPixels++
                } else if (isCloudAchromatic) {
                    skyPixels++
                    cloudPixels++
                } else if (isDarkStormCloud) {
                    skyPixels++
                    cloudPixels++
                    darkCloudPixels++
                }
            }
        }

        val skyCoverage = (skyPixels.toFloat() / total).coerceIn(0f, 1f)
        val cloudFraction = if (skyPixels > 0) (cloudPixels.toFloat() / skyPixels).coerceIn(0f, 1f) else 0f
        val blueRatio = if (skyPixels > 0) (blueSkyPixels.toFloat() / skyPixels).coerceIn(0f, 1f) else 0f
        val avgLum = totalLum / total

        val visualCloudCondition = when {
            darkCloudPixels > (skyPixels * 0.35f) -> "Dark Storm Cloud"
            cloudFraction >= 0.85f -> "Overcast"
            cloudFraction >= 0.50f -> "Mostly Cloudy"
            cloudFraction >= 0.20f -> "Partly Cloudy"
            else -> "Clear Sky"
        }

        val visualPrecipitationHint = darkCloudPixels > (skyPixels * 0.40f) || (avgLum < 65.0 && cloudFraction > 0.80f)
        val visualHazeHint = blueRatio > 0.30f && avgLum > 180.0 && cloudFraction in 0.1f..0.4f

        return SkySegmentationResult(
            skyCoverageFraction = skyCoverage,
            cloudFractionWithinSky = cloudFraction,
            visualCloudCondition = visualCloudCondition,
            visualPrecipitationHint = visualPrecipitationHint,
            visualHazeHint = visualHazeHint,
            averageLuminance = avgLum,
            blueSkyRatio = blueRatio
        )
    }

    private fun buildFallbackResult(
        state: SkyDecisionState,
        quality: SkyQualityMetrics,
        confidence: Float,
        indoorProb: Float = 0f,
        ceilingProb: Float = 0f,
        labels: List<String> = emptyList()
    ): SkyEvaluationResult {
        val emptySeg = SkySegmentationResult(
            skyCoverageFraction = 0f,
            cloudFractionWithinSky = 0f,
            visualCloudCondition = "Undetermined",
            visualPrecipitationHint = false,
            visualHazeHint = false,
            averageLuminance = quality.meanLuminance,
            blueSkyRatio = 0f
        )
        return SkyEvaluationResult(
            state = state,
            confidence = confidence,
            quality = quality,
            segmentation = emptySeg,
            indoorProbability = indoorProb,
            ceilingProbability = ceilingProb,
            outdoorProbability = 0f,
            detectedMlLabels = labels
        )
    }

    private fun buildResult(
        state: SkyDecisionState,
        quality: SkyQualityMetrics,
        segmentation: SkySegmentationResult,
        confidence: Float,
        indoorProb: Float,
        ceilingProb: Float,
        outdoorProb: Float,
        mlLabels: List<Pair<String, Float>>
    ): SkyEvaluationResult {
        return SkyEvaluationResult(
            state = state,
            confidence = confidence,
            quality = quality,
            segmentation = segmentation,
            indoorProbability = indoorProb,
            ceilingProbability = ceilingProb,
            outdoorProbability = outdoorProb,
            detectedMlLabels = mlLabels.map { "${it.first} (${(it.second * 100).toInt()}%)" }
        )
    }
}
