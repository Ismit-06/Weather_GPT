import json
import tracemalloc
from service.model_loader import model_manager

def run_memory_benchmark():
    print('=== WEATHERGPT SKY AI MEMORY BENCHMARK ===')
    tracemalloc.start()
    
    base_current, base_peak = tracemalloc.get_traced_memory()
    baseline_vram_mb = 0.0
    torch_available = False
    try:
        import torch
        torch_available = True
        if torch.cuda.is_available():
            baseline_vram_mb = torch.cuda.memory_allocated() / (1024 * 1024)
    except Exception:
        pass

    model_manager.load()

    loaded_current, loaded_peak = tracemalloc.get_traced_memory()
    loaded_vram_mb = 0.0
    if torch_available:
        try:
            import torch
            if torch.cuda.is_available():
                loaded_vram_mb = torch.cuda.memory_allocated() / (1024 * 1024)
        except Exception:
            pass

    results = {
        'baseline_traced_mb': round(base_current / (1024 * 1024), 2),
        'loaded_traced_mb': round(loaded_current / (1024 * 1024), 2),
        'peak_traced_mb': round(loaded_peak / (1024 * 1024), 2),
        'baseline_vram_mb': round(baseline_vram_mb, 2),
        'loaded_vram_mb': round(loaded_vram_mb, 2),
        'delta_vram_mb': round(loaded_vram_mb - baseline_vram_mb, 2),
        'is_remote_fallback': model_manager.is_remote_fallback,
        'torch_available': torch_available
    }

    print(json.dumps(results, indent=2))
    with open('benchmarks/memory_results.json', 'w') as f:
        json.dump(results, f, indent=2)

if __name__ == '__main__':
    run_memory_benchmark()
