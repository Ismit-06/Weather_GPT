import os
import sys
import json
import yaml
import argparse
from pathlib import Path

def main():
    parser = argparse.ArgumentParser(description="Full Training Execution Script for SKY-LORA-001")
    parser.add_argument("--config", type=str, default="configs/SKY-LORA-001.yaml", help="Path to full training config")
    parser.add_argument("--resume-from-checkpoint", type=str, default=None, help="Resume training from an existing checkpoint")
    args = parser.parse_args()

    config_path = Path(args.config)
    if not config_path.exists():
        print(f"[ERROR] Config file not found: {config_path}")
        sys.exit(1)

    with open(config_path, "r", encoding="utf-8") as f:
        cfg = yaml.safe_load(f)

    exp_id = cfg.get("experiment_id", "SKY-LORA-001")
    model_id = cfg.get("model", {}).get("base_model_name_or_path", "google/gemma-4-26B-A4B-it")
    out_dir = Path(cfg.get("training_args", {}).get("output_dir", f"checkpoints/{exp_id}"))

    print("==================================================")
    print(f" WeatherGPT Sky AI - Full Training ({exp_id})")
    print("==================================================")
    print(f"Base Multimodal Model: {model_id}")
    print(f"Checkpoint Target    : {out_dir}")
    print(f"Visual Token Budget  : {cfg.get('image_token_budget', {}).get('visual_token_budget', 256)}")
    print(f"LoRA Target Modules  : {cfg.get('peft_lora', {}).get('target_modules', [])}")

    # Check framework availability
    try:
        import torch
        import transformers
        import peft
    except ImportError as e:
        print(f"\n[DEPENDENCY AUDIT FAILURE] Missing ML framework: {e}")
        print("To run local training, install: torch, transformers, peft, accelerate, bitsandbytes, datasets")
        sys.exit(1)

    if not torch.cuda.is_available():
        print("\n[HARDWARE FAILURE] No CUDA GPU available for training.")
        print("Full training of Gemma 4 26B A4B requires a high-memory GPU (A100 / RTX 4090 / H100).")
        sys.exit(1)

    vram = torch.cuda.get_device_properties(0).total_memory / (1024**3)
    gpu_name = torch.cuda.get_device_name(0)
    print(f"Detected GPU         : {gpu_name} ({vram:.2f} GB VRAM)")

    if vram < 14.0:
        print(f"\n[FATAL HARDWARE LIMITATION] {gpu_name} has only {vram:.2f} GB VRAM.")
        print("Gemma 4 26B A4B requires at least ~14-16 GB VRAM just to load 4-bit weights and >= 24GB for training.")
        print("Stopping execution safely without crash. Please run on Google Colab Pro (A100) or Cloud GPU.")
        sys.exit(1)

if __name__ == "__main__":
    main()
