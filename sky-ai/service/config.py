import os
from pathlib import Path
from pydantic_settings import BaseSettings

class ServiceSettings(BaseSettings):
    # Model configuration
    model_id: str = "google/gemma-4-26B-A4B-it"
    adapter_path: str = "checkpoints/SKY-LORA-002"
    model_version: str = "SKY-LORA-002"
    
    # Vision & Token Configuration
    image_token_budget: int = 384
    sky_confidence_threshold: float = 0.80
    
    # Image constraints
    max_image_size_mb: int = 15
    max_image_width: int = 4096
    max_image_height: int = 4096
    min_image_dim: int = 64
    
    # Service settings
    host: str = "0.0.0.0"
    port: int = 8000
    log_level: str = "INFO"
    inference_timeout_seconds: int = 30
    save_debug_images: bool = False
    environment: str = "production"  # development, staging, production
    rate_limit_per_minute: int = 60
    max_concurrent_requests: int = 10
    
    # Optional remote inference fallback (e.g. OpenRouter/vLLM backend if local GPU unavailable)
    openrouter_api_key: str = ""
    openrouter_fallback_model: str = "google/gemma-4-26b-a4b-it:free"

    class Config:
        env_file = ".env"
        extra = "ignore"

settings = ServiceSettings()

