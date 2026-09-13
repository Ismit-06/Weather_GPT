# WeatherGPT Sky AI — Operations & Site Reliability Runbook

## 1. System Overview
The **WeatherGPT Sky AI** service is a containerized FastAPI microservice running the **Google Gemma 4 26B A4B Instruct** multimodal model with the **`SKY-LORA-002`** adapter. It provides vision-based atmospheric classification, hard-negative rejection (preventing bedsheets, ceilings, fabrics from being misclassified as sky), and multi-source meteorological fusion.

---

## 2. Health & Readiness Verification

### Endpoints
- **Liveness Probe**: `GET /health` $\to$ Returns HTTP 200 with `{ "status": "ok", "model_version": "SKY-LORA-002" }`.
- **Readiness Probe**: `GET /ready` $\to$ Returns HTTP 200 with `{ "ready": true }` when model is loaded. Returns HTTP 503 if unloaded.
- **Telemetry & Metrics**: `GET /api/sky/metrics` $\to$ Returns QPS, P50/P90/P95/P99 latencies, error counts, and active concurrency.

### Probe Healthcheck Command (Container)
```bash
curl -f http://localhost:8000/ready || exit 1
```

---

## 3. Incident Procedures & Triage Playbooks

### Incident 1: High Latency (> 3.5s warm request)
1. **Diagnosis**: Inspect `/api/sky/metrics` to check `active_concurrency` and P95 latency.
2. **Mitigation**:
   - Check if GPU VRAM memory thrashing or CPU image decoding bottlenecks are occurring.
   - Scale Uvicorn worker processes or spin up an additional replica behind the reverse proxy.
   - If local host load is excessive, enable remote inference backend via `OPENROUTER_API_KEY`.

### Incident 2: False-Sky Anomaly / Regression Detected
1. **Diagnosis**: Check if non-sky indoor images are passing confidence gate $\ge 0.80$.
2. **Immediate Rollback**:
   Trigger atomic adapter rollback to previously verified `SKY-LORA-001` without restarting container:
   ```bash
   curl -X POST "http://localhost:8000/api/sky/admin/rollback?target_version=SKY-LORA-001"
   ```
3. **Verify**: Run `pytest tests/test_known_failures.py` to confirm bedsheet rejection.

### Incident 3: Network Outage or Weather API Down
1. **Expected Behavior**: The WeatherGPT Fusion Engine (`service/fusion.py`) automatically enters graceful degradation mode:
   - Visual sky classification continues to operate.
   - User is informed that numerical weather station telemetry is temporarily unavailable.
   - No numerical temperatures or pressures are hallucinated.

### Incident 4: Android Rate Limiting Spike (HTTP 429)
1. **Diagnosis**: Client is sending frames faster than 60 req/min.
2. **Mitigation**: Verify client `SKY_AI_ANALYSIS_INTERVAL_MS` is pinned to $\ge 1500\text{ ms}$ and `requestSequenceCounter` cancels prior pending requests before dispatching new frames.

---

## 4. Disaster Recovery & Service Restart

### Restarting the Microservice
```bash
# Docker Compose
docker compose restart sky-service

# Standalone Uvicorn
pkill -f "uvicorn service.main:app"
uvicorn service.main:app --host 0.0.0.0 --port 8000 --workers 2
```

### Disabling Sky AI Feature Dynamically (Kill-Switch)
If an unresolvable defect occurs in production, disable Sky AI on Android without a new APK:
1. Update remote config or feature flag: `sky_ai_enabled=false`.
2. The Android application cleanly hides the camera analysis overlay and reverts to standard text/voice weather chat.
