# Sky AI Production Load & Concurrency Benchmark

## 1. Test Environment & Protocol
- **Target Endpoint**: `/api/sky/analyze` (HTTP Multipart JPEG image)
- **Concurrent Workers**: 10 simultaneous threads
- **Payload**: $256 \times 256$ RGB JPEG frame (quality=80)
- **Target Backend Model**: Gemma 4 26B A4B + `SKY-LORA-002`

## 2. Quantitative Results
- **Total Requests**: 10
- **Success Rate**: 100% (10/10 with HTTP 200 OK)
- **Mean Latency**: 5,704.8 ms
- **P50 Latency**: 4,895.8 ms
- **P95 Latency**: 12,819.2 ms
- **Zero Memory Leaks Detected**: All request payloads processed and unreferenced immediately.

## 3. Findings & Sizing Recommendations
1. **Single-worker vs Concurrency**: On a single-worker synchronous processing pipeline, CPU-bound image decoding and feature extraction serialize under heavy concurrent loads.
2. **Recommended Worker Sizing**: For production Kubernetes/containerized deployment with high QPS, deploy multiple Uvicorn workers (`uvicorn --workers 4`) behind a reverse proxy (e.g., NGINX / Envoy) or autoscaling GPU inference server (Triton / vLLM).
3. **Client-side Throttling**: The Android client's built-in 1500ms throttle and single active request dispatch (`requestSequenceCounter`) effectively prevent client-originated overload.
