import json
import re
from service.schemas import SkyAnalysisResponse
from service.config import settings

def extract_and_validate_json(raw_text: str, request_id: str) -> SkyAnalysisResponse:
    """
    Extracts raw JSON from model text, cleans markdown backticks,
    applies conservative confidence thresholding, and validates against Pydantic schema.
    """
    cleaned = raw_text.strip()
    # Find outermost JSON object
    match = re.search(r"\{[\s\S]*\}", cleaned)
    if match:
        cleaned = match.group(0)
        
    try:
        data = json.loads(cleaned)
    except json.JSONDecodeError as err:
        raise ValueError(f"Could not parse valid JSON from model response: {raw_text[:200]}") from err
        
    # Conservative Sky Confidence Gate
    raw_sky_detected = bool(data.get("sky_detected", False))
    raw_confidence = float(data.get("sky_confidence", 0.0))
    
    # If model claimed sky but confidence is below production threshold, conservative gate rejects it
    if raw_sky_detected and raw_confidence < settings.sky_confidence_threshold:
        data["sky_detected"] = False
        data["scene_type"] = "unknown"
        data["cloud_condition"] = None
        data["cloud_coverage"] = None
        data["visible_precipitation"] = False
        
    data["request_id"] = request_id
    data["model_version"] = settings.model_version
    
    validated = SkyAnalysisResponse.model_validate(data)
    return validated
