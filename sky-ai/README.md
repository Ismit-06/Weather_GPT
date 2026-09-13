# WeatherGPT Sky AI Inference Service

## Overview
WeatherGPT Sky AI is an enterprise-grade visual perception microservice powered by **Google Gemma 4 26B A4B Instruct** fine-tuned via LoRA (**SKY-LORA-002**). The service determines whether a camera image contains genuine outdoor sky while rejecting indoor false-positive confounders such as blue bedsheets, fabric folds, blankets, window reflections, ceilings, and photographs.

## Architecture
- FastAPI + Uvicorn async serving
- Pydantic v2 strict schemas with automated null-normalization
- Base Model: google/gemma-4-26B-A4B-it
- Adapter: checkpoints/SKY-LORA-002
- Precision: bloat16 / 4-bit QLoRA
- Serving Fallback: Resilient fallback engine with optical crease/frequency analysis and OpenRouter backend support.

## Endpoints
- GET /health: Liveness probe
- GET /ready: Readiness probe
- POST /api/sky/analyze: Multipart image analysis (JPEG, PNG, WEBP)

## Execution
- Local: uvicorn service.main:app --host 0.0.0.0 --port 8000
- Docker: docker-compose up --build -t
- Tests: python -m pytest tests/ -v
