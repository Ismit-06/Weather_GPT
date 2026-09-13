import time
import uuid
import logging
from collections import defaultdict
from threading import Lock
from contextlib import asynccontextmanager
from fastapi import FastAPI, UploadFile, File, Request, status
from fastapi.responses import JSONResponse
from fastapi.middleware.cors import CORSMiddleware

from service.config import settings
from service.logging_config import setup_logging
from service.model_loader import model_manager
from service.preprocessing import preprocess_image
from service.inference import run_inference
from service.schemas import SkyAnalysisResponse, HealthResponse, ReadyResponse, ErrorResponse
from service.errors import ServiceException, RateLimitException, ServerOverloadException


setup_logging(settings.log_level)
logger = logging.getLogger("sky_service")

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup: Load model and processor ONCE
    logger.info("Starting WeatherGPT Sky AI Production Service...")
    model_manager.load()
    yield
    # Shutdown
    logger.info("Shutting down Sky AI Production Service...")

app = FastAPI(
    title="WeatherGPT Sky AI Inference Service",
    version=settings.model_version,
    description="Production-grade vision-only atmospheric perception & hard-negative rejection API.",
    lifespan=lifespan
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.exception_handler(ServiceException)
async def service_exception_handler(request: Request, exc: ServiceException):
    req_id = getattr(request.state, "request_id", str(uuid.uuid4()))
    logger.warning(f"Request {req_id} rejected with {exc.code}: {exc.message}")
    return JSONResponse(
        status_code=exc.status_code,
        content={"request_id": req_id, "error": {"code": exc.code, "message": exc.message}}
    )

@app.exception_handler(Exception)
async def generic_exception_handler(request: Request, exc: Exception):
    req_id = getattr(request.state, "request_id", str(uuid.uuid4()))
    logger.error(f"Request {req_id} internal error: {exc}", exc_info=False)
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content={"request_id": req_id, "error": {"code": "INTERNAL_ERROR", "message": "An internal server error occurred."}}
    )

@app.get("/health", response_model=HealthResponse)
async def health():
    return HealthResponse(
        status="ok",
        model_loaded=model_manager.is_loaded,
        model_version=settings.model_version
    )

# Production In-Memory Rate Limiter & Concurrency Limiter
request_timestamps = defaultdict(list)
rate_limit_lock = Lock()
active_requests = 0
active_requests_lock = Lock()

# Production Metrics Store
metrics = {
    "total_requests": 0,
    "successful_requests": 0,
    "failed_requests": 0,
    "rate_limited_requests": 0,
    "sky_detected_count": 0,
    "not_sky_count": 0,
    "low_confidence_count": 0,
    "total_latency_ms": 0.0,
    "latencies_ms": [],
}
metrics_lock = Lock()

def enforce_rate_limit(client_id: str):
    now = time.time()
    with rate_limit_lock:
        timestamps = request_timestamps[client_id]
        # Keep only timestamps within last 60 seconds
        valid_timestamps = [t for t in timestamps if now - t < 60.0]
        request_timestamps[client_id] = valid_timestamps
        if len(valid_timestamps) >= settings.rate_limit_per_minute:
            with metrics_lock:
                metrics["rate_limited_requests"] += 1
            raise RateLimitException(f"Rate limit of {settings.rate_limit_per_minute} requests/minute exceeded.")
        request_timestamps[client_id].append(now)

@app.get("/ready", response_model=ReadyResponse)
async def ready():
    if not model_manager.is_loaded:
        return JSONResponse(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            content={"ready": False, "detail": "Model engine not loaded"}
        )
    return ReadyResponse(ready=True)


@app.get("/api/sky/metrics")
async def get_metrics():
    with metrics_lock:
        n = len(metrics["latencies_ms"])
        sorted_lats = sorted(metrics["latencies_ms"])
        p50 = sorted_lats[int(n * 0.50)] if n else 0.0
        p90 = sorted_lats[int(n * 0.90)] if n else 0.0
        p95 = sorted_lats[int(n * 0.95)] if n else 0.0
        p99 = sorted_lats[int(n * 0.99)] if n else 0.0

        sky_rate = (metrics["sky_detected_count"] / metrics["total_requests"]) if metrics["total_requests"] else 0.0
        return {
            "service_version": settings.model_version,
            "environment": settings.environment,
            "total_requests": metrics["total_requests"],
            "successful_requests": metrics["successful_requests"],
            "failed_requests": metrics["failed_requests"],
            "rate_limited_requests": metrics["rate_limited_requests"],
            "sky_detected_rate": round(sky_rate, 4),
            "latency_p50_ms": round(p50, 2),
            "latency_p90_ms": round(p90, 2),
            "latency_p95_ms": round(p95, 2),
            "latency_p99_ms": round(p99, 2),
            "active_concurrency": active_requests,
        }

@app.post("/api/sky/admin/rollback")
async def rollback_adapter(target_version: str = "SKY-LORA-001"):
    """
    Rollback model adapter to a previously validated checkpoint without retraining.
    """
    import os
    allowed_versions = ["SKY-LORA-001", "SKY-LORA-002", "BASE"]
    if target_version not in allowed_versions:
        raise ServiceException(
            status_code=status.HTTP_400_BAD_REQUEST,
            code="INVALID_ROLLBACK_TARGET",
            message=f"Target {target_version} is not in allowed validated rollback checkpoints: {allowed_versions}"
        )
    old_version = settings.model_version
    settings.model_version = target_version
    settings.adapter_path = f"checkpoints/{target_version}" if target_version != "BASE" else ""
    return {
        "status": "success",
        "previous_version": old_version,
        "active_version": settings.model_version,
        "adapter_path": settings.adapter_path,
        "message": f"Successfully switched active adapter to {target_version}."
    }

@app.post("/api/sky/analyze", response_model=SkyAnalysisResponse)
async def analyze_sky(request: Request, image: UploadFile = File(...)):
    global active_requests
    client_ip = request.client.host if request.client else "unknown"
    enforce_rate_limit(client_ip)

    # Concurrency limit check
    with active_requests_lock:
        if active_requests >= settings.max_concurrent_requests:
            raise ServerOverloadException()
        active_requests += 1

    req_id = str(uuid.uuid4())
    request.state.request_id = req_id
    start_time = time.time()

    with metrics_lock:
        metrics["total_requests"] += 1

    try:
        logger.info(f"Received image analysis request: {image.filename} (content_type={image.content_type})", extra={"request_id": req_id})

        # Read image bytes
        raw_bytes = await image.read()

        # Preprocess
        preprocessed_image = preprocess_image(raw_bytes)
        preprocess_time = time.time() - start_time

        # Run Inference
        inference_start = time.time()
        result = run_inference(preprocessed_image, req_id)
        inference_time = time.time() - inference_start
        total_time = time.time() - start_time

        latency_ms = round(total_time * 1000, 2)
        with metrics_lock:
            metrics["successful_requests"] += 1
            metrics["total_latency_ms"] += latency_ms
            metrics["latencies_ms"].append(latency_ms)
            if len(metrics["latencies_ms"]) > 1000:
                metrics["latencies_ms"].pop(0)

            if result.sky_detected:
                metrics["sky_detected_count"] += 1
            else:
                metrics["not_sky_count"] += 1

            if result.sky_confidence < settings.sky_confidence_threshold:
                metrics["low_confidence_count"] += 1

        logger.info(
            f"Analysis complete: sky_detected={result.sky_detected} (scene={result.scene_type}, conf={result.sky_confidence}) in {latency_ms}ms",
            extra={
                "request_id": req_id,
                "latency_ms": latency_ms,
                "model_version": settings.model_version,
                "sky_detected": result.sky_detected,
                "confidence": result.sky_confidence
            }
        )

        return result

    except Exception:
        with metrics_lock:
            metrics["failed_requests"] += 1
        raise

    finally:
        with active_requests_lock:
            active_requests = max(0, active_requests - 1)

@app.post("/api/sky/fuse")
async def fuse_observation(payload: dict):
    from service.fusion import WeatherObservationContext, WeatherGptFusionEngine
    ctx = WeatherObservationContext(**payload.get("context", payload))
    question = payload.get("question")
    explanation = WeatherGptFusionEngine.fuse(ctx, question=question)
    return explanation.model_dump()

@app.get("/api/sky/demo/scenarios")
async def get_demo_scenarios():
    if settings.environment == "production":
        raise ServiceException(
            status_code=status.HTTP_403_FORBIDDEN,
            code="ENDPOINT_DISABLED",
            message="Debug and demo endpoints are disabled in production environment."
        )
    return {
        "scenarios": [
            "1_clear_agreement",
            "2_overcast_agreement",
            "3_overcast_cam_clear_api_contradiction",
            "4_bedsheet_invalid_sky",
            "5_radar_rain_cam_no_rain",
            "6_rain_agreement",
            "7_valid_sky_api_down",
            "8_invalid_sky_api_down"
        ]
    }



