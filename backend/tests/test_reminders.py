from datetime import datetime, timedelta, timezone

import pytest
from fastapi.testclient import TestClient

from app.main import app


@pytest.fixture()
def client():
    with TestClient(app) as c:
        yield c


def test_create_list_and_delete_reminder(client):
    due_at = (datetime.now(timezone.utc) + timedelta(minutes=30)).isoformat()
    create_resp = client.post("/api/reminders", json={"text": "Wäsche aufhängen", "due_at": due_at})
    assert create_resp.status_code == 201
    reminder = create_resp.json()
    assert reminder["text"] == "Wäsche aufhängen"
    assert reminder["fired"] is False

    list_resp = client.get("/api/reminders")
    assert list_resp.status_code == 200
    assert any(r["id"] == reminder["id"] for r in list_resp.json())

    delete_resp = client.delete(f"/api/reminders/{reminder['id']}")
    assert delete_resp.status_code == 204

    list_after = client.get("/api/reminders")
    assert not any(r["id"] == reminder["id"] for r in list_after.json())


def test_delete_unknown_reminder_is_404(client):
    resp = client.delete("/api/reminders/does-not-exist")
    assert resp.status_code == 404


def test_briefing_endpoint_works_without_weather_configured(client):
    # HOME_LATITUDE/HOME_LONGITUDE unset in the test env -> weather stays
    # None, briefing should still respond instead of erroring.
    resp = client.get("/api/briefing/today")
    assert resp.status_code == 200
    body = resp.json()
    assert "summary" in body
    assert body["weather"] is None
