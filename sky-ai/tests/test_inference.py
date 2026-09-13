import io
import pytest
from pathlib import Path
from PIL import Image
from fastapi.testclient import TestClient
from service.main import app
from service.model_loader import model_manager

model_manager.load()
client = TestClient(app)

def test_analyze_valid_sky():
    img = Image.new("RGB", (256, 256), color=(80, 160, 240)) # Clear blue sky color
    buf = io.BytesIO()
    img.save(buf, format="JPEG")
    buf.seek(0)
    
    res = client.post(
        "/api/sky/analyze",
        files={"image": ("test_sky.jpg", buf, "image/jpeg")}
    )
    assert res.status_code == 200
    data = res.json()
    assert "request_id" in data
    assert data["sky_detected"] is True
    assert data["scene_type"] == "outdoor_sky"

def test_analyze_invalid_file():
    res = client.post(
        "/api/sky/analyze",
        files={"image": ("bad.txt", io.BytesIO(b"hello world"), "text/plain")}
    )
    assert res.status_code in [400, 422]
    data = res.json()
    assert "error" in data
