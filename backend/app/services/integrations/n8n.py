import httpx

from app.config import get_settings


class N8nClient:
    """Stub client for triggering n8n workflows via webhook. Disabled by
    default via N8N_ENABLED (see docs/architecture.md §8)."""

    def __init__(self) -> None:
        settings = get_settings()
        self.enabled = settings.n8n_enabled
        self.base_url = settings.n8n_base_url.rstrip("/")
        self.token = settings.n8n_webhook_token

    async def is_healthy(self) -> bool:
        if not self.enabled:
            return False
        try:
            async with httpx.AsyncClient(timeout=3.0) as client:
                resp = await client.get(f"{self.base_url}/healthz")
                return resp.status_code == 200
        except httpx.HTTPError:
            return False

    async def trigger_webhook(self, webhook_path: str, payload: dict) -> dict:
        if not self.enabled:
            raise RuntimeError("n8n integration is disabled")
        headers = {"Authorization": f"Bearer {self.token}"} if self.token else {}
        async with httpx.AsyncClient(timeout=15.0) as client:
            resp = await client.post(
                f"{self.base_url}/webhook/{webhook_path.lstrip('/')}",
                headers=headers,
                json=payload,
            )
            resp.raise_for_status()
            return resp.json()
