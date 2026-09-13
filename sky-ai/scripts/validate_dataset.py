import os
import sys
import json
from pathlib import Path
from collections import Counter
from PIL import Image

# Locate workspace root
ROOT_DIR = Path(__file__).resolve().parent.parent
DATASET_DIR = ROOT_DIR / "dataset"
LABELS_FILE = DATASET_DIR / "labels.jsonl"

SUPPORTED_FORMATS = {".jpg", ".jpeg", ".png", ".webp"}
ALLOWED_SCENE_TYPES = {
    "outdoor_sky", "fabric", "bedsheet", "blanket", "ceiling",
    "wall", "curtain", "indoor_surface", "window_reflection",
    "screen_or_photo", "building", "vegetation", "ground", "indoor_scene", "unknown"
}
ALLOWED_CLOUD_CONDITIONS = {
    "clear", "partly_cloudy", "mostly_cloudy", "overcast", "storm_clouds",
    "haze", "fog", "not_applicable", "unknown"
}

def compute_dhash(image_path, hash_size=8):
    try:
        with Image.open(image_path) as img:
            img = img.convert("L").resize((hash_size + 1, hash_size), Image.Resampling.LANCZOS)
            pixels = list(img.getdata())
            diff = []
            for row in range(hash_size):
                for col in range(hash_size):
                    left = pixels[row * (hash_size + 1) + col]
                    right = pixels[row * (hash_size + 1) + col + 1]
                    diff.append(left > right)
            decimal_val = 0
            hex_str = []
            for index, value in enumerate(diff):
                if value:
                    decimal_val += 2**(index % 8)
                if (index % 8) == 7:
                    hex_str.append(hex(decimal_val)[2:].rjust(2, "0"))
                    decimal_val = 0
            return "".join(hex_str)
    except Exception:
        return None

def main():
    print("==================================================")
    print(" WeatherGPT Sky AI - Dataset Quality & Validation")
    print("==================================================")

    if not DATASET_DIR.exists():
        print(f"[CRITICAL ERROR] Dataset directory missing: {DATASET_DIR}")
        sys.exit(1)

    # 1. Load labels.jsonl
    labels = {}
    label_syntax_errors = []
    if LABELS_FILE.exists():
        with open(LABELS_FILE, "r", encoding="utf-8") as f:
            for line_no, line in enumerate(f, 1):
                line = line.strip()
                if not line:
                    continue
                try:
                    record = json.loads(line)
                    img_rel = record.get("image", "").replace("\\", "/").strip()
                    gt = record.get("ground_truth", {})
                    if not img_rel or not isinstance(gt, dict):
                        label_syntax_errors.append(f"Line {line_no}: missing image path or ground_truth")
                        continue
                    labels[img_rel] = gt
                except Exception as e:
                    label_syntax_errors.append(f"Line {line_no}: JSON decode error: {e}")
    else:
        print(f"[WARNING] {LABELS_FILE} does not exist yet.")

    # 2. Find all images
    all_image_paths = []
    for ext in SUPPORTED_FORMATS:
        all_image_paths.extend(DATASET_DIR.rglob(f"*{ext}"))
    all_image_paths = sorted(list(set(all_image_paths)))

    print(f"Total image files discovered: {len(all_image_paths)}")
    print(f"Total labeled records loaded: {len(labels)}")

    # 3. Check for duplicates, unreadable files, dimensions, hashing
    filename_counter = Counter()
    dhash_map = {}
    corrupted_images = []
    dimension_stats = []
    split_distribution = Counter()
    scene_distribution = Counter()
    sky_distribution = Counter()
    missing_labels = []
    invalid_label_rules = []

    for path in all_image_paths:
        rel_to_dataset = path.relative_to(DATASET_DIR).as_posix()
        filename_counter[path.name] += 1

        # Check split
        parts = rel_to_dataset.split("/")
        split_name = parts[0] if len(parts) > 1 else "root"
        split_distribution[split_name] += 1

        # Check readability & dimensions
        try:
            with Image.open(path) as img:
                img.verify()
            with Image.open(path) as img:
                w, h = img.size
                dimension_stats.append((w, h))
        except Exception as e:
            corrupted_images.append((rel_to_dataset, str(e)))
            continue

        # Perceptual hash for near-duplicate detection
        dh = compute_dhash(path)
        if dh:
            if dh in dhash_map:
                dhash_map[dh].append(rel_to_dataset)
            else:
                dhash_map[dh] = [rel_to_dataset]

        # Check ground truth label
        gt = labels.get(rel_to_dataset)
        if not gt:
            missing_labels.append(rel_to_dataset)
        else:
            sky_det = gt.get("sky_detected")
            scene = gt.get("scene_type")
            cond = gt.get("cloud_condition")

            if sky_det is None or not isinstance(sky_det, bool):
                invalid_label_rules.append(f"{rel_to_dataset}: invalid or missing sky_detected (must be bool)")
            if scene not in ALLOWED_SCENE_TYPES:
                invalid_label_rules.append(f"{rel_to_dataset}: invalid scene_type '{scene}'")
            if cond not in ALLOWED_CLOUD_CONDITIONS:
                invalid_label_rules.append(f"{rel_to_dataset}: invalid cloud_condition '{cond}'")

            # Consistency checks
            if sky_det is False and cond not in ["not_applicable", "unknown"]:
                invalid_label_rules.append(f"{rel_to_dataset}: non-sky image has invalid cloud_condition '{cond}' (must be not_applicable)")
            if sky_det is True and scene != "outdoor_sky":
                invalid_label_rules.append(f"{rel_to_dataset}: sky_detected=true but scene_type is '{scene}'")

            sky_distribution["sky" if sky_det else "non_sky"] += 1
            scene_distribution[scene] += 1

    duplicate_filenames = {name: count for name, count in filename_counter.items() if count > 1}
    perceptual_duplicates = {h: paths for h, paths in dhash_map.items() if len(paths) > 1}

    print("--------------------------------------------------")
    print(" DATASET DISTRIBUTION BY SPLIT")
    print("--------------------------------------------------")
    for split, count in split_distribution.most_common():
        print(f"  - {split.ljust(16)}: {count} images")

    print("\n--------------------------------------------------")
    print(" GROUND TRUTH CLASS DISTRIBUTION")
    print("--------------------------------------------------")
    print(f"  - True Outdoor Sky: {sky_distribution.get('sky', 0)}")
    print(f"  - Non-Sky Objects : {sky_distribution.get('non_sky', 0)}")
    print("  Scenes breakdown:")
    for scene, count in scene_distribution.most_common():
        print(f"    * {scene.ljust(18)}: {count}")

    print("\n--------------------------------------------------")
    print(" DATASET QUALITY & INTEGRITY CHECKS")
    print("--------------------------------------------------")
    print(f"  - Corrupted or unreadable images : {len(corrupted_images)}")
    for c_img, err in corrupted_images:
        print(f"    [!] {c_img}: {err}")

    print(f"  - Duplicate filename occurrences : {len(duplicate_filenames)}")
    for d_name, count in duplicate_filenames.items():
        print(f"    [!] '{d_name}' appears {count} times across directories")

    print(f"  - Perceptual duplicate clusters  : {len(perceptual_duplicates)}")
    for d_h, paths in list(perceptual_duplicates.items())[:5]:
        print(f"    [!] Duplicate cluster ({len(paths)} images): {paths}")

    print(f"  - Images missing labels          : {len(missing_labels)}")
    for m_img in missing_labels[:10]:
        print(f"    [!] Unlabeled: {m_img}")
    if len(missing_labels) > 10:
        print(f"    ... and {len(missing_labels) - 10} more")

    print(f"  - Invalid label schema rules     : {len(invalid_label_rules)}")
    for inv in invalid_label_rules[:10]:
        print(f"    [!] {inv}")

    print("\n==================================================")
    if corrupted_images or invalid_label_rules or missing_labels:
        print(" [VALIDATION SUMMARY] Dataset has issues that need attention.")
    else:
        print(" [VALIDATION SUMMARY] All checked images pass integrity checks.")
    print("==================================================")

if __name__ == "__main__":
    main()
