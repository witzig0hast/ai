from datetime import datetime, timedelta, timezone
from zoneinfo import ZoneInfo

from sqlmodel import Session

from app.config import get_settings
from app.database import engine
from app.services import memory_service, reminder_service
from app.services.integrations.home_assistant import HomeAssistantClient
from app.services.integrations.n8n import N8nClient
from app.services.weather_service import get_weather_service

# Ollama /api/chat "tools" format (OpenAI-style function schemas). Only
# models with tool-calling support (e.g. llama3.1) act on this; others
# silently ignore it. See docs/architecture.md §9.
TOOL_SCHEMAS: list[dict] = [
    {
        "type": "function",
        "function": {
            "name": "get_current_time",
            "description": "Gibt das aktuelle Datum und die Uhrzeit zurück.",
            "parameters": {"type": "object", "properties": {}},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_weather",
            "description": "Aktuelles Wetter und Temperatur am konfigurierten Standort des Nutzers.",
            "parameters": {"type": "object", "properties": {}},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "create_reminder",
            "description": (
                "Legt einen Reminder/Timer an, der zu einem bestimmten "
                "Zeitpunkt fällig wird und dann proaktiv gemeldet wird."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "text": {"type": "string", "description": "Woran erinnert werden soll"},
                    "in_minutes": {
                        "type": "number",
                        "description": "Fälligkeit relativ zu jetzt, in Minuten",
                    },
                    "due_at": {
                        "type": "string",
                        "description": "Fälligkeit absolut als ISO8601-Zeitstempel, alternativ zu in_minutes",
                    },
                },
                "required": ["text"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "list_reminders",
            "description": "Listet alle offenen (noch nicht fälligen) Reminder auf.",
            "parameters": {"type": "object", "properties": {}},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "cancel_reminder",
            "description": "Storniert einen Reminder anhand seiner ID.",
            "parameters": {
                "type": "object",
                "properties": {"reminder_id": {"type": "string"}},
                "required": ["reminder_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "remember_fact",
            "description": (
                "Merkt sich einen Fakt über den Nutzer dauerhaft (z.B. Name, "
                "Vorlieben, wiederkehrende Infos), der dir künftig in jedem "
                "Gespräch automatisch mitgegeben wird."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "text": {"type": "string", "description": "Der zu merkende Fakt, kurz und klar formuliert"}
                },
                "required": ["text"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "list_remembered_facts",
            "description": "Listet alle Fakten auf, die du dir bisher über den Nutzer gemerkt hast.",
            "parameters": {"type": "object", "properties": {}},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "forget_fact",
            "description": "Löscht einen zuvor gemerkten Fakt anhand seiner ID.",
            "parameters": {
                "type": "object",
                "properties": {"fact_id": {"type": "string"}},
                "required": ["fact_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "home_assistant_call_service",
            "description": (
                "Ruft einen Home-Assistant-Service auf, z.B. ein Licht "
                "schalten. Nur verfügbar, wenn Home Assistant aktiviert ist."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "domain": {"type": "string", "description": "z.B. 'light'"},
                    "service": {"type": "string", "description": "z.B. 'turn_on'"},
                    "entity_id": {"type": "string"},
                },
                "required": ["domain", "service", "entity_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "home_assistant_get_state",
            "description": (
                "Liest den aktuellen Zustand einer Home-Assistant-Entity. "
                "Nur verfügbar, wenn Home Assistant aktiviert ist."
            ),
            "parameters": {
                "type": "object",
                "properties": {"entity_id": {"type": "string"}},
                "required": ["entity_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "trigger_n8n_workflow",
            "description": (
                "Löst einen n8n-Webhook-Workflow aus. Nur verfügbar, wenn "
                "n8n aktiviert ist."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "webhook_path": {"type": "string"},
                    "payload": {"type": "object"},
                },
                "required": ["webhook_path"],
            },
        },
    },
]


async def execute_tool(name: str, arguments: dict, conversation_id: str | None) -> dict:
    """Dispatches a tool call by name. Never raises - a failure comes back as
    {"error": "..."} so the model can react in its next round instead of the
    whole chat/voice pipeline crashing."""
    try:
        handler = _HANDLERS.get(name)
        if handler is None:
            return {"error": f"Unbekanntes Tool: {name}"}
        return await handler(arguments, conversation_id)
    except Exception as exc:  # noqa: BLE001
        return {"error": str(exc)}


async def _get_current_time(_arguments: dict, _conversation_id: str | None) -> dict:
    settings = get_settings()
    now = datetime.now(ZoneInfo(settings.timezone))
    return {"iso": now.isoformat(), "formatted": now.strftime("%A, %d.%m.%Y %H:%M")}


async def _get_weather(_arguments: dict, _conversation_id: str | None) -> dict:
    weather = get_weather_service()
    if not weather.is_configured():
        return {"error": "Wetter ist nicht konfiguriert (HOME_LATITUDE/HOME_LONGITUDE fehlen)."}
    current = await weather.get_current()
    return current or {"error": "Wetterdaten aktuell nicht verfügbar."}


async def _create_reminder(arguments: dict, conversation_id: str | None) -> dict:
    text = arguments.get("text")
    if not text:
        return {"error": "Reminder braucht einen Text."}

    due_at_raw = arguments.get("due_at")
    in_minutes = arguments.get("in_minutes")
    if due_at_raw:
        due_at = datetime.fromisoformat(due_at_raw)
    elif in_minutes is not None:
        due_at = datetime.now(timezone.utc) + timedelta(minutes=float(in_minutes))
    else:
        return {"error": "Entweder in_minutes oder due_at angeben."}

    with Session(engine) as session:
        reminder = reminder_service.create_reminder(session, text, due_at, conversation_id)
        return {"id": reminder.id, "text": reminder.text, "due_at": reminder.due_at.isoformat()}


async def _list_reminders(_arguments: dict, _conversation_id: str | None) -> dict:
    with Session(engine) as session:
        reminders = reminder_service.list_reminders(session)
        return {
            "reminders": [
                {"id": r.id, "text": r.text, "due_at": r.due_at.isoformat()}
                for r in reminders
            ]
        }


async def _cancel_reminder(arguments: dict, _conversation_id: str | None) -> dict:
    reminder_id = arguments.get("reminder_id")
    if not reminder_id:
        return {"error": "reminder_id fehlt."}
    with Session(engine) as session:
        ok = reminder_service.cancel_reminder(session, reminder_id)
        return {"cancelled": ok}


async def _remember_fact(arguments: dict, _conversation_id: str | None) -> dict:
    text = arguments.get("text")
    if not text:
        return {"error": "text fehlt."}
    with Session(engine) as session:
        fact = memory_service.remember(session, text)
        return {"id": fact.id, "text": fact.text}


async def _list_remembered_facts(_arguments: dict, _conversation_id: str | None) -> dict:
    with Session(engine) as session:
        facts = memory_service.list_facts(session)
        return {"facts": [{"id": f.id, "text": f.text} for f in facts]}


async def _forget_fact(arguments: dict, _conversation_id: str | None) -> dict:
    fact_id = arguments.get("fact_id")
    if not fact_id:
        return {"error": "fact_id fehlt."}
    with Session(engine) as session:
        return {"forgotten": memory_service.forget(session, fact_id)}


async def _home_assistant_call_service(arguments: dict, _conversation_id: str | None) -> dict:
    client = HomeAssistantClient()
    if not client.enabled:
        return {"error": "Home Assistant ist nicht aktiviert (HOME_ASSISTANT_ENABLED=false)."}
    domain, service, entity_id = (
        arguments.get("domain"),
        arguments.get("service"),
        arguments.get("entity_id"),
    )
    if not (domain and service and entity_id):
        return {"error": "domain, service und entity_id sind erforderlich."}
    return {"result": await client.call_service(domain, service, {"entity_id": entity_id})}


async def _home_assistant_get_state(arguments: dict, _conversation_id: str | None) -> dict:
    client = HomeAssistantClient()
    if not client.enabled:
        return {"error": "Home Assistant ist nicht aktiviert (HOME_ASSISTANT_ENABLED=false)."}
    entity_id = arguments.get("entity_id")
    if not entity_id:
        return {"error": "entity_id ist erforderlich."}
    return {"state": await client.get_states(entity_id)}


async def _trigger_n8n_workflow(arguments: dict, _conversation_id: str | None) -> dict:
    client = N8nClient()
    if not client.enabled:
        return {"error": "n8n ist nicht aktiviert (N8N_ENABLED=false)."}
    webhook_path = arguments.get("webhook_path")
    if not webhook_path:
        return {"error": "webhook_path ist erforderlich."}
    return {"result": await client.trigger_webhook(webhook_path, arguments.get("payload") or {})}


_HANDLERS = {
    "get_current_time": _get_current_time,
    "get_weather": _get_weather,
    "create_reminder": _create_reminder,
    "list_reminders": _list_reminders,
    "cancel_reminder": _cancel_reminder,
    "remember_fact": _remember_fact,
    "list_remembered_facts": _list_remembered_facts,
    "forget_fact": _forget_fact,
    "home_assistant_call_service": _home_assistant_call_service,
    "home_assistant_get_state": _home_assistant_get_state,
    "trigger_n8n_workflow": _trigger_n8n_workflow,
}
