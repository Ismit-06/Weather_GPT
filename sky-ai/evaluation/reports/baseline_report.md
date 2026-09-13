# WeatherGPT Sky AI — Baseline Benchmark Evaluation Report

**Generated**: 2026-09-10 17:06:06  
**Evaluated Model**: google/gemma-4-26b-a4b-it:free  
**Prompt**: Standardized Sky AI Perception Prompt (zero-shot baseline)  

## 1. Executive Summary
- **Total Test Images Evaluated**: 0
- **Overall Accuracy**: 0.00% (0/0)
- **Overall False Sky Rate**: 0.00%
- **Nightmare False Sky Rate**: 0.00%
- **High-Confidence False Sky Predictions (>=0.80)**: 0

## 2. Sky Detection Metrics
| Metric | Value | Description |
|---|---|---|
| **Precision** | 0.0000 | Correctly identified sky out of all predicted sky |
| **Recall** | 0.0000 | Correctly identified sky out of all actual sky |
| **F1 Score** | 0.0000 | Harmonic mean of precision and recall |
| **True Positives (TP)** | 0 | Genuine sky correctly predicted as sky |
| **True Negatives (TN)** | 0 | Non-sky correctly rejected |
| **False Positives (FP)** | 0 | Non-sky misclassified as sky (FALSE SKY) |
| **False Negatives (FN)** | 0 | Genuine sky incorrectly rejected |

## 3. Hard-Negative & Nightmare Rejection Performance
| Category | Total Samples | Misclassified as Sky | False Sky Rate | Rejection Rate |
|---|---|---|---|---|

## 4. Key Failure Analysis
### High-Confidence False Sky Cases (0 instances)
- No high-confidence false sky errors recorded.

### False Negative Sky Cases (0 instances)
- No genuine sky rejections recorded.

## 5. Confidence Bucketing Analysis
Distribution of model confidence scores across genuine sky vs. non-sky images:

`
Bucket      Genuine Sky    Non-Sky
`

## 6. Recommendations for Future Fine-Tuning
1. **Fabric & Bedsheet Distillation**: Introduce contrasting pairs of high-thread count wrinkled bedsheets vs. overcast stratocumulus clouds in training.
2. **Negative Weighting**: Add higher loss penalty for false positives on indoor surfaces compared to slight cloud condition misclassifications.
3. **Direct Scene Head**: Ensure the model predicts scene_type prior to computing sky_detected to force geometric and textural reasoning before probabilistic weather assignment.
