import io
import time
from PIL import Image, ImageOps
from service.config import settings
from service.errors import InvalidImageException, ImageTooLargeException, UnprocessableImageException

def preprocess_image(image_bytes: bytes) -> Image.Image:
    """
    Deterministic, production-grade image preprocessing:
    1. Size verification
    2. Format & decoding validation
    3. EXIF orientation correction
    4. RGB color space conversion
    5. Aspect ratio & dimension bounds enforcement
    """
    # Check byte size
    max_bytes = settings.max_image_size_mb * 1024 * 1024
    if len(image_bytes) > max_bytes:
        raise ImageTooLargeException(f"Image size ({len(image_bytes)/(1024*1024):.2f}MB) exceeds limit ({settings.max_image_size_mb}MB).")

    if not image_bytes:
        raise InvalidImageException("Uploaded image payload is empty.")

    try:
        image = Image.open(io.BytesIO(image_bytes))
        image.verify()
    except Exception as e:
        raise InvalidImageException(f"Image could not be verified or is corrupted: {e}")

    try:
        # Re-open for actual processing after verify()
        image = Image.open(io.BytesIO(image_bytes))
        
        # EXIF orientation handling (vital for mobile phone captures)
        image = ImageOps.exif_transpose(image)
        
        # Convert to RGB (dropping alpha channel if PNG/WebP)
        if image.mode != "RGB":
            image = image.convert("RGB")
            
        w, h = image.size
        if w < settings.min_image_dim or h < settings.min_image_dim:
            raise UnprocessableImageException(f"Image dimensions ({w}x{h}) are smaller than minimum allowed ({settings.min_image_dim}x{settings.min_image_dim}).")
            
        if w > settings.max_image_width or h > settings.max_image_height:
            # Scale down proportionally to preserve cloud details without exceeding model limit
            image.thumbnail((settings.max_image_width, settings.max_image_height), Image.Resampling.LANCZOS)
            
        # Check extreme aspect ratios (e.g. panoramic strips > 1:8)
        aspect = max(w, h) / max(min(w, h), 1)
        if aspect > 8.0:
            raise UnprocessableImageException(f"Extreme aspect ratio ({aspect:.1f}:1) is unsupported for sky observation.")
            
        return image
    except (InvalidImageException, ImageTooLargeException, UnprocessableImageException):
        raise
    except Exception as e:
        raise InvalidImageException(f"Failed to process image: {e}")
