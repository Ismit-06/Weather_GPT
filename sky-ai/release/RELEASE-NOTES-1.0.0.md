# WeatherGPT Sky AI Release Notes — v1.0.0

## Release Summary
- **Release Version**: `1.0.0`
- **Release Date**: 2026-09-10
- **Base Architecture**: Google Gemma 4 26B A4B Instruct (`google/gemma-4-26B-A4B-it`)
- **Active Adapter**: `SKY-LORA-002` (LoRA $r=16, \alpha=32$)
- **API Contract**: `sky-observation-v1`
- **Fusion Contract**: `fusion-v1`

---

## What's New in v1.0.0
1. **Multimodal Sky Perception**:
   - Classifies camera imagery into genuine outdoor sky vs non-sky indoor surfaces (bedsheets, blankets, ceilings, walls).
   - Extracts observed cloud conditions (`clear`, `partly_cloudy`, `mostly_cloudy`, `overcast`, `storm_clouds`), coverage fraction, and visible precipitation.
2. **Defensive Confidence Gate**:
   - Hard threshold at $\ge 0.80$ confidence. Non-sky surfaces (including known bedsheet failure cases) reject cleanly with confidence $\le 0.02$.
3. **Multi-Source Intelligent Fusion**:
   - Synthesizes camera visual facts with MET Norway station telemetry, RainViewer Doppler radar reflectivity, and forecasts.
   - Detects localized micro-climate variations (e.g. camera overcast vs station clear) transparently.
4. **Production Hardening**:
   - In-memory rate limiting (60 req/min per client IP) and concurrency capping (10 active requests).
   - Zero raw camera image persistence on disk or external LLMs.
   - Dynamic zero-downtime model rollback endpoint (`/api/sky/admin/rollback`).
   - Real-time telemetry monitoring (`/api/sky/metrics`).

---

## Known Limitations & Boundaries
- **Visual Model Scope**: The camera AI strictly determines visual atmospheric observations. It does **not** measure temperature ($^\circ\text{C}$), humidity, air pressure, or wind velocity.
- **Optical Fallback Engine**: Under local fallback heuristic without GPU acceleration, pure synthetic monochromatic blue swatches without texture may exhibit false-positive tendencies.
- **Screens & Reflections**: Digital screens displaying sky photos and reflective window panes require future fine-tuning iterations (`SKY-LORA-003`).

---

## Supported Devices & Ecosystem
- **Android Client**: Compatible with Android 8.0+ (API level 26 to 35). Requires Camera2/CameraX compatible hardware.
- **Host Inference Server**: Linux / Windows container with Python 3.12, CUDA 12+, $\ge 14\text{GB}$ VRAM for full GPU inference, or fallback API integration.
