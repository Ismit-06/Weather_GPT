import pytest
from pathlib import Path
from fastapi.testclient import TestClient
from service.main import app
from service.model_loader import model_manager

model_manager.load()
client = TestClient(app)

def test_known_bedsheet_failure_rejection():
    bedsheet_path = Path("dataset/nightmare/bedsheet/known_bedsheet_failure_001.png")
    if not bedsheet_path.exists():
        pytest.skip("Known bedsheet failure image not present in nightmare dataset.")

    with open(bedsheet_path, "rb") as f:
        file_bytes = f.read()

    res = client.post(
        "/api/sky/analyze",
        files={"image": ("known_bedsheet_failure_001.png", file_bytes, "image/png")}
    )
    assert res.status_code == 200
    data = res.json()
    # CRITICAL ACCEPTANCE TEST: Must reject bedsheet as sky
    assert data["sky_detected"] is False, f"Regression detected! Bedsheet was classified as sky: {data}"
    assert data["cloud_condition"] is None
    assert data["cloud_coverage"] is None
