import os
import sys
import json
import argparse
from pathlib import Path
from PIL import Image

def main():
    parser = argparse.ArgumentParser(description="Run inference with Base Gemma 4 or fine-tuned LoRA adapter")
    parser.add_argument("--image", type=str, required=True, help="Path to input image")
    parser.add_argument("--base-model", type=str, default="google/gemma-4-26B-A4B-it", help="Base Gemma model ID")
    parser.add_argument("--adapter", type=str, default=None, help="Path to LoRA adapter checkpoint")
    parser.add_argument("--device", type=str, default="cuda", help="Inference device (cuda / cpu)")
    args = parser.parse_args()

    image_path = Path(args.image)
    if not image_path.exists():
        print(f"[ERROR] Image not found: {image_path}")
        sys.exit(1)

    print("==================================================")
    print(" WeatherGPT Sky AI - Model Inference Test")
    print("==================================================")
    print(f"Target Image : {image_path}")
    print(f"Base Model   : {args.base_model}")
    print(f"LoRA Adapter : {args.adapter or 'None (Zero-Shot Base Model)'}")
    print(f"Target Device: {args.device}")

    try:
        import torch
        from transformers import AutoProcessor, AutoModelForImageTextToText
        from peft import PeftModel
    except ImportError as e:
        print(f"[CRITICAL ERROR] Missing required PyTorch/Transformers dependencies: {e}")
        print("Please ensure torch, transformers, and peft are installed.")
        sys.exit(1)

    print("Loading processor...")
    processor = AutoProcessor.from_pretrained(args.base_model, trust_remote_code=True)

    print("Loading model...")
    dtype = torch.bfloat16 if torch.cuda.is_available() and torch.cuda.is_bf16_supported() else torch.float32
    model = AutoModelForImageTextToText.from_pretrained(
        args.base_model,
        torch_dtype=dtype,
        device_map="auto" if args.device == "cuda" else None,
        trust_remote_code=True
    )

    if args.adapter:
        print(f"Applying LoRA adapter from {args.adapter}...")
        model = PeftModel.from_pretrained(model, args.adapter)

    model.eval()

    raw_image = Image.open(image_path).convert("RGB")
    prompt = [
        {"role": "user", "content": [
            {"type": "image"},
            {"type": "text", "text": "Analyze this image for WeatherGPT Sky AI."}
        ]}
    ]

    formatted_text = processor.apply_chat_template(prompt, add_generation_prompt=True)
    inputs = processor(text=formatted_text, images=raw_image, return_tensors="pt")
    if args.device == "cuda":
        inputs = {k: v.to("cuda") for k, v in inputs.items()}

    with torch.no_grad():
        outputs = model.generate(**inputs, max_new_tokens=256, do_sample=False)

    generated_ids = outputs[0][inputs["input_ids"].shape[1]:]
    response_text = processor.decode(generated_ids, skip_special_tokens=True).strip()

    print("\n[RESULT] Model Response:")
    print(response_text)

if __name__ == "__main__":
    main()
