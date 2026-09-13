# PHASE 8 — BASELINE PRODUCTION STATE REPORT

---

**Date**: 2026-09-10
**Phase**: Phase 8 – Final Hardening & Production Readiness
**Active Production Model**: `SKY-LORA-002` (over `SKY-LORA-001` & BASEE)
**Base Architecture**: `google/gemma-4-26B-A4B-it`
---

## 1. Baseline Configuration Parameters

- **Selected Model**: google/gemma-4-26B-A4B-it
- **Adapter Version**: `SKY-LORA-002` (LoRA r = 32, alpha = 64)
- **Confidence Threshold**: `0.80` (80%)
- **Image Token Budget**: 384 visual tokens (for high-frequency micro-texture regularity)
- **API Latency (Warm)**:
  - P50: ~1140 cold-start to 1140 ms
  - P95: ~1406 ms
- **Android Frame Interval**: `1500 ms` (`SKY_AI_ANALYSIS_INTERVAL_MS = 1500L`)
- quick frame local pre-checks: Blur (Laplacian < 14), extreme darkness (lum < 10), glare
- **Image Resolution**: 640x480 native CameraX auto-frame
- **JPEG Compression Quality**: 85%
- **Devices Tested**: Physical Android Hardware (MOTO / ZA222MF6J7) + ANDROID EMULATOR, ADB suite
- **Host Server Hardware**: NVIDIA Geforce RTX 2050 (4GB VRAM), 7.65 GB Ram
- **Remaining Known Failures to Harden**:
  1. Complex indoor patterned surfaces under natural sunlight.
  2. Window glass with both indoor reflection and outdoor sky visible.
  3. Diffuse white walls with blue artificial lighting.

---

## 2. Enforcement & Hardening Plan
- Regression test all 10 variants of the original bedsheet failure.
- Stress-test hard-negatives (ceiling, wall, fabric, windows, screens, photos).
- Verify real sky in various cloud conditions (sunrise, sunset, overcast, storm, partial).
- Test temporal stability and stale-request protection.
- Measure memory, thermal, and concurrency limits.
- Finalize model manifest and production-lock.yaml.
