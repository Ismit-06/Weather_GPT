import os
import json
import shutil
from pathlib import Path
from typing import Dict, Any

def save_training_metadata(
    output_dir: Path,
    experiment_id: str,
    config: Dict[str, Any],
    trainable_params: int,
    total_params: int,
    loss_history: list,
    eval_metrics: Dict[str, Any]
):
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    meta = {
        "experiment_id": experiment_id,
        "base_model": config.get("model", {}).get("base_model_name_or_path", "google/gemma-4-26B-A4B-it"),
        "peft_type": "LORA",
        "trainable_parameters": trainable_params,
        "total_parameters": total_params,
        "trainable_percentage": round((trainable_params / total_params) * 100, 4) if total_params > 0 else 0.0,
        "final_train_loss": loss_history[-1] if loss_history else None,
        "eval_metrics": eval_metrics,
        "config": config
    }
    with open(output_dir / "training_config.json", "w", encoding="utf-8") as f:
        json.dump(meta, f, indent=2)
    return meta

def verify_checkpoint_structure(checkpoint_dir: Path) -> bool:
    required_files = ["adapter_config.json"]
    checkpoint_dir = Path(checkpoint_dir)
    if not checkpoint_dir.exists():
        return False
    found = [f.name for f in checkpoint_dir.iterdir()]
    has_weights = any(f.startswith("adapter_model") for f in found)
    has_config = "adapter_config.json" in found
    return has_weights and has_config
