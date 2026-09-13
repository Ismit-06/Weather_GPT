import os
import sys
import json
import random
from pathlib import Path
from PIL import Image, ImageDraw

ROOT_DIR = Path(__file__).resolve().parent.parent
DATASET_DIR = ROOT_DIR / "dataset"
TRAIN_DIR = DATASET_DIR / "train"
VAL_DIR = DATASET_DIR / "validation"
TRAIN_SKY = TRAIN_DIR / "sky"
TRAIN_NON_SKY = TRAIN_DIR / "non_sky"
VAL_SKY = VAL_DIR / "sky"
VAL_NON_SKY = VAL_DIR / "non_sky"

TRAIN_JSONL = DATASET_DIR / "train.jsonl"
VAL_JSONL = DATASET_DIR / "validation.jsonl"

def make_sample_image(path, kind, subkind):
    path.parent.mkdir(parents=True, exist_ok=True)
    if kind == "sky":
        if subkind == "clear_blue":
            img = Image.new("RGB", (256, 256), color=(70, 150, 240))
        elif subkind == "overcast_grey":
            img = Image.new("RGB", (256, 256), color=(190, 195, 200))
        elif subkind == "dark_storm":
            img = Image.new("RGB", (256, 256), color=(65, 75, 90))
        elif subkind == "monsoon_cumulus":
            img = Image.new("RGB", (256, 256), color=(110, 160, 210))
            draw = ImageDraw.Draw(img)
            draw.ellipse([50, 50, 200, 150], fill=(230, 235, 240))
        elif subkind == "sunset_golden":
            img = Image.new("RGB", (256, 256), color=(240, 140, 60))
            draw = ImageDraw.Draw(img)
            draw.rectangle([0, 180, 256, 256], fill=(180, 80, 40))
        elif subkind == "haze_fog":
            img = Image.new("RGB", (256, 256), color=(215, 220, 225))
        elif subkind == "sky_with_trees":
            img = Image.new("RGB", (256, 256), color=(90, 170, 245))
            draw = ImageDraw.Draw(img)
            draw.polygon([(0, 256), (60, 140), (120, 256)], fill=(34, 110, 40))
            draw.polygon([(140, 256), (200, 120), (256, 256)], fill=(28, 95, 35))
        elif subkind == "sky_with_poles_wires":
            img = Image.new("RGB", (256, 256), color=(100, 180, 250))
            draw = ImageDraw.Draw(img)
            draw.line([(120, 40), (120, 256)], fill=(40, 40, 40), width=3)
            draw.line([(0, 80), (256, 110)], fill=(30, 30, 30), width=1)
        elif subkind == "sky_with_buildings":
            img = Image.new("RGB", (256, 256), color=(95, 175, 240))
            draw = ImageDraw.Draw(img)
            draw.rectangle([160, 90, 240, 256], fill=(70, 80, 90))
        else:
            img = Image.new("RGB", (256, 256), color=(100, 170, 230))
    else: # non_sky hard negative
        if subkind == "bedsheet_wrinkled":
            img = Image.new("RGB", (256, 256), color=(210, 220, 235))
            draw = ImageDraw.Draw(img)
            for y in range(30, 240, 25):
                draw.line([(10, y), (245, y + 12)], fill=(170, 185, 205), width=2)
        elif subkind == "blanket_plaid":
            img = Image.new("RGB", (256, 256), color=(180, 190, 205))
            draw = ImageDraw.Draw(img)
            for x in range(20, 250, 40):
                draw.line([(x, 0), (x, 256)], fill=(140, 150, 170), width=2)
            for y in range(20, 250, 40):
                draw.line([(0, y), (256, y)], fill=(140, 150, 170), width=2)
        elif subkind == "ceiling_recessed_light":
            img = Image.new("RGB", (256, 256), color=(240, 240, 240))
            draw = ImageDraw.Draw(img)
            draw.ellipse([90, 90, 166, 166], fill=(255, 250, 210), outline=(180, 180, 180), width=3)
        elif subkind == "ceiling_plaster_texture":
            img = Image.new("RGB", (256, 256), color=(230, 230, 225))
            draw = ImageDraw.Draw(img)
            for i in range(10, 250, 20):
                draw.point((i, (i * 3) % 256), fill=(190, 190, 185))
        elif subkind == "wall_painted_blue":
            img = Image.new("RGB", (256, 256), color=(120, 170, 210))
            draw = ImageDraw.Draw(img)
            draw.line([(0, 240), (256, 240)], fill=(240, 240, 240), width=10) # baseboard
        elif subkind == "curtain_folds":
            img = Image.new("RGB", (256, 256), color=(220, 225, 235))
            draw = ImageDraw.Draw(img)
            for x in range(25, 240, 35):
                draw.line([(x, 0), (x, 256)], fill=(175, 180, 195), width=6)
        elif subkind == "fabric_linen_grey":
            img = Image.new("RGB", (256, 256), color=(195, 195, 200))
            draw = ImageDraw.Draw(img)
            for y in range(15, 245, 15):
                draw.line([(0, y), (256, y)], fill=(175, 175, 180), width=1)
        elif subkind == "window_reflection":
            img = Image.new("RGB", (256, 256), color=(140, 160, 180))
            draw = ImageDraw.Draw(img)
            draw.ellipse([140, 80, 190, 130], fill=(255, 230, 150)) # lamp reflection
            draw.line([(80, 0), (80, 256)], fill=(50, 50, 50), width=4) # window sash
        elif subkind == "screen_display_photo":
            img = Image.new("RGB", (256, 256), color=(20, 20, 20))
            draw = ImageDraw.Draw(img)
            draw.rectangle([20, 20, 236, 236], fill=(80, 160, 240)) # inner screen
        else:
            img = Image.new("RGB", (256, 256), color=(200, 200, 200))
    img.save(path, "JPEG")

def format_multimodal_sample(image_rel_path, is_sky, scene_type, cloud_cond, conf, coverage=None, obst="none"):
    assistant_payload = {
        "sky_detected": is_sky,
        "sky_confidence": conf,
        "scene_type": scene_type,
        "cloud_condition": cloud_cond,
        "cloud_coverage": coverage,
        "visible_precipitation": False,
        "horizon_visible": is_sky and obst != "window_frame",
        "obstruction": obst,
        "image_quality": "good"
    }
    return {
        "image": image_rel_path.replace("\\", "/"),
        "messages": [
            {
                "role": "user",
                "content": [
                    {"type": "image"},
                    {"type": "text", "text": "Analyze this image for WeatherGPT Sky AI."}
                ]
            },
            {
                "role": "assistant",
                "content": [
                    {"type": "text", "text": json.dumps(assistant_payload, separators=(",", ":"))}
                ]
            }
        ]
    }

def main():
    print("==================================================")
    print(" WeatherGPT Sky AI - Build Training & Val Datasets")
    print("==================================================")

    sky_subtypes = [
        ("clear_blue", "clear", 0.99, 0.0, "none"),
        ("overcast_grey", "overcast", 0.95, 0.95, "none"),
        ("dark_storm", "storm_clouds", 0.95, 0.98, "none"),
        ("monsoon_cumulus", "partly_cloudy", 0.95, 0.45, "none"),
        ("sunset_golden", "clear", 0.95, 0.10, "none"),
        ("haze_fog", "fog", 0.80, 0.90, "none"),
        ("sky_with_trees", "clear", 0.90, 0.0, "trees"),
        ("sky_with_poles_wires", "clear", 0.90, 0.0, "poles_wires"),
        ("sky_with_buildings", "partly_cloudy", 0.90, 0.30, "buildings")
    ]

    non_sky_subtypes = [
        ("bedsheet_wrinkled", "bedsheet", 0.01),
        ("blanket_plaid", "blanket", 0.01),
        ("ceiling_recessed_light", "ceiling", 0.01),
        ("ceiling_plaster_texture", "ceiling", 0.01),
        ("wall_painted_blue", "wall", 0.01),
        ("curtain_folds", "curtain", 0.01),
        ("fabric_linen_grey", "fabric", 0.01),
        ("window_reflection", "window_reflection", 0.10),
        ("screen_display_photo", "screen_or_photo", 0.01)
    ]

    train_records = []
    val_records = []

    # 1. Generate Training Sky samples (3 variations each)
    for idx, (subkind, cond, conf, cov, obst) in enumerate(sky_subtypes, 1):
        for var in range(1, 4):
            fname = f"train_sky_{subkind}_{var:02d}.jpg"
            p = TRAIN_SKY / fname
            make_sample_image(p, "sky", subkind)
            rec = format_multimodal_sample(f"train/sky/{fname}", True, "outdoor_sky", cond, conf, cov, obst)
            train_records.append(rec)

    # 2. Generate Training Hard-Negative samples (adversarial emphasis, 4 variations each)
    for idx, (subkind, scene, conf) in enumerate(non_sky_subtypes, 1):
        for var in range(1, 5):
            fname = f"train_nonsky_{subkind}_{var:02d}.jpg"
            p = TRAIN_NON_SKY / fname
            make_sample_image(p, "non_sky", subkind)
            rec = format_multimodal_sample(f"train/non_sky/{fname}", False, scene, "not_applicable", conf, None, "none")
            train_records.append(rec)

    # 3. Generate Validation Sky samples (1 variation each, isolated sequence)
    for idx, (subkind, cond, conf, cov, obst) in enumerate(sky_subtypes, 1):
        fname = f"val_sky_{subkind}_01.jpg"
        p = VAL_SKY / fname
        make_sample_image(p, "sky", subkind)
        rec = format_multimodal_sample(f"validation/sky/{fname}", True, "outdoor_sky", cond, conf, cov, obst)
        val_records.append(rec)

    # 4. Generate Validation Hard-Negative samples (1 variation each)
    for idx, (subkind, scene, conf) in enumerate(non_sky_subtypes, 1):
        fname = f"val_nonsky_{subkind}_01.jpg"
        p = VAL_NON_SKY / fname
        make_sample_image(p, "non_sky", subkind)
        rec = format_multimodal_sample(f"validation/non_sky/{fname}", False, scene, "not_applicable", conf, None, "none")
        val_records.append(rec)

    # Shuffle training records with fixed seed
    random.seed(42)
    random.shuffle(train_records)
    random.shuffle(val_records)

    with open(TRAIN_JSONL, "w", encoding="utf-8") as f:
        for r in train_records:
            f.write(json.dumps(r) + "\n")

    with open(VAL_JSONL, "w", encoding="utf-8") as f:
        for r in val_records:
            f.write(json.dumps(r) + "\n")

    print(f"Training set generated  : {len(train_records)} examples -> {TRAIN_JSONL}")
    print(f"Validation set generated: {len(val_records)} examples -> {VAL_JSONL}")

if __name__ == "__main__":
    main()
