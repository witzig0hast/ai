import json

from app.services.ollama_client import OllamaClient

_PROMPT = (
    "Schlage genau 3 kurze, natürliche Folgefragen vor, die man als Nächstes "
    "stellen könnte, basierend auf dieser Antwort. Antworte NUR mit einem "
    "JSON-Array aus 3 kurzen Strings, sonst nichts.\n\nAntwort:\n{answer}"
)


async def generate_suggestions(
    client: OllamaClient, model: str, answer_text: str
) -> list[str]:
    """Best-effort follow-up question suggestions shown under the assistant's
    answer (chat) or spoken as available context (voice). Never fatal - an
    empty list just means no chips/suggestions are shown."""
    if not answer_text.strip():
        return []
    try:
        raw = await client.chat_once(
            model, [{"role": "user", "content": _PROMPT.format(answer=answer_text)}]
        )
        suggestions = json.loads(raw[raw.find("[") : raw.rfind("]") + 1])
        return [str(s) for s in suggestions][:3]
    except Exception:  # noqa: BLE001
        return []
