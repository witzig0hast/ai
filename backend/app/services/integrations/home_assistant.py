import httpx

from app.config import get_settings


class HomeAssistantClient:
    """Stub REST client for Home Assistant. Disabled by default via
    HOME_ASSISTANT_ENABLED (see docs/architecture.md §8) - the agent does not
    depend on Home Assistant to function, this is a later extension point.
    """

    def __init__(self) -> None:
        settings = get_settings()
        self.enabled = settings.home_assistant_enabled
        self.base_url = settings.home_assistant_base_url.rstrip("/")
        self.token = settings.home_assistant_token

    def _headers(self) -> dict[str, str]:
        return {"Authorization": f"Bearer {self.token}", "Content-Type": "application/json"}

    async def is_healthy(self) -> bool:
        if not self.enabled:
            return False
        try:
            async with httpx.AsyncClient(timeout=3.0) as client:
                resp = await client.get(f"{self.base_url}/api/", headers=self._headers())
                return resp.status_code == 200
        except httpx.HTTPError:
            return False

    async def get_states(self, entity_id: str | None = None) -> list[dict] | dict:
        if not self.enabled:
            raise RuntimeError("Home Assistant integration is disabled")
        path = f"/api/states/{entity_id}" if entity_id else "/api/states"
        async with httpx.AsyncClient(timeout=10.0) as client:
            resp = await client.get(f"{self.base_url}{path}", headers=self._headers())
            resp.raise_for_status()
            return resp.json()

    async def call_service(self, domain: str, service: str, data: dict) -> dict:
        if not self.enabled:
            raise RuntimeError("Home Assistant integration is disabled")
        async with httpx.AsyncClient(timeout=10.0) as client:
            resp = await client.post(
                f"{self.base_url}/api/services/{domain}/{service}",
                headers=self._headers(),
                json=data,
            )
            resp.raise_for_status()
            return resp.json()
