import io
import pytest
from PIL import Image
from service.preprocessing import preprocess_image
from service.errors import InvalidImageException, ImageTooLargeException, UnprocessableImageException

def test_preprocess_valid_image():
    img = Image.new("RGB", (256, 256), color=(100, 150, 200))
    buf = io.BytesIO()
    img.save(buf, format="JPEG")
    processed = preprocess_image(buf.getvalue())
    assert processed.size == (256, 256)
    assert processed.mode == "RGB"

def test_preprocess_corrupted_image():
    with pytest.raises(InvalidImageException):
        preprocess_image(b"not_an_image_random_bytes_corrupted")

def test_preprocess_empty_image():
    with pytest.raises(InvalidImageException):
        preprocess_image(b"")

def test_preprocess_too_small():
    img = Image.new("RGB", (32, 32), color=(100, 150, 200))
    buf = io.BytesIO()
    img.save(buf, format="JPEG")
    with pytest.raises(UnprocessableImageException):
        preprocess_image(buf.getvalue())
