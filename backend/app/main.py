import asyncio
import contextlib
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from sqlmodel import Session

from app.api import (
    routes_agents,
    routes_briefing,
    routes_calendar,
    routes_chat,
    routes_files,
    routes_history,
    routes_reminders,
    routes_status,
)
from app.database import engine, init_db
from app.services.agent_service import ensure_default_agent
from app.services.reminder_scheduler import run_reminder_scheduler
from app.ws import events, voice


@asynccontextmanager
async def lifespan(_app: FastAPI):
    init_db()
    with Session(engine) as session:
        ensure_default_agent(session)

    scheduler_task = asyncio.create_task(run_reminder_scheduler())
    try:
        yield
    finally:
        scheduler_task.cancel()
        with contextlib.suppress(asyncio.CancelledError):
            await scheduler_task


app = FastAPI(title="Jarvis Backend", version="0.1.0", lifespan=lifespan)

# Permissive CORS for now (personal single-user deployment behind its own
# domain) - see docs/architecture.md §14 for the auth hardening note.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(routes_status.router)
app.include_router(routes_agents.router)
app.include_router(routes_history.router)
app.include_router(routes_chat.router)
app.include_router(routes_files.router)
app.include_router(routes_calendar.router)
app.include_router(routes_reminders.router)
app.include_router(routes_briefing.router)
app.include_router(voice.router)
app.include_router(events.router)


@app.get("/health")
def health() -> dict:
    return {"ok": True}
