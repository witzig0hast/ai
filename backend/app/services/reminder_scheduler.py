import asyncio
import logging

from sqlmodel import Session

from app.config import get_settings
from app.database import engine
from app.services.events_hub import get_events_hub
from app.services.reminder_service import due_unfired_reminders

logger = logging.getLogger(__name__)


async def run_reminder_scheduler() -> None:
    """Background loop (started from the FastAPI lifespan) that fires due
    reminders by publishing a `reminder_due` event on the /ws/events channel
    (docs/architecture.md §11) and marking them fired. Polling instead of a
    per-reminder timer keeps this simple and resilient to server restarts -
    fine at personal-assistant scale."""
    settings = get_settings()
    hub = get_events_hub()

    while True:
        try:
            with Session(engine) as session:
                for reminder in due_unfired_reminders(session):
                    reminder.fired = True
                    session.add(reminder)
                    session.commit()
                    await hub.publish(
                        {
                            "type": "reminder_due",
                            "reminder": {"id": reminder.id, "text": reminder.text},
                        }
                    )
        except Exception:  # noqa: BLE001 - a scheduler tick must never crash the loop
            logger.exception("reminder scheduler tick failed")

        await asyncio.sleep(settings.reminder_poll_interval_seconds)
