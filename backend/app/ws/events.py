import json

from fastapi import APIRouter, Query, WebSocket, WebSocketDisconnect

from app.config import get_settings
from app.services.events_hub import get_events_hub

router = APIRouter()


@router.websocket("/ws/events")
async def events_ws(websocket: WebSocket, token: str | None = Query(default=None)) -> None:
    """Proactive server -> client push channel (docs/architecture.md §11):
    reminders firing, a fresh daily briefing, etc. Server -> client only.
    Idle-connection pings against NAT timeouts are handled client-side
    (OkHttp pingInterval, see android/README.md)."""
    settings = get_settings()
    if settings.device_token_set and token not in settings.device_token_set:
        await websocket.close(code=4401)
        return

    await websocket.accept()
    hub = get_events_hub()
    queue = hub.subscribe()
    try:
        while True:
            event = await queue.get()
            await websocket.send_text(json.dumps(event))
    except WebSocketDisconnect:
        pass
    finally:
        hub.unsubscribe(queue)
