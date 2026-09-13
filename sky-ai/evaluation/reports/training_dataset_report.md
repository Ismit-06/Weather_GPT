# WeatherGPT Sky AI — Training Dataset & Validation Report

## 1. Split Distribution & Class Balance
- **Locked Evaluation Images**: 4 (Completely isolated)
- **Training Examples**: 63
  - Genuine Sky: 27
  - Hard Negatives: 36
- **Validation Examples**: 18
  - Genuine Sky: 9
  - Hard Negatives: 9
- **Split Ratio**: ~80% train / 20% validation

## 2. Hard-Negative Emphasis
To counter the baseline failure of confusing fabrics/ceilings for overcast sky, hard negatives constitute >55% of the fine-tuning pool:
- **Bedsheets, Blankets & Fabrics**
- **Plaster Ceilings & Recessed Lights**
- **Painted Walls & Pleated Curtains**
- **Window & Specular Glare Reflections**
- **Screens & Sky Photographs**

## 3. Data Leakage & Contamination Safeguards
- **Test Contamination Violations**: 0
- **Unreadable Image Files**: 0
- **Schema Integrity**: 100% compliant with Pydantic perception contract
