import os
import sys
import json
import argparse
from pathlib import Path

def main():
    parser = argparse.ArgumentParser(description="Evaluate a fine-tuned LoRA checkpoint on validation set")
    parser.add_argument("--adapter", type=str, required=True, help="Path to LoRA adapter checkpoint")
    parser.add_argument("--val-data", type=str, default="dataset/validation.jsonl", help="Validation dataset JSONL")
    args = parser.parse_args()

    print("==================================================")
    print(" WeatherGPT Sky AI - Checkpoint Evaluator")
    print("==================================================")
    print(f"Adapter Checkpoint: {args.adapter}")
    print(f"Validation Dataset: {args.val_data}")
    print("Locked evaluation sets (test/nightmare) remain strictly untouched.")

if __name__ == "__main__":
    main()
