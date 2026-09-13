import logging
import base64
import io
from pathlib import Path
from PIL import Image
from service.config import settings
from service.errors import ModelUnavailableException

logger = logging.getLogger("model_loader")

class ModelManager:
    _instance = None
    
    def __init__(self):
        self.model = None
        self.processor = None
        self.is_loaded = False
        self.is_remote_fallback = False

    @classmethod
    def get_instance(cls):
        if cls._instance is None:
            cls._instance = ModelManager()
        return cls._instance

    def load(self):
        """
        Loads the Gemma 4 Multimodal model and selected LoRA adapter ONCE at startup.
        If local CUDA/ML framework is unavailable, prepares the fallback inference bridge.
        """
        if self.is_loaded:
            return

        logger.info(f"Initializing Sky AI Model Engine (Target: {settings.model_id}, Adapter: {settings.adapter_path})...")
        
        try:
            import torch
            from transformers import AutoProcessor, AutoModelForImageTextToText
            from peft import PeftModel

            if not torch.cuda.is_available():
                raise RuntimeError("CUDA GPU unavailable for local 26B model inference.")

            vram = torch.cuda.get_device_properties(0).total_memory / (1024**3)
            if vram < 14.0:
                raise RuntimeError(f"Detected GPU has {vram:.1f}GB VRAM (minimum 14GB-24GB required for Gemma 4 26B).")

            logger.info("Loading AutoProcessor...")
            self.processor = AutoProcessor.from_pretrained(settings.model_id, trust_remote_code=True)
            
            logger.info("Loading Base Gemma 4 weights in 4-bit/bfloat16...")
            dtype = torch.bfloat16 if torch.cuda.is_bf16_supported() else torch.float32
            base_model = AutoModelForImageTextToText.from_pretrained(
                settings.model_id,
                torch_dtype=dtype,
                device_map="auto",
                trust_remote_code=True
            )

            # Load LoRA Adapter if present
            adapter_dir = Path(settings.adapter_path)
            if adapter_dir.exists() and (adapter_dir / "adapter_config.json").exists():
                logger.info(f"Mounting LoRA adapter from {settings.adapter_path}...")
                self.model = PeftModel.from_pretrained(base_model, str(adapter_dir))
            else:
                logger.info("No external LoRA adapter found on disk; running base model checkpoint.")
                self.model = base_model

            self.model.eval()
            self.is_loaded = True
            logger.info("Model Engine loaded and ready for serving.")

        except Exception as e:
            logger.warning(f"Local GPU loader bypassed ({e}). Configuring inference engine.")
            self.is_loaded = True
            self.is_remote_fallback = True

model_manager = ModelManager.get_instance()
