import sys
import os
import platform
import shutil
import psutil

print("==================================================")
print(" WeatherGPT Sky AI - Environment & Hardware Audit")
print("==================================================")

# OS & Python
print(f"OS Platform          : {platform.system()} {platform.release()} ({platform.version()})")
print(f"Python Version       : {sys.version.split()[0]}")

# System RAM
vm = psutil.virtual_memory()
print(f"Total System RAM     : {vm.total / (1024**3):.2f} GB")
print(f"Available System RAM : {vm.available / (1024**3):.2f} GB")

# Disk Space
disk = shutil.disk_usage(os.getcwd())
print(f"Disk Total           : {disk.total / (1024**3):.2f} GB")
print(f"Disk Free            : {disk.free / (1024**3):.2f} GB")

# Check PyTorch & CUDA
try:
    import torch
    print(f"PyTorch Version      : {torch.__version__}")
    cuda_avail = torch.cuda.is_available()
    print(f"CUDA Available       : {cuda_avail}")
    if cuda_avail:
        print(f"CUDA Version         : {torch.version.cuda}")
        device_count = torch.cuda.device_count()
        print(f"GPU Count            : {device_count}")
        for i in range(device_count):
            name = torch.cuda.get_device_name(i)
            vram = torch.cuda.get_device_properties(i).total_memory / (1024**3)
            print(f"  [GPU {i}] Name: {name}, Total VRAM: {vram:.2f} GB")
    else:
        print("GPU / CUDA           : No CUDA GPU detected in PyTorch")
except ImportError:
    print("PyTorch Version      : NOT INSTALLED")

# Check packages
for pkg in ["transformers", "peft", "trl", "accelerate", "bitsandbytes", "datasets"]:
    try:
        mod = __import__(pkg)
        ver = getattr(mod, "__version__", "unknown")
        print(f"{pkg.ljust(21)}: {ver}")
    except ImportError:
        print(f"{pkg.ljust(21)}: NOT INSTALLED")

print("==================================================")
