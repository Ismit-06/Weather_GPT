import base64
import json
import logging
import os
import re
from pathlib import Path
import httpx
from dotenv import load_dotenv

from app.schemas.visual_cloud import (
    CloudObservation,
    CloudType,
    CloudThickness,
    VerticalDevelopment,
    ApparentCloudBase,
    PrecipitationAppearance,
    CloudDevelopmentTrend,
)

load_dotenv(
    dotenv_path=Path(__file__).resolve().parents[2] / ".env",
    override=True,
)

logger = logging.getLogger("visual_cloud_analyzer")

OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"
DEFAULT_VISION_MODEL = "google/gemma-4-26b-a4b-it"
FALLBACK_VISION_MODELS = [
    "google/gemma-4-26b-a4b-it",
    "qwen/qwen-2.5-vl-72b-instruct",
    "meta-llama/llama-3.2-11b-vision-instruct",
]

CLOUD_ANALYSIS_SYSTEM_PROMPT = """You are an expert meteorological computer vision system performing visual cloud intelligence for WeatherGPT.

PRIMARY OBJECTIVE:
Analyze the visible cloud patterns in the sky photograph and return a structured, scientifically cautious JSON observation.

STRICT METHODOLOGICAL PRINCIPLES:
1. REPORT ONLY WHAT IS VISUALLY IDENTIFIABLE:
   - Clearly distinguish VISUAL OBSERVATION from METEOROLOGICAL INTERPRETATION.
   - Do NOT invent or estimate exact numerical altitudes (e.g. NEVER output "cloud_base: 850m").
   - Do NOT invent numerical temperatures, wind vectors, pressure, or precipitation amounts.
   - Do NOT claim certainty about future weather or predict exact rain arrival times.
   - A single photograph CANNOT establish cloud movement speed, direction, or development trends over time. Always set "development_trend" to "unknown" for a single image.

2. CLASSIFICATION VOCABULARY:
   - dominant_cloud_type: One of ["clear", "cumulus", "towering_cumulus", "stratocumulus", "stratus", "altocumulus", "altostratus", "cirrus", "cirrostratus", "cirrocumulus", "nimbostratus", "cumulonimbus", "mixed", "unknown"]
   - secondary_cloud_type: One of the above or null.
   - cloud_coverage: Float from 0.0 (clear) to 1.0 (completely overcast), or null if indeterminate.
   - cloud_thickness: One of ["thin", "moderate", "thick", "very_thick", "unknown"]
   - vertical_development: One of ["none", "low", "moderate", "strong", "extreme", "unknown"] (towering cumulus and cumulonimbus exhibit strong/extreme development).
   - apparent_cloud_base: One of ["high", "medium", "low", "very_low", "unknown"].
   - convective_appearance: Boolean true/false or "unknown" (true if cauliflower structure, towering cumulus, or anvil head visible).
   - precipitation_appearance: One of ["none_visible", "possible", "likely_visible", "unknown"] (refers strictly to visual virga, rain shafts, or dark precipitation curtains).
   - development_trend: Must be "unknown" for single photographs.
   - confidence: Float between 0.0 and 1.0 reflecting how clear the visual cloud evidence is.
   - limitations: List of brief textual limitations if view is obstructed, obscured, indoors, or ambiguous.

3. MANDATORY OUTPUT FORMAT:
Return ONLY valid JSON matching this schema with NO markdown code fences, NO introductory conversational text, and NO trailing explanation:
{
  "analysis_status": "success",
  "dominant_cloud_type": "towering_cumulus",
  "secondary_cloud_type": null,
  "cloud_coverage": 0.65,
  "cloud_thickness": "thick",
  "vertical_development": "strong",
  "apparent_cloud_base": "low",
  "convective_appearance": true,
  "precipitation_appearance": "possible",
  "development_trend": "unknown",
  "confidence": 0.85,
  "limitations": []
}

If the image is not a sky or cannot be reliably classified, return:
{
  "analysis_status": "uncertain",
  "dominant_cloud_type": "unknown",
  "secondary_cloud_type": null,
  "cloud_coverage": null,
  "cloud_thickness": "unknown",
  "vertical_development": "unknown",
  "apparent_cloud_base": "unknown",
  "convective_appearance": "unknown",
  "precipitation_appearance": "unknown",
  "development_trend": "unknown",
  "confidence": 0.2,
  "limitations": ["Cloud structure is ambiguous or obscured."]
}
"""


def extract_json_from_text(text: str) -> dict:
    """Extracts and parses JSON from raw model output, stripping markdown formatting if present."""
    cleaned = text.strip()
    # Remove markdown code blocks if wrapped
    if cleaned.startswith("```"):
        cleaned = re.sub(r"^```(?:json)?\s*", "", cleaned)
        cleaned = re.sub(r"\s*```$", "", cleaned)
        cleaned = cleaned.strip()

    # Search for matching outer braces if extra text exists
    match = re.search(r"\{[\s\S]*\}", cleaned)
    if match:
        cleaned = match.group(0)

    return json.loads(cleaned)


async def analyze_sky_image(
    image_bytes: bytes,
    api_key: str | None = None,
    timeout_seconds: float = 14.0,
) -> CloudObservation:
    """
    Submits the sky photograph to OpenRouter Gemma 4 26B A4B (or configured vision model)
    and validates the structured observation against CloudObservation schema.
    """
    resolved_key = api_key or os.getenv("OPENROUTER_API_KEY", "").strip()
    if not resolved_key:
        logger.warning("OPENROUTER_API_KEY is not configured; returning uncertain observation.")
        return CloudObservation(
            analysis_status="uncertain",
            dominant_cloud_type=CloudType.unknown,
            confidence=0.3,
            limitations=["OpenRouter vision API key is not configured."],
        )

    # Encode to base64 data URL
    b64_img = base64.b64encode(image_bytes).decode("utf-8")
    data_url = f"data:image/jpeg;base64,{b64_img}"

    configured_model = os.getenv("OPENROUTER_VISION_MODEL", "").strip() or DEFAULT_VISION_MODEL
    candidate_models = [configured_model]
    for fm in FALLBACK_VISION_MODELS:
        if fm not in candidate_models:
            candidate_models.append(fm)

    headers = {
        "Authorization": f"Bearer {resolved_key}",
        "HTTP-Referer": "https://weathergpt.app",
        "X-Title": "WeatherGPT Visual Cloud Intelligence",
        "Content-Type": "application/json",
    }

    last_error = None
    for model in candidate_models:
        payload = {
            "model": model,
            "temperature": 0.1,
            "max_tokens": 400,
            "messages": [
                {
                    "role": "system",
                    "content": CLOUD_ANALYSIS_SYSTEM_PROMPT,
                },
                {
                    "role": "user",
                    "content": [
                        {
                            "type": "text",
                            "text": "Analyze the cloud pattern in this sky image and output structured JSON.",
                        },
                        {
                            "type": "image_url",
                            "image_url": {"url": data_url},
                        },
                    ],
                },
            ],
        }

        try:
            async with httpx.AsyncClient(timeout=timeout_seconds) as client:
                resp = await client.post(
                    OPENROUTER_URL,
                    headers=headers,
                    json=payload,
                )

                if resp.status_code != 200:
                    last_error = f"Model {model} returned HTTP {resp.status_code}: {resp.text[:150]}"
                    logger.warning(last_error)
                    continue

                res_json = resp.json()
                choices = res_json.get("choices", [])
                if not choices:
                    last_error = f"Model {model} returned empty choices."
                    continue

                raw_content = choices[0].get("message", {}).get("content", "")
                if not raw_content.strip():
                    last_error = f"Model {model} returned blank content."
                    continue

                parsed = extract_json_from_text(raw_content)

                # Ensure development_trend is forced to unknown for single image
                parsed["development_trend"] = "unknown"

                # Validate with Pydantic
                observation = CloudObservation.model_validate(parsed)
                return observation

        except Exception as exc:
            last_error = f"Exception with model {model}: {exc}"
            logger.warning(last_error)
            continue

    logger.error(f"All vision models failed. Last error: {last_error}")
    return CloudObservation(
        analysis_status="uncertain",
        dominant_cloud_type=CloudType.unknown,
        confidence=0.25,
        limitations=[f"Vision model analysis failed: {last_error or 'Network timeout'}"],
    )
