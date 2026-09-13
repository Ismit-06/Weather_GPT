import os
import sys
import json
import argparse
from pathlib import Path
from collections import defaultdict

ROOT_DIR = Path(__file__).resolve().parent.parent
DATASET_DIR = ROOT_DIR / "dataset"
LABELS_FILE = DATASET_DIR / "labels.jsonl"
RESULTS_DIR = ROOT_DIR / "results"
EVALUATION_DIR = ROOT_DIR / "evaluation"
BASELINE_DIR = EVALUATION_DIR / "baseline"
REPORTS_DIR = EVALUATION_DIR / "reports"

def load_ground_truth():
    gt = {}
    if LABELS_FILE.exists():
        with open(LABELS_FILE, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                try:
                    data = json.loads(line)
                    img = data.get("image", "").replace("\\", "/").strip()
                    gt[img] = data.get("ground_truth", {})
                    # Also map by filename for easy matching
                    gt[Path(img).name] = data.get("ground_truth", {})
                except Exception:
                    continue
    return gt

def main():
    parser = argparse.ArgumentParser(
        description="Sky AI - Compute comprehensive baseline benchmark metrics"
    )
    parser.add_argument(
        "--results-dir",
        type=str,
        default=str(RESULTS_DIR),
        help="Directory containing Gemma prediction JSON outputs"
    )
    args = parser.parse_args()

    REPORTS_DIR.mkdir(parents=True, exist_ok=True)
    BASELINE_DIR.mkdir(parents=True, exist_ok=True)

    gt_map = load_ground_truth()
    results_path = Path(args.results_dir)

    print("==================================================")
    print(" WeatherGPT Sky AI - Baseline Benchmark Evaluation")
    print("==================================================")

    # Gather prediction records
    prediction_records = []
    for f in results_path.glob("*_response.json"):
        try:
            with open(f, "r", encoding="utf-8") as fp:
                data = json.load(fp)
                prediction_records.append(data)
        except Exception:
            continue

    if not prediction_records:
        # Also check batch_*.json files
        for f in results_path.glob("batch_*.json"):
            try:
                with open(f, "r", encoding="utf-8") as fp:
                    items = json.load(fp)
                    if isinstance(items, list):
                        prediction_records.extend(items)
            except Exception:
                continue

    # Deduplicate by filename
    unique_predictions = {}
    for r in prediction_records:
        fname = r.get("filename") or Path(r.get("image_path", "")).name
        if fname:
            unique_predictions[fname] = r

    print(f"Loaded ground-truth records: {len(gt_map)}")
    print(f"Loaded unique predictions : {len(unique_predictions)}")

    total_images = 0
    correct = 0
    incorrect = 0
    tp = 0
    tn = 0
    fp = 0
    fn = 0

    non_sky_total = 0
    non_sky_fp = 0

    nightmare_total = 0
    nightmare_fp = 0

    category_stats = defaultdict(lambda: {"total": 0, "fp": 0, "correct": 0})
    confidence_buckets_sky = defaultdict(int)
    confidence_buckets_non_sky = defaultdict(int)

    failures = []

    for fname, pred_record in unique_predictions.items():
        analysis = pred_record.get("analysis", {})
        if "error" in analysis or "sky_detected" not in analysis:
            continue

        pred_sky = analysis["sky_detected"]
        pred_conf = float(analysis.get("sky_confidence", 0.0))
        pred_scene = analysis.get("scene_type", "unknown")
        pred_cond = analysis.get("cloud_condition", "unknown")
        reason = analysis.get("reason", "")

        img_path_raw = pred_record.get("image_path", fname)
        gt = gt_map.get(fname) or gt_map.get(img_path_raw)

        if not gt:
            continue

        total_images += 1
        gt_sky = gt.get("sky_detected", False)
        gt_scene = gt.get("scene_type", "unknown")

        # Confidence bucket (0.0-0.1, 0.1-0.2, ...)
        bucket_idx = min(int(pred_conf * 10), 9)
        bucket_label = f"{bucket_idx/10:.1f}-{(bucket_idx+1)/10:.1f}"

        if gt_sky:
            confidence_buckets_sky[bucket_label] += 1
        else:
            confidence_buckets_non_sky[bucket_label] += 1

        is_nightmare = "nightmare" in img_path_raw.lower() or gt_scene in [
            "bedsheet", "blanket", "ceiling", "wall", "curtain", "fabric", "window_reflection", "screen_or_photo"
        ]

        # Evaluate correctness
        if pred_sky == gt_sky:
            correct += 1
            if gt_sky:
                tp += 1
            else:
                tn += 1
                category_stats[gt_scene]["correct"] += 1
        else:
            incorrect += 1
            failure_record = {
                "image": img_path_raw,
                "filename": fname,
                "ground_truth": gt,
                "predicted_sky_detected": pred_sky,
                "predicted_confidence": pred_conf,
                "predicted_scene_type": pred_scene,
                "predicted_cloud_condition": pred_cond,
                "model_reason": reason,
                "severity": 0
            }

            if not gt_sky and pred_sky:
                fp += 1
                non_sky_fp += 1
                category_stats[gt_scene]["fp"] += 1
                if is_nightmare:
                    nightmare_fp += 1

                if pred_conf >= 0.80:
                    failure_record["severity"] = 1  # Critical
                    failure_record["severity_label"] = "CRITICAL: High-confidence False Sky (>=0.80)"
                elif pred_conf >= 0.50:
                    failure_record["severity"] = 2
                    failure_record["severity_label"] = "HIGH: False Sky (>=0.50)"
                else:
                    failure_record["severity"] = 3
                    failure_record["severity_label"] = "MEDIUM: False Sky (<0.50)"
            elif gt_sky and not pred_sky:
                fn += 1
                failure_record["severity"] = 4
                failure_record["severity_label"] = "FALSE NEGATIVE: Genuine sky rejected"

            failures.append(failure_record)

        if not gt_sky:
            non_sky_total += 1
            category_stats[gt_scene]["total"] += 1
            if is_nightmare:
                nightmare_total += 1

    # Sort failures by severity
    failures.sort(key=lambda x: x["severity"])

    # Metrics calculation
    accuracy = round(correct / total_images, 4) if total_images > 0 else 0.0
    precision = round(tp / (tp + fp), 4) if (tp + fp) > 0 else 0.0
    recall = round(tp / (tp + fn), 4) if (tp + fn) > 0 else 0.0
    f1 = round(2 * (precision * recall) / (precision + recall), 4) if (precision + recall) > 0 else 0.0
    false_sky_rate = round(non_sky_fp / non_sky_total, 4) if non_sky_total > 0 else 0.0
    nightmare_false_sky_rate = round(nightmare_fp / nightmare_total, 4) if nightmare_total > 0 else 0.0

    # Specific category rates
    category_rates = {}
    for cat, data in category_stats.items():
        tot = data["total"]
        fps = data["fp"]
        category_rates[cat] = {
            "total": tot,
            "false_sky_count": fps,
            "false_sky_rate": round(fps / tot, 4) if tot > 0 else 0.0,
            "rejection_rate": round((tot - fps) / tot, 4) if tot > 0 else 0.0
        }

    high_conf_false_sky = [f for f in failures if f["severity"] == 1]

    metrics_payload = {
        "model": "google/gemma-4-26b-a4b-it:free",
        "total_test_images": total_images,
        "correct": correct,
        "incorrect": incorrect,
        "accuracy": accuracy,
        "sky_detection_metrics": {
            "true_positive": tp,
            "true_negative": tn,
            "false_positive": fp,
            "false_negative": fn,
            "precision": precision,
            "recall": recall,
            "f1_score": f1
        },
        "rates": {
            "false_sky_rate": false_sky_rate,
            "nightmare_false_sky_rate": nightmare_false_sky_rate,
            "category_breakdown": category_rates
        },
        "confidence_distribution": {
            "genuine_sky": dict(confidence_buckets_sky),
            "non_sky": dict(confidence_buckets_non_sky)
        },
        "high_confidence_false_sky_count": len(high_conf_false_sky),
        "total_failures": len(failures)
    }

    # Save outputs
    baseline_json = BASELINE_DIR / "baseline_metrics.json"
    with open(baseline_json, "w", encoding="utf-8") as f:
        json.dump(metrics_payload, f, indent=2)

    report_json = REPORTS_DIR / "baseline_report.json"
    with open(report_json, "w", encoding="utf-8") as f:
        json.dump(metrics_payload, f, indent=2)

    failures_json = REPORTS_DIR / "failures.json"
    with open(failures_json, "w", encoding="utf-8") as f:
        json.dump(failures, f, indent=2)

    print(f"Calculated accuracy      : {accuracy}")
    print(f"Overall False Sky Rate   : {false_sky_rate}")
    print(f"Nightmare False Sky Rate : {nightmare_false_sky_rate}")
    print(f"High-confidence false sky: {len(high_conf_false_sky)}")
    print(f"Reports saved to {REPORTS_DIR}")

if __name__ == "__main__":
    main()
