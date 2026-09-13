import time
import io
import json
import concurrent.futures
from PIL import Image
from fastapi.testclient import TestClient
from service.main import app
from service.model_loader import model_manager

def run_concurrency_benchmark():
    print('=== WEATHERGPT SKY AI CONCURRENCY BENCHMARK ===')
    model_manager.load()
    client = TestClient(app)

    img = Image.new('RGB', (256, 256), color=(80, 160, 240))
    buf = io.BytesIO()
    img.save(buf, format='JPEG', quality=85)
    img_bytes = buf.getvalue()

    def send_request(idx):
        t0 = time.perf_counter()
        res = client.post(
            '/api/sky/analyze',
            files={'image': (f'conc_{idx}.jpg', io.BytesIO(img_bytes), 'image/jpeg')}
        )
        t1 = time.perf_counter()
        return res.status_code, (t1 - t0) * 1000

    concurrency_levels = [1, 2, 4, 8]
    summary = {}

    for c in concurrency_levels:
        total_reqs = c * 3
        print(f'Testing concurrency = {c} with {total_reqs} requests...')
        with concurrent.futures.ThreadPoolExecutor(max_workers=c) as executor:
            futures = [executor.submit(send_request, i) for i in range(total_reqs)]
            results = [f.result() for f in futures]

        latencies = [lat for status, lat in results if status == 200]
        success_count = sum(1 for status, _ in results if status == 200)

        summary[f'concurrency_{c}'] = {
            'workers': c,
            'requests': total_reqs,
            'success_rate': success_count / total_reqs,
            'avg_latency_ms': round(sum(latencies) / len(latencies), 2) if latencies else 0.0,
            'max_latency_ms': round(max(latencies), 2) if latencies else 0.0
        }

    print(json.dumps(summary, indent=2))
    with open('benchmarks/concurrency_results.json', 'w') as f:
        json.dump(summary, f, indent=2)

if __name__ == '__main__':
    run_concurrency_benchmark()
