# WEATHERGPT SYSTEM ARCHITECTURE: END-TO-END FUSION

```
                               ┌─────────────────────────┐
                               │  Android Client (Kotlin)│
                               │     CameraX Preview     │
                               └────────────┬────────────┘
                                            │
                                            ▼
                        ┌───────────────────────────────────────┐
                        │   Quality & Blur Filter (On-Device)   │
                        │   Laplacian Var & Luminance Pre-Check │
                        └───────────────────┬───────────────────┘
                                            │
                                            ▼
                        ┌───────────────────────────────────────┐
                        │   Sky AI Inference Service (FastAPI)  │
                        │   Model: Gemma 4 26B A4B + LoRA-002   │
                        │   Target Token Budget: 384 Visual     │
                        └───────────────────┬───────────────────┘
                                            │
                                            ▼
                        ┌───────────────────────────────────────┐
                        │    Defensive Sky Confidence Gate      │
                        │    sky_detected == true & conf >= 0.80│
                        └───────────────────┬───────────────────┘
                                            │
                                            ▼
                                Visual Observation (JSON)
                                            │
┌───────────────────────────┐               │               ┌───────────────────────────┐
│ Current Weather Station   │───────────────┼──────────────→│ RainViewer Radar Echoes   │
│ MET Norway / Open-Meteo   │               │               │ Precipitation Reflectivity│
└───────────────────────────┘               │               └───────────────────────────┘
              │                             │                             │
              ▼                             ▼                             ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                      WeatherObservationContext (Contract v1.0)                         │
│   Location | Camera Observation | Weather Station | Radar Echoes | Forecast Telemetry  │
└───────────────────────────────────────────┬────────────────────────────────────────────┘
                                            │
                                            ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                        WeatherGPT Intelligent Fusion Layer                             │
│   - Enforces Explicit Source Ownership Matrix                                          │
│   - Detects Local Micro-Climate Contradictions (e.g., Camera overcast vs Station clear)│
│   - Evaluates Radar Hydrometeor Echoes vs Visual Droplet Visibility                    │
│   - Strips PII / Zero Raw Image Persistence to External LLM Providers                  │
│   - Communicates Honest Uncertainty when Data is Missing or Degraded                   │
└───────────────────────────────────────────┬────────────────────────────────────────────┘
                                            │
                                            ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                           Final Grounded User Experience                               │
│        Rich Formatted Explanations & Voice-Safe Synthesized Text (Android UI)          │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Component Roles & Boundaries

1. **Android Client (`Weather_GPT/android`)**:
   - Manages CameraX lifecycle with `STRATEGY_KEEP_ONLY_LATEST`.
   - Protects against stale frame responses using `requestSequenceCounter`.
   - Renders explainable source badges (`Camera observation`, `Current weather`, `Radar`, `Forecast`).

2. **Sky AI Production Service (`sky-ai/service`)**:
   - Executes multimodal visual inference using Google Gemma 4 26B A4B + `SKY-LORA-002`.
   - Strictly enforces Pydantic semantic normalization (no cloud details if `sky_detected=false`).
   - Serves `WeatherObservationContext` endpoints `/api/sky/fuse` and mock scenario injection `/api/sky/demo/scenarios`.

3. **WeatherGPT Backend Agent (`Weather_GPT/app`)**:
   - Ingests structured visual observations alongside MET Norway telemetry and RainViewer radar metadata.
   - Grounded LLM answering (OpenRouter / Sarvam / Deterministic Fallback) that explicitly differentiates visual facts from station reports.
