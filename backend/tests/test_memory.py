import pytest
from fastapi.testclient import TestClient

from app.main import app


@pytest.fixture()
def client():
    with TestClient(app) as c:
        yield c


def test_create_list_and_delete_fact(client):
    create_resp = client.post("/api/memory", json={"text": "Nutzer heißt Karim"})
    assert create_resp.status_code == 201
    fact = create_resp.json()
    assert fact["text"] == "Nutzer heißt Karim"

    list_resp = client.get("/api/memory")
    assert list_resp.status_code == 200
    assert any(f["id"] == fact["id"] for f in list_resp.json())

    delete_resp = client.delete(f"/api/memory/{fact['id']}")
    assert delete_resp.status_code == 204

    list_after = client.get("/api/memory")
    assert not any(f["id"] == fact["id"] for f in list_after.json())


def test_delete_unknown_fact_is_404(client):
    resp = client.delete("/api/memory/does-not-exist")
    assert resp.status_code == 404
