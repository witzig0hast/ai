import json
from collections.abc import AsyncIterator

import httpx

from app.config import get_settings


class OllamaClient:
    """Thin async wrapper around a natively-running Ollama instance.

    This client only ever talks HTTP to Ollama; it never starts/stops/manages
    the Ollama process itself (Ollama runs natively on the host, see
    docs/architecture.md §2, so e.g. Open WebUI can share the same instance).
    """

    def __init__(self, base_url: str | None = None) -> None:
        settings = get_settings()
        self.base_url = (base_url or settings.ollama_host).rstrip("/")

    async def is_healthy(self) -> bool:
        try:
            async with httpx.AsyncClient(timeout=3.0) as client:
                resp = await client.get(f"{self.base_url}/api/tags")
                return resp.status_code == 200
        except httpx.HTTPError:
            return False

    async def chat_stream(
        self,
        model: str,
        messages: list[dict[str, str]],
    ) -> AsyncIterator[str]:
        """Yields response text tokens as they arrive from Ollama's /api/chat."""
        payload = {"model": model, "messages": messages, "stream": True}
        async with httpx.AsyncClient(timeout=None) as client:
            async with client.stream(
                "POST", f"{self.base_url}/api/chat", json=payload
            ) as resp:
                resp.raise_for_status()
                async for line in resp.aiter_lines():
                    if not line.strip():
                        continue
                    chunk = json.loads(line)
                    token = chunk.get("message", {}).get("content", "")
                    if token:
                        yield token
                    if chunk.get("done"):
                        break

    async def chat_once(self, model: str, messages: list[dict[str, str]]) -> str:
        """Non-streaming helper for short auxiliary calls (e.g. suggestion
        generation) where we just want the full text."""
        full_text = ""
        async for token in self.chat_stream(model, messages):
            full_text += token
        return full_text
