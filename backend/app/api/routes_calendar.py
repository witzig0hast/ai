from fastapi import APIRouter, Depends

from app.core.security import require_device_token
from app.models.calendar_event import CalendarEvent

router = APIRouter(prefix="/api/calendar", tags=["calendar"])


@router.get("/upcoming", response_model=list[CalendarEvent])
async def upcoming_events(
    within_minutes: int = 180,
    _token: str = Depends(require_device_token),
):
    # Stub for the Home-Screen "departure board" ticker (docs/architecture.md
    # §5). Returns no events until a real source (Home Assistant calendar
    # entities or CalDAV) is wired up - the Android app is built to simply
    # never show the ticker when this list is empty.
    return []
