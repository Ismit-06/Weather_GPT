import os
import json
import base64
import mimetypes
from pathlib import Path
from typing import Optional, Literal
from pydantic import BaseModel, Field, field_validator, model_validator
import requests
from dotenv import load_dotenv

# Load environment variables (.env from current directory or parent)
load_dotenv()
load_dotenv(dotenv_path=Path(__file__).resolve().parent.parent / ".env")

OPENROUTER_API_KEY = os.getenv("OPENROUTER_API_KEY", "")
OPENROUTER_MODEL = os.getenv("OPENROUTER_MODEL", "google/gemma-4-26b-a4b-it:free")
OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1/chat/completions"

# Allowed schema values
AllowedSceneType = Literal[
    "outdoor_sky",
    "fabric",
    "bedsheet",
    "blanket",
    "ceiling",
    "wall",
    "curtain",
    "indoor_surface",
    "window_reflection",
    "screen_or_photo",
    "building",
    "vegetation",
    "ground",
    "unknown"
]

AllowedCloudCondition = Literal[
    "clear",
    "partly_cloudy",
    "mostly_cloudy",
    "overcast",
    "storm_clouds",
    "haze",
    "fog",
    "not_applicable",
    "unknown"
]

AllowedObstruction = Literal[
    "none",
    "trees",
    "buildings",
    "window_frame",
    "poles_wires",
    "overhang",
    "partial_roof",
    "other"
]

AllowedImageQuality = Literal[
    "good",
    "blurry",
    "overexposed",
    "underexposed",
    "glare",
    "low_resolution"
]

class SkyAnalysisResponse(BaseModel):
    sky_detected: bool = Field(..., description="True ONLY if genuine natural outdoor sky is directly observed.")
    sky_confidence: float = Field(..., ge=0.0, le=1.0, description="Confidence score between 0.0 and 1.0.")
    scene_type: str = Field(..., description="Identified scene category.")
    cloud_condition: str = Field(..., description="Observed cloud condition.")
    cloud_coverage: Optional[float] = Field(None, ge=0.0, le=1.0, description="Cloud coverage between 0.0 and 1.0 (null if no sky).")
    visible_precipitation: bool = Field(False, description="Whether rain/snow/precipitation is visibly falling.")
    horizon_visible: bool = Field(False, description="Whether true natural horizon is visible.")
    obstruction: str = Field("none", description="Obstructions in view.")
    image_quality: str = Field("good", description="Quality of the image.")
    reason: str = Field(..., description="Detailed explanation of visual findings.")

    @model_validator(mode="after")
    def validate_sky_consistency(self):
        # Strict validation rule: if sky is not detected, cloud_condition MUST be 'not_applicable' and cloud_coverage null
        if not self.sky_detected:
            if self.cloud_condition not in ["not_applicable", "unknown"]:
                self.cloud_condition = "not_applicable"
            self.cloud_coverage = None
        return self

SKY_AI_SYSTEM_PROMPT = """You are Sky AI, an expert computer-vision and atmospheric perception system within WeatherGPT.

CRITICAL OBJECTIVE:
Your primary and most vital task is to determine whether GENUINE, NATURAL OUTDOOR SKY is actually visible in the provided image.
This is strictly a VISUAL PERCEPTION task, NOT a general weather forecasting task or speculative guessing.
You must NEVER assume that blue, white, grey, or cloud-like textures are sky.

EXPLICIT FALSE POSITIVE WARNINGS (DO NOT BE FOOLED):
You will frequently encounter deceptive indoor and man-made surfaces. The following MUST NOT be identified as outdoor sky:
- Bedsheets, blankets, quilts, bedspreads, or fabrics (even if light blue, cloudy, white, or wrinkly)
- Ceilings (including acoustic tiles, white plaster, drywall, ceiling fans, light fixtures, recessed lights)
- Walls, wallpapers, curtains, painted surfaces
- Indoor surfaces, table tops, carpets, tiles, floors
- Window reflections showing indoor interiors or specular glare
- Photographs of the sky, posters, or art prints
- Computer monitors, TV screens, or phone screens displaying sky or wallpaper images

STRICT DECISION RULES:
1. If genuine outdoor sky cannot be established with sufficient confidence:
   sky_detected MUST be false.
2. If sky_detected is false:
   - cloud_condition MUST be "not_applicable"
   - cloud_coverage MUST be null
   - visible_precipitation MUST be false unless there is actual visible precipitation unrelated to sky
   - horizon_visible should reflect the image only
   - scene_type must be identified accurately from the allowed list:
     [outdoor_sky, fabric, bedsheet, blanket, ceiling, wall, curtain, indoor_surface, window_reflection, screen_or_photo, building, vegetation, ground, unknown]
3. If genuine outdoor sky IS clearly visible:
   - sky_detected = true
   - scene_type = "outdoor_sky"
   - cloud_condition must be chosen from:
     [clear, partly_cloudy, mostly_cloudy, overcast, storm_clouds, haze, fog, unknown]
   - cloud_coverage must be a float between 0.0 and 1.0
4. You must not invent meteorological information.

OUTPUT SCHEMA:
Respond with a strict raw JSON object with these exact keys:
{
  "sky_detected": boolean,
  "sky_confidence": float (0.0 to 1.0),
  "scene_type": string,
  "cloud_condition": string,
  "cloud_coverage": float or null,
  "visible_precipitation": boolean,
  "horizon_visible": boolean,
  "obstruction": "none" | "trees" | "buildings" | "window_frame" | "poles_wires" | "overhang" | "partial_roof" | "other",
  "image_quality": "good" | "blurry" | "overexposed" | "underexposed" | "glare" | "low_resolution",
  "reason": string
}
Do not include any explanation or markdown tags outside the JSON. Return ONLY the JSON object.
"""

def encode_image_to_data_url(image_path: str | Path) -> str:
    """Converts a local image file to a base64 data URL."""
    path = Path(image_path)
    if not path.exists():
        raise FileNotFoundError(f"Image file not found: {path}")

    mime_type, _ = mimetypes.guess_type(str(path))
    if not mime_type:
        ext = path.suffix.lower()
        if ext in [".jpg", ".jpeg"]:
            mime_type = "image/jpeg"
        elif ext == ".png":
            mime_type = "image/png"
        elif ext == ".webp":
            mime_type = "image/webp"
        else:
            mime_type = "application/octet-stream"

    with open(path, "rb") as f:
        encoded_data = base64.b64encode(f.read()).decode("utf-8")

    return f"data:{mime_type};base64,{encoded_data}"

def analyze_sky_image(
    image_path: str | Path,
    api_key: Optional[str] = None,
    model: Optional[str] = None,
    timeout: int = 60
) -> SkyAnalysisResponse:
    """
    Sends an image to Google Gemma 4 26B A4B through OpenRouter and validates
    the structured Sky AI perception response.
    """
    key = api_key or os.getenv("OPENROUTER_API_KEY", "")
    if not key or key == "your_openrouter_api_key_here":
        raise ValueError(
            "OPENROUTER_API_KEY is not set. Please set it in your environment or sky-ai/.env"
        )

    chosen_model = model or os.getenv("OPENROUTER_MODEL", "google/gemma-4-26b-a4b-it:free")
    data_url = encode_image_to_data_url(image_path)

    headers = {
        "Authorization": f"Bearer {key}",
        "Content-Type": "application/json",
        "HTTP-Referer": os.getenv("OPENROUTER_SITE_URL", "https://github.com/WeatherGPT"),
        "X-Title": os.getenv("OPENROUTER_APP_NAME", "WeatherGPT Sky AI"),
    }

    payload = {
        "model": chosen_model,
        "temperature": 0.1,
        "messages": [
            {
                "role": "system",
                "content": SKY_AI_SYSTEM_PROMPT
            },
            {
                "role": "user",
                "content": [
                    {
                        "type": "text",
                        "text": "Analyze this image according to the Sky AI perception rules and output strict JSON."
                    },
                    {
                        "type": "image_url",
                        "image_url": {
                            "url": data_url
                        }
                    }
                ]
            }
        ]
    }

    max_retries = 3
    backoff_delay = 5
    response = None

    for attempt in range(1, max_retries + 1):
        try:
            response = requests.post(
                OPENROUTER_BASE_URL,
                headers=headers,
                json=payload,
                timeout=timeout
            )
            if response.status_code == 429 and attempt < max_retries:
                print(f"\n[RATE LIMIT 429] OpenRouter free pool busy. Waiting {backoff_delay}s (attempt {attempt}/{max_retries})...")
                import time
                time.sleep(backoff_delay)
                backoff_delay *= 2
                continue
            break
        except requests.exceptions.RequestException as e:
            if attempt < max_retries:
                import time
                time.sleep(backoff_delay)
                continue
            raise e

    if response is None or response.status_code != 200:
        err_msg = response.text if response is not None else "No response received"
        status_code = response.status_code if response is not None else "unknown"
        raise RuntimeError(
            f"OpenRouter API error (status {status_code}): {err_msg}"
        )

    res_json = response.json()
    try:
        raw_content = res_json["choices"][0]["message"]["content"]
    except (KeyError, IndexError) as e:
        raise ValueError(f"Unexpected response structure from OpenRouter: {res_json}") from e

    # Clean markdown codeblocks and extract JSON if surrounded by commentary
    cleaned_content = raw_content.strip()
    import re
    json_match = re.search(r"\{[\s\S]*\}", cleaned_content)
    if json_match:
        cleaned_content = json_match.group(0)

    try:
        parsed_json = json.loads(cleaned_content)
    except json.JSONDecodeError as err:
        raise ValueError(f"Failed to parse JSON from model response: {raw_content}") from err

    validated = SkyAnalysisResponse.model_validate(parsed_json)
    return validated
