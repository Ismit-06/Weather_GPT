# WeatherGPT Sky AI — Multimodal LoRA Fine-Tuning Guide (Phase 3B)

This directory houses the training, checkpoint management, and inference pipeline for fine-tuning Google Gemma 4 26B A4B Instruct (google/gemma-4-26B-A4B-it) to create a lightweight WeatherGPT Sky AI LoRA Adapter.

---

## 1. Hardware Requirements & Local Audit

A strict hardware audit was executed via scripts/check_environment.py and nvidia-smi:

- Operating System: Windows 11 (10.0.26200)
- Local GPU Detected: NVIDIA GeForce RTX 2050 Laptop GPU
- Dedicated VRAM: 4.00 GB (4096 MiB)
- Total System RAM: 7.65 GB (~200 MB free)
- Free Disk Space: ~59.10 GB

### Feasibility Analysis for Gemma 4 26B A4B:
- bfloat16 Full Weights: ~52 GB (Requires an 80GB A100 / H100 GPU).
- 4-bit QLoRA Quantized Weights: ~14-16 GB VRAM just to load, plus 4-8 GB activation memory during gradient checkpointing (>= 20-24 GB VRAM recommended).
- Physical Reality: The local 4GB RTX 2050 GPU and 7.6GB host RAM cannot hold or train the 26-billion parameter Gemma 4 model.
- In strict adherence to Rule 18, we do not fake training or silently switch to an inferior toy model.

---

## 2. Recommended Cloud Training Environments

To execute train_lora.py:
1. Google Colab Pro: Select an A100 (40GB/80GB) GPU runtime.
2. Kaggle: Dual T4 or P100 (2x16GB) or TPU v3-8 (for inference/light QLoRA).
3. RunPod / Lambda Labs / Vast.ai: Rent an RTX 4090 (24GB VRAM) for ~.40/hr or A100 (80GB) for ~.50/hr.

---

## 3. Hugging Face Authentication & Gated Model Access

google/gemma-4-26B-A4B-it is a gated release by Google DeepMind.
1. Visit the official Hugging Face model page: https://huggingface.co/google/gemma-4-26B-A4B-it
2. Accept the Gemma terms of use and click Submit Request.
3. In your training shell, log in with your Hugging Face User Access Token:
   huggingface-cli login

---

## 4. Training Directory Layout

sky-ai/
├── training/
│   ├── train_lora.py          # Main multimodal QLoRA training script
│   ├── evaluate_checkpoint.py # Validates adapter on validation.jsonl
│   ├── inference.py           # Loads base model + adapter for optical testing
│   ├── checkpoint_utils.py    # Checkpoint serialization and integrity validator
│   └── README.md              # Architecture & hardware documentation
├── configs/
│   ├── lora_smoke_test.yaml   # 10-step smoke test config
│   └── lora_training.yaml     # Full training config (rank 16, alpha 32)
├── checkpoints/
│   └── SKY-LORA-001/          # Output LoRA adapter weights & metadata
└── logs/                      # Step-by-step training logs

---

## 5. Execution Commands

### Step 1: Smoke Test
python training/train_lora.py --config configs/lora_smoke_test.yaml

### Step 2: Test Inference with Adapter
python training/inference.py --image dataset/validation/non_sky/val_nonsky_bedsheet_wrinkled_01.jpg --adapter checkpoints/SKY-LORA-001

### Step 3: Checkpoint Validation
python training/evaluate_checkpoint.py --adapter checkpoints/SKY-LORA-001
