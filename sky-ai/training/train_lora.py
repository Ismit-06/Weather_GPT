import os
import sys
import json
import yaml
import argparse
from pathlib import Path
from PIL import Image

def load_dataset_slice(jsonl_path, max_samples=None):
    records = []
    with open(jsonl_path, "r", encoding="utf-8") as f:
        for line in f:
            if not line.strip():
                continue
            records.append(json.loads(line.strip()))
            if max_samples and len(records) >= max_samples:
                break
    return records

def main():
    parser = argparse.ArgumentParser(description="Multimodal LoRA Fine-Tuning for Gemma 4 26B A4B")
    parser.add_argument("--config", type=str, default="configs/lora_smoke_test.yaml", help="Path to YAML training config")
    args = parser.parse_args()

    config_path = Path(args.config)
    if not config_path.exists():
        print(f"[ERROR] Config not found: {config_path}")
        sys.exit(1)

    with open(config_path, "r", encoding="utf-8") as f:
        cfg = yaml.safe_load(f)

    exp_id = cfg.get("experiment_id", "SKY-LORA-001")
    model_id = cfg.get("model", {}).get("base_model_name_or_path", "google/gemma-4-26B-A4B-it")
    output_dir = Path(cfg.get("training_args", {}).get("output_dir", f"checkpoints/{exp_id}"))

    print("==================================================")
    print(" WeatherGPT Sky AI - Gemma 4 Multimodal LoRA Train")
    print("==================================================")
    print(f"Experiment ID    : {exp_id}")
    print(f"Base Model Target: {model_id}")
    print(f"Output Directory : {output_dir}")

    # Preliminary import and hardware verification
    try:
        import torch
        import transformers
        import peft
    except ImportError as e:
        print(f"\n[DEPENDENCY ERROR] Missing machine learning framework: {e}")
        print("To run local training, install: torch, transformers, peft, accelerate, trl, bitsandbytes")
        sys.exit(1)

    # Hardware verification
    if not torch.cuda.is_available():
        print("\n[CRITICAL HARDWARE FAILURE] No CUDA GPU available for training.")
        print("Gemma 4 26B A4B requires an NVIDIA GPU with at least 16GB–24GB VRAM (with 4-bit QLoRA) or 80GB VRAM (bfloat16).")
        sys.exit(1)

    vram_gb = torch.cuda.get_device_properties(0).total_memory / (1024**3)
    gpu_name = torch.cuda.get_device_name(0)
    print(f"CUDA Device      : {gpu_name} ({vram_gb:.2f} GB VRAM)")

    if vram_gb < 12.0:
        print(f"\n[FATAL HARDWARE LIMITATION] Detected GPU has only {vram_gb:.2f} GB VRAM.")
        print("Gemma 4 26B A4B weights in 4-bit quantization require ~14GB–16GB VRAM just to load.")
        print("Training on this machine is not physically possible. Please deploy to Google Colab Pro / Kaggle / Cloud A100/H100.")
        sys.exit(1)

if __name__ == "__main__":
    main()
