import io
from PIL import Image, UnidentifiedImageError

MAX_FILE_SIZE_BYTES = 15 * 1024 * 1024  # 15 MB
MIN_FILE_SIZE_BYTES = 100               # 100 bytes
MIN_DIMENSION_PX = 64
MAX_DIMENSION_PX = 4096
OPTIMAL_MAX_DIMENSION = 1920

ALLOWED_FORMATS = {"JPEG", "JPG", "PNG", "WEBP"}
ALLOWED_MIME_TYPES = {
    "image/jpeg",
    "image/jpg",
    "image/png",
    "image/webp",
}


class ImageValidationError(ValueError):
    """Raised when an uploaded sky image fails validation."""
    pass


def validate_and_prepare_image(
    image_bytes: bytes,
    content_type: str | None = None,
) -> tuple[Image.Image, bytes]:
    """
    Validates uploaded image bytes against format, size, decodability, and dimensions.
    Returns the decoded PIL Image and the normalized/downsampled JPEG bytes for vision model.
    """
    if not image_bytes or len(image_bytes) < MIN_FILE_SIZE_BYTES:
        raise ImageValidationError("Unable to analyze this image. Please upload a clear sky/cloud photograph.")

    if len(image_bytes) > MAX_FILE_SIZE_BYTES:
        raise ImageValidationError("Image file exceeds the maximum allowed size of 15MB.")

    # Validate MIME type if provided
    if content_type:
        normalized_mime = content_type.lower().split(";")[0].strip()
        if normalized_mime not in ALLOWED_MIME_TYPES:
            raise ImageValidationError("Unable to analyze this image. Supported formats are JPEG, PNG, and WebP.")

    # Attempt decoding with Pillow
    try:
        image = Image.open(io.BytesIO(image_bytes))
        image.verify()  # Verify integrity
        # Re-open after verify as per PIL documentation
        image = Image.open(io.BytesIO(image_bytes))
        image.load()    # Force load image data to catch truncation/corruption
    except (UnidentifiedImageError, OSError, SyntaxError, Exception):
        raise ImageValidationError("Unable to analyze this image. Please upload a clear sky/cloud photograph.")

    # Format verification
    img_format = (image.format or "").upper()
    if img_format not in ALLOWED_FORMATS:
        raise ImageValidationError("Unable to analyze this image. Supported formats are JPEG, PNG, and WebP.")

    # Check dimensions
    width, height = image.size
    if width < MIN_DIMENSION_PX or height < MIN_DIMENSION_PX:
        raise ImageValidationError(f"Image dimensions are too small ({width}x{height}). Minimum required is {MIN_DIMENSION_PX}x{MIN_DIMENSION_PX}px.")

    if width > MAX_DIMENSION_PX or height > MAX_DIMENSION_PX:
        raise ImageValidationError(f"Image dimensions exceed maximum allowed limit of {MAX_DIMENSION_PX}px.")

    # Convert to RGB (handles RGBA, P palette, etc.)
    if image.mode != "RGB":
        image = image.convert("RGB")

    # Downscale if larger than optimal dimension to reduce latency and token consumption
    if max(width, height) > OPTIMAL_MAX_DIMENSION:
        image.thumbnail((OPTIMAL_MAX_DIMENSION, OPTIMAL_MAX_DIMENSION), Image.Resampling.LANCZOS)

    buf = io.BytesIO()
    image.save(buf, format="JPEG", quality=85, optimize=True)
    optimized_bytes = buf.getvalue()

    return image, optimized_bytes
