import io
import pytest
from PIL import Image
from app.services.image_validator import (
    validate_and_prepare_image,
    ImageValidationError,
    MAX_FILE_SIZE_BYTES,
)


def create_dummy_image(format="JPEG", size=(200, 200), color=(100, 150, 220)) -> bytes:
    buf = io.BytesIO()
    img = Image.new("RGB", size, color=color)
    img.save(buf, format=format)
    return buf.getvalue()


def test_valid_jpeg_image():
    img_bytes = create_dummy_image("JPEG")
    pil_img, opt_bytes = validate_and_prepare_image(img_bytes, content_type="image/jpeg")
    assert pil_img is not None
    assert len(opt_bytes) > 0
    assert pil_img.size == (200, 200)


def test_valid_png_image():
    img_bytes = create_dummy_image("PNG")
    pil_img, opt_bytes = validate_and_prepare_image(img_bytes, content_type="image/png")
    assert pil_img is not None
    assert len(opt_bytes) > 0


def test_invalid_file_type():
    with pytest.raises(ImageValidationError) as excinfo:
        validate_and_prepare_image(b"This is just a text file.", content_type="text/plain")
    assert "Unable to analyze this image" in str(excinfo.value)


def test_empty_file():
    with pytest.raises(ImageValidationError):
        validate_and_prepare_image(b"", content_type="image/jpeg")


def test_corrupted_image():
    # JPEG header followed by garbage
    corrupted = b"\xff\xd8\xff\xe0" + b"\x00" * 200
    with pytest.raises(ImageValidationError):
        validate_and_prepare_image(corrupted, content_type="image/jpeg")


def test_oversized_file():
    oversized = b"a" * (MAX_FILE_SIZE_BYTES + 1024)
    with pytest.raises(ImageValidationError) as excinfo:
        validate_and_prepare_image(oversized, content_type="image/jpeg")
    assert "exceeds the maximum allowed size" in str(excinfo.value)


def test_too_small_dimensions():
    tiny = create_dummy_image("JPEG", size=(32, 32))
    with pytest.raises(ImageValidationError) as excinfo:
        validate_and_prepare_image(tiny, content_type="image/jpeg")
    assert "too small" in str(excinfo.value)


def test_downscale_large_image():
    large = create_dummy_image("JPEG", size=(2500, 1500))
    pil_img, opt_bytes = validate_and_prepare_image(large, content_type="image/jpeg")
    assert max(pil_img.size) <= 1920
