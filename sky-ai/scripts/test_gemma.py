import sys
import json
import argparse
from pathlib import Path
from datetime import datetime

# Add current scripts directory to path
CURRENT_DIR = Path(__file__).resolve().parent
if str(CURRENT_DIR) not in sys.path:
    sys.path.insert(0, str(CURRENT_DIR))

from sky_client import analyze_sky_image, SkyAnalysisResponse

def main():
    parser = argparse.ArgumentParser(
        description="Sky AI - Test an image with Google Gemma 4 26B A4B on OpenRouter"
    )
    parser.add_argument(
        "--image",
        type=str,
        required=True,
        help="Path to the image file to analyze"
    )
    parser.add_argument(
        "--model",
        type=str,
        default=None,
        help="OpenRouter model ID (defaults to OPENROUTER_MODEL env or google/gemma-4-26b-a4b-it:free)"
    )
    parser.add_argument(
        "--output-dir",
        type=str,
        default=str(CURRENT_DIR.parent / "results"),
        help="Directory to save the resulting JSON file"
    )

    args = parser.parse_args()
    image_path = Path(args.image)

    if not image_path.exists():
        print(f"[ERROR] Image not found: {image_path}")
        sys.exit(1)

    print(f"==================================================")
    print(f" WeatherGPT Sky AI - Gemma Visual Perception Test")
    print(f"==================================================")
    print(f"Target Image : {image_path}")
    print(f"Model        : {args.model or 'Default (from config/env)'}")
    print(f"Processing and querying OpenRouter...")

    try:
        result: SkyAnalysisResponse = analyze_sky_image(
            image_path=image_path,
            model=args.model
        )
    except Exception as e:
        print(f"\n[FAILURE] Analysis could not be completed:")
        print(f"Error: {e}")
        sys.exit(1)

    result_dict = result.model_dump()

    print(f"\n[SUCCESS] Model Response Received and Validated:")
    print(json.dumps(result_dict, indent=2))

    # Save output under results/
    out_dir = Path(args.output_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    out_file = out_dir / f"{image_path.stem}_{timestamp}.json"

    with open(out_file, "w", encoding="utf-8") as f:
        json.dump(
            {
                "image_path": str(image_path),
                "timestamp": timestamp,
                "analysis": result_dict
            },
            f,
            indent=2
        )

    print(f"\nSaved structured result to: {out_file}")

if __name__ == "__main__":
    main()
