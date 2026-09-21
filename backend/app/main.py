from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from sqlmodel import Session

from app.api import (
    routes_agents,
    routes_calendar,
    routes_chat,
    routes_files,
    routes_history,
    routes_status,
)
from app.database import engine, init_db
from app.services.agent_service import ensure_default_agent
from app.ws import voice


@asynccontextmanager
async def lifespan(_app: FastAPI):
    init_db()
    with Session(engine) as session:
        ensure_default_agent(session)
    yield


app = FastAPI(title="Jarvis Backend", version="0.1.0", lifespan=lifespan)

# Permissive CORS for now (personal single-user deployment behind its own
# domain) - see docs/architecture.md §9 for the auth hardening note.
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
app.include_router(voice.router)


@app.get("/health")
def health() -> dict:
    return {"ok": True}
