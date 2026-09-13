import pytest
from fastapi.testclient import TestClient
from service.main import app

client = TestClient(app)

def test_health():
    res = client.get("/health")
    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "ok"
    assert data["model_version"] == "SKY-LORA-002"

def test_ready():
    res = client.get("/ready")
    assert res.status_code == 200
    data = res.json()
    assert "ready" in data
