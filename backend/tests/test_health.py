import pytest
from fastapi.testclient import TestClient

from app.main import app


@pytest.fixture()
def client():
    # Context manager form so FastAPI's lifespan (init_db + default agent
    # seeding) actually runs before requests hit the app.
    with TestClient(app) as c:
        yield c


def test_health(client):
    resp = client.get("/health")
    assert resp.status_code == 200
    assert resp.json() == {"ok": True}


def test_status_without_tokens_configured(client):
    # DEVICE_TOKENS empty in test env -> auth is open, endpoint should still work.
    resp = client.get("/api/status")
    assert resp.status_code == 200
    body = resp.json()
    assert "ollama" in body
    assert "active_agent" in body


def test_list_agents_has_default(client):
    resp = client.get("/api/agents")
    assert resp.status_code == 200
    agents = resp.json()
    assert any(a["id"] == "jarvis" for a in agents)
