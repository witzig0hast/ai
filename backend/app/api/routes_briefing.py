from fastapi import APIRouter, Depends
from sqlmodel import Session

from app.core.security import require_device_token
from app.database import get_session
from app.services.agent_service import get_active_agent
from app.services.briefing_service import compose_briefing

router = APIRouter(prefix="/api/briefing", tags=["briefing"])


@router.get("/today")
async def briefing_today(
    session: Session = Depends(get_session),
    _token: str = Depends(require_device_token),
):
    agent = get_active_agent(session)
    return await compose_briefing(agent.ollama_model)
