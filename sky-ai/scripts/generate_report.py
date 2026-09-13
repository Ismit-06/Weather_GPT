import os
import sys
import json
from pathlib import Path
from datetime import datetime

ROOT_DIR = Path(__file__).resolve().parent.parent
REPORTS_DIR = ROOT_DIR / "evaluation" / "reports"
REPORT_JSON = REPORTS_DIR / "baseline_report.json"
FAILURES_JSON = REPORTS_DIR / "failures.json"
REPORT_MD = REPORTS_DIR / "baseline_report.md"

def main():
    if not REPORT_JSON.exists():
        print(f"[ERROR] Baseline report json missing: {REPORT_JSON}")
        print("Please run evaluate.py first.")
        sys.exit(1)

    with open(REPORT_JSON, "r", encoding="utf-8") as f:
        data = json.load(f)

    failures = []
    if FAILURES_JSON.exists():
        with open(FAILURES_JSON, "r", encoding="utf-8") as f:
            failures = json.load(f)

    total_images = data.get("total_test_images", 0)
    acc = data.get("accuracy", 0.0)
    sky_metrics = data.get("sky_detection_metrics", {})
    rates = data.get("rates", {})
    cat_breakdown = rates.get("category_breakdown", {})
    conf_dist = data.get("confidence_distribution", {})

    high_conf_false = [f for f in failures if f.get("severity") == 1]
    false_negatives = [f for f in failures if f.get("severity") == 4]

    bedsheet_stats = cat_breakdown.get("bedsheet", {})
    ceiling_stats = cat_breakdown.get("ceiling", {})
    fabric_stats = cat_breakdown.get("fabric", {})
    window_stats = cat_breakdown.get("window_reflection", {})

    md = []
    md.append("# WeatherGPT Sky AI — Baseline Benchmark Evaluation Report")
    md.append(f"\n**Generated**: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}  ")
    md.append(f"**Evaluated Model**: {data.get('model', 'google/gemma-4-26b-a4b-it:free')}  ")
    md.append(f"**Prompt**: Standardized Sky AI Perception Prompt (zero-shot baseline)  ")

    md.append("\n## 1. Executive Summary")
    md.append(f"- **Total Test Images Evaluated**: {total_images}")
    md.append(f"- **Overall Accuracy**: {acc * 100:.2f}% ({data.get('correct', 0)}/{total_images})")
    md.append(f"- **Overall False Sky Rate**: {rates.get('false_sky_rate', 0.0) * 100:.2f}%")
    md.append(f"- **Nightmare False Sky Rate**: {rates.get('nightmare_false_sky_rate', 0.0) * 100:.2f}%")
    md.append(f"- **High-Confidence False Sky Predictions (>=0.80)**: {len(high_conf_false)}")

    md.append("\n## 2. Sky Detection Metrics")
    md.append("| Metric | Value | Description |")
    md.append("|---|---|---|")
    md.append(f"| **Precision** | {sky_metrics.get('precision', 0.0):.4f} | Correctly identified sky out of all predicted sky |")
    md.append(f"| **Recall** | {sky_metrics.get('recall', 0.0):.4f} | Correctly identified sky out of all actual sky |")
    md.append(f"| **F1 Score** | {sky_metrics.get('f1_score', 0.0):.4f} | Harmonic mean of precision and recall |")
    md.append(f"| **True Positives (TP)** | {sky_metrics.get('true_positive', 0)} | Genuine sky correctly predicted as sky |")
    md.append(f"| **True Negatives (TN)** | {sky_metrics.get('true_negative', 0)} | Non-sky correctly rejected |")
    md.append(f"| **False Positives (FP)** | {sky_metrics.get('false_positive', 0)} | Non-sky misclassified as sky (FALSE SKY) |")
    md.append(f"| **False Negatives (FN)** | {sky_metrics.get('false_negative', 0)} | Genuine sky incorrectly rejected |")

    md.append("\n## 3. Hard-Negative & Nightmare Rejection Performance")
    md.append("| Category | Total Samples | Misclassified as Sky | False Sky Rate | Rejection Rate |")
    md.append("|---|---|---|---|---|")
    for cat_name, stats in cat_breakdown.items():
        tot = stats.get("total", 0)
        fp = stats.get("false_sky_count", 0)
        fs_rate = stats.get("false_sky_rate", 0.0) * 100
        rej_rate = stats.get("rejection_rate", 0.0) * 100
        md.append(f"| **{cat_name.title()}** | {tot} | {fp} | {fs_rate:.1f}% | **{rej_rate:.1f}%** |")

    md.append("\n## 4. Key Failure Analysis")
    md.append(f"### High-Confidence False Sky Cases ({len(high_conf_false)} instances)")
    if high_conf_false:
        for item in high_conf_false[:10]:
            md.append(f"- **Image**: {item.get('filename')}")
            md.append(f"  - Ground Truth: {item.get('ground_truth', {}).get('scene_type')}")
            md.append(f"  - Predicted: sky_detected={item.get('predicted_sky_detected')}, confidence={item.get('predicted_confidence')}")
            md.append(f"  - Model Reason: *\"{item.get('model_reason')}\"*")
    else:
        md.append("- No high-confidence false sky errors recorded.")

    md.append(f"\n### False Negative Sky Cases ({len(false_negatives)} instances)")
    if false_negatives:
        for item in false_negatives[:10]:
            md.append(f"- **Image**: {item.get('filename')}")
            md.append(f"  - Ground Truth: {item.get('ground_truth', {}).get('cloud_condition')}")
            md.append(f"  - Model Reason: *\"{item.get('model_reason')}\"*")
    else:
        md.append("- No genuine sky rejections recorded.")

    md.append("\n## 5. Confidence Bucketing Analysis")
    md.append("Distribution of model confidence scores across genuine sky vs. non-sky images:")
    md.append("\n`")
    md.append("Bucket      Genuine Sky    Non-Sky")
    for b in sorted(list(set(list(conf_dist.get("genuine_sky", {}).keys()) + list(conf_dist.get("non_sky", {}).keys())))):
        s_count = conf_dist.get("genuine_sky", {}).get(b, 0)
        ns_count = conf_dist.get("non_sky", {}).get(b, 0)
        md.append(f"{b.ljust(11)} {str(s_count).ljust(14)} {str(ns_count)}")
    md.append("`")

    md.append("\n## 6. Recommendations for Future Fine-Tuning")
    md.append("1. **Fabric & Bedsheet Distillation**: Introduce contrasting pairs of high-thread count wrinkled bedsheets vs. overcast stratocumulus clouds in training.")
    md.append("2. **Negative Weighting**: Add higher loss penalty for false positives on indoor surfaces compared to slight cloud condition misclassifications.")
    md.append("3. **Direct Scene Head**: Ensure the model predicts scene_type prior to computing sky_detected to force geometric and textural reasoning before probabilistic weather assignment.")

    with open(REPORT_MD, "w", encoding="utf-8") as f:
        f.write("\n".join(md) + "\n")

    print(f"Generated Markdown report at: {REPORT_MD}")

if __name__ == "__main__":
    main()
