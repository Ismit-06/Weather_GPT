import os
import httpx

WEATHER_AI_API_URL = os.getenv("LOCAL_LLM_URL", "http://127.0.0.1:8001/api/chat/generate")


def format_weather_prompt(question: str, weather_context: str | dict | None = None) -> str:
    """
    Formats question and context into the syntax expected by the WeatherAI model:
    User: <question> [Weather: ...]
    """
    clean_q = question.strip()
    
    # If weather_context is provided and not already in question, append compact weather line
    context_str = ""
    if isinstance(weather_context, dict):
        current = weather_context.get("current", {})
        temp = current.get("temperature_c")
        humidity = current.get("humidity_pct") or current.get("relative_humidity_2m")
        rain = current.get("precipitation_probability") or current.get("rain_probability") or 0
        wind = current.get("wind_speed_kmh") or current.get("wind_speed_10m")
        
        parts = []
        if temp is not None:
            parts.append(f"Temperature: {int(round(float(temp)))} degrees Celsius.")
        if humidity is not None:
            parts.append(f"Humidity: {int(round(float(humidity)))} percent.")
        if rain is not None:
            parts.append(f"Rain probability: {int(round(float(rain)))} percent.")
        if wind is not None:
            parts.append(f"Wind speed: {int(round(float(wind)))} kilometers per hour.")
            
        if parts:
            context_str = " Weather: " + " ".join(parts)
    elif isinstance(weather_context, str) and weather_context.strip():
        context_str = f" Context: {weather_context.strip()}"
        
    return f"{clean_q}{context_str}"


async def generate_local_chat(
    question: str,
    weather_context: str | dict | None = None,
    timeout: float = 6.0,
) -> str | None:
    """
    Invokes the local WeatherAI LLM service over HTTP.
    Returns None if the local service is offline or fails, enabling clean fallback.
    """
    prompt = format_weather_prompt(question, weather_context)
    
    url = os.getenv("LOCAL_LLM_URL", "http://127.0.0.1:8001/api/chat/generate")
    try:
        async with httpx.AsyncClient(timeout=timeout) as client:
            resp = await client.post(
                url,
                json={
                    "prompt": prompt,
                    "max_new_tokens": 64,
                    "temperature": 0.4,
                    "top_k": 5,
                },
            )
            if resp.status_code == 200:
                data = resp.json()
                reply = data.get("response", "").strip()
                if reply:
                    return reply
    except Exception:
        # Service unreachable or timeout -> fallback to OpenRouter / Sarvam
        return None
    return None
