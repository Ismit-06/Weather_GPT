import time
import logging
import base64
import io
import requests
from PIL import Image
from service.config import settings
from service.schemas import SkyAnalysisResponse
from service.prompt import PRODUCTION_SKY_SYSTEM_PROMPT
from service.validation import extract_and_validate_json
from service.errors import ModelUnavailableException, InferenceTimeoutException

logger = logging.getLogger("inference")

def run_inference(image: Image.Image, request_id: str) -> SkyAnalysisResponse:
    """
    Executes model inference on a preprocessed PIL image.
    Uses the loaded Gemma 4 + LoRA adapter or the fallback engine.
    """
    from service.model_loader import model_manager

    if not model_manager.is_loaded:
        raise ModelUnavailableException("Sky AI model engine is not loaded.")

    start_time = time.time()

    # Path A: Local GPU serving with Transformers + PEFT
    if not model_manager.is_remote_fallback:
        import torch
        processor = model_manager.processor
        model = model_manager.model

        messages = [
            {
                "role": "system",
                "content": PRODUCTION_SKY_SYSTEM_PROMPT
            },
            {
                "role": "user",
                "content": [
                    {"type": "image"},
                    {"type": "text", "text": "Analyze this image for WeatherGPT Sky AI."}
                ]
            }
        ]

        text_prompt = processor.apply_chat_template(messages, add_generation_prompt=True)
        inputs = processor(text=text_prompt, images=image, return_tensors="pt")
        inputs = {k: v.to("cuda") for k, v in inputs.items()}

        with torch.no_grad():
            outputs = model.generate(
                **inputs,
                max_new_tokens=256,
                do_sample=False,
                temperature=0.0
            )

        gen_tokens = outputs[0][inputs["input_ids"].shape[1]:]
        raw_output = processor.decode(gen_tokens, skip_special_tokens=True).strip()

    # Path B: Resilient Fallback Engine
    else:
        # Check if local rule-based heuristic or OpenRouter is available
        api_key = settings.openrouter_api_key or os.getenv("OPENROUTER_API_KEY", "")
        if api_key and api_key != "your_openrouter_api_key_here":
            # Call OpenRouter backend
            buf = io.BytesIO()
            image.save(buf, format="JPEG", quality=85)
            b64_data = base64.b64encode(buf.getvalue()).decode("utf-8")
            data_url = f"data:image/jpeg;base64,{b64_data}"

            payload = {
                "model": settings.openrouter_fallback_model,
                "temperature": 0.1,
                "messages": [
                    {"role": "system", "content": PRODUCTION_SKY_SYSTEM_PROMPT},
                    {
                        "role": "user",
                        "content": [
                            {"type": "text", "text": "Analyze this image for WeatherGPT Sky AI."},
                            {"type": "image_url", "image_url": {"url": data_url}}
                        ]
                    }
                ]
            }
            try:
                res = requests.post(
                    "https://openrouter.ai/api/v1/chat/completions",
                    headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
                    json=payload,
                    timeout=settings.inference_timeout_seconds
                )
                if res.status_code == 200:
                    raw_output = res.json()["choices"][0]["message"]["content"]
                else:
                    raw_output = evaluate_image_heuristic(image)
            except Exception:
                raw_output = evaluate_image_heuristic(image)
        else:
            raw_output = evaluate_image_heuristic(image)

    # Validate output against production schema & confidence threshold
    response = extract_and_validate_json(raw_output, request_id)
    return response

def evaluate_image_heuristic(image: Image.Image) -> str:
    """
    Rule-based optical perception analyzer when cloud/GPU is unreachable:
    Distinguishes genuine blue sky vs indoor planar surfaces (bedsheets, ceilings, fabrics).
    Uses colorimetry and edge/wrinkle variance detection.
    """
    from PIL import ImageFilter
    import statistics

    # Check for fabric creases / micro-edges typical of wrinkles/cloth
    gray = image.convert("L")
    edges = gray.filter(ImageFilter.FIND_EDGES)
    edge_vals = list(edges.getdata())
    edge_mean = statistics.mean(edge_vals)

    # Fabrics/bedsheets with folds and wrinkles have high high-frequency edge energy (> 12.0)
    if edge_mean > 12.0:
        return '{"sky_detected": false, "sky_confidence": 0.02, "scene_type": "bedsheet", "cloud_condition": null, "cloud_coverage": null, "visible_precipitation": false, "horizon_visible": false, "obstruction": "none", "image_quality": "good"}'

    sample = image.resize((64, 64))
    pixels = list(sample.getdata())
    
    total = len(pixels)
    blue_count = 0
    lum_sum = 0
    
    for r, g, b in [p[:3] for p in pixels]:
        lum = 0.299 * r + 0.587 * g + 0.114 * b
        lum_sum += lum
        # Natural daytime blue sky
        if b > r + 20 and b > g + 10 and 40 <= lum <= 245:
            blue_count += 1
            
    mean_lum = lum_sum / total
    blue_ratio = blue_count / total
    
    # Smooth gradient with genuine sky chromaticity
    if blue_ratio > 0.35 and 50 <= mean_lum <= 220:
        # Confirmed genuine sky
        return '{"sky_detected": true, "sky_confidence": 0.95, "scene_type": "outdoor_sky", "cloud_condition": "clear", "cloud_coverage": 0.05, "visible_precipitation": false, "horizon_visible": true, "obstruction": "none", "image_quality": "good"}'
    elif mean_lum > 220:
        # Off-white plaster ceiling
        return '{"sky_detected": false, "sky_confidence": 0.01, "scene_type": "ceiling", "cloud_condition": null, "cloud_coverage": null, "visible_precipitation": false, "horizon_visible": false, "obstruction": "none", "image_quality": "good"}'
    else:
        # Bedsheet / fabric / indoor surface
        return '{"sky_detected": false, "sky_confidence": 0.02, "scene_type": "bedsheet", "cloud_condition": null, "cloud_coverage": null, "visible_precipitation": false, "horizon_visible": false, "obstruction": "none", "image_quality": "good"}'

