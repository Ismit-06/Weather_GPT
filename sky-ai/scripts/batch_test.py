import sys
import json
import argparse
import time
from pathlib import Path
from datetime import datetime

CURRENT_DIR = Path(__file__).resolve().parent
if str(CURRENT_DIR) not in sys.path:
    sys.path.insert(0, str(CURRENT_DIR))

from sky_client import analyze_sky_image, SkyAnalysisResponse

SUPPORTED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"}

def main():
    parser = argparse.ArgumentParser(
        description="Sky AI - Run batch inference across an image directory with Gemma 4 26B A4B"
    )
    parser.add_argument(
        "--input",
        type=str,
        required=True,
        help="Path to folder containing images (searched recursively)"
    )
    parser.add_argument(
        "--model",
        type=str,
        default=None,
        help="OpenRouter model ID"
    )
    parser.add_argument(
        "--delay",
        type=float,
        default=1.5,
        help="Delay in seconds between requests to respect rate limits"
    )
    parser.add_argument(
        "--output-dir",
        type=str,
        default=str(CURRENT_DIR.parent / "results"),
        help="Directory to save individual and batch JSON results"
    )

    args = parser.parse_args()
    input_dir = Path(args.input)
    if not input_dir.exists():
        print(f"[ERROR] Input directory not found: {input_dir}")
        sys.exit(1)

    # Search recursively for images
    image_files = []
    for ext in SUPPORTED_EXTENSIONS:
        image_files.extend(input_dir.rglob(f"*{ext}"))
    image_files = sorted(list(set(image_files)))

    if not image_files:
        print(f"[WARNING] No supported images found in {input_dir}")
        sys.exit(0)

    print("==================================================")
    print(" WeatherGPT Sky AI - Batch Inference")
    print("==================================================")
    print(f"Target Directory : {input_dir}")
    print(f"Total Images     : {len(image_files)}")
    print(f"Model            : {args.model or 'Default (from config/env)'}")
    print(f"Output Directory : {args.output_dir}")
    print("==================================================")

    out_dir = Path(args.output_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    batch_records = []
    for idx, img_path in enumerate(image_files, 1):
        print(f"[{idx}/{len(image_files)}] {img_path.name} ...", end=" ", flush=True)
        rel_path = img_path.as_posix()
        try:
            result = analyze_sky_image(image_path=img_path, model=args.model)
            analysis = result.model_dump()
            print(f"DONE -> sky_detected={analysis['sky_detected']} (scene={analysis['scene_type']}, conf={analysis['sky_confidence']})")
        except Exception as e:
            print(f"FAILED ({e})")
            analysis = {"error": str(e)}

        record = {
            "image_path": rel_path,
            "filename": img_path.name,
            "timestamp": datetime.now().isoformat(),
            "analysis": analysis
        }
        batch_records.append(record)

        # Save individual raw response
        raw_file = out_dir / f"{img_path.stem}_response.json"
        with open(raw_file, "w", encoding="utf-8") as f:
            json.dump(record, f, indent=2)

        time.sleep(args.delay)

    # Save summary batch file
    timestamp_str = datetime.now().strftime("%Y%m%d_%H%M%S")
    clean_dirname = input_dir.name or "batch"
    batch_file = out_dir / f"batch_{clean_dirname}_{timestamp_str}.json"
    with open(batch_file, "w", encoding="utf-8") as f:
        json.dump(batch_records, f, indent=2)

    print(f"\nBatch processing finished. Summary saved to: {batch_file}")

if __name__ == "__main__":
    main()
