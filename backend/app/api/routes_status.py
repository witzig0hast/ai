from fastapi import APIRouter, Depends
from sqlmodel import Session

from app.core.security import require_device_token
from app.database import get_session
from app.services.agent_service import get_active_agent
from app.services.integrations.home_assistant import HomeAssistantClient
from app.services.integrations.n8n import N8nClient
from app.services.ollama_client import OllamaClient
from app.services.stt_service import get_stt_service
from app.services.tts_service import get_tts_service

router = APIRouter(prefix="/api/status", tags=["status"])


@router.get("")
async def get_status(
    session: Session = Depends(get_session),
    _token: str = Depends(require_device_token),
):
    ollama_ok = await OllamaClient().is_healthy()
    ha_ok = await HomeAssistantClient().is_healthy()
    n8n_ok = await N8nClient().is_healthy()
    active_agent = get_active_agent(session)

    return {
        "ollama": ollama_ok,
        # STT/TTS models load lazily on first real use (see services) to keep
        # startup fast and VRAM idle until needed - "ready" reflects whether
        # they're currently loaded, not just configured.
        "stt": get_stt_service().is_ready(),
        "tts": get_tts_service().is_ready(),
        "home_assistant": ha_ok,
        "n8n": n8n_ok,
        "active_agent": active_agent.id,
    }
