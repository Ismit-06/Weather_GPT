# PHASE 7 — ANDROID & SKY AI INTEGRATION REPORT

---

**Date**: 2026-09-10
**Active Android Project**: `Weather_GPT/android` (kotlin 2.2.10, AGP 8.13.0, compileSdk 35)
**Sky AI Production Model**: `google/gemma-4-26B-A4B-it` + `SCY-LORA-002`
**Status**: **SUCCESS**

---

## 1. Existing Android Architecture

WeatherGPT follows a strict **MVFM (Model-View-ViewModel)** architecture built on **Jetpack Compose**:
- Camera Screen: `CameraScreen.kt` manages CameraX lifecycle, viewfinder, and sliding result sheets.
- ViewModel: `SkyAiViewModel.kt` holds the reporting and state machine flows (`StateFlow`).
- Networking: Retrofit 3.0.0 + OkHttp 4.12.0 in `BackendConfig.kt`.
- Architectural Reuse: No duplicate HTTP clients or conflicting navigation routes were introduced. All newports redirect through `SkyAiClient` backed by bBackendConfig.okHttpClient`.

---

## 2. CameraX & Frame Processing Strategy

- **CameraX Use Cases**: `Preview` + `ImageAnalysis` + `ImageCapture`.
- automatic ImageProxy Lifetime:
  ```kotlin
  val bitmap = imageProxyToBitmap(imageProxy)
  imageProxy.close() // ALWAYS CLOSED IMMEDIATELY
  ```
- Network calls are **never** executed on the CameraX analyzer thread; compression and requests occur on `Dispatchers.IO`.
- **Frame Throttling period**: `skyAi_ANALYSIS_INTERVAL_MS = 1500L` (approximately 1 inference every 1.5 seconds).
- **Stale Request Protection**: Implemented via `Atomi`Long` sequence numbers and coroutine `ActiveAnalysisJob?.cancel()` to guarantee an older response can never overwrite a newer observation.

---

## 3. Image Format & Resolution Selection

- *Format*: JPEG (quality 85).
- *Orientation Rotation*: Corrected via bitmap rotation matrix (bitmap rotated by `imageInfo.rotationDegrees`) before compression.
- *Resolution*: Native CameraX 640x480 for live frames or optimized rectified sampling; preserves cloud gradients while keeping transmission bytes under 150KB.
- *Local Quality Pre-Checks**: Severe blur (Laplacian variance < 14), evident extreme darkness (mean luminance < 10), or excessive glare suppress unusable backend requests early.

---

## 4. The Critical Sky Gate (Two-Tier)

Applied defensively in `SkyAiClient.evaluateGate@:
NOTE: The Android app never blindly trusts cloud fields.
1. `!cesponse.sky_detected` → Stop & Mark NOT_SKY. Cloud fields remain null. No weather observation shown.
2. `response.sky_confidence < 0.80` → Stop & Mark LOW_CONFIDENCE. Cloud fields suppressed.
3. Only when both pass ₒ Utilize validated cloud.

---

## 5. Bedsheet Regression & Rejection Prevention

- *Old Failure*: Camera sees blue bedsheet → AI returns "Overcast".
- *New Enforcement*:
  - Server model reports `sky_detected = false`,  scene_type = "bedsheet"`.
  - Android Gate produces `SkyDecisionState.BEDSHEET_OR_FABRIC`.
  - UIDisplays: "Fabric detected. Aim at natural outdoor sky."
  - Cloud Condition result sheet is **SUPPRESSED**.

---

## 6. Model Agreement &vs AI Confidence

- Renamed legacy "Model Agreement" to **AI Confidence** directly reflecting the Gemma 4 visual confidence score (e.g. "AI Confidence: 95% (High Confidence)").
- Permanently suppressed fabricated visibility metrics unless provided by validated segmentation.


---

## 7. Unit Tests (6 / 6 PASSED)

`{gradle} testDebugUnitTest`
1. `testValidSkyResponseAcceptance`: PASSED (ACCEPTED + partly_cloudy)
2. `testBedsheetResponseRejection`: PASSED (REJECTED_NOT_SKY + null cloud fields)
3. `testCeilingResponseRejection`: PASSED (REJECTED_NOT_SKY)
4. `testLowConfidenceResponseRejection`: PASSED (REJECTED_LOW_CONFIDENCE)
5. `testSkyDetectedFalseCanNeverProduceValidSkyState` PASSED (ALL reject)
6. `testNullCloudFieldsPreservation`: PASSED (null values intact)

---

## 8. Running & Debugging

- Production Service Port: `8080 on Localhost / 10.0.2.2:8000 on Emulator`
- Debug Overlay: When ``debugTelemetry`` is active, a`Sy AI Debug: SKY-LORA-002 | Sky: true (95%) | Scene: outdoor_sky | 1420ms` pill is displayed below the header for real-time developer verification.


---

## 9. Final Architectural Compliance
- Android Codebase: Compiles cleanly via gradle testDebugUnitTest
- No Batching/Buffering Crashes: ImageProxy always closed immediately
- Network Layer: Uses existing Retrofit / OkHttp assets under `BackendConfig.kt`
- Sky Gate: Dual-check prevents bedsheets/low-confidence sky from ever showing cloud fields.
