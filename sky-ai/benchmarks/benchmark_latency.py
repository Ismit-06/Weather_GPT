import time
import io
import json
import statistics
from PIL import Image
from fastapi.testclient import TestClient
from service.main import app
from service.model_loader import model_manager

def run_latency_benchmark():
    print('=== WEATHERGPT SKY AI LATENCY BENCHMARK ===')
    start_load = time.perf_counter()
    model_manager.load()
    cold_start_duration = (time.perf_counter() - start_load) * 1000
    print(f'Cold start model initialization: {cold_start_duration:.2f} ms')

    client = TestClient(app)

    img = Image.new('RGB', (512, 512), color=(100, 180, 255))
    buf = io.BytesIO()
    img.save(buf, format='JPEG', quality=85)
    img_bytes = buf.getvalue()

    latencies = []
    num_requests = 20

    print(f'Executing {num_requests} warm inference requests...')
    for i in range(num_requests):
        t0 = time.perf_counter()
        res = client.post(
            '/api/sky/analyze',
            files={'image': (f'bench_{i}.jpg', io.BytesIO(img_bytes), 'image/jpeg')}
        )
        t1 = time.perf_counter()
        assert res.status_code == 200
        latencies.append((t1 - t0) * 1000)

    p50 = statistics.median(latencies)
    p90 = sorted(latencies)[int(0.90 * len(latencies))]
    p95 = sorted(latencies)[int(0.95 * len(latencies))]
    p99 = sorted(latencies)[-1]
    mean_lat = statistics.mean(latencies)

    results = {
        'cold_start_ms': round(cold_start_duration, 2),
        'total_requests': num_requests,
        'mean_latency_ms': round(mean_lat, 2),
        'p50_latency_ms': round(p50, 2),
        'p90_latency_ms': round(p90, 2),
        'p95_latency_ms': round(p95, 2),
        'p99_latency_ms': round(p99, 2),
    }

    print(json.dumps(results, indent=2))
    with open('benchmarks/latency_results.json', 'w') as f:
        json.dump(results, f, indent=2)

if __name__ == '__main__':
    run_latency_benchmark()
