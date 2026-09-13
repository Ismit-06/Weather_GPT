import os
import sys
import json
import argparse
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parent.parent
DATASET_DIR = ROOT_DIR / "dataset"
LABELS_FILE = DATASET_DIR / "labels.jsonl"

SUPPORTED_FORMATS = {".jpg", ".jpeg", ".png", ".webp"}

def infer_ground_truth(rel_path: str):
    parts = Path(rel_path).as_posix().split("/")
    # Check nightmare path
    if "nightmare" in parts:
        subcat = parts[1] if len(parts) > 2 else "other"
        return {
            "sky_detected": False,
            "scene_type": subcat if subcat in [
                "bedsheet", "blanket", "ceiling", "wall", "curtain", "fabric",
                "window_reflection", "screen_or_photo"
            ] else "indoor_surface",
            "cloud_condition": "not_applicable"
        }

    # Check non_sky
    if "non_sky" in parts:
        filename = Path(rel_path).stem.lower()
        scene = "indoor_surface"
        for candidate in ["bedsheet", "blanket", "ceiling", "wall", "curtain", "fabric"]:
            if candidate in filename:
                scene = candidate
                break
        return {
            "sky_detected": False,
            "scene_type": scene,
            "cloud_condition": "not_applicable"
        }

    # Check sky
    if "sky" in parts:
        filename = Path(rel_path).stem.lower()
        condition = "partly_cloudy"
        for cond in ["clear", "overcast", "storm_clouds", "haze", "fog", "mostly_cloudy", "partly_cloudy"]:
            if cond in filename:
                condition = cond
                break
        return {
            "sky_detected": True,
            "scene_type": "outdoor_sky",
            "cloud_condition": condition
        }

    return None

def main():
    parser = argparse.ArgumentParser(
        description="Sky AI - Generate or synchronize dataset/labels.jsonl based on folder organization"
    )
    parser.add_argument(
        "--sync",
        action="store_true",
        help="Scan dataset/ and generate label templates for unlabelled images"
    )
    args = parser.parse_args()

    print("==================================================")
    print(" WeatherGPT Sky AI - Labels Generator & Manager")
    print("==================================================")

    existing_labels = {}
    if LABELS_FILE.exists():
        with open(LABELS_FILE, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                try:
                    record = json.loads(line)
                    img = record.get("image", "").replace("\\", "/").strip()
                    if img:
                        existing_labels[img] = record.get("ground_truth", {})
                except Exception:
                    continue

    all_images = []
    for ext in SUPPORTED_FORMATS:
        all_images.extend(DATASET_DIR.rglob(f"*{ext}"))
    all_images = sorted(list(set(all_images)))

    new_count = 0
    updated_records = []

    for path in all_images:
        rel_posix = path.relative_to(DATASET_DIR).as_posix()
        if rel_posix in existing_labels:
            updated_records.append({
                "image": rel_posix,
                "ground_truth": existing_labels[rel_posix]
            })
        else:
            inferred = infer_ground_truth(rel_posix)
            if inferred:
                updated_records.append({
                    "image": rel_posix,
                    "ground_truth": inferred
                })
                new_count += 1
            else:
                updated_records.append({
                    "image": rel_posix,
                    "ground_truth": {
                        "sky_detected": False,
                        "scene_type": "unknown",
                        "cloud_condition": "not_applicable"
                    }
                })
                new_count += 1

    with open(LABELS_FILE, "w", encoding="utf-8") as f:
        for rec in updated_records:
            f.write(json.dumps(rec) + "\n")

    print(f"Total labels recorded: {len(updated_records)}")
    print(f"New labels appended  : {new_count}")
    print(f"Saved to             : {LABELS_FILE}")

if __name__ == "__main__":
    main()
